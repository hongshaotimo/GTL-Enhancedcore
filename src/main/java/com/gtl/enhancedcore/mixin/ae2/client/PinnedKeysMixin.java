package com.gtl.enhancedcore.mixin.ae2.client;

import appeng.api.stacks.AEKey;
import appeng.client.gui.me.common.PinnedKeys;
import com.gtl.enhancedcore.client.PinnedKeysTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Overwrite;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;

/**
 * 修复 AE2 原版 PinnedKeys.pinKey 的淘汰 bug：原实现 subList(0, 9 - toRemove.size()) 在放入第 10 个
 * 键时结束下标为负数，直接 IndexOutOfBoundsException（appeng.client.gui.me.common.PinnedKeys 反编译实证）。
 * 本覆写改为 subList(MAX_PINNED, size) 淘汰最旧的超出部分；上限与 AE2 保持一致的 9
 * （Repo 的置顶行受 rowSize 限制，默认 9，超出一行的置顶在界面上不会被渲染）。
 */
@Mixin(value = PinnedKeys.class, remap = false)
public abstract class PinnedKeysMixin {
    private static final int MAX_PINNED = 9;

    @Shadow(remap = false)
    private static Map<AEKey, PinnedKeys.PinInfo> pinned;

    @Shadow(remap = false)
    private static Comparator<Map.Entry<AEKey, PinnedKeys.PinInfo>> TIME_COMPARATOR;

    /**
     * @author GTL-Enhancedcore
     * @reason 修复 AE2 原版 subList(0, 9 - toRemove.size()) 在第 10 个键时负下标抛 IndexOutOfBoundsException
     */
    @Overwrite(remap = false)
    public static void pinKey(AEKey key, PinnedKeys.PinReason reason) {
        PinnedKeys.PinInfo info = pinned.get(key);
        if (info != null) {
            info.since = Instant.now();
        } else {
            pinned.put(key, new PinnedKeys.PinInfo(reason));
        }
        if (pinned.size() > MAX_PINNED) {
            ArrayList<Map.Entry<AEKey, PinnedKeys.PinInfo>> toRemove = new ArrayList<>(pinned.entrySet());
            toRemove.sort(TIME_COMPARATOR);
            for (Map.Entry<AEKey, PinnedKeys.PinInfo> entry : toRemove.subList(MAX_PINNED, toRemove.size())) {
                pinned.remove(entry.getKey());
            }
        }
    }

    /**
     * AE2 原版在关闭终端时把无合成任务的 CRAFTING 置顶标记为可剪除，并在下一 tick（无界面时）
     * 调用 prune() 清空，导致 Alt+左键手动置顶在重开终端后丢失（2026-08-03 实证：MEStorageScreen.onClose
     * 置 canPrune=true + AppEngClient.tickPinnedKeys 在 screen==null 时 prune）。
     * 覆写为：只清理原版合成任务自动置顶（不在手动置顶跟踪表内），玩家 Alt+左键手动置顶保留。
     *
     * @author GTL-Enhancedcore
     * @reason 保留玩家手动置顶，只清理原版合成任务的自动置顶
     */
    @Overwrite(remap = false)
    public static void prune() {
        PinnedKeysTracker.retainAll(pinned.keySet());
        pinned.entrySet().removeIf(e -> e.getValue().canPrune && !PinnedKeysTracker.isManual(e.getKey()));
    }

    /**
     * 退档（世界卸载）时原版会清空全部置顶，手动置顶跟踪表同步清空，避免陈旧标记残留。
     *
     * @author GTL-Enhancedcore
     * @reason 清空置顶时同步清空手动置顶跟踪表
     */
    @Overwrite(remap = false)
    public static void clearPinnedKeys() {
        pinned.clear();
        PinnedKeysTracker.clear();
    }
}
