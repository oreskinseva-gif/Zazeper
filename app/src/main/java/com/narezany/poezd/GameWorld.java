package com.narezany.poezd;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/** Всё состояние игры: поезда, игрок, ТБшники, охрана, провода, задания. */
final class GameWorld {
    static final int MENU = 0, PLAY = 1, LOSE = 2, WIN = 3;

    static final float[] WHITE = {1f, 1f, 1f};
    static final String[] NAMES = {"Сапсан", "Ласточка", "Метеор", "Базовый поезд"};
    static final float[] KMH = {250f, 210f, 400f, 170f};
    static final float[][] BODY = {
        {0.90f, 0.93f, 0.98f}, {0.88f, 0.18f, 0.16f},
        {0.98f, 0.76f, 0.12f}, {0.20f, 0.62f, 0.42f}
    };
    private static final float[][] ACCENT = {
        {0.08f, 0.38f, 0.82f}, {0.96f, 0.96f, 0.96f},
        {0.14f, 0.14f, 0.17f}, {0.94f, 0.94f, 0.94f}
    };

    static final String[] MISSIONS = {
        "Зацепитесь за любой поезд",
        "Проедьте 250 метров",
        "Покатитесь на «Метеоре» 400 км/ч",
        "Доедьте до следующей станции"
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
        boolean announced;
        Train(int type, float z) {
            this.type = type; this.z = z;
            cruise = KMH[type] / 3.6f * 0.18f;
            speed = cruise; target = cruise;
        }
    }

    static final class Cop {
        float base, phase, x, z, see, life;
        boolean called, stopped, active = true;
    }

    static final class Ev {
        final String text;
        float life;
        Ev(String t) { text = t; life = 6.5f; }
    }

    static final class Hud {
        String[] lines = new String[4];
        int lineCount;
        String speed = "0 км/ч";
        String train = "—";
        String tip = "";
        String mission = "";
        String score = "0";
        String best = "0";
        String timer = "00:00";
        float missionProgress;
        float alert;
        boolean nearTrain;
        boolean leftReady, rightReady;
        boolean gameOver, win, menu;
        float flash;
        int health = 100;
        int ach;
        String style = "Синий";
        boolean brokenLeg, brokenArm, hospital;
    }

    Context ctx;
    final List<Obj> scenery = new ArrayList<Obj>();
    final List<Train> trains = new ArrayList<Train>();
    final List<Cop> cops = new ArrayList<Cop>();
    final List<Cop> security = new ArrayList<Cop>();
    final List<Ev> events = new ArrayList<Ev>();
    final Mesh[] trainMeshes = new Mesh[4];
    final List<Obj> civilians = new ArrayList<Obj>();
    Mesh humanMesh, carMesh, busMesh, civilianMesh;

    volatile Hud hud = new Hud();
    volatile boolean jumpRequested;
    volatile int state = MENU;

    float px = 2.4f, py = 0f, pz = 0f;
    float yaw = 0f, pitch = -0.05f;
    float vx, vy, vz;
    boolean onGround = true, onRoof;
    boolean leftHand, rightHand;
    Train attached;
    float leftCd, rightCd, shockCd, flash, time, hudTimer;
    Train stoppedTrain;
    float stopTimer;
    int score, best;
    boolean[] missionDone = new boolean[4];
    int missionIndex;
    float ridden;
    float health = 100f, fallStartY;
    boolean brokenLeg, brokenArm;
    int style, achievements;
    float clackTimer, armSlip;
    boolean atStation;
    String loseReason = "";

    static final String[] STYLES = {"Синий", "Красный", "Зелёный", "Чёрный"};
    static final float[][] STYLE_COLORS = {
        {0.20f, 0.34f, 0.62f}, {0.65f, 0.16f, 0.16f},
        {0.16f, 0.48f, 0.30f}, {0.16f, 0.17f, 0.20f}
    };

    GameWorld(Context ctx) {
        this.ctx = ctx;
        SharedPreferences p = ctx.getSharedPreferences("poezd", Context.MODE_PRIVATE);
        best = p.getInt("best", 0);
        achievements = p.getInt("ach", 0);
        style = (int) Math3D.clamp(p.getInt("style", 0), 0f, STYLES.length - 1f);
        for (int i = 0; i < 4; i++) trainMeshes[i] = buildTrainMesh(i);
        humanMesh = buildHumanMesh();
        carMesh = buildCarMesh();
        busMesh = buildBusMesh();
        civilianMesh = buildCivilianMesh();
        buildScenery();
        reset(false);
    }

