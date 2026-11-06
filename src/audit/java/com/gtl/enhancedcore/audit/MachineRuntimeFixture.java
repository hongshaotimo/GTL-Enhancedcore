package com.gtl.enhancedcore.audit;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableEnergyContainer;
import com.gregtechceu.gtceu.api.misc.EnergyContainerList;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;
import com.gtl.enhancedcore.common.machine.IndustrialSteamPlatformMachine;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;
import java.lang.reflect.Field;
import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

final class MachineRuntimeFixture implements AutoCloseable {
        final AuditWorld world;
        final List<MetaMachineBlockEntity> holders = new ArrayList<>();
        final WorkableMultiblockMachine machine;
        NotifiableEnergyContainer energy;
        int ticks;

        MachineRuntimeFixture(String id, boolean electric) throws Exception {
            var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); unsafeField.setAccessible(true);
            var unsafe = (sun.misc.Unsafe) unsafeField.get(null);
            world = (AuditWorld) unsafe.allocateInstance(AuditWorld.class);
            field(world, "blockEntities").set(world, new HashMap<>());
            field(world, "renderedBlocks").set(world, new HashMap<>());
            field(world, "proxyWorld").set(world, new WeakReference<Level>(null));
            ObfuscationReflectionHelper.setPrivateValue(Level.class, world, false, "f_46443_");
            ObfuscationReflectionHelper.setPrivateValue(Level.class, world, Level.OVERWORLD, "f_46421_");
            machine = (WorkableMultiblockMachine) place(id, 0).getMetaMachine();
            field(machine, "isFormed").setBoolean(machine, true); machine.getMultiblockState().error = null;
            var output = (ItemBusPartMachine) place("gtceu:lv_output_bus", 2).getMetaMachine();
            field(machine, "parts").set(machine, new ArrayList<IMultiPart>(List.of(output)));
            field(machine, "partPositions").set(machine, new BlockPos[]{output.getPos()});
            machine.getCapabilitiesProxy().clear();
            if (machine instanceof com.gregtechceu.gtceu.common.machine.multiblock.primitive.PrimitiveWorkableMachine primitive) {
                machine.getCapabilitiesProxy().put(IO.IN, ItemRecipeCapability.CAP, List.of(primitive.importItems));
                machine.getCapabilitiesProxy().put(IO.OUT, ItemRecipeCapability.CAP, List.of(primitive.exportItems));
            } else machine.getCapabilitiesProxy().put(IO.OUT, ItemRecipeCapability.CAP, List.of(output.getInventory()));
            energy = electric ? new NotifiableEnergyContainer(machine, 1000000, 32, 1, 0, 0) : null;
            if (energy != null) {
                energy.setEnergyStored(1000000); machine.getCapabilitiesProxy().put(IO.IN, EURecipeCapability.CAP, List.of(energy));
                field(machine, "energyContainer").set(machine, new EnergyContainerList(List.of(energy)));
                field(machine, "tier").setInt(machine, 1);
            } else if (machine instanceof WorkableElectricMultiblockMachine)
                field(machine, "energyContainer").set(machine, new EnergyContainerList(List.of()));
            if (machine instanceof IndustrialSteamPlatformMachine steam)
                field(machine, "tier").setInt(machine, steam.getEffectiveTier());
            if (machine instanceof org.gtlcore.gtlcore.api.machine.trait.IRecipeCapabilityMachine capabilities) capabilities.upDate();
            if (machine instanceof IndustrialSteamPlatformMachine) {
                var registration = IndustrialSteamPlatformMachine.class.getDeclaredMethod("registerSteamEnergyHandler");
                registration.setAccessible(true); registration.invoke(machine);
            }
            machine.getRecipeLogic().markLastRecipeDirty();
            machine.getRecipeLogic().setWorkingEnabled(true);
        }
        MetaMachineBlockEntity place(String id, int x) {
            var definition = GTRegistries.MACHINES.get(new ResourceLocation(id));
            if (definition == null) throw new AssertionError("Missing actual registered fixture machine " + id);
            var pos = new BlockPos(x, 80, 0);
            var holder = (MetaMachineBlockEntity) definition.getBlockEntityType().create(pos, definition.defaultBlockState());
            holder.setLevel(world); world.blockEntities.put(pos, holder);
            world.renderedBlocks.put(pos, new BlockInfo(definition.defaultBlockState())); holders.add(holder); return holder;
        }
        void parts(List<? extends IMultiPart> parts) throws Exception {
            field(machine, "parts").set(machine, new ArrayList<>(parts));
            field(machine, "partPositions").set(machine, parts.stream().map(part -> part.self().getPos()).toArray(BlockPos[]::new));
        }
        void tick() throws Exception {
            field(machine.getHolder(), "offset").setLong(machine.getHolder(), ++ticks - 1000L);
            machine.getRecipeLogic().serverTick();
        }
        @Override public void close() { for (var holder : holders) holder.setLevel(null); }

    private static final class AuditWorld extends TrackedDummyWorld {
        private AuditWorld() { super(null); }
        @Override public Level getLevel() { return this; }
        @Override public net.minecraft.core.RegistryAccess registryAccess() { return net.minecraft.core.RegistryAccess.EMPTY; }
        @Override public net.minecraft.world.flag.FeatureFlagSet enabledFeatures() { return net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS; }
        @Override public net.minecraft.world.item.crafting.RecipeManager getRecipeManager() { return new net.minecraft.world.item.crafting.RecipeManager(); }
        @Override public long getGameTime() { return 1000; }
        @Override public BlockPos getSharedSpawnPos() { return BlockPos.ZERO; }
        @Override public void blockEntityChanged(BlockPos pos) {}
        @Override public void setBlocksDirty(BlockPos pos, net.minecraft.world.level.block.state.BlockState before,
                net.minecraft.world.level.block.state.BlockState after) {}
        @Override public net.minecraft.world.level.block.state.BlockState getBlockState(BlockPos pos) {
            var block = renderedBlocks.get(pos);
            return block == null ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState() : block.getBlockState();
        }
        @Override public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(BlockPos pos) { return blockEntities.get(pos); }
    }
    private static Field field(Object object, String name) throws Exception {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try { var field = type.getDeclaredField(name); field.setAccessible(true); return field; }
            catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }

}
