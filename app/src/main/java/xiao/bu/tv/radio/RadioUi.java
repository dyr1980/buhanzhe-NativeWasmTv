package xiao.bu.tv.radio;

import android.graphics.Bitmap;
import android.view.View;
import android.animation.TimeInterpolator;

/** Version 1 host/plugin contract. Only Android API14 types cross this boundary. */
public interface RadioUi {
    View view();
    void show(String title, boolean live, Bitmap cover);
    void showPending(String title, String logo, Bitmap cover);
    boolean hasPendingPresentation();
    void clear(boolean keepPending);
    void trimMemory();
    void setTransitionListener(Runnable listener);
    boolean isTransitionRunning();
    void beginChannelSwitch(int direction);
    void finishChannelSwitch();
    void previewSlide(float distance, boolean nextIsAudio, String logo, Bitmap cover);
    void updateNeighborCover(String logo, Bitmap cover);
    void restoreSlide(long duration, TimeInterpolator interpolator);
    void resetSlide();
    void setCover(Bitmap cover);
    void setPlaying(boolean playing);
    void setActive(boolean active);
    Bitmap cover();
    int coverRevision();
    void dispose();
}
