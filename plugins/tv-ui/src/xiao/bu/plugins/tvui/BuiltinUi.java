package xiao.bu.plugins.tvui;
import android.content.Context;
import android.os.Bundle;
import android.view.*;
import xiao.bu.tv.skin.TvUi;
/** Original native XML renderer. Resource names are part of the host contract. */
public final class BuiltinUi implements TvUi {
 public View create(Context c,String part,Bundle state,View[] slots,Events events){
  boolean row=part.endsWith("-row");
  if(!row&&slots!=null&&slots.length>0)return slots[0];
  String layout="group-row".equals(part)?"item_channel_group":"channel-row".equals(part)?"item_channel":"activity_main";
  int id=c.getResources().getIdentifier(layout,"layout",c.getPackageName());
  if(id==0)throw new IllegalStateException("原版布局不存在："+layout);
  return LayoutInflater.from(c).inflate(id,row&&slots!=null?(ViewGroup)slots[0]:null,false);
 }
 public void update(View v,Bundle state){}
}
