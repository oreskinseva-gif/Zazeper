package com.narezany.poezd;

import java.util.ArrayList;
import java.util.List;

/** Всё состояние игры: поезда, игрок, ТБшники, охрана, провода, события. */
final class GameWorld {
    static final float[] WHITE = {1f, 1f, 1f};

    static final String[] NAMES = {"Сапсан", "Ласточка", "Метеор", "Базовый поезд"};
    static final float[] KMH = {250f, 210f, 400f, 170f};
    private static final float[][] BODY = {
        {0.88f, 0.92f, 0.98f}, {0.86f, 0.18f, 0.16f},
        {0.98f, 0.75f, 0.12f}, {0.20f, 0.60f, 0.40f}
    };
    private static final float[][] ACCENT = {
        {0.10f, 0.40f, 0.80f}, {0.95f, 0.95f, 0.95f},
        {0.15f, 0.15f, 0.18f}, {0.92f, 0.92f, 0.92f}
    };

    static final class Obj {
        final Mesh mesh;
        final float x, y, z, ry, sx, sy, sz;
        final float[] tint;
        Obj(Mesh m, float x, float y, float z, float sx, float sy, float sz, float[] tint) {
            this(m, x, y, z, 0, sx, sy, sz, tint);
        }
        Obj(Mesh m, float x, float y, float z, float ry, float sx, float sy, float sz, float[] tint) {
            this.mesh = m; this.x = x; this.y = y; this.z = z; this.ry = ry;
            this.sx = sx; this.sy = sy; this.sz = sz; this.tint = tint;
        }
    }

    static final class Train {
        int type;
        float z, speed, cruise, target;
        float stopTimer;
        boolean announced;
        Train(int type, float z) {
            this.type = type; this.z = z;
            cruise = KMH[type] / 3.6f * 0.18f;
            speed = cruise; target = cruise;
        }
    }

    static final class Cop {
        float base, phase, x, z, see, stopTimer, life;
        boolean called, stopped, active = true;
    }

    static final class Ev {
        final String text;
        float life;
        Ev(String t) { text = t; life = 7f; }
    }

    static final class Hud {
        String[] lines = new String[4];
        int lineCount;
        String speed = "0 км/ч";
        String train = "—";
        String tip = "";
        boolean gameOver;
        float flash;
    }

    final List<Obj> scenery = new ArrayList<Obj>();
    final List<Train> trains = new ArrayList<Train>();
    final List<Cop> cops = new ArrayList<Cop>();
    final List<Cop> security = new ArrayList<Cop>();
    final List<Ev> events = new ArrayList<Ev>();
    final Mesh[] trainMeshes = new Mesh[4];
    Mesh humanMesh;

    volatile Hud hud = new Hud();
    volatile boolean caught;

    // игрок
    float px = 2.4f, py = 0f, pz = 0f;
    float yaw = 0f, pitch = -0.06f;
    float vx, vy, vz;
    boolean onGround = true, onRoof;
    boolean leftHand, rightHand;
    Train attached;
    float leftCd, rightCd, shockCd, flash, time, hudTimer;
    Train stoppedTrain;
    float stopTimer;
    int score;

    GameWorld() {
        for (int i = 0; i < 4; i++) trainMeshes[i] = buildTrainMesh(i);
        humanMesh = buildHumanMesh();
        buildScenery();
        reset();
    }

    // ---------- геометрия моделей ----------

