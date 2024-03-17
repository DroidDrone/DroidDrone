package de.droiddrone.control;

import android.opengl.GLES31;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;

public class GlText {
    private static final int blindCharId = 63;// ? - Symbol
    private final int textureId;
    private final GlBuffer positionBuffer;
    private final GlBuffer texCoordBuffer;
    private final int maxSymbolsOnScreen;
    private final int colorUniformHandle;
    private final OptimizeBuffer[] optimizeBuffers;
    private Char[] chars = null;
    private Char blind = null;
    private Kerning[] kernings = null;
    private int charOnScreenIndex;
    private int lineHeight;
    private float defaultTextSize;
    private float colorR, colorG, colorB, colorA;
    private float screenFactor;
    private float osdCanvasFactor;

    public GlText(int textureId, int xmlResourceId, MainActivity activity, GlBuffer positionBuffer, GlBuffer texCoordBuffer, int colorUniformHandle) throws XmlPullParserException, IOException, NumberFormatException {
        this.positionBuffer = positionBuffer;
        this.texCoordBuffer = texCoordBuffer;
        this.textureId = textureId;
        this.colorUniformHandle = colorUniformHandle;
        charOnScreenIndex = 0;
        screenFactor = 1;
        maxSymbolsOnScreen = positionBuffer.sizeInBytes / 6 / 2 / 4;
        optimizeBuffers = new OptimizeBuffer[maxSymbolsOnScreen];
        setDefaultTextSize(14);
        setColor(255, 255, 255, 255);

        XmlPullParser parser = Xml.newPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        try (InputStream inputStream = activity.getResources().openRawResource(xmlResourceId)) {
            parser.setInput(inputStream, null);
            int eventType = parser.getEventType();
            int cChar = 0;
            int cKerning = 0;
            int textureWidth = 0;
            int textureHeight = 0;
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType != XmlPullParser.START_TAG) {
                    eventType = parser.next();
                    continue;
                }
                String name = parser.getName();
                if ("common".equals(name)) {
                    for (int i = 0; i < parser.getAttributeCount(); i++) {
                        if ("lineHeight".equals(parser.getAttributeName(i))) {
                            lineHeight = Integer.parseInt(parser.getAttributeValue(i));
                        } else if ("scaleW".equals(parser.getAttributeName(i))) {
                            textureWidth = Integer.parseInt(parser.getAttributeValue(i));
                        } else if ("scaleH".equals(parser.getAttributeName(i))) {
                            textureHeight = Integer.parseInt(parser.getAttributeValue(i));
                        }
                    }
                } else if ("chars".equals(name)) {
                    for (int i = 0; i < parser.getAttributeCount(); i++) {
                        if ("count".equals(parser.getAttributeName(i))) {
                            int count = Integer.parseInt(parser.getAttributeValue(i));
                            if (count <= 0) throw new XmlPullParserException("Wrong chars count.");
                            chars = new Char[count];
                            break;
                        }
                    }
                } else if ("char".equals(name) && chars != null && cChar < chars.length) {
                    if (textureWidth == 0 || textureHeight == 0)
                        throw new XmlPullParserException("Wrong texture scale.");
                    int id = 0, x = 0, y = 0, width = 0, height = 0, xoffset = 0, yoffset = 0, xadvance = 0;
                    for (int i = 0; i < parser.getAttributeCount(); i++) {
                        if ("id".equals(parser.getAttributeName(i))) {
                            id = Integer.parseInt(parser.getAttributeValue(i));
                        } else if ("x".equals(parser.getAttributeName(i))) {
                            x = Integer.parseInt(parser.getAttributeValue(i));
                        } else if ("y".equals(parser.getAttributeName(i))) {
                            y = Integer.parseInt(parser.getAttributeValue(i));
                        } else if ("width".equals(parser.getAttributeName(i))) {
                            width = Integer.parseInt(parser.getAttributeValue(i));
                        } else if ("height".equals(parser.getAttributeName(i))) {
                            height = Integer.parseInt(parser.getAttributeValue(i));
                        } else if ("xoffset".equals(parser.getAttributeName(i))) {
                            xoffset = Integer.parseInt(parser.getAttributeValue(i));
                        } else if ("yoffset".equals(parser.getAttributeName(i))) {
                            yoffset = Integer.parseInt(parser.getAttributeValue(i));
                        } else if ("xadvance".equals(parser.getAttributeName(i))) {
                            xadvance = Integer.parseInt(parser.getAttributeValue(i));
                        }
                    }
                    chars[cChar] = new Char(id, x, y, width, height, xoffset, yoffset, xadvance, textureWidth, textureHeight);
                    if (id == blindCharId) blind = chars[cChar];
                    cChar++;
                } else if ("kernings".equals(name)) {
                    for (int i = 0; i < parser.getAttributeCount(); i++) {
                        if ("count".equals(parser.getAttributeName(i))) {
                            int count = Integer.parseInt(parser.getAttributeValue(i));
                            if (count > 0) kernings = new Kerning[count];
                            break;
                        }
                    }
                } else if ("kerning".equals(name) && kernings != null && cKerning < kernings.length) {
                    int first = 0, second = 0, amount = 0;
                    for (int i = 0; i < parser.getAttributeCount(); i++) {
                        if ("first".equals(parser.getAttributeName(i))) {
                            first = Integer.parseInt(parser.getAttributeValue(i));
                        } else if ("second".equals(parser.getAttributeName(i))) {
                            second = Integer.parseInt(parser.getAttributeValue(i));
                        } else if ("amount".equals(parser.getAttributeName(i))) {
                            amount = Integer.parseInt(parser.getAttributeValue(i));
                        }
                    }
                    kernings[cKerning] = new Kerning(first, second, amount);
                    cKerning++;
                }
                eventType = parser.next();
            }
        }
    }

    public void setOsdCanvasFactor(float osdCanvasFactor){
        this.osdCanvasFactor = osdCanvasFactor;
    }

    public void setScreenFactor(float screenFactor){
        this.screenFactor = screenFactor;
    }

    public void setDefaultTextSize(float size){
        defaultTextSize = size;
    }

    public float getDefaultTextSize(){
        return defaultTextSize;
    }

    private float convertTextSize(float size){
        return size * screenFactor * osdCanvasFactor * 4 / lineHeight;
    }

    public void setColor(int r, int g, int b, int a){// RGBA 0..255
        colorR = r / 255f;
        colorG = g / 255f;
        colorB = b / 255f;
        colorA = a / 255f - 1;
    }

    public int getLengthInPixels(String text){
        return getLengthInPixels(text, defaultTextSize);
    }

    public int getLengthInPixels(String text, float size){
        if (text == null) return 0;
        int length = text.length();
        if (length == 0) return 0;
        float xCurrent = 0;
        float xMax = 0;
        Char previous = null;
        float sizeFactor = convertTextSize(size);
        for (int i = 0; i < length; i++) {
            int id = text.charAt(i);
            if (id == 13) continue;
            if (id == 10) {
                if (xCurrent > xMax) xMax = xCurrent;
                xCurrent = 0;
                continue;
            }
            Char ch = getChar(id);
            if (ch == null) continue;
            xCurrent += (ch.xAdvance + getKerningAmount(previous, ch)) * sizeFactor;
            previous = ch;
        }
        if (xCurrent > xMax) xMax = xCurrent;
        return Math.round(xMax);
    }

    public float getLineHeight(){
        return getLineHeight(defaultTextSize);
    }

    public float getLineHeight(float size){
        float sizeFactor = convertTextSize(size);
        return lineHeight * sizeFactor;
    }

    public void addText(String text, float xLeft, float y){
        addText(text, xLeft, y, defaultTextSize);
    }

    public void addText(String text, float xLeft, float y, float size){
        if (text == null) return;
        int length = text.length();
        if (length == 0) return;
        float[] buf12 = new float[12];
        float xCurrent = xLeft;
        float yCurrent = y;
        Char previous = null;
        float sizeFactor = convertTextSize(size);
        float lineHeight = this.lineHeight * sizeFactor;
        float yOffset = lineHeight / 6;
        for (int i = 0; i < length; i++) {
            if (charOnScreenIndex == maxSymbolsOnScreen) return;
            int id = text.charAt(i);
            if (id == 13) continue;
            if (id == 10) {
                yCurrent -= lineHeight;
                xCurrent = xLeft;
                continue;
            }
            Char ch = getChar(id);
            if (ch == null) continue;
            if (optimizeBuffers[charOnScreenIndex] != null && optimizeBuffers[charOnScreenIndex].isEqual(id, xCurrent, yCurrent, sizeFactor)){
                charOnScreenIndex++;
                xCurrent += (ch.xAdvance + getKerningAmount(previous, ch)) * sizeFactor;
                previous = ch;
                continue;
            }
            optimizeBuffers[charOnScreenIndex] = new OptimizeBuffer(id, xCurrent, yCurrent, sizeFactor);
            buf12[0] = xCurrent + (ch.xOffset + ch.width) * sizeFactor;
            buf12[1] = yCurrent - (ch.yOffset + ch.height) * sizeFactor - yOffset;
            buf12[2] = xCurrent + ch.xOffset * sizeFactor;
            buf12[3] = buf12[1];
            buf12[4] = buf12[0];
            buf12[5] = yCurrent - ch.yOffset * sizeFactor - yOffset;
            buf12[6] = buf12[2];
            buf12[7] = buf12[3];
            buf12[8] = buf12[4];
            buf12[9] = buf12[5];
            buf12[10] = buf12[2];
            buf12[11] = buf12[5];
            xCurrent += (ch.xAdvance + getKerningAmount(previous, ch)) * sizeFactor;
            previous = ch;
            positionBuffer.putArray(charOnScreenIndex * 12, buf12);
            buf12[0] = ch.tx1;
            buf12[1] = ch.ty1;
            buf12[2] = ch.tx0;
            buf12[3] = buf12[1];
            buf12[4] = buf12[0];
            buf12[5] = ch.ty0;
            buf12[6] = buf12[2];
            buf12[7] = buf12[1];
            buf12[8] = buf12[0];
            buf12[9] = buf12[5];
            buf12[10] = buf12[2];
            buf12[11] = buf12[5];
            texCoordBuffer.putArray(charOnScreenIndex * 12, buf12);
            charOnScreenIndex++;
        }
    }

    public void clearCache(){
        for (int i = 0; i < maxSymbolsOnScreen; i++) {
            optimizeBuffers[i] = null;
        }
    }

    public void draw(){
        if (charOnScreenIndex == 0) return;
        positionBuffer.update(false);
        texCoordBuffer.update(false);
        GLES31.glUniform4f(colorUniformHandle, colorR, colorG, colorB, colorA);
        GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, textureId);
        GLES31.glDrawArrays(GLES31.GL_TRIANGLES, 0, charOnScreenIndex * 6);
        charOnScreenIndex = 0;
    }

    private int getKerningAmount(Char first, Char second){
        if (first == null || second == null || kernings == null) return 0;
        int firstId = first.id;
        int secondId = second.id;
        for (Kerning kerning : kernings){
            if (kerning.first == firstId && kerning.second == secondId) return kerning.amount;
        }
        return 0;
    }

    private Char getChar(int id){
        if (chars == null) return null;
        for (Char aChar : chars) {
            if (aChar.id == id) return aChar;
        }
        return blind;
    }

    private static class OptimizeBuffer{
        public final int id;
        public final float x;
        public final float y;
        public final float size;

        public OptimizeBuffer(int id, float x, float y, float size) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.size = size;
        }

        public boolean isEqual(int id, float x, float y, float size){
            return (this.id == id && this.x == x && this.y == y && this.size == size);
        }
    }

    private static class Kerning{
        public final int first;
        public final int second;
        public final int amount;

        public Kerning(int first, int second, int amount) {
            this.first = first;
            this.second = second;
            this.amount = amount;
        }
    }

    private static class Char{
        public final int id;
        public final int width;
        public final int height;
        public final int xOffset;
        public final int yOffset;
        public final int xAdvance;
        public final float tx0;
        public final float ty0;
        public final float tx1;
        public final float ty1;

        public Char(int id, int x, int y, int width, int height, int xOffset, int yOffset, int xAdvance, int textureWidth, int textureHeight) {
            this.id = id;
            this.width = width;
            this.height = height;
            this.xOffset = xOffset;
            this.yOffset = yOffset;
            this.xAdvance = xAdvance;
            tx0 = (float) x / textureWidth;
            tx1 = (float) (x + width) / textureWidth;
            ty0 = (float) y / textureHeight;
            ty1 = (float) (y + height) / textureHeight;
        }
    }
}
