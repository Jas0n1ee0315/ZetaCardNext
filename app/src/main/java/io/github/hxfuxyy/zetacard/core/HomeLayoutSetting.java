package io.github.hxfuxyy.zetacard.core;

/**
 * [ZetaCardNext] 「強制大卡片首頁」設定的共用常數。
 *
 * <p>App 端（{@code ui.HomeLayoutToggle}）寫入、Wallet 端（{@code wallet.HomeLayoutOverride}）讀取。
 * 兩端透過 LSPosed 遠端設定（Remote Preferences）溝通，App 另外存一份本地設定供選單顯示目前狀態。
 */
public final class HomeLayoutSetting {
    /** LSPosed 遠端設定的群組名稱 */
    public static final String REMOTE_GROUP = "settings";
    /** App 本地 SharedPreferences 檔名 */
    public static final String LOCAL_PREFS = "zetacard_home";
    /** 是否強制大卡片首頁（預設 false = 維持 Wallet 原本的首頁） */
    public static final String KEY_BIG_CARDS = "home_big_cards";

    private HomeLayoutSetting() {
    }
}
