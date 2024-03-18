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

import java.util.HashMap;

public class FrameFragments {
    private final int frameSize;
    private int sizeReceived;
    public boolean isStartReceived;
    public boolean isEndReceived;
    public boolean isCompleted;
    public final boolean isKeyFrame;
    private final HashMap<Integer, byte[]> frames = new HashMap<>();

    public FrameFragments(int frameSize, boolean isKeyFrame) {
        this.frameSize = frameSize;
        this.isKeyFrame = isKeyFrame;
        sizeReceived = 0;
        isStartReceived = false;
        isEndReceived = false;
        isCompleted = false;
    }

    public void putFragment(int offset, byte[] buf){
        if (buf == null) return;
        if (frames.containsKey(offset)) return;
        frames.put(offset, buf);
        sizeReceived += buf.length;
        if (offset == 0) isStartReceived = true;
        if (buf.length + offset == frameSize) isEndReceived = true;
        if (sizeReceived == frameSize) isCompleted = true;
    }

    public byte[] getFrame(){
        try {
            if (frameSize == 0) return null;
            byte[] frame = new byte[frameSize];
            for (HashMap.Entry<Integer, byte[]> entry : frames.entrySet()) {
                int offset = entry.getKey();
                byte[] buf = entry.getValue();
                if (buf == null) continue;
                System.arraycopy(buf, 0, frame, offset, buf.length);
            }
            return frame;
        }catch (Exception e){
            e.printStackTrace();
            return null;
        }
    }
}
