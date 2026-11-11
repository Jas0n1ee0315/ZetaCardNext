package io.github.hxfuxyy.zetacard.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import io.github.hxfuxyy.zetacard.R;
import io.github.hxfuxyy.zetacard.ZetaApplication;
import io.github.hxfuxyy.zetacard.core.HomeLayoutSetting;
import io.github.hxfuxyy.zetacard.logging.DiagnosticsLog;
import io.github.hxfuxyy.zetacard.root.RootBridge;
import io.github.hxfuxyy.zetacard.root.RootShell;
import io.github.libxposed.service.XposedService;

/**
 * [ZetaCardNext] 三點選單「強制大卡片首頁」開關的 App 端邏輯。
 *
 * <p>流程：寫入 LSPosed 遠端設定（Wallet hook 讀這份）→ 寫入本地設定（選單顯示用）→ 以 root force-stop Wallet。
 * 新設定在下次開啟 Wallet 時生效，由 {@code wallet.HomeLayoutOverride} 決定是否改首頁。
 */
public final class HomeLayoutToggle {
    private HomeLayoutToggle() {
    }

    /** 目前是否開啟（讀本地設定，供選單顯示） */
    public static boolean isEnabled(Context context) {
        return prefs(context).getBoolean(HomeLayoutSetting.KEY_BIG_CARDS, false);
    }

    /**
     * 切換開關。涉及 binder 與 root 指令，會在背景執行緒進行，完成後以 Toast 回報結果。
     *
     * @param context Application context
     */
    public static void toggle(Context context) {
        Context app = context.getApplicationContext();
        Thread worker = new Thread(() -> toast(app, apply(app)), "ZetaCard home layout");
        worker.setDaemon(true);
        worker.start();
    }

    /** 實際執行切換，回傳要顯示給使用者的訊息 */
    private static String apply(Context context) {
        try {
            XposedService service = ZetaApplication.currentService();
            // 訊息皆使用字串資源，跟隨 App 語言（1.6 起支援 18 種語言）
            if (service == null) return context.getString(R.string.big_cards_failed);

            boolean enabled = !isEnabled(context);
            boolean saved = service.getRemotePreferences(HomeLayoutSetting.REMOTE_GROUP).edit()
                    .putBoolean(HomeLayoutSetting.KEY_BIG_CARDS, enabled)
                    .commit();
            if (!saved) return context.getString(R.string.big_cards_failed);
            prefs(context).edit().putBoolean(HomeLayoutSetting.KEY_BIG_CARDS, enabled).commit();
            DiagnosticsLog.info(context, enabled
                    ? "Home layout override enabled (big cards)"
                    : "Home layout override disabled (Wallet default)");

            // 必須用 force-stop：RootBridge.restartWallet 的 kill -9 會讓 Android 以 savedInstanceState
            // 還原舊的首頁 Fragment，新設定不會生效
            // force-stop 失敗時（例如 root 被拒）使用者重開 Wallet 前需自行關閉，訊息中的「重新開啟」仍適用
            if (RootShell.run("am force-stop " + RootBridge.WALLET_PACKAGE) == null) {
                DiagnosticsLog.warning(context, "Home layout toggle: could not force-stop Wallet");
            }
            return context.getString(enabled ? R.string.big_cards_enabled : R.string.big_cards_disabled);
        } catch (Throwable error) {
            DiagnosticsLog.warning(context, "Home layout toggle failed: " + error.getClass().getSimpleName());
            return context.getString(R.string.big_cards_failed);
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(HomeLayoutSetting.LOCAL_PREFS, Context.MODE_PRIVATE);
    }

    /** 在主執行緒顯示 Toast */
    private static void toast(Context context, String message) {
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(context, message, Toast.LENGTH_LONG).show());
    }
}