    private Mesh buildTrainMesh(int type) {
        Mesh.Geo g = new Mesh.Geo();
        float[] body = BODY[type], ac = ACCENT[type];
        float[] dark = {0.08f, 0.10f, 0.14f};
        float[] glass = {0.14f, 0.30f, 0.42f};
        float[] metal = {0.45f, 0.48f, 0.52f};
        g.box(0, 0, 0, 2.9f, 3.0f, 22f, body);                 // кузов
        g.box(0, 1.62f, 0, 2.5f, 0.3f, 21.4f, ac);             // крыша
        g.box(0, -1.12f, 0, 2.94f, 0.26f, 22f, ac);            // полоса
        for (float z = -9.5f; z <= 9.5f; z += 2.7f) {
            g.box(1.47f, 0.52f, z, 0.05f, 0.92f, 1.75f, glass);
            g.box(-1.47f, 0.52f, z, 0.05f, 0.92f, 1.75f, glass);
            g.box(1.46f, -0.42f, z, 0.04f, 0.42f, 1.75f, dark);
            g.box(-1.46f, -0.42f, z, 0.04f, 0.42f, 1.75f, dark);
        }
        g.box(0, -0.1f, 11.2f, 2.4f, 2.5f, 1.8f, body);        // нос
        g.box(0, 0.82f, 12.12f, 1.9f, 0.7f, 0.06f, glass);     // лобовое стекло
        g.box(0, -0.75f, 12.13f, 1.6f, 0.22f, 0.06f, new float[]{1f, 0.9f, 0.5f});
        for (float z : new float[]{-7f, 7f}) {
            g.box(1.25f, -1.65f, z, 0.45f, 0.55f, 1.0f, dark);
            g.box(-1.25f, -1.65f, z, 0.45f, 0.55f, 1.0f, dark);
        }
        return g.build();
    }

    private Mesh buildHumanMesh() {
        Mesh.Geo g = new Mesh.Geo();
        float[] uniform = {0.72f, 0.74f, 0.78f};
        float[] trousers = {0.12f, 0.16f, 0.26f};
        float[] skin = {0.83f, 0.64f, 0.48f};
        float[] cap = {0.10f, 0.14f, 0.24f};
        g.box(-0.17f, 0.45f, 0, 0.28f, 0.9f, 0.32f, trousers);
        g.box(0.17f, 0.45f, 0, 0.28f, 0.9f, 0.32f, trousers);
        g.box(0, 1.35f, 0, 0.72f, 0.92f, 0.42f, uniform);
        g.box(0, 2.02f, 0, 0.4f, 0.4f, 0.4f, skin);
        g.box(0, 2.28f, 0, 0.52f, 0.14f, 0.52f, cap);
        g.box(0.21f, 1.55f, 0.22f, 0.12f, 0.12f, 0.04f, new float[]{1f, 0.82f, 0.2f});
        return g.build();
    }

