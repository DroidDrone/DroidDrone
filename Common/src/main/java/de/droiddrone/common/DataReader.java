package de.droiddrone.common;

import java.nio.charset.StandardCharsets;

public class DataReader {
    private final byte[] data;
    private final int size;
    private final boolean isBigEndian;
    private int offset;

    public DataReader(byte[] data, boolean isBigEndian) {
        this.data = data;
        this.isBigEndian = isBigEndian;
        offset = 0;
        if (data == null){
            size = 0;
        }else{
            size = data.length;
        }
    }

    public byte readByte(){
        byte value = data[offset];
        offset++;
        return value;
    }

    public int readUnsignedByteAsInt(){
        byte value = data[offset];
        offset++;
        return value & 0xFF;
    }

    public short readShort(){
        short value;
        if (isBigEndian){
            value = (short) (((data[offset] & 0xFF) << 8) | (data[offset+1] & 0xFF));
        }else{
            value = (short) (((data[offset + 1] & 0xFF) << 8) | (data[offset] & 0xFF));
        }
        offset += 2;
        return value;
    }

    public int readUnsignedShortAsInt(){
        short value;
        if (isBigEndian){
            value = (short) (((data[offset] & 0xFF) << 8) | (data[offset+1] & 0xFF));
        }else{
            value = (short) (((data[offset + 1] & 0xFF) << 8) | (data[offset] & 0xFF));
        }
        offset += 2;
        return value & 0xFFFF;
    }

    public int readInt(){
        int value;
        if (isBigEndian){
            value = ((data[offset] & 0xFF) << 24) | ((data[offset + 1] & 0xFF) << 16) | ((data[offset + 2] & 0xFF) << 8) | (data[offset + 3] & 0xFF);
        }else {
            value = ((data[offset + 3] & 0xFF) << 24) | ((data[offset + 2] & 0xFF) << 16) | ((data[offset + 1] & 0xFF) << 8) | (data[offset] & 0xFF);
        }
        offset += 4;
        return value;
    }

    public float readFloat(){
        return Float.intBitsToFloat(readInt());
    }

    public long readLong(){
        long value;
        if (isBigEndian){
            value = ((long) (data[offset] & 0xFF) << 56) | ((long) (data[offset + 1] & 0xFF) << 48) | ((long) (data[offset + 2] & 0xFF) << 40) | ((long) (data[offset + 3] & 0xFF) << 32) |
                ((long) (data[offset + 4] & 0xFF) << 24) | ((long)(data[offset+5] & 0xFF) << 16) | ((long)(data[offset+6] & 0xFF) << 8) | (long)(data[offset+7] & 0xFF);
        }else {
            value = ((long) (data[offset + 7] & 0xFF) << 56) | ((long) (data[offset + 6] & 0xFF) << 48) | ((long) (data[offset + 5] & 0xFF) << 40) | ((long) (data[offset + 4] & 0xFF) << 32) |
                    ((long) (data[offset + 3] & 0xFF) << 24) | ((long)(data[offset+2] & 0xFF) << 16) | ((long)(data[offset+1] & 0xFF) << 8) | (long)(data[offset] & 0xFF);
        }
        offset += 8;
        return value;
    }

    public double readDouble(){
        return Double.longBitsToDouble(readLong());
    }

    public String readBufferAsString(){
        offset = size;
        return new String(data, StandardCharsets.US_ASCII);
    }

    public String readUTF(){
        short length = readShort();
        if (length <= 0) return null;
        byte[] strBytes = new byte[length];
        System.arraycopy(data, offset, strBytes, 0, length);
        offset += length;
        return new String(strBytes, StandardCharsets.UTF_8);
    }

    public byte[] getData(){
        return data;
    }

    public int getOffset(){
        return offset;
    }

    public int getSize(){
        return size;
    }

    public int read(byte[] dest, int destOffset, int length){
        int remaining = getRemaining();
        int readBytes = Math.min(length, remaining);
        System.arraycopy(data, offset, dest, destOffset, readBytes);
        offset += readBytes;
        return readBytes;
    }

    public boolean readBoolean(){
        boolean value = (data[offset] != 0);
        offset++;
        return value;
    }

    public int getRemaining(){
        return size - offset;
    }
}
