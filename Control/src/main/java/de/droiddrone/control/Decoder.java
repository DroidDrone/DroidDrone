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

package de.droiddrone.control;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.view.Surface;

import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;

import de.droiddrone.common.MediaCodecBuffer;
import de.droiddrone.common.MediaCommon;

import static de.droiddrone.common.Logcat.log;
import static de.droiddrone.common.Utils.getNextPow2;

public class Decoder {
    static final int BUFFER_FLAG_CODEC_CONFIG = MediaCodec.BUFFER_FLAG_CODEC_CONFIG;
    static final int BUFFER_FLAG_KEY_FRAME = MediaCodec.BUFFER_FLAG_KEY_FRAME;
    private MediaCodec videoDecoder, audioDecoder;
    ArrayBlockingQueue<MediaCodecBuffer> videoInputBuffer = new ArrayBlockingQueue<>(30);
    ArrayBlockingQueue<MediaCodecBuffer> audioInputBuffer = new ArrayBlockingQueue<>(30);
    private boolean videoDecoderStarted = false;
    public boolean videoDecoderInitializationRunning = false;
    private boolean audioDecoderStarted = false;
    private int videoThreadId, audioThreadId;
    private final GlRenderer renderer;
    private int audioEncoding, audioChannelCount, audioSampleRate;
    private AudioTrack audioTrack;
    private int skipAudioBufCount;
    private byte[] initialFrame;
    private boolean isHevc;
    private int width;
    private int height;
    private boolean isFrontCamera;

    public Decoder(GlRenderer renderer) {
        this.renderer = renderer;
        videoThreadId = 0;
        audioThreadId = 0;
    }

