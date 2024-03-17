package de.droiddrone.control;

import android.os.Bundle;
import android.text.InputType;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.rarepebble.colorpicker.ColorPreference;

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
        if (cameraId != null) {
            cameraId.setSummaryProvider((Preference.SummaryProvider<EditTextPreference>) EditTextPreference::getText);
            cameraId.setOnBindEditTextListener(editText -> editText.setInputType(InputType.TYPE_CLASS_NUMBER));
        }
        EditTextPreference serialPortIndex = findPreference("serialPortIndex");
        if (serialPortIndex != null) {
            serialPortIndex.setSummaryProvider((Preference.SummaryProvider<EditTextPreference>) EditTextPreference::getText);
            serialPortIndex.setOnBindEditTextListener(editText -> editText.setInputType(InputType.TYPE_CLASS_NUMBER));
        }
        setListPreferenceSummary(findPreference("cameraResolution"));
        setListPreferenceSummary(findPreference("cameraFps"));
        setListPreferenceSummary(findPreference("bitrateLimit"));
        setListPreferenceSummary(findPreference("audioStreamBitrate"));
        setListPreferenceSummary(findPreference("recordedAudioBitrate"));
        setListPreferenceSummary(findPreference("recordedVideoBitrate"));
        setListPreferenceSummary(findPreference("mspTelemetryRefreshRate"));
        setListPreferenceSummary(findPreference("mspRcRefreshRate"));
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
}
