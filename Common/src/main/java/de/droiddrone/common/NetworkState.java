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

public class NetworkState {
    public static final int NETWORK_TYPE_NA = 0;
    public static final int NETWORK_TYPE_GSM = 1;
    public static final int NETWORK_TYPE_2G = 2;
    public static final int NETWORK_TYPE_3G = 3;
    public static final int NETWORK_TYPE_LTE = 4;
    public static final int NETWORK_TYPE_5G = 5;
    public static final int NETWORK_TYPE_WLAN = 10;
    private final int rssi;
    private final int networkType;

    public NetworkState(int networkType, int rssi){
        this.networkType = networkType;
        if (rssi < 0) rssi = 0;
        if (rssi > 100) rssi = 100;
        this.rssi = rssi;
    }

    public NetworkState(){
        this.networkType = NETWORK_TYPE_NA;
        this.rssi = 0;
    }

    public String getNetworkName() {
        switch (networkType){
            case NETWORK_TYPE_GSM:
                return "1G";
            case NETWORK_TYPE_2G:
                return "2G";
            case NETWORK_TYPE_3G:
                return "3G";
            case NETWORK_TYPE_LTE:
                return "LTE";
            case NETWORK_TYPE_5G:
                return "5G";
            case NETWORK_TYPE_WLAN:
                return "WLAN";
            default:
                return "-";
        }
    }

    public int getRssi() {
        return rssi;
    }

    public int getNetworkType(){
        return networkType;
    }

}
