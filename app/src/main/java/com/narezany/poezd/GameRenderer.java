package com.narezany.poezd;

import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.util.Log;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** 3D-рендер: сцена, поезда, люди и две модели рук от первого лица. */
final class GameRenderer implements GLSurfaceView.Renderer {
    private static final String TAG = "PoezdGL";

    private static final String VS =
        "attribute vec3 aPos;" +
        "attribute vec3 aNorm;" +
        "attribute vec3 aColor;" +
        "uniform mat4 uMVP;" +
        "uniform mat4 uModel;" +
        "uniform vec3 uTint;" +
        "uniform vec3 uCam;" +
        "varying vec3 vColor;" +
        "varying float vDist;" +
        "void main() {" +
        "  vec4 world = uModel * vec4(aPos, 1.0);" +
        "  gl_Position = uMVP * vec4(aPos, 1.0);" +
        "  vec3 n = normalize(mat3(uModel[0].xyz, uModel[1].xyz, uModel[2].xyz) * aNorm);" +
        "  vec3 L = normalize(vec3(-0.35, 0.85, 0.40));" +
        "  float d = max(dot(n, L), 0.0);" +
        "  float amb = 0.40 + 0.14 * n.y;" +
        "  vColor = aColor * uTint * (amb + 0.70 * d);" +
        "  vDist = distance(world.xyz, uCam);" +
        "}";

    private static final String FS =
        "precision mediump float;" +
        "varying vec3 vColor;" +
        "varying float vDist;" +
        "uniform vec3 uFog;" +
        "uniform float uFogMix;" +
        "void main() {" +
        "  float f = clamp((vDist - 45.0) / 175.0, 0.0, 1.0) * uFogMix;" +
        "  gl_FragColor = vec4(mix(vColor, uFog, f), 1.0);" +
        "}";

    private final GameWorld world;
    private final ControlsView controls;
    private int program, aPos, aNorm, aCol, uMVP, uModel, uTint, uCam, uFog, uFogMix;
    private float[] proj = Math3D.identity();
    private long lastTime;
    private Mesh handMesh;
    private boolean reported;

    GameRenderer(GameWorld world, ControlsView controls) {
        this.world = world;
        this.controls = controls;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig cfg) {
      try {
        CrashLog.mark("surface created");
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        GLES30.glEnable(GLES30.GL_CULL_FACE);
        GLES30.glCullFace(GLES30.GL_BACK);
        GLES30.glClearColor(0.55f, 0.72f, 0.90f, 1f);

        program = link(compile(GLES30.GL_VERTEX_SHADER, VS), compile(GLES30.GL_FRAGMENT_SHADER, FS));
        aPos = GLES30.glGetAttribLocation(program, "aPos");
        aNorm = GLES30.glGetAttribLocation(program, "aNorm");
        aCol = GLES30.glGetAttribLocation(program, "aColor");
        uMVP = GLES30.glGetUniformLocation(program, "uMVP");
        uModel = GLES30.glGetUniformLocation(program, "uModel");
        uTint = GLES30.glGetUniformLocation(program, "uTint");
        uCam = GLES30.glGetUniformLocation(program, "uCam");
        uFog = GLES30.glGetUniformLocation(program, "uFog");
        uFogMix = GLES30.glGetUniformLocation(program, "uFogMix");

        Mesh.Geo g = new Mesh.Geo();
        g.box(1, 1, 1, GameWorld.WHITE);
        handMesh = g.build();
        lastTime = 0;
        CrashLog.mark("shaders and meshes ready");
      } catch (Throwable t) {
        CrashLog.error("surfaceCreated", t);
        throw new RuntimeException(t);
      }
    }

    private int compile(int type, String src) {
        int sh = GLES30.glCreateShader(type);
        GLES30.glShaderSource(sh, src);
        GLES30.glCompileShader(sh);
        int[] ok = new int[1];
        GLES30.glGetShaderiv(sh, GLES30.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            String msg = GLES30.glGetShaderInfoLog(sh);
            Log.e(TAG, "Shader: " + msg);
            CrashLog.mark("SHADER ERROR: " + msg);
        }
        return sh;
    }

