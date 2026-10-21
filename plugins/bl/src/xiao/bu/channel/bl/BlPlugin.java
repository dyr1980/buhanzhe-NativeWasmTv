package xiao.bu.channel.bl;

import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.text.*;
import org.json.*;
import okhttp3.OkHttpClient;
import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import xiao.bu.tv.jar.ChannelPlugin;
import java.util.*;
import java.util.concurrent.*;

/** BBLL-inspired native single-View Bilibili channel. No Activity, Fragment or WebView. */
public final class BlPlugin implements ChannelPlugin,ChannelPlugin.NativePage {
    private interface Job{Object run()throws Exception;}
    private interface Result{void show(Object value)throws Exception;}
    private static final class Route{
        String kind,title,arg;long playbackCid,playbackEpisode;int playbackPart;int page=1,selection,scroll;boolean more=true;JSONArray cached=new JSONArray();JSONObject data;
        Route(String kind,String title,String arg){this.kind=kind;this.title=title;this.arg=arg;}
    }
    private static final class Option{String title;Runnable action;Option(String title,Runnable action){this.title=title;this.action=action;}}
    private static final class Panel{String title;List<Option> rows;Panel parent;int selection;Panel(String title,List<Option> rows,Panel parent){this.title=title;this.rows=rows;this.parent=parent;}}
    private Panel panel,openingPanel;
    private BlLiveDanmaku liveDanmaku;private boolean liveLegacy,progressiveFallback;
    private Context context;private Host host;private BlUi ui;private BlApi api;private BlImages images;private BlMediaProxy proxy;
    private Root root;private LinearLayout sidebar;private FrameLayout content,modal;private GridView grid;private TextView status;
    private CardAdapter adapter;private final List<BlItem> items=new ArrayList<>();
    private final Map<String,Button> tabs=new LinkedHashMap<>();
    private final Deque<Route> history=new ArrayDeque<>();private Route route;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor jobs=new ThreadPoolExecutor(2,2,20,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(16),new ThreadPoolExecutor.DiscardOldestPolicy());
    private int generation,modalGeneration;private boolean closed,loading,statusError,active=true;private String selectedTab="home";
    private BlPlayer player;private BlDanmakuFeed dmFeed;private long dmCid;private final List<BlPlayer> retired=new ArrayList<>();private JSONObject video,playData;private long cid,episode;private int playbackGeneration;private int partIndex;private View modalFocus;
    private final Runnable clock=new Runnable(){public void run(){if(!closed&&active){if(status!=null&&!loading&&!statusError)status.setText(android.text.format.DateFormat.format("HH:mm",System.currentTimeMillis()));main.postDelayed(this,60000);}}};
    /** Build the entire native channel inside one root View using the host transport. */
    public View create(Context context,ClassLoader resources,JSONObject config,Host host)throws Exception{
        if(!(host instanceof ChannelPlugin.NativeHost))throw new java.io.IOException("BL 需要更新支持原生插件的宿主 APK");
        Object client=((ChannelPlugin.NativeHost)host).httpClient();if(!(client instanceof OkHttpClient))throw new java.io.IOException("宿主共享 OkHttp 不可用");
        // A fixed, separate namespace lets device instrumentation leave real accounts/history intact.
        String preferences=config.optBoolean("testPreferences",false)?"ntv-bl-instrumentation":"ntv-bl";
        this.context=context;this.host=host;ui=new BlUi(context);api=new BlApi((OkHttpClient)client,context.getSharedPreferences(preferences,Context.MODE_PRIVATE),config);images=new BlImages(api,resources);proxy=new BlMediaProxy(api);
        root=new Root(context);root.setBackgroundColor(BlUi.BG);root.setContentDescription("BL频道");
        ImageView background=new ImageView(context);background.setScaleType(ImageView.ScaleType.CENTER_CROP);background.setImageBitmap(images.asset("background.png"));background.setAlpha(0.20f);root.addView(background,new FrameLayout.LayoutParams(-1,-1));
        sidebar=ui.vertical();sidebar.setPadding(ui.px(10),ui.px(14),ui.px(10),ui.px(12));sidebar.setBackgroundColor(0x77303030);
        TextView brand=ui.title("BL");brand.setTextColor(BlUi.ACCENT);brand.setGravity(Gravity.CENTER);sidebar.addView(brand,new LinearLayout.LayoutParams(-1,ui.px(54)));
        tab("search","搜索","icon_search");tab("home","推荐","icon_home");tab("popular","热门","icon_favorite");tab("region","分区","icon_list");tab("season","番剧","icon_play");tab("live","直播","icon_play");tab("dynamic","动态","icon_user");tab("mine","我的","icon_user");tab("settings","设置","icon_setting");
        root.addView(sidebar,new FrameLayout.LayoutParams(ui.px(100),-1,Gravity.LEFT));content=new FrameLayout(context);FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-1,-1);cp.leftMargin=ui.px(108);root.addView(content,cp);
        route=new Route("home","推荐","");render();return root;
    }
    private void tab(final String key,String title,String icon){
        Button b=ui.button(title);b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,ui.px(16));b.setPadding(ui.px(2),ui.px(4),ui.px(2),ui.px(4));b.setContentDescription(title);
        Bitmap bitmap=images.asset(icon+".png");if(bitmap!=null){android.graphics.drawable.BitmapDrawable drawable=new android.graphics.drawable.BitmapDrawable(context.getResources(),bitmap);drawable.setBounds(0,0,ui.px(25),ui.px(25));b.setCompoundDrawables(null,drawable,null,null);b.setCompoundDrawablePadding(ui.px(4));}
        sidebar.addView(b,new LinearLayout.LayoutParams(-1,0,1));tabs.put(key,b);
        b.setOnClickListener(new View.OnClickListener(){public void onClick(View v){selectedTab=key;history.clear();navigate(new Route(key,tabTitle(key),""),false);}});
        b.setOnKeyListener(new View.OnKeyListener(){public boolean onKey(View v,int code,KeyEvent event){if(code==KeyEvent.KEYCODE_DPAD_RIGHT){if(event.getAction()==KeyEvent.ACTION_DOWN&&grid!=null&&adapter!=null&&adapter.getCount()>0){grid.requestFocusFromTouch();grid.setSelection(0);}return true;}return false;}});
    }
    private String tabTitle(String key){String[] names={"search","搜索","home","推荐","popular","热门","region","分区","season","番剧","live","直播","dynamic","动态","mine","我的","settings","设置"};for(int i=0;i<names.length;i+=2)if(names[i].equals(key))return names[i+1];return key;}
    private void navigate(Route next,boolean push){
        saveRoute();closePlayer();hideModal();if(push&&route!=null){history.push(route);while(history.size()>12)history.removeLast();}route=next;render();
    }
    private void saveRoute(){if(route!=null&&grid!=null){route.selection=Math.max(0,grid.getSelectedItemPosition());route.scroll=grid.getFirstVisiblePosition();}}
    private void render(){
        generation++;loading=false;statusError=false;main.removeCallbacks(clock);content.removeAllViews();grid=null;adapter=null;items.clear();
        for(Map.Entry<String,Button> t:tabs.entrySet())t.getValue().setSelected(t.getKey().equals(selectedTab));
        boolean full=route.kind.equals("player");sidebar.setVisibility(full?View.GONE:View.VISIBLE);FrameLayout.LayoutParams cp=(FrameLayout.LayoutParams)content.getLayoutParams();cp.leftMargin=full?0:ui.px(108);content.setLayoutParams(cp);
        if(full){renderPlayer();return;}
        final LinearLayout page=ui.vertical();page.setPadding(ui.px(20),ui.px(18),ui.px(20),ui.px(12));content.addView(page,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout header=ui.horizontal();header.addView(ui.title(route.title),new LinearLayout.LayoutParams(0,ui.px(48),1));status=ui.text("",15,BlUi.SUB);status.setMaxWidth(ui.px(430));status.setMaxLines(2);status.setEllipsize(android.text.TextUtils.TruncateAt.END);header.addView(status);ui.gap(header,18);Button diagnostics=ui.button("错误详情");diagnostics.setOnClickListener(new View.OnClickListener(){public void onClick(View v){showDiagnostics();}});header.addView(diagnostics);ui.gap(header,8);Button refresh=ui.button("刷新");refresh.setOnClickListener(new View.OnClickListener(){public void onClick(View v){route.cached=new JSONArray();route.page=1;route.more=true;render();}});header.addView(refresh);page.addView(header);
        main.post(clock);
        if(route.kind.equals("search")){renderSearch(page);return;}
        if(route.kind.equals("mine")){renderMine(page);return;}
        if(route.kind.equals("settings")){renderSettings(page);return;}
        if(route.kind.equals("region")){renderRegions(page);return;}
        if(route.kind.equals("detail")){renderDetail(page);return;}
        if(route.kind.equals("seasonDetail")){renderSeason(page);return;}
        if(route.kind.equals("login")){renderLogin(page);return;}
        makeGrid(page);if(route.cached.length()>0)bind(route.cached,false);else loadPage(false);
    }
    private void makeGrid(LinearLayout page){
        grid=new GridView(context);grid.setNumColumns(4);grid.setHorizontalSpacing(ui.px(8));grid.setVerticalSpacing(ui.px(8));grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);grid.setColumnWidth(ui.px(240));grid.setClipToPadding(false);grid.setPadding(0,ui.px(18),0,ui.px(14));
        android.graphics.drawable.GradientDrawable selector=ui.fill(0x11bb86fc,10);selector.setStroke(ui.px(2),BlUi.ACCENT);grid.setSelector(selector);grid.setDrawSelectorOnTop(true);
        adapter=new CardAdapter();grid.setAdapter(adapter);page.addView(grid,new LinearLayout.LayoutParams(-1,0,1));
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener(){public void onItemClick(AdapterView<?> p,View v,int position,long id){openItem(items.get(position));}});
        grid.setOnKeyListener(new View.OnKeyListener(){public boolean onKey(View v,int code,KeyEvent event){if(code==KeyEvent.KEYCODE_DPAD_LEFT&&Math.max(0,grid.getSelectedItemPosition())%4==0){if(event.getAction()==KeyEvent.ACTION_DOWN){Button tab=tabs.get(selectedTab);if(tab!=null)tab.requestFocus();}return true;}return false;}});
        grid.setOnScrollListener(new AbsListView.OnScrollListener(){public void onScrollStateChanged(AbsListView v,int state){if(state==SCROLL_STATE_IDLE&&route.more&&adapter.getCount()>0&&v.getLastVisiblePosition()>=adapter.getCount()-4)loadPage(true);}public void onScroll(AbsListView v,int first,int visible,int count){if(count>0&&first>0&&first+visible>=count-4&&route.more&&!loading)loadPage(true);}});
    }
    private final class CardAdapter extends BaseAdapter{
        public int getCount(){return items.size();}public Object getItem(int p){return items.get(p);}public long getItemId(int p){return p;}
        public View getView(int p,View reuse,ViewGroup parent){BlCard card=reuse instanceof BlCard?(BlCard)reuse:new BlCard(ui);card.bind(items.get(p),images);return card;}
    }
    private String expected(){String k=route.kind;return k.equals("favorites")?"folder":k.equals("following")?"user":k.equals("season")?"season":k.equals("dynamic")?"dynamic":k.equals("results")?route.data.optString("type","video"):k.equals("live")?"live":"video";}
    private void bind(JSONArray data,boolean append){
        if(!append)items.clear();String type=expected();Set<String> ids=new HashSet<>();for(BlItem i:items)ids.add(i.kind+":"+i.id);
        for(int i=0;i<data.length()&&items.size()<144;i++){JSONObject v=data.optJSONObject(i);if(v==null)continue;BlItem item=new BlItem(v,type);if(!item.id.isEmpty()&&!item.id.equals("0")&&!item.title.trim().isEmpty()&&ids.add(item.kind+":"+item.id))items.add(item);}
        adapter.notifyDataSetChanged();if(!append){grid.setSelection(Math.min(route.selection,Math.max(0,items.size()-1)));grid.requestFocusFromTouch();}
        if(items.isEmpty())message("暂无内容，登录后可查看账号内容",false);else status.setText(items.size()+" 项");
        if(items.size()>=144)route.more=false;
    }
    private void loadPage(final boolean more){
        if(loading||closed||!route.more)return;final int page=more?route.page+1:1;final Route target=route;final int token=generation;loading=true;status.setText("正在加载…");
        submit(new Job(){public Object run()throws Exception{
            String k=target.kind;if(k.equals("home"))try{return api.home(page);}catch(Exception error){return api.popular(page);}
            if(k.equals("popular"))return api.popular(page);if(k.equals("live"))return api.live(page);
            if(k.equals("season"))return api.seasons(page);if(k.equals("history"))return api.history(page);if(k.equals("later"))return api.later();
            if(k.equals("favorites"))return api.favoriteFolders();if(k.equals("favoriteItems"))return api.favorites(Long.parseLong(target.arg),page);
            if(k.equals("following"))return api.followed(page);if(k.equals("uploads"))return api.uploads(Long.parseLong(target.arg),page);
            if(k.equals("regionItems"))return api.region(Integer.parseInt(target.arg),page);
            if(k.equals("results"))return api.search(target.arg,target.data.optString("type","video"),page);
            if(k.equals("dynamic")){String offset="";if(target.cached.length()>0){JSONObject last=target.cached.optJSONObject(target.cached.length()-1);if(last!=null)offset=last.optString("id_str");}return api.dynamic(offset);}
            return new JSONArray();
        }},token,new Result(){public void show(Object value)throws Exception{
            loading=false;JSONArray data=(JSONArray)value;if(!more)target.cached=new JSONArray();for(int i=0;i<data.length()&&target.cached.length()<144;i++)target.cached.put(data.get(i));target.page=page;target.more=data.length()>=(target.kind.equals("regionItems")?15:20)&&!target.kind.equals("favorites")&&!target.kind.equals("later")&&!(target.kind.equals("history")&&!api.signedIn());bind(data,more);
        }});
    }
    private void openItem(BlItem item){
        if(item.kind.equals("folder"))navigate(new Route("favoriteItems",item.title,item.id),true);
        else if(item.kind.equals("user"))navigate(new Route("uploads",item.title,item.id),true);
        else if(item.kind.equals("season"))navigate(new Route("seasonDetail",item.title,item.id),true);
        else if(item.kind.equals("live")){video=item.raw;episode=0;cid=Long.parseLong(item.id);Route r=new Route("player",item.title,item.id);r.data=item.raw;try{r.data.put("bl_live",true);}catch(Exception ignored){}navigate(r,true);}
        else navigate(new Route("detail",item.title,item.id),true);
    }
    private void renderSearch(LinearLayout page){
        final EditText input=new EditText(context);input.setTextColor(BlUi.TEXT);input.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,ui.px(21));input.setSingleLine();input.setHint("输入关键词、BV 号或视频链接");input.setHintTextColor(0xffaaaaaa);input.setPadding(ui.px(14),0,ui.px(14),0);input.setBackgroundDrawable(ui.focus(0x55303030));input.setText(route.arg);
        LinearLayout line=ui.horizontal();line.setPadding(0,ui.px(18),0,ui.px(12));line.addView(input,new LinearLayout.LayoutParams(0,ui.px(52),1));ui.gap(line,10);Button search=ui.button("搜索");line.addView(search);page.addView(line);
        final Runnable run=new Runnable(){public void run(){String q=input.getText().toString().trim();if(q.isEmpty())return;java.util.regex.Matcher m=java.util.regex.Pattern.compile("BV[0-9A-Za-z]{10}").matcher(q);if(m.find()){navigate(new Route("detail",m.group(),m.group()),true);return;}showSearch(q,"video");}};
        search.setOnClickListener(new View.OnClickListener(){public void onClick(View v){run.run();}});input.setOnEditorActionListener(new TextView.OnEditorActionListener(){public boolean onEditorAction(TextView v,int id,KeyEvent e){run.run();return true;}});
        LinearLayout filters=ui.horizontal();for(final String[] pair:new String[][]{{"视频","video"},{"番剧","media_bangumi"},{"直播","live_room"},{"UP主","bili_user"}}){Button b=ui.button(pair[0]);filters.addView(b);ui.gap(filters,10);b.setOnClickListener(new View.OnClickListener(){public void onClick(View v){String q=input.getText().toString().trim();if(!q.isEmpty())showSearch(q,pair[1]);}});}page.addView(filters);
        TextView help=ui.text("支持遥控器字母键盘、手机/蓝牙输入；关键词可通过系统输入法输入中文",16,BlUi.SUB);help.setPadding(0,ui.px(20),0,ui.px(16));page.addView(help);
        GridView keyboard=new GridView(context);keyboard.setNumColumns(10);final String chars="ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";keyboard.setAdapter(new BaseAdapter(){public int getCount(){return chars.length()+2;}public Object getItem(int p){return p<chars.length()?""+chars.charAt(p):p==chars.length()?"删除":"空格";}public long getItemId(int p){return p;}public View getView(int p,View view,ViewGroup parent){TextView t=ui.text((String)getItem(p),23,BlUi.TEXT);t.setGravity(Gravity.CENTER);t.setPadding(0,ui.px(16),0,ui.px(16));t.setBackgroundDrawable(ui.focus(0x44222222));return t;}});keyboard.setOnItemClickListener(new AdapterView.OnItemClickListener(){public void onItemClick(AdapterView<?> p,View v,int position,long id){String value=input.getText().toString();if(position<chars.length())input.setText(value+chars.charAt(position));else if(position==chars.length())input.setText(value.isEmpty()?"":value.substring(0,value.length()-1));else input.setText(value+" ");input.setSelection(input.length());}});page.addView(keyboard,new LinearLayout.LayoutParams(-1,0,1));keyboard.requestFocusFromTouch();keyboard.setSelection(0);
    }
    private void showSearch(String query,String type){Route next=new Route("results","搜索 · "+query,query);next.data=new JSONObject();try{next.data.put("type",type);}catch(Exception ignored){}navigate(next,true);}
    private void renderRegions(LinearLayout page){List<Option> options=new ArrayList<>();for(final String[] p:new String[][]{{"动画","1005"},{"音乐","1003"},{"游戏","1008"},{"娱乐","1002"},{"知识","1010"},{"生活","1031"},{"科技","1012"},{"影视","1001"}})options.add(new Option(p[0],new Runnable(){public void run(){navigate(new Route("regionItems",p[0],p[1]),true);}}));page.addView(optionList(options),new LinearLayout.LayoutParams(-1,0,1));}
    private void renderMine(LinearLayout page){
        String name="未登录";try{name=new JSONObject(api.prefs.getString("user","{}")).optString("uname",name);}catch(Exception ignored){}TextView profile=ui.title(name);profile.setPadding(0,ui.px(24),0,ui.px(20));page.addView(profile);
        List<Option> options=new ArrayList<>();for(final String[] p:new String[][]{{"历史记录","history"},{"我的收藏","favorites"},{"稍后再看","later"},{"我的关注","following"},{"扫码登录","login"}})options.add(new Option(p[0],new Runnable(){public void run(){navigate(new Route(p[1],p[0],""),true);}}));
        if(api.signedIn())options.add(new Option("退出登录",new Runnable(){public void run(){List<Option> confirm=new ArrayList<>();confirm.add(new Option("确认退出登录",new Runnable(){public void run(){api.logout();render();}}));options("账号",confirm);}}));page.addView(optionList(options),new LinearLayout.LayoutParams(-1,0,1));
        if(api.signedIn()){final int token=generation;submit(new Job(){public Object run()throws Exception{return api.profile();}},token,new Result(){public void show(Object value){}});}
    }
    private void renderSettings(LinearLayout page){
        List<Option> list=new ArrayList<>();final boolean hardware=api.prefs.getBoolean("hardware",true),auto=api.prefs.getBoolean("autoplay",true);
        list.add(new Option("错误详情与诊断",new Runnable(){public void run(){showDiagnostics();}}));
        list.add(new Option("硬件解码："+(hardware?"开":"关")+"（Android 4.0 使用系统播放器）",new Runnable(){public void run(){api.prefs.edit().putBoolean("hardware",!hardware).apply();render();}}));
        list.add(new Option("自动播放下一分 P："+(auto?"开":"关"),new Runnable(){public void run(){api.prefs.edit().putBoolean("autoplay",!auto).apply();render();}}));
        list.add(new Option("DASH 音视频分离播放："+(api.prefs.getBoolean("prefer_dash",true)?"开":"关")+"（Android 4.1+）",new Runnable(){public void run(){api.prefs.edit().putBoolean("prefer_dash",!api.prefs.getBoolean("prefer_dash",true)).apply();render();}}));
        list.add(new Option("默认画质："+qualityName(api.prefs.getInt("quality",64)),new Runnable(){public void run(){List<Option> values=new ArrayList<>();for(final int q:new int[]{16,32,64,80})values.add(new Option(qualityName(q),new Runnable(){public void run(){api.prefs.edit().putInt("quality",q).apply();render();}}));options("默认画质",values);}}));
        list.add(new Option("播放器："+(api.prefs.getBoolean("systemPlayer",false)?"系统播放器":"IJK"),new Runnable(){public void run(){api.prefs.edit().putBoolean("systemPlayer",!api.prefs.getBoolean("systemPlayer",false)).apply();render();}}));
        list.add(new Option("清空本地观看历史",new Runnable(){public void run(){api.prefs.edit().remove("history").apply();message("本地历史已清空",false);}}));
        list.add(new Option("BL JAR · API 14 / Android 4.0+ · BBLL 1.5.2 UI · newBV 接口",new Runnable(){public void run(){message("网络复用宿主 OkHttp；原生单 View；无独立 Activity",false);}}));page.addView(optionList(list),new LinearLayout.LayoutParams(-1,0,1));
    }
    private void renderDetail(final LinearLayout page){
        if(route.data!=null){detailViews(page,route.data);return;}final Route target=route;final int token=generation;status.setText("正在加载详情…");submit(new Job(){public Object run()throws Exception{return api.detail(target.arg);}},token,new Result(){public void show(Object value){target.data=(JSONObject)value;detailViews(page,target.data);}});
    }
    private void detailViews(final LinearLayout page,final JSONObject data){
        status.setText(BlUi.count(data.optJSONObject("stat")==null?0:data.optJSONObject("stat").optLong("view"))+" 播放");
        LinearLayout summary=ui.horizontal();summary.setPadding(0,ui.px(18),0,ui.px(14));ImageView cover=new ImageView(context);cover.setScaleType(ImageView.ScaleType.CENTER_CROP);images.load(cover,data.optString("pic"),true);summary.addView(cover,new LinearLayout.LayoutParams(ui.px(300),ui.px(169)));ui.gap(summary,24);
        LinearLayout text=ui.vertical();TextView title=ui.title(data.optString("title"));title.setMaxLines(2);text.addView(title);JSONObject owner=data.optJSONObject("owner");TextView author=ui.text("UP · "+(owner==null?"":owner.optString("name")),18,BlUi.ACCENT);author.setPadding(0,ui.px(14),0,ui.px(12));text.addView(author);TextView description=ui.text(data.optString("desc"),16,BlUi.SUB);description.setMaxLines(3);description.setEllipsize(TextUtils.TruncateAt.END);text.addView(description);summary.addView(text,new LinearLayout.LayoutParams(0,-2,1));page.addView(summary);
        HorizontalScrollView actions=new HorizontalScrollView(context);actions.setHorizontalScrollBarEnabled(false);LinearLayout row=ui.horizontal();actions.addView(row);final JSONArray parts=BlApi.array(data,"pages");
        Button play=ui.button("播放");row.addView(play);play.setOnClickListener(new View.OnClickListener(){public void onClick(View v){startVideo(data,0,0);}});
        action(row,"选集 ("+parts.length()+")",new Runnable(){public void run(){episodeOptions(data,0);}});
        action(row,"评论",new Runnable(){public void run(){comments(data.optLong("aid"),1);}});
        action(row,"收藏",new Runnable(){public void run(){favorite(data);}});
        action(row,"稍后再看",new Runnable(){public void run(){mutation("/x/v2/history/toview/add",BlApi.map("aid",""+data.optLong("aid")));}});
        action(row,"点赞",new Runnable(){public void run(){mutation("/x/web-interface/archive/like",BlApi.map("aid",""+data.optLong("aid"),"like","1"));}});
        action(row,"投币",new Runnable(){public void run(){List<Option> choices=new ArrayList<>();for(final int n:new int[]{1,2})choices.add(new Option("确认投 "+n+" 枚硬币",new Runnable(){public void run(){mutation("/x/web-interface/coin/add",BlApi.map("aid",""+data.optLong("aid"),"multiply",""+n,"select_like","0"));}}));options("投币",choices);}});
        if(owner!=null){final JSONObject up=owner;action(row,"UP 主",new Runnable(){public void run(){navigate(new Route("uploads",up.optString("name"),up.optString("mid")),true);}});}page.addView(actions);
        TextView related=ui.text("相关推荐",20,BlUi.TEXT);related.setPadding(0,ui.px(16),0,0);page.addView(related);makeGrid(page);final int token=generation;
        submit(new Job(){public Object run()throws Exception{return api.related(data.optString("bvid"));}},token,new Result(){public void show(Object value){bind((JSONArray)value,false);route.more=false;play.requestFocusFromTouch();}});
        play.requestFocusFromTouch();
    }
    private void action(LinearLayout row,String text,final Runnable run){ui.gap(row,8);Button b=ui.button(text);b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,ui.px(16));b.setPadding(ui.px(10),ui.px(8),ui.px(10),ui.px(8));row.addView(b);b.setOnClickListener(new View.OnClickListener(){public void onClick(View v){run.run();}});}
    private void renderSeason(final LinearLayout page){
        final Route target=route;final int token=generation;submit(new Job(){public Object run()throws Exception{return api.season(Long.parseLong(target.arg));}},token,new Result(){public void show(Object value)throws Exception{JSONObject season=(JSONObject)value;target.data=season;List<Option> values=new ArrayList<>();JSONArray episodes=BlApi.array(season,"episodes");for(int i=0;i<episodes.length();i++){final JSONObject ep=episodes.getJSONObject(i);final JSONObject data=new JSONObject().put("bvid",ep.optString("bvid")).put("aid",ep.optLong("aid")).put("title",season.optString("title")+" · "+ep.optString("long_title",ep.optString("title"))).put("pic",ep.optString("cover")).put("pages",new JSONArray().put(new JSONObject().put("cid",ep.optLong("cid")).put("part",ep.optString("long_title",ep.optString("title")))));values.add(new Option(ep.optString("title")+"  "+ep.optString("long_title"),new Runnable(){public void run(){startVideo(data,0,ep.optLong("id"));}}));}page.addView(optionList(values),new LinearLayout.LayoutParams(-1,0,1));status.setText(episodes.length()+" 集");}});
    }
    private void startVideo(JSONObject data,int index,long episodeId){video=data;episode=episodeId;partIndex=index;JSONObject part=BlApi.array(data,"pages").optJSONObject(index);cid=part==null?data.optLong("cid"):part.optLong("cid");if(cid<=0){message("视频分 P 信息缺失",true);return;}Route next=new Route("player",data.optString("title"),data.optString("bvid"));next.data=data;next.playbackCid=cid;next.playbackEpisode=episodeId;next.playbackPart=index;navigate(next,true);}
    private void renderPlayer(){
        liveLegacy=false;progressiveFallback=false;playData=null;
        video=route.data;cid=route.playbackCid;episode=route.playbackEpisode;partIndex=route.playbackPart;final boolean live=video.optBoolean("bl_live");if(live)cid=Long.parseLong(route.arg);
        player=new BlPlayer(ui,proxy,api,images,route.title,api.prefs.getBoolean("hardware",true),api.prefs.getBoolean("systemPlayer",false),new BlPlayer.Actions(){
            public void settings(){playerSettings();}public void episodes(){episodeOptions(video,episode);}public void comments(){BlPlugin.this.comments(video.optLong("aid"),1);}public void subtitle(){subtitles();}
            public void more(){playerMore();}public void diagnostics(){showDiagnostics();}public void related(){playerRelated();}public void up(){playerUp();}public void step(int delta){stepPart(delta);}public void order(){playOrder();}public void exit(){leavePlayer();}public void position(long at){if(dmFeed!=null)dmFeed.update(at);}
            public void complete(){String order=api.prefs.getString("play_order","顺序播放");if(!live&&order.equals("单集循环")){loadPlayback(0);return;}if(!live&&api.prefs.getBoolean("autoplay",true)&&(!order.equals("播完停止"))){if(stepPart(order.equals("倒序播放")?-1:1))return;}if(!live&&api.prefs.getBoolean("exit_after_play",false)){leavePlayer();return;}message("播放结束",false);}
            public void failure(String error){if(live&&!liveLegacy){liveLegacy=true;loadPlayback(0);message("正在切换兼容直播线路",false);}else if(!live&&!progressiveFallback&&playData!=null&&playData.optJSONObject("dash")!=null&&player!=null){progressiveFallback=true;long at=player.position();api.diagnostics.record("DASH 媒体回退 MP4/FLV",new java.io.IOException(error),"所有可用 DASH 线路失败或准备超时；重新请求兼容媒体，保留原始错误记录");player.recovering("正在切换兼容点播线路…");loadPlayback(at);message("DASH 播放失败，正在切换兼容线路",false);}else message(error,true);}
        });content.addView(player,new FrameLayout.LayoutParams(-1,-1));loadPlayback(live?0:api.prefs.getLong("position:"+video.optString("bvid")+":"+cid,0));
        if(!live)api.remember(video,cid);else{liveDanmaku=new BlLiveDanmaku(api,player.danmaku,cid);if(!active)liveDanmaku.active(false);}
    }
    private void loadPlayback(final long at){route.playbackCid=cid;route.playbackEpisode=episode;route.playbackPart=partIndex;final int request=++playbackGeneration;final boolean live=video.optBoolean("bl_live");final String bvid=video.optString("bvid");final long selectedCid=cid,selectedEpisode=episode;final int token=generation;final int quality=api.prefs.getInt("quality",64);
        final boolean dash=!progressiveFallback&&Build.VERSION.SDK_INT>=16&&api.prefs.getBoolean("prefer_dash",true)&&!api.prefs.getBoolean("systemPlayer",false);
        submit(new Job(){public Object run()throws Exception{if(live&&liveLegacy)return api.liveLegacy(selectedCid,api.prefs.getInt("live_quality",0));if(live)return api.livePlay(selectedCid,api.prefs.getInt("live_quality",0),Build.VERSION.SDK_INT<16||api.prefs.getBoolean("systemPlayer",false));if(dash)try{JSONObject data=selectedEpisode>0?api.episodeDash(selectedEpisode,selectedCid,quality):api.playDash(bvid,selectedCid,quality);if(data.optJSONObject("dash")!=null||BlApi.array(data,"durl").length()>0)return data;}catch(Exception error){api.diagnostics.record("DASH 接口回退 MP4/FLV",error,"BV="+bvid+"; cid="+selectedCid+"; 请求画质="+quality);}return selectedEpisode>0?api.episodePlay(selectedEpisode,selectedCid,quality):api.play(bvid,selectedCid,quality);}},token,new Result(){public void show(Object value)throws Exception{
            if(request!=playbackGeneration||selectedCid!=cid||player==null)return;playData=(JSONObject)value;JSONArray list=BlStreams.select(playData,quality,api.prefs.getString("video_codec","AVC"),api.prefs.getInt("audio_track",0));if(list.length()==0)throw new java.io.IOException("接口未返回可播放媒体，请登录或选择较低画质");player.play(list,at);JSONObject part=BlApi.array(video,"pages").optJSONObject(partIndex);String info=(part==null?"":("P"+(partIndex+1)+" · "+part.optString("part"))+"  ")+qualityName(list.optJSONObject(0).optInt("bl_quality",playData.optInt("quality",quality)))+" · "+(list.optJSONObject(0).has("bl_audio")?"DASH":"MP4/FLV");if(live)info="直播 · "+BlLiveStreams.quality(playData,playData.optInt("quality"));player.title(route.title,info);
        }});
        if(!live&&player!=null&&dmCid!=cid){if(dmFeed!=null)dmFeed.close();dmCid=cid;dmFeed=new BlDanmakuFeed(api,player.danmaku);dmFeed.reset(cid,video.optLong("aid"));player.setSubtitle(new JSONArray());if(api.prefs.getBoolean("subtitle_default",false))defaultSubtitle();api.remember(video,cid);}
    }

    private void leavePlayer(){saveRoute();closePlayer();hideModal();route=history.isEmpty()?new Route("home","推荐",""):history.pop();render();}
    private boolean stepPart(int delta){if(video==null||video.optBoolean("bl_live"))return false;JSONArray parts=BlApi.array(video,"pages");int next=partIndex+delta;String order=api.prefs.getString("play_order","顺序播放");if(order.equals("随机播放")&&parts.length()>1)next=(partIndex+1+new Random().nextInt(parts.length()-1))%parts.length();if(next<0||next>=parts.length()){message(delta>0?"已经是最后一集":"已经是第一集",false);return false;}savePlaybackPosition();partIndex=next;cid=parts.optJSONObject(next).optLong("cid");loadPlayback(0);return true;}
    private void playOrder(){if(video.optBoolean("bl_live")){message("直播不支持播放顺序",false);return;}List<Option> rows=new ArrayList<>();for(final String value:new String[]{"顺序播放","倒序播放","单集循环","随机播放","播完停止"})rows.add(new Option((value.equals(api.prefs.getString("play_order","顺序播放"))?"● ":"")+value,new Runnable(){public void run(){api.prefs.edit().putString("play_order",value).apply();message(value,false);}}));options("播放顺序",rows);}
    private void playerSettings(){
        List<Option> rows=new ArrayList<>();rows.add(new Option(video.optBoolean("bl_live")?"直播画质\n"+BlLiveStreams.quality(playData,playData==null?0:playData.optInt("quality")):"画质\n"+qualityName(playData==null?api.prefs.getInt("quality",64):playData.optInt("quality",64)),new Runnable(){public void run(){qualityOptions();}}));
        rows.add(new Option("播放速度\n"+api.prefs.getFloat("play_speed",1)+"x",new Runnable(){public void run(){List<Option> choices=new ArrayList<>();for(final float speed:new float[]{.25f,.5f,.75f,1,1.25f,1.5f,2})choices.add(new Option(speed+"x",new Runnable(){public void run(){if(player!=null&&!player.speed(speed))message("当前系统播放器仅支持 1 倍速",true);}}));options("播放速度",choices);}}));
        rows.add(new Option("画面缩放\n"+api.prefs.getString("screen_ratio","自适应"),new Runnable(){public void run(){List<Option> choices=new ArrayList<>();for(final String mode:new String[]{"自适应","宽度适应","高度适应","拉伸","放大"})choices.add(new Option(mode,new Runnable(){public void run(){api.prefs.edit().putString("screen_ratio",mode).apply();if(player!=null)player.refreshSettings();}}));options("画面缩放",choices);}}));
        rows.add(new Option("视频编码\n"+api.prefs.getString("video_codec","AVC"),new Runnable(){public void run(){List<Option> choices=new ArrayList<>();for(final String codec:new String[]{"AVC","HEVC","自动"})choices.add(new Option(codec,new Runnable(){public void run(){api.prefs.edit().putString("video_codec",codec).apply();if(player!=null)loadPlayback(player.position());}}));options("视频编码",choices);}}));
        rows.add(new Option("音轨",new Runnable(){public void run(){audioTracks();}}));rows.add(new Option("弹幕设置\n"+(dmFeed!=null?dmFeed.status:liveDanmaku!=null?liveDanmaku.status:"正在加载"),new Runnable(){public void run(){danmakuSettings();}}));
        rows.add(new Option("播放线路",new Runnable(){public void run(){List<Option> choices=new ArrayList<>();List<String> urls=player==null?Collections.<String>emptyList():player.lines();for(int i=0;i<urls.size();i++){final int index=i;choices.add(new Option(i==0?"主线路":"备用线路 "+i,new Runnable(){public void run(){if(player!=null)player.line(index);}}));}options("播放线路",choices);}}));
        rows.add(new Option("字幕",new Runnable(){public void run(){subtitles();}}));rows.add(new Option("字幕字号\n"+api.prefs.getInt("subtitle_size",27),new Runnable(){public void run(){integerChoices("字幕字号","subtitle_size",new int[]{20,24,27,32,36});}}));
        rows.add(new Option("底部常驻进度条\n"+(api.prefs.getBoolean("bottom_progress",true)?"开":"关"),new Runnable(){public void run(){api.prefs.edit().putBoolean("bottom_progress",!api.prefs.getBoolean("bottom_progress",true)).apply();if(player!=null)player.refreshSettings();}}));
        rows.add(new Option("显示视频调试信息\n"+(api.prefs.getBoolean("player_debug",false)?"开":"关"),new Runnable(){public void run(){api.prefs.edit().putBoolean("player_debug",!api.prefs.getBoolean("player_debug",false)).apply();}}));
        rows.add(new Option("控制栏与播放偏好",new Runnable(){public void run(){playerPreferences();}}));rows.add(new Option("错误详情",new Runnable(){public void run(){showDiagnostics();}}));options("播放设置",rows);
    }
    private void playerPreferences(){List<Option> rows=new ArrayList<>();for(final String[] item:new String[][]{{"show_next_previous","显示上下集切换按钮"},{"show_re_ff","显示快进快退按钮"},{"show_dm_switch","显示弹幕开关按钮"},{"subtitle_default","默认启用字幕"},{"exit_after_play","都播完后退出播放器"}}){final boolean fallback=item[0].startsWith("show_");rows.add(new Option(item[1]+"\n"+(api.prefs.getBoolean(item[0],fallback)?"开":"关"),new Runnable(){public void run(){boolean value=!api.prefs.getBoolean(item[0],fallback);api.prefs.edit().putBoolean(item[0],value).apply();if(player!=null){player.refreshSettings();if(item[0].equals("subtitle_default")){if(value)defaultSubtitle();else player.setSubtitle(new JSONArray());}}}}));}options("控制栏与播放偏好",rows);}
    private void defaultSubtitle(){if(video==null||video.optBoolean("bl_live"))return;final long selectedCid=cid;final String bvid=video.optString("bvid");final int token=generation;submit(new Job(){public Object run()throws Exception{JSONArray list=api.subtitles(bvid,selectedCid);if(list.length()==0)return new JSONArray();JSONObject chosen=list.optJSONObject(0);for(int i=0;i<list.length();i++){JSONObject s=list.optJSONObject(i);if(s!=null&&s.optString("lan").startsWith("zh")){chosen=s;break;}}if(chosen==null)return new JSONArray();String url=chosen.optString("subtitle_url");if(url.startsWith("//"))url="https:"+url;return new JSONObject(new String(api.bytes(url,2*1024*1024),"UTF-8")).optJSONArray("body");}},token,new Result(){public void show(Object value){if(player!=null&&selectedCid==cid&&api.prefs.getBoolean("subtitle_default",false))player.setSubtitle(value instanceof JSONArray?(JSONArray)value:new JSONArray());}},false);}
    private void audioTracks(){List<Option> rows=new ArrayList<>();JSONObject dash=playData==null?null:playData.optJSONObject("dash");JSONArray tracks=BlApi.array(dash,"audio");for(int i=0;i<tracks.length();i++){JSONObject track=tracks.optJSONObject(i);if(track==null)continue;final int id=track.optInt("id");rows.add(new Option("AAC · "+Math.round(track.optDouble("bandwidth")/1000)+" kbps",new Runnable(){public void run(){api.prefs.edit().putInt("audio_track",id).apply();if(player!=null)loadPlayback(player.position());}}));}if(rows.isEmpty())rows.add(new Option("当前媒体仅有一条音轨",new Runnable(){public void run(){}}));options("音轨",rows);}
    private void danmakuSettings(){List<Option> rows=new ArrayList<>();
        rows.add(new Option("弹幕开关\n"+(api.prefs.getBoolean("dm_enabled",true)?"开":"关"),new Runnable(){public void run(){if(player!=null)player.danmaku.enabled(!player.danmaku.enabled());}}));
        rows.add(new Option("弹幕字体大小\n"+Math.round(api.prefs.getFloat("dm_size",1)*100)+"%",new Runnable(){public void run(){floatChoices("弹幕字体大小","dm_size",new float[]{.7f,.85f,1,1.2f,1.5f});}}));
        rows.add(new Option("弹幕透明度\n"+Math.round(api.prefs.getFloat("dm_alpha",.9f)*100)+"%",new Runnable(){public void run(){floatChoices("弹幕透明度","dm_alpha",new float[]{.25f,.5f,.75f,.9f,1});}}));
        rows.add(new Option("弹幕速度\n"+api.prefs.getFloat("dm_speed",1)+"x",new Runnable(){public void run(){floatChoices("弹幕速度","dm_speed",new float[]{.5f,.75f,1,1.25f,1.5f,2});}}));
        rows.add(new Option("弹幕占屏比\n"+Math.round(api.prefs.getFloat("dm_area",.5f)*100)+"%",new Runnable(){public void run(){floatChoices("弹幕占屏比","dm_area",new float[]{.25f,.5f,.75f,1});}}));
        rows.add(new Option("弹幕密度\n"+api.prefs.getInt("dm_density",24),new Runnable(){public void run(){integerChoices("弹幕密度","dm_density",new int[]{8,16,24,32,48});}}));
        rows.add(new Option("弹幕智能屏蔽等级\n"+api.prefs.getInt("dm_weight",0),new Runnable(){public void run(){integerChoices("弹幕智能屏蔽等级","dm_weight",new int[]{0,2,4,6,8});}}));
        for(final String key:new String[]{"dm_top","dm_bottom"})rows.add(new Option((key.equals("dm_top")?"允许顶部悬停弹幕":"允许底部悬停弹幕")+"\n"+(api.prefs.getBoolean(key,true)?"开":"关"),new Runnable(){public void run(){api.prefs.edit().putBoolean(key,!api.prefs.getBoolean(key,true)).apply();if(player!=null)player.refreshSettings();}}));
        rows.add(new Option("关键词屏蔽\n"+api.prefs.getString("dm_block","未设置"),new Runnable(){public void run(){editKeywords();}}));options("弹幕设置",rows);
    }
    private void floatChoices(String title,final String key,float[] values){List<Option> rows=new ArrayList<>();for(final float value:values)rows.add(new Option(key.equals("dm_speed")?value+"x":Math.round(value*100)+"%",new Runnable(){public void run(){api.prefs.edit().putFloat(key,value).apply();if(player!=null)player.refreshSettings();}}));options(title,rows);}
    private void integerChoices(String title,final String key,int[] values){List<Option> rows=new ArrayList<>();for(final int value:values)rows.add(new Option(""+value,new Runnable(){public void run(){api.prefs.edit().putInt(key,value).apply();if(player!=null)player.refreshSettings();}}));options(title,rows);}
    private void editKeywords(){hideModal();modal=new FrameLayout(context);modal.setBackgroundColor(0xbb000000);LinearLayout card=ui.vertical();card.setPadding(ui.px(24),ui.px(20),ui.px(24),ui.px(20));card.setBackgroundDrawable(ui.fill(0xff303030,12));card.addView(ui.title("关键词屏蔽"));final EditText edit=new EditText(context);edit.setTextColor(BlUi.TEXT);edit.setHintTextColor(BlUi.SUB);edit.setHint("多个关键词用逗号分隔");edit.setText(api.prefs.getString("dm_block",""));card.addView(edit,new LinearLayout.LayoutParams(-1,ui.px(130)));Button save=ui.button("保存");card.addView(save);save.setOnClickListener(new View.OnClickListener(){public void onClick(View v){api.prefs.edit().putString("dm_block",edit.getText().toString()).apply();hideModal();if(player!=null)player.refreshSettings();}});modal.addView(card,ui.frame(640,-2,Gravity.CENTER));root.addView(modal,new FrameLayout.LayoutParams(-1,-1));edit.requestFocus();}
    private void playerMore(){List<Option> rows=new ArrayList<>();if(video.optBoolean("bl_live")){rows.add(new Option("直播信息\n"+route.title,new Runnable(){public void run(){}}));rows.add(new Option("直播画质",new Runnable(){public void run(){qualityOptions();}}));rows.add(new Option("弹幕设置",new Runnable(){public void run(){danmakuSettings();}}));rows.add(new Option("重新加载直播",new Runnable(){public void run(){loadPlayback(0);}}));options("直播",rows);return;}rows.add(new Option("视频详情\n"+video.optString("desc"),new Runnable(){public void run(){List<Option> detail=new ArrayList<>();detail.add(new Option(video.optString("desc","暂无简介"),new Runnable(){public void run(){}}));options("视频详情",detail);}}));
        rows.add(new Option("评论",new Runnable(){public void run(){comments(video.optLong("aid"),1);}}));rows.add(new Option("点赞",new Runnable(){public void run(){mutation("/x/web-interface/archive/like",BlApi.map("aid",""+video.optLong("aid"),"like","1"));}}));
        rows.add(new Option("投币",new Runnable(){public void run(){List<Option> choices=new ArrayList<>();for(final int n:new int[]{1,2})choices.add(new Option("确认投 "+n+" 枚硬币",new Runnable(){public void run(){mutation("/x/web-interface/coin/add",BlApi.map("aid",""+video.optLong("aid"),"multiply",""+n,"select_like","0"));}}));options("投币",choices);}}));
        rows.add(new Option("收藏",new Runnable(){public void run(){favorite(video);}}));rows.add(new Option("稍后再看",new Runnable(){public void run(){mutation("/x/v2/history/toview/add",BlApi.map("aid",""+video.optLong("aid")));}}));
        rows.add(new Option("一键三连",new Runnable(){public void run(){List<Option> choices=new ArrayList<>();choices.add(new Option("确认点赞、投币并收藏",new Runnable(){public void run(){mutation("/x/web-interface/archive/like/triple",BlApi.map("aid",""+video.optLong("aid")));}}));options("一键三连",choices);}}));options("更多",rows);
    }
    private void playerRelated(){if(video==null||video.optBoolean("bl_live")){message("直播没有相关视频",false);return;}final int token=generation;final String bvid=video.optString("bvid");submit(new Job(){public Object run()throws Exception{return api.related(bvid);}},token,new Result(){public void show(Object value){List<Option> rows=new ArrayList<>();JSONArray list=(JSONArray)value;for(int i=0;i<Math.min(24,list.length());i++){JSONObject item=list.optJSONObject(i);if(item==null)continue;final BlItem card=new BlItem(item,"video");rows.add(new Option(card.title+"\n"+card.owner,new Runnable(){public void run(){openItem(card);}}));}options("相关视频",rows);}});}
    private void playerUp(){final JSONObject owner=video==null?null:video.optJSONObject("owner");if(owner==null){message("当前内容没有 UP 主信息",false);return;}List<Option> rows=new ArrayList<>();rows.add(new Option("查看投稿\n"+owner.optString("name"),new Runnable(){public void run(){navigate(new Route("uploads",owner.optString("name"),owner.optString("mid")),true);}}));rows.add(new Option("关注 UP 主",new Runnable(){public void run(){List<Option> confirm=new ArrayList<>();confirm.add(new Option("确认关注 "+owner.optString("name"),new Runnable(){public void run(){mutation("/x/relation/modify",BlApi.map("fid",owner.optString("mid"),"act","1","re_src","11"));}}));options("关注",confirm);}}));rows.add(new Option("取消关注",new Runnable(){public void run(){List<Option> confirm=new ArrayList<>();confirm.add(new Option("确认取消关注 "+owner.optString("name"),new Runnable(){public void run(){mutation("/x/relation/modify",BlApi.map("fid",owner.optString("mid"),"act","2","re_src","11"));}}));options("取消关注",confirm);}}));options(owner.optString("name"),rows);}
    private void qualityOptions(){List<Option> values=new ArrayList<>();final boolean live=video!=null&&video.optBoolean("bl_live");JSONArray allowed=playData==null?new JSONArray():playData.optJSONArray("accept_quality");if(allowed==null||allowed.length()==0){if(live){message("接口未返回其他直播画质",false);return;}allowed=new JSONArray(Arrays.asList(16,32,64,80));}for(int i=0;i<allowed.length();i++){final int q=allowed.optInt(i);values.add(new Option(live?BlLiveStreams.quality(playData,q):qualityName(q),new Runnable(){public void run(){long at=live||player==null?0:player.position();api.prefs.edit().putInt(live?"live_quality":"quality",q).apply();if(live)liveLegacy=false;loadPlayback(at);}}));}options(live?"直播画质":"画质（可用画质由账号和接口决定）",values);}
    private static String qualityName(int q){switch(q){case 16:return "360P";case 32:return "480P";case 64:return "720P";case 74:return "720P 60帧";case 80:return "1080P";case 112:return "1080P+";case 116:return "1080P 60帧";case 120:return "4K";default:return "画质 "+q;}}
    private void episodeOptions(final JSONObject data,final long ep){List<Option> values=new ArrayList<>();JSONArray parts=BlApi.array(data,"pages");for(int i=0;i<parts.length();i++){final int index=i;JSONObject p=parts.optJSONObject(i);if(p==null)continue;values.add(new Option("P"+(i+1)+" · "+p.optString("part"),new Runnable(){public void run(){if(player!=null){savePlaybackPosition();partIndex=index;cid=BlApi.array(data,"pages").optJSONObject(index).optLong("cid");loadPlayback(0);}else startVideo(data,index,ep);}}));}options("选集",values);}
    private void subtitles(){
        if(video==null||player==null||video.optBoolean("bl_live")){message("当前内容没有字幕",false);return;}
        final int token=generation;final String bvid=video.optString("bvid");final long atCid=cid;final BlPlayer target=player;final Panel parent=openingPanel;
        submit(new Job(){public Object run()throws Exception{return api.subtitles(bvid,atCid);}},token,new Result(){public void show(Object value)throws Exception{
            if(player!=target||atCid!=cid)return;JSONArray list=(JSONArray)value;List<Option> choices=new ArrayList<>();choices.add(new Option("关闭字幕",new Runnable(){public void run(){if(player==target&&atCid==cid)player.setSubtitle(new JSONArray());}}));
            for(int i=0;i<list.length();i++){final JSONObject s=list.getJSONObject(i);choices.add(new Option(s.optString("lan_doc",s.optString("lan")),new Runnable(){public void run(){if(player!=target||atCid!=cid)return;String u=s.optString("subtitle_url");final String url=u.startsWith("//")?"https:"+u:u;submit(new Job(){public Object run()throws Exception{return new JSONObject(new String(api.bytes(url,2*1024*1024),"UTF-8")).optJSONArray("body");}},token,new Result(){public void show(Object value){if(player==target&&atCid==cid)player.setSubtitle(value instanceof JSONArray?(JSONArray)value:new JSONArray());}});}}));}
            openingPanel=parent;try{options("字幕",choices);}finally{openingPanel=null;}
        }});
    }
    private void comments(final long aid,final int page){if(aid<=0){message("当前内容无视频评论",false);return;}final int token=generation;submit(new Job(){public Object run()throws Exception{return api.comments(aid,page);}},token,new Result(){public void show(Object value)throws Exception{JSONArray replies=(JSONArray)value;List<Option> values=new ArrayList<>();for(int i=0;i<replies.length();i++){JSONObject reply=replies.getJSONObject(i),member=reply.optJSONObject("member"),body=reply.optJSONObject("content");values.add(new Option((member==null?"":member.optString("uname"))+"   ♥ "+reply.optLong("like")+"\n"+(body==null?"":body.optString("message")),new Runnable(){public void run(){}}));}if(replies.length()>=20)values.add(new Option("下一页",new Runnable(){public void run(){comments(aid,page+1);}}));options("热门评论 · 第 "+page+" 页",values);}});}
    private void favorite(final JSONObject data){final int token=generation;submit(new Job(){public Object run()throws Exception{return api.favoriteFolders();}},token,new Result(){public void show(Object value)throws Exception{JSONArray folders=(JSONArray)value;List<Option> values=new ArrayList<>();for(int i=0;i<folders.length();i++){final JSONObject f=folders.getJSONObject(i);values.add(new Option("收藏到 · "+f.optString("title"),new Runnable(){public void run(){mutation("/x/v3/fav/resource/deal",BlApi.map("rid",""+data.optLong("aid"),"type","2","add_media_ids",f.optString("id")));}}));}options("选择收藏夹",values);}});}
    private void mutation(final String path,final Map<String,String> data){final int token=generation;submit(new Job(){public Object run()throws Exception{List<String> pairs=new ArrayList<>();for(Map.Entry<String,String> p:data.entrySet()){pairs.add(p.getKey());pairs.add(p.getValue());}return api.post(path,pairs.toArray(new String[0]));}},token,new Result(){public void show(Object value){message("操作成功",false);}});}
    private void renderLogin(final LinearLayout page){
        final ImageView qr=new ImageView(context);qr.setBackgroundColor(0xffffffff);LinearLayout.LayoutParams image=new LinearLayout.LayoutParams(ui.px(250),ui.px(250));image.gravity=Gravity.CENTER_HORIZONTAL;image.topMargin=ui.px(20);page.addView(qr,image);final TextView hint=ui.text("正在获取二维码…",20,BlUi.TEXT);hint.setGravity(Gravity.CENTER);hint.setPadding(0,ui.px(16),0,ui.px(16));page.addView(hint);
        final int token=generation;submit(new Job(){public Object run()throws Exception{return api.qr();}},token,new Result(){public void show(Object value)throws Exception{JSONObject q=(JSONObject)value;String address=q.getString("url"),key=q.getString("qrcode_key");BitMatrix matrix=new QRCodeWriter().encode(address,BarcodeFormat.QR_CODE,320,320);int[] pixels=new int[320*320];for(int y=0;y<320;y++)for(int x=0;x<320;x++)pixels[y*320+x]=matrix.get(x,y)?0xff000000:0xffffffff;Bitmap b=Bitmap.createBitmap(pixels,320,320,Bitmap.Config.ARGB_8888);qr.setImageBitmap(b);hint.setText("使用哔哩哔哩手机客户端扫码并确认登录");poll(key,hint,token,0);}});
    }
    private void poll(final String key,final TextView hint,final int token,final int count){if(closed||generation!=token)return;if(count>=90){hint.setText("二维码已过期，请刷新");return;}main.postDelayed(new Runnable(){public void run(){if(closed||!active||generation!=token)return;submit(new Job(){public Object run()throws Exception{return api.poll(key);}},token,new Result(){public void show(Object value)throws Exception{JSONObject d=(JSONObject)value;int code=d.optInt("code");if(code==0&&api.signedIn()){hint.setText("登录成功");navigate(new Route("mine","我的",""),false);}else if(code==86038)hint.setText("二维码已过期，请刷新");else{hint.setText(code==86090?"已扫码，请在手机上确认":"等待扫码…");poll(key,hint,token,count+1);}}});}},2000);}
    private ListView optionList(final List<Option> values){
        final ListView list=new ListView(context);list.setDividerHeight(ui.px(3));list.setPadding(0,ui.px(12),0,ui.px(8));list.setAdapter(new BaseAdapter(){public int getCount(){return values.size();}public Object getItem(int p){return values.get(p);}public long getItemId(int p){return p;}public View getView(int p,View reuse,ViewGroup parent){LinearLayout row=ui.horizontal();row.setGravity(Gravity.CENTER_VERTICAL);row.setMinimumHeight(ui.px(64));row.setPadding(ui.px(18),ui.px(12),ui.px(14),ui.px(12));row.setBackgroundDrawable(ui.focus(0x44303030));LinearLayout words=ui.vertical();String[] text=values.get(p).title.split("\n",2);TextView title=ui.text(text[0],22,BlUi.TEXT);title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);words.addView(title);if(text.length>1){TextView sub=ui.text(text[1],17,BlUi.SUB);sub.setMaxLines(3);sub.setEllipsize(android.text.TextUtils.TruncateAt.END);words.addView(sub);}row.addView(words,new LinearLayout.LayoutParams(0,-2,1));TextView arrow=ui.text("›",26,BlUi.SUB);arrow.setPadding(ui.px(10),0,0,0);row.addView(arrow);return row;}});android.graphics.drawable.GradientDrawable selected=ui.fill(0x55bb86fc,6);selected.setStroke(ui.px(2),BlUi.ACCENT);list.setSelector(selected);list.setDrawSelectorOnTop(true);list.setOnItemClickListener(new AdapterView.OnItemClickListener(){public void onItemClick(AdapterView<?> p,View v,int position,long id){Option option=values.get(position);Panel from=panel;if(from!=null)from.selection=position;openingPanel=from;hideModal();try{option.action.run();}finally{openingPanel=null;}}});list.requestFocusFromTouch();list.setSelection(0);return list;
    }
    private void options(String title,List<Option> values){showPanel(new Panel(title,values,openingPanel));}
    private void showPanel(Panel value){hideModal();panel=value;modalGeneration++;modalFocus=root.findFocus();modal=new FrameLayout(context);modal.setBackgroundColor(player==null?0xcc101010:0x66101010);LinearLayout card=ui.vertical();card.setPadding(ui.px(20),ui.px(20),ui.px(20),ui.px(16));card.setBackgroundDrawable(ui.fill(0xf5262626,player==null||value.title.equals("选集")?10:0));TextView title=ui.title(value.title);title.setMaxLines(2);card.addView(title);ListView list=optionList(value.rows);card.addView(list,new LinearLayout.LayoutParams(-1,0,1));Button back=ui.button("返回");card.addView(back);back.setOnClickListener(new View.OnClickListener(){public void onClick(View v){backPanel();}});modal.addView(card,player!=null&&!value.title.equals("选集")?ui.frame(440,-1,Gravity.RIGHT):ui.frame(player!=null?680:860,player!=null?410:500,Gravity.CENTER));root.addView(modal,new FrameLayout.LayoutParams(-1,-1));list.requestFocusFromTouch();list.setSelection(value.selection);}
    private void backPanel(){Panel parent=panel==null?null:panel.parent;if(parent==null)hideModal();else showPanel(parent);}
    private void hideModal(){panel=null;modalGeneration++;if(modal!=null){root.removeView(modal);modal=null;if(modalFocus!=null)modalFocus.requestFocus();modalFocus=null;}}
    private String diagnosticContext(){return "页面="+(route==null?"启动":route.kind)+"; 登录="+api.signedIn()+(video==null?"":"; BV="+video.optString("bvid")+"; cid="+cid+"; P="+(partIndex+1))+"; 请求画质="+api.prefs.getInt("quality",64);}
    private void reportFailure(String stage,Throwable error,boolean visible){BlDiagnostics.Entry entry=api.diagnostics.record(stage,error,diagnosticContext());if(visible){if(player!=null)player.requestFailure(entry.summary);message(entry.summary+" · 查看错误详情",true);}}
    private void showDiagnostics(){
        List<BlDiagnostics.Entry> entries=api.diagnostics.snapshot();StringBuilder text=new StringBuilder();if(entries.isEmpty())text.append("暂无错误记录。\nBL v").append(BlDiagnostics.VERSION).append(" · Android ").append(Build.VERSION.RELEASE).append(" / API ").append(Build.VERSION.SDK_INT).append('\n').append(diagnosticContext());
        for(BlDiagnostics.Entry entry:entries){if(text.length()>48000)break;if(text.length()>0)text.append("\n\n────────────────\n\n");text.append(entry.details);}final String details=text.toString();
        Panel parent=openingPanel;hideModal();panel=new Panel("错误详情",new ArrayList<Option>(),parent);modalGeneration++;modalFocus=root.findFocus();modal=new FrameLayout(context);modal.setBackgroundColor(0xcc101010);
        LinearLayout card=ui.vertical();card.setPadding(ui.px(22),ui.px(18),ui.px(22),ui.px(16));card.setBackgroundDrawable(ui.fill(0xff262626,10));card.addView(ui.title("错误详情 · 最近 "+entries.size()+" 条"));
        TextView body=ui.text(details,17,BlUi.TEXT);body.setGravity(Gravity.TOP|Gravity.LEFT);body.setPadding(ui.px(6),ui.px(12),ui.px(6),ui.px(12));ScrollView scroll=ui.scroll(body);scroll.setContentDescription("错误详情内容");scroll.setId(0x424c10);scroll.setFocusable(true);scroll.setFocusableInTouchMode(true);scroll.setNextFocusDownId(0x424c11);card.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout buttons=ui.horizontal();Button copy=ui.button("复制详情");copy.setId(0x424c11);copy.setNextFocusUpId(0x424c10);copy.setNextFocusRightId(0x424c12);buttons.addView(copy,new LinearLayout.LayoutParams(0,-2,1));ui.gap(buttons,12);Button back=ui.button("返回");back.setId(0x424c12);back.setNextFocusUpId(0x424c10);back.setNextFocusLeftId(0x424c11);buttons.addView(back,new LinearLayout.LayoutParams(0,-2,1));card.addView(buttons);
        copy.setOnClickListener(new View.OnClickListener(){public void onClick(View v){android.content.ClipboardManager clipboard=(android.content.ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);if(clipboard!=null){clipboard.setPrimaryClip(android.content.ClipData.newPlainText("BL 错误详情",details));Toast.makeText(context,"已复制错误详情",Toast.LENGTH_SHORT).show();}}});back.setOnClickListener(new View.OnClickListener(){public void onClick(View v){backPanel();}});
        modal.addView(card,ui.frame(1060,600,Gravity.CENTER));root.addView(modal,new FrameLayout.LayoutParams(-1,-1));copy.requestFocusFromTouch();
    }
    private void message(String text,boolean error){statusError=error;String safe=BlDiagnostics.clean(text);if(error&&!safe.contains("[BL-"))safe=api.diagnostics.record("界面提示",new java.io.IOException(safe),diagnosticContext()).summary;if(status!=null){status.setText(safe);status.setTextColor(error?0xffffb0b0:BlUi.SUB);}if(error||player!=null)Toast.makeText(context,safe,Toast.LENGTH_LONG).show();}
    private void submit(final Job job,final int token,final Result result){submit(job,token,result,true);}
    private void submit(final Job job,final int token,final Result result,final boolean report){
        if(closed)return;
        jobs.execute(new Runnable(){public void run(){
            if(closed||token!=generation)return;
            try{
                final Object value=job.run();
                main.post(new Runnable(){public void run(){
                    if(closed||token!=generation)return;
                    try{result.show(value);}catch(Exception e){loading=false;reportFailure("内容处理",e,report);}catch(LinkageError e){loading=false;reportFailure("宿主接口不兼容",e,report);}
                }});
            }catch(final Exception error){
                main.post(new Runnable(){public void run(){
                    if(closed||token!=generation)return;
                    loading=false;reportFailure("页面请求",error,report);
                    if(report&&error instanceof BlApi.ApiException&&((BlApi.ApiException)error).code==-101){
                        List<Option> values=new ArrayList<>();values.add(new Option("扫码登录",new Runnable(){public void run(){navigate(new Route("login","扫码登录",""),true);}}));options("此功能需要登录",values);
                    }
                }});
            }catch(final LinkageError error){main.post(new Runnable(){public void run(){if(closed||token!=generation)return;loading=false;reportFailure("宿主接口不兼容",error,report);}});}
        }});
    }
    private void savePlaybackPosition(){if(player!=null&&video!=null&&!video.optBoolean("bl_live"))api.savePosition(video.optString("bvid"),cid,player.position());}
    private void closePlayer(){playbackGeneration++;if(liveDanmaku!=null){liveDanmaku.close();liveDanmaku=null;}if(dmFeed!=null){dmFeed.close();dmFeed=null;}dmCid=0;if(player!=null){savePlaybackPosition();player.close();retired.add(player);player=null;for(Iterator<BlPlayer> i=retired.iterator();i.hasNext();)try{if(i.next().awaitRelease(0))i.remove();}catch(InterruptedException e){Thread.currentThread().interrupt();}}}
    /** Back first dismisses an in-View modal, then pops the native route stack. */
    public boolean onBack(){if(modal!=null){backPanel();return true;}if(player!=null&&player.back())return true;if(history.isEmpty())return false;saveRoute();closePlayer();route=history.pop();render();return true;}
    /** Host visibility drives media and timers without an Activity inside this JAR. */
    public void setActive(boolean active){if(this.active!=active&&liveDanmaku!=null)liveDanmaku.active(active);this.active=active;if(player!=null)player.active(active);main.removeCallbacks(clock);if(active&&!closed){main.post(clock);if(route!=null&&route.kind.equals("login"))render();}}
    public void update(long positionMs,boolean playing){}
    /** Stop playback, sockets, downloads, polling and bitmap workers on channel exit. */
    public void dispose(){if(closed)return;closed=true;generation++;main.removeCallbacksAndMessages(null);closePlayer();hideModal();jobs.shutdownNow();if(images!=null)images.close();history.clear();if(root!=null)root.removeAllViews();final List<BlPlayer> release=new ArrayList<>(retired);Thread cleanup=new Thread(new Runnable(){public void run(){for(BlPlayer p:release)try{p.awaitRelease(5);}catch(InterruptedException e){Thread.currentThread().interrupt();}if(proxy!=null)proxy.close();if(api!=null)api.close();}},"bl-channel-cleanup");cleanup.setDaemon(true);cleanup.start();}
    private final class Root extends FrameLayout {
        Root(Context c){super(c);}
        public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused&&modal==null&&player==null&&grid!=null&&adapter!=null&&adapter.getCount()>0){grid.requestFocusFromTouch();grid.setSelection(Math.min(route.selection,adapter.getCount()-1));}}
        public boolean dispatchKeyEvent(KeyEvent event){
            if(player!=null&&modal==null&&player.key(event))return true;
            int code=event.getKeyCode();if(player==null&&modal==null&&findFocus()==null&&grid!=null&&adapter!=null&&adapter.getCount()>0&&(code==KeyEvent.KEYCODE_DPAD_UP||code==KeyEvent.KEYCODE_DPAD_DOWN||code==KeyEvent.KEYCODE_DPAD_LEFT||code==KeyEvent.KEYCODE_DPAD_RIGHT||code==KeyEvent.KEYCODE_DPAD_CENTER||code==KeyEvent.KEYCODE_ENTER)){if(event.getAction()==KeyEvent.ACTION_DOWN){grid.requestFocusFromTouch();grid.setSelection(Math.min(route.selection,adapter.getCount()-1));}return true;}
            if(event.getKeyCode()==KeyEvent.KEYCODE_DPAD_CENTER||event.getKeyCode()==KeyEvent.KEYCODE_ENTER){View focus=findFocus();if(focus instanceof AdapterView){if(event.getAction()==KeyEvent.ACTION_UP&&!event.isCanceled()){AdapterView<?> list=(AdapterView<?>)focus;int p=Math.max(0,list.getSelectedItemPosition());if(list.getAdapter()!=null&&p<list.getAdapter().getCount())list.performItemClick(list.getSelectedView(),p,list.getAdapter().getItemId(p));}return true;}}
            if(event.getKeyCode()>=KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE&&event.getKeyCode()<=KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)return true;
            return super.dispatchKeyEvent(event);
        }
    }
}
