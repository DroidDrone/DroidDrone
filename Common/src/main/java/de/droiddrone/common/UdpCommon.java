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

public class UdpCommon {
    public static final int defaultPort = 6286;
    public static final int packetLength = 1100;

    //region Packet headers
    public static final byte VideoInitialFrame = 0;
    public static final byte VideoFrame = 1;
    public static final byte KeyFrame = 2;
    public static final byte StartVideo = 3;
    public static final byte GetVideoConfig = 4;
    public static final byte ChangeBitRate = 5;
    public static final byte RequestPackets = 6;
    public static final byte PacketReceived = 7;
    public static final byte Connect = 8;
    public static final byte Disconnect = 9;
    public static final byte Ping = 10;
    public static final byte Pong = 11;
    public static final byte AudioInitialFrame = 12;
    public static final byte AudioFrame = 13;
    public static final byte FcInfo = 14;
    public static final byte OsdConfig = 15;
    public static final byte BatteryConfig = 16;
    public static final byte BoxIds = 17;
    public static final byte BoxNames = 18;
    public static final byte TelemetryData = 19;
    public static final byte StartStopRecording = 20;
    public static final byte Config = 21;
    public static final byte ConfigReceived = 22;
    public static final byte VersionMismatch = 23;
    public static final byte RcFrame = 24;
    //endregion

    public static boolean isPacketNumbered(byte packetName){
        switch (packetName){
            case VideoInitialFrame:
            case AudioInitialFrame:
            case StartVideo:
            case GetVideoConfig:
            case ChangeBitRate:
            case Connect:
            case FcInfo:
            case OsdConfig:
            case BatteryConfig:
            case BoxIds:
            case BoxNames:
            case StartStopRecording:
            case Config:
            case ConfigReceived:
            case VersionMismatch:
                return true;
            default:
                return false;
        }
    }

    public static boolean isSendPacketReceived(byte packetName){
        switch (packetName){
            case VideoInitialFrame:
            case AudioInitialFrame:
            case StartVideo:
            case GetVideoConfig:
            case Connect:
            case FcInfo:
            case OsdConfig:
            case BatteryConfig:
            case BoxIds:
            case BoxNames:
            case StartStopRecording:
            case Config:
            case ConfigReceived:
            case VersionMismatch:
                return true;
            default:
                return false;
        }
    }

    public static int getPacketLifeTimeMs(byte packetName){
        switch (packetName){
            case VideoFrame:
            case AudioFrame:
            case RcFrame:
                return 0;
            case KeyFrame:
                return 500;
            default:
                return 1000;
        }
    }
}
