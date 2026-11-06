package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gtl.enhancedcore.common.recipe.iv.*;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;
import com.lowdragmc.lowdraglib.gui.widget.*;
import java.util.List;
import net.minecraft.network.chat.Component;
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
}
