package de.droiddrone.common;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;

public class MediaCommon {
    public static final String hevcCodecMime = "video/hevc";
    public static final String avcCodecMime = "video/avc";
    public static final String audioCodecMime = "audio/mp4a-latm";
    public static final int mp4AudioConsumerId = 0;
    public static final int streamAudioConsumerId = 1;

    public static String getCodecName(String mimeType, boolean isEncoder)
    {
        MediaCodecList codecList = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        MediaCodecInfo[] codecInfos = codecList.getCodecInfos();
        for (MediaCodecInfo codecInfo : codecInfos)
        {
            if (isEncoder != codecInfo.isEncoder()) continue;
            if (Build.VERSION.SDK_INT >= 29 && !codecInfo.isHardwareAccelerated()) continue;
            String[] types = codecInfo.getSupportedTypes();
            for (String type : types) {
                if (type.equalsIgnoreCase(mimeType))
                    return codecInfo.getName();
            }
        }
        for (MediaCodecInfo codecInfo : codecInfos)
        {
            if (isEncoder != codecInfo.isEncoder()) continue;
            String[] types = codecInfo.getSupportedTypes();
            for (String type : types) {
                if (type.equalsIgnoreCase(mimeType))
                    return codecInfo.getName();
            }
        }
        return null;
    }
}
