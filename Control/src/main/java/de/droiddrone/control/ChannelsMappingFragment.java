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
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;

import de.droiddrone.common.FcCommon;
import de.droiddrone.common.SettingsCommon;
import de.droiddrone.common.Utils;

public class ChannelsMappingFragment extends Fragment {
    public static final int fragmentId = 4;
    private final MainActivity activity;
    private final Config config;
    private final Rc rc;
    private TextView tvControllerStatusRc;
    private Spinner headTrackingChannelX;
    private Spinner headTrackingChannelY;
    private Spinner headTrackingChannelZ;
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

        headTrackingChannelX = view.findViewById(R.id.headTrackingChannelX);
        headTrackingChannelY = view.findViewById(R.id.headTrackingChannelY);
        headTrackingChannelZ = view.findViewById(R.id.headTrackingChannelZ);
        setHeadTrackingChannelSpinner(headTrackingChannelX, 0);
        setHeadTrackingChannelSpinner(headTrackingChannelY, 1);
        setHeadTrackingChannelSpinner(headTrackingChannelZ, 2);
        setHeadTrackingAngleLimitEditText(view.findViewById(R.id.headTrackingAngleLimitX), 0);
        setHeadTrackingAngleLimitEditText(view.findViewById(R.id.headTrackingAngleLimitY), 1);
        setHeadTrackingAngleLimitEditText(view.findViewById(R.id.headTrackingAngleLimitZ), 2);
    }

    private void setHeadTrackingAngleLimitEditText(EditText editText, final int axis){
        int[] headTrackingAngleLimits = config.getHeadTrackingAngleLimits();
        editText.setText(String.valueOf(headTrackingAngleLimits[axis]));
        editText.setInputType(InputType.TYPE_CLASS_NUMBER);
        editText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE ||
                    event != null && event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                if (event == null || !event.isShiftPressed()) {
                    int value = Utils.parseInt(v.getText().toString(), SettingsCommon.headTrackingAngleLimitMin);
                    if (value < SettingsCommon.headTrackingAngleLimitMin) v.setText(String.valueOf(SettingsCommon.headTrackingAngleLimitMin));
                    if (value > SettingsCommon.headTrackingAngleLimitMax) v.setText(String.valueOf(SettingsCommon.headTrackingAngleLimitMax));
                    config.updateHeadTrackingAngleLimit(value, axis);
                }
            }
            return false;
        });
    }

    private void setHeadTrackingChannelSpinner(Spinner spinner, final int axis){
        ArrayList<String> headTrackingChannels = new ArrayList<>();
        headTrackingChannels.add(this.getResources().getString(R.string.head_tracking_disabled));
        for (int i = 0; i < FcCommon.MAX_SUPPORTED_RC_CHANNEL_COUNT; i++) {
            headTrackingChannels.add(getChannelName(i));
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity.getApplicationContext(), android.R.layout.simple_spinner_item, headTrackingChannels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int code = Rc.HEAD_TRACKING_CODE_OFFSET + axis;
                if (position > 0){
                    int channel = position - 1;
                    assignChannelMap(channel, code);
                }else{
                    int[] channelsMap = config.getRcChannelsMap();
                    for (int i = 0; i < channelsMap.length; i++) {
                        if (channelsMap[i] == code) {
                            assignChannelMap(i, -1);
                        }
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
        spinner.setSelection(config.getHeadTrackingChannel(axis) + 1);
    }

    public static String getChannelName(int channelId){
        String channelName = "CH" + (channelId + 1);
        switch (channelId){
            case 0:
                channelName += " (A - Roll): ";
                break;
            case 1:
                channelName += " (E - Pitch): ";
                break;
            case 2:
                channelName += " (R - Yaw): ";
                break;
            case 3:
                channelName += " (T - Throttle): ";
                break;
            default:
                channelName += ": ";
        }
        return channelName;
    }

    private void assignChannelMap(int channel, int code){
        selectedChannel = -1;
        if (channel == -1) return;
        uiChannels[channel].resetFocus();
        int[] channelsMap = config.getRcChannelsMap();
        if (code == -1){
            rc.resetChannel(channel);
            uiChannels[channel].setChannelLevel(Rc.MIN_CHANNEL_LEVEL);
        }else{
            for (int i = 0; i < channelsMap.length; i++) {
                if (channelsMap[i] == code && i != channel) {
                    rc.resetChannel(i);
                    uiChannels[i].setChannelLevel(Rc.MIN_CHANNEL_LEVEL);
                }
            }
        }
        if (code != Rc.HEAD_TRACKING_CODE_OFFSET) resetHeadTrackingSpinnerChannel(headTrackingChannelX, channel);
        if (code != Rc.HEAD_TRACKING_CODE_OFFSET + 1) resetHeadTrackingSpinnerChannel(headTrackingChannelY, channel);
        if (code != Rc.HEAD_TRACKING_CODE_OFFSET + 2) resetHeadTrackingSpinnerChannel(headTrackingChannelZ, channel);
        config.updateChannelMap(channel, code);
        channelsMap = config.getRcChannelsMap();
        for (int i = 0; i < channelsMap.length; i++) {
            uiChannels[i].setCode(channelsMap[i]);
        }
    }

    private void resetHeadTrackingSpinnerChannel(Spinner spinner, int channel){
        if (spinner == null) return;
        int spinnerChannel = spinner.getSelectedItemPosition() - 1;
        if (spinnerChannel != channel) return;
        spinner.setSelection(0);
    }

    public void updateStatus(){
        if (isHidden) return;
        activity.updateControllerStatusUi(tvControllerStatusRc);
    }

    public void updateChannelsLevels(short[] channels, int lastMovedStickCode){
        if (isHidden) return;
        if (channels == null) return;
        for (int i = 0; i < channels.length; i++) {
            uiChannels[i].setChannelLevel(channels[i]);
        }
        if (selectedChannel != -1 && lastMovedStickCode != -1) {
            assignChannelMap(selectedChannel, lastMovedStickCode);
        }
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