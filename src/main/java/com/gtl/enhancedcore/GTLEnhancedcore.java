package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.machine.GTLEnhancedcoreMachines;
import com.mojang.logging.LogUtils;
import com.gtl.enhancedcore.common.CommonProxy;
import com.gtl.enhancedcore.common.config.ConfigMigration;
import com.gtl.enhancedcore.common.data.machine.InfinitySingularityRecipeLoader;
import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gtl.enhancedcore.common.data.GTLEnhancedcoreItems;
import com.gtl.enhancedcore.common.data.GTLEnhancedcoreRecipeTypes;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import com.gtl.enhancedcore.common.config.GTLConfig;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

/**
 * GTL-Enhancedcore 主类。
 *
 * 命名规则：MOD_ID 固定为 "gtl_enhancedcore" —— 世界存档、方块状态、语言键、模型全部绑定此 ID，
 * 改动会导致旧存档机器全部丢失，2026-07-31 按用户指令无视规则 9 全面改名：MOD_ID/命名空间改为 gtl_enhancedcore，包改为 com.gtl.enhancedcore，旧存档 shcore 机器随之失效。
 *
 * 规则（见 API标准.md「注册时序」）：
 * - 先注册 Registrate 事件监听 → 注册创造标签页 → 监听 GTCEu 机器注册事件 → 事件触发后 init()。
 * - 新机器/物品必须在 BuildCreativeModeTabContentsEvent 中添加创造标签页条目，并在 zh_cn.json 汉化。
 */
@Mod(GTLEnhancedcore.MOD_ID)
public class GTLEnhancedcore {

    public static final String MOD_ID = "gtl_enhancedcore";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final GTRegistrate REGISTRATE = GTRegistrate.create(MOD_ID);

    /** 创造模式标签页（机器通过 BuildCreativeModeTabContentsEvent 事件添加）。 */
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);
    public static final RegistryObject<CreativeModeTab> MAIN_TAB = TABS.register("main", () ->
            CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.gtl_enhancedcore.main"))
                    .icon(() -> new ItemStack(Items.FURNACE))
                    .build()
    );

    public GTLEnhancedcore() {
        // 配置文件统一放在 config/GTL-Enhancedcore/ 目录；Forge 内置配置界面（模组列表 → 配置）可编辑
        // COMMON 类型：文件生成在全局 config/GTL-Enhancedcore/common.toml，主菜单/模组列表配置界面即可编辑
        // （SERVER 类型会存进每个存档的 serverconfig 文件夹，模组列表配置界面打不开）
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, GTLConfig.COMMON_SPEC, "GTL-Enhancedcore/common.toml");
        ConfigMigration.migrateLegacyFiles();
        // 确保无限奇点压缩器的默认配方配置文件存在（不依赖 mixin）
        InfinitySingularityRecipeLoader.ensureDefaultConfig();
        // Register common networking after mod registration.
        CommonProxy.register(FMLJavaModLoadingContext.get().getModEventBus());
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        REGISTRATE.registerEventListeners(bus);
        TABS.register(bus);

        // 物品注册（Registrate）：样板生成工具（gtlcore debug_pattern_test 移植，删冲突分析、加玩家绑定）。
        GTLEnhancedcoreItems.init();

        // 配方类型必须在 GTCEu 配方类型注册事件里注册——等到机器注册事件时
        // gtceu:recipe_type 注册表已冻结，直接注册会崩（2026-07-28 崩溃实证）。
        bus.addGenericListener(GTRecipeType.class,
                (GTCEuAPI.RegisterEvent<ResourceLocation, GTRecipeType> event) -> {
                    LOGGER.debug("GTCEu recipe type register event fired, registering GTL-Enhancedcore recipe types");
                    GTLEnhancedcoreRecipeTypes.init();
                });

        bus.addGenericListener(MachineDefinition.class,
                (GTCEuAPI.RegisterEvent<ResourceLocation, MachineDefinition> event) -> {
                    LOGGER.debug("GTCEu machine register event fired, registering GTL-Enhancedcore machines");
                    GTLEnhancedcoreMachines.init();
                    // 第三方机器的 ADD 可变多配方接入：必须在定义注册后、机器工厂被使用前替换 supplier。
                    com.gtl.enhancedcore.common.registration.AddMutableMachineRegistration.init();
                });

        bus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTab() != MAIN_TAB.get()) return;
            GTLEnhancedcoreMachines.all().forEach(definition -> event.accept(definition.asStack()));
            Item patternGenerator = GTLEnhancedcoreItems.getPatternGenerator();
            if (patternGenerator != null) event.accept(patternGenerator.getDefaultInstance());
        });

        LOGGER.info("GTL-Enhancedcore loaded");
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
