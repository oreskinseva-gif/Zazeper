package com.narezany.poezd;

import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.util.Log;

import java.io.File;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** 3D-рендер: сцена, поезда, люди и две модели рук от первого лица. */
final class GameRenderer implements GLSurfaceView.Renderer {
    private static final String TAG = "PoezdGL";

    private static final String BG_VS =
        "attribute vec2 aPos;" +
        "void main() { gl_Position = vec4(aPos, 0.0, 1.0); }";

    private static final String BG_FS =
        "precision mediump float;" +
        "uniform vec2 uRes;" +
        "void main() {" +
        "  float t = clamp(gl_FragCoord.y / uRes.y, 0.0, 1.0);" +
        "  vec3 sky = mix(vec3(0.78, 0.88, 0.96), vec3(0.20, 0.46, 0.80), t);" +
        "  vec2 sp = vec2(uRes.x * 0.78, uRes.y * 0.80);" +
        "  float d = distance(gl_FragCoord.xy, sp) / uRes.y;" +
        "  sky += vec3(1.0, 0.88, 0.60) * (1.0 - smoothstep(0.0, 0.20, d)) * 0.85;" +
        "  float cloud = sin(gl_FragCoord.x * 0.006 + 1.0) * sin(gl_FragCoord.y * 0.011);" +
        "  sky += vec3(0.06) * smoothstep(0.35, 0.8, cloud) * t;" +
        "  gl_FragColor = vec4(sky, 1.0);" +
        "}";

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
    private int bgProgram, bgPos, bgRes, bgVbo;
    private float viewW = 1280f, viewH = 720f;
    private float[] proj = Math3D.identity();
    private long lastTime;
    private Mesh handMesh;
    private boolean reported;
    private int frameNo;

    private void cp(String s) {
        if (frameNo < 2) CrashLog.mark("frame" + frameNo + " " + s);
    }

    private final boolean safeMode = new File(
            "/storage/emulated/0/Download/Zazeper/safe").exists();

    GameRenderer(GameWorld world, ControlsView controls) {
        this.world = world;
        this.controls = controls;
        if (safeMode) CrashLog.mark("SAFE MODE enabled");
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

        bgProgram = link(compile(GLES30.GL_VERTEX_SHADER, BG_VS),
                compile(GLES30.GL_FRAGMENT_SHADER, BG_FS));
        bgPos = GLES30.glGetAttribLocation(bgProgram, "aPos");
        bgRes = GLES30.glGetUniformLocation(bgProgram, "uRes");
        float[] quad = {-1, -1, 1, -1, 1, 1, -1, -1, 1, 1, -1, 1};
        java.nio.FloatBuffer qb = java.nio.ByteBuffer.allocateDirect(quad.length * 4)
                .order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer();
        qb.put(quad); qb.position(0);
        int[] b = new int[1];
        GLES30.glGenBuffers(1, b, 0);
        bgVbo = b[0];
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, bgVbo);
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, quad.length * 4, qb, GLES30.GL_STATIC_DRAW);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);

        Mesh.Geo g = new Mesh.Geo();
        g.box(1, 1, 1, GameWorld.WHITE);
        handMesh = g.build();
        for (GameWorld.Obj o : world.scenery) o.mesh.upload();
        for (int i = 0; i < 4; i++) world.trainMeshes[i].upload();
        world.humanMesh.upload();
        handMesh.upload();
        CrashLog.mark("meshes uploaded");
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
        viewW = Math.max(1, w); viewH = Math.max(1, h);
        proj = Math3D.perspective(72f, Math.max(0.5f, (float) w / h), 0.1f, 700f);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
      try {
        long now = System.nanoTime();
        float dt = lastTime == 0 ? 0.016f : (now - lastTime) / 1e9f;
        lastTime = now;
        cp("start");
        if (safeMode) {
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT);
            cp("safe cleared");
            controls.invalidate();
            return;
        }

        world.update(dt, controls.lx, controls.ly, controls.rx, controls.ry);
        cp("after update");

        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT);
        drawBackground();
        GLES30.glUseProgram(program);
        GLES30.glUniform3f(uFog, 0.55f, 0.72f, 0.90f);
        GLES30.glUniform1f(uFogMix, 1f);
        cp("after clear");

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
        cp("after camera");

        int oi = 0;
        for (GameWorld.Obj o : world.scenery) {
            if (frameNo < 2 && (oi < 16 || oi % 25 == 0)) cp("scenery idx " + oi);
            drawObj(o.mesh, o.x, o.y, o.z, o.ry, o.sx, o.sy, o.sz, o.tint, proj, view);
            oi++;
        }
        cp("after scenery");

        for (GameWorld.Train t : world.trains) {
            float front = t.z - 12f;
            for (int car = 0; car < 4; car++) {
                float cz = front - car * 23.6f;
                float spin = t.speed * 0.12f;
                drawObj(handMesh, -1.2f, 0.14f, cz, 0f, 3.4f, 0.03f, 23f, SHADOW, proj, view);
                drawObj(world.trainMeshes[t.type], -1.2f, 2.25f, cz,
                        0f, 1f, 1f, 1f, GameWorld.WHITE, proj, view);
                for (float wz : new float[]{-7.5f, 7.5f}) {
                    drawObj(handMesh, -2.46f, 0.62f, cz + wz,
                            spin, 0.18f, 0.55f, 1.05f, new float[]{0.09f, 0.09f, 0.11f}, proj, view);
                    drawObj(handMesh, 0.06f, 0.62f, cz + wz,
                            spin, 0.18f, 0.55f, 1.05f, new float[]{0.09f, 0.09f, 0.11f}, proj, view);
                }
            }
        }
        cp("after trains");
        for (GameWorld.Cop c : world.cops) {
            drawObj(handMesh, c.x, 0.715f, c.z, 0f, 0.9f, 0.02f, 0.5f, SHADOW, proj, view);
            drawObj(world.humanMesh, c.x, 0.72f, c.z, 0f, 1f, 1f, 1f, WHITE_TINT, proj, view);
        }
        for (GameWorld.Cop s : world.security) {
            if (!s.active) continue;
            drawObj(handMesh, s.x, 0.715f, s.z, 0f, 0.9f, 0.02f, 0.5f, SHADOW, proj, view);
            drawObj(world.humanMesh, s.x, 0.72f, s.z, 0f, 1f, 1f, 1f, RED_TINT, proj, view);
        }

        cp("after people");
        if (world.handsVisible()) drawHands();
        cp("after hands");

        controls.invalidate();
        cp("before invalidate");
        if (!reported) { CrashLog.mark("first frame drawn"); reported = true; }
        frameNo++;
      } catch (Throwable t) {
        if (!reported) { CrashLog.error("drawFrame", t); reported = true; }
        throw new RuntimeException(t);
      }
    }

    private void drawBackground() {
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
        GLES30.glDepthMask(false);
        GLES30.glUseProgram(bgProgram);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, bgVbo);
        GLES30.glEnableVertexAttribArray(bgPos);
        GLES30.glVertexAttribPointer(bgPos, 2, GLES30.GL_FLOAT, false, 0, 0);
        GLES30.glUniform2f(bgRes, viewW, viewH);
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6);
        GLES30.glDisableVertexAttribArray(bgPos);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        GLES30.glDepthMask(true);
    }

    private static final float[] WHITE_TINT = {1f, 1f, 1f};
    private static final float[] SHADOW = {0.10f, 0.12f, 0.11f};
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
