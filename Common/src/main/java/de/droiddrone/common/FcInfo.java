package de.droiddrone.common;

public class FcInfo {
    public static final byte FC_VARIANT_UNKNOWN = 0;
    public static final byte FC_VARIANT_INAV = 1;
    public static final byte FC_VARIANT_BETAFLIGHT = 2;
    public static final String INAV_ID = "INAV";
    public static final String BETAFLIGHT_ID = "BTFL";
    public static final String INAV_NAME = "INAV";
    public static final String BETAFLIGHT_NAME = "BETAFLIGHT";
    private final int fcVariant;
    private final int fcVersionMajor;
    private final int fcVersionMinor;
    private final int fcVersionPatchLevel;
    private final int mspProtocolVersion;
    private final int mspApiVersionMajor;
    private final int mspApiVersionMinor;

    public FcInfo(int fcVariant, int fcVersionMajor, int fcVersionMinor, int fcVersionPatchLevel, int mspProtocolVersion, int mspApiVersionMajor, int mspApiVersionMinor) {
        this.fcVariant = fcVariant;
        this.fcVersionMajor = fcVersionMajor;
        this.fcVersionMinor = fcVersionMinor;
        this.fcVersionPatchLevel = fcVersionPatchLevel;
        this.mspProtocolVersion = mspProtocolVersion;
        this.mspApiVersionMajor = mspApiVersionMajor;
        this.mspApiVersionMinor = mspApiVersionMinor;
    }

    public String getFcVersionStr(){
        return fcVersionMajor + "." + fcVersionMinor + "." + fcVersionPatchLevel;
    }

    public String getMspVersionStr(){
        return mspProtocolVersion + "." + mspApiVersionMajor + "." + mspApiVersionMinor;
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

    public int getMspProtocolVersion() {
        return mspProtocolVersion;
    }

    public int getMspApiVersionMajor() {
        return mspApiVersionMajor;
    }

    public int getMspApiVersionMinor() {
        return mspApiVersionMinor;
    }
}