    private void buildScenery() {
        Mesh.Geo gb = new Mesh.Geo();
        gb.box(1, 1, 1, WHITE);
        Mesh box = gb.build();
        Mesh.Geo gc = new Mesh.Geo();
        gc.cyl(1, 1, 10, WHITE);
        Mesh cyl = gc.build();

        float[] ballast = {0.30f, 0.31f, 0.33f};
        float[] concrete = {0.58f, 0.59f, 0.60f};
        float[] sleepers = {0.36f, 0.28f, 0.22f};
        float[] steel = {0.72f, 0.74f, 0.77f};
        float[] yellow = {0.95f, 0.78f, 0.12f};
        float[] wire = {0.22f, 0.24f, 0.27f};
        float[] roofCol = {0.42f, 0.48f, 0.58f};
        float[] blue = {0.12f, 0.35f, 0.62f};

        scenery.add(new Obj(box, 0, -0.25f, -150, 240, 0.5f, 1500, new float[]{0.24f, 0.31f, 0.24f}));
        scenery.add(new Obj(box, -1.2f, 0.0f, -150, 6.2f, 0.2f, 1500, ballast));
        scenery.add(new Obj(box, 2.5f, 0.35f, -150, 5.0f, 0.7f, 1500, concrete));
        scenery.add(new Obj(box, 0.02f, 0.72f, -150, 0.12f, 0.04f, 1500, yellow));
        scenery.add(new Obj(box, -2.0f, 0.25f, -150, 0.11f, 0.18f, 1500, steel));
        scenery.add(new Obj(box, -0.4f, 0.25f, -150, 0.11f, 0.18f, 1500, steel));
        for (float z = -420; z <= 420; z += 5.5f)
            scenery.add(new Obj(box, -1.2f, 0.08f, z, 3.4f, 0.14f, 0.42f, sleepers));

        scenery.add(new Obj(box, -1.2f, 5.2f, -150, 0.045f, 0.045f, 1500, wire));
        scenery.add(new Obj(box, -1.65f, 5.45f, -150, 0.035f, 0.035f, 1500, wire));
        for (float z = -420; z <= 420; z += 42f) {
            scenery.add(new Obj(cyl, -3.5f, 3.1f, z, 0.19f, 6.2f, 0.19f, steel));
            scenery.add(new Obj(box, -2.4f, 5.75f, z, 2.6f, 0.12f, 0.12f, steel));
        }

        for (float sz : new float[]{-300f, 0f, 300f}) {
            scenery.add(new Obj(box, 2.5f, 5.6f, sz, 8.2f, 0.32f, 74f, roofCol));
            scenery.add(new Obj(box, 6.2f, 2.9f, sz, 0.3f, 5.8f, 74f, concrete));
            scenery.add(new Obj(box, 2.5f, 4.65f, sz, 3.6f, 0.9f, 0.28f, blue));
            scenery.add(new Obj(box, 2.5f, 4.65f, sz - 0.16f, 3.0f, 0.5f, 0.04f, WHITE));
            for (float z = sz - 32; z <= sz + 32; z += 10.5f) {
                scenery.add(new Obj(cyl, 5.7f, 2.8f, z, 0.17f, 5.6f, 0.17f, steel));
                scenery.add(new Obj(cyl, 0.35f, 2.8f, z, 0.15f, 5.6f, 0.15f, steel));
            }
            scenery.add(new Obj(box, 11f, 4.2f, sz, 9f, 8.4f, 46f, new float[]{0.52f, 0.55f, 0.60f}));
            for (float z = sz - 18; z <= sz + 18; z += 4f) {
                scenery.add(new Obj(box, 6.48f, 3f, z, 0.06f, 1.6f, 2.2f, new float[]{0.20f, 0.38f, 0.50f}));
            }
        }

        float[] trunk = {0.34f, 0.25f, 0.18f};
        float[] leaves = {0.20f, 0.42f, 0.22f};
        for (float z = -400; z <= 400; z += 55f) {
            if (Math.abs(z) < 40 || Math.abs(Math.abs(z) - 300) < 40) continue;
            scenery.add(new Obj(cyl, -7f, 1.6f, z, 0.28f, 3.2f, 0.28f, trunk));
            scenery.add(new Obj(box, -7f, 4.2f, z, 2.6f, 2.8f, 2.6f, leaves));
        }
    }

    // ---------- состояние ----------

    void reset() {
        trains.clear(); cops.clear(); security.clear(); events.clear();
        trains.add(new Train(0, -70));
        trains.add(new Train(1, -190));
        trains.add(new Train(2, -310));
        trains.add(new Train(3, -430));
        float[][] spots = {{20f, 0f}, {-55f, 1.7f}, {75f, 3.4f}};
        for (float[] s : spots) {
            Cop c = new Cop();
            c.base = s[0]; c.phase = s[1]; c.x = 2.6f; c.z = c.base;
            cops.add(c);
        }
        px = 2.4f; py = 0f; pz = 0f; yaw = 0f; pitch = -0.06f;
        vx = vy = vz = 0f; onGround = true; onRoof = false;
        leftHand = rightHand = false; attached = null;
        leftCd = rightCd = shockCd = flash = time = hudTimer = 0f;
        stoppedTrain = null; stopTimer = 0f; score = 0; caught = false;
        log("Вы на станции. Дождитесь поезда и зацепитесь!");
        refreshHud();
    }

