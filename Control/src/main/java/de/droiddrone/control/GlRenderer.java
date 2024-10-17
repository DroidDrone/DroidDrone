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

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES31;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import static de.droiddrone.common.Logcat.log;

import java.util.ArrayList;

import de.droiddrone.common.SettingsCommon;

public class GlRenderer implements GLSurfaceView.Renderer {
    private GlBuffer videoFramePositionBuffer;
    private GlBuffer videoFrameTexCoordBuffer;
    private final float[] mvpMatrix = new float[16];
    private final float[] mViewMatrix = new float[16];
    private final float[] mProjectionMatrix = new float[16];
    private final float[] mProjectionMatrixRight = new float[16];
    private final float[] pvMatrix = new float[16];
    private final float[] mModelMatrix = new float[16];
    private final MainActivity activity;
    private int vrScreenWidth, vrScreenHeight;
    private int screenWidth, screenHeight;
    private int videoFrameShader, textShader, spritesShader;
    private int vfMVPMatrixHandle, vfTextureUniformHandle;
    private int textMVPMatrixHandle, textTextureUniformHandle;
    private int spritesMVPMatrixHandle, spritesTextureUniformHandle;
    private int videoFrameTexture;
    private boolean createVideoFrameTexture;
    private SurfaceTexture mSurfaceTexture;
    private Surface glSurface;
    private int glFrameCounter;
    private int lastFps;
    private long glFrameTimestamp;
    private Osd osd = null;
    private Udp udp = null;
    private GlText glText;
    private GlSprites glSprites;
    private GlButtons glButtons;
    private GlButtons.Button recButton;
    private float osdCanvasFactor;
    private float screenFactor;
    private final Config config;
    private LeftSidebar leftSidebar;
    private int videoFrameWidth;
    private int videoFrameHeight;
    private boolean isFrontCamera;
    private int vrMode;
    private float vrScale;
    private float vrOsdScale;
    private float vrCenterOffset;
    private float vrOsdOffset;
    private boolean drawOsd;
    private HeadTracker headTracker;
    private final float[] headTrackingAxes = new float[3];
    private boolean vrHeadTracking;
    private boolean headTrackingResetTouch;
    private int headTrackingResetTouchPointerId;
    private int screenWidthD2, screenHeightD2;
    private int vrScreenWidthD2, vrScreenHeightD2;
    private int screenWidthD4, screenHeightD4;

    public GlRenderer(MainActivity activity, Config config){
        this.activity = activity;
        this.config = config;
        osdCanvasFactor = 1;
        screenFactor = 1;
    }

    public void setHeadTracker(HeadTracker headTracker){
        this.headTracker = headTracker;
    }

    public void setGyroValues(float[] gyroAxes){
        if (!vrHeadTracking) return;
        int[] maxAngleAbs = {55, 40, 35};
        float[] angles = headTrackingAxes.clone();
        for (int i = 0; i < 3; i++) {
            angles[i] -= gyroAxes[i];
            if (angles[i] < -maxAngleAbs[i]) angles[i] = -maxAngleAbs[i];
            if (angles[i] > maxAngleAbs[i]) angles[i] = maxAngleAbs[i];
        }
        float xyAbs = Math.abs(gyroAxes[0]) + Math.abs(gyroAxes[1]);
        float zAbs = Math.abs(angles[2]);
        if (xyAbs > zAbs) xyAbs = zAbs;
        if (angles[2] > 0){
            angles[2] -= xyAbs;
        }else{
            angles[2] += xyAbs;
        }
        System.arraycopy(angles, 0, headTrackingAxes, 0, 3);
    }

    private void resetHeadTrackingAxes(){
        for (int i = 0; i < 3; i++){
            headTrackingAxes[i] = 0;
        }
    }

    public void setOsd(Osd osd){
        this.osd = osd;
    }

    public void setUdp(Udp udp){
        this.udp = udp;
    }

