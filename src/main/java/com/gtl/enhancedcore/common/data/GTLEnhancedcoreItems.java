package com.gtl.enhancedcore.common.data;

import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.item.PatternGeneratorBehavior;
import com.gtl.enhancedcore.common.machine.LargeFurnaceMachine;
import com.gregtechceu.gtceu.api.item.ComponentItem;
import com.gregtechceu.gtceu.api.item.component.IAddInformation;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

/**
 * GTL-Enhancedcore 物品注册（2026-08-16；样板生成工具 2026-08-19 更名 + 加 Tips + 配方类型补写）。
 * 注册时机：GTLEnhancedcore 构造中 REGISTRATE.registerEventListeners 之后调用 init()。
 * 新物品必须在 GTLEnhancedcore.BuildCreativeModeTabContentsEvent 添加创造标签页条目并双语汉化。
 */
public final class GTLEnhancedcoreItems {

    /** 样板生成工具：gtlcore debug_pattern_test / 山海样板调试工具“生成”模块移植版（删分析、生成直接进背包）。 */
    public static ItemEntry<ComponentItem> PATTERN_GENERATOR;

    private GTLEnhancedcoreItems() {
    }

    public static void init() {
        // 注意：禁止用 GTItems.attach 挂组件——它会在 mod 构造期触发 GTCEu GTItems 类静态初始化，
        // 其链上 GTToolType.<clinit>（gtlcore fix10 GTToolTypeMixin）注册 gtceu:sound 时 registry 已冻结导致崩溃（2026-08-16 实证）。
        // 改在 onRegister 回调里直接调用 ComponentItem.attachComponents，不引用 GTItems 类。
        PATTERN_GENERATOR = GTLEnhancedcore.REGISTRATE
                .item("pattern_generator", ComponentItem::create)
                .onRegister(item -> item.attachComponents(
                        PatternGeneratorBehavior.INSTANCE,
                        (IAddInformation) (stack, level, lines, flag) -> {
                            for (String line : java.util.List.of("open", "choose", "generate", "fill", "presets")) {
                                lines.add(Component.translatable("tooltip.gtl_enhancedcore.pattern_generator." + line)
                                        .withStyle(ChatFormatting.GRAY));
                            }
                            lines.add(LargeFurnaceMachine.getCreditLine());
                        }))
                .model(com.tterrag.registrate.util.nullness.NonNullBiConsumer.noop())
                .register();
    }

    public static Item getPatternGenerator() {
        return PATTERN_GENERATOR.get();
    }
}
