package de.droiddrone.flight;

import static de.droiddrone.common.Logcat.log;
import static de.droiddrone.common.Utils.getNextPow2;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTimestamp;
import android.media.MediaRecorder;

import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;

import de.droiddrone.common.MediaCommon;

public class AudioSource {
    private static final int sampleRate = 48000;
    private static final int channelConfig = AudioFormat.CHANNEL_IN_STEREO;
    private static final int encoding = AudioFormat.ENCODING_PCM_16BIT;
    private static final int[] audioSources = {MediaRecorder.AudioSource.CAMCORDER, MediaRecorder.AudioSource.MIC, MediaRecorder.AudioSource.DEFAULT};
    private final Context context;
    private final ArrayBlockingQueue<byte[]> mp4Buffer = new ArrayBlockingQueue<>(30);
    private final ArrayBlockingQueue<byte[]> streamBuffer = new ArrayBlockingQueue<>(30);
    private AudioRecord audioRecord;
    private int bytesToRead;
    private AudioFormat audioFormat;
    private boolean isInitialized;
    private boolean usedInMp4;
    private boolean usedInStream;
    private int audioSourceId;

    public AudioSource(Context context){
        this.context = context;
        isInitialized = false;
        usedInMp4 = false;
        usedInStream = false;
        audioSourceId = 0;
    }

    public boolean initialize(int consumerId){
        if (isInitialized){
            setConsumer(consumerId);
            return true;
        }
        audioFormat = new AudioFormat.Builder()
                .setEncoding(encoding)
                .setSampleRate(sampleRate)
                .setChannelMask(channelConfig)
                .build();
        bytesToRead = AudioRecord.getMinBufferSize(audioFormat.getSampleRate(), channelConfig, audioFormat.getEncoding());
        bytesToRead = getNextPow2(bytesToRead) / 2;
        boolean started = startAudioRecord();
        if (!started) return false;
        isInitialized = true;
        setConsumer(consumerId);
        Thread audioThread = new Thread(audioThreadRunnable);
        audioThread.setDaemon(false);
        audioThread.setName("audioThread");
        audioThread.start();
        return true;
    }

    private final Runnable audioThreadRunnable = new Runnable() {
        @Override
        public void run() {
            ByteBuffer buf = ByteBuffer.allocateDirect(bytesToRead);
            while (isInitialized){
                if (audioRecord.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING || audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    continue;
                }
                buf.position(0);
                int size = audioRecord.read(buf, bytesToRead, AudioRecord.READ_BLOCKING);
                if (size <= 0) continue;
                byte[] data = new byte[size];
                buf.get(data, 0, size);
                if (usedInMp4) mp4Buffer.offer(data);
                if (usedInStream) streamBuffer.offer(data);
            }
        }
    };

    public byte[] getBufferData(int consumerId){
        if (consumerId == MediaCommon.mp4AudioConsumerId) return mp4Buffer.poll();
        if (consumerId == MediaCommon.streamAudioConsumerId) return streamBuffer.poll();
        return null;
    }

    public long getTimestamp(){
        AudioTimestamp timestamp = new AudioTimestamp();
        audioRecord.getTimestamp(timestamp, AudioTimestamp.TIMEBASE_MONOTONIC);
        return timestamp.nanoTime/1000;
    }

    public AudioFormat getAudioFormat(){
        return audioFormat;
    }

    public int getBufferSize(){
        return bytesToRead;
    }

    private boolean startAudioRecord(){
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED){
            log("startAudioRecord error: no permission");
            return false;
        }
        try {
            audioRecord = new AudioRecord.Builder()
                    .setAudioSource(audioSources[audioSourceId])
                    .setAudioFormat(audioFormat)
                    .setBufferSizeInBytes(bytesToRead * 4)
                    .build();
            audioRecord.startRecording();
        }catch (Exception e){
            if (audioSourceId < audioSources.length - 1){
                audioSourceId++;
                return startAudioRecord();
            }
            log("AudioRecord error: " + e);
            return false;
        }
        return true;
    }

    private void setConsumer(int consumerId){
        if (consumerId == MediaCommon.mp4AudioConsumerId) usedInMp4 = true;
        if (consumerId == MediaCommon.streamAudioConsumerId) usedInStream = true;
    }

    public void stop(int consumerId){
        if (consumerId == MediaCommon.mp4AudioConsumerId) {
            usedInMp4 = false;
            mp4Buffer.clear();
        }
        if (consumerId == MediaCommon.streamAudioConsumerId) {
            usedInStream = false;
            streamBuffer.clear();
        }
        if (!usedInMp4 && !usedInStream) close();
    }

    public void close(){
        if (!isInitialized) return;
        isInitialized = false;
        usedInMp4 = false;
        usedInStream = false;
        try {
            audioRecord.stop();
        }catch (Exception e){
            e.printStackTrace();
        }
        mp4Buffer.clear();
        streamBuffer.clear();
        audioRecord.release();
    }
}
