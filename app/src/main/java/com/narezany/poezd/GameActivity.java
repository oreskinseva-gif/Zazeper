package com.narezany.poezd;

import android.app.Activity;
import android.app.ActivityManager;
import android.graphics.Color;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;

public class GameActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CrashLog.init(this);
        CrashLog.mark("onCreate start");
        hideBars();

        GameWorld world = new GameWorld(this);
        CrashLog.mark("world created, scenery=" + world.scenery.size());
        ControlsView controls = new ControlsView(this, world);
        GLSurfaceView gl = new GLSurfaceView(this);

        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        int gles = am.getDeviceConfigurationInfo().reqGlEsVersion;
        int version = gles >= 0x30000 ? 3 : 2;
        CrashLog.mark("GLES version from device: " + Integer.toHexString(gles) + " -> use " + version);
        gl.setEGLContextClientVersion(version);
        gl.setRenderer(new GameRenderer(world, controls));
        gl.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        CrashLog.mark("renderer set");

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.addView(gl, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(controls, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);
        CrashLog.mark("content view set");
    }

    private void hideBars() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Sound.get().start();
    }

    @Override
    protected void onPause() {
        Sound.get().stop();
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideBars();
    }
}