    public void setOsdCanvasFactor(float osdCanvasFactor){
        this.osdCanvasFactor = osdCanvasFactor;
        if (glText != null) glText.setOsdCanvasFactor(osdCanvasFactor);
        if (glSprites != null) glSprites.setOsdCanvasFactor(osdCanvasFactor);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        gl.glClearColor(0.2f, 0.2f, 0.2f, 1f);
        gl.glEnable(GLES31.GL_BLEND);
        gl.glDisable(GLES31.GL_DEPTH_TEST);
        gl.glBlendFunc(GLES31.GL_SRC_ALPHA, GLES31.GL_ONE_MINUS_SRC_ALPHA);
        gl.glPixelStorei(GLES31.GL_UNPACK_ALIGNMENT,1);
        final String vertexShader =
                "uniform mat4 u_MVPMatrix;                              \n"
                        + "attribute vec4 a_Position;                   \n"
                        + "attribute vec2 a_TexCoordinate;              \n"
                        + "varying vec2 v_TexCoordinate;                \n"
                        + "void main()                                  \n"
                        + "{                                            \n"
                        + "    gl_Position = u_MVPMatrix * a_Position;  \n"
                        + "    v_TexCoordinate = a_TexCoordinate;       \n"
                        + "}                                            \n";

        final String videoFragmentShader =
                "#extension GL_OES_EGL_image_external : require                         \n"
                        + "#ifdef GL_FRAGMENT_PRECISION_HIGH                            \n"
                        + "precision highp float;                                       \n"
                        + "#else                                                        \n"
                        + "precision mediump float;                                     \n"
                        + "#endif                                                       \n"
                        + "uniform samplerExternalOES u_Texture;                        \n"
                        + "varying vec2 v_TexCoordinate;	                            \n"
                        + "void main()                                                  \n"
                        + "{                                                            \n"
                        + "    gl_FragColor = texture2D(u_Texture, v_TexCoordinate);    \n"
                        + "}";
        final String textFragmentShader =
                "#ifdef GL_FRAGMENT_PRECISION_HIGH                                                  \n"
                        + "precision highp float;                                                   \n"
                        + "#else                                                                    \n"
                        + "precision mediump float;                                                 \n"
                        + "#endif                                                                   \n"
                        + "uniform sampler2D u_Texture;	                                            \n"
                        + "uniform vec4 u_Color;	                                                \n"
                        + "varying vec2 v_TexCoordinate;	                                        \n"
                        + "void main()                                                              \n"
                        + "{                                                                        \n"
                        + "    gl_FragColor = u_Color + texture2D(u_Texture, v_TexCoordinate);      \n"
                        + "}";
        final String spritesFragmentShader =
                "#ifdef GL_FRAGMENT_PRECISION_HIGH                                      \n"
                        + "precision highp float;                                       \n"
                        + "#else                                                        \n"
                        + "precision mediump float;                                     \n"
                        + "#endif                                                       \n"
                        + "uniform sampler2D u_Texture;	                                \n"
                        + "varying vec2 v_TexCoordinate;	                            \n"
                        + "void main()                                                  \n"
                        + "{                                                            \n"
                        + "    gl_FragColor = texture2D(u_Texture, v_TexCoordinate);    \n"
                        + "}";

        int vertexShaderHandle = GLES31.glCreateShader(GLES31.GL_VERTEX_SHADER);
        GLES31.glShaderSource(vertexShaderHandle, vertexShader);
        GLES31.glCompileShader(vertexShaderHandle);
        final int[] compileStatus = new int[1];
        GLES31.glGetShaderiv(vertexShaderHandle, GLES31.GL_COMPILE_STATUS, compileStatus, 0);
        if (compileStatus[0] == 0)
        {
            log("Vertex shader error: " + GLES31.glGetShaderInfoLog(vertexShaderHandle));
            GLES31.glDeleteShader(vertexShaderHandle);
        }

        int videoFragmentShaderHandle = GLES31.glCreateShader(GLES31.GL_FRAGMENT_SHADER);
        GLES31.glShaderSource(videoFragmentShaderHandle, videoFragmentShader);
        GLES31.glCompileShader(videoFragmentShaderHandle);
        GLES31.glGetShaderiv(videoFragmentShaderHandle, GLES31.GL_COMPILE_STATUS, compileStatus, 0);
        if (compileStatus[0] == 0)
        {
            log("Video fragment shader error: " + GLES31.glGetShaderInfoLog(videoFragmentShaderHandle));
            GLES31.glDeleteShader(videoFragmentShaderHandle);
        }

        int textFragmentShaderHandle = GLES31.glCreateShader(GLES31.GL_FRAGMENT_SHADER);
        GLES31.glShaderSource(textFragmentShaderHandle, textFragmentShader);
        GLES31.glCompileShader(textFragmentShaderHandle);
        GLES31.glGetShaderiv(textFragmentShaderHandle, GLES31.GL_COMPILE_STATUS, compileStatus, 0);
        if (compileStatus[0] == 0)
        {
            log("Text fragment shader error: " + GLES31.glGetShaderInfoLog(textFragmentShaderHandle));
            GLES31.glDeleteShader(textFragmentShaderHandle);
        }

        int spritesFragmentShaderHandle = GLES31.glCreateShader(GLES31.GL_FRAGMENT_SHADER);
        GLES31.glShaderSource(spritesFragmentShaderHandle, spritesFragmentShader);
        GLES31.glCompileShader(spritesFragmentShaderHandle);
        GLES31.glGetShaderiv(spritesFragmentShaderHandle, GLES31.GL_COMPILE_STATUS, compileStatus, 0);
        if (compileStatus[0] == 0)
        {
            log("Sprites fragment shader error: " + GLES31.glGetShaderInfoLog(spritesFragmentShaderHandle));
            GLES31.glDeleteShader(spritesFragmentShaderHandle);
        }

        videoFrameShader = GLES31.glCreateProgram();
        GLES31.glAttachShader(videoFrameShader, vertexShaderHandle);
        GLES31.glAttachShader(videoFrameShader, videoFragmentShaderHandle);
        GLES31.glBindAttribLocation(videoFrameShader, 0, "a_Position");
        GLES31.glBindAttribLocation(videoFrameShader, 1, "a_TexCoordinate");
        GLES31.glLinkProgram(videoFrameShader);

        textShader = GLES31.glCreateProgram();
        GLES31.glAttachShader(textShader, vertexShaderHandle);
        GLES31.glAttachShader(textShader, textFragmentShaderHandle);
        GLES31.glBindAttribLocation(textShader, 2, "a_Position");
        GLES31.glBindAttribLocation(textShader, 3, "a_TexCoordinate");
        GLES31.glLinkProgram(textShader);

        spritesShader = GLES31.glCreateProgram();
        GLES31.glAttachShader(spritesShader, vertexShaderHandle);
        GLES31.glAttachShader(spritesShader, spritesFragmentShaderHandle);
        GLES31.glBindAttribLocation(spritesShader, 4, "a_Position");
        GLES31.glBindAttribLocation(spritesShader, 5, "a_TexCoordinate");
        GLES31.glLinkProgram(spritesShader);

        vfMVPMatrixHandle = GLES31.glGetUniformLocation(videoFrameShader, "u_MVPMatrix");
        vfTextureUniformHandle = GLES31.glGetUniformLocation(videoFrameShader, "u_Texture");
        int vfPosition = GLES31.glGetAttribLocation(videoFrameShader, "a_Position");
        int vfTexture = GLES31.glGetAttribLocation(videoFrameShader, "a_TexCoordinate");

        textMVPMatrixHandle = GLES31.glGetUniformLocation(textShader, "u_MVPMatrix");
        textTextureUniformHandle = GLES31.glGetUniformLocation(textShader, "u_Texture");
        int textColorUniformHandle = GLES31.glGetUniformLocation(textShader, "u_Color");
        int textPosition = GLES31.glGetAttribLocation(textShader, "a_Position");
        int textTexture = GLES31.glGetAttribLocation(textShader, "a_TexCoordinate");

        spritesMVPMatrixHandle = GLES31.glGetUniformLocation(spritesShader, "u_MVPMatrix");
        spritesTextureUniformHandle = GLES31.glGetUniformLocation(spritesShader, "u_Texture");
        int spritesPosition = GLES31.glGetAttribLocation(spritesShader, "a_Position");
        int spritesTexture = GLES31.glGetAttribLocation(spritesShader, "a_TexCoordinate");

        final int bufferCount = 6;
        int[] buffers = new int[bufferCount];
        GLES31.glGenBuffers(bufferCount, buffers, 0);

        int videoFrameBufferSize = 4 * 2 * 4;// 4 points per rectangle; 2 coords per point; 4 bytes per coord
        videoFramePositionBuffer = new GlBuffer(buffers[0], videoFrameBufferSize, GLES31.GL_STATIC_DRAW, 2, vfPosition);
        videoFrameTexCoordBuffer = new GlBuffer(buffers[1], videoFrameBufferSize, GLES31.GL_STATIC_DRAW, 2, vfTexture);

        int maxSymbolsOnScreen = 400;
        int textBufferSize = maxSymbolsOnScreen * 6 * 2 * 4;// 6 points per rectangle * 2 coords per point * 4 bytes
        GlBuffer textPositionBuffer = new GlBuffer(buffers[2], textBufferSize, GLES31.GL_DYNAMIC_DRAW, 2, textPosition);
        GlBuffer textTexCoordBuffer = new GlBuffer(buffers[3], textBufferSize, GLES31.GL_DYNAMIC_DRAW, 2, textTexture);

        int maxSpritesOnScreen = 100;
        int spritesBufferSize = maxSpritesOnScreen * 6 * 2 * 4;// 6 points per rectangle * 2 coords per point * 4 bytes
        GlBuffer spritesPositionBuffer = new GlBuffer(buffers[4], spritesBufferSize, GLES31.GL_DYNAMIC_DRAW, 2, spritesPosition);
        GlBuffer spritesTexCoordBuffer = new GlBuffer(buffers[5], spritesBufferSize, GLES31.GL_DYNAMIC_DRAW, 2, spritesTexture);

        try {
            glText = new GlText(loadTextureFromResource(R.raw.font1_0), R.raw.font1, activity, textPositionBuffer, textTexCoordBuffer, textColorUniformHandle);
            glText.setOsdCanvasFactor(osdCanvasFactor);
        }catch (Exception e){
            log("Create GlFont error: " + e);
        }
        try {
            glSprites = new GlSprites(R.raw.osd_tex, R.raw.osd_map, activity, spritesPositionBuffer, spritesTexCoordBuffer);
            glSprites.setOsdCanvasFactor(osdCanvasFactor);
            glButtons = new GlButtons(glSprites);
        }catch (Exception e){
            log("Create GlSprites error: " + e);
        }

        glFrameCounter = 0;
        lastFps = 0;
        glFrameTimestamp = System.currentTimeMillis();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        vrMode = config.getVrMode();
        vrScale = config.getVrFrameScale() * 0.01f;
        vrOsdScale = config.getVrOsdScale() * 0.01f * vrScale;
        drawOsd = config.isDrawOsd();
        screenWidth = width;
        screenHeight = height;
        vrScreenWidth = width;
        vrScreenHeight = height;
        screenWidthD2 = width / 2;
        screenHeightD2 = height / 2;
        screenWidthD4 = width / 4;
        screenHeightD4 = height / 4;
        int osdScreenHeight = height;
        if (vrMode == SettingsCommon.VrMode.off) {
            Matrix.orthoM(mProjectionMatrix, 0, 0, screenWidth, 0, screenHeight, 0, 1);
        }else{
            vrScreenWidth = vrScreenWidth / 2;
            osdScreenHeight = Math.round(vrScreenHeight * 0.65f);
            Matrix.orthoM(mProjectionMatrix, 0, 0, vrScreenWidth, 0, vrScreenHeight, 0, 1);
            Matrix.orthoM(mProjectionMatrixRight, 0, 0, vrScreenWidth * 2, 0, vrScreenHeight, 0, 1);
        }
        if (vrScreenWidth > vrScreenHeight){
            screenFactor = vrScreenWidth / 1920f;
        }else{
            screenFactor = vrScreenHeight / 1920f;
        }
        vrScreenWidthD2 = vrScreenWidth / 2;
        vrScreenHeightD2 = vrScreenHeight / 2;
        vrCenterOffset = config.getVrCenterOffset() * screenFactor;
        vrOsdOffset = config.getVrOsdOffset() * screenFactor;

        int osdHeightOffset = (vrScreenHeight - osdScreenHeight) / 2;
        if (osd != null) osd.setScreenSize(vrScreenWidth, osdScreenHeight, osdHeightOffset, screenFactor);

        GLES31.glViewport(0, 0, screenWidth, screenHeight);
        Matrix.setLookAtM(mViewMatrix, 0, 0, 0, 1, 0, 0, -1, 0, 1, 0);
        Matrix.multiplyMM(pvMatrix, 0, mProjectionMatrix, 0, mViewMatrix, 0);
        Matrix.setIdentityM(mModelMatrix, 0);
        Matrix.multiplyMM(mvpMatrix, 0, pvMatrix, 0, mModelMatrix, 0);

        resetHeadTrackingAxes();
        vrHeadTracking = config.isVrHeadTracking();

        if (mSurfaceTexture == null) {
            createVideoFrameTexture();
        }

        if (glButtons != null){
            glButtons.clear();
            if (vrMode == SettingsCommon.VrMode.off && drawOsd) {
                if (config.isShowVideoRecordButton() && !config.isViewer()) {
                    recButton = glButtons.registerButton(screenWidth - Osd.rawRecButtonOffset * screenFactor,
                            screenHeight, Osd.rawPhoneOsdHeight * screenFactor, Osd.rawPhoneOsdHeight * screenFactor,
                            SpritesMapping.BUTTON_REC, SpritesMapping.BUTTON_REC_DOWN, SpritesMapping.BUTTON_STOP, SpritesMapping.BUTTON_STOP_DOWN, false);
                    recButton.setOnClickListener(new GlButtons.OnClickListener() {
                        @Override
                        void onClick(GlButtons.Button button) {
                            boolean startRecording = (button.getCurrentState() == 0);
                            if (udp != null) udp.sendStartStopRecording(startRecording);
                        }
                    });
                }

                // left sidebar buttons
                float buttonsPadding = 5 * screenFactor;
                float buttonsSize = 70 * screenFactor;
                float buttonsY = screenHeight - Osd.rawPhoneOsdHeight * screenFactor - buttonsPadding - 100 * screenFactor;
                boolean isHidden = true;

                if (leftSidebar != null) isHidden = leftSidebar.isHidden();
                leftSidebar = new LeftSidebar(0, buttonsPadding + buttonsSize, SpritesMapping.LEFT_SIDEBAR_1, SpritesMapping.LEFT_SIDEBAR_2, isHidden);

                GlButtons.Button mapButton = glButtons.registerButton(buttonsPadding, buttonsY,
                        buttonsSize, buttonsSize,
                        SpritesMapping.BUTTON_MAP_1, SpritesMapping.BUTTON_MAP_2, 0, 0, false);
                leftSidebar.addButton(mapButton);
                mapButton.setOnClickListener(new GlButtons.OnClickListener() {
                    @Override
                    void onClick(GlButtons.Button button) {
                        activity.showMapFragment();
                    }
                });

                buttonsY -= buttonsSize + buttonsPadding;
                GlButtons.Button settingsButton = glButtons.registerButton(buttonsPadding, buttonsY,
                        buttonsSize, buttonsSize,
                        SpritesMapping.BUTTON_SETTINGS_1, SpritesMapping.BUTTON_SETTINGS_2, 0, 0, false);
                leftSidebar.addButton(settingsButton);
                settingsButton.setOnClickListener(new GlButtons.OnClickListener() {
                    @Override
                    void onClick(GlButtons.Button button) {
                        activity.showSettingsFragment();
                    }
                });

                if (!config.isViewer() && config.getCamerasCount() > 1) {
                    buttonsY -= buttonsSize + buttonsPadding;
                    GlButtons.Button cameraButton = glButtons.registerButton(buttonsPadding, buttonsY,
                            buttonsSize, buttonsSize,
                            SpritesMapping.BUTTON_CAMERA_1, SpritesMapping.BUTTON_CAMERA_2, 0, 0, false);
                    leftSidebar.addButton(cameraButton);
                    cameraButton.setOnClickListener(new GlButtons.OnClickListener() {
                        @Override
                        void onClick(GlButtons.Button button) {
                            udp.sendChangeCamera();
                            surfaceReset();
                            activity.stopDecoder();
                        }
                    });
                }
            }
        }

        if (glText != null){
            glText.clearCache();
            glText.setScreenFactor(screenFactor);
            int color = config.getOsdTextColor();
            int a = color >> 24 & 0xFF;
            int r = color >> 16 & 0xFF;
            int g = color >> 8 & 0xFF;
            int b = color & 0xFF;
            glText.setColor(r, g, b, a);
        }
        if (glSprites != null){
            glSprites.setScreenFactor(screenFactor);
        }
        videoFramePositionBuffer.update(true);
        videoFrameTexCoordBuffer.update(true);
    }

