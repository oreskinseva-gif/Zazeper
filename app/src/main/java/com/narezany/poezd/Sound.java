package com.narezany.poezd;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

/** Процедурный звук без файлов: гул поезда, сигналы, ток, сирена. */
final class Sound {
    private static final Sound INSTANCE = new Sound();
    private static final int RATE = 22050;

    private AudioTrack track;
    private Thread thread;
    private volatile boolean running;
    private volatile float engine;
    private volatile float beepFreq;
    private volatile float beepLeft;
    private volatile float sirenLeft;

    static Sound get() { return INSTANCE; }

    void start() {
        if (running) return;
        try {
            int buf = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            if (buf < 2048) buf = 4096;
            track = new AudioTrack(AudioManager.STREAM_MUSIC, RATE,
                    AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    buf, AudioTrack.MODE_STREAM);
            track.setVolume(0.55f);
            track.play();
            running = true;
            thread = new Thread(new Runnable() {
                @Override public void run() { loop(); }
            }, "sound");
            thread.start();
        } catch (Throwable ignored) {
            running = false;
        }
    }

    void setEngine(float v) { engine = Math3D.clamp(v, 0f, 1f); }

    void beep(float freq, float seconds) {
        beepFreq = freq;
        beepLeft = seconds;
    }

    void zap() { beep(90f, 0.35f); }

    void siren() { sirenLeft = 2.5f; }

    private void loop() {
        short[] data = new short[512];
        double phase = 0, bp = 0, sp = 0;
        while (running) {
            for (int i = 0; i < data.length; i++) {
                double v = Math.sin(phase) * (0.05 + engine * 0.16);
                phase += 2 * Math.PI * (55 + engine * 190) / RATE;
                if (phase > 2 * Math.PI) phase -= 2 * Math.PI;

                if (beepLeft > 0) {
                    v += Math.sin(bp) * 0.22 * Math.min(1f, beepLeft * 4f);
                    bp += 2 * Math.PI * beepFreq / RATE;
                    beepLeft -= 1f / RATE;
                }
                if (sirenLeft > 0) {
                    double f = 520 + 260 * Math.sin(sp * 2);
                    v += Math.sin(sp * f / 60.0) * 0.14;
                    sp += 0.06;
                    sirenLeft -= 1f / RATE;
                }
                data[i] = (short) (Math3D.clamp((float) v, -1f, 1f) * 26000f);
            }
            try {
                if (track != null) track.write(data, 0, data.length);
                else break;
            } catch (Throwable t) {
                break;
            }
        }
    }

    void stop() {
        running = false;
        try { if (track != null) { track.stop(); track.release(); } } catch (Throwable ignored) {}
        track = null;
        thread = null;
    }
}
