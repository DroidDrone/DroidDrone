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