    private class LeftSidebar{
        private final float moveSpeed = 1 * screenFactor;
        private final float sidebarSpriteWidth = 14 * screenFactor;
        private final float sidebarSpriteHeight = 60 * screenFactor;
        private final float spriteOffsetX = 2 * screenFactor;
        private final float xMin;
        private final float xMax;
        private final float xTouchMax;
        private final int spriteMin;
        private final int spriteMax;
        private final float spriteY;
        private float xCurrent;
        private float xTouch;
        private boolean isHidden;
        private boolean isTouch;
        private int touchPointerId;
        private final ArrayList<GlButtons.Button> buttons = new ArrayList<>();

        public LeftSidebar(float xMin, float xMax, int spriteMin, int spriteMax, boolean isHidden) {
            this.xMin = xMin;
            this.xMax = xMax;
            this.spriteMin = spriteMin;
            this.spriteMax = spriteMax;
            this.isHidden = isHidden;
            if (isHidden) {
                xCurrent = xMin;
            }else{
                xCurrent = xMax;
            }
            isTouch = false;
            spriteY = screenHeight / 2f + sidebarSpriteHeight / 2f;
            xTouchMax = xMax + sidebarSpriteWidth + spriteOffsetX;
        }

        public boolean isHidden() {
            return isHidden;
        }

