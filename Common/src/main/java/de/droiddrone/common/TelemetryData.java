package de.droiddrone.common;

public class TelemetryData {
    public final short code;
    public final byte[] data;


    public TelemetryData(short code, byte[] data) {
        this.code = code;
        this.data = data;
    }
}
