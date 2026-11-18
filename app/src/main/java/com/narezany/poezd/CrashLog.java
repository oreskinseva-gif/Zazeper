package com.narezany.poezd;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Пишет диагностику в Download/Zazeper через MediaStore (работает на Android 10+). */
final class CrashLog {
    private static final String REL = Environment.DIRECTORY_DOWNLOADS + "/Zazeper";
    private static final String DEBUG_NAME = "debug.txt";
    private static final String CRASH_NAME = "crash.txt";
    private static Context ctx;
    private static Uri debugUri, crashUri;

    static void init(Context c) {
        ctx = c.getApplicationContext();
        if (Build.VERSION.SDK_INT >= 29) {
            deleteOld(DEBUG_NAME);
            deleteOld(CRASH_NAME);
            debugUri = uriFor(DEBUG_NAME);
            crashUri = uriFor(CRASH_NAME);
        } else {
            File dir = new File("/storage/emulated/0/Download/Zazeper");
            dir.mkdirs();
            new File(dir, DEBUG_NAME).delete();
            new File(dir, CRASH_NAME).delete();
        }
        mark("app start");
        final Thread.UncaughtExceptionHandler old = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                write(CRASH_NAME, crashUri, stack(e));
                if (old != null) old.uncaughtException(t, e);
                else System.exit(1);
            }
        });
    }

    static void mark(String s) {
        write(DEBUG_NAME, debugUri,
                new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date()) + " " + s);
    }

    static void error(String tag, Throwable e) {
        mark(tag + ": " + stack(e));
    }

    private static String stack(Throwable e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private static void deleteOld(String name) {
        try {
            Uri col = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
            String sel = MediaStore.MediaColumns.DISPLAY_NAME + "=? AND "
                    + MediaStore.MediaColumns.RELATIVE_PATH + "=?";
            String[] args = {name, REL + "/"};
            Cursor cur = ctx.getContentResolver().query(col,
                    new String[]{MediaStore.MediaColumns._ID}, sel, args, null);
            if (cur != null) {
                while (cur.moveToNext()) {
                    long id = cur.getLong(0);
                    ctx.getContentResolver().delete(
                            ContentUris.withAppendedId(col, id), null, null);
                }
                cur.close();
            }
        } catch (Throwable ignored) {
        }
    }

    private static Uri uriFor(String name) {
        try {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            cv.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
            cv.put(MediaStore.MediaColumns.RELATIVE_PATH, REL + "/");
            return ctx.getContentResolver().insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void write(String name, Uri uri, String s) {
        try {
            if (Build.VERSION.SDK_INT >= 29 && uri != null) {
                OutputStream os = ctx.getContentResolver().openOutputStream(uri, "wa");
                if (os != null) {
                    os.write((s + "\n").getBytes("UTF-8"));
                    os.close();
                    return;
                }
            }
            File dir = new File("/storage/emulated/0/Download/Zazeper");
            dir.mkdirs();
            FileOutputStream out = new FileOutputStream(new File(dir, name), true);
            out.write((s + "\n").getBytes("UTF-8"));
            out.close();
        } catch (Throwable ignored) {
        }
    }
}