        public void addButton(GlButtons.Button button){
            if (button != null) buttons.add(button);
        }

        public void processSidebar() {
            int spriteId;
            if (isHidden) {
                spriteId = spriteMin;
                if (!isTouch && xCurrent > xMin) xCurrent -= moveSpeed;
            } else {
                spriteId = spriteMax;
                if (!isTouch && xCurrent < xMax) xCurrent += moveSpeed;
            }
            for (GlButtons.Button button : buttons) {
                button.moveTo(xCurrent - button.getWidth(), button.getY());
            }
            if (glSprites != null) {
                glSprites.addSpriteSizeOverride(spriteId, xCurrent + spriteOffsetX, spriteY, sidebarSpriteWidth, sidebarSpriteHeight);
            }
        }

        public void touchDown(float xTouch, int pointerId){
            if (xTouch > xTouchMax || isTouch && pointerId != touchPointerId) return;
            this.xTouch = xTouch;
            this.touchPointerId = pointerId;
            isTouch = true;
        }

        public void move(float xTouch, int pointerId){
            if (!isTouch || pointerId != touchPointerId) return;
            float offset = xTouch - this.xTouch;
            this.xTouch = xTouch;
            if (offset > 0){
                if (xCurrent + offset < xMax){
                    xCurrent += offset;
                }else{
                    xCurrent = xMax;
                }
            }else{
                if (xCurrent + offset > xMin){
                    xCurrent += offset;
                }else{
                    xCurrent = xMin;
                }
            }
        }