    void cycleStyle() {
        style = (style + 1) % STYLES.length;
        saveBest();
        Sound.get().beep(600 + style * 90, 0.08f);
        refreshHud();
    }

    void addAch(int bit) {
        if ((achievements & bit) != 0) return;
        achievements |= bit;
        saveBest();
        log("Достижение получено! (" + Integer.bitCount(achievements) + "/8)");
    }

    void lose(String reason) {
        if (state == LOSE) return;
        state = LOSE;
        loseReason = reason;
        score += Math.max(0, (int) ridden / 10);
        saveBest();
        log(reason);
        refreshHud();
    }

    void jump() {
        if (state != PLAY || attached != null || !onGround) return;
        vy = brokenLeg ? 4.4f : 6.4f;
        onGround = false;
        fallStartY = py;
        Sound.get().beep(300, 0.06f);
    }

    // ---------- геометрия ----------

    private Mesh buildTrainMesh(int type) {
        Mesh.Geo g = new Mesh.Geo();
        float[] body = BODY[type], ac = ACCENT[type];
        float[] dark = {0.07f, 0.09f, 0.12f};
        float[] glass = {0.10f, 0.28f, 0.40f};
        float[] lamp = {1f, 0.94f, 0.72f};
        float[] steel = {0.72f, 0.75f, 0.78f};

        g.box(0, 0, 0, 2.95f, 3.0f, 22.6f, body);
        g.box(0, 1.63f, 0, 2.6f, 0.28f, 21.8f, ac);
        g.box(0, -1.12f, 0, 3.0f, 0.24f, 22.6f, ac);
        g.box(0, 1.82f, -6f, 1.5f, 0.22f, 2.4f, dark);
        g.box(0, 1.82f, 6f, 1.5f, 0.22f, 2.4f, dark);
        for (float z = -10f; z <= 10f; z += 2.6f) {
            g.box(1.49f, 0.55f, z, 0.05f, 0.92f, 1.8f, glass);
            g.box(-1.49f, 0.55f, z, 0.05f, 0.92f, 1.8f, glass);
            g.box(1.48f, -0.35f, z, 0.04f, 0.34f, 1.8f, dark);
            g.box(-1.48f, -0.35f, z, 0.04f, 0.34f, 1.8f, dark);
        }
        // нос: три ступени, чтобы был похож на обтекаемый
        g.box(0, -0.05f, 11.6f, 2.75f, 2.8f, 1.5f, body);
        g.box(0, -0.1f, 12.5f, 2.35f, 2.35f, 1.0f, body);
        g.box(0, 0.85f, 12.98f, 1.95f, 0.75f, 0.08f, glass);
        g.box(-0.82f, -0.75f, 13.02f, 0.42f, 0.2f, 0.06f, lamp);
        g.box(0.82f, -0.75f, 13.02f, 0.42f, 0.2f, 0.06f, lamp);
        g.box(0, -1.15f, 13.02f, 2.2f, 0.12f, 0.06f, ac);
        for (float z : new float[]{-7.5f, 7.5f}) {
            g.box(1.26f, -1.68f, z, 0.45f, 0.55f, 1.05f, dark);
            g.box(-1.26f, -1.68f, z, 0.45f, 0.55f, 1.05f, dark);
        }
        // пантограф и рёбра крыши
        g.box(0, 1.79f, -5.5f, 1.9f, 0.05f, 0.9f, dark);
        for (float x : new float[]{-0.6f, 0.6f}) {
            g.box(x, 2.15f, -5.5f, 0.09f, 0.7f, 0.09f, steel);
        }
        g.box(0, 2.5f, -5.5f, 1.55f, 0.08f, 0.12f, steel);
        g.box(0, 2.62f, -5.5f, 0.5f, 0.12f, 0.5f, new float[]{0.12f, 0.12f, 0.14f});
        for (float z = -9f; z <= 9f; z += 3f)
            g.box(0, 1.79f, z, 2.4f, 0.03f, 0.08f, ac);
        // табло маршрута
        g.box(0, 1.35f, 12.99f, 1.3f, 0.28f, 0.04f, new float[]{0.05f, 0.10f, 0.13f});
        return g.build();
    }

