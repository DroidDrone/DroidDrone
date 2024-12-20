/*
 *  This file is part of DroidDrone.
 *
 *  DroidDrone is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  DroidDrone is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with DroidDrone.  If not, see <https://www.gnu.org/licenses/>.
 */

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
import java.util.concurrent.ConcurrentLinkedQueue;

import static de.droiddrone.common.Logcat.log;

public class StreamEncoder {
    private static final int[] baseBitRates = {250000, 500000, 1000000, 2000000, 4000000, 6000000,
            8000000, 10000000, 15000000, 20000000};
    private final CameraManager cameraManager;
    private final AudioSource audioSource;
    private final Config config;
    private String codecType = MediaCommon.hevcCodecMime;
    private MediaCodec videoEncoder, audioEncoder;
    private final int maxOutputBufferSize = 30;
    public final ConcurrentLinkedQueue<MediaCodecBuffer> videoStreamOutputBuffer = new ConcurrentLinkedQueue<>();
    public final ConcurrentLinkedQueue<MediaCodecBuffer> videoRecorderOutputBuffer = new ConcurrentLinkedQueue<>();
    public final ConcurrentLinkedQueue<MediaCodecBuffer> audioOutputBuffer = new ConcurrentLinkedQueue<>();
    private int audioBitRate;
    private long bitRateCounter = 0;
    private long bitRateTimestamp = 0;
    private float lastBitRateMbs = 0;
    private int bitRateIndex = 3;
    private boolean lockIncreaseBitrate = false;
    private long lockIncreaseBitrateTs;
    private boolean encoderBitrateChange = false;
    private boolean sendFrames = false;
    private boolean writeToRecorder;
    private boolean isAudioSending;
    private boolean isVideoEncoderInitialized;
    private int audioThreadId;
    private long lastBitrateReduceTs;
    private Surface surface;

    public StreamEncoder(CameraManager cameraManager, AudioSource audioSource, Config config){
        this.cameraManager = cameraManager;
        this.audioSource = audioSource;
        this.config = config;
        writeToRecorder = false;
        isAudioSending = false;
        isVideoEncoderInitialized = false;
        audioThreadId = 0;
    }

    public Surface initializeVideo() {
        if (videoEncoder != null) {
            try {
                videoEncoder.stop();
            } catch (IllegalStateException e) {
                e.printStackTrace();
            }
            videoEncoder.release();
        }
        String encoderName = MediaCommon.getCodecName(codecType, true);
        if (encoderName == null) {
            codecType = MediaCommon.avcCodecMime;
            encoderName = MediaCommon.getCodecName(codecType, true);
        }
        if (encoderName == null) {
            log("No encoder found.");
            return null;
        }
        log("codecType: " + codecType + ", encoderName: " + encoderName);
        try {
            videoEncoder = MediaCodec.createByCodecName(encoderName);
        } catch (Exception e) {
            log("createEncoder error: " + e);
            return null;
        }
        videoStreamOutputBuffer.clear();
        videoRecorderOutputBuffer.clear();
        videoEncoder.setCallback(encoderCallback);
        try {
            MediaFormat mediaFormat = getEncoderFormat();
            videoEncoder.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        } catch (Exception e) {
            log("StreamEncoder configure error: " + e);
            e.printStackTrace();
            return null;
        }
        try {
            surface = videoEncoder.createInputSurface();
            videoEncoder.start();
        } catch (IllegalStateException e) {
            log("videoEncoder.start error: " + e);
            e.printStackTrace();
            surface = null;
            return null;
        }
        bitRateCounter = 0;
        bitRateTimestamp = System.currentTimeMillis();
        lastBitrateReduceTs = System.currentTimeMillis();
        lastBitRateMbs = 0;
        if (config.getBitrateLimit() != 0){
            while (getTargetBitRate() > config.getBitrateLimit() && bitRateIndex > 0){
                bitRateIndex--;
            }
        }
        encoderBitrateChange = true;
        isVideoEncoderInitialized = true;
        log("videoEncoder started.");
        return surface;
    }

    public Surface getSurface(){
        return surface;
    }

    public boolean isVideoEncoderInitialized(){
        return isVideoEncoderInitialized;
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
                if (sendFrames || info.flags == MediaCodec.BUFFER_FLAG_CODEC_CONFIG) {
                    audioOutputBuffer.offer(new MediaCodecBuffer(info, buf));
                    if (audioOutputBuffer.size() > maxOutputBufferSize) audioOutputBuffer.remove();
                }
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
            if (lockIncreaseBitrate) return;
            if (bitRateIndex < baseBitRates.length-1){
                if (baseBitRates[bitRateIndex+1] <= config.getBitrateLimit() || config.getBitrateLimit() == 0) {
                    bitRateIndex++;
                    encoderBitrateChange = true;
                }
            }
        }else{
            setLockIncreaseBitrate(true);
            long currentMs = System.currentTimeMillis();
            if (currentMs < lastBitrateReduceTs + 2000) return;
            lastBitrateReduceTs = currentMs;
            if (bitRateIndex > 0) {
                bitRateIndex--;
                encoderBitrateChange = true;
            }
        }
    }

    public void setLockIncreaseBitrate(boolean lockIncreaseBitrate){
        long current = System.currentTimeMillis();
        if (!lockIncreaseBitrate && lockIncreaseBitrateTs > current - 5000) return;
        this.lockIncreaseBitrate = lockIncreaseBitrate;
        if (lockIncreaseBitrate) lockIncreaseBitrateTs = current;
    }

    public int getTargetBitRate(){// Bit/s
        return baseBitRates[bitRateIndex];
    }

    public int getNextBitrate(){
        int index = bitRateIndex + 1;
        if (index >= baseBitRates.length) index = baseBitRates.length - 1;
        return baseBitRates[index];
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
            if (sendFrames || info.flags == MediaCodec.BUFFER_FLAG_CODEC_CONFIG) {
                videoStreamOutputBuffer.offer(new MediaCodecBuffer(info, buf));
                if (videoStreamOutputBuffer.size() > maxOutputBufferSize) videoStreamOutputBuffer.remove();
            }
            if (writeToRecorder) {
                videoRecorderOutputBuffer.offer(new MediaCodecBuffer(info, buf));
                if (videoRecorderOutputBuffer.size() > maxOutputBufferSize) videoRecorderOutputBuffer.remove();
            }
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

    private MediaFormat getEncoderFormat(){
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(codecType, cameraManager.getCamera().getWidth(), cameraManager.getCamera().getHeight());
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, baseBitRates[bitRateIndex]);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, cameraManager.getCamera().getTargetFps());
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        mediaFormat.setFloat(MediaFormat.KEY_I_FRAME_INTERVAL, 0.5f);
        return mediaFormat;
    }

    public void startSendFrames(){
        sendFrames = true;
    }

    public void close(){
        sendFrames = false;
        audioThreadId++;
        isVideoEncoderInitialized = false;
        surface = null;
        if (videoEncoder != null){
            try {
                videoEncoder.stop();
            }catch (IllegalStateException ignored){
            }
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
