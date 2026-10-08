package com.gtl.enhancedcore.client;

import appeng.api.stacks.AEKey;

import java.util.Set;

/**
 * 手动置顶跟踪：区分玩家 Alt+左键手动置顶（需跨终端开关保留）与 AE2 原版
 * 合成任务自动置顶（关闭终端后应被原版 prune 清理）。
 * 纯客户端静态集合，生命周期与 PinnedKeys 一致（退档时随 clearPinnedKeys 清空）。
 */
public final class PinnedKeysTracker {

    private static final Set<AEKey> MANUAL = new java.util.LinkedHashSet<>();

    private PinnedKeysTracker() {
    }

    public static boolean isManual(AEKey key) {
        return MANUAL.contains(key);
    }

    public static void mark(AEKey key) {
        if (key != null) MANUAL.add(key);
    }

    public static void unmark(AEKey key) {
        MANUAL.remove(key);
    }

    /** 淘汰/清理后同步：移除已不在置顶表中的陈旧标记。 */
    public static void retainAll(Set<AEKey> keys) {
        MANUAL.retainAll(keys);
    }

    public static void clear() {
        MANUAL.clear();
    }

    /** 当前手动置顶集合的快照（用于持久化）。 */
    public static java.util.List<AEKey> snapshot() {
        return new java.util.ArrayList<>(MANUAL);
    }
}
