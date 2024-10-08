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

import android.graphics.Color;
import android.graphics.Typeface;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

public class RcChannelMapUiElement {
    private final TextView tvChannel, tvCode;
    private final ProgressBar bar;
    private final MainActivity activity;
    private int code;
    private boolean selected;
    private OnClickListener onClickListener;

    public RcChannelMapUiElement(MainActivity activity, LinearLayout parent, int channelId, int code) {
        this.activity = activity;
        this.code = code;
        selected = false;
        final float scale = activity.getResources().getDisplayMetrics().density;
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setLayoutParams(lp);

        lp = new LinearLayout.LayoutParams((int) (150 * scale), FrameLayout.LayoutParams.MATCH_PARENT);
        LinearLayout lChannel = new LinearLayout(activity);
        lChannel.setOrientation(LinearLayout.HORIZONTAL);
        lChannel.setLayoutParams(lp);

        lp = new LinearLayout.LayoutParams((int) (150 * scale), FrameLayout.LayoutParams.MATCH_PARENT);
        LinearLayout lCode = new LinearLayout(activity);
        lCode.setOrientation(LinearLayout.HORIZONTAL);
        lCode.setLayoutParams(lp);

        lp = new LinearLayout.LayoutParams((int) (300 * scale), FrameLayout.LayoutParams.MATCH_PARENT);
        LinearLayout lBar = new LinearLayout(activity);
        lBar.setOrientation(LinearLayout.HORIZONTAL);
        lBar.setLayoutParams(lp);

        lp = new LinearLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        tvChannel = new TextView(activity);
        tvCode = new TextView(activity);
        bar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);

        tvChannel.setText(ChannelsMappingFragment.getChannelName(channelId));
        tvChannel.setTextColor(Color.BLACK);
        tvChannel.setBackgroundColor(0x00000000);
        tvChannel.setSingleLine(true);
        tvChannel.setPadding((int) (10 * scale), (int) (10 * scale), (int) (10 * scale), (int) (10 * scale));
        tvChannel.setLayoutParams(lp);
        layout.setOnClickListener(view -> {
            selected = !selected;
            if (selected){
                tvChannel.setTypeface(null, Typeface.BOLD);
                tvCode.setTypeface(null, Typeface.BOLD);
            }else{
                tvChannel.setTypeface(null, Typeface.NORMAL);
                tvCode.setTypeface(null, Typeface.NORMAL);
            }
            onClickListener.onClick(channelId, selected);
        });
        lChannel.addView(tvChannel);

        updateCodeUi();
        tvCode.setTextColor(Color.BLACK);
        tvCode.setBackgroundColor(0x00000000);
        tvCode.setSingleLine(true);
        tvCode.setPadding((int) (10 * scale), (int) (10 * scale), (int) (10 * scale), (int) (10 * scale));
        tvCode.setLayoutParams(lp);
        lCode.addView(tvCode);

        bar.setIndeterminate(false);
        bar.setProgress(0);
        bar.setLayoutParams(lp);
        lBar.addView(bar);

        layout.addView(lChannel);
        layout.addView(lCode);
        layout.addView(lBar);
        parent.addView(layout);
    }

    public void setCode(int code){
        this.code = code;
        updateCodeUi();
    }

    private void updateCodeUi(){
        if (code >= 0 && code < Rc.KEY_CODE_OFFSET){
            tvCode.setText(activity.getResources().getString(R.string.rc_axis, code));
        }else if (code >= Rc.KEY_CODE_OFFSET && code < Rc.HEAD_TRACKING_CODE_OFFSET){
            tvCode.setText(activity.getResources().getString(R.string.rc_key, code - Rc.KEY_CODE_OFFSET));
        }else if (code >= Rc.HEAD_TRACKING_CODE_OFFSET && code < Rc.HEAD_TRACKING_CODE_OFFSET + 3){
            resetFocus();
            switch (code - Rc.HEAD_TRACKING_CODE_OFFSET){
                case 0:
                    tvCode.setText(activity.getResources().getString(R.string.rc_head_tracking_x));
                    break;
                case 1:
                    tvCode.setText(activity.getResources().getString(R.string.rc_head_tracking_y));
                    break;
                case 2:
                    tvCode.setText(activity.getResources().getString(R.string.rc_head_tracking_z));
                    break;
            }
        }else{
            tvCode.setText(activity.getResources().getString(R.string.not_set));
        }
    }

    public static abstract class OnClickListener{
        abstract void onClick(int channelId, boolean selected);
    }

    public void setOnClickListener(OnClickListener onClickListener){
        this.onClickListener = onClickListener;
    }

    public void setChannelLevel(int value){
        int percent = Math.round(((float)value - Rc.MIN_CHANNEL_LEVEL) / (Rc.MAX_CHANNEL_LEVEL - Rc.MIN_CHANNEL_LEVEL) * 95f) + 5;
        bar.setProgress(percent);
    }

    public void resetFocus(){
        selected = false;
        tvChannel.setTypeface(null, Typeface.NORMAL);
        tvCode.setTypeface(null, Typeface.NORMAL);
    }
}
