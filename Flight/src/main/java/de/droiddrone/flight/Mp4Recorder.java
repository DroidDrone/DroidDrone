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

import static de.droiddrone.common.Logcat.log;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.view.Surface;

import androidx.annotation.NonNull;

import java.io.File;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import de.droiddrone.common.MediaCodecBuffer;
import de.droiddrone.common.MediaCommon;

public class Mp4Recorder {
    private final Camera camera;
    private final Context context;
    private final AudioSource audioSource;
    private final Config config;
    private String codecType = MediaCommon.hevcCodecMime;
    private MediaCodec videoEncoder, audioEncoder;
    private Surface surface;
    private MediaMuxer muxer;
    private int videoBitRate;
    private int videoTrackIndex = -1, audioTrackIndex = -1;
    private boolean isRecording = false;
    private long startRecordingTimestamp;
    private boolean muxerStarted = false;
    private boolean isAudioRecording = false;
    private boolean isInitialized = false;
    private Thread audioEncoderThread;
    private long videoStartTimestamp = -1;
    private long audioStartTimestamp = -1;
    private StreamEncoder streamEncoder;

    public Mp4Recorder(Camera camera, Context context, AudioSource audioSource, Config config) {
        this.camera = camera;
        this.context = context;
        this.audioSource = audioSource;
        this.config = config;
    }

    public Surface initialize() {
        isRecording = false;
        startRecordingTimestamp = 0;
        isAudioRecording = false;
        muxerStarted = false;
        videoTrackIndex = -1;
        audioTrackIndex = -1;
        videoStartTimestamp = -1;
        audioStartTimestamp = -1;
        if (!config.isUseExtraEncoder()) {
            this.surface = null;
            isInitialized = true;
            return null;
        }
        videoBitRate = config.getRecordedVideoBitrate();

        String encoderName = MediaCommon.getCodecName(codecType, true);
        if (encoderName == null) {
            codecType = MediaCommon.avcCodecMime;
            encoderName = MediaCommon.getCodecName(codecType, true);
        }
        if (encoderName == null) {
            log("No encoder found.");
            return null;
        }
        try {
            videoEncoder = MediaCodec.createByCodecName(encoderName);
        } catch (Exception e) {
            log("createEncoder error: " + e);
            return null;
        }
        Surface surface;
        videoEncoder.setCallback(videoEncoderCallback);
        try {
            MediaFormat mediaFormat = getEncoderFormat();
            videoEncoder.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            surface = videoEncoder.createInputSurface();
        } catch (Exception e) {
            log("Mp4Encoder configure error: " + e);
            e.printStackTrace();
            return null;
        }
        this.surface = surface;
        videoEncoder.start();
        isInitialized = true;
        return surface;
    }

    private void startAudioEncoder(){
        int audioBitRate = config.getRecordedAudioBitrate();

        String encoderName = MediaCommon.getCodecName(MediaCommon.audioCodecMime, true);
        log("startAudioEncoder: "+encoderName);
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
        isAudioRecording = true;
        audioEncoderThread = new Thread(audioEncoderRunnable);
        audioEncoderThread.setDaemon(false);
        audioEncoderThread.setName("audioEncoderThread");
        audioEncoderThread.start();
    }

    private final Runnable audioEncoderRunnable = new Runnable() {
        public void run() {
            audioEncoderThread.setPriority(Thread.NORM_PRIORITY);
            while (isAudioRecording) {
                try {
                    byte[] buf = audioSource.getBufferData(MediaCommon.mp4AudioConsumerId);
                    if (buf != null) offerEncoder(buf);
                    checkEncoderOutput();
                } catch (Exception e) {
                    log("audioEncoderRunnable error: " + e);
                }
            }
        }
    };

