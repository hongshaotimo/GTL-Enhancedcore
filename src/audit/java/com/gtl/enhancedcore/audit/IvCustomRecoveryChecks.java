package com.gtl.enhancedcore.audit;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import com.gtl.enhancedcore.common.machine.TieredParallelMachine;
import com.gtl.enhancedcore.common.recipe.iv.IvBuffers;
import com.gtl.enhancedcore.common.recipe.iv.IvJob;
import com.gtl.enhancedcore.common.recipe.iv.IvRecipeLogic;
import com.gtladd.gtladditions.common.machine.multiblock.part.MESuperPatternBufferPartMachine;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class IvCustomRecoveryChecks {
    private static int assertions;

    private IvCustomRecoveryChecks() {}

    public static int run(Object controller, Object part) throws Exception {
        var machine = (TieredParallelMachine) controller;
        var buffer = (MESuperPatternBufferPartMachine) part;
        var logic = (IvRecipeLogic) machine.getRecipeLogic();
        var state = IvBuffers.state(buffer);
        int previousAssertions = assertions;
        check(machine.isRecipeLogicAvailable() && IvBuffers.bind(buffer, machine), "Recovery probe requires a real formed controller");
        check(machine.getThreadsForTier() >= 2, "Probe requires at least two admission slots");
        check(state.jobs.isEmpty(), "Probe cannot modify a pre-existing order");
        long initialEnergy = machine.getEnergyContainer().getEnergyStored();
        boolean initialWorkingEnabled = machine.isWorkingEnabled();
        var initialError = machine.getMultiblockState().error;
        var type = machine.getRecipeTypes()[0];
        GTRecipe missing = GTRecipeBuilder.of(new ResourceLocation("gtl_enhancedcore", "audit_missing_catalyst"), type)
                .inputItems(new ItemStack(Items.APPLE)).notConsumable(new ItemStack(Items.DIAMOND))
                .outputItems(new ItemStack(Items.GOLD_INGOT)).duration(400).EUt(32).buildRawRecipe();
        GTRecipe valid = GTRecipeBuilder.of(new ResourceLocation("gtl_enhancedcore", "audit_startable_order"), type)
                .inputItems(new ItemStack(Items.APPLE)).outputItems(new ItemStack(Items.GOLD_INGOT))
                .duration(400).EUt(32).buildRawRecipe();
        AEKey apple = AEItemKey.of(new ItemStack(Items.APPLE));
        var constructor = IvJob.class.getDeclaredConstructor(UUID.class, int.class, GTRecipe.class, CompoundTag.class,
                Map.class, Map.class, long.class);
        constructor.setAccessible(true);
        var blocked = constructor.newInstance(new UUID(0, 1), 0, missing, new CompoundTag(), Map.of(apple, 1L), Map.of(), 1L);
        var runnable = constructor.newInstance(new UUID(0, 2), 1, valid, new CompoundTag(), Map.of(apple, 1L), Map.of(), 1L);
        try {
            state.jobs.add(blocked);
            state.jobs.add(runnable);
            logic.setLock(true);
            logic.setLockRecipe(null);
            machine.setWorkingEnabled(true);
            logic.serverTick();
            check(runnable.active(), "A catalyst-starved first order blocked a runnable second order");
            check(!blocked.active() && blocked.remaining == 1 && blocked.inventory.get(apple) == 1,
                    "Rejected order consumed or reserved its ingredient");
            check(logic.getLockRecipe() != null && logic.getLockRecipe().id.equals(valid.id),
                    "Auto-lock committed a recipe that did not actually start");
            check(runnable.remaining == 0 && !runnable.inventory.containsKey(apple), "Successful admission did not consume exactly one operation");
            runnable.validateAccounting();
            String paidBeforeUnload = runnable.save().toString();
            long energyBeforeUnload = machine.getEnergyContainer().getEnergyStored();
            machine.getMultiblockState().setError(MultiblockState.UNLOAD_ERROR);
            check(machine.isFormed() && !machine.isRecipeLogicAvailable(), "Probe does not reproduce the formed-but-unavailable window");
            logic.serverTick();
            check(paidBeforeUnload.equals(runnable.save().toString()), "Unavailable structure changed the paid order ledger");
            check(energyBeforeUnload == machine.getEnergyContainer().getEnergyStored(), "Unavailable structure paid energy");
            check(logic.isWorkingEnabled(), "Transient unloading became a persistent machine fault");
            machine.getMultiblockState().setError(initialError);
            check(machine.isRecipeLogicAvailable(), "Restoring the structure did not restore availability");
            int elapsedBefore = runnable.elapsed;
            var unpaidBefore = runnable.energyLeft;
            machine.getEnergyContainer().addEnergy(machine.getEnergyContainer().getEnergyCapacity());
            logic.serverTick();
            check(runnable.elapsed > elapsedBefore && runnable.energyLeft.compareTo(unpaidBefore) < 0,
                    "Order did not resume after availability/power returned without restart");
            check(runnable.remaining == 0 && !runnable.inventory.containsKey(apple), "Recovery consumed ingredients twice");
            runnable.validateAccounting();
            return assertions - previousAssertions;
        } finally {
            machine.getMultiblockState().setError(initialError);
            state.jobs.clear();
            logic.setLock(false);
            logic.setLockRecipe(null);
            logic.resetRecipeLogic();
            machine.setWorkingEnabled(false);
            logic.serverTick();
            long currentEnergy = machine.getEnergyContainer().getEnergyStored();
            if (currentEnergy > initialEnergy) machine.getEnergyContainer().removeEnergy(currentEnergy - initialEnergy);
            else if (currentEnergy < initialEnergy) machine.getEnergyContainer().addEnergy(initialEnergy - currentEnergy);
            machine.setWorkingEnabled(initialWorkingEnabled);
            buffer.markDirty();
        }
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
