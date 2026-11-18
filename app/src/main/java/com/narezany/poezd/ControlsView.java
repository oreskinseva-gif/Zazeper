package com.narezany.poezd;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.view.MotionEvent;
import android.view.SurfaceView;

import java.util.ArrayList;
import java.util.List;

/** Прозрачный слой поверх 3D: два джойстика, лог событий, подсказки. */
final class ControlsView extends SurfaceView {
    volatile float lx, ly, rx, ry;

    private final GameWorld world;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float radius;
    private long leftId = -1, rightId = -1;
    private float lox, loy, rox, roy;

    ControlsView(Context ctx, GameWorld world) {
        super(ctx);
        this.world = world;
        setZOrderOnTop(true);
        getHolder().setFormat(PixelFormat.TRANSLUCENT);
        radius = 46f * ctx.getResources().getDisplayMetrics().density;
    }

    private void setLeft(float dx, float dy) {
        lx = Math3D.clamp(dx / radius, -1f, 1f);
        ly = Math3D.clamp(dy / radius, -1f, 1f);
    }

    private void setRight(float dx, float dy) {
        rx = Math3D.clamp(dx / radius, -1f, 1f);
        ry = Math3D.clamp(dy / radius, -1f, 1f);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        if (world.caught) {
            if (action == MotionEvent.ACTION_DOWN) {
                world.reset();
                lx = ly = rx = ry = 0;
                leftId = rightId = -1;
            }
            return true;
        }
        int w = getWidth();
        int idx = e.getActionIndex();
        int id = e.getPointerId(idx);
        float x = e.getX(idx), y = e.getY(idx);

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                if (x < w * 0.5f && leftId == -1) { leftId = id; lox = x; loy = y; }
                else if (x >= w * 0.5f && rightId == -1) { rightId = id; rox = x; roy = y; }
                break;
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < e.getPointerCount(); i++) {
                    int pid = e.getPointerId(i);
                    if (pid == leftId) setLeft(e.getX(i) - lox, e.getY(i) - loy);
                    else if (pid == rightId) setRight(e.getX(i) - rox, e.getY(i) - roy);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL:
                if (id == leftId) { leftId = -1; lx = ly = 0; }
                else if (id == rightId) { rightId = -1; rx = ry = 0; }
                break;
            default:
                break;
        }
        return true;
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float d = getResources().getDisplayMetrics().density;
        int w = getWidth(), h = getHeight();
        GameWorld.Hud hud = world.hud;

        // верхняя панель с событиями
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xC4000000);
        c.drawRect(0, 0, w, (16 + hud.lineCount * 15) * d, p);
        p.setTextSize(13.5f * d);
        p.setTextAlign(Paint.Align.LEFT);
        float ty = 18 * d;
        for (int i = 0; i < hud.lineCount; i++) {
            String s = hud.lines[i] == null ? "" : hud.lines[i];
            p.setColor(s.contains("ТБшник") || s.contains("охран") || s.contains("задержал")
                    ? Color.rgb(255, 110, 90)
                    : (s.contains("Удар") ? Color.rgb(255, 215, 80) : Color.WHITE));
            c.drawText(s, 12 * d, ty, p);
            ty += 15 * d;
        }

        // скорость и поезд
        p.setColor(Color.WHITE);
        p.setTextAlign(Paint.Align.RIGHT);
        p.setTextSize(18 * d);
        c.drawText(hud.speed, w - 12 * d, 25 * d, p);
        p.setTextSize(13 * d);
        c.drawText(hud.train, w - 12 * d, 43 * d, p);
        p.setTextAlign(Paint.Align.LEFT);

        // джойстики
        p.setColor(0x33FFFFFF);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2 * d);
        c.drawCircle(w * 0.18f, h * 0.74f, radius, p);
        c.drawCircle(w * 0.82f, h * 0.74f, radius, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x88FFFFFF);
        c.drawCircle(w * 0.18f + lx * radius * 0.45f, h * 0.74f + ly * radius * 0.45f,
                radius * 0.34f, p);
        c.drawCircle(w * 0.82f + rx * radius * 0.45f, h * 0.74f + ry * radius * 0.45f,
                radius * 0.34f, p);

        // прицел
        p.setColor(0x88FFFFFF);
        c.drawCircle(w / 2f, h / 2f, 3 * d, p);

        // подсказка
        if (hud.tip != null && !hud.tip.isEmpty()) {
            p.setColor(0xDDFFFFFF);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(12.5f * d);
            wrapCenter(c, hud.tip, w / 2f, h - 40 * d, w - 40 * d, 16 * d);
            p.setTextAlign(Paint.Align.LEFT);
        }

        if (hud.flash > 0f) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb((int) (140 * Math.min(1f, hud.flash)), 255, 210, 60));
            c.drawRect(0, 0, w, h, p);
        }

        if (hud.gameOver) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xCC000000);
            c.drawRect(0, 0, w, h, p);
            p.setColor(Color.rgb(255, 90, 80));
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(34 * d);
            c.drawText("ВАС ЗАДЕРЖАЛИ", w / 2f, h / 2f - 16 * d, p);
            p.setColor(Color.WHITE);
            p.setTextSize(17 * d);
            c.drawText("Нажмите, чтобы начать заново", w / 2f, h / 2f + 28 * d, p);
            p.setTextAlign(Paint.Align.LEFT);
        }
    }

    private void wrapCenter(Canvas c, String text, float x, float y, float maxW, float lh) {
        List<String> lines = new ArrayList<String>();
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ")) {
            String test = cur.length() == 0 ? word : cur + " " + word;
            if (p.measureText(test) > maxW && cur.length() > 0) {
                lines.add(cur.toString());
                cur = new StringBuilder(word);
            } else {
                cur = new StringBuilder(test);
            }
        }
        if (cur.length() > 0) lines.add(cur.toString());
        float yy = y;
        for (int i = lines.size() - 1; i >= 0; i--) {
            c.drawText(lines.get(i), x, yy, p);
            yy -= lh;
        }
    }
}
