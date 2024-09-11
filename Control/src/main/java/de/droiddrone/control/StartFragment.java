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

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class StartFragment extends Fragment {
    public static final int fragmentId = 1;
    private final MainActivity activity;
    private final Config config;
    EditText etIp, etPort, etKey;
    private TextView tvNetworkStatus, tvControllerStatus;
    private Button bConnectDisconnect, bShowGl, bSettings, bMap;
    CheckBox isViewer;

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bConnectDisconnect = view.findViewById(R.id.bConnectDisconnect);
        bConnectDisconnect.setOnClickListener(v -> {
            activity.runConnectDisconnect();
            updateUi();
        });
        bShowGl = view.findViewById(R.id.bShowGl);
        bShowGl.setOnClickListener(v -> {
            if (!activity.isConnected()) return;
            activity.showGlFragment(false);
        });
        bMap = view.findViewById(R.id.bMap);
        bMap.setOnClickListener(v -> {
            activity.showMapFragment();
        });
        bSettings = view.findViewById(R.id.bSettings);
        bSettings.setOnClickListener(v -> {
            activity.showSettingsFragment();
        });
        etIp = view.findViewById(R.id.editText_ip);
        etPort = view.findViewById(R.id.editText_port);
        etKey = view.findViewById(R.id.editText_key);
        isViewer = view.findViewById(R.id.isViewer);
        tvNetworkStatus = view.findViewById(R.id.tvNetworkStatus);
        tvControllerStatus = view.findViewById(R.id.tvControllerStatus);
        etIp.setText(config.getIp());
        etPort.setText(String.valueOf(config.getPort()));
        etKey.setText(config.getKey());
        isViewer.setChecked(config.isViewer());
        TextView tvVersion = view.findViewById(R.id.tvVersion);
        tvVersion.setText(getResources().getString(R.string.version, MainActivity.versionName));
    }

    public StartFragment(MainActivity activity, Config config) {
        super(R.layout.fragment_start);
        this.activity = activity;
        this.config = config;
    }

    public void updateUi(){
        if (activity.isRunning){
            bConnectDisconnect.setText(getResources().getString(R.string.disconnect));
            bSettings.setEnabled(false);
            if (activity.isConnected()) {
                bShowGl.setEnabled(true);
                if (activity.isConfigReceived() || config.isViewer()) {
                    if (activity.isVideoStreamStarted()) {
                        tvNetworkStatus.setText(getResources().getString(R.string.status_connected));
                        tvNetworkStatus.setTextColor(Color.GREEN);
                    } else {
                        tvNetworkStatus.setText(getResources().getString(R.string.status_connected_no_video));
                        tvNetworkStatus.setTextColor(Color.BLUE);
                    }
                } else {
                    tvNetworkStatus.setText(getResources().getString(R.string.status_connected_send_config));
                    tvNetworkStatus.setTextColor(Color.BLUE);
                }
            }else{
                bShowGl.setEnabled(false);
                tvNetworkStatus.setText(getResources().getString(R.string.status_connecting));
                tvNetworkStatus.setTextColor(Color.BLACK);
            }
            etIp.setEnabled(false);
            etPort.setEnabled(false);
            etKey.setEnabled(false);
            isViewer.setEnabled(false);
        }else{
            bConnectDisconnect.setText(getResources().getString(R.string.connect));
            tvNetworkStatus.setText(getResources().getString(R.string.status_disconnected));
            tvNetworkStatus.setTextColor(Color.RED);
            etIp.setEnabled(true);
            etPort.setEnabled(true);
            etKey.setEnabled(true);
            bShowGl.setEnabled(false);
            bSettings.setEnabled(true);
            isViewer.setEnabled(true);
        }
        activity.updateControllerStatusUi(tvControllerStatus);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateUi();
    }

    @Override
    public void onPause() {
        super.onPause();
    }
}