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

import java.util.ArrayList;

public class GlButtons {
    private final GlSprites glSprites;
    private final ArrayList<Button> buttons = new ArrayList<>();

    public GlButtons(GlSprites glSprites) {
        this.glSprites = glSprites;
    }

    public Button registerButton(float x, float y, float width, float height, int state1SpriteId, int state1SpriteDownId, int state2SpriteId, int state2SpriteDownId, boolean changeStateOnClick){
        Button button = new Button(x, y, width, height, state1SpriteId, state1SpriteDownId, state2SpriteId, state2SpriteDownId, changeStateOnClick);
        buttons.add(button);
        return button;
    }

    public void processTouchDown(float x, float y, int pointerId){
        for (Button button : buttons){
            if (isTouch(x, y, button)){
                button.isDown = true;
                button.touchPointerId = pointerId;
            }
        }
    }

    public void processTouchUp(float x, float y, int pointerId){
        for (Button button : buttons){
            if (isTouch(x, y, button) && button.isDown && button.touchPointerId == pointerId){
                if (button.changeStateOnClick){
                    int currentState = button.currentState;
                    button.currentState = currentState == 0 ? 1 : 0;
                }
                button.onClickListener.onClick(button);
            }
            if (button.touchPointerId == pointerId){
                button.touchPointerId = -1;
                button.isDown = false;
            }
        }
    }

    private boolean isTouch(float touchX, float touchY, Button button){
        if (button == null || touchX < 0) return false;
        return (touchX >= button.x && touchX <= button.x + button.width && touchY <= button.y && touchY >= button.y - button.height);
    }

    public void prepareFrame(){
        for (Button button : buttons){
            int state = button.currentState;
            int spriteId = button.isDown ? button.spriteDown[state] : button.sprite[state];
            glSprites.addSpriteSizeOverride(spriteId, button.x, button.y, button.width, button.height);
        }
    }

    public void clear(){
        buttons.clear();
    }

    public static abstract class OnClickListener{
        abstract void onClick(Button button);
    }

    public static final class Button{
        private final int stateCount = 2;
        private float x;
        private float y;
        private final float width;
        private final float height;
        private final int[] sprite = new int[stateCount];
        private final int[] spriteDown = new int[stateCount];
        private final boolean changeStateOnClick;
        private int currentState;
        private int touchPointerId;
        private boolean isDown;
        private OnClickListener onClickListener;

        public Button(float x, float y, float width, float height, int state1Sprite, int state1SpriteDown, int state2Sprite, int state2SpriteDown, boolean changeStateOnClick) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.changeStateOnClick = changeStateOnClick;
            sprite[0] = state1Sprite;
            spriteDown[0] = state1SpriteDown;
            sprite[1] = state2Sprite;
            spriteDown[1] = state2SpriteDown;
            currentState = 0;
            touchPointerId = -1;
            isDown = false;
        }

        public int getCurrentState(){
            return currentState;
        }

        public void setCurrentState(int newState){
            if (newState < 0 || newState >= stateCount) return;
            currentState = newState;
        }

        public void setOnClickListener(OnClickListener onClickListener){
            this.onClickListener = onClickListener;
        }

        public void moveTo(float x, float y){
            this.x = x;
            this.y = y;
        }

        public float getX(){
            return x;
        }

        public float getY(){
            return y;
        }

        public float getWidth(){
            return width;
        }

        public float getHeight(){
            return height;
        }
    }
}
