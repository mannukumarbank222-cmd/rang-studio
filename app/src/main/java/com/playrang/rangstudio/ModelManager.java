package com.playrang.rangstudio;

import android.content.Context;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Downloads the small offline Hindi Vosk model (~44 MB) once, into the app's private files dir. */
final class ModelManager {
    private ModelManager() {}

    static final String NAME = "vosk-model-small-hi-0.22";
    static final String URL_ZIP = "https://alphacephei.com/vosk/models/" + NAME + ".zip";

    interface Progress { void onProgress(int percent, String msg); }

    static File dir(Context c) { return new File(c.getFilesDir(), NAME); }

    static boolean ready(Context c) {
        File d = dir(c);
        return new File(d, "am/final.mdl").exists() && new File(d, "conf/model.conf").exists() && new File(d, ".complete").exists();
    }

    private static volatile boolean running;
    static boolean isDownloading() { return running; }

    /** Blocking; call off the main thread. @return null on success, otherwise a Hindi error message */
    static synchronized String download(Context ctx, Progress p) {
        if (ready(ctx)) return null;
        running = true;
        File tmp = new File(ctx.getCacheDir(), NAME + ".zip.part");
        File out = new File(ctx.getFilesDir(), NAME + ".tmp");
        try {
            long have = tmp.exists() ? tmp.length() : 0;
            HttpURLConnection c = (HttpURLConnection) new URL(URL_ZIP).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setRequestProperty("User-Agent", "Mitra-Android/2.2");
            if (have > 0) c.setRequestProperty("Range", "bytes=" + have + "-");
            int code = c.getResponseCode();
            if (code == 200) have = 0; else if (code != 206) return "मॉडल डाउनलोड नहीं हुआ (कोड " + code + ")।";
            long total = have + Math.max(0, c.getContentLength());
            InputStream in = new BufferedInputStream(c.getInputStream());
            OutputStream os = new FileOutputStream(tmp, have > 0);
            byte[] buf = new byte[64 * 1024];
            long done = have;
            int n, lastPct = -1;
            while ((n = in.read(buf)) > 0) {
                os.write(buf, 0, n);
                done += n;
                int pct = total > 0 ? (int) (done * 90 / total) : 0;
                if (pct != lastPct && p != null) { lastPct = pct; p.onProgress(pct, "डाउनलोड हो रहा है… " + (done >> 20) + " / " + (total >> 20) + " MB"); }
            }
            os.close();
            in.close();
            c.disconnect();
            if (p != null) p.onProgress(92, "मॉडल खोल रहा हूँ…");
            deleteAll(out);
            out.mkdirs();
            ZipInputStream zin = new ZipInputStream(new BufferedInputStream(new java.io.FileInputStream(tmp)));
            ZipEntry e;
            String prefix = NAME + "/";
            String canon = out.getCanonicalPath() + File.separator;
            while ((e = zin.getNextEntry()) != null) {
                String name = e.getName();
                if (name.startsWith(prefix)) name = name.substring(prefix.length());
                if (name.isEmpty()) continue;
                File f = new File(out, name);
                if (!f.getCanonicalPath().startsWith(canon)) continue; // zip-slip guard
                if (e.isDirectory()) { f.mkdirs(); continue; }
                f.getParentFile().mkdirs();
                OutputStream fo = new FileOutputStream(f);
                while ((n = zin.read(buf)) > 0) fo.write(buf, 0, n);
                fo.close();
            }
            zin.close();
            if (!new File(out, "am/final.mdl").exists()) { deleteAll(out); tmp.delete(); return "मॉडल फ़ाइल अधूरी है, फिर से कोशिश करें।"; }
            new File(out, ".complete").createNewFile();
            File d = dir(ctx);
            deleteAll(d);
            if (!out.renameTo(d)) return "मॉडल सेव नहीं हो पाया।";
            tmp.delete();
            if (p != null) p.onProgress(100, "मॉडल तैयार ✅");
            return null;
        } catch (Exception ex) {
            return "इंटरनेट जाँचिए — मॉडल डाउनलोड रुक गया (" + ex.getClass().getSimpleName() + ")। दोबारा दबाने पर आगे से शुरू होगा।";
        } finally {
            running = false;
        }
    }

    static void deleteAll(File f) {
        if (f == null || !f.exists()) return;
        File[] k = f.listFiles();
        if (k != null) for (File x : k) deleteAll(x);
        f.delete();
    }
}