    private Mesh buildHumanMesh() {
        Mesh.Geo g = new Mesh.Geo();
        float[] uniform = {0.74f, 0.76f, 0.80f};
        float[] trousers = {0.11f, 0.15f, 0.25f};
        float[] skin = {0.84f, 0.65f, 0.49f};
        float[] cap = {0.09f, 0.13f, 0.23f};
        g.box(-0.17f, 0.45f, 0, 0.28f, 0.9f, 0.32f, trousers);
        g.box(0.17f, 0.45f, 0, 0.28f, 0.9f, 0.32f, trousers);
        g.box(0, 1.35f, 0, 0.74f, 0.92f, 0.44f, uniform);
        g.box(-0.47f, 1.35f, 0, 0.2f, 0.85f, 0.25f, uniform);
        g.box(0.47f, 1.35f, 0, 0.2f, 0.85f, 0.25f, uniform);
        g.box(-0.47f, 0.86f, 0, 0.19f, 0.2f, 0.22f, skin);
        g.box(0.47f, 0.86f, 0, 0.19f, 0.2f, 0.22f, skin);
        g.box(0, 2.02f, 0, 0.4f, 0.4f, 0.4f, skin);
        g.box(0, 2.28f, 0, 0.54f, 0.14f, 0.54f, cap);
        g.box(0, 2.22f, 0.25f, 0.5f, 0.05f, 0.16f, cap);
        g.box(0.22f, 1.55f, 0.23f, 0.13f, 0.13f, 0.04f, new float[]{1f, 0.82f, 0.2f});
        return g.build();
    }

    private Mesh buildCarMesh() {
        Mesh.Geo g = new Mesh.Geo();
        float[] body = {0.72f, 0.20f, 0.18f};
        float[] glass = {0.16f, 0.30f, 0.38f};
        float[] dark = {0.09f, 0.10f, 0.12f};
        g.box(0, 0, 0, 1.9f, 0.8f, 4.2f, body);
        g.box(0, 0.62f, -0.2f, 1.55f, 0.55f, 2.1f, glass);
        g.box(0, 0.62f, 1.55f, 1.55f, 0.5f, 0.9f, glass);
        for (float x : new float[]{-0.95f, 0.95f})
            for (float z : new float[]{-1.4f, 1.4f})
                g.box(x, -0.38f, z, 0.22f, 0.42f, 0.55f, dark);
        g.box(-0.6f, 0.46f, 2.12f, 0.35f, 0.16f, 0.05f, new float[]{1f, 0.92f, 0.7f});
        g.box(0.6f, 0.46f, 2.12f, 0.35f, 0.16f, 0.05f, new float[]{1f, 0.92f, 0.7f});
        return g.build();
    }

    private Mesh buildBusMesh() {
        Mesh.Geo g = new Mesh.Geo();
        float[] body = {0.16f, 0.52f, 0.32f};
        float[] glass = {0.18f, 0.34f, 0.42f};
        float[] dark = {0.09f, 0.10f, 0.12f};
        g.box(0, 0, 0, 2.3f, 2.3f, 7.5f, body);
        for (float z = -3f; z <= 3f; z += 1.5f) {
            g.box(1.16f, 0.5f, z, 0.04f, 0.85f, 1.2f, glass);
            g.box(-1.16f, 0.5f, z, 0.04f, 0.85f, 1.2f, glass);
        }
        g.box(0, 0.6f, 3.78f, 1.7f, 1.0f, 0.05f, glass);
        for (float z : new float[]{-2.4f, 2.2f})
            for (float x : new float[]{-1.0f, 1.0f})
                g.box(x, -1.02f, z, 0.35f, 0.5f, 0.7f, dark);
        return g.build();
    }

    private Mesh buildCivilianMesh() {
        Mesh.Geo g = new Mesh.Geo();
        float[] coat = {0.62f, 0.30f, 0.24f};
        float[] legs = {0.20f, 0.22f, 0.28f};
        float[] skin = {0.82f, 0.64f, 0.48f};
        g.box(-0.14f, 0.42f, 0, 0.22f, 0.85f, 0.25f, legs);
        g.box(0.14f, 0.42f, 0, 0.22f, 0.85f, 0.25f, legs);
        g.box(0, 1.28f, 0, 0.6f, 0.9f, 0.36f, coat);
        g.box(-0.38f, 1.28f, 0, 0.16f, 0.8f, 0.2f, coat);
        g.box(0.38f, 1.28f, 0, 0.16f, 0.8f, 0.2f, coat);
        g.box(0, 1.93f, 0, 0.34f, 0.34f, 0.34f, skin);
        return g.build();
    }

