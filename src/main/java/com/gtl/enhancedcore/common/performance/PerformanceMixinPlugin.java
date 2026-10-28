package com.gtl.enhancedcore.common.performance;

import com.gtl.enhancedcore.common.config.ForeignMachineMixinPolicy;
import com.gtl.enhancedcore.common.config.MachineOverrideConfig;
import com.gtl.enhancedcore.common.integration.GtlTerminalExtras;
import com.gtl.enhancedcore.common.integration.ExPatternTerminalExtras;
import com.gtl.enhancedcore.common.integration.JerMobWorldLaziness;
import com.gtl.enhancedcore.common.integration.MultiblockPreviewLaziness;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class PerformanceMixinPlugin implements IMixinConfigPlugin {
    /** JEI 缺失时不能应用以其内部类为目标的 mixin，否则目标类找不到会报错。 */
    private static final String JEI_RECIPE_COPY = "com.gtl.enhancedcore.mixin.jei.JeiRecipeCopyMixin";
    private static final String GRID_HATCH_MIXIN = "com.gtl.enhancedcore.mixin.gtmadvancedhatch.GridEnergyRecipeInputMixin";
    private static Boolean jeiPresent;

    @Override public void onLoad(String mixinPackage) {
        MachineOverrideConfig.current();
        ClientThreadSettings.configureBackground();
    }
    @Override public String getRefMapperConfig() { return null; }

    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.startsWith("com.gtl.enhancedcore.mixin.extendedae.client."))
            return hasClass(targetClassName);
        if (mixinClassName.equals("com.gtl.enhancedcore.mixin.jade.WeatherAnchorRecipeLogicProviderMixin"))
            return hasClass(targetClassName);
        if (mixinClassName.startsWith("com.gtl.enhancedcore.mixin.ftblibrary.client."))
            return hasClass(targetClassName) && hasClass("dev.ftb.mods.ftbquests.client.gui.quests.ViewQuestPanel") && jeiPresent();
        if (mixinClassName.startsWith("com.gtl.enhancedcore.mixin.ftbquests.client."))
            return hasClass(targetClassName);
        if (!ForeignMachineMixinPolicy.shouldApply(MachineOverrideConfig.current(), mixinClassName)) return false;
        if (mixinClassName.endsWith(".MapRegionPackedImageMixin") || mixinClassName.endsWith(".BloomPendingPruneMixin"))
            return PerformanceMixinPlugin.class.getClassLoader().getResource(targetClassName.replace('.', '/') + ".class") != null;
        if (mixinClassName.startsWith("com.gtl.enhancedcore.mixin.ae2ct.client.")
                || mixinClassName.startsWith("com.gtl.enhancedcore.mixin.jer.client."))
            return PerformanceMixinPlugin.class.getClassLoader().getResource(targetClassName.replace('.', '/') + ".class") != null;
        if (JEI_RECIPE_COPY.equals(mixinClassName)) return jeiPresent();
        if (mixinClassName.startsWith("com.gtl.enhancedcore.mixin.jei.")) return jeiPresent();
        if (GRID_HATCH_MIXIN.equals(mixinClassName))
            return PerformanceMixinPlugin.class.getClassLoader().getResource(targetClassName.replace('.', '/') + ".class") != null;
        if (mixinClassName.startsWith("com.gtl.enhancedcore.mixin.ae2.remote."))
            return PerformanceMixinPlugin.class.getClassLoader().getResource(
                    targetClassName.replace('.', '/') + ".class") != null;
        if (mixinClassName.equals("com.gtl.enhancedcore.mixin.travelanchors.TravelAnchorRangeMixin"))
            return hasClass(targetClassName);
        return true;
    }

    private static boolean hasClass(String className) {
        return PerformanceMixinPlugin.class.getClassLoader().getResource(className.replace('.', '/') + ".class") != null;
    }

    private static boolean jeiPresent() {
        if (jeiPresent == null) {
            try {
                Class.forName("mezz.jei.gui.recipes.RecipeLayoutWithButtons", false,
                        PerformanceMixinPlugin.class.getClassLoader());
                jeiPresent = true;
            } catch (ClassNotFoundException | LinkageError absent) {
                jeiPresent = false;
            }
        }
        return jeiPresent;
    }

    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        if (targetClassName.equals("net.minecraft.Util")) ClientThreadSettings.configureBackground();
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        GtlTerminalExtras.postApply(targetClassName, targetClass, mixinClassName);
        ExPatternTerminalExtras.postApply(targetClassName, targetClass, mixinClassName);
        MultiblockPreviewLaziness.postApply(targetClassName, targetClass, mixinClassName);
        JerMobWorldLaziness.postApply(targetClassName, targetClass, mixinClassName);
    }
}
