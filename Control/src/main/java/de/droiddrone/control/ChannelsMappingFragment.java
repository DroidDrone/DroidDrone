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

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import de.droiddrone.common.FcCommon;

public class ChannelsMappingFragment extends Fragment {
    public static final int fragmentId = 4;
    private final MainActivity activity;
    private final Config config;
    private final Rc rc;
    private TextView tvControllerStatusRc;
    private final RcChannelMapUiElement[] uiChannels = new RcChannelMapUiElement[FcCommon.MAX_SUPPORTED_RC_CHANNEL_COUNT];
    private int selectedChannel = -1;
    private boolean isHidden = false;

    public ChannelsMappingFragment(MainActivity activity, Config config, Rc rc){
        super(R.layout.fragment_channels_mapping);
        this.activity = activity;
        this.config = config;
        this.rc = rc;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (savedInstanceState != null) return;
        LinearLayout llChannelsMapping = view.findViewById(R.id.llChannelsMapping);
        tvControllerStatusRc = view.findViewById(R.id.tvControllerStatusRc);
        int[] channelsMap = config.getRcChannelsMap();
        for (int i = 0; i < FcCommon.MAX_SUPPORTED_RC_CHANNEL_COUNT; i++) {
            uiChannels[i] = new RcChannelMapUiElement(activity, llChannelsMapping, i, channelsMap[i]);
            uiChannels[i].setOnClickListener(new RcChannelMapUiElement.OnClickListener() {
                @Override
                void onClick(int channelId, boolean selected) {
                    if (!selected) {
                        selectedChannel = -1;
                        return;
                    }
                    selectedChannel = channelId;
                    for (int j = 0; j < FcCommon.MAX_SUPPORTED_RC_CHANNEL_COUNT; j++) {
                        if (j != channelId) uiChannels[j].resetFocus();
                    }
                }
            });
        }
    }

    private void detectMovedChannel(){
        int lastCode = rc.getLastMovedStickCode();
        if (lastCode == -1) return;
        int selected = selectedChannel;
        selectedChannel = -1;
        if (selected == -1) return;
        uiChannels[selected].resetFocus();
        int oldChannel = rc.getChannelFromCode(lastCode);
        if (oldChannel != -1 && oldChannel != selected){
            rc.resetChannel(oldChannel);
            uiChannels[oldChannel].setChannelLevel(Rc.MIN_CHANNEL_LEVEL);
        }
        config.updateChannelMap(selected, lastCode);
        int[] channelsMap = config.getRcChannelsMap();
        for (int i = 0; i < FcCommon.MAX_SUPPORTED_RC_CHANNEL_COUNT; i++) {
            uiChannels[i].setCode(channelsMap[i]);
        }
    }

    public void updateStatus(){
        if (isHidden) return;
        activity.updateControllerStatusUi(tvControllerStatusRc);
    }

    public void updateChannelsLevels(){
        if (isHidden) return;
        short[] channels = rc.getRcChannels();
        if (channels == null) return;
        for (int i = 0; i < channels.length; i++) {
            uiChannels[i].setChannelLevel(channels[i]);
        }
        if (selectedChannel != -1) detectMovedChannel();
    }

    @Override
    public void onResume() {
        if (!isHidden) updateStatus();
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        isHidden = hidden;
        if (!isHidden) updateStatus();
        super.onHiddenChanged(hidden);
    }
}