    private void buildScenery() {
        Mesh.Geo gb = new Mesh.Geo();
        gb.box(1, 1, 1, WHITE);
        Mesh box = gb.build();
        Mesh.Geo gc = new Mesh.Geo();
        gc.cyl(1, 1, 12, WHITE);
        Mesh cyl = gc.build();

        float[] ballast = {0.29f, 0.30f, 0.32f};
        float[] concrete = {0.60f, 0.61f, 0.62f};
        float[] tile2 = {0.53f, 0.55f, 0.57f};
        float[] sleepers = {0.34f, 0.26f, 0.20f};
        float[] steel = {0.74f, 0.76f, 0.79f};
        float[] yellow = {0.97f, 0.80f, 0.10f};
        float[] wire = {0.20f, 0.22f, 0.25f};
        float[] roofCol = {0.38f, 0.46f, 0.58f};
        float[] blue = {0.10f, 0.33f, 0.64f};
        float[] lamp = {1f, 0.92f, 0.62f};
        float[] dark = {0.14f, 0.16f, 0.19f};

        scenery.add(new Obj(box, 0, -0.25f, -150, 260, 0.5f, 1500, new float[]{0.24f, 0.34f, 0.22f}));
        scenery.add(new Obj(box, -1.2f, 0.0f, -150, 6.4f, 0.2f, 1500, ballast));
        scenery.add(new Obj(box, 2.5f, 0.35f, -150, 5.0f, 0.7f, 1500, concrete));
        scenery.add(new Obj(box, 0.02f, 0.72f, -150, 0.14f, 0.05f, 1500, yellow));
        scenery.add(new Obj(box, -2.0f, 0.26f, -150, 0.12f, 0.19f, 1500, steel));
        scenery.add(new Obj(box, -0.4f, 0.26f, -150, 0.12f, 0.19f, 1500, steel));
        scenery.add(new Obj(box, -2.0f, 0.36f, -150, 0.06f, 0.02f, 1500, new float[]{0.92f, 0.95f, 0.98f}));
        scenery.add(new Obj(box, -0.4f, 0.36f, -150, 0.06f, 0.02f, 1500, new float[]{0.92f, 0.95f, 0.98f}));

        for (float z = -420; z <= 420; z += 8f)
            scenery.add(new Obj(box, -1.2f, 0.09f, z, 3.5f, 0.14f, 0.46f, sleepers));
        for (float z = -400; z <= 400; z += 9f) {
            float[] tint = ((int) ((z + 400) / 9) % 2 == 0) ? concrete : tile2;
            scenery.add(new Obj(box, 2.5f, 0.705f, z, 4.7f, 0.02f, 8.6f, tint));
        }

        scenery.add(new Obj(box, -1.2f, 5.3f, -150, 0.05f, 0.05f, 1500, wire));
        scenery.add(new Obj(box, -1.7f, 5.55f, -150, 0.04f, 0.04f, 1500, wire));
        for (float z = -420; z <= 420; z += 55f) {
            scenery.add(new Obj(cyl, -3.6f, 3.2f, z, 0.2f, 6.4f, 0.2f, steel));
            scenery.add(new Obj(box, -2.4f, 5.85f, z, 2.8f, 0.13f, 0.13f, steel));
            scenery.add(new Obj(box, -2.4f, 6.05f, z, 0.3f, 0.25f, 0.3f, dark));
        }

        for (float sz : new float[]{-300f, 0f, 300f}) {
            scenery.add(new Obj(box, 2.5f, 5.7f, sz, 8.6f, 0.34f, 78f, roofCol));
            scenery.add(new Obj(box, 6.2f, 2.9f, sz, 0.3f, 5.8f, 78f, concrete));
            scenery.add(new Obj(box, 2.5f, 4.7f, sz, 3.8f, 1.0f, 0.3f, blue));
            scenery.add(new Obj(box, 2.5f, 4.7f, sz - 0.17f, 3.2f, 0.55f, 0.04f, WHITE));
            scenery.add(new Obj(box, 2.5f, 5.48f, sz, 8.8f, 0.22f, 0.5f, yellow));
            for (float z = sz - 34; z <= sz + 34; z += 11f) {
                scenery.add(new Obj(cyl, 5.8f, 2.85f, z, 0.18f, 5.7f, 0.18f, steel));
                scenery.add(new Obj(cyl, 0.3f, 2.85f, z, 0.16f, 5.7f, 0.16f, steel));
                scenery.add(new Obj(box, 5.8f, 5.35f, z, 0.7f, 0.18f, 0.7f, lamp));
                // скамейка
                scenery.add(new Obj(box, 5.0f, 1.05f, z + 4f, 0.6f, 0.12f, 2.0f, new float[]{0.45f, 0.28f, 0.16f}));
                scenery.add(new Obj(box, 5.0f, 1.45f, z + 4f, 0.12f, 0.8f, 2.0f, new float[]{0.45f, 0.28f, 0.16f}));
                scenery.add(new Obj(box, 5.0f, 0.85f, z + 4f, 0.12f, 0.4f, 0.12f, dark));
            }
            scenery.add(new Obj(box, 1.1f, 0.95f, sz + 12f, 0.5f, 0.5f, 0.5f, dark));
            scenery.add(new Obj(box, 11f, 4.3f, sz, 9.5f, 8.6f, 50f, new float[]{0.50f, 0.54f, 0.60f}));
            scenery.add(new Obj(box, 6.2f, 8.7f, sz, 0.4f, 0.4f, 40f, blue));
            for (float z = sz - 20; z <= sz + 20; z += 4.5f) {
                scenery.add(new Obj(box, 6.22f, 3.1f, z, 0.07f, 1.8f, 2.5f, new float[]{0.18f, 0.40f, 0.55f}));
            }
        }

        float[] trunk = {0.32f, 0.23f, 0.16f};
        float[] leaves = {0.18f, 0.42f, 0.20f};
        for (float z = -400; z <= 400; z += 55f) {
            if (Math.abs(z) < 40 || Math.abs(Math.abs(z) - 300) < 40) continue;
            scenery.add(new Obj(cyl, -7.5f, 1.7f, z, 0.3f, 3.4f, 0.3f, trunk));
            scenery.add(new Obj(box, -7.5f, 4.4f, z, 3.0f, 3.0f, 3.0f, leaves));
            scenery.add(new Obj(box, -7.5f, 6.1f, z, 2.0f, 1.6f, 2.0f, new float[]{0.22f, 0.48f, 0.24f}));
        }

        // город на горизонте
        float[] city = {0.30f, 0.40f, 0.52f};
        for (int i = 0; i < 28; i++) {
            float h = 9f + (float) Math.abs(Math.sin(i * 2.3f)) * 26f;
            scenery.add(new Obj(box, -84f + i * 6.4f, h * 0.5f - 1f, -660f,
                    5.2f, h, 6f, city));
        }

        // машины и автобусы у станций
        float[][] cars = {
            {0.72f, 0.20f, 0.18f}, {0.90f, 0.90f, 0.92f},
            {0.14f, 0.30f, 0.60f}, {0.88f, 0.72f, 0.16f}
        };
        for (float sz : new float[]{-300f, 0f, 300f}) {
            for (int i = 0; i < 4; i++) {
                scenery.add(new Obj(carMesh, 11.4f + (i % 2) * 2.8f, 0.45f,
                        sz + 15f + i * 11f, 0f, 1f, 1f, 1f, cars[i]));
            }
            scenery.add(new Obj(busMesh, 15.5f, 1.3f, sz - 10f,
                    0f, 1f, 1f, 1f, new float[]{0.16f, 0.50f, 0.32f}));
        }

        // люди на платформах
        float[][] coats = {
            {0.62f, 0.30f, 0.24f}, {0.20f, 0.34f, 0.62f},
            {0.32f, 0.46f, 0.26f}, {0.52f, 0.30f, 0.55f}
        };
        for (int i = 0; i < 22; i++) {
            civilians.add(new Obj(civilianMesh, 3.6f + (i % 3) * 1.1f, 0.72f,
                    -300f + i * 28f, 0f, 1f, 1f, 1f, coats[i % coats.length]));
        }
    }

