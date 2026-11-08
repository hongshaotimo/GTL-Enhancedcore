package com.gtl.enhancedcore.common.integration;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.ClassInfo;

/** Lets GTLCore's read-only NBT redirect compose with GTLAdditions' tooltip overwrite. */
public final class DataItemBehaviorCompatibility {
    public static final String MARKER =
            "com.gtl.enhancedcore.mixin.gtceu.DataItemBehaviorCompatibilityMixin";
    private static final String TARGET = "com.gregtechceu.gtceu.common.item.DataItemBehavior";
    private static final String CORE = "org.gtlcore.gtlcore.mixin.gtm.item.DataItemBehaviorMixin";
    private static final String ADD = "com.gtladd.gtladditions.mixin.gtceu.common.item.DataItemBehaviorMixin";
    private static final String READER = "(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/nbt/CompoundTag;";
    private static final String TOOLTIP = "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;"
            + "Ljava/util/List;Lnet/minecraft/world/item/TooltipFlag;)V";

    private DataItemBehaviorCompatibility() {}

    public static void preApply(String target, ClassNode node, String mixin) {
        if (!MARKER.equals(mixin)) return;
        if (!TARGET.equals(target) || !TARGET.replace('.', '/').equals(node.name))
            throw new IllegalStateException("Unexpected data-item compatibility target: " + target);
        try {
            // In Mixin 0.8.5 all plugin preApply callbacks run before MAIN and PREINJECT.
            // The contexts already exist, but MixinTargetContext.getPriority reads MixinInfo live.
            Method getMixins = ClassInfo.class.getDeclaredMethod("getMixins");
            getMixins.setAccessible(true);
            Set<?> mixins = (Set<?>) getMixins.invoke(ClassInfo.forName(target));
            IMixinInfo core = null;
            IMixinInfo add = null;
            for (Object value : mixins) {
                IMixinInfo info = (IMixinInfo) value;
                if (CORE.equals(info.getClassName())) core = info;
                if (ADD.equals(info.getClassName())) add = info;
            }
            // Older GTLCore has no redirect; installations without ADD need no adjustment.
            if (core == null || add == null || core.getPriority() > add.getPriority()) return;
            ClassNode coreNode = core.getClassNode(0);
            MethodNode reader = coreNode.methods.stream()
                    .filter(method -> method.name.equals("gtlcore$readTooltipData") && method.desc.equals(READER))
                    .findFirst().orElseThrow(() -> new IllegalStateException("GTLCore data-item reader changed"));
            if (reader.visibleAnnotations == null || reader.visibleAnnotations.stream().noneMatch(annotation ->
                    annotation.desc.equals("Lorg/spongepowered/asm/mixin/injection/Redirect;")))
                throw new IllegalStateException("GTLCore data-item reader is no longer a redirect");
            if (coreNode.methods.stream().anyMatch(method -> !method.name.equals("<init>") && method != reader))
                throw new IllegalStateException("GTLCore data-item mixin has additional behavior");
            boolean overwrite = add.getClassNode(0).methods.stream().anyMatch(method ->
                    method.name.equals("appendHoverText") && method.desc.equals(TOOLTIP)
                            && method.visibleAnnotations != null && method.visibleAnnotations.stream().anyMatch(annotation ->
                            annotation.desc.equals("Lorg/spongepowered/asm/mixin/Overwrite;")));
            if (!overwrite) throw new IllegalStateException("GTLAdditions data-item tooltip overwrite changed");
            if (core.getPriority() != 1000 || add.getPriority() != 1000)
                throw new IllegalStateException("Unexpected GTLCore/GTLAdditions data-item priorities");
            Field priority = core.getClass().getDeclaredField("priority");
            priority.setAccessible(true);
            // Change this one injector mixin, preserving ADD's complete method and Core's handler.
            priority.setInt(core, 1100);
            if (core.getPriority() != 1100) throw new IllegalStateException("Data-item priority did not update");
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unsupported Mixin data-item compatibility API", exception);
        }
    }
}