        public void touchUp(int pointerId){
            if (!isTouch || pointerId != touchPointerId) return;
            isTouch = false;
            float min = xMin > 0 ? xMin : 0;
            float middle = (min + xMax) / 2;
            isHidden = !(xCurrent >= middle);
        }
    }

    private float getHeadTrackingOffsetX(){
        if (!vrHeadTracking) return 0;
        int offsetFactor = 32;
        float scale = vrScale;
        if (scale > 1) scale = 1 + (scale - 1) / 2f;
        return headTrackingAxes[0] * screenFactor * scale * offsetFactor;
    }

    private float getHeadTrackingOffsetY(){
        if (!vrHeadTracking) return 0;
        int offsetFactor = 32;
        float scale = vrScale;
        if (scale > 1) scale = 1 + (scale - 1) / 2f;
        return headTrackingAxes[1] * screenFactor * scale * offsetFactor;
    }

    private void setLeftVrDisplay(){
        GLES31.glViewport(0, 0, vrScreenWidth, vrScreenHeight);
        Matrix.setIdentityM(mModelMatrix, 0);
        Matrix.rotateM(mModelMatrix, 0, headTrackingAxes[2], 0, 0, 1);
        Matrix.translateM(mModelMatrix, 0, -vrScreenWidthD2 * vrScale, -vrScreenHeightD2 * vrScale, 0);
        Matrix.scaleM(mModelMatrix, 0, vrScale, vrScale, 1);
        float x = -vrScreenWidthD2 - vrCenterOffset + getHeadTrackingOffsetX();
        float y = -vrScreenHeightD2 + getHeadTrackingOffsetY();
        Matrix.setLookAtM(mViewMatrix, 0, x, y, 1, x, y, -1, 0, 1, 0);
        Matrix.multiplyMM(pvMatrix, 0, mProjectionMatrix, 0, mViewMatrix, 0);
        Matrix.multiplyMM(mvpMatrix, 0, pvMatrix, 0, mModelMatrix, 0);
        if (vrMode == SettingsCommon.VrMode.stereoCamera){
            float[] textureBufferArray = {0, 1, 0.5f, 1, 0, 0, 0.5f, 0};
            if (isFrontCamera != config.isInvertVideoAxisX()) {
                textureBufferArray[0] = 0.5f;
                textureBufferArray[2] = 0;
                textureBufferArray[4] = 0.5f;
                textureBufferArray[6] = 0;
            }
            if (config.isInvertVideoAxisY()){
                textureBufferArray[1] = 0;
                textureBufferArray[3] = 0;
                textureBufferArray[5] = 1;
                textureBufferArray[7] = 1;
            }
            videoFrameTexCoordBuffer.putArray(0, textureBufferArray);
        }
    }

