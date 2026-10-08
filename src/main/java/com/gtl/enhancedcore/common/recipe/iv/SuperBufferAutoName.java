package com.gtl.enhancedcore.common.recipe.iv;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * 超级样板总成“重命名样板总成”自动命名策略。
 *
 * <h2>命名规则</h2>
 * 总成在机器上的位置排第几，就叫机器配方类型表里的第几个名字。
 * 例如等离子机床配方表是 [车床, 卷板机, 压缩机, …]，排第 1 的总成叫“车床”、
 * 第 2 个叫“卷板机”、第 3 个叫“压缩机”……超出配方类型数量的总成留空。
 *
 * <p>自动名称使用独立中文词典并保存为中文，不依赖服务端或客户端的当前语言。
 * 自动命名只填空名；重新成型不会改写已经存在的名字（包括此前自动生成的名字）。
 * 玩家可通过总成界面的“恢复自动命名”按钮主动重新命名。
 */
public final class SuperBufferAutoName {

    private SuperBufferAutoName() {}

    /** 从注册名得到 GTCEu 使用的语言键，如 gtceu:compressor → gtceu.compressor。 */
    public static String languageKey(ResourceLocation recipeTypeId) {
        return recipeTypeId == null ? "" : recipeTypeId.toLanguageKey();
    }

    /** 取机器配方类型表的语言键列表，顺序即命名顺序。 */
    public static List<String> languageKeys(Iterable<ResourceLocation> recipeTypeIds) {
        List<String> keys = new ArrayList<>();
        if (recipeTypeIds == null) return keys;
        for (ResourceLocation id : recipeTypeIds) {
            if (id != null) keys.add(id.toLanguageKey());
        }
        return keys;
    }

    /** 按位置序号取语言键：第 index 个总成取配方类型表第 index 项；越界返回空串（留空）。 */
    public static String keyForIndex(int index, List<String> recipeTypeLangKeys) {
        if (index < 0 || recipeTypeLangKeys == null || index >= recipeTypeLangKeys.size()) return "";
        String key = recipeTypeLangKeys.get(index);
        return key == null ? "" : key;
    }

    /** Formation only fills truly empty, non-manual names. Keep the former ownership argument for callers. */
    public static boolean shouldWrite(String current, String targetKey, String lastAutomatic, boolean manual) {
        return !manual && targetKey != null && !targetKey.isEmpty()
                && (current == null || current.isEmpty());
    }

    public static boolean shouldTranslate(String current, String lastAutomatic, boolean manual) {
        return !manual && current != null && !current.isEmpty() && current.equals(lastAutomatic);
    }

    /** 该字符串是否是本机配方类型表里的语言键。 */
    public static boolean isRecipeTypeKey(String stored, Collection<String> recipeTypeLangKeys) {
        return stored != null && !stored.isEmpty()
                && recipeTypeLangKeys != null && recipeTypeLangKeys.contains(stored);
    }

    /** Only call for proven automatic names; includes pre-2.6.7 language keys. */
    public static String displayName(String stored) {
        return SuperBufferChineseNames.resolve(stored);
    }

    /** 只有独占单一控制器的总成才自动命名（共享总成跳过）。 */
    public static boolean exclusive(int controllerCount) {
        return controllerCount == 1;
    }

    /**
     * 位置键：把方块坐标打包成可比较的 long，用来确定“第几个”。
     *
     * <p>必须用坐标而非遍历下标：GTCEu 的 {@code MultiblockMachineDefinition.partSorter}
     * 默认是 {@code null}，{@code getParts()} 顺序不保证稳定，用下标会导致名字在重启后互换。
     */
    public static long positionKey(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (y & 0xFFF) << 26) | (z & 0x3FFFFFF);
    }
}
