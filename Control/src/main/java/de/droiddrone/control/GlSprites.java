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

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES31;
import android.opengl.GLUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

public class GlSprites {
    private final int textureId;
    private final int textureWidth;
    private final int textureHeight;
    private final GlBuffer positionBuffer;
    private final GlBuffer texCoordBuffer;
    private final int maxSpritesOnScreen;
    private final Sprite[] sprites;
    private final OptimizeBuffer[] optimizeBuffers;
    private int spriteOnScreenIndex;
    private float screenFactor;
    private float defaultSizeFactor;
    private float osdCanvasFactor;

    public GlSprites(int textureRessourceId, int mapRessourceId, MainActivity activity, GlBuffer positionBuffer, GlBuffer texCoordBuffer) throws IOException {
        this.positionBuffer = positionBuffer;
        this.texCoordBuffer = texCoordBuffer;
        spriteOnScreenIndex = 0;
        screenFactor = 1;
        defaultSizeFactor = 1;
        maxSpritesOnScreen = positionBuffer.sizeInBytes / 6 / 2 / 4;
        optimizeBuffers = new OptimizeBuffer[maxSpritesOnScreen];

        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        GLES31.glActiveTexture(GLES31.GL_TEXTURE0);
        int[] t = new int[1];
        GLES31.glGenTextures(1, t, 0);
        GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, t[0]);
        GLES31.glTexParameterf(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_MIN_FILTER, GLES31.GL_LINEAR);
        GLES31.glTexParameterf(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_MAG_FILTER, GLES31.GL_LINEAR);
        GLES31.glTexParameterf(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_WRAP_S, GLES31.GL_CLAMP_TO_EDGE);
        GLES31.glTexParameterf(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_WRAP_T, GLES31.GL_CLAMP_TO_EDGE);
        Bitmap bitmap = BitmapFactory.decodeResource(activity.getResources(), textureRessourceId, options);
        textureWidth = bitmap.getWidth();
        textureHeight = bitmap.getHeight();
        GLUtils.texImage2D(GLES31.GL_TEXTURE_2D, 0, bitmap, 0);
        bitmap.recycle();
        textureId = t[0];

