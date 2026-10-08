package com.gtl.enhancedcore.mixin.gtceu;

import com.google.common.primitives.Ints;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gtl.enhancedcore.common.recipe.iv.*;
import com.gtladd.gtladditions.api.machine.IThreadModifierMachine;
import com.gtladd.gtladditions.api.machine.logic.MutableRecipesLogic;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;
import org.gtlcore.gtlcore.common.machine.trait.MultipleRecipesLogic;
import com.lowdragmc.lowdraglib.gui.widget.*;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value = WorkableElectricMultiblockMachine.class, remap = false)
public abstract class IvNativeMachineUiMixin implements com.gregtechceu.gtceu.api.machine.feature.IFancyUIMachine {
    /**
     * 侧栏装配。
     * <p>
     * <b>不要在这里调用 {@code IFancyUIMachine.super.attachSideTabs(...)}</b>：本 mixin 把接口默认方法
     * 变成目标类的**具体方法**后，那条 super 调用会被编译成 InterfaceMethodref，
     * 而 JVM 在具体方法体内只接受 Methodref 常量，运行期直接
     * {@code IncompatibleClassChangeError: ... must be Methodref constant}（2026-09-26 实测崩溃）。
     * 因此这里**逐句内联**接口默认实现。字节码实证的原始逻辑：
     * <pre>
     *   tabs.setMainTab(this);
     *   if (this instanceof IRecipeLogicMachine m &amp;&amp; m.getRecipeTypes().length > 1)
     *       tabs.attachSubTab(new MachineModeFancyConfigurator(m));
     *   var d = CombinedDirectionalFancyConfigurator.of(self(), self());
     *   if (d != null) tabs.attachSubTab(d);
     * </pre>
     * 隔离目标（铂系精炼矩阵等）只保留主标签 + 朝向配置页，与既有行为一致。
     */
    @Override public void attachSideTabs(com.gregtechceu.gtceu.api.gui.fancy.TabsWidget tabs) {
        tabs.setMainTab(this);
        if (!IvMachineScope.crossRecipeEnabled(iv$self())) {
            if (iv$self() instanceof com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine logicMachine
                    && logicMachine.getRecipeTypes().length > 1) {
                tabs.attachSubTab(new com.gregtechceu.gtceu.api.machine.fancyconfigurator.MachineModeFancyConfigurator(logicMachine));
            }
            var plainDirectional = com.gregtechceu.gtceu.api.machine.fancyconfigurator.CombinedDirectionalFancyConfigurator.of(iv$self(), iv$self());
            if (plainDirectional != null) tabs.attachSubTab(plainDirectional);
            return;
        }
        var directional = com.gregtechceu.gtceu.api.machine.fancyconfigurator.CombinedDirectionalFancyConfigurator.of(iv$self(), iv$self());
        if (directional != null) tabs.attachSubTab(directional);
    }
    @Unique private WorkableElectricMultiblockMachine iv$self() { return (WorkableElectricMultiblockMachine)(Object)this; }
    @Unique private List<MEPatternBufferPartMachine> iv$buffers() {
        return IvBuffers.collect(iv$self()).stream().filter(IvBuffers::isolated).toList();
    }
    @Unique private boolean iv$accepting() {
        var buffers = iv$buffers();
        return !buffers.isEmpty() && buffers.stream().allMatch(buffer -> IvBuffers.state(buffer).accepting);
    }
    @Inject(method = "createUIWidget", at = @At("RETURN"), cancellable = true)
    private void iv$buttons(CallbackInfoReturnable<Widget> cir) {
        // Custom controllers add their own buttons after this parent UI returns.
        if (!IvMachineScope.nativeTarget(iv$self())
                || !IvMachineScope.crossRecipeEnabled(iv$self())) return;
        var original = cir.getReturnValue();
        int width = original.getSizeWidth(), height = original.getSizeHeight();
        var group = new WidgetGroup(0, 0, width, height + 44); group.addWidget(original);
        group.addWidget(new IvActionButton(4, height+2, width-8, 18,
                () -> iv$buffers().stream().anyMatch(buffer -> !IvBuffers.state(buffer).jobs.isEmpty()), () -> false,
                "gtl_enhancedcore.gui.iv_cancel_machine", "gtl_enhancedcore.gui.iv_cancel_machine", "gtl_enhancedcore.gui.iv_cancel_help",
                false, () -> iv$buffers().forEach(buffer -> IvCancellation.cancel(buffer, -1))));
        group.addWidget(new IvActionButton(4, height+23, width-8, 18, () -> !iv$buffers().isEmpty(), this::iv$accepting,
                "gtl_enhancedcore.gui.iv_admission_on", "gtl_enhancedcore.gui.iv_admission_off", "gtl_enhancedcore.gui.iv_admission_help",
                true, () -> { boolean accepting = !iv$accepting(); iv$buffers().forEach(buffer -> IvCancellation.setAccepting(buffer, accepting)); }));
        cir.setReturnValue(group);
    }
    @Unique private static String iv$key(Component line) {
        return line.getContents() instanceof TranslatableContents translated ? translated.getKey() : "";
    }
    /** Replace only known upstream rows, preserving their position and every machine-specific display line. */
    @Unique private static void iv$canonicalLine(List<Component> text, String key, Component replacement) {
        boolean found = false;
        for (int i = 0; i < text.size();) {
            if (!key.equals(iv$key(text.get(i)))) {
                i++;
            } else if (!found && replacement != null) {
                text.set(i++, replacement);
                found = true;
            } else {
                text.remove(i);
            }
        }
        if (!found && replacement != null) {
            int insertAt = text.size();
            if (key.equals("gtladditions.multiblock.threads")) {
                boolean anchored = false;
                for (String anchor : List.of("gtceu.multiblock.parallel", "gtceu.gui.machinemode",
                        "gtceu.multiblock.max_recipe_tier")) {
                    for (int i = 0; i < text.size(); i++) {
                        if (anchor.equals(iv$key(text.get(i)))) {
                            insertAt = i + 1;
                            anchored = true;
                            break;
                        }
                    }
                    if (anchored) break;
                }
            }
            text.add(insertAt, replacement);
        }
    }
    @Unique private static Component iv$threadLine(int threads) {
        return Component.translatable("gtladditions.multiblock.threads",
                Component.literal(FormattingUtil.formatNumbers(threads)).withStyle(ChatFormatting.GOLD))
                .withStyle(ChatFormatting.GRAY);
    }
    @Unique private static int iv$plainBatchThreads(int additionalThreads) {
        return Ints.saturatedCast((long)IvMachineScope.UPSTREAM_MULTIPLE_RECIPE_THREADS + additionalThreads);
    }
    /** Match the ordinary-mode branches of GTLAdditions' ParallelProviderMixin. */
    @Unique private static int iv$plainThreads(WorkableElectricMultiblockMachine machine) {
        var logic = machine.getRecipeLogic();
        if (logic instanceof MultipleRecipesLogic && machine instanceof IThreadModifierMachine modifier)
            return iv$plainBatchThreads(modifier.getAdditionalThread());
        if (logic instanceof MutableRecipesLogic<?> mutable && mutable.isMultipleRecipeMode())
            return mutable.getMultipleThreads();
        return 0;
    }
    @Inject(method = "addDisplayText", at = @At("TAIL"))
    private void iv$display(List<Component> text, CallbackInfo ci) {
        var machine = iv$self();
        if (!IvMachineScope.nativeTarget(machine) || !machine.isFormed()) return;
        if (!IvMachineScope.crossRecipeEnabled(machine)) {
            int threads = iv$plainThreads(machine);
            iv$canonicalLine(text, "gtladditions.multiblock.threads", threads > 1 ? iv$threadLine(threads) : null);
            return;
        }
        // The GTCEu method has already added its energy tier, status, and registration-specific diagnostics.
        iv$canonicalLine(text, "gtceu.gui.machinemode",
                Component.translatable("gtceu.gui.machinemode", Component.translatable("gtl_enhancedcore.gui.iv_mode"))
                        .withStyle(ChatFormatting.AQUA));
        int parallel = IvMachineScope.parallel(machine);
        iv$canonicalLine(text, "gtceu.multiblock.parallel", parallel > 1
                ? Component.translatable("gtceu.multiblock.parallel",
                        Component.literal(FormattingUtil.formatNumbers(parallel)).withStyle(ChatFormatting.DARK_PURPLE))
                        .withStyle(ChatFormatting.GRAY) : null);
        int threads = IvMachineScope.threads(machine);
        iv$canonicalLine(text, "gtladditions.multiblock.threads", threads > 1 ? iv$threadLine(threads) : null);
        IvPresentation.append(text, ((IvNativeAccess)machine.getRecipeLogic()).iv$summary(), true);
    }
    @Inject(method = "addDisplayText", at = @At("TAIL"))
    private void iv$plainModeWarning(List<Component> text, CallbackInfo ci) {
        if (IvMachineScope.nativeTarget(iv$self()) && !IvMachineScope.crossRecipeEnabled(iv$self()) && iv$self().isFormed())
            text.add(Component.translatable("gtl_enhancedcore.tooltip.iv_native.1").withStyle(ChatFormatting.YELLOW));
    }
}
