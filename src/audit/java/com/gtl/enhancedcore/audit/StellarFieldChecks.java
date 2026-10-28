package com.gtl.enhancedcore.audit;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gtl.enhancedcore.common.machine.galaxy.GalaxyConnectionHatch;
import com.gtl.enhancedcore.common.machine.galaxy.StellarFieldTicker;
import com.gtl.enhancedcore.common.machine.galaxy.StellarVortexSpaceStationMachine;
import com.lowdragmc.lowdraglib.utils.DummyWorld;
import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.LogicalSide;

/** Executes the real field state machine against deterministic clock/circuit/payment boundaries. */
final class StellarFieldChecks {
    private StellarFieldChecks() {}

    static void run(BiConsumer<Boolean, String> check) throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        var client = (ClientProbe) unsafe.allocateInstance(ClientProbe.class);
        set(StellarVortexSpaceStationMachine.class, client, "fieldActive", true);
        check.accept(client.isFieldActive(), "Synced active field renders without client pattern validation or recipe proxies");
        set(StellarVortexSpaceStationMachine.class, client, "fieldActive", false);
        check.accept(!client.isFieldActive(), "Synced inactive field stops client rendering");

        var machine = (ServerProbe) unsafe.allocateInstance(ServerProbe.class);
        machine.world = (DummyWorld) unsafe.allocateInstance(DummyWorld.class);
        machine.source = (SourceProbe) unsafe.allocateInstance(SourceProbe.class);
        machine.circuit = true;
        machine.balance = StellarVortexSpaceStationMachine.EU_PER_TICK * 1_001L;
        set(StellarVortexSpaceStationMachine.class, machine, "userId", new UUID(42, 1));
        Class<?> fieldLogicClass = Class.forName(StellarVortexSpaceStationMachine.class.getName() + "$FieldLogic");
        var logic = (RecipeLogic) unsafe.allocateInstance(fieldLogicClass);
        for (Class<?> owner = fieldLogicClass; owner != null; owner = owner.getSuperclass()) {
            for (Field field : owner.getDeclaredFields()) {
                if (field.getName().equals("machine") && field.getType().isInstance(machine)) {
                    field.setAccessible(true);
                    field.set(logic, machine);
                }
            }
        }
        set(RecipeLogic.class, logic, "status", RecipeLogic.Status.IDLE);
        set(WorkableMultiblockMachine.class, machine, "recipeLogic", logic);
        var tick = StellarVortexSpaceStationMachine.class.getDeclaredMethod("tickField");
        tick.setAccessible(true);
        var register = StellarFieldTicker.class.getDeclaredMethod("register", StellarVortexSpaceStationMachine.class);
        register.setAccessible(true);
        register.invoke(null, machine);
        var event = new TickEvent.LevelTickEvent(LogicalSide.SERVER, TickEvent.Phase.END, machine.world, () -> true);
        for (int i = 1; i <= 1_000; i++) {
            machine.tick = i;
            // No MetaMachine serverTick or subscription runs: this also models
            // consecutive catch-up ticks skipped by GTLCore's 40 ms throttle.
            StellarFieldTicker.onLevelTick(event);
            if (!machine.ensurePaid() || !machine.isFieldActive() || !logic.isWorking() || !logic.isActive()) {
                throw new AssertionError("Field became idle during funded continuous tick " + i);
            }
            StellarFieldTicker.onLevelTick(event);
        }
        check.accept(machine.payments == 1_000 && machine.balance == StellarVortexSpaceStationMachine.EU_PER_TICK,
                "1,000 real world-ticker events stay working without machine ticks and charge once per tick");
        check.accept(logic.getFieldHolder().getSyncedFieldIndex("status") != null,
                "FieldLogic managed holder retains inherited synced status");
        machine.tick++;
        machine.balance = 0;
        tick.invoke(machine);
        check.accept(!machine.isFieldActive() && logic.isWaiting() && machine.payments == 1_001,
                "Insufficient grid balance waits without reporting an active field");
        machine.balance = StellarVortexSpaceStationMachine.EU_PER_TICK * 3L;
        machine.tick++;
        tick.invoke(machine);
        check.accept(machine.isFieldActive() && logic.isWorking(), "Field resumes on the next funded tick");
        int paidBeforeCircuitLoss = machine.payments;
        machine.circuit = false;
        machine.tick++;
        tick.invoke(machine);
        check.accept(!machine.isFieldActive() && machine.payments == paidBeforeCircuitLoss && logic.isWaiting(),
                "Missing circuit pauses without requesting grid payment");
        machine.circuit = true;
        machine.tick++;
        tick.invoke(machine);
        int paidBeforeDisable = machine.payments;
        logic.setWorkingEnabled(false);
        check.accept(!machine.isFieldActive() && !logic.isActive(), "Working switch stops the field immediately");
        logic.setWorkingEnabled(true);
        tick.invoke(machine);
        check.accept(machine.payments == paidBeforeDisable, "Disable/re-enable within a tick never charges twice");
        machine.tick++;
        tick.invoke(machine);
        check.accept(machine.isFieldActive(), "Field resumes normally after a working switch cycle");
        var unregister = StellarFieldTicker.class.getDeclaredMethod("unregister", StellarVortexSpaceStationMachine.class);
        unregister.setAccessible(true);
        unregister.invoke(null, machine);
        int paidBeforeUnregister = machine.payments;
        machine.tick++;
        StellarFieldTicker.onLevelTick(event);
        check.accept(machine.payments == paidBeforeUnregister, "Unregistered station no longer receives world-ticker payments");
    }

    private static void set(Class<?> owner, Object target, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class ClientProbe extends StellarVortexSpaceStationMachine {
        private ClientProbe(IMachineBlockEntity holder) { super(holder); }
        @Override public boolean isRemote() { return true; }
        @Override public boolean isFormed() { throw new AssertionError("Client field consulted server formation"); }
        @Override public boolean isRecipeLogicAvailable() { throw new AssertionError("Client field consulted UNINIT_ERROR"); }
    }

    private static final class ServerProbe extends StellarVortexSpaceStationMachine {
        private long tick;
        private long balance;
        private int payments;
        private boolean circuit;
        private SourceProbe source;
        private Level world;
        private ServerProbe(IMachineBlockEntity holder) { super(holder); }
        @Override public boolean isRemote() { return false; }
        @Override public Level getLevel() { return world; }
        @Override public boolean isInValid() { return false; }
        @Override public boolean isFormed() { return true; }
        @Override public boolean isRecipeLogicAvailable() { return true; }
        @Override public List<IMultiPart> getParts() { return List.of(source); }
        @Override protected long currentFieldTick() { return tick; }
        @Override protected boolean circuitAvailable() { return circuit; }
        @Override protected boolean payFieldTick() {
            payments++;
            if (balance < EU_PER_TICK) return false;
            balance -= EU_PER_TICK;
            return true;
        }
        @Override public void notifyStatusChanged(RecipeLogic.Status before, RecipeLogic.Status after) {}
        @Override public void onWaiting() {}
    }

    private static final class SourceProbe extends GalaxyConnectionHatch {
        private SourceProbe(IMachineBlockEntity holder) { super(holder, true); }
        @Override public boolean isSource() { return true; }
        @Override public boolean isWorkingEnabled() { return true; }
    }
}
