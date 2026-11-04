package io.github.hxfuxyy.zetacard.wallet;

import android.content.Context;
import io.github.hxfuxyy.zetacard.core.HomeLayoutSetting;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Method;

/**
 * [ZetaCardNext] 強制 Google Wallet 使用「大卡片」首頁。
 *
 * <h2>背景</h2>
 * Wallet 的 {@code WalletFragmentFactory} 依下列邏輯決定首頁（以 26.43.1009867093 反組譯得知）：
 * <pre>
 * if (旗標 45805255) {                                   // 新首頁總開關
 *     if (!isFelicaDevice || 旗標 45777533) → 大卡片首頁   // FeliCa 手機也啟用新首頁
 *     else                                  → 列表首頁
 * } else → 列表首頁
 * </pre>
 * 其中 isFelicaDevice 會在裝有おサイフケータイ（{@code com.felicanetworks.mfc}）或 device_country=JP
 * 時成立，所以日本版 Pixel 預設停在列表首頁。
 *
 * <h2>做法</h2>
 * Hook 讀取這兩個伺服器旗標的方法，一律回傳 true。旗標類別與方法名稱經過混淆、每個 Wallet 版本都可能不同，
 * 因此只對 {@link #TARGETS} 列出、已驗證過的版本生效；其他版本會跳過並寫 log，不會誤 hook。
 *
 * <h2>支援新版 Wallet</h2>
 * 用 apktool 反組譯新版 Wallet，搜尋 {@code const-string "45777533"} 與 {@code "45805255"}，
 * 找到所在類別（實作旗標介面的 final class）與對應的 {@code ()Z} 方法名稱，加入 {@link #TARGETS}。
 */
final class HomeLayoutOverride {
    /** 記錄一般事件（對應 WalletHook.event） */
    interface EventSink {
        void event(String message);
    }

    /** 記錄警告（對應 WalletHook.warn） */
    interface WarnSink {
        void warn(String key, String message, Throwable error);
    }

    /** 某個 Wallet 版本的旗標位置 */
    private static final class Target {
        final long versionCode;
        final String flagClass;
        /** 依序為旗標 45805255、45777533 的讀取方法 */
        final String[] flagMethods;

        Target(long versionCode, String flagClass, String... flagMethods) {
            this.versionCode = versionCode;
            this.flagClass = flagClass;
            this.flagMethods = flagMethods;
        }
    }

    /** 已驗證的 Wallet 版本 */
    private static final Target[] TARGETS = {
            // Google Wallet 26.43.1009867093
            new Target(934004749L, "brfp", "x", "p"),
    };

    private HomeLayoutOverride() {
    }

    /**
     * 依設定安裝 hook。必須在 Wallet 建立首頁 Fragment 之前呼叫（WalletHook.start 於 Application 附加後執行）。
     *
     * @param module  用於讀取遠端設定與安裝 hook 的 Xposed 模組
     * @param context Wallet 的 Application
     * @param version Wallet 的 versionCode
     */
    static void install(XposedModule module, Context context, long version, EventSink events, WarnSink warnings) {
        try {
            boolean enabled = module.getRemotePreferences(HomeLayoutSetting.REMOTE_GROUP)
                    .getBoolean(HomeLayoutSetting.KEY_BIG_CARDS, false);
            if (!enabled) {
                events.event("Home layout override off (Wallet default)");
                return;
            }
            Target target = null;
            for (Target candidate : TARGETS) {
                if (candidate.versionCode == version) target = candidate;
            }
            if (target == null) {
                events.event("Home layout override skipped: unsupported Wallet " + version);
                return;
            }
            Class<?> flags = Class.forName(target.flagClass, false, context.getClassLoader());
            for (String name : target.flagMethods) {
                Method method = flags.getDeclaredMethod(name);
                // 防呆：旗標方法必定是無參數、回傳 boolean
                if (method.getReturnType() != boolean.class) {
                    throw new IllegalStateException("flag method is not boolean: " + name);
                }
                module.hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .setId("zetacard-home-" + target.flagClass + "." + name)
                        .intercept(chain -> Boolean.TRUE);
            }
            events.event("Home layout forced to card carousel (flags 45805255, 45777533)");
        } catch (Throwable error) {
            warnings.warn("home-layout", "Home layout override unavailable", error);
        }
    }
}
