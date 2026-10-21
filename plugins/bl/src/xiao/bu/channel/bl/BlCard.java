package xiao.bu.channel.bl;

import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.*;

/** Recycled BBLL 16:9 cover, gradient counters, two-line title and UP row. */
final class BlCard extends LinearLayout {
    private final ImageView cover;
    private final TextView title,owner,badge;
    private final FrameLayout image;
    private final BlUi ui;
    BlCard(BlUi ui){
        super(ui.context);this.ui=ui;setOrientation(VERTICAL);setPadding(ui.px(10),ui.px(12),ui.px(10),ui.px(12));setBackgroundDrawable(ui.focus(0));setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        image=new FrameLayout(ui.context);cover=new ImageView(ui.context);cover.setScaleType(ImageView.ScaleType.CENTER_CROP);image.addView(cover,new FrameLayout.LayoutParams(-1,-1));
        badge=ui.text("",14,0xffeeeeee);badge.setGravity(Gravity.BOTTOM|Gravity.LEFT);badge.setPadding(ui.px(8),ui.px(12),ui.px(8),ui.px(8));badge.setBackgroundDrawable(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x00000000,0xcc000000}));image.addView(badge,new FrameLayout.LayoutParams(-1,ui.px(40),Gravity.BOTTOM));addView(image,new LinearLayout.LayoutParams(-1,ui.px(130)));
        title=ui.text("",19,BlUi.TEXT);title.setMaxLines(2);title.setMinLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,ui.px(52));tp.topMargin=ui.px(8);addView(title,tp);
        owner=ui.text("",14,BlUi.SUB);owner.setSingleLine();owner.setEllipsize(android.text.TextUtils.TruncateAt.END);addView(owner,new LinearLayout.LayoutParams(-1,ui.px(24)));
    }
    void bind(BlItem item,BlImages images){title.setText(item.title);owner.setText(item.owner.isEmpty()?(item.kind.equals("folder")?"收藏夹":item.kind.equals("live")?"直播":item.kind.equals("season")?"番剧":""):"UP · "+item.owner);badge.setText((item.kind.equals("live")?"●  ":"▷  ")+BlUi.count(item.count)+(item.duration>0?"     "+BlUi.time(item.duration):""));images.load(cover,item.picture,true);setContentDescription(item.title);}
    protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);int height=Math.max(1,(w-getPaddingLeft()-getPaddingRight())*9/16);if(image.getLayoutParams().height!=height){image.getLayoutParams().height=height;image.requestLayout();}}
}