    // ---------- состояние ----------

    void reset(boolean startPlaying) {
        trains.clear(); cops.clear(); security.clear(); events.clear();
        trains.add(new Train(0, -80));
        trains.add(new Train(1, -200));
        trains.add(new Train(2, -320));
        trains.add(new Train(3, -440));
        trains.add(new Train(1, -560));
        float[][] spots = {{20f, 0f}, {-55f, 1.7f}, {75f, 3.4f}, {140f, 5f}};
        for (float[] s : spots) {
            Cop c = new Cop();
            c.base = s[0]; c.phase = s[1]; c.x = 2.6f; c.z = c.base;
            cops.add(c);
        }
        px = 2.4f; py = 0f; pz = 0f; yaw = 0f; pitch = -0.05f;
        vx = vy = vz = 0f; onGround = true; onRoof = false;
        leftHand = rightHand = false; attached = null;
        leftCd = rightCd = shockCd = flash = time = hudTimer = 0f;
        stoppedTrain = null; stopTimer = 0f; score = 0; ridden = 0f;
        health = 100f; fallStartY = 0f; brokenLeg = false; brokenArm = false;
        jumpRequested = false; clackTimer = 0f; armSlip = 0f; loseReason = "";
        missionDone = new boolean[4]; missionIndex = 0;
        state = startPlaying ? PLAY : MENU;
        log("Добро пожаловать! Задание: зацепитесь за поезд.");
        refreshHud();
    }

