package com.gtl.enhancedcore.common.machine;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.IParallelHatch;
import com.gtl.enhancedcore.common.recipe.iv.IvRecipeLogic;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gtladd.gtladditions.api.machine.multiblock.GTLAddWorkableElectricParallelHatchMultipleRecipesMachine;
import com.gtl.enhancedcore.common.recipe.iv.IvCancellation;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;


/**
 * IV 四件套（等离子机床/精炼塔/质谱阵列/融合组装器）。
 * 专用超级样板总成按订单隔离，注册的所有类型共同调度。
 * 保留电压、并行仓和线程规格；实际任务计时沿用原 GTLAdditions 合批公式。
 */
public class TieredParallelMachine extends GTLAddWorkableElectricParallelHatchMultipleRecipesMachine {


    /** 线程起算电压：IV = 1 个跨配方线程。 */
    private static final int THREAD_BASE_TIER = GTValues.IV;

    /** 线程封顶电压：UHV 及以上统一按 UHV 计算。 */
    private static final int THREAD_CAP_TIER = GTValues.UHV;

    public TieredParallelMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
    }

    @Override public void onStructureFormed() {
        super.onStructureFormed();
        ((IvRecipeLogic)getRecipeLogic()).refreshInputPower();
    }

    /** 无并行控制仓时并行数固定为 1（单配方运行），装仓后使用仓的并行数。 */
    @Override
    public int getMaxParallel() {
        return getParts().stream().anyMatch(IParallelHatch.class::isInstance)
                ? Math.max(1, super.getMaxParallel()) : 1;
    }

    /**
     * 跨配方线程 = IV 起算 1 个，电压每高一级 +4，UHV 封顶（2026-09-03 用户调整，原为每级 +2）。
     * IV 1 / LuV 5 / ZPM 9 / UV 13 / UHV 及以上 17。
     * getTier() 由 GTCEu 在 onStructureFormed 按 getMaxVoltage() 计算（含原版双仓升压）。
     */
    public int getThreadsForTier() {
        int tier = Math.min(THREAD_CAP_TIER, this.getTier());
        return Math.max(1, (tier - THREAD_BASE_TIER) * 4 + 1);
    }

    /** 维护仓不能缩短耗时（2.7.7 四台 IV 规则）；子类可改回原生维护倍率。 */
    public boolean ivMaintenancePenalty() {
        return true;
    }

    /** 跨配方线程按电压递增（无其它附加机制）。 */
    @Override
    public RecipeLogic createRecipeLogic(Object... args) {
        return new IvRecipeLogic(this);
    }

    @Override public Widget createUIWidget() {
        Widget original=super.createUIWidget();
        if (!com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.crossRecipeEnabled(this)) return original;
        int width=original.getSizeWidth(),height=original.getSizeHeight();
        var group=new WidgetGroup(0,0,width,height+44);
        group.addWidget(original);
        group.addWidget(new com.gtl.enhancedcore.common.recipe.iv.IvActionButton(4,height+2,width-8,18,
                ()->ivBuffers().stream().anyMatch(buffer->!com.gtl.enhancedcore.common.recipe.iv.IvBuffers.state(buffer).jobs.isEmpty()),()->false,
                "gtl_enhancedcore.gui.iv_cancel_machine","gtl_enhancedcore.gui.iv_cancel_machine","gtl_enhancedcore.gui.iv_cancel_help",false,
                ()->ivBuffers().forEach(buffer->IvCancellation.cancel(buffer,-1))));
        group.addWidget(new com.gtl.enhancedcore.common.recipe.iv.IvActionButton(4,height+23,width-8,18,
                ()->!ivBuffers().isEmpty(),this::acceptingOrders,
                "gtl_enhancedcore.gui.iv_admission_on","gtl_enhancedcore.gui.iv_admission_off","gtl_enhancedcore.gui.iv_admission_help",true,
                ()->{boolean enabled=!acceptingOrders();ivBuffers().forEach(buffer->IvCancellation.setAccepting(buffer,enabled));}));
        return group;
    }

    private java.util.List<org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine> ivBuffers() {
        return com.gtl.enhancedcore.common.recipe.iv.IvBuffers.collect(this).stream()
                .filter(buffer->com.gtl.enhancedcore.common.recipe.iv.IvBuffers.isolated(buffer)).toList();
    }
    private boolean acceptingOrders() {
        var buffers=ivBuffers();
        return !buffers.isEmpty() && buffers.stream().allMatch(buffer->com.gtl.enhancedcore.common.recipe.iv.IvBuffers.state(buffer).accepting);
    }

    @Override
    protected void addMachineModeDisplay(java.util.List<net.minecraft.network.chat.Component> text) {
        if (com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.crossRecipeEnabled(this)) {
            text.add(net.minecraft.network.chat.Component.translatable("gtceu.gui.machinemode",
                    net.minecraft.network.chat.Component.translatable("gtl_enhancedcore.gui.iv_mode"))
                    .withStyle(net.minecraft.ChatFormatting.AQUA));
        } else {
            super.addMachineModeDisplay(text);
        }
    }
    @Override
    public void addDisplayText(java.util.List<net.minecraft.network.chat.Component> text) {
        // Keep ADD's energy tier, parallel, thread, working status and invalid-structure rows.
        super.addDisplayText(text);
        if (!isFormed()) return;
        if (!com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.crossRecipeEnabled(this)) {
            text.add(net.minecraft.network.chat.Component.translatable("tooltip.gtl_enhancedcore.iv_only_super_buffer")
                    .withStyle(net.minecraft.ChatFormatting.YELLOW));
            return;
        }
        IvRecipeLogic logic = (IvRecipeLogic)getRecipeLogic();
        com.gtl.enhancedcore.common.recipe.MachineDiagnostics.append(this, text);
        com.gtl.enhancedcore.common.recipe.iv.IvPresentation.append(text,logic.getOrderSummary(),true);
    }
    @Override public void attachSideTabs(com.gregtechceu.gtceu.api.gui.fancy.TabsWidget tabs) {
        tabs.setMainTab(this);
        if (!com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.crossRecipeEnabled(this)
                && getRecipeTypes().length > 1) {
            tabs.attachSubTab(new com.gregtechceu.gtceu.api.machine.fancyconfigurator.MachineModeFancyConfigurator(this));
        }
        var directional = com.gregtechceu.gtceu.api.machine.fancyconfigurator.CombinedDirectionalFancyConfigurator.of(this,this);
        if (directional!=null) tabs.attachSubTab(directional);
    }
    @Override public void onStructureInvalid() {
        logLifecycle("STRUCTURE_INVALID");
        super.onStructureInvalid();
        ((IvRecipeLogic)getRecipeLogic()).clearInputPower();
    }
    @Override public void onPartUnload() {
        logLifecycle("PART_UNLOAD");
        super.onPartUnload();
        ((IvRecipeLogic)getRecipeLogic()).clearInputPower();
    }
    private void logLifecycle(String event) {
        if (isRemote()) return;
        for (var buffer : com.gtl.enhancedcore.common.recipe.iv.IvBuffers.collect(this)) {
            var state = com.gtl.enhancedcore.common.recipe.iv.IvBuffers.state(buffer);
            if (state != null && state.dedicated()) com.gtl.enhancedcore.common.recipe.iv.IvTaskLog.event(buffer, null, event, "TASKS_RETAINED", "tasks", state.jobs.size());
        }
    }

}
