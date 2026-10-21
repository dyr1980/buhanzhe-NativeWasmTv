package xiao.bu.channel.bl;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.view.Surface;
import android.view.SurfaceHolder;
import java.io.*;
import java.util.Map;
import tv.danmaku.ijk.media.player.AbstractMediaPlayer;
import tv.danmaku.ijk.media.player.MediaInfo;
import tv.danmaku.ijk.media.player.misc.ITrackInfo;

/** API-14 platform adapter. IJK's AndroidMediaPlayer listener references API-16 TimedText. */
final class BlSystemPlayer extends AbstractMediaPlayer {
    private final MediaPlayer player=new MediaPlayer();
    private String source="";
    BlSystemPlayer(){
        player.setOnPreparedListener(new MediaPlayer.OnPreparedListener(){public void onPrepared(MediaPlayer p){notifyOnPrepared();}});
        player.setOnCompletionListener(new MediaPlayer.OnCompletionListener(){public void onCompletion(MediaPlayer p){notifyOnCompletion();}});
        player.setOnBufferingUpdateListener(new MediaPlayer.OnBufferingUpdateListener(){public void onBufferingUpdate(MediaPlayer p,int percent){notifyOnBufferingUpdate(percent);}});
        player.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener(){public void onSeekComplete(MediaPlayer p){notifyOnSeekComplete();}});
        player.setOnVideoSizeChangedListener(new MediaPlayer.OnVideoSizeChangedListener(){public void onVideoSizeChanged(MediaPlayer p,int w,int h){notifyOnVideoSizeChanged(w,h,1,1);}});
        player.setOnErrorListener(new MediaPlayer.OnErrorListener(){public boolean onError(MediaPlayer p,int what,int extra){return notifyOnError(what,extra);}});
        player.setOnInfoListener(new MediaPlayer.OnInfoListener(){public boolean onInfo(MediaPlayer p,int what,int extra){return notifyOnInfo(what,extra);}});
    }
    public void setDisplay(SurfaceHolder h){player.setDisplay(h);}
    public void setSurface(Surface s){player.setSurface(s);}
    public void setDataSource(Context c,Uri u)throws IOException{source=u.toString();player.setDataSource(c,u);}
    public void setDataSource(Context c,Uri u,Map<String,String> headers)throws IOException{source=u.toString();player.setDataSource(c,u,headers);}
    public void setDataSource(FileDescriptor f)throws IOException{source="";player.setDataSource(f);}
    public void setDataSource(String s)throws IOException{source=s;player.setDataSource(s);}
    public String getDataSource(){return source;}
    public void prepareAsync(){player.prepareAsync();}
    public void start(){player.start();}public void stop(){player.stop();}public void pause(){player.pause();}
    public void setScreenOnWhilePlaying(boolean v){player.setScreenOnWhilePlaying(v);}
    public int getVideoWidth(){return player.getVideoWidth();}public int getVideoHeight(){return player.getVideoHeight();}
    public boolean isPlaying(){return player.isPlaying();}
    public void seekTo(long at){player.seekTo((int)Math.min(Integer.MAX_VALUE,Math.max(0,at)));}
    public long getCurrentPosition(){return player.getCurrentPosition();}public long getDuration(){return player.getDuration();}
    public void release(){resetListeners();player.release();}public void reset(){player.reset();resetListeners();}
    public void setVolume(float l,float r){player.setVolume(l,r);}
    public int getAudioSessionId(){return player.getAudioSessionId();}
    public MediaInfo getMediaInfo(){return new MediaInfo();}public void setLogEnabled(boolean v){}public boolean isPlayable(){return true;}
    public void setAudioStreamType(int v){player.setAudioStreamType(v);}public void setKeepInBackground(boolean v){}
    public int getVideoSarNum(){return 1;}public int getVideoSarDen(){return 1;}
    public void setWakeMode(Context c,int mode){player.setWakeMode(c,mode);}
    public void setLooping(boolean v){player.setLooping(v);}public boolean isLooping(){return player.isLooping();}
    public ITrackInfo[] getTrackInfo(){return new ITrackInfo[0];}
}
