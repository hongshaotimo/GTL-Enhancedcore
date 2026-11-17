package com.gtl.enhancedcore.mixin.jei;

import com.gtl.enhancedcore.client.copy.CopyKeysClient;
import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.input.UserInput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * 按 . 复制 JEI 当前配方的 JSON（移植自 JEI-Copy-Recipe-Json 的核心思路）。
 *
 * <p>注入点选在 JEI 自己的 {@code RecipeLayoutUserInputHandler.handleUserInput}：
 * 这里能同时拿到「被查看的配方对象」（{@link IRecipeLayoutDrawable#getRecipe()}）与
 * 它的注册 ID（{@code IRecipeCategory#getRegistryName}），是唯一可靠的配方入口。
 *
 * <p>与原 mod 的差异：
 * <ul>
 *   <li>原 mod 只在 {@code Internal.getKeyMappings()} 调用点注入并直接吞掉输入；
 *       这里先判断本模组的按键开关与键位是否匹配，不匹配就完全放行，不影响 JEI 原有功能。</li>
 *   <li>配方 JSON 由 {@code RecipeClipboard} 解析：数据包原始文件优先，读不到再实时生成，
 *       因此 GTCEu 代码生成的配方也能复制（原 mod 会静默失败）。</li>
 * </ul>
 */
@Mixin(targets = "mezz.jei.gui.recipes.RecipeLayoutWithButtons$RecipeLayoutUserInputHandler", remap = false)
public abstract class JeiRecipeCopyMixin<R> {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static boolean logged;

    @Shadow
    @Final
    private IRecipeLayoutDrawable<R> recipeLayout;

    @Inject(method = "handleUserInput",
            at = @At(value = "INVOKE",
                    target = "Lmezz/jei/common/Internal;getKeyMappings()Lmezz/jei/common/input/IInternalKeyMappings;"),
            cancellable = true, remap = false, require = 0)
    private void gtlEnhancedcore$copyRecipeJson(Screen screen, UserInput input,
                                                IInternalKeyMappings keyBindings,
                                                CallbackInfoReturnable<Optional<IUserInputHandler>> cir) {
        if (!logged) {
            logged = true;
            LOGGER.info("[GTL-Enhancedcore] 配方复制已挂接 JEI 配方输入处理（按 . 复制 JSON）");
        }
        InputConstants.Key key = input.getKey();
        if (!CopyKeysClient.recipeCopyActive(key)) return;
        // 模拟输入只用于探测，不应产生副作用。
        if (input.isSimulate()) {
            cir.setReturnValue(Optional.of((IUserInputHandler) this));
            return;
        }
        var category = recipeLayout.getRecipeCategory();
        R recipe = recipeLayout.getRecipe();
        if (category == null || recipe == null) return;
        ResourceLocation id = category.getRegistryName(recipe);
        if (id == null) return;
        net.minecraft.world.item.crafting.Recipe<?> syncedRecipe =
                recipe instanceof com.gregtechceu.gtceu.integration.jei.recipe.GTRecipeWrapper wrapper
                        ? wrapper.recipe
                        : recipe instanceof net.minecraft.world.item.crafting.Recipe<?> vanilla ? vanilla : null;
        com.gtl.enhancedcore.client.copy.CopyActions.copyRecipe(id, syncedRecipe);
        cir.setReturnValue(Optional.of((IUserInputHandler) this));
    }
}
