package dev.arcdock.app;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.provider.Settings;
import android.widget.Toast;

public final class ArcActions {
    private ArcActions(){}
    private static boolean torchOn=false;
    private static String torchId=null;
    private static void toast(Context c,String s){
        android.os.Handler h=new android.os.Handler(android.os.Looper.getMainLooper());
        h.post(()->Toast.makeText(c.getApplicationContext(),s,Toast.LENGTH_SHORT).show());
    }
    private static void start(Context c,Intent i){
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try{c.startActivity(i);}
        catch(Exception e){toast(c,"无法打开目标，请检查是否已安装");}
    }
    public static void perform(Context c,String type,String value){
        String t=type==null?"":type, v=value==null?"":value;
        try{
            switch(t){
                case "camera":
                    start(c,new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA));break;
                case "browser": case "url":{
                    String link=v.isEmpty()?"https://www.google.com":v;
                    Uri u=Uri.parse(link);
                    if(!"https".equalsIgnoreCase(u.getScheme())&&!"http".equalsIgnoreCase(u.getScheme())){
                        toast(c,"仅支持 http 或 https 链接");return;
                    }
                    Intent i=new Intent(Intent.ACTION_VIEW,u);
                    i.addCategory(Intent.CATEGORY_BROWSABLE);
                    start(c,i);break;
                }
                case "dial":
                    start(c,new Intent(Intent.ACTION_DIAL,Uri.parse("tel:"+Uri.encode(v.replaceAll("[^+0-9*#]","")))));break;
                case "share":{
                    Intent i=new Intent(Intent.ACTION_SEND);
                    i.setType("text/plain");
                    i.putExtra(Intent.EXTRA_TEXT,v.isEmpty()?"ArcDock · 一划即达":v);
                    start(c,Intent.createChooser(i,"选择分享方式"));break;
                }
                case "app":{
                    if(v.isEmpty())return;
                    Intent i=c.getPackageManager().getLaunchIntentForPackage(v);
                    if(i==null)toast(c,"应用不存在或无法启动");else start(c,i);
                    break;
                }
                case "torch":torch(c);break;
                case "settings":start(c,new Intent(c,MainActivity.class));break;
                case "copy":{
                    ClipboardManager m=(ClipboardManager)c.getSystemService(Context.CLIPBOARD_SERVICE);
                    if(m!=null){m.setPrimaryClip(ClipData.newPlainText("ArcDock",v));toast(c,"已复制");}
                    break;
                }
                default:toast(c,"暂不支持该快捷操作");
            }
        }catch(Exception e){toast(c,"操作失败，请检查权限");}
    }
    private static void torch(Context c){
        if(c.checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){
            toast(c,"请先进入 ArcDock 授予相机权限以使用手电筒");
            start(c,new Intent(c,MainActivity.class).putExtra("askCamera",true));
            return;
        }
        try{
            CameraManager m=(CameraManager)c.getSystemService(Context.CAMERA_SERVICE);
            if(m==null){toast(c,"设备不支持手电筒");return;}
            if(torchId==null){
                for(String id:m.getCameraIdList()){
                    CameraCharacteristics info=m.getCameraCharacteristics(id);
                    Boolean has=info.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                    Integer facing=info.get(CameraCharacteristics.LENS_FACING);
                    if(Boolean.TRUE.equals(has)&&facing!=null&&facing==CameraCharacteristics.LENS_FACING_BACK){torchId=id;break;}
                }
            }
            if(torchId==null){toast(c,"无可用闪光灯");return;}
            m.setTorchMode(torchId,!torchOn);
            torchOn=!torchOn;
            toast(c,torchOn?"手电筒已打开":"手电筒已关闭");
        }catch(Exception e){toast(c,"闪光灯被其他程序占用");}
    }
}
