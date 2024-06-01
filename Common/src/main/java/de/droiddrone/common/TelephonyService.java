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

package de.droiddrone.common;

import static de.droiddrone.common.Logcat.log;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.telephony.CellInfo;
import android.telephony.CellSignalStrength;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;

import java.util.List;

public class TelephonyService {
    private final Context context;
    private TelephonyManager telephonyManager;
    private ConnectivityManager connectivityManager;
    private WifiManager wifiManager;

    public TelephonyService(Context context){
        this.context = context;
        try {
            connectivityManager = context.getSystemService(ConnectivityManager.class);
            telephonyManager = context.getSystemService(TelephonyManager.class);
            wifiManager = context.getSystemService(WifiManager.class);
        } catch (Exception e) {
            log("TelephonyService error: " + e);
        }
    }

    public NetworkState getNetworkState() {
        if (connectivityManager == null) return unknownNetwork();
        try {
            Network currentNetwork = connectivityManager.getActiveNetwork();
            if (currentNetwork == null) return unknownNetwork();
            NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(currentNetwork);
            if (caps == null) return unknownNetwork();
            boolean wlan = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    || Build.VERSION.SDK_INT >= 26 && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI_AWARE);

            if (wlan) {
                if (wifiManager == null) return unknownNetwork();
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                if (wifiInfo == null) return unknownNetwork();
                int rssiDbm = wifiInfo.getRssi();
                int rssi = Math.round((rssiDbm + 100) / 90f * 100);
                return new NetworkState(NetworkState.NETWORK_TYPE_WLAN, rssi);
            } else {
                if (telephonyManager == null) return unknownNetwork();
                int networkType;
                if (Build.VERSION.SDK_INT >= 33) {
                    if (context.checkSelfPermission(Manifest.permission.READ_BASIC_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                        return unknownNetwork();
                    }
                    networkType = telephonyManager.getDataNetworkType();
                } else {
                    NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
                    if (networkInfo == null) return unknownNetwork();
                    networkType = networkInfo.getSubtype();
                }
                if (networkType == TelephonyManager.NETWORK_TYPE_UNKNOWN) return unknownNetwork();
                if (Build.VERSION.SDK_INT >= 29) {
                    SignalStrength signalStrength = telephonyManager.getSignalStrength();
                    if (signalStrength == null) return unknownNetwork();
                    List<CellSignalStrength> strengths = signalStrength.getCellSignalStrengths();
                    int asu = CellInfo.UNAVAILABLE;
                    for (CellSignalStrength strength : strengths) {
                        asu = strength.getAsuLevel();
                        if (asu == CellInfo.UNAVAILABLE) continue;
                        break;
                    }
                    if (asu == CellInfo.UNAVAILABLE) return unknownNetwork();
                    return getCelullarNetworkState(networkType, asu);
                } else {
                    return unknownNetwork();
                }
            }
        }catch (Exception e){
            log("getNetworkState error: " + e);
        }
        return unknownNetwork();
    }

    private NetworkState getCelullarNetworkState(int networkType, int asu){
        switch (networkType) {
            case TelephonyManager.NETWORK_TYPE_GSM:
                return new NetworkState(NetworkState.NETWORK_TYPE_GSM, Math.round(asu / 31f * 100));
            case TelephonyManager.NETWORK_TYPE_GPRS:
            case TelephonyManager.NETWORK_TYPE_EDGE:
            case TelephonyManager.NETWORK_TYPE_CDMA:
            case TelephonyManager.NETWORK_TYPE_1xRTT:
            case TelephonyManager.NETWORK_TYPE_IDEN:
                return new NetworkState(NetworkState.NETWORK_TYPE_2G, Math.round(asu / 31f * 100));
            case TelephonyManager.NETWORK_TYPE_UMTS:
            case TelephonyManager.NETWORK_TYPE_EVDO_0:
            case TelephonyManager.NETWORK_TYPE_EVDO_A:
            case TelephonyManager.NETWORK_TYPE_HSDPA:
            case TelephonyManager.NETWORK_TYPE_HSUPA:
            case TelephonyManager.NETWORK_TYPE_HSPA:
            case TelephonyManager.NETWORK_TYPE_EVDO_B:
            case TelephonyManager.NETWORK_TYPE_EHRPD:
            case TelephonyManager.NETWORK_TYPE_HSPAP:
                return new NetworkState(NetworkState.NETWORK_TYPE_3G, Math.round(asu / 31f * 100));
            case TelephonyManager.NETWORK_TYPE_LTE:
                return new NetworkState(NetworkState.NETWORK_TYPE_LTE, Math.round(asu / 95f * 100));
            case TelephonyManager.NETWORK_TYPE_NR:
                return new NetworkState(NetworkState.NETWORK_TYPE_5G, Math.round(asu / 95f * 100));
            case TelephonyManager.NETWORK_TYPE_IWLAN:
                return new NetworkState(NetworkState.NETWORK_TYPE_WLAN, Math.round(asu / 95f * 100));
            default:
                return new NetworkState(NetworkState.NETWORK_TYPE_NA, Math.round(asu / 95f * 100));
        }
    }

    private NetworkState unknownNetwork(){
        return new NetworkState();
    }
}
