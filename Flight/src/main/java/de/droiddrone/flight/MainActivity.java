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

package de.droiddrone.flight;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import static de.droiddrone.common.Logcat.log;

import java.util.Timer;
import java.util.TimerTask;

import de.droiddrone.common.FcInfo;
import de.droiddrone.common.FcCommon;

public class MainActivity extends Activity {
    public static String versionName;
    public static int versionCode;
    private MainActivity activity;
    private Button bStartStopService;
    private LinearLayout main;
    Spinner connectionMode;
    EditText etIp, etPort, etKey;
    private TextView tvNetworkStatus, tvFcStatus;
    private Timer uiTimer;
    private boolean isPaused = false;
    public static Config config;

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);

        try {
            PackageInfo packageInfo = this.getPackageManager().getPackageInfo(this.getPackageName(), 0);
            versionName = packageInfo.versionName;
            versionCode = packageInfo.versionCode;
        }catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);
        activity = this;
        main = findViewById(R.id.llMain);
        main.setKeepScreenOn(true);
        bStartStopService = findViewById(R.id.startStopService);
        bStartStopService.setOnClickListener(v -> {
            if (checkPermissions()) startStopService();
        });
        etIp = findViewById(R.id.editText_ip);
        etPort = findViewById(R.id.editText_port);
        etKey = findViewById(R.id.editText_key);
        tvNetworkStatus = findViewById(R.id.tvNetworkConnectionStatus);
        tvFcStatus = findViewById(R.id.tvFcConnectionStatus);
        connectionMode = findViewById(R.id.connectionMode);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.connection_modes, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        connectionMode.setAdapter(adapter);
        connectionMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0){
                    etIp.setVisibility(View.VISIBLE);
                }else{
                    etIp.setVisibility(View.GONE);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
        config = new Config(activity, versionCode);
        etIp.setText(config.getIp());
        etPort.setText(String.valueOf(config.getPort()));
        etKey.setText(config.getKey());
        connectionMode.setSelection(config.getConnectionMode());
        TextView tvVersion = findViewById(R.id.tvVersion);
        tvVersion.setText(getResources().getString(R.string.version, MainActivity.versionName));
    }

    private void updateUi(){
        if (DDService.isRunning){
            bStartStopService.setText(getResources().getString(R.string.disconnect));
            if (DDService.isConnected){
                tvNetworkStatus.setText(getResources().getString(R.string.status_connected));
                tvNetworkStatus.setTextColor(Color.GREEN);
            }else{
                if (config.getConnectionMode() == 0) {
                    tvNetworkStatus.setText(getResources().getString(R.string.status_connecting));
                }else{
                    tvNetworkStatus.setText(getResources().getString(R.string.status_awaiting_connection));
                }
                tvNetworkStatus.setTextColor(Color.BLACK);
            }
            int serialPortStatus = DDService.getSerialPortStatus();
            switch (serialPortStatus){
                case Serial.STATUS_NOT_INITIALIZED:
                case Serial.STATUS_DEVICE_NOT_CONNECTED:
                    tvFcStatus.setText(getResources().getString(R.string.status_disconnected));
                    tvFcStatus.setTextColor(Color.RED);
                    break;
                case Serial.STATUS_DEVICE_FOUND:
                    tvFcStatus.setText(getResources().getString(R.string.fc_status_device_found));
                    tvFcStatus.setTextColor(Color.BLUE);
                    break;
                case Serial.STATUS_USB_PERMISSION_REQUESTED:
                    tvFcStatus.setText(getResources().getString(R.string.fc_status_permission_requested));
                    tvFcStatus.setTextColor(Color.BLUE);
                    break;
                case Serial.STATUS_USB_PERMISSION_DENIED:
                    tvFcStatus.setText(getResources().getString(R.string.fc_status_permission_denied));
                    tvFcStatus.setTextColor(Color.RED);
                    break;
                case Serial.STATUS_USB_PERMISSION_GRANTED:
                    tvFcStatus.setText(getResources().getString(R.string.fc_status_permission_granted));
                    tvFcStatus.setTextColor(Color.BLUE);
                    break;
                case Serial.STATUS_SERIAL_PORT_ERROR:
                    tvFcStatus.setText(getResources().getString(R.string.fc_status_serial_port_error));
                    tvFcStatus.setTextColor(Color.RED);
                    break;
                case Serial.STATUS_SERIAL_PORT_OPENED:
                    tvFcStatus.setTextColor(Color.BLUE);
                    String status = getResources().getString(R.string.fc_status_serial_port_opened);
                    FcInfo fcInfo = DDService.getFcInfo();
                    if (fcInfo == null){
                        status += getResources().getString(R.string.check_fc_version);
                    }else{
                        tvFcStatus.setTextColor(Color.GREEN);
                        status += " " + fcInfo.getFcName() + " Ver. " + fcInfo.getFcVersionStr() + getResources().getString(R.string.detected);
                        int mspApiCompatibilityLevel = DDService.getMspApiCompatibilityLevel();
                        switch (mspApiCompatibilityLevel){
                            case FcCommon.MSP_API_COMPATIBILITY_UNKNOWN:
                            case FcCommon.MSP_API_COMPATIBILITY_ERROR:
                                status += getResources().getString(R.string.msp_api_compatibility_error);
                                tvFcStatus.setTextColor(Color.RED);
                                break;
                            case FcCommon.MSP_API_COMPATIBILITY_WARNING:
                                status += getResources().getString(R.string.msp_api_compatibility_warning);
                                tvFcStatus.setTextColor(Color.YELLOW);
                                break;
                        }
                    }
                    tvFcStatus.setText(status);
                    break;
            }
            connectionMode.setEnabled(false);
            etIp.setEnabled(false);
            etPort.setEnabled(false);
            etKey.setEnabled(false);
        }else{
            bStartStopService.setText(getResources().getString(R.string.connect));
            tvNetworkStatus.setText(getResources().getString(R.string.status_disconnected));
            tvNetworkStatus.setTextColor(Color.RED);
            tvFcStatus.setText(getResources().getString(R.string.status_disconnected));
            tvFcStatus.setTextColor(Color.RED);
            connectionMode.setEnabled(true);
            etIp.setEnabled(true);
            etPort.setEnabled(true);
            etKey.setEnabled(true);
        }
    }

    private void startStopService(){
        Intent intent = new Intent(getApplicationContext(), DDService.class);
        if (DDService.isRunning){
            stopService(intent);
        }else{
            if (!config.updateConfig()) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                getApplicationContext().startForegroundService(intent);
            }else{
                getApplicationContext().startService(intent);
            }
        }
        updateUi();
    }

    private boolean checkPermissions(){
        boolean cameraPermission = isCameraPermissionGranted();
        boolean storagePermission = isStoragePermissionGranted();
        boolean audioPermission = isAudioPermissionGranted();
        return cameraPermission && storagePermission && audioPermission;
    }

    private boolean isCameraPermissionGranted(){
        if (this.checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){
            activity.requestPermission(Manifest.permission.CAMERA, 0);
            return false;
        }
        return true;
    }

    private boolean isStoragePermissionGranted() {
        if (Build.VERSION.SDK_INT <= 28) {
            if (this.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
                requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, 1);
                return false;
            }
        }
        return true;
    }

    private boolean isAudioPermissionGranted(){
        if (this.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED){
            activity.requestPermission(Manifest.permission.RECORD_AUDIO, 2);
            return false;
        }
        return true;
    }

    @Override
    protected void onResume(){
        super.onResume();
        isPaused = false;
        main.setKeepScreenOn(true);
        updateUi();
        uiTimer = new Timer();
        TimerTask tt = new TimerTask() {
            @Override
            public void run() {
                if (isPaused){
                    uiTimer.cancel();
                    return;
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateUi();
                    }
                });
            }
        };
        uiTimer.schedule(tt, 10, 1000);
    }

    @Override
    protected void onPause(){
        super.onPause();
        isPaused = true;
        if (uiTimer != null) {
            uiTimer.cancel();
            uiTimer.purge();
        }
    }

    @Override
    protected void onStart(){
        super.onStart();
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
    }

    void requestPermission(String permissionName, int permissionRequestCode) {
        ActivityCompat.requestPermissions(this, new String[]{permissionName}, permissionRequestCode);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (requestCode >= 0 && requestCode <= 4 && checkPermissions()) startStopService();
        } else {
            if (requestCode == 0) log("Camera permission denied!");
            if (requestCode == 1) log("Storage permission denied!");
            if (requestCode == 2) log("Audio permission denied!");
        }
    }
}
