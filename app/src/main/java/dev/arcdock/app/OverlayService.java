package dev.arcdock.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.animation.ValueAnimator;
import org.json.JSONArray;
import org.json.JSONObject;

public final class OverlayService extends Service {
    public static final String ACTION_STOP="dev.arcdock.app.STOP";
    private static final String CHANNEL="arcdock_overlay";
    private static OverlayService live;
    public static boolean isRunning(){return live!=null;}
    public static OverlayService current(){return live;}
    private WindowManager wm;
    private HandleView handle;
    private DockView dock;
    private WindowManager.LayoutParams handleLp;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private Runnable dismissTask;
    private boolean right=true;
    private boolean haptics=true;
    private int accent=Color.rgb(228,183,124);
    private int glass=62;
    private final String[] labels={"相机","浏览器","拨号","分享","设置"};
    private final String[] types={"camera","browser","dial","share","settings"};
    private final String[] values={"","https://www.google.com","","",""};
    private float anchorY=0;
    private int selection=-1;
    private float progress=0f;
    private int screenW, screenH;
    private ValueAnimator animator;

    @Override public void onCreate(){
        super.onCreate();
        live=this;
        wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        loadConfig();
        try{
            beginForeground();
            if(!Settings.canDrawOverlays(this)){stopSelf();return;}
            initHandle();
        }catch(Exception e){
            android.widget.Toast.makeText(this,"ArcDock 悬浮服务无法启动，请检查悬浮窗权限",android.widget.Toast.LENGTH_LONG).show();
            stopSelf();
        }
    }
    private SharedPreferences prefs(){return getSharedPreferences("arcdock",MODE_PRIVATE);}
    private int d(float n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    private void loadConfig(){
        String raw=prefs().getString("configuration","");
        try{
            JSONObject o=new JSONObject(raw);
            right=!"left".equals(o.optString("hand","right"));
            haptics=o.optBoolean("haptics",true);
            glass=o.optInt("glass",62);
            switch(o.optString("theme","amber")){
                case "ice": accent=Color.rgb(145,215,213);break;
                case "orchid":accent=Color.rgb(195,169,236);break;
                default:accent=Color.rgb(228,183,124);
            }
            JSONArray a=o.optJSONArray("slots");
            if(a!=null){
                for(int i=0;i<Math.min(5,a.length());i++){
                    JSONObject s=a.optJSONObject(i);
                    if(s==null)continue;
                    labels[i]=s.optString("label",labels[i]);
                    types[i]=s.optString("type",types[i]);
                    values[i]=s.optString("value",values[i]);
                }
            }
        }catch(Exception ignore){}
    }
    public void refresh(){
        if(Looper.myLooper()!=Looper.getMainLooper()){handler.post(this::refresh);return;}
        loadConfig();
        if(handle!=null&&handleLp!=null){
            handleLp.gravity=Gravity.TOP|(right?Gravity.RIGHT:Gravity.LEFT);
            try{wm.updateViewLayout(handle,handleLp);}catch(Exception ignore){}
            handle.invalidate();
        }
        if(dock!=null)dock.invalidate();
    }
    private void beginForeground(){
        NotificationManager n=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel c=new NotificationChannel(CHANNEL,"ArcDock 全局控制环",NotificationManager.IMPORTANCE_LOW);
            c.setDescription("悬浮控制环持续运行时显示");
            n.createNotificationChannel(c);
        }
        PendingIntent launch=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Intent stopIntent=new Intent(this,OverlayService.class).setAction(ACTION_STOP);
        PendingIntent stop=PendingIntent.getService(this,1,stopIntent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.ic_menu_manage)
          .setContentTitle("ArcDock 控制环已开启")
          .setContentText("从屏幕边缘滑入 · 点击进入设置")
          .setOngoing(true).setContentIntent(launch)
          .addAction(android.R.drawable.ic_menu_close_clear_cancel,"关闭控制环",stop);
        startForeground(517,b.build());
    }
    @Override public int onStartCommand(Intent i,int flags,int startId){
        if(i!=null&&ACTION_STOP.equals(i.getAction())){
            prefs().edit().putBoolean("overlay_enabled",false).apply();
            stopSelf();
            return START_NOT_STICKY;
        }
        if(!Settings.canDrawOverlays(this)){stopSelf();return START_NOT_STICKY;}
        if(handle==null)try{initHandle();}catch(Exception ignore){}
        return START_STICKY;
    }
    private void measure(){
        android.graphics.Point p=new android.graphics.Point();
        wm.getDefaultDisplay().getRealSize(p);
        screenW=p.x;screenH=p.y;
    }
    private void initHandle(){
        if(handle!=null)return;
        measure();
        handle=new HandleView(this);
        handleLp=new WindowManager.LayoutParams(d(30),d(104),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        handleLp.gravity=Gravity.TOP|(right?Gravity.RIGHT:Gravity.LEFT);
        anchorY=prefs().getInt("handleY",screenH/2);
        anchorY=Math.max(d(145),Math.min(screenH-d(145),anchorY));
        handleLp.y=(int)anchorY-d(52);
        wm.addView(handle,handleLp);
    }
    private void moveHandle(float y){
        if(handle==null)return;
        anchorY=Math.max(d(145),Math.min(screenH-d(145),y));
        handleLp.y=(int)anchorY-d(52);
        try{wm.updateViewLayout(handle,handleLp);}catch(Exception ignore){}
        prefs().edit().putInt("handleY",(int)anchorY).apply();
        if(dock!=null)dock.invalidate();
    }
    private void showDock(float y){
        if(dock!=null)return;
        measure();
        anchorY=Math.max(d(210),Math.min(screenH-d(210),y));
        selection=-1;
        progress=0f;
        dock=new DockView(this);
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.TOP|Gravity.LEFT;
        try{wm.addView(dock,p);}catch(Exception e){dock=null;return;}
        animate(true);
        buzz(false);
        scheduleClose();
    }
    private void animate(boolean open){
        if(animator!=null)animator.cancel();
        animator=ValueAnimator.ofFloat(progress,open?1f:0f);
        animator.setDuration(open?290:145);
        animator.setInterpolator(new DecelerateInterpolator(1.7f));
        animator.addUpdateListener(a->{progress=(float)a.getAnimatedValue();if(dock!=null)dock.invalidate();});
        animator.start();
    }
    private void closeDock(){
        if(dock==null)return;
        cancelClose();
        final DockView old=dock;
        dock=null;
        selection=-1;
        if(animator!=null)animator.cancel();
        handler.post(()->{try{wm.removeView(old);}catch(Exception ignore){}});
    }
    private void cancelClose(){if(dismissTask!=null){handler.removeCallbacks(dismissTask);dismissTask=null;}}
    private void scheduleClose(){
        cancelClose();
        dismissTask=this::closeDock;
        handler.postDelayed(dismissTask,11000);
    }
    private float cx(){return right?screenW+d(8):-d(8);}
    private float radius(){return Math.min(d(158),screenW*.42f);}
    private float[] point(int index){
        double angle=(right?(140+index*20):(-40+index*20))*Math.PI/180d;
        return new float[]{cx()+(float)(Math.cos(angle)*radius()),anchorY+(float)(Math.sin(angle)*radius())};
    }
    private int hit(float x,float y){
        int best=-1;float dist=Float.MAX_VALUE;
        for(int i=0;i<5;i++){
            float[] pt=point(i);
            float dd=(float)Math.hypot(x-pt[0],y-pt[1]);
            if(dd<dist){dist=dd;best=i;}
        }
        return dist<=d(56)?best:-1;
    }
    private void choose(float x,float y){
        int i=hit(x,y);
        if(i!=selection){selection=i;if(i>=0)buzz(false);if(dock!=null)dock.invalidate();}
        scheduleClose();
    }
    private void runSelected(int i){
        if(i<0||i>=5){closeDock();return;}
        String type=types[i],value=values[i];
        closeDock();
        buzz(true);
        handler.postDelayed(()->ArcActions.perform(this,type,value),70);
    }
    private void buzz(boolean strong){
        if(!haptics)return;
        try{
            Vibrator v=(Vibrator)getSystemService(VIBRATOR_SERVICE);
            if(v!=null&&v.hasVibrator()&&Build.VERSION.SDK_INT>=26)
                v.vibrate(VibrationEffect.createOneShot(strong?27:12,strong?100:52));
        }catch(Exception ignore){}
    }
    @Override public void onDestroy(){
        cancelClose();
        if(animator!=null)animator.cancel();
        closeDock();
        if(handle!=null){try{wm.removeView(handle);}catch(Exception ignore){}handle=null;}
        live=null;
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent i){return null;}

    private final class HandleView extends View {
        private final Paint p=new Paint(3);
        private float startX,startY;
        private boolean swiping=false,moving=false;
        HandleView(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);
            float w=getWidth(),h=getHeight();
            float left=right?w*.28f:0f;
            float rightX=right?w:w*.72f;
            RectF shell=new RectF(left,d(9),rightX,h-d(9));
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(0,0,w,h,new int[]{0xD9464A50,0xD916191E,0xDB29241F},null,Shader.TileMode.CLAMP));
            p.setShadowLayer(d(12),0,d(2),0x66000000);
            c.drawRoundRect(shell,d(17),d(17),p);
            p.clearShadowLayer();p.setShader(null);
            p.setColor(accent);p.setAlpha(150);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(d(1));
            c.drawRoundRect(shell,d(17),d(17),p);
            p.setStyle(Paint.Style.FILL);p.setAlpha(210);
            c.drawCircle((left+rightX)/2,h/2,d(3.5f),p);
            p.setAlpha(120);p.setStrokeWidth(d(1.5f));p.setStyle(Paint.Style.STROKE);
            for(int i=-1;i<=1;i++){float y=h/2+i*d(15);c.drawLine((left+rightX)/2-d(3),y,(left+rightX)/2+d(3),y,p);}
            p.setShader(null);p.setAlpha(255);p.setStyle(Paint.Style.FILL);
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    startX=e.getRawX();startY=e.getRawY();swiping=false;moving=false;
                    return true;
                case MotionEvent.ACTION_MOVE:{
                    float dx=e.getRawX()-startX,dy=e.getRawY()-startY;
                    if(!swiping&&!moving&&Math.abs(dx)>d(18)&&(right?dx<0:dx>0)){
                        swiping=true;showDock(startY);
                    }
                    if(!swiping&&Math.abs(dy)>d(12)){moving=true;moveHandle(e.getRawY());}
                    if(swiping)choose(e.getRawX(),e.getRawY());
                    return true;
                }
                case MotionEvent.ACTION_UP:
                    if(swiping){
                        if(selection>=0)runSelected(selection);
                        else if(dock!=null)scheduleClose();
                    }else if(!moving){
                        if(dock!=null)closeDock();else showDock(e.getRawY());
                    }
                    swiping=false;moving=false;
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    swiping=false;moving=false;return true;
            }
            return true;
        }
    }

    private final class DockView extends View {
        private final Paint p=new Paint(3);
        DockView(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        private void fill(Canvas c,int color){p.reset();p.setAntiAlias(true);p.setColor(color);p.setStyle(Paint.Style.FILL);c.drawColor(color);}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);
            p.reset();p.setAntiAlias(true);
            p.setColor(0xB2080A0E);p.setAlpha((int)(190*progress));
            c.drawRect(0,0,getWidth(),getHeight(),p);
            float rad=radius();
            p.setStyle(Paint.Style.STROKE);p.setColor(accent);p.setStrokeWidth(d(1.1f));
            p.setAlpha((int)(90*progress));
            RectF orbit=new RectF(cx()-rad,anchorY-rad,cx()+rad,anchorY+rad);
            c.drawArc(orbit,right?138:-42,84,false,p);
            p.setStyle(Paint.Style.FILL);
            p.setShader(new RadialGradient(cx(),anchorY,rad+d(70),new int[]{0x45E4B77C,0x130A0E14,0x00000000},null,Shader.TileMode.CLAMP));
            c.drawCircle(cx(),anchorY,rad+d(70),p);p.setShader(null);
            for(int i=0;i<5;i++){
                float[] pt=point(i);
                float x=cx()+(pt[0]-cx())*(0.55f+0.45f*progress);
                float y=anchorY+(pt[1]-anchorY)*(0.55f+0.45f*progress);
                float R=d(i==selection?36:32)*Math.max(.1f,progress);
                drawNode(c,x,y,R,i);
            }
            p.reset();p.setAntiAlias(true);p.setColor(0xFFAFA7A0);p.setTextSize(d(11));
            p.setTextAlign(Paint.Align.CENTER);p.setAlpha((int)(220*progress));
            c.drawText("沿弧形选择  ·  轻触执行",getWidth()/2f,Math.min(getHeight()-d(55),anchorY+rad+d(105)),p);
        }
        private void drawNode(Canvas c,float x,float y,float r,int i){
            if(r<1)return;
            int color=(selection==i)?accent:0xFFE9E5DF;
            p.reset();p.setAntiAlias(true);p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(x-r,y-r,x+r,y+r,
                new int[]{selection==i?0xE0745C3F:0xD0454850,0xD614171C,0xDD25201D},null,Shader.TileMode.CLAMP));
            p.setShadowLayer(d(13),0,d(4),0x88000000);
            c.drawCircle(x,y,r,p);p.clearShadowLayer();p.setShader(null);
            p.setStyle(Paint.Style.STROKE);p.setColor(selection==i?accent:0x88E7D0B2);
            p.setStrokeWidth(d(selection==i?1.8f:1));p.setAlpha((int)(progress*220));
            c.drawCircle(x,y,r,p);
            p.setStyle(Paint.Style.FILL);p.setColor(color);p.setTextAlign(Paint.Align.CENTER);
            String kind=types[i];
            p.setTextSize(d(24));p.setTypeface(android.graphics.Typeface.DEFAULT);
            String mark;
            switch(kind){
                case "camera":mark="◎";break;
                case "browser":case "url":mark="◉";break;
                case "dial":mark="☎";break;
                case "share":mark="↗";break;
                case "settings":mark="⚙";break;
                case "app":mark="▦";break;
                case "torch":mark="✦";break;
                case "copy":mark="▤";break;
                default:mark="●";
            }
            c.drawText(mark,x,y+d(8),p);
            p.setTextSize(d(10));p.setColor(0xFFE9E0D5);
            String label=labels[i];
            if(label.length()>6)label=label.substring(0,6);
            c.drawText(label,x,y+r+d(17),p);
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN:choose(e.getX(),e.getY());return true;
                case MotionEvent.ACTION_MOVE:choose(e.getX(),e.getY());return true;
                case MotionEvent.ACTION_UP:{
                    int index=hit(e.getX(),e.getY());
                    if(index>=0)runSelected(index);else closeDock();
                    return true;
                }
                case MotionEvent.ACTION_CANCEL:closeDock();return true;
            }
            return true;
        }
    }
}
