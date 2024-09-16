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

import android_serialport_api.SerialPortFinder;
import de.droiddrone.common.FcInfo;
import de.droiddrone.common.FcCommon;
import tp.xmaihh.serialport.SerialHelper;
import tp.xmaihh.serialport.bean.ComBean;

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
    private static final int serialMaxBufferSize = 2048;
    // native serial
    private static final int nativeSerialParityNone = 0;
    private static final int nativeSerialParityOdd = 1;
    private static final int nativeSerialParityEven = 2;
    private static final int nativeSerialParitySpace = 3;
    private static final int nativeSerialParityMark = 4;
    private static final int nativeSerialFlowControlNone = 0;
    private static final int nativeSerialFlowControlRtsCts = 1;
    private static final int nativeSerialFlowControlXonXoff = 2;
    private final Context context;
    private final Config config;
    private final UsbManager manager;
    private Msp msp;
    private Mavlink mavlink;
    private UsbSerialPort port;
    private UsbDeviceConnection connection;
    private UsbSerialDriver driver;
    private int threadsId;
    private int status;
    private boolean isMavlink;
    private SerialHelper serialHelper;

    public Serial(Context context, Config config){
        this.context = context;
        this.config = config;
        threadsId = 0;
        msp = null;
        mavlink = null;
        manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        status = STATUS_NOT_INITIALIZED;
    }

    public void initialize(Msp msp, Mavlink mavlink) {
        this.msp = msp;
        this.mavlink = mavlink;
        isMavlink = false;
        msp.close();
        mavlink.close();
        threadsId++;
        Thread initThread = new Thread(initRun);
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
            log("Start serial init thread - OK");
            status = STATUS_DEVICE_NOT_CONNECTED;
            while (id == threadsId) {
                try {
                    if (config.isUseNativeSerialPort()){
                        if (openNativeSerialPort()) {
                            log("Native serial port is opened");
                            return;
                        }
                    }else {
                        if (findDevice()) {
                            if (checkPermission()) {
                                if (openUsbSerialPort()) {
                                    log("USB Serial port is opened");
                                    return;
                                }
                            }
                        }
                    }
                    Thread.sleep(2000);
                } catch (Exception e) {
                    log("Serial init thread error: " + e);
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ignored) {
                    }
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
            if (count == 1 || FcInfo.INAV_ID.equals(name) || FcInfo.BETAFLIGHT_ID.equals(name) || FcInfo.BETAFLIGHT_NAME.equals(name)
                    || FcInfo.ARDUPILOT_ID.equals(name) || FcInfo.ARDUPILOT_NAME.equals(name)) {
                driver = availableDrivers.get(i);
                status = STATUS_DEVICE_FOUND;
                setFcProtocol(name);
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

    private void setFcProtocol(String UsbDeviceManufacturerName){
        switch (config.getFcProtocol()){
            case FcCommon.FC_PROTOCOL_AUTO:
            default:
                isMavlink = FcInfo.ARDUPILOT_ID.equals(UsbDeviceManufacturerName) || FcInfo.ARDUPILOT_NAME.equals(UsbDeviceManufacturerName);
                break;
            case FcCommon.FC_PROTOCOL_MSP:
                isMavlink = false;
                break;
            case FcCommon.FC_PROTOCOL_MAVLINK:
                isMavlink = true;
                break;
        }
    }

    private boolean checkFcProtocol(){
        if (isMavlink){
            if (config.getFcProtocol() == FcCommon.FC_PROTOCOL_MSP){
                status = STATUS_SERIAL_PORT_ERROR;
                return false;
            }
        }else{
            if (config.getFcProtocol() == FcCommon.FC_PROTOCOL_MAVLINK){
                status = STATUS_SERIAL_PORT_ERROR;
                return false;
            }
        }
        return true;
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    private boolean checkPermission() {
        if (status == STATUS_SERIAL_PORT_OPENED || status == STATUS_USB_PERMISSION_GRANTED) return true;
        if (manager.hasPermission(driver.getDevice())){
            status = STATUS_USB_PERMISSION_GRANTED;
            return true;
        }
        if (status == STATUS_USB_PERMISSION_REQUESTED) return false;
        PendingIntent pendingIntent;
        Intent intent = new Intent(ACTION_USB_PERMISSION);
        intent.setPackage(context.getPackageName());
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        }else{
            pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_UPDATE_CURRENT);
        }
        status = STATUS_USB_PERMISSION_REQUESTED;
        manager.requestPermission(driver.getDevice(), pendingIntent);
        return false;
    }

    private boolean openNativeSerialPort(){
        if (status == STATUS_SERIAL_PORT_OPENED) return true;
        setFcProtocol(null);
        if (serialHelper != null) serialHelper.close();
        try {
            SerialPortFinder serialPortFinder = new SerialPortFinder();
            String[] ports = serialPortFinder.getAllDevicesPath();
            if (ports != null) {
                for (String port : ports) {
                    log("Native serial port found: " + port);
                }
            }
            serialHelper = new SerialHelper(config.getNativeSerialPort(), config.getSerialBaudRate()) {
                @Override
                protected void onDataReceived(ComBean comBean) {
                    try {
                        byte[] buf = comBean.bRec;
                        int size = buf.length;
                        if (!checkFcProtocol()) return;
                        if (size > 0){
                            status = STATUS_SERIAL_PORT_OPENED;
                            if (isMavlink){
                                mavlink.addData(buf, size);
                            } else {
                                msp.addData(buf, size);
                            }
                        }
                    } catch (Exception e) {
                        log("Native serial reader error: " + e);
                    }
                }
            };
            serialHelper.setStopBits(1);
            serialHelper.setDataBits(serialDataBits);
            serialHelper.setParity(nativeSerialParityNone);
            serialHelper.setFlowCon(nativeSerialFlowControlNone);
            serialHelper.open();
            if (isMavlink){
                if (!mavlink.isInitialized()) mavlink.initialize();
            } else {
                if (!msp.isInitialized()) msp.initialize();
            }
        }catch (Exception e){
            status = STATUS_SERIAL_PORT_ERROR;
            log("Native serial port error: " + e);
            return false;
        }
        status = STATUS_SERIAL_PORT_OPENED;
        return true;
    }

    private boolean openUsbSerialPort(){
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
        port = driver.getPorts().get(config.getUsbSerialPortIndex());
        try {
            port.open(connection);
            port.setParameters(config.getSerialBaudRate(), serialDataBits, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
        } catch (IOException e) {
            status = STATUS_SERIAL_PORT_ERROR;
            log("USB Serial error: " + e);
            e.printStackTrace();
            return false;
        }
        Thread readerThread = new Thread(readerRun);
        readerThread.setDaemon(false);
        readerThread.setName("readerThread");
        readerThread.setPriority(Thread.MAX_PRIORITY);
        readerThread.start();
        if (isMavlink){
            if (!mavlink.isInitialized()) mavlink.initialize();
        } else {
            if (!msp.isInitialized()) msp.initialize();
        }
        return true;
    }

    private final Runnable readerRun = new Runnable() {
        public void run() {
            final int id = threadsId;
            int serialErrors = 0;
            byte[] buf = new byte[serialMaxBufferSize];
            status = STATUS_SERIAL_PORT_OPENED;
            log("Start USB serial reader thread - OK");
            while (id == threadsId) {
                try {
                    int size = port.read(buf, serialPortReadWriteTimeoutMs);
                    if (!checkFcProtocol()) continue;
                    if (size > 0){
                        serialErrors = 0;
                        status = STATUS_SERIAL_PORT_OPENED;
                        if (isMavlink){
                            mavlink.addData(buf, size);
                        } else {
                            msp.addData(buf, size);
                        }
                    }
                } catch (Exception e) {
                    serialErrors++;
                    if (serialErrors > 10) status = STATUS_SERIAL_PORT_ERROR;
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

    public void writeDataMsp(byte[] data, boolean checkMspCompatibility){
        if (status != STATUS_SERIAL_PORT_OPENED || data == null) return;
        if (checkMspCompatibility && FcCommon.getFcApiCompatibilityLevel(msp.getFcInfo()) != FcCommon.FC_API_COMPATIBILITY_OK
                && FcCommon.getFcApiCompatibilityLevel(msp.getFcInfo()) != FcCommon.FC_API_COMPATIBILITY_WARNING) return;
        try {
            if (config.isUseNativeSerialPort()){
                if (serialHelper != null && serialHelper.isOpen()) serialHelper.send(data);
            }else{
                if (port != null && port.isOpen()) port.write(data, serialPortReadWriteTimeoutMs);
            }
        } catch (IOException e) {
            log("Serial writeDataMsp error: " + e);
        }
    }

    public void writeDataMavlink(byte[] data){
        if (status != STATUS_SERIAL_PORT_OPENED || data == null) return;
        try {
            if (config.isUseNativeSerialPort()){
                if (serialHelper != null && serialHelper.isOpen()) serialHelper.send(data);
            }else{
                if (port != null && port.isOpen()) port.write(data, serialPortReadWriteTimeoutMs);
            }
        } catch (IOException e) {
            log("Serial writeDataMavlink error: " + e);
        }
    }

    public boolean isMavlink(){
        return isMavlink;
    }

    public void close(){
        threadsId++;
        if (msp != null) msp.close();
        if (mavlink != null) mavlink.close();
        try {
            if (port != null) port.close();
        } catch (IOException e) {
            //
        }
        try {
            if (serialHelper != null) serialHelper.close();
        } catch (Exception e) {
            //
        }
        if (connection != null) connection.close();
        status = STATUS_NOT_INITIALIZED;
    }
}
