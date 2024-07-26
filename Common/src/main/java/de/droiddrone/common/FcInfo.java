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

public class FcInfo {
    public static final byte FC_VARIANT_UNKNOWN = 0;
    public static final byte FC_VARIANT_INAV = 1;
    public static final byte FC_VARIANT_BETAFLIGHT = 2;
    public static final byte FC_VARIANT_ARDUPILOT = 3;
    public static final String INAV_ID = "INAV";
    public static final String BETAFLIGHT_ID = "BTFL";
    public static final String ARDUPILOT_ID = "ARDU";
    public static final String INAV_NAME = "INAV";
    public static final String BETAFLIGHT_NAME = "Betaflight";
    public static final String ARDUPILOT_NAME = "ArduPilot";
    private final int fcVariant;
    private final int fcVersionMajor;
    private final int fcVersionMinor;
    private final int fcVersionPatchLevel;
    private final int apiProtocolVersion;
    private final int apiVersionMajor;
    private final int apiVersionMinor;

    public FcInfo(int fcVariant, int fcVersionMajor, int fcVersionMinor, int fcVersionPatchLevel, int apiProtocolVersion, int apiVersionMajor, int apiVersionMinor) {
        this.fcVariant = fcVariant;
        this.fcVersionMajor = fcVersionMajor;
        this.fcVersionMinor = fcVersionMinor;
        this.fcVersionPatchLevel = fcVersionPatchLevel;
        this.apiProtocolVersion = apiProtocolVersion;
        this.apiVersionMajor = apiVersionMajor;
        this.apiVersionMinor = apiVersionMinor;
    }

    public String getFcVersionStr(){
        return fcVersionMajor + "." + fcVersionMinor + "." + fcVersionPatchLevel;
    }

    public String getFcApiVersionStr(){
        return apiProtocolVersion + "." + apiVersionMajor + "." + apiVersionMinor;
    }

    public String getFcName(){
        String name = "UNKNOWN";
        switch (fcVariant){
            case FC_VARIANT_INAV:
                name = INAV_NAME;
                break;
            case FC_VARIANT_BETAFLIGHT:
                name = BETAFLIGHT_NAME;
                break;
            case FC_VARIANT_ARDUPILOT:
                name = ARDUPILOT_NAME;
                break;
        }
        return name;
    }

    public int getFcVariant() {
        return fcVariant;
    }

    public int getFcVersionMajor() {
        return fcVersionMajor;
    }

    public int getFcVersionMinor() {
        return fcVersionMinor;
    }

    public int getFcVersionPatchLevel() {
        return fcVersionPatchLevel;
    }

    public int getApiProtocolVersion() {
        return apiProtocolVersion;
    }

    public int getApiVersionMajor() {
        return apiVersionMajor;
    }

    public int getApiVersionMinor() {
        return apiVersionMinor;
    }
}
