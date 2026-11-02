package dev.arcdock.app;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class MainActivity extends Activity {
    private static final int REQ_CAMERA=103;
    private WebView webView;
    private boolean torchOn=false;
    private String torchId;

    @Override public void onCreate(Bundle state) {
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
        w.setJavaScriptCanOpenWindowsAutomatically(false);
        w.setSupportMultipleWindows(false);
        w.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){
                return !"file".equalsIgnoreCase(r.getUrl().getScheme());
            }
        });
        webView.addJavascriptInterface(new ArcBridge(),"ArcNative");
        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");
    }
    private void toast(String t){runOnUiThread(()->Toast.makeText(this,t,Toast.LENGTH_SHORT).show());}
    private void safeStart(Intent i){
        try{startActivity(i);}
        catch(ActivityNotFoundException|SecurityException x){toast("没有可用的应用");}
        catch(RuntimeException x){toast("启动失败，请检查设置");}
    }
    private void act(String type,String value){
        switch(type){
            case "camera":
                safeStart(new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA));break;
            case "browser":case "url":{
                String url=value.isEmpty()?"https://www.google.com":value;
                Uri u=Uri.parse(url);
                if(!"https".equalsIgnoreCase(u.getScheme())&&!"http".equalsIgnoreCase(u.getScheme())){
                    toast("仅允许 http 或 https 网页");return;
                }
                Intent in=new Intent(Intent.ACTION_VIEW,u);
                in.addCategory(Intent.CATEGORY_BROWSABLE);
                safeStart(in);break;
            }
            case "dial":{
                String n=value.replaceAll("[^+0-9*#]","");
                safeStart(new Intent(Intent.ACTION_DIAL,Uri.parse("tel:"+Uri.encode(n))));break;
            }
            case "share":{
                Intent in=new Intent(Intent.ACTION_SEND);
                in.setType("text/plain");
                in.putExtra(Intent.EXTRA_TEXT,value.isEmpty()?"来自 ArcDock 的分享":value);
                safeStart(Intent.createChooser(in,"分享到"));break;
            }
            case "app":{
                if(value.isEmpty())return;
                Intent in=getPackageManager().getLaunchIntentForPackage(value);
                if(in==null)toast("应用未安装或不可启动");else safeStart(in);
                break;
            }
            case "torch":torch();break;
            case "settings":safeStart(new Intent(Settings.ACTION_SETTINGS));break;
            default:toast("不支持的操作");
        }
    }
    private void torch(){
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.CAMERA},REQ_CAMERA);return;
        }
        try{
            CameraManager m=(CameraManager)getSystemService(Context.CAMERA_SERVICE);
            if(m==null){toast("不支持手电筒");return;}
            if(torchId==null){
                for(String id:m.getCameraIdList()){
                    CameraCharacteristics c=m.getCameraCharacteristics(id);
                    Boolean has=c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                    Integer face=c.get(CameraCharacteristics.LENS_FACING);
                    if(Boolean.TRUE.equals(has)&&face!=null&&face==CameraCharacteristics.LENS_FACING_BACK){
                        torchId=id;break;
                    }
                }
            }
            if(torchId==null){toast("没有可用闪光灯");return;}
            m.setTorchMode(torchId,!torchOn);
            torchOn=!torchOn;
            toast(torchOn?"手电筒已开启":"手电筒已关闭");
        }catch(CameraAccessException|SecurityException|IllegalArgumentException e){
            toast("手电筒暂时不可用");
        }
    }
    @Override public void onRequestPermissionsResult(int req,String[] permissions,int[] results){
        super.onRequestPermissionsResult(req,permissions,results);
        if(req==REQ_CAMERA){
            if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)torch();
            else toast("需要相机权限控制手电筒");
        }
    }
    @Override public void onBackPressed(){
        webView.evaluateJavascript("window.arcBack ? window.arcBack() : false",r->{
            if("false".equals(r)) MainActivity.super.onBackPressed();
        });
    }
    @Override protected void onDestroy(){
        if(torchOn){
            try{
                CameraManager m=(CameraManager)getSystemService(Context.CAMERA_SERVICE);
                if(m!=null&&torchId!=null)m.setTorchMode(torchId,false);
            }catch(Exception ignore){}
        }
        if(webView!=null){webView.destroy();webView=null;}
        super.onDestroy();
    }
    public final class ArcBridge {
        @JavascriptInterface public void execute(String type,String value){
            String t=type==null?"":type,v=value==null?"":value;
            runOnUiThread(()->act(t,v));
        }
        @JavascriptInterface public void haptic(String style){
            runOnUiThread(()->{
                if("commit".equals(style)){
                    Vibrator vib=(Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
                    if(vib!=null&&vib.hasVibrator()){
                        if(Build.VERSION.SDK_INT>=26)vib.vibrate(VibrationEffect.createOneShot(25,85));
                    }
                }else if(webView!=null){
                    webView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                }
            });
        }
        @JavascriptInterface public String getApps(){
            try{
                Intent i=new Intent(Intent.ACTION_MAIN,null);
                i.addCategory(Intent.CATEGORY_LAUNCHER);
                PackageManager p=getPackageManager();
                List<ResolveInfo> apps=p.queryIntentActivities(i,0);
                List<JSONObject> out=new ArrayList<>();
                for(ResolveInfo r:apps){
                    if(r.activityInfo==null||r.activityInfo.packageName==null)continue;
                    JSONObject a=new JSONObject();
                    a.put("label",String.valueOf(r.loadLabel(p)));
                    a.put("packageName",r.activityInfo.packageName);
                    out.add(a);
                }
                Collections.sort(out,Comparator.comparing(a->a.optString("label","")));
                JSONArray json=new JSONArray();
                for(JSONObject a:out)json.put(a);
                return json.toString();
            }catch(Exception x){return "[]";}
        }
    }
}
