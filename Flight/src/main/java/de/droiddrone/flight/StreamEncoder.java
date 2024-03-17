package de.droiddrone.flight;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.view.Surface;

import androidx.annotation.NonNull;
import de.droiddrone.common.MediaCodecBuffer;
import de.droiddrone.common.MediaCommon;

import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;

import static de.droiddrone.common.Logcat.log;

public class StreamEncoder {
    private static final int[] baseBitRates = {250000, 500000, 1000000, 2000000, 4000000, 6000000,
            8000000, 10000000, 15000000, 20000000, 25000000, 30000000};
    private final Camera camera;
    private final AudioSource audioSource;
    private final Config config;
    private String codecType = MediaCommon.hevcCodecMime;
    private MediaCodec videoEncoder, audioEncoder;
    public final ArrayBlockingQueue<MediaCodecBuffer> videoStreamOutputBuffer = new ArrayBlockingQueue<>(30);
    public final ArrayBlockingQueue<MediaCodecBuffer> videoRecorderOutputBuffer = new ArrayBlockingQueue<>(30);
    public final ArrayBlockingQueue<MediaCodecBuffer> audioOutputBuffer = new ArrayBlockingQueue<>(30);
    private int audioBitRate;
    private int maxBitRate;
    private long bitRateCounter = 0;
    private long bitRateTimestamp = 0;
    private float lastBitRateMbs = 0;
    private int bitRateIndex = 3;
    private boolean encoderBitrateChange = false;
    private boolean sendFrames = false;
    private int resolutionDiv;
    private boolean writeToRecorder;
    private boolean isAudioSending;
    private int audioThreadId;

    public StreamEncoder(Camera camera, AudioSource audioSource, Config config){
        this.camera = camera;
        this.audioSource = audioSource;
        this.config = config;
        resolutionDiv = 1;
        writeToRecorder = false;
        isAudioSending = false;
        audioThreadId = 0;
    }

    public Surface initializeVideo(){
        maxBitRate = config.getBitrateLimit();

        if (videoEncoder != null){
            try {
                videoEncoder.stop();
            }catch(IllegalStateException e){
                e.printStackTrace();
            }
            videoEncoder.release();
        }
        String encoderName = MediaCommon.getCodecName(codecType, true);
        if (encoderName == null){
            codecType = MediaCommon.avcCodecMime;
            encoderName = MediaCommon.getCodecName(codecType, true);
        }
        if (encoderName == null){
            log("No encoder found.");
            return null;
        }
        log("codecType: " + codecType + ", encoderName: " + encoderName);
        try
        {
            videoEncoder = MediaCodec.createByCodecName(encoderName);
        }
        catch (Exception e)
        {
            log("createEncoder error: " + e);
            return null;
        }
        videoStreamOutputBuffer.clear();
        videoRecorderOutputBuffer.clear();
        videoEncoder.setCallback(encoderCallback);
        for (int i = 0; true; i++){
            try{
                MediaFormat mediaFormat = getEncoderFormat(resolutionDiv);
                videoEncoder.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                break;
            }catch (Exception e){
                if (i == 2){
                    log("StreamEncoder configure error: " + e);
                    e.printStackTrace();
                    return null;
                }
                resolutionDiv = resolutionDiv * 2;
            }
        }
        Surface surface = videoEncoder.createInputSurface();
        try {
            videoEncoder.start();
        }catch(IllegalStateException e){
            log("videoEncoder.start error: " + e);
            e.printStackTrace();
            return null;
        }
        bitRateCounter = 0;
        bitRateTimestamp = System.currentTimeMillis();
        lastBitRateMbs = 0;
        encoderBitrateChange = true;
        log("videoEncoder started.");
        return surface;
    }

    public int getAudioBitRate(){
        return audioBitRate;
    }

