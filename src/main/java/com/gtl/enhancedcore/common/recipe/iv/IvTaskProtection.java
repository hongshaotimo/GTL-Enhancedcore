package com.gtl.enhancedcore.common.recipe.iv;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;
import net.minecraftforge.fml.common.Mod;

/** Protect the sole task ledger from ordinary mining/explosions until it is drained. */
@Mod.EventBusSubscriber(modid = "gtl_enhancedcore", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class IvTaskProtection {
    private IvTaskProtection() {}
    private static boolean holdsTasks(Object machine) {
        IvBufferState state = IvBuffers.state(machine);
        return state != null && (!state.jobs.isEmpty() || !state.healthy());
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void breaking(BlockEvent.BreakEvent event) {
        if(event.isCanceled())return;
        var machine=MetaMachine.getMachine(event.getLevel(), event.getPos());
        if(!(machine instanceof MEPatternBufferPartMachine buffer) || !IvBuffers.isolated(buffer))return;
        if(!event.getPlayer().isShiftKeyDown()) {
            event.setCanceled(true);
            event.getPlayer().displayClientMessage(Component.translatable("gtl_enhancedcore.gui.iv_break_help"),true);
            return;
        }
        var state=IvBuffers.state(buffer);
        IvTaskLog.event(buffer,null,"SHIFT_BREAK","DISCARD_CONTENTS","player",event.getPlayer().getUUID(),"tasks",state.jobs.size(),"quarantined",!state.healthy());
        for(var job:state.jobs) {
            IvTaskLog.event(buffer,job,"DISCARD_TASK","SHIFT_BREAK","input",IvTaskLog.stock(job.inventory),"refunds",IvTaskLog.stock(job.refunds),
                    "pending",IvTaskLog.stock(job.pending),"completion",IvTaskLog.stock(job.completion),"remaining",job.remaining,"parallel",job.parallel);
        }
        state.discardTasks();
        for(Object slot:buffer.getInternalInventory())((IvSlotAccess)slot).iv$discardStock();
        buffer.getBuffer().clear();
        var shared=buffer.getSharedCatalystInventory();
        for(int i=0;i<shared.getSlots();i++)shared.setStackInSlot(i,net.minecraft.world.item.ItemStack.EMPTY);
        var tanks=buffer.getSharedCatalystTank();
        for(int i=0;i<tanks.getTanks();i++)tanks.setFluidInTank(i,com.lowdragmc.lowdraglib.side.fluid.FluidStack.empty());
        var circuits=buffer.getSharedCircuitInventory();
        for(int i=0;i<circuits.getSlots();i++)circuits.setStackInSlot(i,net.minecraft.world.item.ItemStack.EMPTY);
        buffer.markDirty();
        event.getPlayer().displayClientMessage(Component.translatable("gtl_enhancedcore.gui.iv_break_done"),true);
    }
    @SubscribeEvent public static void explosion(ExplosionEvent.Detonate event) {
        event.getAffectedBlocks().removeIf(pos -> holdsTasks(MetaMachine.getMachine(event.getLevel(), pos)));
    }
}
