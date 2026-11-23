package com.playrang.rangstudio;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * After a reboot: Android 11+ does not let a microphone service start from the background, so we post a
 * tap-to-start notification (on older Android the service starts directly).
 */
public class BootReceiver extends BroadcastReceiver {
    static final int NOTIF_BOOT = 403;

    @Override
    public void onReceive(Context c, Intent intent) {
        String a = intent == null ? null : intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(a) && !"android.intent.action.QUICKBOOT_POWERON".equals(a)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) return;
        if (!WakeService.enabled(c) || !WakeService.hasMic(c)) return;
        if (Build.VERSION.SDK_INT < 30) { WakeService.start(c); return; }
        WakeService.channels(c);
        int fl = Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT : PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent pi = PendingIntent.getActivity(c, 5, new Intent(c, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(MainActivity.EXTRA_START_WAKE, true), fl);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, WakeService.CH_WAKE) : new Notification.Builder(c);
        b.setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("मित्र को फिर से चालू करें")
                .setContentText("फ़ोन रीस्टार्ट हुआ है — 'हमेशा सुनें' चालू करने के लिए यहाँ टैप करें")
                .setAutoCancel(true).setContentIntent(pi);
        try { ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(NOTIF_BOOT, b.build()); } catch (Exception ignored) { }
    }
}