    public void startAudioEncoder(){
        audioBitRate = config.getAudioStreamBitrate();
        isAudioSending = false;
        audioThreadId++;
        audioOutputBuffer.clear();
        if (!audioSource.initialize(MediaCommon.streamAudioConsumerId)){
            return;
        }

        String encoderName = MediaCommon.getCodecName(MediaCommon.audioCodecMime, true);
        log("startAudioEncoder: " + encoderName);
        if (encoderName == null) return;
        try {
            audioEncoder = MediaCodec.createByCodecName(encoderName);
        } catch (Exception e) {
            log("startAudioEncoder error: " + e);
            return;
        }
        try {
            MediaFormat format  = MediaFormat.createAudioFormat(MediaCommon.audioCodecMime, audioSource.getAudioFormat().getSampleRate(), audioSource.getAudioFormat().getChannelCount());
            format.setInteger(MediaFormat.KEY_BIT_RATE, audioBitRate);
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, audioSource.getBufferSize());
            format.setInteger(MediaFormat.KEY_PCM_ENCODING, audioSource.getAudioFormat().getEncoding());
            format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            audioEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            audioEncoder.start();
        } catch (Exception e) {
            log("audioEncoder configure error: " + e);
            e.printStackTrace();
            return;
        }
        isAudioSending = true;
        Thread audioStreamThread = new Thread(audioStreamRunnable);
        audioStreamThread.setDaemon(false);
        audioStreamThread.setName("audioStreamThread");
        audioStreamThread.start();
    }

    public void stopAudioEncoder(){
        audioSource.stop(MediaCommon.streamAudioConsumerId);
        isAudioSending = false;
        try {
            audioEncoder.stop();
            audioEncoder.release();
        } catch (Exception e) {
            log("stopAudioEncoder error: " + e);
        }
        audioOutputBuffer.clear();
    }

    public AudioFormat getAudioFormat(){
        return audioSource.getAudioFormat();
    }

    private final Runnable audioStreamRunnable = new Runnable() {
        public void run() {
            final int id = audioThreadId;
            while (isAudioSending && id == audioThreadId) {
                try {
                    byte[] buf = audioSource.getBufferData(MediaCommon.streamAudioConsumerId);
                    if (buf != null && getTargetBitRate() >= 2000000) offerAudioEncoder(buf);
                    checkAudioEncoderOutput();
                } catch (Exception e) {
                    log("audioStreamRunnable error: " + e);
                }
            }
        }
    };

    private void offerAudioEncoder(byte[] buf) {
        if (audioEncoder == null) return;
        int index = audioEncoder.dequeueInputBuffer(0);
        if (index >= 0) {
            ByteBuffer inputBuffer = audioEncoder.getInputBuffer(index);
            if (inputBuffer == null) return;
            inputBuffer.put(buf);
            long timeUs = audioSource.getTimestamp();
            audioEncoder.queueInputBuffer(index, 0, buf.length, timeUs, 0);
        }
    }

    private void checkAudioEncoderOutput() throws IllegalStateException {
        if (audioEncoder == null) return;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        int index = audioEncoder.dequeueOutputBuffer(info, 100);
        if (index >= 0) {
            if (info.size <= 0) {
                audioEncoder.releaseOutputBuffer(index, false);
                return;
            }
            byte[] buf = new byte[info.size];
            ByteBuffer outputBuffer = audioEncoder.getOutputBuffer(index);
            if (outputBuffer != null) {
                outputBuffer.get(buf);
                if (sendFrames || info.flags == MediaCodec.BUFFER_FLAG_CODEC_CONFIG)
                    audioOutputBuffer.offer(new MediaCodecBuffer(info, buf));
            }
            audioEncoder.releaseOutputBuffer(index, false);
        }
    }

    public boolean isAudioSending(){
        return isAudioSending;
    }

    public String getCurrentCodecType(){
        return codecType;
    }

    public void changeBitRate(boolean increase){
        if (increase) {
            if (bitRateIndex < baseBitRates.length-1){
                if (baseBitRates[bitRateIndex+1] <= maxBitRate || maxBitRate == 0) {
                    bitRateIndex++;
                    encoderBitrateChange = true;
                }
            }
        }else{
            if (bitRateIndex > 0) {
                bitRateIndex--;
                encoderBitrateChange = true;
            }
        }
    }

    public int getTargetBitRate(){// Bit/s
        return baseBitRates[bitRateIndex];
    }

    public float getBitRate(){// MBit/s
        long current = System.currentTimeMillis();
        float timeSec = (current - bitRateTimestamp) / 1000f;
        if (timeSec < 0.2f && lastBitRateMbs > 0) return lastBitRateMbs;
        float bitRate = (bitRateCounter * 8) / timeSec / 1000000f;
        bitRateCounter = 0;
        bitRateTimestamp = current;
        lastBitRateMbs = bitRate;
        return bitRate;
    }

    public void setDefaultHevc(boolean isDefaultHevc){
        if (isDefaultHevc){
            codecType = MediaCommon.hevcCodecMime;
        }else{
            codecType = MediaCommon.avcCodecMime;
        }
    }

    public void setWriteToRecorder(boolean writeToRecorder){
        this.writeToRecorder = writeToRecorder;
    }

    public boolean isWriteToRecorder(){
        return writeToRecorder;
    }

    public MediaFormat getOutputFormat(){
        if (videoEncoder == null) return null;
        return videoEncoder.getOutputFormat();
    }

    private final MediaCodec.Callback encoderCallback = new MediaCodec.Callback() {
        @Override
        public void onInputBufferAvailable(@NonNull MediaCodec codec, int index) {

        }

        @Override
        public void onOutputBufferAvailable(@NonNull MediaCodec codec, int index, @NonNull MediaCodec.BufferInfo info) {
            byte[] buf = new byte[info.size];
            bitRateCounter += info.size;
            try {
                ByteBuffer outputByteBuffer = codec.getOutputBuffer(index);
                if (outputByteBuffer == null){
                    codec.releaseOutputBuffer(index, false);
                    return;
                }
                outputByteBuffer.get(buf);
                codec.releaseOutputBuffer(index, false);
            }catch (Exception e){
                e.printStackTrace();
            }
            if (sendFrames || info.flags == MediaCodec.BUFFER_FLAG_CODEC_CONFIG) videoStreamOutputBuffer.offer(new MediaCodecBuffer(info, buf));
            if (writeToRecorder) videoRecorderOutputBuffer.offer(new MediaCodecBuffer(info, buf));
            if (encoderBitrateChange){
                log("Encoder bitrate change: " + baseBitRates[bitRateIndex]);
                encoderBitrateChange = false;
                Bundle param = new Bundle();
                param.putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, baseBitRates[bitRateIndex]);
                videoEncoder.setParameters(param);
            }
        }

        @Override
        public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException e) {
            log("encoderCallback - onError: " + e);
        }

        @Override
        public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {
            log("encoderCallback - onOutputFormatChanged");
        }
    };

    private MediaFormat getEncoderFormat(int resolutionDiv){
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(codecType, camera.cameraResolution.getWidth()/resolutionDiv, camera.cameraResolution.getHeight()/resolutionDiv);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, baseBitRates[bitRateIndex]);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, camera.frameRate.getUpper());
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        if (Build.VERSION.SDK_INT >= 25){
            mediaFormat.setFloat(MediaFormat.KEY_I_FRAME_INTERVAL, 0.5f);
        }else{
            mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        }
        return mediaFormat;
    }

    public void startSendFrames(){
        sendFrames = true;
    }

    public void close(){
        audioThreadId++;
        if (videoEncoder != null){
            videoEncoder.stop();
            videoEncoder.release();
            videoEncoder = null;
        }
        if (isAudioSending) {
            isAudioSending = false;
            try {
                audioSource.stop(MediaCommon.streamAudioConsumerId);
                audioEncoder.stop();
            }catch (Exception e){
                e.printStackTrace();
            }
            audioEncoder.release();
        }
        videoStreamOutputBuffer.clear();
        videoRecorderOutputBuffer.clear();
        audioOutputBuffer.clear();
    }
}