    private void offerEncoder(byte[] buf) {
        if (audioEncoder == null || !isRecording || videoStartTimestamp == -1) return;
        int index = audioEncoder.dequeueInputBuffer(0);
        if (index >= 0) {
            ByteBuffer inputBuffer = audioEncoder.getInputBuffer(index);
            if (inputBuffer == null) return;
            inputBuffer.put(buf);
            long timeUs = audioSource.getTimestamp();
            if (audioStartTimestamp == -1) audioStartTimestamp = timeUs;
            audioEncoder.queueInputBuffer(index, 0, buf.length, timeUs - audioStartTimestamp, 0);
        }
    }

    private void checkEncoderOutput() throws IllegalStateException {
        if (audioEncoder == null) return;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        int index = audioEncoder.dequeueOutputBuffer(info, 100);
        if (index >= 0) {
            if (info.size <= 0) {
                audioEncoder.releaseOutputBuffer(index, false);
                return;
            }
            ByteBuffer outputBuffer = audioEncoder.getOutputBuffer(index);
            if (audioTrackIndex == -1){
                audioTrackIndex = muxer.addTrack(audioEncoder.getOutputFormat());
                checkMuxerStart();
            }
            if (muxerStarted){
                if (outputBuffer != null) muxer.writeSampleData(audioTrackIndex, outputBuffer, info);
            }
            audioEncoder.releaseOutputBuffer(index, false);
        }
    }

    public boolean isRecording() {
        return isRecording && muxerStarted;
    }

    public long getStartRecordingTimestamp(){
        return startRecordingTimestamp;
    }

