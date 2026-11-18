package com.narezany.poezd;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Пишет диагностику в Download/Zazeper, чтобы понять причину вылета. */
final class CrashLog {
    private static final File DIR = new File("/storage/emulated/0/Download/Zazeper");
    private static final File DEBUG = new File(DIR, "debug.txt");
    private static final File CRASH = new File(DIR, "crash.txt");

    static void init(Context c) {
        DIR.mkdirs();
        CRASH.delete();
        DEBUG.delete();
        mark("app start");
        final Thread.UncaughtExceptionHandler old = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                write(CRASH, stack(e));
                if (old != null) old.uncaughtException(t, e);
                else System.exit(1);
            }
        });
    }

    static void mark(String s) {
        write(DEBUG, new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date()) + " " + s);
    }

    static void error(String tag, Throwable e) {
        mark(tag + ": " + stack(e));
    }

    private static String stack(Throwable e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private static void write(File f, String s) {
        try {
            FileOutputStream out = new FileOutputStream(f, true);
            out.write((s + "\n").getBytes("UTF-8"));
            out.close();
        } catch (Throwable ignored) {
        }
    }
}
