package io.github.hxfuxyy.zetacard;

import android.app.Application;
import io.github.hxfuxyy.zetacard.data.ArtSync;
import io.github.hxfuxyy.zetacard.logging.DiagnosticsLog;
import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ZetaApplication extends Application implements XposedServiceHelper.OnServiceListener {
    private static volatile XposedService service;
    private final ExecutorService syncExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ZetaCard artwork sync");
        thread.setDaemon(true);
        return thread;
    });

    @Override public void onCreate() {
        super.onCreate();
        DiagnosticsLog.info(this, "App service starting");
        XposedServiceHelper.registerListener(this);
    }

    @Override public void onServiceBind(XposedService connected) {
        service = connected;
        DiagnosticsLog.info(this, "LSPosed data service connected");
        syncExecutor.execute(() -> {
            XposedService current = service;
            if (current != null) ArtSync.syncAll(current, this);
        });
    }

    @Override public void onServiceDied(XposedService disconnected) {
        if (service == disconnected) service = null;
        DiagnosticsLog.warning(this, "LSPosed data service disconnected");
    }

    public static boolean isConnected() {
        return service != null;
    }

    /** [ZetaCardNext] 首頁樣式開關需要寫入 LSPosed 遠端設定；未連線時回傳 null */
    public static XposedService currentService() {
        return service;
    }

    public static boolean syncArt(Application context, String id) {
        return ArtSync.sync(service, context, id);
    }
}