    private void setRightVrDisplay(){
        GLES31.glViewport(vrScreenWidth, 0, vrScreenWidth * 2, vrScreenHeight);
        Matrix.setIdentityM(mModelMatrix, 0);
        Matrix.rotateM(mModelMatrix, 0, headTrackingAxes[2], 0, 0, 1);
        Matrix.translateM(mModelMatrix, 0, -vrScreenWidthD2 * vrScale, -vrScreenHeightD2 * vrScale, 0);
        Matrix.scaleM(mModelMatrix, 0, vrScale, vrScale, 1);
        float x = -vrScreenWidthD2 + vrCenterOffset + getHeadTrackingOffsetX();
        float y = -vrScreenHeightD2 + getHeadTrackingOffsetY();
        Matrix.setLookAtM(mViewMatrix, 0, x, y, 1, x, y, -1, 0, 1, 0);
        Matrix.multiplyMM(pvMatrix, 0, mProjectionMatrixRight, 0, mViewMatrix, 0);
        Matrix.multiplyMM(mvpMatrix, 0, pvMatrix, 0, mModelMatrix, 0);
        if (vrMode == SettingsCommon.VrMode.stereoCamera) {
            float[] textureBufferArray = {0.5f, 1, 1, 1, 0.5f, 0, 1, 0};
            if (isFrontCamera != config.isInvertVideoAxisX()) {
                textureBufferArray[0] = 1;
                textureBufferArray[2] = 0.5f;
                textureBufferArray[4] = 1;
                textureBufferArray[6] = 0.5f;
            }
            if (config.isInvertVideoAxisY()) {
                textureBufferArray[1] = 0;
                textureBufferArray[3] = 0;
                textureBufferArray[5] = 1;
                textureBufferArray[7] = 1;
            }
            videoFrameTexCoordBuffer.putArray(0, textureBufferArray);
        }
    }

    private void setLeftOsdOffset(){
        Matrix.setIdentityM(mModelMatrix, 0);
        Matrix.rotateM(mModelMatrix, 0, headTrackingAxes[2], 0, 0, 1);
        Matrix.translateM(mModelMatrix, 0, -vrScreenWidthD2 * vrOsdScale, -vrScreenHeightD2 * vrOsdScale, 0);
        Matrix.scaleM(mModelMatrix, 0, vrOsdScale, vrOsdScale, 1);
        float x = -vrScreenWidthD2 - vrCenterOffset - vrOsdOffset + getHeadTrackingOffsetX();
        float y = -vrScreenHeightD2 + getHeadTrackingOffsetY();
        Matrix.setLookAtM(mViewMatrix, 0, x, y, 1, x, y, -1, 0, 1, 0);
        Matrix.multiplyMM(pvMatrix, 0, mProjectionMatrix, 0, mViewMatrix, 0);
        Matrix.multiplyMM(mvpMatrix, 0, pvMatrix, 0, mModelMatrix, 0);
    }

    private void setRightOsdOffset(){
        Matrix.setIdentityM(mModelMatrix, 0);
        Matrix.rotateM(mModelMatrix, 0, headTrackingAxes[2], 0, 0, 1);
        Matrix.translateM(mModelMatrix, 0, -vrScreenWidthD2 * vrOsdScale, -vrScreenHeightD2 * vrOsdScale, 0);
        Matrix.scaleM(mModelMatrix, 0, vrOsdScale, vrOsdScale, 1);
        float x = -vrScreenWidthD2 + vrCenterOffset + vrOsdOffset + getHeadTrackingOffsetX();
        float y = -vrScreenHeightD2 + getHeadTrackingOffsetY();
        Matrix.setLookAtM(mViewMatrix, 0, x, y, 1, x, y, -1, 0, 1, 0);
        Matrix.multiplyMM(pvMatrix, 0, mProjectionMatrixRight, 0, mViewMatrix, 0);
        Matrix.multiplyMM(mvpMatrix, 0, pvMatrix, 0, mModelMatrix, 0);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        try {
            if (createVideoFrameTexture) createVideoFrameTexture();

            glFrameCounter++;
            GLES31.glClear(GLES31.GL_COLOR_BUFFER_BIT);
            if (drawOsd) prepareOsdFrame();

            switch (vrMode){
                case SettingsCommon.VrMode.off:
                default:
                    drawVideoFrame();
                    if (drawOsd) {
                        if (leftSidebar != null) leftSidebar.processSidebar();
                        if (glButtons != null) glButtons.prepareFrame();
                        drawOsdFrame(true);
                    }
                    break;
                case SettingsCommon.VrMode.singleCamera:
                case SettingsCommon.VrMode.stereoCamera:
                    setLeftVrDisplay();
                    drawVideoFrame();
                    if (drawOsd) {
                        setLeftOsdOffset();
                        drawOsdFrame(false);
                    }
                    setRightVrDisplay();
                    drawVideoFrame();
                    if (drawOsd) {
                        setRightOsdOffset();
                        drawOsdFrame(true);
                    }
                    break;
            }
        }catch (Exception e){
            //
        }
    }

