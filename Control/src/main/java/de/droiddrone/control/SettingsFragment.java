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
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import com.rarepebble.colorpicker.ColorPreference;

import de.droiddrone.common.Logcat;

public class SettingsFragment extends PreferenceFragmentCompat {
    public static final int fragmentId = 3;
    private final MainActivity activity;

    public SettingsFragment(MainActivity activity){
        this.activity = activity;
    }

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);
        EditTextPreference cameraId = findPreference("cameraId");
        SwitchPreferenceCompat useUsbCamera = findPreference("useUsbCamera");
        if (cameraId != null) {
            setNumericEditTextPreferenceSummary(cameraId);
            cameraId.setEnabled(useUsbCamera == null || !useUsbCamera.isChecked());
        }
        if (useUsbCamera != null) {
            useUsbCamera.setOnPreferenceChangeListener((preference, newValue) -> {
                if (cameraId != null) cameraId.setEnabled(Boolean.FALSE.equals(newValue));
                return true;
            });
        }
        setNumericEditTextPreferenceSummary(findPreference("serialPortIndex"));
        setNumericEditTextPreferenceSummary(findPreference("mavlinkTargetSysId"));
        setNumericEditTextPreferenceSummary(findPreference("mavlinkGcsSysId"));
        setListPreferenceSummary(findPreference("usbCameraFrameFormat"));
        setListPreferenceSummary(findPreference("cameraResolution"));
        setListPreferenceSummary(findPreference("cameraFps"));
        setListPreferenceSummary(findPreference("bitrateLimit"));
        setListPreferenceSummary(findPreference("audioStreamBitrate"));
        setListPreferenceSummary(findPreference("recordedAudioBitrate"));
        setListPreferenceSummary(findPreference("recordedVideoBitrate"));
        setListPreferenceSummary(findPreference("telemetryRefreshRate"));
        setListPreferenceSummary(findPreference("rcRefreshRate"));
        setListPreferenceSummary(findPreference("serialBaudRate"));
        Preference channelsMapping = findPreference("channelsMapping");
        if (channelsMapping != null) {
            channelsMapping.setOnPreferenceClickListener(preference -> {
                activity.showChannelsMappingFragment();
                return true;
            });
        }
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

    private void setNumericEditTextPreferenceSummary(EditTextPreference preference){
        if (preference != null) {
            preference.setSummaryProvider((Preference.SummaryProvider<EditTextPreference>) EditTextPreference::getText);
            preference.setOnBindEditTextListener(editText -> editText.setInputType(InputType.TYPE_CLASS_NUMBER));
        }
    }
}
