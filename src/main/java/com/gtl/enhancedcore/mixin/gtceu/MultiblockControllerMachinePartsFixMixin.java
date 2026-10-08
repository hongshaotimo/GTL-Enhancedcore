package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.pattern.util.PatternMatchContext;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Set;
import java.util.function.Supplier;

/**
 * GTCEu 1.4.4 结构成型时 "parts" 容器类型修复。
 *
 * @author GTL-Enhancedcore
 * @reason 原版 onStructureFormed 用 {@code Collections::emptySet} 创建 "parts"，而 gtlcore 的
 *         BlockPatternMixin 在结构检查时把它强转为 ObjectOpenHashSet；一旦 "parts" 之前不存在，
 *         EmptySet 被存入后再检查就会 ClassCastException（日志实证：
 *         "Collections$EmptySet cannot be cast to ObjectOpenHashSet"），异常经
 *         checkPatternWithTryLock 泄漏锁导致死锁。这里把创建类型统一为 ObjectOpenHashSet。
 */
@Mixin(value = MultiblockControllerMachine.class, remap = false)
public abstract class MultiblockControllerMachinePartsFixMixin {

    @Redirect(method = "onStructureFormed",
            at = @At(value = "INVOKE",
                    target = "Lcom/gregtechceu/gtceu/api/pattern/util/PatternMatchContext;getOrCreate(Ljava/lang/String;Ljava/util/function/Supplier;)Ljava/lang/Object;",
                    remap = false),
            remap = false)
    private Object gtlcore$partsAsObjectOpenHashSet(PatternMatchContext context, String key, Supplier<?> supplier) {
        return "parts".equals(key)
                ? context.getOrCreate(key, (Supplier<Set<IMultiPart>>) ObjectOpenHashSet::new)
                : context.getOrCreate(key, supplier);
    }
}