    public void setRecButtonState(boolean isRecording){
        if (recButton == null) return;
        recButton.setCurrentState(isRecording ? 1 : 0);
    }

    private void prepareOsdFrame(){
        if (osd == null) return;
        try {
            osd.drawItems();
        }catch (Exception e){
            //
        }
    }

    public int getFps(){
        long current = System.currentTimeMillis();
        float timeSec = (current - glFrameTimestamp) / 1000f;
        if (timeSec < 0.2f && lastFps > 0) return lastFps;
        int fps = Math.round(glFrameCounter / timeSec);
        glFrameCounter = 0;
        glFrameTimestamp = current;
        lastFps = fps;
        return fps;
    }

    private void processHeadTrackingResetDown(float x, float y, int pointerId){
        if (x >= screenWidthD2 - screenWidthD4 && x <= screenWidthD2 + screenWidthD4
                && y >= screenHeightD2 - screenHeightD4 && y <= screenHeightD2 + screenHeightD4){
            headTrackingResetTouch = true;
            headTrackingResetTouchPointerId = pointerId;
        }
    }

    private void processHeadTrackingResetUp(float x, float y, int pointerId){
        if (!headTrackingResetTouch || pointerId != headTrackingResetTouchPointerId) return;
        headTrackingResetTouch = false;
        if (x >= screenWidthD2 - screenWidthD4 && x <= screenWidthD2 + screenWidthD4
                && y >= screenHeightD2 - screenHeightD4 && y <= screenHeightD2 + screenHeightD4){
            resetHeadTrackingAxes();
            headTracker.resetRcChannels();
        }
    }

