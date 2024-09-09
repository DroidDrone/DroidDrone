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

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.BatteryManager;
import android.os.Bundle;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

import static de.droiddrone.common.Logcat.log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentManager;

import de.droiddrone.common.TelephonyService;


public class MainActivity extends AppCompatActivity {
    public static String versionName;
    public static int versionCode;
    private Timer mainTimer;
    private Udp udp;
    private Decoder decoder;
    private GlRenderer renderer;
    private Rc rc;
    boolean isRunning;
    private Osd osd;
    private MainActivity activity;
    private BatteryManager batteryManager;
    private Config config;
    private boolean connectionThreadRunning = false;
    private TelephonyService telephonyService;
    private boolean phoneStatePermissionRequested = false;
    public static final int fullScreenFlags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
    public static final int showNavigationFlags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_FULLSCREEN;
    private CustomFragmentFactory customFragmentFactory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            PackageInfo packageInfo = this.getPackageManager().getPackageInfo(this.getPackageName(), 0);
            versionName = packageInfo.versionName;
            versionCode = packageInfo.versionCode;
        }catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        isRunning = false;

        activity = this;
        config = new Config(this, versionCode);
        telephonyService = new TelephonyService(this);
        renderer = new GlRenderer(this, config);
        rc = new Rc(config);

        FragmentManager fm = getSupportFragmentManager();
        customFragmentFactory = new CustomFragmentFactory(fm, this, config, rc, renderer);
        fm.setFragmentFactory(customFragmentFactory);
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_main);

        showStartFragment();
        startMainTimer();

        try {
            batteryManager = (BatteryManager) this.getSystemService(Context.BATTERY_SERVICE);
        }catch (Exception e){
            log("BatteryManager error: " + e);
        }
    }

    @Override
    protected void onResume(){
        super.onResume();
        startMainTimer();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onPause(){
        stopMainTimer();
        super.onPause();
    }

    public GlRenderer getRenderer(){
        return renderer;
    }

    public void showStartFragment(){
        customFragmentFactory.showStartFragment();
    }

    public void showGlFragment(boolean runInThread){
        if (runInThread){
            runOnUiThread(() -> customFragmentFactory.showGlFragment());
        }else{
            customFragmentFactory.showGlFragment();
        }
    }

    public void showSettingsFragment(){
        customFragmentFactory.showSettingsFragment();
    }

    public void showChannelsMappingFragment(){
        customFragmentFactory.showChannelsMappingFragment();
    }

    public void runConnectDisconnect(){
        if (isRunning){
            getWindow().getDecorView().setSystemUiVisibility(showNavigationFlags);
            closeAll();
        }else{
            if (!config.updateConfig()) return;
            getWindow().getDecorView().setSystemUiVisibility(fullScreenFlags);
            isRunning = true;
            GlFragment glFragment = customFragmentFactory.getGlFragment();
            if (glFragment != null) glFragment.resume();
            if (decoder == null) decoder = new Decoder(renderer);
            osd = new Osd(renderer, config);
            renderer.setOsd(osd);
            if (udp != null) udp.close();
            udp = new Udp(config, decoder, osd, rc, activity);
            Thread t1 = new Thread(() -> {
                if (udp.initialize()){
                    startConnectionThread();
                }else{
                    closeAll();
                }
            });
            t1.start();
            renderer.setUdp(udp);
        }
    }

    public StartFragment getStartFragment(){
        return customFragmentFactory.getStartFragment();
    }

    private void startConnectionThread(){
        if (connectionThreadRunning) return;
        Thread connectionThread = new Thread(() -> {
            connectionThreadRunning = true;
            while (isRunning){
                try {
                    if (udp.isVersionMismatch()){
                        String msg = getResources().getString(R.string.version_mismatch);
                        log(msg);
                        runOnUiThread(() -> Toast.makeText(activity, msg, Toast.LENGTH_LONG).show());
                        showStartFragment();
                        closeAll();
                        break;
                    }
                    if (!isConnected()){
                        udp.sendConnect();
                        Thread.sleep(500);
                    }else if (!udp.isConfigReceived() && !config.isViewer()){
                        udp.sendConfig();
                        Thread.sleep(500);
                    }else if (!udp.isVideoInitialFrameReceived()){
                        udp.startVideoStream();
                        Thread.sleep(2500);
                    }else if (!osd.isInitialized() || osd.getOsdConfig() == null || !osd.isHasBoxIds() || !osd.isHasBatteryConfig()) {
                        if (!osd.isInitialized()) udp.sendGetFcInfo();
                        if (osd.getOsdConfig() == null) udp.sendGetOsdConfig();
                        if (!osd.isHasBoxIds()) udp.sendGetBoxIds();
                        if (!osd.isHasBatteryConfig()) udp.sendGetBatteryConfig();
                        Thread.sleep(1000);
                    }else{
                        log("Connection thread finished.");
                        break;
                    }
                }catch (Exception e) {
                    log("connectionThread error: " + e);
                }
            }
            connectionThreadRunning = false;
        });
        connectionThread.setDaemon(false);
        connectionThread.setName("connectionThread");
        connectionThread.start();
    }

    public boolean isVideoStreamStarted(){
        return (decoder != null && decoder.isVideoDecoderStarted());
    }

    public boolean isConfigReceived(){
        return udp != null && udp.isConfigReceived();
    }

    public boolean isControllerConnected(){
        return rc != null && rc.isActive();
    }

    public void updateControllerStatusUi(TextView controllerStatus){
        if (controllerStatus == null) return;
        if (isControllerConnected()){
            int channels = getControllerChannelCount();
            String status = getResources().getString(R.string.status_connected) + ". "
                    + getResources().getString(R.string.channels_detected) + channels;
            if (channels < 8){
                controllerStatus.setTextColor(Color.BLACK);
                status += ". " + getResources().getString(R.string.move_sticks_note);
            }else{
                controllerStatus.setTextColor(Color.GREEN);
            }
            controllerStatus.setText(status);
        }else{
            controllerStatus.setText(getResources().getString(R.string.status_disconnected));
            controllerStatus.setTextColor(Color.RED);
        }
    }

    public int getControllerChannelCount(){
        return rc.getControllerChannelCount();
    }

    public boolean isConnected(){
        return (udp != null && udp.isConnected());
    }

    private void updateBatteryState(){
        if (batteryManager == null) return;
        try {
            int batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            if (batteryLevel < 0) return;
            boolean isCharging = batteryManager.isCharging();
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                int status = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS);
                isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING);
            }
            osd.setControlPhoneBatteryState((byte) batteryLevel, isCharging);
        }catch (Exception e){
            log("updateBatteryState error: " + e);
        }
    }

    private void updateNetworkState(){
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.READ_BASIC_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                if (!phoneStatePermissionRequested) {
                    phoneStatePermissionRequested = true;
                    ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.READ_BASIC_PHONE_STATE}, 3);
                }
                return;
            }
        }
        osd.setControlNetworkState(telephonyService.getNetworkState());
    }

    private void startMainTimer() {
        if (mainTimer != null) return;
        mainTimer = new Timer();
        TimerTask tt = new TimerTask() {
            @Override
            public void run() {
                if (isRunning && !isConnected()) startConnectionThread();
                if (osd != null){
                    osd.setGlFps((short) renderer.getFps());
                    updateBatteryState();
                    updateNetworkState();
                }
                runOnUiThread(() -> {
                    StartFragment startFragment = customFragmentFactory.getStartFragment();
                    if (startFragment != null) startFragment.updateUi();
                    if (customFragmentFactory.getCurrentFragmentId() == ChannelsMappingFragment.fragmentId) {
                        ChannelsMappingFragment channelsMappingFragment = customFragmentFactory.getChannelsMappingFragment();
                        if (channelsMappingFragment != null) channelsMappingFragment.updateStatus();
                    }
                });
            }
        };
        mainTimer.schedule(tt, 1000, 1000);
    }

    private void stopMainTimer(){
        if (mainTimer != null){
            mainTimer.cancel();
            mainTimer.purge();
            mainTimer = null;
        }
    }

    @Override
    protected void onStart(){
        super.onStart();
    }

    @Override
    protected void onDestroy(){
        stopMainTimer();
        closeAll();
        super.onDestroy();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int source = event.getSource();
        int action = event.getAction();
        int code = event.getKeyCode();
        ChannelsMappingFragment channelsMappingFragment = null;
        if ((source & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD || (source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK){
            if (customFragmentFactory.getCurrentFragmentId() == ChannelsMappingFragment.fragmentId) {
                channelsMappingFragment = customFragmentFactory.getChannelsMappingFragment();
            }
            switch (action){
                case KeyEvent.ACTION_DOWN:
                    rc.setKeyValue(code, true);
                    if (channelsMappingFragment != null) channelsMappingFragment.updateChannelsLevels();
                    break;
                case KeyEvent.ACTION_UP:
                    rc.setKeyValue(code, false);
                    if (channelsMappingFragment != null) channelsMappingFragment.updateChannelsLevels();
                    break;
            }
            return true;
        }else{
            return super.dispatchKeyEvent(event);
        }
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        int source = event.getSource();
        int action = event.getAction();
        ChannelsMappingFragment channelsMappingFragment = null;
        if ((source & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD || (source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {
            if (action == MotionEvent.ACTION_MOVE) {
                if (customFragmentFactory.getCurrentFragmentId() == ChannelsMappingFragment.fragmentId) {
                    channelsMappingFragment = customFragmentFactory.getChannelsMappingFragment();
                }
                HashMap<Integer, Float> axisValues = new HashMap<>();
                float axisX = event.getAxisValue(MotionEvent.AXIS_X);
                axisValues.put(MotionEvent.AXIS_X, axisX);
                float axisY = event.getAxisValue(MotionEvent.AXIS_Y);
                axisValues.put(MotionEvent.AXIS_Y, axisY);
                float axisRX = event.getAxisValue(MotionEvent.AXIS_RX);
                axisValues.put(MotionEvent.AXIS_RX, axisRX);
                float axisRY = event.getAxisValue(MotionEvent.AXIS_RY);
                axisValues.put(MotionEvent.AXIS_RY, axisRY);
                float axisZ = event.getAxisValue(MotionEvent.AXIS_Z);
                axisValues.put(MotionEvent.AXIS_Z, axisZ);
                float axisRZ = event.getAxisValue(MotionEvent.AXIS_RZ);
                axisValues.put(MotionEvent.AXIS_RZ, axisRZ);
                float axisHX = event.getAxisValue(MotionEvent.AXIS_HAT_X);
                axisValues.put(MotionEvent.AXIS_HAT_X, axisHX);
                float axisHY = event.getAxisValue(MotionEvent.AXIS_HAT_Y);
                axisValues.put(MotionEvent.AXIS_HAT_Y, axisHY);
                float axisBrake = event.getAxisValue(MotionEvent.AXIS_BRAKE);
                axisValues.put(MotionEvent.AXIS_BRAKE, axisBrake);
                float axisGas = event.getAxisValue(MotionEvent.AXIS_GAS);
                axisValues.put(MotionEvent.AXIS_GAS, axisGas);
                float axisHScroll = event.getAxisValue(MotionEvent.AXIS_HSCROLL);
                axisValues.put(MotionEvent.AXIS_HSCROLL, axisHScroll);
                float axisLTrigger = event.getAxisValue(MotionEvent.AXIS_LTRIGGER);
                axisValues.put(MotionEvent.AXIS_LTRIGGER, axisLTrigger);
                float axisRTrigger = event.getAxisValue(MotionEvent.AXIS_RTRIGGER);
                axisValues.put(MotionEvent.AXIS_RTRIGGER, axisRTrigger);
                float axisRudder = event.getAxisValue(MotionEvent.AXIS_RUDDER);
                axisValues.put(MotionEvent.AXIS_RUDDER, axisRudder);
                float axisThrottle = event.getAxisValue(MotionEvent.AXIS_THROTTLE);
                axisValues.put(MotionEvent.AXIS_THROTTLE, axisThrottle);
                rc.setAxisValues(axisValues);
                if (channelsMappingFragment != null) channelsMappingFragment.updateChannelsLevels();
            }else{
                if (action == MotionEvent.ACTION_CANCEL) rc.setState(false);
            }
            return true;
        }else{
            return super.onGenericMotionEvent(event);
        }
    }

    private void closeAll(){
        isRunning = false;
        if (udp != null) {
            udp.disconnect();
        }
        if (decoder != null) decoder.close();
        if (renderer != null) renderer.close();
        GlFragment glFragment = customFragmentFactory.getGlFragment();
        if (glFragment != null) glFragment.close();
        System.gc();
    }
}
