package de.droiddrone.common;

import android.media.MediaCodec;

public class MediaCodecBuffer{
    public final int flags;
    public final MediaCodec.BufferInfo info;
    public final byte[] data;

    public MediaCodecBuffer(MediaCodec.BufferInfo info, byte[] data){
        this.flags = info.flags;
        this.data = data;
        this.info = info;
    }

    public MediaCodecBuffer(int flags, byte[] data) {
        this.flags = flags;
        this.data = data;
        this.info = null;
    }

    public MediaCodecBuffer(byte[] data) {
        this.flags = 0;
        this.data = data;
        this.info = null;
    }
}
