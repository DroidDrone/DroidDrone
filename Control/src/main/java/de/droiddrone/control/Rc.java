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

import android.view.MotionEvent;

import java.util.HashMap;

import de.droiddrone.common.FcCommon;

public class Rc {
    public static final int KEY_CODE_OFFSET = 10000;
    public static final int MIN_CHANNEL_LEVEL = 1000;
    public static final int MAX_CHANNEL_LEVEL = 2000;
    private final short[] rcChannels = new short[FcCommon.MAX_SUPPORTED_RC_CHANNEL_COUNT];
    private final boolean[] rcChannelUpdate = new boolean[FcCommon.MAX_SUPPORTED_RC_CHANNEL_COUNT];
    private final Config config;
    private boolean isActive = false;
    private long lastActiveTimestamp;
    private int lastChannelIndex = -1;
    private int lastMovedStickCode = -1;
    private long lastMovedStickTimestamp = 0;
    private final HashMap<Integer, Integer> lastMovedSticks = new HashMap<>();

    public Rc(Config config){
        this.config = config;
        for (int i = 0; i < FcCommon.MAX_SUPPORTED_RC_CHANNEL_COUNT; i++) {
            rcChannels[i] = MIN_CHANNEL_LEVEL;
            rcChannelUpdate[i] = false;
        }
    }

    public short[] getRcChannels(){
        if (lastChannelIndex == -1 || !isActive()) return null;
        int count = lastChannelIndex + 1;
        short[] channels = new short[count];
        System.arraycopy(rcChannels, 0, channels, 0, count);
        return channels;
    }

    public void setState(boolean isActive){
        this.isActive = isActive;
        lastActiveTimestamp = System.currentTimeMillis();
    }

    public boolean isActive(){
        return isActive || System.currentTimeMillis() - lastActiveTimestamp < 500;
    }

    public int getControllerChannelCount(){
        return lastChannelIndex + 1;
    }

    public void setKeyValue(int keyCode, boolean isDown){
        int code = keyCode + Rc.KEY_CODE_OFFSET;
        int value = isDown ? MAX_CHANNEL_LEVEL : MIN_CHANNEL_LEVEL;
        Integer lastValue = lastMovedSticks.get(code);
        if (lastValue == null) lastValue = 0;
        lastMovedSticks.put(code, value);
        if (lastValue != value) {
            lastMovedStickCode = code;
            lastMovedStickTimestamp = System.currentTimeMillis();
        }
        int channel = getChannelFromCode(code);
        if (channel == -1) return;
        rcChannels[channel] = (short) value;
        rcChannelUpdate[channel] = true;
        if (channel > lastChannelIndex) lastChannelIndex = channel;
    }

    public void setAxisValues(HashMap<Integer, Float> axisValues){
        for (HashMap.Entry<Integer, Float> entry : axisValues.entrySet()) {
            int code = entry.getKey();
            float axisValue = entry.getValue();
            int value = convertAxisValue(code, axisValue);
            Integer lastValue = lastMovedSticks.get(code);
            if (lastValue == null) lastValue = 0;
            int offset = Math.abs(lastValue - value);
            if (offset > 99) {
                lastMovedSticks.put(code, value);
                lastMovedStickCode = code;
                lastMovedStickTimestamp = System.currentTimeMillis();
            }
            int channel = getChannelFromCode(code);
            if (channel == -1) continue;
            if (axisValue != 0) rcChannelUpdate[channel] = true;
            if (rcChannelUpdate[channel]){
                rcChannels[channel] = (short) value;
                if (channel > lastChannelIndex) lastChannelIndex = channel;
            }
            isActive = true;
        }
    }

    public void resetChannel(int channel){
        rcChannels[channel] = MIN_CHANNEL_LEVEL;
    }

    public int getLastMovedStickCode(){
        if (System.currentTimeMillis() - lastMovedStickTimestamp > 500) return -1;
        return lastMovedStickCode;
    }

    public int convertAxisValue(int code, float axisValue){
        if (code == MotionEvent.AXIS_BRAKE || code == MotionEvent.AXIS_GAS || code == MotionEvent.AXIS_LTRIGGER
                || code == MotionEvent.AXIS_RTRIGGER || code == MotionEvent.AXIS_THROTTLE){
            axisValue = (axisValue - 0.5f) * 2;
        }
        return Math.round((axisValue + 1) * ((MAX_CHANNEL_LEVEL - MIN_CHANNEL_LEVEL) / 2f) + MIN_CHANNEL_LEVEL);
    }

    public int getChannelFromCode(int code){
        int[] rcChannelsMap = config.getRcChannelsMap();
        for (int i = 0; i < FcCommon.MAX_SUPPORTED_RC_CHANNEL_COUNT; i++) {
            if (rcChannelsMap[i] == code) return i;
        }
        return -1;
    }
}
