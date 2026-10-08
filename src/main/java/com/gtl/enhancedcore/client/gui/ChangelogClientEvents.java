package com.gtl.enhancedcore.client.gui;

import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.config.ChangelogConfig;

import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 更新公告的弹出时机（GTL-Enhancedcore，2026-09-02）。
 *
 * 在主菜单（TitleScreen）初始化完成后弹一次。用 ScreenEvent.Init.Post 而不是 mod 构造期，
 * 是因为此时 Minecraft 实例与字体已就绪，且能拿到主菜单作为 parent（关闭后回到主菜单）。
 *
 * 每次游戏进程只弹一次（{@code shownThisLaunch}）：主菜单可能被反复重建
 * （从世界退回、切换语言/资源包重载都会重新 init），不加这个标记会反复弹窗。
 */
@Mod.EventBusSubscriber(modid = GTLEnhancedcore.MOD_ID, value = Dist.CLIENT)
public final class ChangelogClientEvents {

    private static boolean shownThisLaunch;

    private ChangelogClientEvents() {
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (shownThisLaunch || !(event.getScreen() instanceof TitleScreen titleScreen)) {
            return;
        }
        if (!ChangelogConfig.shouldShow()) {
            // 关闭/已选"不再提示"时也置标记，避免每次重建主菜单都重复读盘。
            shownThisLaunch = true;
            return;
        }
        shownThisLaunch = true;
        ChangelogScreen.open(titleScreen);
    }
}