    public void initializeAudio(int sampleRate, int channelCount, int encoding){
        if (audioDecoderStarted && audioSampleRate == sampleRate && audioChannelCount == channelCount && audioEncoding == encoding){
            return;
        }
        audioThreadId++;
        audioDecoderStarted = false;
        skipAudioBufCount = 10;
        audioSampleRate = sampleRate;
        audioChannelCount = channelCount;
        audioEncoding = encoding;
        if (audioDecoder != null){
            try {
                audioDecoder.stop();
            }catch (IllegalStateException e){
                e.printStackTrace();
            }
            audioDecoder.release();
        }
        if (audioTrack != null){
            try {
                audioTrack.stop();
            }catch (IllegalStateException e){
                e.printStackTrace();
            }
            audioTrack.release();
        }
        String decoderName = MediaCommon.getCodecName(MediaCommon.audioCodecMime, false);
        if (decoderName == null){
            log("No audio decoder found.");
            return;
        }
        log("audioDecoder name: " + decoderName);
        try
        {
            audioDecoder = MediaCodec.createByCodecName(decoderName);
        }
        catch (Exception e)
        {
            log("create audioDecoder error: " + e);
            return;
        }
        int channelConfig = (channelCount == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO);
        MediaFormat format  = MediaFormat.createAudioFormat(MediaCommon.audioCodecMime, sampleRate, channelCount);
        format.setInteger(MediaFormat.KEY_PCM_ENCODING, encoding);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        try{
            audioDecoder.configure(format, null, null, 0);
            audioDecoder.start();
        }catch (Exception e){
            log("audioDecoder configure error: " + e);
            return;
        }
        audioDecoderStarted = true;
        int audioBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, encoding);
        audioBufferSize = getNextPow2(audioBufferSize) * 4;
        AudioTrack.Builder atBuilder = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(encoding)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelConfig)
                        .build())
                .setBufferSizeInBytes(audioBufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM);
        atBuilder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY);
        try {
            audioTrack = atBuilder.build();
            audioTrack.play();
        } catch (Exception e) {
            log("AudioTrack error: " + e);
            audioTrack.release();
            audioDecoderStarted = false;
            return;
        }
        Thread audioDecoderThread = new Thread(audioDecoderRunnable);
        audioDecoderThread.setDaemon(false);
        audioDecoderThread.setName("audioDecoderThread");
        audioDecoderThread.start();
    }

    public void reset(){
        close();
        videoInputBuffer.offer(new MediaCodecBuffer(Decoder.BUFFER_FLAG_CODEC_CONFIG, initialFrame));
        Thread t1 = new Thread(() -> initializeVideo(isHevc, width, height, isFrontCamera));
        t1.start();
    }

    public void setVideoInitialFrame(byte[] buf){
        initialFrame = buf.clone();
        videoInputBuffer.clear();
        videoInputBuffer.offer(new MediaCodecBuffer(Decoder.BUFFER_FLAG_CODEC_CONFIG, buf));
    }

    public void initializeVideo(boolean isHevc, int width, int height, boolean isFrontCamera){
        videoThreadId++;
        videoDecoderStarted = false;
        this.isHevc = isHevc;
        this.width = width;
        this.height = height;
        this.isFrontCamera = isFrontCamera;

        // waiting for OpenGL
        for (int i = 0; i < 10; i++) {
            if (renderer.getSurface() != null) i = 10;
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                //
            }
        }

        try {
            renderer.setVideoFrameSize(width, height, isFrontCamera);
        }catch (Exception e){
            log("setVideoFrameSize error: " + e);
            videoDecoderInitializationRunning = false;
            return;
        }
        Surface surface = renderer.getSurface();
        if (surface == null) {
            videoDecoderInitializationRunning = false;
            return;
        }
        if (videoDecoder != null){
            try {
                videoDecoder.stop();
            }catch (IllegalStateException e){
                e.printStackTrace();
            }
            videoDecoder.release();
        }
        String type;
        if (isHevc){
            type = MediaCommon.hevcCodecMime;
        }else{
            type = MediaCommon.avcCodecMime;
        }
        String decoderName = MediaCommon.getCodecName(type, false);
        if (decoderName == null){
            log("No video decoder found.");
            videoDecoderInitializationRunning = false;
            return;
        }
        log("videoDecoder name: " + decoderName);
        try
        {
            videoDecoder = MediaCodec.createByCodecName(decoderName);
        }
        catch (Exception e)
        {
            log("create videoDecoder error: " + e);
            videoDecoderInitializationRunning = false;
            return;
        }
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(type, width, height);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        try{
            videoDecoder.configure(mediaFormat, surface, null, 0);
            videoDecoder.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT);
            if (Build.VERSION.SDK_INT >= 30){
                Bundle b=new Bundle();
                b.putInt(MediaCodec.PARAMETER_KEY_LOW_LATENCY, 1);
                videoDecoder.setParameters(b);
            }
            videoDecoder.start();
        }catch (Exception e){
            log("videoDecoder configure error: " + e);
            videoDecoderInitializationRunning = false;
            return;
        }
        videoDecoderStarted = true;
        videoDecoderInitializationRunning = false;
        Thread videoDecoderThread = new Thread(videoDecoderRunnable);
        videoDecoderThread.setDaemon(false);
        videoDecoderThread.setName("videoDecoderThread");
        videoDecoderThread.start();
    }

    public boolean isVideoDecoderStarted(){
        return videoDecoderStarted;
    }

    public boolean isAudioDecoderStarted(){
        return audioDecoderStarted;
    }

    private final Runnable audioDecoderRunnable = new Runnable() {
        public void run() {
            final int id = audioThreadId;
            while (audioDecoderStarted && id == audioThreadId) {
                MediaCodecBuffer buf = audioInputBuffer.peek();
                try {
                    if (buf != null) decodeAudioData(buf);
                    checkAudioDecoderOutput();
                } catch (Exception e) {
                    log("audioDecoderRunnable error: " + e);
                }
            }
        }
    };

    private void decodeAudioData(MediaCodecBuffer buf) {
        if (!audioDecoderStarted || audioDecoder == null || buf.data == null) return;
        int index = audioDecoder.dequeueInputBuffer(0);
        if (index >= 0) {
            ByteBuffer inputBuffer = audioDecoder.getInputBuffer(index);
            if (inputBuffer == null) return;
            audioInputBuffer.poll();
            inputBuffer.put(buf.data);
            audioDecoder.queueInputBuffer(index, 0, buf.data.length, 0, buf.flags);
        }
    }

    private void checkAudioDecoderOutput() throws IllegalStateException {
        if (!audioDecoderStarted || audioDecoder == null) return;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        int index = audioDecoder.dequeueOutputBuffer(info, 100);
        if (index >= 0) {
            if (info.size <= 0) {
                audioDecoder.releaseOutputBuffer(index, false);
                return;
            }
            ByteBuffer outputBuffer = audioDecoder.getOutputBuffer(index);
            if (skipAudioBufCount > 0){
                skipAudioBufCount--;
            }else{
                if (outputBuffer != null) audioTrack.write(outputBuffer, info.size, AudioTrack.WRITE_NON_BLOCKING);
            }
            audioDecoder.releaseOutputBuffer(index, false);
        }
    }

    private final Runnable videoDecoderRunnable = new Runnable() {
        public void run() {
            final int id = videoThreadId;
            while (videoDecoderStarted && id == videoThreadId) {
                MediaCodecBuffer buf = videoInputBuffer.peek();
                try {
                    if (buf != null) decodeVideoData(buf);
                    checkVideoDecoderOutput();
                } catch (Exception e) {
                    // nothing
                }
            }
        }
    };

    private void decodeVideoData(MediaCodecBuffer buf) {
        if (!videoDecoderStarted || videoDecoder == null || buf.data == null) return;
        int index = videoDecoder.dequeueInputBuffer(0);
        if (index >= 0) {
            ByteBuffer inputBuffer = videoDecoder.getInputBuffer(index);
            if (inputBuffer == null) return;
            videoInputBuffer.poll();
            inputBuffer.put(buf.data);
            videoDecoder.queueInputBuffer(index, 0, buf.data.length, 0, buf.flags);
        }
    }

    private void checkVideoDecoderOutput() throws IllegalStateException {
        if (!videoDecoderStarted || videoDecoder == null) return;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        int index = videoDecoder.dequeueOutputBuffer(info, 100);
        if (index >= 0) {
            if (info.size <= 0) {
                videoDecoder.releaseOutputBuffer(index, false);
                return;
            }
            videoDecoder.releaseOutputBuffer(index, true);
        }
    }

    public boolean isHevcSupported(){
        return MediaCommon.getCodecName(MediaCommon.hevcCodecMime, false) != null;
    }

    public void close(){
        videoThreadId++;
        audioThreadId++;
        videoDecoderStarted = false;
        audioDecoderStarted = false;
        if (videoDecoder != null){
            try {
                videoDecoder.stop();
                videoDecoder.release();
                videoDecoder = null;
            }catch (Exception ignored){}
        }
        if (audioDecoder != null){
            try {
                audioDecoder.stop();
                audioDecoder.release();
                audioDecoder = null;
            }catch (Exception ignored){}
        }
        if (audioTrack != null){
            try {
                audioTrack.stop();
                audioTrack.release();
                audioTrack = null;
            }catch (Exception ignored){}
        }
        videoInputBuffer.clear();
        audioInputBuffer.clear();
    }
}
