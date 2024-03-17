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
    private Button bConnectDisconnect, bShowGl, bSettings;
    CheckBox isViewer;

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bConnectDisconnect = activity.findViewById(R.id.bConnectDisconnect);
        bConnectDisconnect.setOnClickListener(v -> {
            activity.runConnectDisconnect();
            updateUi();
        });
        bShowGl = activity.findViewById(R.id.bShowGl);
        bShowGl.setOnClickListener(v -> {
            if (!activity.isConnected()) return;
            activity.showGlFragment(false);
        });
        bSettings = activity.findViewById(R.id.bSettings);
        bSettings.setOnClickListener(v -> {
            activity.showSettingsFragment();
        });
        etIp = activity.findViewById(R.id.editText_ip);
        etPort = activity.findViewById(R.id.editText_port);
        etKey = activity.findViewById(R.id.editText_key);
        isViewer = activity.findViewById(R.id.isViewer);
        tvNetworkStatus = activity.findViewById(R.id.tvNetworkStatus);
        tvControllerStatus = activity.findViewById(R.id.tvControllerStatus);
        etIp.setText(config.getIp());
        etPort.setText(String.valueOf(config.getPort()));
        etKey.setText(config.getKey());
        isViewer.setChecked(config.isViewer());
        TextView tvVersion = activity.findViewById(R.id.tvVersion);
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