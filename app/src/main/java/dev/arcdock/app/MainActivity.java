package dev.arcdock.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class MainActivity extends Activity {
    private WebView webView;
    private static final int REQ_CAMERA=201;
    private boolean openedOverlaySettings=false;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(0xff0b0d10);
        getWindow().setNavigationBarColor(0xff0b0d10);
        webView=new WebView(this);
        webView.setBackgroundColor(0xff0b0d10);
        WebSettings w=webView.getSettings();
        w.setJavaScriptEnabled(true);
        w.setDomStorageEnabled(true);
        w.setAllowFileAccess(true);
        w.setAllowContentAccess(false);
        w.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        w.setJavaScriptCanOpenWindowsAutomatically(false);
        webView.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){
                return !"file".equalsIgnoreCase(r.getUrl().getScheme());
            }
        });
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.addJavascriptInterface(new Bridge(),"ArcNative");
        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");
        if(getIntent().getBooleanExtra("askCamera",false))askCamera();
        if(!prefs().getBoolean("onboarding",false)){
            prefs().edit().putBoolean("onboarding",true).apply();
            new AlertDialog.Builder(this)
                .setTitle("开启全局指尖控制环")
                .setMessage("ArcDock 可在其他 App 上显示一条细小的边缘把手。滑入后选择快捷项，松手即可执行。需要你手动授予悬浮窗权限；随时可以在 ArcDock 中关闭。")
                .setPositiveButton("开启全局控制环",(d,w)->enableOverlay())
                .setNegativeButton("暂时只在应用内使用",(d,w)->{}).show();
        }
    }
    private android.content.SharedPreferences prefs(){return getSharedPreferences("arcdock",MODE_PRIVATE);}
    private void askCamera(){
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.CAMERA},REQ_CAMERA);
    }
    private void enableOverlay(){
        prefs().edit().putBoolean("overlay_enabled",true).apply();
        if(Build.VERSION.SDK_INT>=23&&!Settings.canDrawOverlays(this)){
            openedOverlaySettings=true;
            Toast.makeText(this,"请允许 ArcDock 显示在其他应用上层",Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));
            return;
        }
        startOverlay();
    }
    private void startOverlay(){
        try{
            Intent intent=new Intent(this,OverlayService.class);
            if(Build.VERSION.SDK_INT>=26)startForegroundService(intent);
            else startService(intent);
        }catch(Exception e){Toast.makeText(this,"悬浮服务启动失败，请检查系统权限",Toast.LENGTH_LONG).show();}
        syncUi();
    }
    private void disableOverlay(){
        prefs().edit().putBoolean("overlay_enabled",false).apply();
        stopService(new Intent(this,OverlayService.class));
        syncUi();
    }
    private void syncUi(){
        if(webView!=null)webView.post(()->webView.evaluateJavascript("window.syncOverlayUI&&window.syncOverlayUI()",null));
    }
    @Override protected void onResume(){
        super.onResume();
        if(prefs().getBoolean("overlay_enabled",false)&&(Build.VERSION.SDK_INT<23||Settings.canDrawOverlays(this))){
            if(!OverlayService.isRunning())startOverlay();
        }
        openedOverlaySettings=false;
        syncUi();
    }
    @Override public void onRequestPermissionsResult(int code,String[] perms,int[] res){
        super.onRequestPermissionsResult(code,perms,res);
        if(code==REQ_CAMERA)Toast.makeText(this,(res.length>0&&res[0]==PackageManager.PERMISSION_GRANTED)?"相机权限已授予":"未授予相机权限，手电筒不可用",Toast.LENGTH_SHORT).show();
    }
    @Override public void onBackPressed(){
        if(webView==null){super.onBackPressed();return;}
        webView.evaluateJavascript("window.arcBack?window.arcBack():false",r->{
            if("false".equals(r))MainActivity.super.onBackPressed();
        });
    }
    @Override protected void onDestroy(){
        if(webView!=null){webView.destroy();webView=null;}
        super.onDestroy();
    }
    public final class Bridge {
        @android.webkit.JavascriptInterface public void execute(String type,String value){
            runOnUiThread(()->{
                if("torch".equals(type))askCamera();
                ArcActions.perform(MainActivity.this,type,value);
            });
        }
        @android.webkit.JavascriptInterface public void haptic(String kind){
            runOnUiThread(()->{
                if("commit".equals(kind)){
                    Vibrator v=(Vibrator)getSystemService(VIBRATOR_SERVICE);
                    if(v!=null&&v.hasVibrator()&&Build.VERSION.SDK_INT>=26)
                        v.vibrate(VibrationEffect.createOneShot(24,80));
                }else if(webView!=null)webView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            });
        }
        @android.webkit.JavascriptInterface public void enableOverlay(){runOnUiThread(()->MainActivity.this.enableOverlay());}
        @android.webkit.JavascriptInterface public void disableOverlay(){runOnUiThread(()->MainActivity.this.disableOverlay());}
        @android.webkit.JavascriptInterface public String overlayStatus(){
            try{
                JSONObject j=new JSONObject();
                j.put("permission",Build.VERSION.SDK_INT<23||Settings.canDrawOverlays(MainActivity.this));
                j.put("enabled",prefs().getBoolean("overlay_enabled",false));
                j.put("running",OverlayService.isRunning());
                return j.toString();
            }catch(Exception e){return "{}";}
        }
        @android.webkit.JavascriptInterface public void saveConfiguration(String json){
            if(json==null||json.length()>25000)return;
            try{
                JSONObject obj=new JSONObject(json);
                JSONArray slots=obj.optJSONArray("slots");
                if(slots==null||slots.length()!=5)return;
                prefs().edit().putString("configuration",json).apply();
                runOnUiThread(()->{
                    OverlayService service=OverlayService.current();
                    if(service!=null)service.refresh();
                });
            }catch(Exception ignore){}
        }
        @android.webkit.JavascriptInterface public String getConfiguration(){
            return prefs().getString("configuration","");
        }
        @android.webkit.JavascriptInterface public String getApps(){
            try{
                Intent q=new Intent(Intent.ACTION_MAIN,null).addCategory(Intent.CATEGORY_LAUNCHER);
                PackageManager pm=getPackageManager();
                List<ResolveInfo> a=pm.queryIntentActivities(q,0);
                List<JSONObject> out=new ArrayList<>();
                for(ResolveInfo info:a){
                    if(info.activityInfo==null||info.activityInfo.packageName==null)continue;
                    JSONObject item=new JSONObject();
                    item.put("label",String.valueOf(info.loadLabel(pm)));
                    item.put("packageName",info.activityInfo.packageName);
                    out.add(item);
                }
                out.sort(Comparator.comparing(o->o.optString("label","")));
                JSONArray arr=new JSONArray();
                for(JSONObject item:out)arr.put(item);
                return arr.toString();
            }catch(Exception e){return "[]";}
        }
    }
}