        ArrayList<Sprite> spritesList = new ArrayList<>();
        Resources r = activity.getResources();
        try (InputStream inputStream = r.openRawResource(mapRessourceId)) {
            InputStreamReader isr = new InputStreamReader(inputStream);
            BufferedReader br = new BufferedReader(isr);
            String line;
            while ((line = br.readLine()) != null) {
                String[] items = line.split("=");
                if (items.length != 2) continue;
                if ("$sizeFactor".equals(items[0].trim())){
                    try{
                        defaultSizeFactor = Float.parseFloat(items[1].trim());
                    }catch(Exception e){
                        //
                    }
                }
                String name = items[0].trim();
                String[] sizes = items[1].trim().split(" ");
                if (sizes.length != 4) continue;
                int x, y, width, height;
                x = Integer.parseInt(sizes[0]);
                y = Integer.parseInt(sizes[1]);
                width = Integer.parseInt(sizes[2]);
                height = Integer.parseInt(sizes[3]);
                spritesList.add(new Sprite(name, width, height,
                        x / (float) textureWidth,
                        y / (float) textureHeight,
                        (x + width) / (float) textureWidth,
                        (y + height) / (float) textureHeight));
            }
        }
        sprites = spritesList.toArray(new Sprite[0]);
    }

    public void setOsdCanvasFactor(float osdCanvasFactor){
        this.osdCanvasFactor = osdCanvasFactor;
    }

    public void setScreenFactor(float screenFactor){
        this.screenFactor = screenFactor;
    }

    public float getDefaultSizeFactor(){
        return defaultSizeFactor;
    }

    public void addSpriteSizeOverride(int numericName, float x, float y, float width, float height){
        Sprite sprite = getSprite(numericName);
        if (sprite == null) return;
        float sizeFactor = 1;
        addSprite(x, y, width, height, sizeFactor, 0, sprite.tx0, sprite.ty0, sprite.tx1, sprite.ty1);
    }

    public void addSpriteCutLeft(int numericName, float x, float y, float width){
        addSpriteCutLeft(numericName, x, y, width, defaultSizeFactor);
    }

    public void addSpriteCutLeft(int numericName, float x, float y, float width, float size){
        Sprite sprite = getSprite(numericName);
        if (sprite == null) return;
        float sizeFactor = size * screenFactor * osdCanvasFactor;
        float tx0 = sprite.tx0;
        if (width < sprite.width) {
            tx0 = (sprite.tx1 * textureWidth - width) / (float) textureWidth;
        }
        addSprite(x, y, width, sprite.height, sizeFactor, 0, tx0, sprite.ty0, sprite.tx1, sprite.ty1);
    }

    public void addSpriteCutRight(int numericName, float x, float y, float width){
        addSpriteCutRight(numericName, x, y, width, defaultSizeFactor);
    }

    public void addSpriteCutRight(int numericName, float x, float y, float width, float size){
        Sprite sprite = getSprite(numericName);
        if (sprite == null) return;
        float sizeFactor = size * screenFactor * osdCanvasFactor;
        float tx1 = sprite.tx1;
        if (width < sprite.width) {
            tx1 = (sprite.tx0 * textureWidth + width) / (float) textureWidth;
        }
        addSprite(x, y, width, sprite.height, sizeFactor, 0, sprite.tx0, sprite.ty0, tx1, sprite.ty1);
    }

    public void addSprite(int numericName, float x, float y, float size){
        Sprite sprite = getSprite(numericName);
        if (sprite == null) return;
        float sizeFactor = size * screenFactor * osdCanvasFactor;
        addSprite(x, y, sprite.width, sprite.height, sizeFactor, 0, sprite.tx0, sprite.ty0, sprite.tx1, sprite.ty1);
    }

    public void addSprite(int numericName, float x, float y){
        Sprite sprite = getSprite(numericName);
        if (sprite == null) return;
        float sizeFactor = defaultSizeFactor * screenFactor * osdCanvasFactor;
        addSprite(x, y, sprite.width, sprite.height, sizeFactor, 0, sprite.tx0, sprite.ty0, sprite.tx1, sprite.ty1);
    }

    public void addSpriteAngle(int numericName, float x, float y, float angle){
        addSpriteAngle(numericName, x, y, angle, defaultSizeFactor);
    }

    public void addSpriteAngle(int numericName, float x, float y, float angle, float size){
        Sprite sprite = getSprite(numericName);
        if (sprite == null) return;
        float sizeFactor = size * screenFactor * osdCanvasFactor;
        addSprite(x, y, sprite.width, sprite.height, sizeFactor, angle, sprite.tx0, sprite.ty0, sprite.tx1, sprite.ty1);
    }

    private void addSprite(float x, float y, float width, float height, float sizeFactor, float angle, float tx0, float ty0, float tx1, float ty1){
        if (spriteOnScreenIndex >= maxSpritesOnScreen) return;
        width *= sizeFactor;
        height *= sizeFactor;
        if (optimizeBuffers[spriteOnScreenIndex] != null
                && optimizeBuffers[spriteOnScreenIndex].isEqual(x, y, width, height, angle, tx0, ty0, tx1, ty1)){
            spriteOnScreenIndex++;
            return;
        }
        optimizeBuffers[spriteOnScreenIndex] = new OptimizeBuffer(x, y, width, height, angle, tx0, ty0, tx1, ty1);
        float[] buf12 = new float[12];
        if (angle == 0) {
            buf12[0] = x + width;
            buf12[1] = y - height;
            buf12[2] = x;
            buf12[3] = buf12[1];
            buf12[4] = buf12[0];
            buf12[5] = y;
            buf12[6] = buf12[2];
            buf12[7] = buf12[3];
            buf12[8] = buf12[4];
            buf12[9] = buf12[5];
            buf12[10] = buf12[2];
            buf12[11] = buf12[5];
        }else{
            float widthD2 = width / 2;
            float heightD2 = height / 2;
            x += widthD2;
            y -= heightD2;
            double angleRad = Math.toRadians(angle);
            double cos = Math.cos(angleRad);
            double sin = Math.sin(angleRad);
            double hCos = heightD2 * cos;
            double hSin = heightD2 * sin;
            double wCos = widthD2 * cos;
            double wSin = widthD2 * sin;
            buf12[0] = (float) (-hSin + wCos) + x;
            buf12[1] = (float) (-hCos - wSin) + y;
            buf12[2] = (float) (-hSin - wCos) + x;
            buf12[3] = (float) (-hCos + wSin) + y;
            buf12[4] = (float) (hSin + wCos) + x;
            buf12[5] = (float) (hCos - wSin) + y;
            buf12[6] = buf12[2];
            buf12[7] = buf12[3];
            buf12[8] = buf12[4];
            buf12[9] = buf12[5];
            buf12[10] = (float) (hSin - wCos) + x;
            buf12[11] = (float) (hCos + wSin) + y;
        }
        positionBuffer.putArray(spriteOnScreenIndex * 12, buf12);
        buf12[0] = tx1;
        buf12[1] = ty1;
        buf12[2] = tx0;
        buf12[3] = buf12[1];
        buf12[4] = buf12[0];
        buf12[5] = ty0;
        buf12[6] = buf12[2];
        buf12[7] = buf12[1];
        buf12[8] = buf12[0];
        buf12[9] = buf12[5];
        buf12[10] = buf12[2];
        buf12[11] = buf12[5];
        texCoordBuffer.putArray(spriteOnScreenIndex * 12, buf12);
        spriteOnScreenIndex++;
    }

    public void draw(){
        if (spriteOnScreenIndex == 0) return;
        positionBuffer.update(false);
        texCoordBuffer.update(false);
        GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, textureId);
        GLES31.glDrawArrays(GLES31.GL_TRIANGLES, 0, spriteOnScreenIndex * 6);
    }

    public void clear(){
        spriteOnScreenIndex = 0;
    }

    public float getSpriteWidth(int numericName){
        return getSpriteWidth(numericName, defaultSizeFactor);
    }

    public float getSpriteWidth(int numericName, float sizeFactor){
        if (numericName == 0) return 0;
        for (Sprite sprite : sprites) {
            if (sprite.numericName == numericName) return sprite.width * sizeFactor * screenFactor * osdCanvasFactor;
        }
        return 0;
    }

    public float getRawSpriteWidth(int numericName){
        if (numericName == 0) return 0;
        for (Sprite sprite : sprites) {
            if (sprite.numericName == numericName) return sprite.width;
        }
        return 0;
    }

    public float getSpriteHeight(int numericName){
        return getSpriteHeight(numericName, defaultSizeFactor);
    }

    public float getSpriteHeight(int numericName, float sizeFactor){
        if (numericName == 0) return 0;
        for (Sprite sprite : sprites) {
            if (sprite.numericName == numericName) return sprite.height * sizeFactor * screenFactor * osdCanvasFactor;
        }
        return 0;
    }

    public Sprite getSprite(int numericName){
        if (numericName == 0) return null;
        for (Sprite sprite : sprites) {
            if (sprite.numericName == numericName) return sprite;
        }
        return null;
    }

    public Sprite getSprite(String name){
        if (name == null) return null;
        for (Sprite sprite : sprites) {
            if (name.equals(sprite.name)) return sprite;
        }
        return null;
    }

    private static class OptimizeBuffer{
        public final float xCenter;
        public final float yCenter;
        public final float width;
        public final float height;
        public final float angle;
        public final float tx0;
        public final float ty0;
        public final float tx1;
        public final float ty1;

        public OptimizeBuffer(float xCenter, float yCenter, float width, float height, float angle, float tx0, float ty0, float tx1, float ty1) {
            this.xCenter = xCenter;
            this.yCenter = yCenter;
            this.width = width;
            this.height = height;
            this.angle = angle;
            this.tx0 = tx0;
            this.ty0 = ty0;
            this.tx1 = tx1;
            this.ty1 = ty1;
        }

        public boolean isEqual(float xCenter, float yCenter, float width, float height, float angle, float tx0, float ty0, float tx1, float ty1){
            return (this.xCenter == xCenter && this.yCenter == yCenter && this.width == width && this.height == height && this.angle == angle
                && this.tx0 == tx0 && this.ty0 == ty0 && this.tx1 == tx1 && this.ty1 == ty1);
        }
    }

    public static class Sprite{
        public final int numericName;
        public final String name;
        public final int width;
        public final int height;
        public final float tx0;
        public final float ty0;
        public final float tx1;
        public final float ty1;

        public Sprite(String name, int width, int height, float tx0, float ty0, float tx1, float ty1) {
            this.name = name;
            this.width = width;
            this.height = height;
            this.tx0 = tx0;
            this.ty0 = ty0;
            this.tx1 = tx1;
            this.ty1 = ty1;
            if (name.matches("\\d+")){
                numericName = Integer.parseInt(name);
            }else{
                numericName = 0;
            }
        }
    }
}
