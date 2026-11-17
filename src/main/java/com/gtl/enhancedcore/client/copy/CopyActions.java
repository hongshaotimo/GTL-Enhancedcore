package com.gtl.enhancedcore.client.copy;

import com.gtl.enhancedcore.client.config.CopyKeysConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * 「按 0 复制 ID」与「按 . 复制配方」的共享剪贴板动作与提示。
 *
 * <p>物品与流体都支持：悬停时由 {@link CopyKeysClient} 记录最近一次 tooltip 看到的物品/流体，
 * 按键时取出对应 ID，包成带引号的 JSON 片段写入剪贴板并回显提示。
 */
public final class CopyActions {
    private CopyActions() {}

    /** 复制悬停物品 ID：输出 {@code "gtceu:iron_ingot"}。 */
    public static boolean copyItem(@Nullable ItemStack stack) {
        ResourceLocation id = RecipeClipboard.itemId(stack);
        if (id == null) return false;
        return copy(RecipeClipboard.quotedId(id), "gtl_enhancedcore.copy.item", id.toString());
    }

    /** 复制悬停流体 ID：输出 {@code "gtceu:water"}（原 mod 不支持，本模组新增）。 */
    public static boolean copyFluid(@Nullable FluidStack stack) {
        ResourceLocation id = RecipeClipboard.fluidId(stack);
        if (id == null) return false;
        return copy(RecipeClipboard.quotedId(id), "gtl_enhancedcore.copy.fluid", id.toString());
    }

    /** 复制配方的 JSON 文本。 */
    public static boolean copyRecipe(@Nullable ResourceLocation recipeId) {
        return copyRecipe(recipeId, null);
    }

    /** JEI already owns the synced recipe, including when connected to a dedicated server. */
    public static boolean copyRecipe(@Nullable ResourceLocation recipeId,
                                     @Nullable net.minecraft.world.item.crafting.Recipe<?> recipe) {
        if (recipeId == null) return false;
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (recipe == null && Minecraft.getInstance().level != null)
            recipe = Minecraft.getInstance().level.getRecipeManager().byKey(recipeId).orElse(null);
        String json = RecipeClipboard.toJson(server, recipeId, recipe);
        if (json == null) {
            message(Component.translatable("gtl_enhancedcore.copy.recipe.failed", recipeId.toString()));
            return false;
        }
        return copy(json, "gtl_enhancedcore.copy.recipe", recipeId.toString());
    }

    private static boolean copy(String text, String messageKey, String display) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.keyboardHandler == null) return false;
        minecraft.keyboardHandler.setClipboard(text);
        message(Component.translatable(messageKey, display));
        return true;
    }

    private static void message(Component component) {
        var player = Minecraft.getInstance().player;
        if (player != null) player.displayClientMessage(component, false);
    }

    /** 开关全部关闭时不拦截按键，交回原版/JEI 处理。 */
    public static boolean itemCopyEnabled() {
        return CopyKeysConfig.copyItemId() || CopyKeysConfig.copyFluidId();
    }
}
