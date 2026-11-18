package com.narezany.poezd;

import android.opengl.GLES30;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;

/** Геометрия: вершины = позиция(3) + нормаль(3) + цвет(3). */
final class Mesh {
    private final FloatBuffer vertices;
    private final ShortBuffer indices;
    final int count;

    private int vbo, ibo;

    private Mesh(float[] v, short[] i) {
        ByteBuffer vb = ByteBuffer.allocateDirect(v.length * 4).order(ByteOrder.nativeOrder());
        vb.asFloatBuffer().put(v);
        vertices = vb.asFloatBuffer();
        vertices.position(0);
        ByteBuffer ib = ByteBuffer.allocateDirect(i.length * 2).order(ByteOrder.nativeOrder());
        ib.asShortBuffer().put(i);
        indices = ib.asShortBuffer();
        indices.position(0);
        count = i.length;
    }

    /** Вызывается после создания GL-контекста. */
    void upload() {
        if (vbo != 0) return;
        int[] buf = new int[2];
        GLES30.glGenBuffers(2, buf, 0);
        vbo = buf[0];
        ibo = buf[1];
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo);
        vertices.position(0);
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertices.capacity() * 4,
                vertices, GLES30.GL_STATIC_DRAW);
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ibo);
        indices.position(0);
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, indices.capacity() * 2,
                indices, GLES30.GL_STATIC_DRAW);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    void bind(int posLoc, int normLoc, int colLoc) {
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo);
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ibo);
        GLES30.glVertexAttribPointer(posLoc, 3, GLES30.GL_FLOAT, false, 36, 0);
        GLES30.glEnableVertexAttribArray(posLoc);
        GLES30.glVertexAttribPointer(normLoc, 3, GLES30.GL_FLOAT, false, 36, 12);
        GLES30.glEnableVertexAttribArray(normLoc);
        GLES30.glVertexAttribPointer(colLoc, 3, GLES30.GL_FLOAT, false, 36, 24);
        GLES30.glEnableVertexAttribArray(colLoc);
    }

    void draw() {
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, count, GLES30.GL_UNSIGNED_SHORT, 0);
    }

    static final class Geo {
        private final ArrayList<Float> v = new ArrayList<Float>();
        private final ArrayList<Short> idx = new ArrayList<Short>();

        private void vert(float[] p, float nx, float ny, float nz, float[] c) {
            v.add(p[0]); v.add(p[1]); v.add(p[2]);
            v.add(nx); v.add(ny); v.add(nz);
            v.add(c[0]); v.add(c[1]); v.add(c[2]);
        }

        void tri(float[] a, float[] b, float[] c, float[] col) {
            float ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
            float vx = c[0] - a[0], vy = c[1] - a[1], vz = c[2] - a[2];
            float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
            float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (l < 1e-6f) l = 1f;
            nx /= l; ny /= l; nz /= l;
            short base = (short) (v.size() / 9);
            vert(a, nx, ny, nz, col); vert(b, nx, ny, nz, col); vert(c, nx, ny, nz, col);
            idx.add(base); idx.add((short) (base + 1)); idx.add((short) (base + 2));
        }

        void quad(float[] a, float[] b, float[] c, float[] d, float[] col) {
            tri(a, b, c, col); tri(a, c, d, col);
        }

        private static float[] p(float x, float y, float z) { return new float[]{x, y, z}; }

        void box(float cx, float cy, float cz, float sx, float sy, float sz, float[] col) {
            float hx = sx * 0.5f, hy = sy * 0.5f, hz = sz * 0.5f;
            float[][][] q = {
                {p(cx-hx,cy-hy,cz+hz), p(cx+hx,cy-hy,cz+hz), p(cx+hx,cy+hy,cz+hz), p(cx-hx,cy+hy,cz+hz)},
                {p(cx+hx,cy-hy,cz-hz), p(cx-hx,cy-hy,cz-hz), p(cx-hx,cy+hy,cz-hz), p(cx+hx,cy+hy,cz-hz)},
                {p(cx+hx,cy-hy,cz+hz), p(cx+hx,cy-hy,cz-hz), p(cx+hx,cy+hy,cz-hz), p(cx+hx,cy+hy,cz+hz)},
                {p(cx-hx,cy-hy,cz-hz), p(cx-hx,cy-hy,cz+hz), p(cx-hx,cy+hy,cz+hz), p(cx-hx,cy+hy,cz-hz)},
                {p(cx-hx,cy+hy,cz+hz), p(cx+hx,cy+hy,cz+hz), p(cx+hx,cy+hy,cz-hz), p(cx-hx,cy+hy,cz-hz)},
                {p(cx-hx,cy-hy,cz-hz), p(cx+hx,cy-hy,cz-hz), p(cx+hx,cy-hy,cz+hz), p(cx-hx,cy-hy,cz+hz)}
            };
            for (float[][] f : q) quad(f[0], f[1], f[2], f[3], col);
        }

        void box(float sx, float sy, float sz, float[] col) { box(0, 0, 0, sx, sy, sz, col); }

        /** Цилиндр вдоль оси Y, центр в нуле. */
        void cyl(float r, float h, int seg, float[] col) {
            float y0 = -h * 0.5f, y1 = h * 0.5f;
            for (int i = 0; i < seg; i++) {
                float a0 = (float) (2 * Math.PI * i / seg);
                float a1 = (float) (2 * Math.PI * (i + 1) / seg);
                float[] b0 = p(r * (float)Math.cos(a0), y0, r * (float)Math.sin(a0));
                float[] t0 = p(r * (float)Math.cos(a0), y1, r * (float)Math.sin(a0));
                float[] b1 = p(r * (float)Math.cos(a1), y0, r * (float)Math.sin(a1));
                float[] t1 = p(r * (float)Math.cos(a1), y1, r * (float)Math.sin(a1));
                quad(b0, t0, t1, b1, col);
                tri(p(0, y1, 0), t1, t0, col);
                tri(p(0, y0, 0), b0, b1, col);
            }
        }

        Mesh build() {
            float[] fv = new float[v.size()];
            for (int i = 0; i < fv.length; i++) fv[i] = v.get(i);
            short[] si = new short[idx.size()];
            for (int i = 0; i < si.length; i++) si[i] = idx.get(i);
            return new Mesh(fv, si);
        }
    }
}