    void start() {
        reset(true);
        log("Поехали! Задание: " + MISSIONS[0]);
        Sound.get().beep(700, 0.14f);
    }

    void saveBest() {
        if (score > best) best = score;
        try {
            SharedPreferences p = ctx.getSharedPreferences("poezd", Context.MODE_PRIVATE);
            p.edit().putInt("best", best).putInt("style", style)
                    .putInt("ach", achievements).apply();
        } catch (Throwable ignored) {}
    }

    void log(String s) {
        events.add(0, new Ev(s));
        if (events.size() > 5) events.remove(events.size() - 1);
        if (s.contains("Удар") || s.contains("током")) Sound.get().zap();
        else if (s.contains("ТБшник") || s.contains("охран")) Sound.get().siren();
        else if (s.contains("Задание") || s.contains("задание")) Sound.get().beep(920, 0.16f);
        else Sound.get().beep(420, 0.05f);
    }

    private void completeMission() {
        missionDone[missionIndex] = true;
        score += 100;
        log("Задание выполнено! +100 очков");
        missionIndex++;
        if (missionIndex >= MISSIONS.length) {
            state = WIN;
            addAch(8);
            if (health >= 99f) addAch(32);
            score += 200;
            saveBest();
            log("Все задания выполнены! Победа!");
        } else {
            log("Новое задание: " + MISSIONS[missionIndex]);
        }
    }

    private void stopNearestTrain() {
        if (stoppedTrain != null) { stopTimer = Math.max(stopTimer, 12f); return; }
        Train bestT = null; float bd = 1e9f;
        for (Train t : trains) {
            float d = Math.abs(t.z - pz);
            if (t.speed > 0.5f && d < bd) { bd = d; bestT = t; }
        }
        if (bestT != null) {
            stoppedTrain = bestT; bestT.target = 0f; stopTimer = 12f;
            log("ТБшник остановил поезд «" + NAMES[bestT.type] + "»");
        }
    }

    private void spawnSecurity(Cop from) {
        Cop s = new Cop();
        s.x = from.x; s.z = from.z; s.life = 26f; s.active = true;
        security.add(s);
        if (security.size() > 4) security.remove(0);
    }

    boolean handsVisible() {
        return state == PLAY && (attached != null || nearbyTrain() != null);
    }

    private Train nearbyTrain() {
        Train best = null; float bd = 1e9f;
        for (Train t : trains) {
            float d = Math.abs(pz - (t.z - 36f));
            if (d < 40f && px < 2.0f && px > -1.7f && d < bd) { bd = d; best = t; }
        }
        return best;
    }

    private void detach(boolean left) {
        if (left) leftHand = false; else rightHand = false;
        if (leftHand || rightHand) return;
        Train t = attached;
        attached = null; onRoof = false;
        fallStartY = py;
        if (t != null) {
            vx = 2.8f; vy = 2.4f; vz = t.speed * 0.9f;
            onGround = false;
            log("Вы отпустили поезд");
        }
    }

