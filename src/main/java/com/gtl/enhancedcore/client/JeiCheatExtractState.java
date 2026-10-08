package com.gtl.enhancedcore.client;

import appeng.api.stacks.AEKey;
import net.minecraft.client.gui.screens.Screen;

/**
 * JEI 作弊模式取物的客户端挂起状态（GTL-Enhancedcore）。
 *
 * gtlcore 原版把「作弊模式给物」与「AE 无线抽取」都放在同一对鼠标事件里，
 * 并用私有 record PendingJeiExtraction 记录挂起状态。本模组在 mixin 中禁用
 * 无线抽取分支（2026-08-31 双抽修复）时无法引用其私有 record，故由本类在
 * mixin 包之外独立维护作弊分支的挂起状态（规则 19：mixin 引用的辅助类不得
 * 放在 mixin 包内）。
 */
public final class JeiCheatExtractState {

    private static Screen pendingScreen;
    private static AEKey pendingKey;

    private JeiCheatExtractState() {
    }

    public static void setPending(Screen screen, AEKey key) {
        pendingScreen = screen;
        pendingKey = key;
    }

    /**
     * JEI 作弊模式总开关（反射：mezz.jei.common.Internal.getClientToggleState().isCheatItemsEnabled()）。
     * gtlcore 的 {@code isCheatStackInputActive(0)} 还要求按键映射匹配鼠标左键，无法覆盖右键；
     * 本方法只看总开关，供左键/右键统一接管判断（2026-09-05）。
     */
    public static boolean isCheatModeEnabled() {
        try {
            Class<?> internal = Class.forName("mezz.jei.common.Internal");
            Object toggleState = internal.getMethod("getClientToggleState").invoke(null);
            if (toggleState == null) {
                return false;
            }
            return Boolean.TRUE.equals(toggleState.getClass().getMethod("isCheatItemsEnabled").invoke(toggleState));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /** 取出并清除与指定屏幕匹配的挂起取物；屏幕不匹配时返回 null（同时清除陈旧状态）。 */
    public static AEKey take(Screen screen) {
        AEKey key = null;
        if (pendingScreen != null && pendingScreen == screen && pendingKey != null) {
            key = pendingKey;
        }
        pendingScreen = null;
        pendingKey = null;
        return key;
    }
}
