package com.gtl.enhancedcore.client;

import com.gtl.enhancedcore.GTLEnhancedcore;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * AE 终端手动置顶的恢复时机（客户端）。
 *
 * AE2 在 AppEngClient 构造期注册了 ClientPlayerNetworkEvent.LoggingIn → PinnedKeys.clearPinnedKeys()
 * （appeng.core.AppEngClient 反编译实证）。旧实现在 LevelEvent.Load 里恢复，而 ClientLevel 创建早于
 * LoggingIn，恢复出来的置顶随后被 AE2 清空，且我们的 clearPinnedKeys 覆写会同步清掉手动置顶跟踪表，
 * 下一次保存就把持久化文件写成空 —— 表现为「Alt+左键置顶重进游戏后全丢」。
 *
 * 因此改为：登录事件只置标记，等到客户端 tick（此时 AE2 的清空已执行完）再恢复一次。
 */
@Mod.EventBusSubscriber(modid = GTLEnhancedcore.MOD_ID, value = Dist.CLIENT)
public final class AEPinnedKeysClientEvents {

    private static boolean restorePending;

    private AEPinnedKeysClientEvents() {
    }

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        restorePending = true;
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        restorePending = false;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !restorePending) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        restorePending = false;
        AEPinnedKeysStore.restore();
    }
}