    // ---------- обновление ----------

    void update(float dt, float lx, float ly, float rx, float ry) {
        dt = Math3D.clamp(dt, 0f, 0.05f);
        time += dt;
        flash = Math.max(0, flash - dt * 1.6f);

        // поезда движутся и в меню — красивый фон
        for (Train t : trains) {
            t.speed += (t.target - t.speed) * Math.min(1f, dt * 1.6f);
            t.z += t.speed * dt;
            if (state == PLAY && !t.announced && t.z > -60f) {
                t.announced = true;
                log("Приближается «" + NAMES[t.type] + "» — " + (int) KMH[t.type] + " км/ч");
            }
            if (t.z > 540f) {
                t.type = (int) (Math.random() * 4);
                t.z = -560f - (float) Math.random() * 90f;
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
        Sound.get().setEngine(attached != null ? attached.speed / 12f : 0.05f);

        if (state != PLAY) {
            hudTimer -= dt;
            if (hudTimer <= 0) { hudTimer = 0.1f; refreshHud(); }
            return;
        }

        leftCd = Math.max(0, leftCd - dt);
        rightCd = Math.max(0, rightCd - dt);
        shockCd = Math.max(0, shockCd - dt);

        for (Cop c : cops) {
            c.z = c.base + (float) Math.sin(time * 0.32f + c.phase) * 7f;
            c.x = 2.6f;
            float dx = c.x - px, dz = c.z - pz;
            float dist = (float) Math.sqrt(dx * dx + dz * dz);
            boolean sees = dist < 16f && (px > 1.15f || (attached != null && attached.speed < 1f && dist < 7f));
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

        for (Cop s : security) {
            if (!s.active) continue;
            s.life -= dt;
            if (s.life <= 0) { s.active = false; continue; }
            float dx = px - s.x, dz = pz - s.z;
            float d = (float) Math.sqrt(dx * dx + dz * dz);
            boolean canCatch = py < 2.6f || (attached != null && attached.speed < 1f);
            if (d < 1.6f && canCatch) {
                lose("Охрана догнала и задержала вас!");
                return;
            }
            if (d > 0.05f) {
                float step = Math.min(d, 4.6f * dt) / d;
                s.x += dx * step; s.z += dz * step;
            }
        }

        Train near = attached != null ? attached : nearbyTrain();

        if (near != null) {
            if (!leftHand && leftCd <= 0 && lx < -0.55f) {
                leftHand = true; attached = near; score += 5;
                log("Левая рука зацепилась за «" + NAMES[near.type] + "»");
                addAch(1);
                if (near.speed > 9f) addAch(64);
                if (missionIndex == 0) completeMission();
            }
            if (!rightHand && !brokenArm && rightCd <= 0 && rx > 0.55f) {
                rightHand = true; attached = near; score += 5;
                log("Правая рука зацепилась за «" + NAMES[near.type] + "»");
                addAch(1);
                if (near.speed > 9f) addAch(64);
                if (missionIndex == 0) completeMission();
            }
            if (leftHand && lx > 0.55f) { leftCd = 0.6f; detach(true); }
            if (rightHand && rx < -0.55f) { rightCd = 0.6f; detach(false); }

            if (attached != null) {
                ridden += Math.abs(attached.speed) * dt;
                clackTimer -= dt;
                if (clackTimer <= 0f && attached.speed > 2f) {
                    clackTimer = Math.max(0.055f, 0.6f / attached.speed);
                    Sound.get().beep(150f, 0.02f);
                }
                if (brokenArm && rightHand) {
                    armSlip += dt;
                    if (armSlip > 1.6f) {
                        armSlip = 0f;
                        detach(false);
                        log("Сломанная рука соскользнула!");
                    }
                }
                onRoof = leftHand && rightHand && ly < -0.6f && ry < -0.6f;
                if (onRoof) addAch(2);
                px = onRoof ? -1.2f : 0.42f;
                pz = attached.z - 36f;
                float targetY = onRoof ? 4.05f : 1.95f;
                py += (targetY - py) * Math.min(1f, dt * 6f);
                vx = 0; vy = 0; vz = attached.speed;
                onGround = false;

                if (missionIndex == 1 && ridden >= 250f) completeMission();
                if (missionIndex == 2 && attached.type == 2 && ridden > 300f) {
                    addAch(4);
                    completeMission();
                }
                if (missionIndex == 3 && pz > 250f) completeMission();

                if (onRoof && Math.abs(px + 1.2f) < 1.9f && shockCd <= 0) {
                    shockCd = 2.6f; flash = 1f;
                    detach(true); detach(false);
                    py = 2.6f; vy = -3.5f; vx = 2.5f;
                    health -= 16f;
                    log("⚡ Удар током! Провода сбросили вас");
                    if (health <= 0f) { lose("Погиб от удара током"); return; }
                }
            } else if (ly < -0.5f && ry < -0.5f) {
                px -= 2.4f * dt;
            }
        } else {
            float spd = brokenLeg ? 2.0f : 3.4f;
            vx = lx * spd;
            vz = ly * spd;
            yaw += rx * dt * 1.7f;
            pitch = Math3D.clamp(pitch - ry * dt * 1.3f, -1.15f, 1.15f);
            if (jumpRequested) { jumpRequested = false; jump(); }
            if (!onGround) {
                vy -= 12f * dt;
                py += vy * dt;
                if (py <= 0f) {
                    float fell = fallStartY - py;
                    py = 0f; vy = 0f; onGround = true;
                    if (fell > 3.2f) {
                        health -= (fell - 3.2f) * 7f;
                        flash = 0.8f;
                        if (fell > 10f) {
                            brokenLeg = true; brokenArm = true;
                            log("Переломы ноги и руки! Больница у станции");
                        } else if (fell > 6.5f) {
                            brokenLeg = true;
                            log("Перелом ноги! Выдвигайтесь медленнее");
                        } else {
                            log("Удар при падении: -" + (int) ((fell - 3.2f) * 7f) + " HP");
                        }
                        if (health <= 0f) { lose("Вы разбились"); return; }
                    }
                }
            }
            px += vx * dt;
            pz += vz * dt;
            px = Math3D.clamp(px, -0.6f, 5.3f);
        }

        // больница у станции: лечит и ставит на ноги
        atStation = false;
        for (float sz : new float[]{-300f, 0f, 300f})
            if (Math.abs(pz - sz) < 16f && px > 2.8f) atStation = true;
        if (atStation && health < 100f) {
            health = Math.min(100f, health + 14f * dt);
            if (brokenLeg || brokenArm) {
                brokenLeg = false; brokenArm = false;
                log("Больница: вас полечили и поставили на ноги");
                addAch(16);
            }
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
        h.score = String.valueOf(score);
        h.best = String.valueOf(best);
        int secs = (int) time;
        h.timer = String.format("%02d:%02d", secs / 60, secs % 60);
        h.menu = state == MENU;
        h.gameOver = state == LOSE;
        h.win = state == WIN;
        h.flash = flash;
        h.mission = missionIndex < MISSIONS.length ? MISSIONS[missionIndex] : "Выполнено!";
        h.missionProgress = missionIndex == 1 ? Math3D.clamp(ridden / 250f, 0, 1)
                : (missionIndex == 3 ? Math3D.clamp(pz / 250f, 0, 1)
                : (missionIndex >= MISSIONS.length || missionDone[missionIndex] ? 1f : 0f));
        float alert = 0f;
        for (Cop c : cops) alert = Math.max(alert, c.see / 3.2f);
        for (Cop s : security) if (s.active) alert = Math.max(alert, 0.75f);
        h.alert = Math3D.clamp(alert, 0f, 1f);
        Train n = nearbyTrain();
        h.nearTrain = n != null;
        h.leftReady = n != null && !leftHand;
        h.rightReady = n != null && !rightHand;
        if (attached == null && time < 18f)
            h.tip = "Подойдите к поезду: левый стик влево — левая рука, правый вправо — правая. Оба вверх — на крышу (там провода!).";
        else if (attached != null && !onRoof)
            h.tip = "Держитесь: не двигайте стик в противоположную сторону. Оба стика вверх — на крышу.";
        else h.tip = "";
        h.health = Math.max(0, (int) health);
        h.ach = Integer.bitCount(achievements);
        h.style = STYLES[style];
        h.brokenLeg = brokenLeg;
        h.brokenArm = brokenArm;
        h.hospital = atStation && health < 99.5f;
        hud = h;
    }
}
