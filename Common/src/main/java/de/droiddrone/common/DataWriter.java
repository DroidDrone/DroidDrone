package de.droiddrone.common;

import java.util.ArrayList;
import java.util.List;

public class DataWriter {
    private final List<Byte> data;
    private final boolean isBigEndian;

    public DataWriter(boolean isBigEndian) {
        this.isBigEndian = isBigEndian;
        data = new ArrayList<>();
    }

    public void writeByte(byte value){
        data.add(value);
    }

    public void writeBoolean(boolean value){
        data.add((byte) (value ? 1 : 0));
    }

    public void writeShort(short value){
        writeNum(value, 2);
    }

    public void writeInt(int value){
        writeNum(value, 4);
    }

    public void writeFloat(float value){
        writeNum(Float.floatToIntBits(value), 4);
    }

    public void writeLong(long value){
        writeNum(value, 8);
    }

    public void writeArray(byte[] values, int offset, int length){
        for (int i = offset; i < offset+length; i++) {
            data.add(values[i]);
        }
    }

    public int getSize(){
        return data.size();
    }

    public byte[] getData(){
        if (data.isEmpty()) return null;
        byte[] arr = new byte[data.size()];
        int c = 0;
        for (byte val : data){
            arr[c] = val;
            c++;
        }
        return arr;
    }

    private void writeNum(long value, int bytesCount){
        if (isBigEndian){
            for (int i = bytesCount-1; i >= 0; i--) {
                data.add((byte)(value >>> i*8));
            }
        }else{
            for (int i = 0; i < bytesCount; i++) {
                data.add((byte)(value >>> i*8));
            }
        }
    }
}
