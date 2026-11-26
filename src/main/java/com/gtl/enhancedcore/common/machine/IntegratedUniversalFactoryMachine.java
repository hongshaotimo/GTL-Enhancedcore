package com.gtl.enhancedcore.common.machine;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gtl.enhancedcore.common.recipe.ThreadLimitedRecipeLogic;
import com.gtladd.gtladditions.api.machine.multiblock.GTLAddWorkableElectricMultipleRecipesMachine;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

/** Each installed feed/return combination opens a scheduling lane in the selected recipe family. */
public final class IntegratedUniversalFactoryMachine extends GTLAddWorkableElectricMultipleRecipesMachine {
    public static final int PARALLEL = 64;
    public static final int THREADS = 10;
    private static final ManagedFieldHolder FIELDS = new ManagedFieldHolder(IntegratedUniversalFactoryMachine.class, GTLAddWorkableElectricMultipleRecipesMachine.Companion.getMANAGED_FIELD_HOLDER());
    @DescSynced private int lanes = 1;

    public IntegratedUniversalFactoryMachine(IMachineBlockEntity holder, Object... args) { super(holder, args); }

    public static int lanesFor(int itemFeeds, int fluidFeeds, int fluidReturns) {
        return Math.max(1, Math.min(THREADS, itemFeeds * fluidFeeds * fluidReturns));
    }

    @Override public ManagedFieldHolder getFieldHolder() { return FIELDS; }
    @Override public int getMaxParallel() { return PARALLEL; }
    @Override public RecipeLogic createRecipeLogic(Object... args) { return new ThreadLimitedRecipeLogic(this, () -> lanes); }
    @Override public void attachSideTabs(com.gregtechceu.gtceu.api.gui.fancy.TabsWidget tabs) {
        tabs.setMainTab(this);
        tabs.attachSubTab(new com.gregtechceu.gtceu.api.machine.fancyconfigurator.MachineModeFancyConfigurator(this));
        var direction = com.gregtechceu.gtceu.api.machine.fancyconfigurator.CombinedDirectionalFancyConfigurator.of(this, this);
        if (direction != null) tabs.attachSubTab(direction);
    }
    @Override public boolean keepSubscribing() { return true; }

    @Override public void onStructureFormed() {
        super.onStructureFormed();
        int items = 0, feeds = 0, returns = 0;
        for (var part : getParts()) {
            var block = part.self().getBlockState().getBlock();
            if (PartAbility.IMPORT_ITEMS.isApplicable(block)) items++;
            else if (PartAbility.IMPORT_FLUIDS.isApplicable(block)) feeds++;
            else if (PartAbility.EXPORT_FLUIDS.isApplicable(block)) returns++;
        }
        lanes = lanesFor(items, feeds, returns);
    }

    @Override public void onStructureInvalid() { super.onStructureInvalid(); lanes = 1; }
    @Override public void onPartUnload() { super.onPartUnload(); lanes = 1; }
}
