package com.gtl.enhancedcore.mixin.gtlcore.client;

import appeng.api.stacks.AEKey;
import com.gtl.enhancedcore.client.JeiCheatExtractState;
import com.lowdragmc.lowdraglib.LDLib;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.client.event.ScreenEvent;
import org.gtlcore.gtlcore.client.forge.ForgeClientEventListener;
import org.gtlcore.gtlcore.integration.jei.JeiCheatModeCompat;
import org.gtlcore.gtlcore.integration.jei.JeiMeInventoryTooltip;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.util.Optional;

/**
 * JEI 鼠标事件改写（GTL-Enhancedcore）：
 *
 * 1. 2026-08-31：禁用 gtlcore 的 JEI 无线抽取（非作弊分支）—— extendedae_plus
 *    InputEvents 同时监听「Shift+左键 JEI 物品」，且 gtlcore 的事件带
 *    receiveCanceled=true，取消对它无效，导致一次拉取两组。
 * 2. 2026-09-03：保留 gtlcore 的「JEI 作弊模式取物」分支（CommandUtil.giveStack），
 *    此前整对方法置空导致作弊模式无法取出物品（有 OP 权限的服务器环境同样失效）。
 * 3. 2026-09-05：放宽接管条件——作弊总开关开启时：
 *    - 左键：仅当 JEI cheat 键位激活（isCheatStackInputActive(0)，含 Ctrl/Shift 组合）或
 *      按住 Ctrl/Shift 时接管，普通左键保留 JEI 原版（查看配方/用途）；
 *    - 右键：直接接管（服务器环境 JEI 原版右键 give 概率失败，统一走 CommandUtil）。
 *    释放时统一执行 CommandUtil.giveStack；非作弊分支完全放行，
 *    交给 extendedae_plus / JEI 原版，避免无线抽取双抽与右键功能被破坏。
 */
@Mixin(value = ForgeClientEventListener.class, remap = false)
public class ForgeClientEventListenerJeiExtractMixin {

    /**
     * @author GTL-Enhancedcore
     * @reason 只保留作弊取物分支；禁用无线抽取分支，避免与 extendedae_plus 双抽
     */
    @Overwrite(remap = false)
    public static void onJeiWirelessExtractMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != 0 && event.getButton() != 1) {
            return;
        }
        if (!LDLib.isJeiLoaded()) {
            return;
        }
        Optional<AEKey> hovered = JeiMeInventoryTooltip.getHoveredIngredientKey(
                event.getScreen(), event.getMouseX(), event.getMouseY());
        if (hovered.isEmpty()) {
            return;
        }
        if (!JeiCheatExtractState.isCheatModeEnabled()) {
            // 非作弊：不处理，交由 extendedae_plus 无线抽取 / JEI 原版
            return;
        }
        boolean takeOver;
        if (event.getButton() == 0) {
            // 左键：cheat 键位激活（含 Ctrl/Shift 组合）或按住 Ctrl/Shift 时接管
            takeOver = JeiCheatModeCompat.isCheatStackInputActive(0)
                    || Screen.hasControlDown() || Screen.hasShiftDown();
        } else {
            // 右键：作弊总开关开启即接管（服务器环境 JEI 原版 give 概率失败）
            takeOver = true;
        }
        if (!takeOver) {
            return;
        }
        JeiCheatExtractState.setPending(event.getScreen(), hovered.get());
        event.setCanceled(true);
    }

    /**
     * @author GTL-Enhancedcore
     * @reason 只处理作弊取物的释放；无线抽取的释放不再发送任何请求
     */
    @Overwrite(remap = false)
    public static void onJeiWirelessExtractMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (event.getButton() != 0 && event.getButton() != 1) {
            return;
        }
        AEKey key = JeiCheatExtractState.take(event.getScreen());
        if (key != null) {
            event.setCanceled(true);
            JeiCheatModeCompat.executeCheatStackFallback(key);
        }
    }
}
