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

package de.droiddrone.control;

import android.opengl.GLES31;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class GlBuffer {
    private final FloatBuffer floatBuffer;
    public final int sizeInBytes;
    public final int bufferIndex;
    public final int valuesPerPoint;
    public final int attribPointer;
    private boolean needUpdate;
    private int updateStartPos;
    private int updateEndPos;

    public GlBuffer(int bufferIndex, int sizeInBytes, int usage, int valuesPerPoint, int attribPointer) {
        this.sizeInBytes = sizeInBytes;
        this.bufferIndex = bufferIndex;
        this.valuesPerPoint = valuesPerPoint;
        this.attribPointer = attribPointer;

        ByteBuffer bb = ByteBuffer.allocateDirect(sizeInBytes);
        bb.order(ByteOrder.nativeOrder());
        floatBuffer = bb.asFloatBuffer();

        GLES31.glBindBuffer(GLES31.GL_ARRAY_BUFFER, bufferIndex);
        floatBuffer.position(0);
        GLES31.glBufferData(GLES31.GL_ARRAY_BUFFER, sizeInBytes, floatBuffer, usage);
        GLES31.glVertexAttribPointer(attribPointer, valuesPerPoint, GLES31.GL_FLOAT, false, valuesPerPoint * 4, 0);
        GLES31.glEnableVertexAttribArray(attribPointer);

        clear();
    }

    public void clear(){
        int sizeFloat = sizeInBytes / 4;
        putArray(0, new float[sizeFloat]);
        update(false);
        floatBuffer.position(0);
    }

    public void update(boolean force){
        if (needUpdate || force){
            if (force){
                updateStartPos = 0;
                updateEndPos = sizeInBytes / 4;
            }
            GLES31.glBindBuffer(GLES31.GL_ARRAY_BUFFER, bufferIndex);
            floatBuffer.position(updateStartPos);
            GLES31.glBufferSubData(GLES31.GL_ARRAY_BUFFER, updateStartPos * 4, (updateEndPos - updateStartPos) * 4, floatBuffer);
            updateStartPos = sizeInBytes / 4;
            updateEndPos = 0;
            needUpdate = false;
        }
    }

    public void putArray(int offset, float[] buf){
        floatBuffer.position(offset);
        floatBuffer.put(buf);
        int endPos = offset + buf.length;
        if (offset < updateStartPos) updateStartPos = offset;
        if (endPos > updateEndPos) updateEndPos = endPos;
        needUpdate = true;
    }
}