    void log(String s) {
        events.add(0, new Ev(s));
        if (events.size() > 5) events.remove(events.size() - 1);
    }

    private void stopNearestTrain() {
        if (stoppedTrain != null) { stopTimer = Math.max(stopTimer, 11f); return; }
        Train best = null; float bd = 1e9f;
        for (Train t : trains) {
            float d = Math.abs(t.z - pz);
            if (t.speed > 0.5f && d < bd) { bd = d; best = t; }
        }
        if (best != null) {
            stoppedTrain = best; best.target = 0f; stopTimer = 11f;
            log("ТБшник остановил поезд «" + NAMES[best.type] + "»");
        }
    }

    private void spawnSecurity(Cop from) {
        Cop s = new Cop();
        s.x = from.x; s.z = from.z; s.life = 24f; s.active = true;
        security.add(s);
    }

    boolean handsVisible() {
        return attached != null || nearbyTrain() != null;
    }

    private Train nearbyTrain() {
        Train best = null; float bd = 1e9f;
        for (Train t : trains) {
            float d = Math.abs(pz - (t.z - 30f));
            if (d < 34f && px < 1.9f && px > -1.6f && d < bd) { bd = d; best = t; }
        }
        return best;
    }

    private void detach(boolean left) {
        if (left) leftHand = false; else rightHand = false;
        if (leftHand || rightHand) return;
        Train t = attached;
        attached = null; onRoof = false;
        if (t != null) {
            vx = 2.8f; vy = 2.2f; vz = t.speed * 0.9f;
            onGround = false;
            log("Вы отпустили поезд");
        }
    }

    // ---------- обновление ----------

