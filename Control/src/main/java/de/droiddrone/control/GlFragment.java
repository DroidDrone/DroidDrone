package de.droiddrone.control;

import android.annotation.SuppressLint;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.view.View;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class GlFragment extends Fragment {
    public static final int fragmentId = 2;
    private final MainActivity activity;
    private final GlRenderer renderer;
    private GLSurfaceView glView;

    public GlFragment(MainActivity activity, GlRenderer renderer){
        super(R.layout.fragment_gl);
        this.activity = activity;
        this.renderer = renderer;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        RelativeLayout rlGl = activity.findViewById(R.id.rlGl);
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        glView = new GLSurfaceView(activity);
        glView.setLayoutParams(lp);
        glView.setEGLContextClientVersion(3);
        glView.setEGLConfigChooser(8, 8, 8, 8, 0, 0);
        glView.setRenderer(activity.getRenderer());
        glView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        glView.setPadding(0, 0, 0, 0);
        glView.setOnTouchListener(renderer.onTouchListener);
        rlGl.addView(glView);
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    public void close(){
        if (glView != null) glView.onPause();
    }

    public void resume(){
        if (glView != null) glView.onResume();
    }
}