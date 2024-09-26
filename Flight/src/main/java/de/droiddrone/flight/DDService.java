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

import static de.droiddrone.common.Logcat.log;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import java.util.Timer;
import java.util.TimerTask;

import de.droiddrone.common.FcInfo;
import de.droiddrone.common.MediaCommon;
import de.droiddrone.common.FcCommon;
import de.droiddrone.common.SettingsCommon;

public class DDService extends Service {
    public static final String CHANNEL_ID = "DDServiceChannel";
    public static boolean isRunning = false;
    public static boolean isConnected = false;
    private static int serialPortStatus = Serial.STATUS_NOT_INITIALIZED;
    private static FcInfo fcInfo = null;
    private static int fcApiCompatibilityLevel = FcCommon.FC_API_COMPATIBILITY_UNKNOWN;
    private Timer mainTimer;
    private Udp udp;
    private CameraManager cameraManager;
    private StreamEncoder streamEncoder;
    private Mp4Recorder mp4Recorder;
    private AudioSource audioSource;
    private Serial serial;
    private Msp msp;
    private Mavlink mavlink;
    private PhoneTelemetry phoneTelemetry;
    private int connectionMode;


    public DDService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        isConnected = false;
        String destIp = MainActivity.config.getIp();
        int port = MainActivity.config.getPort();
        String key = MainActivity.config.getKey();
        connectionMode = MainActivity.config.getConnectionMode();

        createNotificationChannel();
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getResources().getString(R.string.app_name))
                .setContentText("DroidDrone is running")
                .setSmallIcon(R.drawable.baseline_rocket_launch_24)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 30) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                    | ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    | ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        }else{
            startForeground(1, notification);
        }

        if (cameraManager == null) cameraManager = new CameraManager(this, MainActivity.config);
        if (audioSource == null) audioSource = new AudioSource(this);
        if (MainActivity.config.isRecordAudio()) audioSource.initialize(MediaCommon.mp4AudioConsumerId);
        if (MainActivity.config.isSendAudioStream()) audioSource.initialize(MediaCommon.streamAudioConsumerId);
        if (streamEncoder == null) streamEncoder = new StreamEncoder(cameraManager, audioSource, MainActivity.config);
        if (mp4Recorder == null) mp4Recorder = new Mp4Recorder(cameraManager, this, audioSource, MainActivity.config);
        this.getExternalMediaDirs();
        if (serial != null) serial.close();
        serial = new Serial(this, MainActivity.config);
        msp = new Msp(serial, MainActivity.config);
        mavlink = new Mavlink(serial, MainActivity.config);
        serial.initialize(msp, mavlink);
        phoneTelemetry = new PhoneTelemetry(this, streamEncoder, cameraManager, mp4Recorder);
        phoneTelemetry.initialize();
        if (udp != null) udp.close();
        udp = new Udp(destIp, port, key, connectionMode, streamEncoder, mp4Recorder, cameraManager, msp, mavlink, phoneTelemetry, MainActivity.config, serial);
        Thread t1 = new Thread(() -> {
            if (!udp.initialize()){
                log("UDP initialize error.");
                stopSelf();
            }
        });
        t1.start();
        isRunning = true;
        startMainTimer();
        return START_REDELIVER_INTENT;
    }

    @Override
    public void onDestroy() {
        closeAll();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(CHANNEL_ID, "DD", NotificationManager.IMPORTANCE_HIGH);
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }

    public static int getSerialPortStatus(){
        return serialPortStatus;
    }

    public static FcInfo getFcInfo(){
        return fcInfo;
    }

    public static int getFcApiCompatibilityLevel(){
        return fcApiCompatibilityLevel;
    }

    private void startMainTimer() {
        if (mainTimer != null) {
            mainTimer.cancel();
            mainTimer.purge();
        }
        mainTimer = new Timer();
        TimerTask tt = new TimerTask() {
            @Override
            public void run() {
                if (!isRunning){
                    mainTimer.cancel();
                    return;
                }
                isConnected = udp.isConnected();
                if (!isConnected && connectionMode == SettingsCommon.ConnectionMode.overServer) udp.sendConnect();
                serialPortStatus = serial.getStatus();
                if (serialPortStatus == Serial.STATUS_SERIAL_PORT_ERROR){
                    if (serial != null && (msp != null && mavlink != null)) {
                        fcInfo = null;
                        serial.initialize(msp, mavlink);
                    }
                }
                if (serial != null && fcInfo == null) {
                    if (serial.isMavlink()){
                        if (mavlink != null) fcInfo = mavlink.getFcInfo();
                    } else {
                        if (msp != null) fcInfo = msp.getFcInfo();
                    }
                    fcApiCompatibilityLevel = FcCommon.getFcApiCompatibilityLevel(fcInfo);
                }
            }
        };
        mainTimer.schedule(tt, 1000, 1000);
    }

    private void closeAll() {
        isRunning = false;
        try {
            if (mainTimer != null) {
                mainTimer.cancel();
                mainTimer.purge();
            }
        }catch (Exception e){
            //
        }
        if (phoneTelemetry != null) phoneTelemetry.close();
        if (serial != null) serial.close();
        if (udp != null){
            udp.disconnect();
        }
        if (streamEncoder != null) streamEncoder.close();
        if (mp4Recorder != null) mp4Recorder.close();
        if (audioSource != null) audioSource.close();
        if (cameraManager != null) cameraManager.close();
        isConnected = false;
        fcInfo = null;
        System.gc();
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}