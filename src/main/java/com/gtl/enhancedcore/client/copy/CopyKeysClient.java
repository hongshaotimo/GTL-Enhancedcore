package com.gtl.enhancedcore.client.copy;

import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.client.config.CopyKeysConfig;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * 「按 0 复制物品/流体 ID」与「按 . 复制配方」的客户端入口。
 *
 * <p>按键默认绑定：0 复制 ID（物品优先，无物品时取流体）、. 复制配方。
 * 两者都注册在「GTL-Enhancedcore」分类下，玩家可在原版按键设置里改键。
 *
 * <p>实现要点（对照原 mod JEI-Copy-Recipe-Json）：
 * <ul>
 *   <li>原 mod 用 {@code InputConstants.Key(-1)} 无默认键，且只认 tooltip 里的物品；
 *       这里给 0 与 . 具体默认键，并**同时缓存物品与流体**，因此流体会被复制。</li>
 *   <li>按键处理走 {@link ScreenEvent.KeyPressed.Pre}，与界面无关，容器/JEI 界面都可触发。</li>
 * </ul>
 */
public final class CopyKeysClient {
    /** 复制物品/流体 ID 的按键（默认 0）。 */
    public static final KeyMapping COPY_ID = new KeyMapping(
            "key.gtl_enhancedcore.copy_id", GLFW.GLFW_KEY_0, "key.categories.gtl_enhancedcore");
    /** 复制配方的按键（默认 .）。 */
    public static final KeyMapping COPY_RECIPE = new KeyMapping(
            "key.gtl_enhancedcore.copy_recipe", GLFW.GLFW_KEY_PERIOD, "key.categories.gtl_enhancedcore");

    /** 最近一次 tooltip 看到的物品（tooltip 事件只在此处提供悬停对象）。 */
    private static ItemStack hoveredItem = ItemStack.EMPTY;
    private static int itemTicks;
    /** 最近一次 tooltip 看到的流体。 */
    private static FluidStack hoveredFluid = FluidStack.EMPTY;
    private static int fluidTicks;

    private CopyKeysClient() {}

    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(COPY_ID);
        event.register(COPY_RECIPE);
        // 正向证据：确认按键真的注册进去了（默认 0 与 .）。
        com.mojang.logging.LogUtils.getLogger().info(
                "[GTL-Enhancedcore] 复制按键已注册：0=复制物品/流体 ID，.=复制配方 JSON");
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.register(CopyKeysClient.class);
        // 命令要注册在 Forge 事件总线（RegisterCommandsEvent 走 bus，不是 mod bus）。
        MinecraftForge.EVENT_BUS.register(CopyKeysCommand.class);
    }

    /** 缓存悬停物品；3 tick 内有效，避免 tooltip 消失后仍复制到旧对象。 */
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        if (event.getItemStack().isEmpty()) return;
        hoveredItem = event.getItemStack();
        itemTicks = 3;
    }

    /**
     * 缓存悬停流体。GTCEu/Forge 的流体 tooltip 最终也会走 ItemTooltipEvent（流体桶），
     * 但直接悬停流体（如 JEI 流体槽）不会；这里额外从当前界面取。
     */
    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        FluidStack stack = CopyHoverLookup.fluidUnderMouse(event.getScreen());
        if (stack != null && !stack.isEmpty()) {
            hoveredFluid = stack;
            fluidTicks = 3;
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        CopyKeysConfig.tick();
        if (itemTicks > 0 && --itemTicks == 0) hoveredItem = ItemStack.EMPTY;
        if (fluidTicks > 0 && --fluidTicks == 0) hoveredFluid = FluidStack.EMPTY;
    }

    @SubscribeEvent
    public static void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        InputConstants.Key key = InputConstants.getKey(event.getKeyCode(), event.getScanCode());
        if (COPY_ID.isActiveAndMatches(key)) {
            handleCopyId();
            return;
        }
        // 配方复制由 JEI mixin 处理（需要配方对象）；这里只兜底关闭开关时不拦截。
        if (COPY_RECIPE.isActiveAndMatches(key) && !CopyKeysConfig.copyRecipe()) {
            return;
        }
    }

    /** 物品优先，其次流体；两者都开时按「当前悬停的是什么」决定。 */
    private static void handleCopyId() {
        boolean allowItem = CopyKeysConfig.copyItemId();
        boolean allowFluid = CopyKeysConfig.copyFluidId();
        if (!allowItem && !allowFluid) return;
        if (allowItem && !hoveredItem.isEmpty()) {
            CopyActions.copyItem(hoveredItem);
            return;
        }
        if (allowFluid && !hoveredFluid.isEmpty()) {
            CopyActions.copyFluid(hoveredFluid);
            return;
        }
    }

    /** 供 JEI mixin 判断是否应接管配方复制。 */
    public static boolean recipeCopyActive(InputConstants.Key key) {
        return CopyKeysConfig.copyRecipe() && COPY_RECIPE.isActiveAndMatches(key);
    }

    /** 当前悬停物品，便于诊断。 */
    @Nullable
    public static ItemStack hoveredItem() {
        return hoveredItem.isEmpty() ? null : hoveredItem;
    }

    @Nullable
    public static FluidStack hoveredFluid() {
        return hoveredFluid.isEmpty() ? null : hoveredFluid;
    }

    /** 引用一次 MOD_ID，避免仅用于日志时被判定未使用。 */
    static String modId() {
        return GTLEnhancedcore.MOD_ID;
    }
}
