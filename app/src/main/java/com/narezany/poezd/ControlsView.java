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

/** Прозрачный слой поверх 3D: меню, задания, джойстики, лог, мини-карта. */
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
        radius = 44f * ctx.getResources().getDisplayMetrics().density;
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
        int w = getWidth();
        int h = getHeight();
        if (world.state != GameWorld.PLAY) {
            if (action == MotionEvent.ACTION_DOWN) {
                float sx = e.getX(), sy = e.getY();
                if (Math.abs(sx - w / 2f) < 110f
                        && Math.abs(sy - h * 0.69f) < 34f) {
                    world.cycleStyle();
                } else {
                    world.start();
                    lx = ly = rx = ry = 0;
                    leftId = rightId = -1;
                }
            }
            return true;
        }
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            int pi = e.getActionIndex();
            if (Math.abs(e.getX(pi) - w / 2f) < 62f
                    && Math.abs(e.getY(pi) - h * 0.82f) < 62f) {
                world.jumpRequested = true;
                return true;
            }
        }
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

        if (hud.menu) {
            drawMenu(c, w, h, d);
            return;
        }

        // лог событий
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xB8000000);
        c.drawRect(0, 0, w, (14 + hud.lineCount * 14) * d, p);
        p.setTextSize(12.5f * d);
        p.setTextAlign(Paint.Align.LEFT);
        float ty = 16 * d;
        for (int i = 0; i < hud.lineCount; i++) {
            String s = hud.lines[i] == null ? "" : hud.lines[i];
            p.setColor(s.contains("ТБшник") || s.contains("охран") || s.contains("задержал")
                    ? Color.rgb(255, 110, 90)
                    : (s.contains("Удар") || s.contains("током")
                        ? Color.rgb(255, 220, 80) : Color.WHITE));
            c.drawText(s, 10 * d, ty, p);
            ty += 14 * d;
        }

        // задание
        float top = (18 + hud.lineCount * 14) * d;
        p.setColor(0xAA102040);
        c.drawRect(8 * d, top, w * 0.62f, top + 44 * d, p);
        p.setColor(Color.rgb(120, 200, 255));
        p.setTextSize(11 * d);
        c.drawText("ЗАДАНИЕ " + (world.missionIndex + 1) + "/4   +100 очков",
                14 * d, top + 14 * d, p);
        p.setColor(Color.WHITE);
        p.setTextSize(13.5f * d);
        c.drawText(hud.mission, 14 * d, top + 31 * d, p);
        p.setColor(0x33FFFFFF);
        c.drawRect(14 * d, top + 36 * d, w * 0.62f - 14 * d, top + 40 * d, p);
        p.setColor(Color.rgb(90, 220, 140));
        c.drawRect(14 * d, top + 36 * d,
                (14 * d) + (w * 0.62f - 28 * d) * hud.missionProgress, top + 40 * d, p);

        // скорость, счёт, время
        p.setTextAlign(Paint.Align.RIGHT);
        p.setColor(Color.WHITE);
        p.setTextSize(20 * d);
        c.drawText(hud.speed, w - 10 * d, 23 * d, p);
        p.setTextSize(12 * d);
        c.drawText(hud.train, w - 10 * d, 38 * d, p);
        p.setColor(Color.rgb(255, 220, 120));
        c.drawText("очки " + hud.score + "   лучший " + hud.best, w - 10 * d, 53 * d, p);
        p.setColor(Color.rgb(200, 220, 240));
        c.drawText(hud.timer, w - 10 * d, 67 * d, p);
        p.setTextAlign(Paint.Align.LEFT);

        // тревога
        if (hud.alert > 0.02f) {
            float bw = 120 * d;
            p.setColor(0x88000000);
            c.drawRect(w / 2f - bw / 2, 8 * d, w / 2f + bw / 2, 16 * d, p);
            p.setColor(Color.rgb(255, (int) (200 - 160 * hud.alert), 60));
            c.drawRect(w / 2f - bw / 2, 8 * d,
                    w / 2f - bw / 2 + bw * hud.alert, 16 * d, p);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(10 * d);
            p.setColor(Color.WHITE);
            c.drawText("ТБШНИК ВИДИТ", w / 2f, 28 * d, p);
            p.setTextAlign(Paint.Align.LEFT);
        }

        // здоровье и состояние
        p.setStyle(Paint.Style.FILL);
        float hbX = w - 162 * d, hbY = 82 * d;
        p.setColor(0x99000000);
        c.drawRect(hbX - 2 * d, hbY - 2 * d, hbX + 150 * d + 2 * d, hbY + 8 * d, p);
        p.setColor(hud.health > 55 ? Color.rgb(90, 220, 130)
                : (hud.health > 25 ? Color.rgb(250, 200, 70) : Color.rgb(250, 80, 70)));
        c.drawRect(hbX, hbY, hbX + 150 * d * hud.health / 100f, hbY + 6 * d, p);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(10 * d);
        p.setColor(Color.WHITE);
        c.drawText("ЗДОРОВЬЕ " + hud.health + "%", hbX + 75 * d, hbY + 20 * d, p);
        if (hud.brokenLeg || hud.brokenArm) {
            p.setColor(Color.rgb(255, 120, 100));
            c.drawText("ТРАВМА: " + (hud.brokenLeg ? "нога " : "")
                    + (hud.brokenArm ? "рука" : ""), hbX + 75 * d, hbY + 33 * d, p);
        }
        if (hud.hospital) {
            p.setColor(Color.rgb(120, 255, 180));
            c.drawText("БОЛЬНИЦА: лечение рядом с платформой", hbX + 75 * d, hbY + 46 * d, p);
        }
        p.setTextAlign(Paint.Align.LEFT);

        // кнопка прыжка
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x7733AACC);
        c.drawCircle(w / 2f, h * 0.82f, 46, p);
        p.setStyle(Paint.Style.STROKE);
        p.setColor(0xFFAADDFF);
        p.setStrokeWidth(2 * d);
        c.drawCircle(w / 2f, h * 0.82f, 46, p);
        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(12 * d);
        p.setColor(Color.WHITE);
        c.drawText("ПРЫЖОК", w / 2f, h * 0.82f + 5 * d, p);
        p.setTextAlign(Paint.Align.LEFT);

        // джойстики и подсказки захвата
        float jly = h * 0.74f;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2 * d);
        p.setColor(hud.leftReady ? Color.rgb(90, 230, 140) : 0x66FFFFFF);
        c.drawCircle(w * 0.18f, jly, radius, p);
        p.setColor(hud.rightReady ? Color.rgb(90, 230, 140) : 0x66FFFFFF);
        c.drawCircle(w * 0.82f, jly, radius, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(hud.leftReady ? Color.rgb(120, 255, 170) : 0x88FFFFFF);
        c.drawCircle(w * 0.18f + lx * radius * 0.45f, jly + ly * radius * 0.45f,
                radius * 0.34f, p);
        p.setColor(hud.rightReady ? Color.rgb(120, 255, 170) : 0x88FFFFFF);
        c.drawCircle(w * 0.82f + rx * radius * 0.45f, jly + ry * radius * 0.45f,
                radius * 0.34f, p);

        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(11 * d);
        p.setColor(Color.WHITE);
        c.drawText("ЛЕВАЯ РУКА", w * 0.18f, jly + radius + 16 * d, p);
        c.drawText("ПРАВАЯ РУКА", w * 0.82f, jly + radius + 16 * d, p);
        if (hud.leftReady) {
            p.setColor(Color.rgb(120, 255, 170));
            c.drawText("◀ ТЯНИ", w * 0.18f - radius - 30 * d, jly, p);
        }
        if (hud.rightReady) {
            p.setColor(Color.rgb(120, 255, 170));
            c.drawText("ТЯНИ ▶", w * 0.82f + radius + 30 * d, jly, p);
        }
        p.setTextAlign(Paint.Align.LEFT);

        // прицел
        p.setColor(0xAAFFFFFF);
        c.drawCircle(w / 2f, h / 2f, 3 * d, p);

        // мини-карта
        float mw = w * 0.44f, mx = w / 2f - mw / 2, my = h - 22 * d;
        p.setColor(0x88000000);
        c.drawRect(mx, my - 8 * d, mx + mw, my + 8 * d, p);
        p.setColor(0x66FFFFFF);
        c.drawRect(mx, my - 0.5f * d, mx + mw, my + 0.5f * d, p);
        for (GameWorld.Train t : world.trains) {
            float x = mx + mw * Math3D.clamp((t.z - world.pz + 120f) / 240f, 0, 1);
            p.setColor(GameWorld.BODY[t.type][0] > 0.8f && GameWorld.BODY[t.type][1] < 0.3f
                    ? Color.rgb(255, 90, 80) : Color.rgb(120, 220, 255));
            c.drawRect(x - 2 * d, my - 5 * d, x + 2 * d, my + 5 * d, p);
        }
        for (GameWorld.Cop s : world.security) {
            if (!s.active) continue;
            float x = mx + mw * Math3D.clamp((s.z - world.pz + 120f) / 240f, 0, 1);
            p.setColor(Color.RED);
            c.drawCircle(x, my, 3 * d, p);
        }
        p.setColor(Color.rgb(120, 255, 170));
        c.drawCircle(mx + mw / 2, my, 4 * d, p);

        // подсказка
        if (hud.tip != null && !hud.tip.isEmpty()) {
            p.setColor(0xEEFFFFFF);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(11.5f * d);
            wrapCenter(c, hud.tip, w / 2f, h - 42 * d, w - 40 * d, 14 * d);
            p.setTextAlign(Paint.Align.LEFT);
        }

        if (hud.flash > 0f) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb((int) (150 * Math.min(1f, hud.flash)), 255, 215, 70));
            c.drawRect(0, 0, w, h, p);
        }

        if (hud.gameOver || hud.win) drawResult(c, w, h, d, hud);
    }

    private void drawMenu(Canvas c, int w, int h, float d) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xB0000000);
        c.drawRect(0, 0, w, h, p);
        p.setTextAlign(Paint.Align.CENTER);
        p.setColor(Color.rgb(255, 220, 110));
        p.setTextSize(38 * d);
        c.drawText("ЗАЦЕПКА: ПОЕЗДА", w / 2f, h * 0.24f, p);
        p.setColor(Color.WHITE);
        p.setTextSize(16 * d);
        c.drawText("Гонка по крышам и вагонам", w / 2f, h * 0.33f, p);
        // заметная метка сборки: сразу видно, что установлена новая версия
        p.setColor(Color.rgb(255, 150, 60));
        p.setTextSize(12 * d);
        c.drawText("▼ ВЕРСИЯ 8: камера поворачивается при зацепе ▼",
                w / 2f, h * 0.385f, p);
        p.setColor(Color.rgb(150, 220, 255));
        p.setTextSize(14 * d);
        c.drawText("Левый стик — левая рука, правый — правая", w / 2f, h * 0.44f, p);
        c.drawText("Тяните к поезду, чтобы зацепиться. Оба вверх — на крышу", w / 2f, h * 0.50f, p);
        c.drawText("ТБшник вызывает охрану и останавливает поезд", w / 2f, h * 0.56f, p);
        p.setColor(Color.rgb(255, 220, 120));
        p.setTextSize(15 * d);
        c.drawText("Лучший: " + world.hud.best + "    Достижения: " + world.hud.ach + "/8",
                w / 2f, h * 0.64f, p);

        // выбор стиля одежды
        p.setTextSize(12 * d);
        p.setColor(Color.rgb(190, 210, 230));
        c.drawText("СТИЛЬ: " + world.hud.style + "  (нажмите, чтобы сменить)",
                w / 2f, h * 0.665f, p);
        for (int i = 0; i < 4; i++) {
            float[] col = GameWorld.STYLE_COLORS[i];
            float x0 = w / 2f - 100f + i * 50f;
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.rgb((int) (col[0] * 255), (int) (col[1] * 255), (int) (col[2] * 255)));
            c.drawRect(x0, h * 0.69f - 18f, x0 + 44f, h * 0.69f + 18f, p);
            p.setStyle(Paint.Style.STROKE);
            p.setColor(i == world.style ? Color.WHITE : 0x66FFFFFF);
            p.setStrokeWidth((i == world.style ? 3 : 1) * d);
            c.drawRect(x0, h * 0.69f - 18f, x0 + 44f, h * 0.69f + 18f, p);
        }
        p.setStyle(Paint.Style.FILL);

        p.setColor(Color.rgb(90, 230, 140));
        p.setTextSize(22 * d);
        c.drawText("НАЖМИТЕ, ЧТОБЫ НАЧАТЬ", w / 2f, h * 0.81f, p);
        p.setTextSize(11 * d);
        p.setColor(Color.rgb(180, 190, 200));
        c.drawText("В реальности зацепка за поезда смертельно опасна — это игра", w / 2f, h - 12 * d, p);
        p.setTextAlign(Paint.Align.LEFT);
    }

    private void drawResult(Canvas c, int w, int h, float d, GameWorld.Hud hud) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xD0000000);
        c.drawRect(0, 0, w, h, p);
        p.setTextAlign(Paint.Align.CENTER);
        p.setColor(hud.win ? Color.rgb(120, 240, 150) : Color.rgb(255, 90, 80));
        p.setTextSize(34 * d);
        c.drawText(hud.win ? "ВЫ ПОБЕДИЛИ!" : "ВАС ЗАДЕРЖАЛИ", w / 2f, h * 0.38f, p);
        p.setColor(Color.WHITE);
        p.setTextSize(17 * d);
        c.drawText("Очки: " + hud.score + "   Лучший: " + hud.best, w / 2f, h * 0.50f, p);
        p.setTextSize(13 * d);
        p.setColor(Color.rgb(190, 215, 240));
        if (!hud.win && world.loseReason != null && !world.loseReason.isEmpty())
            c.drawText(world.loseReason, w / 2f, h * 0.56f, p);
        c.drawText("Достижения: " + hud.ach + "/8", w / 2f, h * 0.60f, p);
        p.setColor(Color.rgb(120, 240, 150));
        p.setTextSize(20 * d);
        c.drawText("НАЖМИТЕ, ЧТОБЫ ИГРАТЬ СНОВА", w / 2f, h * 0.68f, p);
        p.setTextAlign(Paint.Align.LEFT);
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
