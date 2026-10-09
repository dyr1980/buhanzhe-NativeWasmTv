package xiao.bu.tv;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Messenger;

/** The browser binds above-client so memory pressure sacrifices it before the player. */
public final class LegacyWebGuardService extends Service {
    private final Messenger inbox = new Messenger(new Handler(message -> {
        LegacyWebSession session = LegacyWebSession.current;
        if (session != null) session.receive(message);
        else LegacyWebSession.rejectHello(message);
        return true;
    }));
    @Override public IBinder onBind(Intent intent) { return inbox.getBinder(); }
}
