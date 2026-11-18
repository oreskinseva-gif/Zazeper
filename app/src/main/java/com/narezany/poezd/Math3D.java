package com.narezany.poezd;

/** Маленькая библиотека матриц (column-major, как в OpenGL). */
final class Math3D {
    private Math3D() {}

    static float[] identity() {
        float[] m = new float[16];
        m[0] = m[5] = m[10] = m[15] = 1f;
        return m;
    }

    /** a * b */
    static float[] mul(float[] a, float[] b) {
        float[] o = new float[16];
        for (int c = 0; c < 4; c++) {
            for (int r = 0; r < 4; r++) {
                float s = 0f;
                for (int k = 0; k < 4; k++) s += a[r + k * 4] * b[k + c * 4];
                o[r + c * 4] = s;
            }
        }
        return o;
    }

    static float[] perspective(float fovyDeg, float aspect, float near, float far) {
        float f = 1f / (float) Math.tan(Math.toRadians(fovyDeg) * 0.5f);
        float[] m = new float[16];
        m[0] = f / aspect;
        m[5] = f;
        m[10] = (far + near) / (near - far);
        m[11] = -1f;
        m[14] = 2f * far * near / (near - far);
        return m;
    }

    static float[] lookAt(float ex, float ey, float ez,
                          float cx, float cy, float cz) {
        float zx = ex - cx, zy = ey - cy, zz = ez - cz;
        float l = (float) Math.sqrt(zx * zx + zy * zy + zz * zz);
        if (l < 1e-5f) { zx = 0; zy = 0; zz = 1; l = 1; }
        zx /= l; zy /= l; zz /= l;

        float xx = zz, xy = 0f, xz = -zx;           // cross(up, z), up = (0,1,0)
        l = (float) Math.sqrt(xx * xx + xz * xz);
        if (l < 1e-5f) { xx = 1; xz = 0; l = 1; }
        xx /= l; xz /= l;

        float yx = zy * xz - zz * xy;
        float yy = zz * xx - zx * xz;
        float yz = zx * xy - zy * xx;

        float[] m = new float[16];
        m[0] = xx; m[4] = xy; m[8]  = xz; m[12] = -(xx * ex + xy * ey + xz * ez);
        m[1] = yx; m[5] = yy; m[9]  = yz; m[13] = -(yx * ex + yy * ey + yz * ez);
        m[2] = zx; m[6] = zy; m[10] = zz; m[14] = -(zx * ex + zy * ey + zz * ez);
        m[15] = 1f;
        return m;
    }

    private static float[] translate(float x, float y, float z) {
        float[] m = identity();
        m[12] = x; m[13] = y; m[14] = z;
        return m;
    }

    private static float[] scale(float x, float y, float z) {
        float[] m = identity();
        m[0] = x; m[5] = y; m[10] = z;
        return m;
    }

    private static float[] rotY(float a) {
        float[] m = identity();
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        m[0] = c; m[2] = -s; m[8] = s; m[10] = c;
        return m;
    }

    private static float[] rotX(float a) {
        float[] m = identity();
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        m[5] = c; m[6] = s; m[9] = -s; m[10] = c;
        return m;
    }

    /** Модельная матрица: перемещение, поворот вокруг Y, затем масштаб. */
    static float[] model(float x, float y, float z, float ry,
                         float sx, float sy, float sz) {
        float[] m = translate(x, y, z);
        if (ry != 0f) m = mul(m, rotY(ry));
        m = mul(m, scale(sx, sy, sz));
        return m;
    }

    static float[] model(float x, float y, float z, float ry, float rx,
                         float sx, float sy, float sz) {
        float[] m = translate(x, y, z);
        if (ry != 0f) m = mul(m, rotY(ry));
        if (rx != 0f) m = mul(m, rotX(rx));
        m = mul(m, scale(sx, sy, sz));
        return m;
    }

    static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
