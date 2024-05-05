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

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.io.IOException;
import java.util.List;

import de.droiddrone.common.FcInfo;
import de.droiddrone.common.FcCommon;

public class Serial {
    public static final String ACTION_USB_PERMISSION = "de.droiddrone.flight.USB_PERMISSION";
    public static final int STATUS_NOT_INITIALIZED = 0;
    public static final int STATUS_DEVICE_NOT_CONNECTED = 1;
    public static final int STATUS_DEVICE_FOUND = 2;
    public static final int STATUS_USB_PERMISSION_REQUESTED = 3;
    public static final int STATUS_USB_PERMISSION_DENIED = 4;
    public static final int STATUS_USB_PERMISSION_GRANTED = 5;
    public static final int STATUS_SERIAL_PORT_ERROR = 6;
    public static final int STATUS_SERIAL_PORT_OPENED = 7;
    private static final int serialDataBits = 8;
    private static final int serialPortReadWriteTimeoutMs = 100;
    private static final int serialMaxBufferSize = 4096 + 16;
    private int serialBaudRate;
    private int serialPortIndex;
    private final Context context;
    private final Config config;
    private final UsbManager manager;
    private Msp msp;
    private UsbSerialPort port;
    private UsbDeviceConnection connection;
    private UsbSerialDriver driver;
    private int threadsId;
    private Thread initThread, readerThread;
    private int status;

    public Serial(Context context, Config config){
        this.context = context;
        this.config = config;
        threadsId = 0;
        msp = null;
        manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        status = STATUS_NOT_INITIALIZED;
    }

    public void initialize() {
        serialBaudRate = config.getSerialBaudRate();
        serialPortIndex = config.getSerialPortIndex();
        threadsId++;
        initThread = new Thread(initRun);
        initThread.setDaemon(false);
        initThread.setName("serialInitThread");
        initThread.start();
    }

    public int getStatus(){
        return status;
    }

    private final Runnable initRun = new Runnable() {
        public void run() {
            final int id = threadsId;
            if (initThread == null) return;
            log("Start serial init thread - OK");
            status = STATUS_DEVICE_NOT_CONNECTED;
            while (id == threadsId) {
                try {
                    if (findDevice()) {
                        if (checkPermission()) {
                            if (openPort()) {
                                log("Serial port is opened");
                                return;
                            }
                        }
                    }
                    Thread.sleep(2000);
                } catch (Exception e) {
                    log("Serial init thread error: " + e);
                }
            }
        }
    };

    private boolean findDevice(){
        if (status != STATUS_DEVICE_NOT_CONNECTED && driver != null) return true;
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
        if (availableDrivers.isEmpty()) {
            status = STATUS_DEVICE_NOT_CONNECTED;
            return false;
        }
        int count = availableDrivers.size();
        for (int i = 0; i < count; i++) {
            String name = availableDrivers.get(i).getDevice().getManufacturerName();
            if (count == 1 || FcInfo.INAV_ID.equals(name) || FcInfo.BETAFLIGHT_ID.equals(name) || "Betaflight".equals(name)) {
                driver = availableDrivers.get(i);
                status = STATUS_DEVICE_FOUND;
                log("Serial device manufacturer name: " + name);
                log("Serial device driver version: " + driver.getDevice().getVersion());
                log("Serial device driver vendorId: " + driver.getDevice().getVendorId());
                log("Serial device driver productId: " + driver.getDevice().getProductId());
                return true;
            }
        }
        status = STATUS_DEVICE_NOT_CONNECTED;
        return false;
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    private boolean checkPermission() {
        if (status == STATUS_SERIAL_PORT_OPENED || status == STATUS_USB_PERMISSION_GRANTED) return true;
        if (manager.hasPermission(driver.getDevice())){
            status = STATUS_USB_PERMISSION_GRANTED;
            return true;
        }
        if (status == STATUS_USB_PERMISSION_REQUESTED) return false;
        PendingIntent intent;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            intent = PendingIntent.getBroadcast(context, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        }else{
            intent = PendingIntent.getBroadcast(context, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_UPDATE_CURRENT);
        }
        status = STATUS_USB_PERMISSION_REQUESTED;
        manager.requestPermission(driver.getDevice(), intent);
        return false;
    }

    public void setMsp(Msp msp){
        this.msp = msp;
    }

    private boolean openPort(){
        if (status == STATUS_SERIAL_PORT_OPENED) return true;
        if (driver == null){
            status = STATUS_DEVICE_NOT_CONNECTED;
            return false;
        }
        UsbDevice device = driver.getDevice();
        if (device == null){
            status = STATUS_DEVICE_NOT_CONNECTED;
            return false;
        }
        connection = manager.openDevice(device);
        if (connection == null) {
            status = STATUS_USB_PERMISSION_DENIED;
            return false;
        }
        port = driver.getPorts().get(serialPortIndex);
        try {
            port.open(connection);
            port.setParameters(serialBaudRate, serialDataBits, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
        } catch (IOException e) {
            status = STATUS_SERIAL_PORT_ERROR;
            log("USB Serial error: " + e);
            e.printStackTrace();
            return false;
        }
        readerThread = new Thread(readerRun);
        readerThread.setDaemon(false);
        readerThread.setName("readerThread");
        readerThread.setPriority(Thread.MAX_PRIORITY);
        readerThread.start();
        if (!msp.isInitialized()) msp.initialize();
        return true;
    }

    private final Runnable readerRun = new Runnable() {
        public void run() {
            final int id = threadsId;
            if (readerThread == null) return;
            byte[] buf = new byte[serialMaxBufferSize];
            status = STATUS_SERIAL_PORT_OPENED;
            log("Start serial reader thread - OK");
            while (id == threadsId) {
                try {
                    int size = port.read(buf, serialPortReadWriteTimeoutMs);
                    if (size > 0){
                        status = STATUS_SERIAL_PORT_OPENED;
                        msp.processData(buf, size);
                    }
                } catch (Exception e) {
                    status = STATUS_SERIAL_PORT_ERROR;
                    log("Serial reader thread error: " + e);
                    try {
                        Thread.sleep(serialPortReadWriteTimeoutMs);
                    } catch (InterruptedException ex) {
                        //
                    }
                }
            }
        }
    };

    public void writeData(byte[] data, boolean checkMspCompatibility){
        if (data == null || port == null || !port.isOpen()) return;
        if (checkMspCompatibility && msp.getMspApiCompatibilityLevel() != FcCommon.MSP_API_COMPATIBILITY_OK
                && msp.getMspApiCompatibilityLevel() != FcCommon.MSP_API_COMPATIBILITY_WARNING) return;
        try {
            port.write(data, serialPortReadWriteTimeoutMs);
        } catch (IOException e) {
            log("Serial writeData error: " + e);
        }
    }

    public void close(){
        threadsId++;
        if (msp != null) msp.close();
        try {
            if (port != null) port.close();
        } catch (IOException e) {
            //
        }
        if (connection != null) connection.close();
        status = STATUS_NOT_INITIALIZED;
    }
}
