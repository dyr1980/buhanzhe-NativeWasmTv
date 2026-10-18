package xiao.bu.tv.skin;

import android.os.Bundle;
import android.content.Context;
import android.view.View;

/** API 2: views belong to the JAR; media, data and actions belong to the host.
 * create/update are called on the UI thread; constructors run on the loader thread.
 * Slots are existing data-backed controls, never a video Surface or player.
 */
public interface TvUi {
    View create(Context context, String part, Bundle state, View[] slots, Events events);
    void update(View view, Bundle state);

    interface Events {
        Bundle options(int category);
        void action(int category, int option);
        void dismiss();
    }
}
