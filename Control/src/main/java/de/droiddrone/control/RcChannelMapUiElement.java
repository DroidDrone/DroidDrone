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

        lp = new LinearLayout.LayoutParams((int) (100 * scale), FrameLayout.LayoutParams.MATCH_PARENT);
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

        String text = "CH" + (channelId + 1);
        switch (channelId){
            case 0:
                text += " (A - Roll): ";
                break;
            case 1:
                text += " (E - Pitch): ";
                break;
            case 2:
                text += " (R - Yaw): ";
                break;
            case 3:
                text += " (T - Throttle): ";
                break;
            default:
                text += ": ";
        }
        tvChannel.setText(text);
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
        if (code == -1){
            tvCode.setText(activity.getResources().getString(R.string.not_set));
        }else if (code >= Rc.KEY_CODE_OFFSET){
            tvCode.setText("Key - " + (code - Rc.KEY_CODE_OFFSET));
        }else{
            tvCode.setText("Axis - " + code);
        }
    }

    static abstract class OnClickListener{
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
