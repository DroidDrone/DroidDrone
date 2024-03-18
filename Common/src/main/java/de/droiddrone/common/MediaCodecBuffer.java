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
