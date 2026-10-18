package xiao.bu.tv;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;
import org.json.*;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import xiao.bu.tv.radio.RadioUi;

/** Real downloaded DEX, one audio decoder, deterministic glass renders and lifecycle checks. */
public final class GlassRecordInstrumentation extends Instrumentation {
    private volatile MainActivity activity;
    private Bundle args;
    private File out;
    private AudioArtworkView artwork;
    private RadioUi portrait;
    private final JSONArray rows=new JSONArray();
    private final JSONObject metrics=new JSONObject();
    private String base,secure;
    interface Work {void run()throws Exception;}
    interface Condition {boolean get()throws Exception;}
    @Override public void onCreate(Bundle b){super.onCreate(b);args=b;start();}
    @Override public void callActivityOnResume(Activity a){super.callActivityOnResume(a);if(a instanceof MainActivity)activity=(MainActivity)a;}
    private static Object field(Object value,String name)throws Exception {
        Class<?> type=value instanceof Class?(Class<?>)value:value.getClass();
        while(type!=null){try{Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(value instanceof Class?null:value);}catch(NoSuchFieldException e){type=type.getSuperclass();}}
        throw new NoSuchFieldException(name);
    }
    private static void field(Object value,String name,Object next)throws Exception {
        Class<?> type=value instanceof Class?(Class<?>)value:value.getClass();
        while(type!=null){try{Field f=type.getDeclaredField(name);f.setAccessible(true);f.set(value instanceof Class?null:value,next);return;}catch(NoSuchFieldException e){type=type.getSuperclass();}}
        throw new NoSuchFieldException(name);
    }
    private Object call(String name,Class<?>[] types,Object...values)throws Exception {Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(activity,values);}
    private void main(Work work)throws Exception {Throwable[] error={null};runOnMainSync(()->{try{work.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new AssertionError(error[0]);}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private void until(Condition condition,long duration,String failure)throws Exception {long end=SystemClock.elapsedRealtime()+duration;while(!condition.get()&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(35);check(condition.get(),failure);}
    private RadioUi ui()throws Exception{return (RadioUi)field(artwork,"ui");}
    private View motion(RadioUi value)throws Exception{return (View)field(field(value,"recordView"),"motionView");}
    private boolean running(RadioUi value)throws Exception{return (Boolean)field(motion(value),"running");}
    private void row(String name)throws Exception{rows.put(new JSONObject().put("name",name).put("pass",true));}
    private JSONObject stats()throws Exception {
        HttpURLConnection connection=NetworkClient.openBounded(new URL(base+"/stats"),5000);
        try {
            connection.setConnectTimeout(3000);connection.setReadTimeout(3000);connection.setUseCaches(false);
            check(connection.getResponseCode()==200,"Stats endpoint returned HTTP "+connection.getResponseCode());
            check(connection.getContentLength()<=65536,"Stats response exceeded 64 KiB");
            try(InputStream in=connection.getInputStream();ByteArrayOutputStream body=new ByteArrayOutputStream()){
                byte[] buffer=new byte[4096];int length;while((length=in.read(buffer))!=-1){check(body.size()+length<=65536,"Stats response exceeded 64 KiB");body.write(buffer,0,length);}
                return new JSONObject(new String(body.toByteArray(),"UTF-8"));
            }
        }finally{connection.disconnect();}
    }
    private static int requests(JSONObject stats,String path){return stats.optJSONObject("requests").optInt(path,0);}
    private void choose(String id)throws Exception {
        RadioUiPlugin.configure(getTargetContext(),new JSONObject().put("id",id));
        if(artwork!=null)until(()->ui()!=null&&id.equals(new JSONObject(RadioUiPlugin.status(getTargetContext())).getString("selected"))
                &&id.equals(field(artwork,"uiStyle"))&&!ui().isTransitionRunning(),8000,"Style did not apply: "+id);
        waitForIdleSync();
    }
    private Bitmap draw(View view)throws Exception {
        Bitmap[] image={null};main(()->{image[0]=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(image[0]));});return image[0];
    }
    private void save(Bitmap image,String name)throws Exception {try(FileOutputStream file=new FileOutputStream(new File(out,name+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,file);}}
    private void capture(String name)throws Exception {Bitmap image=draw(artwork);try{save(image,name);}finally{image.recycle();}}
    private void shot(String name)throws Exception {Bitmap image=getUiAutomation().takeScreenshot();if(image!=null)try{save(image,name);}finally{image.recycle();}}
    private static long difference(Bitmap a,Bitmap b,Rect area) {
        long changed=0;int left=Math.max(0,area.left),top=Math.max(0,area.top),right=Math.min(a.getWidth(),area.right),bottom=Math.min(a.getHeight(),area.bottom);
        int width=right-left;if(width<=0)return 0;int[] first=new int[width],second=new int[width];
        for(int y=top;y<bottom;y++){a.getPixels(first,0,width,left,y,width,1);b.getPixels(second,0,width,left,y,width,1);for(int x=0;x<width;x++)if(first[x]!=second[x])changed++;}return changed;
    }
    private static long nontransparent(Bitmap image){long count=0;int width=image.getWidth();int[] pixels=new int[width];for(int y=0;y<image.getHeight();y+=2){image.getPixels(pixels,0,width,0,y,width,1);for(int x=0;x<width;x+=2)if(Color.alpha(pixels[x])>10)count++;}return count;}
    private static long heap(){Runtime runtime=Runtime.getRuntime();return runtime.totalMemory()-runtime.freeMemory();}
    private static void restore(SharedPreferences preferences,Map<String,?> values){SharedPreferences.Editor e=preferences.edit().clear();for(Map.Entry<String,?> item:values.entrySet()){
        String key=item.getKey();Object v=item.getValue();if(v instanceof String)e.putString(key,(String)v);else if(v instanceof Integer)e.putInt(key,(Integer)v);else if(v instanceof Long)e.putLong(key,(Long)v);else if(v instanceof Boolean)e.putBoolean(key,(Boolean)v);else if(v instanceof Float)e.putFloat(key,(Float)v);else if(v instanceof Set)e.putStringSet(key,(Set<String>)v);
    }e.commit();}
    private void freeze(RadioUi value,long elapsed)throws Exception {value.setPlaying(false);Object m=motion(value);field(m,"elapsedMs",elapsed);field(m,"lastTick",SystemClock.uptimeMillis());View record=(View)field(value,"recordView");Method rotate=record.getClass().getDeclaredMethod("setDiscRotation",float.class);rotate.setAccessible(true);rotate.invoke(record,elapsed*.018f);}
    private void deterministic(RadioUi value,long elapsed)throws Exception {main(()->{freeze(value,elapsed);motion(value).invalidate();});}
    private Bitmap plane(RadioUi value,boolean foreground,float seconds)throws Exception {
        Object particles=field(value,"particles");Method render=particles.getClass().getDeclaredMethod("draw",Canvas.class,float.class,float.class,RectF.class,float.class,boolean.class);render.setAccessible(true);
        Bitmap[] image={null};main(()->{View view=value.view();image[0]=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);render.invoke(particles,new Canvas(image[0]),(float)view.getWidth(),(float)view.getHeight(),field(value,"disc"),seconds,foreground);});return image[0];
    }
    private void particlePlanes()throws Exception {
        for(boolean foreground:new boolean[]{false,true}){
            Bitmap a=plane(portrait,foreground,0f),b=plane(portrait,foreground,4f),same=plane(portrait,foreground,4f);
            String name=foreground?"front":"back";save(a,"particles-"+name+"-t0000");save(b,"particles-"+name+"-t4000");Rect all=new Rect(0,0,a.getWidth(),a.getHeight());long changed=difference(a,b,all);
            check(changed>100&&nontransparent(a)>100,"Empty/static "+name+" particle plane");check(difference(b,same,all)==0,"Nondeterministic "+name+" particle plane");metrics.put(name+"ParticleChangedPixels",changed).put(name+"ParticleVisibleQuarterSamples",nontransparent(a));
            a.recycle();b.recycle();same.recycle();row("Independent "+name+" particle plane moves with fixed seed and is deterministic");
        }
        particleOcclusion();
    }
    private void particleOcclusion()throws Exception {
        deterministic(portrait,4000);
        Object particles=field(portrait,"particles");float[] opacity=(float[])field(particles,"opacity");float[] saved=opacity.clone();int backCount=(Integer)field(particles.getClass(),"BACK_COUNT");
        check(backCount>0&&backCount<opacity.length,"Invalid particle layer partition");Bitmap none=null,back=null,front=null;
        try {
            main(()->{Arrays.fill(opacity,0f);portrait.view().invalidate();});none=draw(portrait.view());
            main(()->{System.arraycopy(saved,0,opacity,0,backCount);portrait.view().invalidate();});back=draw(portrait.view());
            main(()->{Arrays.fill(opacity,0f);System.arraycopy(saved,backCount,opacity,backCount,opacity.length-backCount);portrait.view().invalidate();});front=draw(portrait.view());
            RectF disc=(RectF)field(portrait,"disc");float scale=1000f/disc.width(),radius=445f/scale;
            int left=Math.max(0,(int)Math.floor(disc.centerX()-radius)),right=Math.min(none.getWidth(),(int)Math.ceil(disc.centerX()+radius));
            int top=Math.max(0,(int)Math.floor(disc.centerY()-radius)),bottom=Math.min(none.getHeight(),(int)Math.ceil(disc.centerY()+radius));
            int width=right-left;check(width>0&&bottom>top,"Glass disc lies outside the portrait render");int[] plainRow=new int[width],backRow=new int[width],frontRow=new int[width];long backOpaque=0,backGlass=0,frontOpaque=0;
            for(int y=top;y<bottom;y++){
                none.getPixels(plainRow,0,width,left,y,width,1);back.getPixels(backRow,0,width,left,y,width,1);front.getPixels(frontRow,0,width,left,y,width,1);
                float dy=(y+.5f-disc.centerY())*scale;
                for(int x=0;x<width;x++){
                    float dx=(left+x+.5f-disc.centerX())*scale,r2=dx*dx+dy*dy;
                    if(r2<330f*330f){if(backRow[x]!=plainRow[x])backOpaque++;if(frontRow[x]!=plainRow[x])frontOpaque++;}
                    else if(r2>350f*350f&&r2<445f*445f&&backRow[x]!=plainRow[x])backGlass++;
                }
            }
            metrics.put("backOpaqueChangedPixels",backOpaque).put("backGlassChangedPixels",backGlass).put("frontOpaqueChangedPixels",frontOpaque);
            check(backOpaque==0,"Back particles leaked through opaque material: "+backOpaque);check(backGlass>10,"Back particles missing through transparent glass: "+backGlass);check(frontOpaque>10,"Front particles missing above opaque material: "+frontOpaque);
            row("Actual scene hides back particles under opaque rings, transmits them through glass and draws front particles above the record");
        }finally{
            main(()->{System.arraycopy(saved,0,opacity,0,opacity.length);portrait.view().invalidate();});
            if(none!=null)none.recycle();if(back!=null)back.recycle();if(front!=null)front.recycle();
        }
    }
    private void lifecycleAndPixels()throws Exception {
        RadioUi optical=ui();until(()->running(optical),3000,"Particles not running during audio playback");
        Bitmap a=draw(artwork);SystemClock.sleep(550);Bitmap b=draw(artwork);save(a,"landscape-playing-0");save(b,"landscape-playing-1");
        RectF disc=(RectF)field(optical,"disc");Rect upper=new Rect(0,0,a.getWidth(),Math.max(1,Math.round(disc.centerY()))),lower=new Rect(0,Math.round(disc.centerY()),a.getWidth(),a.getHeight());
        long upperChanged=difference(a,b,upper),lowerChanged=difference(a,b,lower);check(upperChanged>10&&lowerChanged>10,"Upper/lower glass scene did not animate");
        metrics.put("landscapeUpperChangedPixels",upperChanged).put("landscapeLowerChangedPixels",lowerChanged);a.recycle();b.recycle();row("Upper and lower glass scene changes with playback");
        IMediaPlayer audio=(IMediaPlayer)field(activity,"player");main(()->{audio.pause();artwork.setPlaying(false);});until(()->!running(optical),1500,"Pause left particle clock running");SystemClock.sleep(80);
        a=draw(artwork);SystemClock.sleep(240);b=draw(artwork);check(difference(a,b,new Rect(0,0,a.getWidth(),a.getHeight()))==0,"Paused glass changed pixels");save(a,"landscape-paused");a.recycle();b.recycle();row("Paused artwork and both particle regions remain pixel stable");
        main(()->{audio.start();artwork.setPlaying(true);artwork.setActive(false);});until(()->!running(optical),1500,"Inactive/background artwork clock kept running");
        long time=(Long)field(motion(optical),"elapsedMs");SystemClock.sleep(250);check((Long)field(motion(optical),"elapsedMs")==time,"Inactive clock advanced");row("Host inactive/background callback stops the shared particle clock");
        main(()->{artwork.setActive(true);artwork.setVisibility(View.GONE);});until(()->!running(optical),1500,"Hidden artwork clock kept running");SystemClock.sleep(100);time=(Long)field(motion(optical),"elapsedMs");SystemClock.sleep(200);check((Long)field(motion(optical),"elapsedMs")==time,"Hidden clock advanced");row("Hidden artwork clock stops");
        main(()->artwork.setVisibility(View.VISIBLE));until(()->running(optical),1500,"Visible artwork did not resume");row("Visible artwork resumes shared animation");
    }
    private void transitions(IMediaPlayer audio,Bitmap cover)throws Exception {
        final RadioUi optical=ui();main(()->{artwork.setPlaying(false);artwork.previewSlide(-60,true,"neighbor",cover);});check(optical.isTransitionRunning(),"Preview not marked running");
        ImageView neighbor=(ImageView)field(optical,"neighborView");check(neighbor.getVisibility()==View.VISIBLE&&neighbor.getDrawable() instanceof android.graphics.drawable.BitmapDrawable,"Missing neighbor glass snapshot");
        Bitmap image=((android.graphics.drawable.BitmapDrawable)neighbor.getDrawable()).getBitmap();check(nontransparent(image)>1000,"Neighbor snapshot empty");save(image,"neighbor-snapshot");capture("gesture-preview");
        RadioUiPlugin.configure(getTargetContext(),new JSONObject().put("id","classic"));check(ui()==optical,"Plugin replaced during gesture");
        main(()->artwork.restoreSlide());until(()->!optical.isTransitionRunning()&&ui()!=optical,3000,"Deferred classic selection not applied");check(field(activity,"player")==audio&&audio.isPlaying(),"Gesture style switch replaced audio");row("Gesture defers plugin replacement and retains audio");
        choose("optical");final RadioUi ready=ui();main(()->{artwork.setPlaying(false);artwork.beginChannelSwitch(1);});
        ImageView outgoing=(ImageView)field(ready,"outgoingView");check(outgoing.getVisibility()==View.VISIBLE&&outgoing.getDrawable() instanceof android.graphics.drawable.BitmapDrawable,"Missing outgoing glass snapshot");
        image=((android.graphics.drawable.BitmapDrawable)outgoing.getDrawable()).getBitmap();check(nontransparent(image)>1000,"Outgoing snapshot empty");save(image,"outgoing-snapshot");capture("handoff-waiting");
        main(()->artwork.showPending("玻璃唱片加载预览","pending",cover));main(()->artwork.show("玻璃唱片测试",true,cover));until(()->!ready.isTransitionRunning(),3000,"Pending channel handoff stuck");
        main(()->artwork.setPlaying(true));check(field(activity,"player")==audio&&audio.isPlaying(),"Snapshot handoff replaced audio decoder");row("Outgoing and neighbor snapshots survive pending handoff and restore");
    }
    private void portraitAndCost(Bitmap cover)throws Exception {
        RadioUiPlugin.Factory factory=(RadioUiPlugin.Factory)field(artwork,"factory");main(()->{
            portrait=factory.create(activity);portrait.show("玻璃唱片",false,cover);portrait.setPlaying(false);
            View child=portrait.view();((FrameLayout)activity.getWindow().getDecorView()).addView(child,new FrameLayout.LayoutParams(1080,2400,Gravity.TOP|Gravity.LEFT));
            child.measure(View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2400,View.MeasureSpec.EXACTLY));child.layout(0,0,1080,2400);
        });
        Bitmap a=null,b=null;for(long phase:new long[]{0,4000,8000,12000,16000,19000}){deterministic(portrait,phase);b=draw(portrait.view());save(b,String.format(Locale.US,"portrait-1080x2400-t%05d",phase));check(b.getWidth()==1080&&b.getHeight()==2400,"Portrait dimensions changed");if(a!=null){check(difference(a,b,new Rect(0,0,1080,2400))>100,"Portrait rendering did not change at "+phase);a.recycle();}a=b;}
        a.recycle();row("Real plugin renders reference portrait dimensions at 0,4,8,12,16,19 seconds");
        deterministic(portrait,4000);a=draw(portrait.view());b=draw(portrait.view());check(difference(a,b,new Rect(0,0,1080,2400))==0,"Fixed phase render is nondeterministic");a.recycle();b.recycle();row("Fixed phase render is deterministic");particlePlanes();
        final long[] cpu=new long[24],wall=new long[24],memory=new long[4];final Bitmap target=Bitmap.createBitmap(1080,2400,Bitmap.Config.ARGB_8888);final Canvas canvas=new Canvas(target);
        main(()->{for(int i=0;i<3;i++)portrait.view().draw(canvas);memory[0]=heap();memory[1]=Debug.getNativeHeapAllocatedSize();for(int i=0;i<cpu.length;i++){long c=Debug.threadCpuTimeNanos(),w=System.nanoTime();portrait.view().draw(canvas);wall[i]=System.nanoTime()-w;cpu[i]=Debug.threadCpuTimeNanos()-c;}memory[2]=heap();memory[3]=Debug.getNativeHeapAllocatedSize();});
        int targetBytes=target.getAllocationByteCount();target.recycle();Arrays.sort(cpu);Arrays.sort(wall);metrics.put("softwarePortraitDrawSamples",cpu.length).put("softwarePortraitDrawCpuP50Ms",cpu[12]/1000000d).put("softwarePortraitDrawCpuP95Ms",cpu[22]/1000000d).put("softwarePortraitDrawWallP95Ms",wall[22]/1000000d).put("javaHeapDeltaDrawBytes",memory[2]-memory[0]).put("nativeHeapDeltaDrawBytes",memory[3]-memory[1]).put("softwareDrawTargetBytes",targetBytes).put("javaHeapAfterTestBitmapRecycleBytes",heap()).put("nativeHeapAfterTestBitmapRecycleBytes",Debug.getNativeHeapAllocatedSize());
        check(cpu[22]<1000000000L,"Portrait draw consumes over one CPU second");row("Bounded software draw cost measured without per-frame bitmap creation");
        RadioUi detached=portrait;main(()->{((FrameLayout)activity.getWindow().getDecorView()).removeView(portrait.view());portrait.setPlaying(true);});check(!running(detached),"Detached particle clock running");main(()->{portrait.dispose();portrait=null;});row("Detached plugin does not schedule animation");
    }
    private void animationCost()throws Exception {
        RadioUi value=ui();final ArrayList<Long> intervals=new ArrayList<>();final boolean[] measuring={true};final long[] previous={0};
        Choreographer.FrameCallback callback=new Choreographer.FrameCallback(){public void doFrame(long nanos){if(!measuring[0])return;if(previous[0]!=0)intervals.add(nanos-previous[0]);previous[0]=nanos;Choreographer.getInstance().postFrameCallback(this);}};
        main(()->{artwork.setPlaying(true);Choreographer.getInstance().postFrameCallback(callback);});long cpu=android.os.Process.getElapsedCpuTime(),start=SystemClock.elapsedRealtime();SystemClock.sleep(6000);long used=android.os.Process.getElapsedCpuTime()-cpu,elapsed=SystemClock.elapsedRealtime()-start;
        main(()->{measuring[0]=false;Choreographer.getInstance().removeFrameCallback(callback);});Collections.sort(intervals);check(intervals.size()>30,"Not enough animation timing samples");
        int jank=0;for(Long i:intervals)if(i>26000000L)jank++;metrics.put("animationSampleMs",elapsed).put("animationProcessCpuMs",used).put("animationCpuOneCorePercent",100d*used/elapsed).put("animationVsyncSamples",intervals.size()).put("animationVsyncP50Ms",intervals.get(intervals.size()/2)/1000000d).put("animationVsyncP95Ms",intervals.get(intervals.size()*95/100)/1000000d).put("animationIntervalsOver26ms",jank);
        check(running(value),"Animation stopped during sustained sample");row("Sustained real window animation CPU and frame pacing sampled");
    }
    @Override public void onStart(){Bundle result=new Bundle();int code=1;Context c=getTargetContext();SharedPreferences prefs=c.getSharedPreferences("radio_ui",0),settings=c.getSharedPreferences(MainActivity.PREFERENCES,0);Map<String,?> oldPrefs=new HashMap<>(prefs.getAll()),oldSettings=new HashMap<>(settings.getAll());ChannelCatalog.Group[] oldGroups=ChannelCatalog.GROUPS;
        try{
            base=args.getString("base","http://127.0.0.1:39489");secure=args.getString("secure","https://127.0.0.1:39488");out=new File(c.getExternalFilesDir(null),"glass-record");out.mkdirs();
            settings.edit().putBoolean("auto_update_channel_list",false).putBoolean(GithubProxy.ENABLED_PREFERENCE,false).commit();NetworkClient.initialize(c);installTls();
            String catalog=new String(RadioUiCatalog.download(secure+"/catalog.json",65536,8000),"UTF-8");List<RadioUiCatalog.Entry> entries=RadioUiCatalog.parse(catalog);field(RadioUiCatalog.class,"entries",Collections.unmodifiableList(entries));
            prefs.edit().clear().putString("style","classic").putString("selected","classic").putString("catalog",catalog).putLong("catalog_checked_at",System.currentTimeMillis()).commit();
            field(RadioUiPlugin.class,"active",null);field(RadioUiPlugin.class,"requested",false);field(RadioUiPlugin.class,"retryAfter",0L);((Map<?,?>)field(RadioUiPlugin.class,"factories")).clear();
            for(RadioUiCatalog.Entry e:entries){File jar=new File(new File(c.getFilesDir(),"radio-ui"),e.sha+".jar");if(jar.isFile()){jar.setWritable(true);check(jar.delete(),"Unable to clear test plugin cache");}}
            c.startActivity(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));until(()->activity!=null,15000,"No Activity");SystemClock.sleep(1800);
            main(()->{Channel radio=new Channel("1","玻璃唱片测试","",base+"/audio.mp3",null,null);ChannelCatalog.GROUPS=new ChannelCatalog.Group[]{new ChannelCatalog.Group("唱片",ChannelCatalog.SOURCE_CUSTOM,new Channel[]{radio})};for(String n:new String[]{"currentGroupIndex","currentChannelIndex","currentSourceIndex"})field(activity,n,0);call("startChannel",new Class[]{int.class},0);});
            until(()->(Boolean)field(activity,"prepared")&&(Boolean)field(activity,"audioOnlyPlayback"),20000,"Audio fixture did not prepare");artwork=(AudioArtworkView)field(activity,"audioArtwork");until(()->ui()!=null,15000,"Classic first-use download did not show");IMediaPlayer audio=(IMediaPlayer)field(activity,"player");long position=audio.getCurrentPosition();
            Bitmap cover=BitmapFactory.decodeFile(args.getString("cover",""));if(cover==null){cover=Bitmap.createBitmap(128,128,Bitmap.Config.ARGB_8888);cover.eraseColor(0xffc4b3bf);}final Bitmap fixedCover=cover;
            main(()->{((AlbumArtLoader)field(activity,"albumArtLoader")).clear();artwork.setCover(fixedCover);for(String name:new String[]{"channelBar","debugInfoOverlay","networkSpeedOverlay","backPrompt"})((View)field(activity,name)).setVisibility(View.GONE);});
            capture("landscape-classic");JSONObject before=stats();int audioRequests=requests(before,"/audio.mp3");choose("optical");check(ui().getClass().getName().equals("xiao.bu.radio.RadioArtwork"),"Not real DEX renderer");check(new JSONObject(RadioUiPlugin.status(c)).getInt("version")>=5,"Loaded old optical JAR revision");check(field(activity,"player")==audio&&audio.isPlaying()&&artwork.cover()==cover,"Selection changed audio or cover");row("Fresh download loads new optical DEX without replacing the audio player");
            portraitAndCost(cover);
            if(!"true".equals(args.getString("renderOnly"))){SystemClock.sleep(500);lifecycleAndPixels();transitions(audio,cover);check(requests(stats(),"/audio.mp3")==audioRequests,"Style/lifecycle/gesture rendering opened another audio network request");row("Style changes and particle lifecycle do not add audio requests");animationCost();}
            shot("landscape-window");JSONObject counts=stats();check(requests(counts,"/classic.jar")==1&&requests(counts,"/optical.jar")==1,"Plugin cache did not reuse downloaded JARs: "+counts);check(field(activity,"player")==audio&&audio.isPlaying()&&audio.getCurrentPosition()>position,"Audio player changed or stopped");row("Repeated selections reuse verified downloads and retain advancing audio");
            metrics.put("originStats",counts).put("javaHeapBytes",heap()).put("nativeHeapBytes",Debug.getNativeHeapAllocatedSize()).put("checks",rows.length()).put("renderOnly","true".equals(args.getString("renderOnly")));
            int demoSeconds=Math.max(0,Math.min(20,Integer.parseInt(args.getString("demoSeconds","0"))));
            if(demoSeconds>0&&!"true".equals(args.getString("renderOnly"))){
                main(()->{artwork.setActive(true);artwork.setVisibility(View.VISIBLE);artwork.setPlaying(true);for(String name:new String[]{"channelBar","debugInfoOverlay","networkSpeedOverlay","backPrompt"})((View)field(activity,name)).setVisibility(View.GONE);});
                until(()->running(ui()),1500,"Demo animation did not resume");waitForIdleSync();
                Bundle ready=new Bundle();ready.putString("stream","GLASS_DEMO_READY\n");sendStatus(1,ready);SystemClock.sleep(demoSeconds*1000L);
            }
            result.putString("stream","true".equals(args.getString("renderOnly"))?"PASS: glass record; render-only portrait phases, particle planes, deterministic draw and retained audio\n":"PASS: glass record; downloaded DEX, retained audio, particle animation/pause/inactive/hidden/detach, snapshots, portrait phases and performance\n");code=-1;
        }catch(Throwable e){result.putString("stream",android.util.Log.getStackTraceString(e));}
        finally{
            try{if(out!=null){try(FileOutputStream f=new FileOutputStream(new File(out,"results.json"))){f.write(new JSONObject().put("checks",rows).put("metrics",metrics).toString(2).getBytes("UTF-8"));}}}catch(Exception ignored){}
            try{if(activity!=null)main(()->{if(portrait!=null){((FrameLayout)activity.getWindow().getDecorView()).removeView(portrait.view());portrait.dispose();portrait=null;}if(artwork!=null)artwork.setActive(false);activity.finish();});}catch(Exception ignored){}
            ChannelCatalog.GROUPS=oldGroups;restore(prefs,oldPrefs);restore(settings,oldSettings);
        }finish(code,result);
    }
    private void installTls()throws Exception {
        java.security.cert.Certificate certificate;try(InputStream in=new FileInputStream(args.getString("cert"))){certificate=java.security.cert.CertificateFactory.getInstance("X.509").generateCertificate(in);}
        java.security.KeyStore store=java.security.KeyStore.getInstance(java.security.KeyStore.getDefaultType());store.load(null);store.setCertificateEntry("fixture",certificate);javax.net.ssl.TrustManagerFactory managers=javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());managers.init(store);javax.net.ssl.X509TrustManager trust=(javax.net.ssl.X509TrustManager)managers.getTrustManagers()[0];javax.net.ssl.SSLContext tls=javax.net.ssl.SSLContext.getInstance("TLS");tls.init(null,new javax.net.ssl.TrustManager[]{trust},null);
        okhttp3.OkHttpClient client=NetworkClient.sharedClient().newBuilder().sslSocketFactory(tls.getSocketFactory(),trust).build();field(NetworkClient.class,"client",client);field(NetworkClient.class,"factory",new okhttp3.OkUrlFactory(client));GithubProxy.setEnabled(false);
    }
}