    public View.OnTouchListener onTouchListener = new View.OnTouchListener() {

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            int pc = event.getPointerCount();
            int pi = event.getActionIndex();
            int pointerId = event.getPointerId(pi);
            float x = event.getX(pi);
            float y = screenHeight - event.getY(pi);
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN:
                    if (glButtons != null) glButtons.processTouchDown(x, y, pointerId);
                    if (leftSidebar != null) leftSidebar.touchDown(x, pointerId);
                    processHeadTrackingResetDown(x, y, pointerId);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP:
                    if (glButtons != null) glButtons.processTouchUp(x, y, pointerId);
                    if (leftSidebar != null) leftSidebar.touchUp(pointerId);
                    processHeadTrackingResetUp(x, y, pointerId);
                    break;
                case MotionEvent.ACTION_MOVE:
                    for (int i = 0; i < pc; i++) {
                        float mX = event.getX(i);
                        float mY = screenHeight - event.getY(i);
                        int mPointerId = event.getPointerId(i);
                        if (leftSidebar != null) leftSidebar.move(mX, mPointerId);
                    }
                    break;
                case MotionEvent.ACTION_CANCEL:
                    break;
            }
            return true;
        }
    };

    public GlText getGlTextObject(){
        return glText;
    }

    public GlSprites getGlSpritesObject(){
        return glSprites;
    }

    public void addSpriteWithText(float x, float y, int spriteName, String text, boolean spriteFirst, boolean addWarning){
        float xOffset = 0;
        if (spriteFirst) {
            if (glSprites != null) {
                if (addWarning){
                    glSprites.addSprite(SpritesMapping.ALERT, x, y);
                    xOffset += glSprites.getSpriteWidth(SpritesMapping.ALERT) + 5 * screenFactor;
                }
                glSprites.addSprite(spriteName, x + xOffset, y);
                xOffset += glSprites.getSpriteWidth(spriteName);
            }
            text = " " + text;
            if (glText != null) glText.addText(text, x + xOffset, y);
        }else{
            if (glText != null) {
                text = text + " ";
                glText.addText(text, x, y);
                xOffset = glText.getLengthInPixels(text);
            }
            if (glSprites != null) {
                glSprites.addSprite(spriteName, x + xOffset, y);
                if (addWarning){
                    xOffset += glSprites.getSpriteWidth(spriteName) + 5 * screenFactor;
                    glSprites.addSprite(SpritesMapping.ALERT, x + xOffset, y);
                }
            }
        }
    }

    public void addText(String text, float x, float y){
        if (glText != null) glText.addText(text, x, y);
    }

    public void addText(String text, float x, float y, boolean addWarning){
        float xOffset = 0;
        if (glSprites != null) {
            if (addWarning){
                glSprites.addSprite(SpritesMapping.ALERT, x, y);
                xOffset += glSprites.getSpriteWidth(SpritesMapping.ALERT) + 5 * screenFactor;
            }
        }
        if (glText != null) glText.addText(text, x + xOffset, y);
    }

    private void drawOsdFrame(boolean clear){
        if (glSprites != null) {
            GLES31.glUseProgram(spritesShader);
            GLES31.glUniformMatrix4fv(spritesMVPMatrixHandle, 1, false, mvpMatrix, 0);
            GLES31.glUniform1i(spritesTextureUniformHandle, 0);
            glSprites.draw();
            if (clear) glSprites.clear();
        }
        if (glText != null) {
            GLES31.glUseProgram(textShader);
            GLES31.glUniformMatrix4fv(textMVPMatrixHandle, 1, false, mvpMatrix, 0);
            GLES31.glUniform1i(textTextureUniformHandle, 0);
            glText.draw();
            if (clear) glText.clear();
        }
    }

    private void drawVideoFrame(){
        if (videoFrameTexture == 0 || mSurfaceTexture == null) return;
        try{
            mSurfaceTexture.updateTexImage();
        }catch (Exception e){
            log("drawVideoFrame - updateTexImage error: " + e);
            return;
        }
        videoFramePositionBuffer.update(false);
        videoFrameTexCoordBuffer.update(false);
        GLES31.glUseProgram(videoFrameShader);
        GLES31.glUniformMatrix4fv(vfMVPMatrixHandle, 1, false, mvpMatrix, 0);
        GLES31.glUniform1i(vfTextureUniformHandle, 0);
        GLES31.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, videoFrameTexture);
        GLES31.glDrawArrays(GLES31.GL_TRIANGLE_STRIP, 0, 4);
        GLES31.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
    }

    private int loadTextureFromResource(int resourceId){
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        GLES31.glActiveTexture(GLES31.GL_TEXTURE0);
        int[] t = new int[1];
        GLES31.glGenTextures(1, t, 0);
        GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, t[0]);
        GLES31.glTexParameterf(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_MIN_FILTER,GLES31.GL_LINEAR);
        GLES31.glTexParameterf(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_MAG_FILTER,GLES31.GL_LINEAR);
        GLES31.glTexParameterf(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_WRAP_S, GLES31.GL_CLAMP_TO_EDGE);
        GLES31.glTexParameterf(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_WRAP_T, GLES31.GL_CLAMP_TO_EDGE);
        Bitmap bitmap = BitmapFactory.decodeResource(activity.getResources(), resourceId, options);
        GLUtils.texImage2D(GLES31.GL_TEXTURE_2D, 0, bitmap, 0);
        bitmap.recycle();
        return t[0];
    }

    private int createExternalOesTexture() {
        GLES31.glActiveTexture(GLES31.GL_TEXTURE0);
        int[] t = new int[1];
        GLES31.glGenTextures(1, t, 0);
        GLES31.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, t[0]);
        GLES31.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES31.GL_TEXTURE_MIN_FILTER, GLES31.GL_LINEAR);
        GLES31.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES31.GL_TEXTURE_MAG_FILTER, GLES31.GL_LINEAR);
        GLES31.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES31.GL_TEXTURE_WRAP_S, GLES31.GL_CLAMP_TO_EDGE);
        GLES31.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES31.GL_TEXTURE_WRAP_T, GLES31.GL_CLAMP_TO_EDGE);
        GLES31.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
        return t[0];
    }

    private void createVideoFrameTexture(){
        int texId = createExternalOesTexture();
        mSurfaceTexture = new SurfaceTexture(texId);
        glSurface = new Surface(mSurfaceTexture);
        videoFrameTexture = texId;
        createVideoFrameTexture = false;
    }

    public void setVideoFrameSize(int width, int height, boolean isFront) {
        mSurfaceTexture.setDefaultBufferSize(width, height);
        videoFrameWidth = width;
        if (vrMode == SettingsCommon.VrMode.stereoCamera) videoFrameWidth = width / 2;
        videoFrameHeight = height;
        isFrontCamera = isFront;
        updateVideoFrameOrientation();
    }

    private void updateVideoFrameOrientation(){
        int width = videoFrameWidth;
        int height = videoFrameHeight;
        float videoRatio = (float) width / height;
        float screenRatio = (float) vrScreenWidth / vrScreenHeight;
        int wOffset = 0;
        int hOffset = 0;
        if (screenRatio > videoRatio) {
            width = Math.round(width * (float) vrScreenHeight / height);
            height = vrScreenHeight;
            wOffset = (vrScreenWidth - width) / 2;
        } else {
            height = Math.round(height * (float) vrScreenWidth / width);
            width = vrScreenWidth;
            hOffset = (vrScreenHeight - height) / 2;
        }
        float[] buf8 = new float[8];
        buf8[0] = wOffset;
        buf8[1] = hOffset;
        buf8[2] = width + wOffset;
        buf8[3] = hOffset;
        buf8[4] = wOffset;
        buf8[5] = height + hOffset;
        buf8[6] = width + wOffset;
        buf8[7] = height + hOffset;
        videoFramePositionBuffer.putArray(0, buf8);

        float[] textureBufferArray = {0, 1, 1, 1, 0, 0, 1, 0};
        if (isFrontCamera != config.isInvertVideoAxisX()) {
            textureBufferArray[0] = 1;
            textureBufferArray[2] = 0;
            textureBufferArray[4] = 1;
            textureBufferArray[6] = 0;
        }
        if (config.isInvertVideoAxisY()){
            textureBufferArray[1] = 0;
            textureBufferArray[3] = 0;
            textureBufferArray[5] = 1;
            textureBufferArray[7] = 1;
        }
        videoFrameTexCoordBuffer.putArray(0, textureBufferArray);
    }

    public Surface getSurface(){
        return glSurface;
    }

    private void surfaceReset(){
        close();
        createVideoFrameTexture = true;
    }

    public void close(){
        videoFrameTexture = 0;

        if (glSurface != null){
            glSurface.release();
            glSurface = null;
        }
        if (mSurfaceTexture != null) {
            mSurfaceTexture.release();
            mSurfaceTexture = null;
        }
    }
}