    private boolean isStoragePermissionGranted() {
        if (Build.VERSION.SDK_INT <= 28) {
            return context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    public void startRecording(boolean withAudio) {
        startRecording(withAudio, null);
    }

    public void startRecording(boolean withAudio, StreamEncoder streamEncoder) {
        if (isRecording) return;
        log("startRecording");
        if (!isInitialized || !isStoragePermissionGranted()) return;
        this.streamEncoder = streamEncoder;
        if (streamEncoder == null){
            camera.startCapture();
        }else{
            streamEncoder.setWriteToRecorder(true);
            Thread streamEncoderThread = new Thread(streamEncoderThreadRun);
            streamEncoderThread.setDaemon(false);
            streamEncoderThread.setName("streamEncoderThread");
            streamEncoderThread.start();
        }
        File myDir = new File(context.getExternalMediaDirs()[0], "Video");
        boolean dirExists = true;
        if (!myDir.exists()) dirExists = myDir.mkdirs();
        if (!dirExists) return;
        String fileName = new SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().getTime()) + ".mp4";
        File file = new File(myDir, fileName);
        try {
            boolean created = file.createNewFile();
            if (!created) return;
            muxer = new MediaMuxer(file.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            if (withAudio) {
                if (audioSource.initialize(MediaCommon.mp4AudioConsumerId)) startAudioEncoder();
            }
            isRecording = true;
            startRecordingTimestamp = System.currentTimeMillis();
            new SingleMediaScanner(context, file);
        } catch (Exception e) {
            e.printStackTrace();
            log("Write file error: " + e);
        }
    }

    private void checkMuxerStart(){
        if ((!isAudioRecording || audioTrackIndex != -1) && videoTrackIndex != -1){
            muxer.start();
            muxerStarted = true;
        }
    }

    static class SingleMediaScanner implements MediaScannerConnection.MediaScannerConnectionClient {
        private final MediaScannerConnection mMs;
        private final File mFile;

        public SingleMediaScanner(Context context, File f) {
            mFile = f;
            mMs = new MediaScannerConnection(context, this);
            mMs.connect();
        }

        @Override
        public void onMediaScannerConnected() {
            mMs.scanFile(mFile.getAbsolutePath(), null);
        }

        @Override
        public void onScanCompleted(String path, Uri uri) {
            mMs.disconnect();
        }
    }

    public void stopRecording(){
        if (!isRecording) return;
        log("stopRecording");
        if (streamEncoder == null){
            camera.stopCapture();
        }else{
            streamEncoder.setWriteToRecorder(false);
        }
        isRecording = false;
        muxerStarted = false;
        startRecordingTimestamp = 0;
        videoTrackIndex = -1;
        audioTrackIndex = -1;
        videoStartTimestamp = -1;
        audioStartTimestamp = -1;
        try {
            muxer.stop();
        }catch (Exception e){
            e.printStackTrace();
        }
        try {
            muxer.release();
        }catch (Exception e){
            e.printStackTrace();
        }
        if (isAudioRecording) {
            isAudioRecording = false;
            try {
                audioSource.stop(MediaCommon.mp4AudioConsumerId);
                audioEncoder.stop();
            }catch (Exception e){
                e.printStackTrace();
            }
            audioEncoder.release();
        }
    }

    private final Runnable streamEncoderThreadRun = new Runnable() {
        public void run() {
            while (streamEncoder != null && streamEncoder.isWriteToRecorder()) {
                try {
                    MediaCodecBuffer buf = streamEncoder.videoRecorderOutputBuffer.poll();
                    if (buf == null || !isRecording || buf.info == null) continue;
                    if (videoTrackIndex == -1){
                        videoTrackIndex = muxer.addTrack(streamEncoder.getOutputFormat());
                        checkMuxerStart();
                    }
                    if (videoStartTimestamp == -1 && buf.info.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME && muxerStarted) videoStartTimestamp = buf.info.presentationTimeUs;
                    if (muxerStarted && videoStartTimestamp != -1) {
                        buf.info.presentationTimeUs = buf.info.presentationTimeUs - videoStartTimestamp;
                        ByteBuffer outputByteBuffer = ByteBuffer.allocateDirect(buf.data.length);
                        outputByteBuffer.put(buf.data);
                        outputByteBuffer.position(0);
                        muxer.writeSampleData(videoTrackIndex, outputByteBuffer, buf.info);
                    }
                } catch (Exception e) {
                    // do nothing
                }
            }
        }
    };

    private final MediaCodec.Callback videoEncoderCallback = new MediaCodec.Callback() {
        @Override
        public void onInputBufferAvailable(@NonNull MediaCodec codec, int index) {

        }

        @Override
        public void onOutputBufferAvailable(@NonNull MediaCodec codec, int index, @NonNull MediaCodec.BufferInfo info) {
            try {
                ByteBuffer outputByteBuffer = codec.getOutputBuffer(index);
                if (isRecording){
                    try {
                        if (videoTrackIndex == -1){
                            videoTrackIndex = muxer.addTrack(videoEncoder.getOutputFormat());
                            checkMuxerStart();
                        }
                        if (videoStartTimestamp == -1 && info.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME && muxerStarted) videoStartTimestamp = info.presentationTimeUs;
                        if (muxerStarted && videoStartTimestamp != -1) {
                            info.presentationTimeUs = info.presentationTimeUs - videoStartTimestamp;
                            if (outputByteBuffer != null) muxer.writeSampleData(videoTrackIndex, outputByteBuffer, info);
                        }
                    }catch (Exception e){
                        log("Mp4Encoder buffer error: " + e);
                        e.printStackTrace();
                        stopRecording();
                    }
                }
                codec.releaseOutputBuffer(index, false);
            }catch (Exception e){
                e.printStackTrace();
            }
        }

        @Override
        public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException e) {

        }

        @Override
        public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {

        }
    };

    private MediaFormat getEncoderFormat(){
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(codecType, camera.getWidth(), camera.getHeight());
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, videoBitRate);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, camera.getTargetFps());
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 3);
        return mediaFormat;
    }


    public void close(){
        isInitialized = false;
        boolean recording = isRecording;
        stopRecording();
        if (recording) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        if (videoEncoder != null){
            try {
                videoEncoder.stop();
            }catch (IllegalStateException ignored){
            }
            videoEncoder.release();
            videoEncoder = null;
        }
        if (surface != null) surface.release();
    }
}