    private int link(int vs, int fs) {
        int p = GLES30.glCreateProgram();
        GLES30.glAttachShader(p, vs);
        GLES30.glAttachShader(p, fs);
        GLES30.glLinkProgram(p);
        int[] ok = new int[1];
        GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) {
            String msg = GLES30.glGetProgramInfoLog(p);
            Log.e(TAG, "Link: " + msg);
            CrashLog.mark("LINK ERROR: " + msg);
        }
        return p;
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int w, int h) {
        GLES30.glViewport(0, 0, w, h);
        proj = Math3D.perspective(72f, Math.max(0.5f, (float) w / h), 0.1f, 700f);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
      try {
        long now = System.nanoTime();
        float dt = lastTime == 0 ? 0.016f : (now - lastTime) / 1e9f;
        lastTime = now;

        world.update(dt, controls.lx, controls.ly, controls.rx, controls.ry);

        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT);
        GLES30.glUseProgram(program);
        GLES30.glUniform3f(uFog, 0.55f, 0.72f, 0.90f);
        GLES30.glUniform1f(uFogMix, 1f);

        float cp = (float) Math.cos(world.pitch);
        float dx = (float) Math.sin(world.yaw) * cp;
        float dy = (float) Math.sin(world.pitch);
        float dz = -(float) Math.cos(world.yaw) * cp;
        float shake = world.flash * 0.035f;
        float ex = world.px + ((float) Math.random() - 0.5f) * shake;
        float ey = world.py + 1.7f + ((float) Math.random() - 0.5f) * shake;
        float ez = world.pz + ((float) Math.random() - 0.5f) * shake;
        float[] view = Math3D.lookAt(ex, ey, ez, ex + dx, ey + dy, ez + dz);
        GLES30.glUniform3f(uCam, ex, ey, ez);

        for (GameWorld.Obj o : world.scenery) drawObj(o.mesh, o.x, o.y, o.z, o.ry,
                o.sx, o.sy, o.sz, o.tint, proj, view);

        for (GameWorld.Train t : world.trains) {
            float z = t.z - 11f;
            for (int car = 0; car < 3; car++) {
                float spin = t.speed * 0.12f;
                drawObj(world.trainMeshes[t.type], -1.2f, 2.25f, z - car * 23f,
                        0f, 1f, 1f, 1f, GameWorld.WHITE, proj, view);
                // колёса как маленькие коробки (видны снизу у платформы)
                for (float wz : new float[]{-7f, 7f}) {
                    drawObj(handMesh, -2.45f, 0.62f, z - car * 23f + wz,
                            spin, 0.18f, 0.55f, 0.55f, new float[]{0.1f, 0.1f, 0.12f}, proj, view);
                    drawObj(handMesh, 0.05f, 0.62f, z - car * 23f + wz,
                            spin, 0.18f, 0.55f, 0.55f, new float[]{0.1f, 0.1f, 0.12f}, proj, view);
                }
            }
        }
        for (GameWorld.Cop c : world.cops)
            drawObj(world.humanMesh, c.x, 0.72f, c.z, 0f, 1f, 1f, 1f, WHITE_TINT, proj, view);
        for (GameWorld.Cop s : world.security) {
            if (!s.active) continue;
            drawObj(world.humanMesh, s.x, 0.72f, s.z, 0f, 1f, 1f, 1f, RED_TINT, proj, view);
        }

        if (world.handsVisible()) drawHands();

        controls.invalidate();
        if (!reported) { CrashLog.mark("first frame drawn"); reported = true; }
      } catch (Throwable t) {
        if (!reported) { CrashLog.error("drawFrame", t); reported = true; }
        throw new RuntimeException(t);
      }
    }

    private static final float[] WHITE_TINT = {1f, 1f, 1f};
    private static final float[] RED_TINT = {1f, 0.45f, 0.35f};
    private static final float[] SKIN = {0.86f, 0.66f, 0.50f};
    private static final float[] SLEEVE = {0.20f, 0.28f, 0.42f};

    private void drawObj(Mesh m, float x, float y, float z, float ry,
                         float sx, float sy, float sz, float[] tint,
                         float[] proj, float[] view) {
        float[] model = Math3D.model(x, y, z, ry, sx, sy, sz);
        float[] mv = Math3D.mul(view, model);
        float[] mvp = Math3D.mul(proj, mv);
        GLES30.glUniformMatrix4fv(uMVP, 1, false, mvp, 0);
        GLES30.glUniformMatrix4fv(uModel, 1, false, model, 0);
        GLES30.glUniform3fv(uTint, 1, tint, 0);
        m.bind(aPos, aNorm, aCol);
        m.draw();
    }

    /** Руки рисуем в пространстве камеры: view = единичная матрица. */
    private void drawHands() {
        GLES30.glUniform1f(uFogMix, 0f);
        GLES30.glUniform3f(uCam, 0f, 0f, 0f);
        drawHand(-0.52f, world.leftHand, false);
        drawHand(0.52f, world.rightHand, true);
        GLES30.glUniform1f(uFogMix, 1f);
    }

    private void drawHand(float x, boolean attached, boolean right) {
        float reach = attached ? 0.42f : 0f;
        float z = -1.05f - reach;
        float y = -0.52f - reach * 0.25f;
        float[] model = Math3D.model(x, y, z, 0f, 0.17f, 0.17f, 0.5f + reach);
        GLES30.glUniformMatrix4fv(uMVP, 1, false, Math3D.mul(proj, model), 0);
        GLES30.glUniformMatrix4fv(uModel, 1, false, model, 0);
        GLES30.glUniform3fv(uTint, 1, SKIN, 0);
        handMesh.bind(aPos, aNorm, aCol);
        handMesh.draw();

        float[] sleeve = Math3D.model(x * 1.25f, y - 0.42f, z + 0.16f, 0f,
                0.26f, 0.6f, 0.3f);
        GLES30.glUniformMatrix4fv(uMVP, 1, false, Math3D.mul(proj, sleeve), 0);
        GLES30.glUniformMatrix4fv(uModel, 1, false, sleeve, 0);
        GLES30.glUniform3fv(uTint, 1, SLEEVE, 0);
        handMesh.bind(aPos, aNorm, aCol);
        handMesh.draw();
    }
}