    void update(float dt, float lx, float ly, float rx, float ry) {
        if (caught) return;
        dt = Math3D.clamp(dt, 0f, 0.05f);
        time += dt;
        leftCd = Math.max(0, leftCd - dt);
        rightCd = Math.max(0, rightCd - dt);
        shockCd = Math.max(0, shockCd - dt);
        flash = Math.max(0, flash - dt * 1.6f);

        // поезда
        for (Train t : trains) {
            t.speed += (t.target - t.speed) * Math.min(1f, dt * 1.6f);
            t.z += t.speed * dt;
            if (!t.announced && t.z > -55f) {
                t.announced = true;
                log("Приближается «" + NAMES[t.type] + "» — " + (int) KMH[t.type] + " км/ч");
            }
            if (t.z > 520f) {
                t.type = (int) (Math.random() * 4);
                t.z = -520f - (float) Math.random() * 90f;
                t.cruise = KMH[t.type] / 3.6f * 0.18f;
                t.target = t.cruise; t.speed = t.cruise; t.announced = false;
            }
        }
        if (stoppedTrain != null) {
            stopTimer -= dt;
            if (stopTimer <= 0) {
                stoppedTrain.target = stoppedTrain.cruise;
                stoppedTrain = null;
                log("Поезд продолжил путь");
            }
        }

        // ТБшники
        for (Cop c : cops) {
            c.z = c.base + (float) Math.sin(time * 0.32f + c.phase) * 7f;
            c.x = 2.6f;
            float dx = c.x - px, dz = c.z - pz;
            float dist = (float) Math.sqrt(dx * dx + dz * dz);
            boolean sees = dist < 15f && px > 1.15f && py < 3.6f;
            if (sees) {
                c.see += dt;
                if (c.see > 1.3f && !c.called) {
                    c.called = true;
                    log("ТБшник вызвал охрану");
                    spawnSecurity(c);
                }
                if (c.see > 3.2f && !c.stopped) {
                    c.stopped = true;
                    stopNearestTrain();
                }
            } else {
                c.see = Math.max(0, c.see - dt * 0.5f);
                c.called = false;
            }
        }

        // охрана догоняет только если видит
        for (Cop s : security) {
            if (!s.active) continue;
            s.life -= dt;
            if (s.life <= 0) { s.active = false; continue; }
            float dx = px - s.x, dz = pz - s.z;
            float d = (float) Math.sqrt(dx * dx + dz * dz);
            if (d < 1.5f && py < 2.6f) {
                caught = true;
                log("Охрана догнала и задержала вас!");
                refreshHud();
                break;
            }
            if (d > 0.05f) {
                float step = Math.min(d, 4.4f * dt) / d;
                s.x += dx * step; s.z += dz * step;
            }
        }

        Train near = attached != null ? attached : nearbyTrain();

        if (near != null) {
            // левая половина экрана = левая рука, правая = правая
            if (!leftHand && leftCd <= 0 && lx < -0.55f) {
                leftHand = true; attached = near; score += 5;
                log("Левая рука зацепилась за «" + NAMES[near.type] + "»");
            }
            if (!rightHand && rightCd <= 0 && rx > 0.55f) {
                rightHand = true; attached = near; score += 5;
                log("Правая рука зацепилась за «" + NAMES[near.type] + "»");
            }
            if (leftHand && lx > 0.55f) { leftCd = 0.6f; detach(true); }
            if (rightHand && rx < -0.55f) { rightCd = 0.6f; detach(false); }

            if (attached != null) {
                onRoof = leftHand && rightHand && ly < -0.6f && ry < -0.6f;
                px = onRoof ? -1.2f : 0.42f;
                pz = attached.z - 30f;
                float targetY = onRoof ? 4.05f : 1.95f;
                py += (targetY - py) * Math.min(1f, dt * 6f);
                vx = 0; vy = 0; vz = attached.speed;
                onGround = false;
                if (onRoof && Math.abs(px + 1.2f) < 1.9f && shockCd <= 0) {
                    shockCd = 2.6f; flash = 1f;
                    detach(true); detach(false);
                    py = 2.6f; vy = -3.5f; vx = 2.5f;
                    log("⚡ Удар током! Провода сбросили вас");
                }
            } else if (ly < -0.5f && ry < -0.5f) {
                px -= 2.4f * dt;
            }
        } else {
            // пешком: левый стик — движение, правый — поворот головы
            vx = lx * 3.4f;
            vz = ly * 3.4f;
            yaw += rx * dt * 1.7f;
            pitch = Math3D.clamp(pitch - ry * dt * 1.3f, -1.15f, 1.15f);
            if (!onGround) {
                vy -= 12f * dt;
                py += vy * dt;
                if (py <= 0f) { py = 0f; vy = 0f; onGround = true; }
            }
            px += vx * dt;
            pz += vz * dt;
            px = Math3D.clamp(px, -0.6f, 5.3f);
        }

        for (int i = events.size() - 1; i >= 0; i--) {
            events.get(i).life -= dt;
            if (events.get(i).life <= 0) events.remove(i);
        }
        hudTimer -= dt;
        if (hudTimer <= 0) { hudTimer = 0.08f; refreshHud(); }
    }

    private void refreshHud() {
        Hud h = new Hud();
        h.lineCount = Math.min(events.size(), 4);
        for (int i = 0; i < h.lineCount; i++) h.lines[i] = events.get(i).text;
        h.train = attached != null ? NAMES[attached.type] : "—";
        float kmh = attached != null ? attached.speed / 0.18f * 3.6f
                : (float) Math.sqrt(vx * vx + vz * vz) * 3.6f;
        h.speed = (int) kmh + " км/ч";
        h.gameOver = caught;
        h.flash = flash;
        if (attached == null && time < 14f)
            h.tip = "Подойдите к поезду. Левый стик влево — левая рука, правый вправо — правая. Оба вверх — залезть на крышу (осторожно: провода!).";
        else if (attached != null && !onRoof)
            h.tip = "Держаться: не двигайте стик в противоположную сторону. Оба стика вверх — на крышу.";
        else h.tip = "";
        hud = h;
    }
}
