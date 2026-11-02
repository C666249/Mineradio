package dev.arcdock.app;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i) {
        if(i==null||!Intent.ACTION_BOOT_COMPLETED.equals(i.getAction()))return;
        if(!c.getSharedPreferences("arcdock",Context.MODE_PRIVATE).getBoolean("overlay_enabled",false))return;
        if(!Settings.canDrawOverlays(c))return;
        try{
            Intent s=new Intent(c,OverlayService.class);
            if(Build.VERSION.SDK_INT>=26)c.startForegroundService(s);else c.startService(s);
        }catch(Exception ignore){}
    }
}