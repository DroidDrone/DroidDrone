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
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import com.rarepebble.colorpicker.ColorPreference;

import de.droiddrone.common.FcCommon;
import de.droiddrone.common.SettingsCommon;
import de.droiddrone.common.Utils;

public class SettingsFragment extends PreferenceFragmentCompat {
    public static final int fragmentId = 3;
    private final MainActivity activity;
    private final boolean[] isCameraEnabled = new boolean[SettingsCommon.maxCamerasCount];
    private final boolean[] isUseUsbCamera = new boolean[SettingsCommon.maxCamerasCount];

    public SettingsFragment(MainActivity activity){
        this.activity = activity;
    }

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);
        checkFsWarning();
        isCameraEnabled[0] = true;
        for (int i = 0; i < SettingsCommon.maxCamerasCount; i++) {
            String cameraNum = "";
            SwitchPreferenceCompat cameraEnabled = null;
            if (i > 0){
                cameraNum = "_" + (i + 1);
                cameraEnabled = findPreference("cameraEnabled" + cameraNum);
                isCameraEnabled[i] = cameraEnabled != null && cameraEnabled.isChecked();
            }
            SwitchPreferenceCompat useUsbCamera = findPreference("useUsbCamera" + cameraNum);
            isUseUsbCamera[i] = useUsbCamera != null && useUsbCamera.isChecked();
            EditTextPreference cameraId = findPreference("cameraId" + cameraNum);
            if (cameraId != null) {
                setEditTextPreferenceSummary(cameraId);
                cameraId.setEnabled(!isUseUsbCamera[i] && isCameraEnabled[i]);
            }
            setListPreferenceSummary(findPreference("usbCameraFrameFormat" + cameraNum));
            setListPreferenceSummary(findPreference("cameraResolution" + cameraNum));
            setListPreferenceSummary(findPreference("cameraFps" + cameraNum));
            onUseUsbCameraChanged(useUsbCamera, cameraId, i);
            onCameraEnabledChanged(cameraEnabled, cameraId, i);
        }
        EditTextPreference usbSerialPortIndex = findPreference("usbSerialPortIndex");
        EditTextPreference mavlinkTargetSysId = findPreference("mavlinkTargetSysId");
        EditTextPreference mavlinkGcsSysId = findPreference("mavlinkGcsSysId");
        ListPreference mavlinkUdpBridge = findPreference("mavlinkUdpBridge");
        EditTextPreference mavlinkUdpBridgeIp = findPreference("mavlinkUdpBridgeIp");
        EditTextPreference mavlinkUdpBridgePort = findPreference("mavlinkUdpBridgePort");
        setNumericEditTextPreferenceSummary(usbSerialPortIndex);
        setNumericEditTextPreferenceSummary(mavlinkTargetSysId);
        setNumericEditTextPreferenceSummary(mavlinkGcsSysId);
        setNumericEditTextPreferenceSummary(mavlinkUdpBridgePort);
        setEditTextPreferenceSummary(mavlinkUdpBridgeIp);
        setListPreferenceSummary(mavlinkUdpBridge);
        setListPreferenceSummary(findPreference("bitrateLimit"));
        setListPreferenceSummary(findPreference("audioStreamBitrate"));
        setListPreferenceSummary(findPreference("recordedAudioBitrate"));
        setListPreferenceSummary(findPreference("recordedVideoBitrate"));
        setListPreferenceSummary(findPreference("videoRecorderCodec"));
        setListPreferenceSummary(findPreference("telemetryRefreshRate"));
        setListPreferenceSummary(findPreference("rcRefreshRate"));
        setListPreferenceSummary(findPreference("serialBaudRate"));
        setEditTextPreferenceSummary(findPreference("nativeSerialPort"));
        SwitchPreferenceCompat useNativeSerialPort = findPreference("useNativeSerialPort");
        if (usbSerialPortIndex != null) {
            usbSerialPortIndex.setEnabled(useNativeSerialPort == null || !useNativeSerialPort.isChecked());
        }
        if (useNativeSerialPort != null) {
            useNativeSerialPort.setOnPreferenceChangeListener((preference, newValue) -> {
                if (usbSerialPortIndex != null) usbSerialPortIndex.setEnabled(Boolean.FALSE.equals(newValue));
                return true;
            });
        }
        if (mavlinkUdpBridge != null) {
            mavlinkUdpBridge.setOnPreferenceChangeListener((preference, newValue) -> {
                int value = Utils.parseInt((String) newValue, SettingsCommon.mavlinkUdpBridge);
                if (mavlinkUdpBridgeIp != null) mavlinkUdpBridgeIp.setEnabled(value == SettingsCommon.MavlinkUdpBridge.specificIp);
                if (mavlinkUdpBridgePort != null) mavlinkUdpBridgePort.setEnabled(value != SettingsCommon.MavlinkUdpBridge.disabled);
                return true;
            });
        }

        ListPreference fcProtocol = findPreference("fcProtocol");
        if (fcProtocol != null){
            setListPreferenceSummary(fcProtocol);
            fcProtocol.setOnPreferenceChangeListener((preference, newValue) -> {
                fcProtocolChanged(mavlinkTargetSysId, mavlinkGcsSysId, mavlinkUdpBridge, mavlinkUdpBridgeIp, mavlinkUdpBridgePort, (String) newValue);
                return true;
            });
            fcProtocolChanged(mavlinkTargetSysId, mavlinkGcsSysId, mavlinkUdpBridge, mavlinkUdpBridgeIp, mavlinkUdpBridgePort, fcProtocol.getValue());
        }

        EditTextPreference vrFrameScale = findPreference("vrFrameScale");
        EditTextPreference vrCenterOffset = findPreference("vrCenterOffset");
        EditTextPreference vrOsdOffset = findPreference("vrOsdOffset");
        EditTextPreference vrOsdScale = findPreference("vrOsdScale");
        SwitchPreferenceCompat vrHeadTracking = findPreference("vrHeadTracking");
        setNumericEditTextPreferenceSummary(vrFrameScale, SettingsCommon.vrFrameScaleMin, SettingsCommon.vrFrameScaleMax);
        setNumericEditTextPreferenceSummary(vrCenterOffset, SettingsCommon.vrCenterOffsetMin, SettingsCommon.vrCenterOffsetMax);
        setNumericEditTextPreferenceSummary(vrOsdOffset, SettingsCommon.vrOsdOffsetMin, SettingsCommon.vrOsdOffsetMax);
        setNumericEditTextPreferenceSummary(vrOsdScale, SettingsCommon.vrOsdScaleMin, SettingsCommon.vrOsdScaleMax);

        ListPreference vrMode = findPreference("vrMode");
        if (vrMode != null){
            setListPreferenceSummary(vrMode);
            vrMode.setOnPreferenceChangeListener((preference, newValue) -> {
                vrModeChanged(vrFrameScale, vrCenterOffset, vrOsdOffset, vrHeadTracking, vrOsdScale, (String) newValue);
                return true;
            });
            vrModeChanged(vrFrameScale, vrCenterOffset, vrOsdOffset, vrHeadTracking, vrOsdScale, vrMode.getValue());
        }

        Preference channelsMapping = findPreference("channelsMapping");
        if (channelsMapping != null) {
            channelsMapping.setOnPreferenceClickListener(preference -> {
                activity.showChannelsMappingFragment();
                return true;
            });
        }
    }

    private void onUseUsbCameraChanged(SwitchPreferenceCompat useUsbCamera, final EditTextPreference cameraId, final int cameraIndex){
        if (useUsbCamera != null) {
            useUsbCamera.setOnPreferenceChangeListener((preference, newValue) -> {
                if (cameraId != null){
                    cameraId.setEnabled(Boolean.FALSE.equals(newValue) && isCameraEnabled[cameraIndex]);
                }
                isUseUsbCamera[cameraIndex] = (boolean) newValue;
                return true;
            });
        }
    }

    private void onCameraEnabledChanged(SwitchPreferenceCompat cameraEnabled, final EditTextPreference cameraId, final int cameraIndex){
        if (cameraEnabled != null) {
            cameraEnabled.setOnPreferenceChangeListener((preference, newValue) -> {
                if (cameraId != null) {
                    cameraId.setEnabled(Boolean.TRUE.equals(newValue) && !isUseUsbCamera[cameraIndex]);
                }
                isCameraEnabled[cameraIndex] = (boolean) newValue;
                return true;
            });
        }
    }

    private void fcProtocolChanged(EditTextPreference mavlinkTargetSysId, EditTextPreference mavlinkGcsSysId,
                                   ListPreference mavlinkUdpBridge, EditTextPreference mavlinkUdpBridgeIp,
                                   EditTextPreference mavlinkUdpBridgePort, String value){
        boolean isMsp = false;
        try {
            isMsp = Integer.parseInt(value) == FcCommon.FC_PROTOCOL_MSP;
        }catch (Exception ignored){
        }
        int mavlinkUdpBridgeValue = SettingsCommon.mavlinkUdpBridge;
        if (mavlinkTargetSysId != null) mavlinkTargetSysId.setEnabled(!isMsp);
        if (mavlinkGcsSysId != null) mavlinkGcsSysId.setEnabled(!isMsp);
        if (mavlinkUdpBridge != null) {
            mavlinkUdpBridge.setEnabled(!isMsp);
            mavlinkUdpBridgeValue = Utils.parseInt(mavlinkUdpBridge.getValue(), SettingsCommon.mavlinkUdpBridge);
        }
        if (mavlinkUdpBridgeIp != null) mavlinkUdpBridgeIp.setEnabled(!isMsp && mavlinkUdpBridgeValue == SettingsCommon.MavlinkUdpBridge.specificIp);
        if (mavlinkUdpBridgePort != null) mavlinkUdpBridgePort.setEnabled(!isMsp && mavlinkUdpBridgeValue != SettingsCommon.MavlinkUdpBridge.disabled);
    }

    private void vrModeChanged(EditTextPreference vrFrameScale, EditTextPreference vrCenterOffset,
                               EditTextPreference vrOsdOffset, SwitchPreferenceCompat vrHeadTracking,
                               EditTextPreference vrOsdScale, String value){
        boolean isVrEnabled = false;
        try {
            isVrEnabled = Integer.parseInt(value) != SettingsCommon.VrMode.off;
        }catch (Exception ignored){
        }
        if (vrFrameScale != null) vrFrameScale.setEnabled(isVrEnabled);
        if (vrCenterOffset != null) vrCenterOffset.setEnabled(isVrEnabled);
        if (vrOsdOffset != null) vrOsdOffset.setEnabled(isVrEnabled);
        if (vrHeadTracking != null) vrHeadTracking.setEnabled(isVrEnabled);
        if (vrOsdScale != null) vrOsdScale.setEnabled(isVrEnabled);
    }

    @Override
    public void onDisplayPreferenceDialog(@NonNull Preference preference) {
        if (preference instanceof ColorPreference) {
            ((ColorPreference) preference).showDialog(this, 0);
        } else super.onDisplayPreferenceDialog(preference);
    }

    private void setListPreferenceSummary(ListPreference preference){
        if (preference != null){
            preference.setSummaryProvider((Preference.SummaryProvider<ListPreference>) ListPreference::getEntry);
        }
    }

    private void setEditTextPreferenceSummary(EditTextPreference preference){
        if (preference != null) {
            preference.setSummaryProvider((Preference.SummaryProvider<EditTextPreference>) EditTextPreference::getText);
            preference.setOnBindEditTextListener(editText -> {
                editText.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                editText.setMaxLines(1);
            });
        }
    }

    private void setNumericEditTextPreferenceSummary(EditTextPreference preference){
        if (preference != null) {
            preference.setSummaryProvider((Preference.SummaryProvider<EditTextPreference>) EditTextPreference::getText);
            preference.setOnBindEditTextListener(editText -> editText.setInputType(InputType.TYPE_CLASS_NUMBER));
        }
    }

    private void setNumericEditTextPreferenceSummary(EditTextPreference preference, final int min, final int max){
        if (preference != null) {
            preference.setSummaryProvider((Preference.SummaryProvider<EditTextPreference>) EditTextPreference::getText);
            preference.setOnBindEditTextListener(editText -> {
                editText.setInputType(InputType.TYPE_CLASS_NUMBER);
                editText.setOnEditorActionListener((v, actionId, event) -> {
                    if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE ||
                            event != null && event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                        if (event == null || !event.isShiftPressed()) {
                            int value = Utils.parseInt(v.getText().toString(), min);
                            if (value < min) v.setText(String.valueOf(min));
                            if (value > max) v.setText(String.valueOf(max));
                        }
                    }
                    return false;
                });
            });
        }
    }

    private void checkFsWarning(){
        Preference fsWarning = findPreference("fsWarning");
        if (fsWarning != null) fsWarning.setVisible(activity.isRunning);
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) checkFsWarning();
    }
}
