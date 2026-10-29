package com.gtl.enhancedcore.audit;

import com.gregtechceu.gtceu.api.machine.*;
import com.gregtechceu.gtceu.api.machine.multiblock.*;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import com.gtl.enhancedcore.common.cloud.*;
import com.gtl.enhancedcore.common.cloud.store.*;
import com.gtl.enhancedcore.common.recipe.iv.*;
import com.mojang.authlib.GameProfile;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayerFactory;

/** Fresh-world real factory/pattern checks; coolant callbacks use the actual native machine. */
public final class CloudRepairRuntimeChecks {
    private CloudRepairRuntimeChecks() {}
    public static void structures(ServerLevel level, List<String> checks) throws Exception {
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "CloudRepairAudit"));
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        var cloud = GTRegistries.MACHINES.get(new ResourceLocation("gtl_enhancedcore:cloud_pattern_buffer"));
        int x = 640;
        for (String id : List.of("assembly_line", "circuit_assembly_line", "cold_ice_freezer", "blaze_blast_furnace")) {
            BlockPos pos = new BlockPos(x, 100, 640); x += 96;
            for (int cx = (pos.getX()-24)>>4; cx <= (pos.getX()+24)>>4; cx++)
                for (int cz = 38; cz <= 42; cz++) level.setChunkForced(cx, cz, true);
            var definition = GTRegistries.MACHINES.get(new ResourceLocation("gtceu", id));
            level.setBlock(pos, definition.defaultBlockState(), 3);
            var machine = (WorkableElectricMultiblockMachine)MetaMachine.getMachine(level, pos);
            machine.setFrontFacing(Direction.NORTH); player.setPos(pos.getX(), 100, 636);
            machine.getPattern().autoBuild(player, machine.getMultiblockState());
            check(machine.getPattern().checkPatternAt(machine.getMultiblockState(), false), id+" upstream production geometry forms before replacement: "+machine.getMultiblockState().error, checks);
            var cells = machine.getMultiblockState().getCache().stream().map(BlockPos::immutable).toList();
            int inputs=0, outputs=0;
            for (var cell : cells) {
                var block = level.getBlockState(cell).getBlock();
                boolean output = PartAbility.EXPORT_ITEMS.isApplicable(block) || PartAbility.EXPORT_FLUIDS.isApplicable(block);
                boolean input = PartAbility.IMPORT_ITEMS.isApplicable(block) || PartAbility.IMPORT_FLUIDS.isApplicable(block);
                if ((id.endsWith("assembly_line") && (input || output)) || (!id.endsWith("assembly_line") && inputs==0 && PartAbility.IMPORT_ITEMS.isApplicable(block))) {
                    level.setBlock(cell, cloud.defaultBlockState(), 3);
                    if (input) inputs++; if (output) outputs++;
                }
            }
            if (inputs==0 && !id.endsWith("assembly_line")) {
                for (var cell:cells) {
                    if (MetaMachine.getMachine(level,cell)!=null) continue;
                    var previous=level.getBlockState(cell); level.setBlock(cell,cloud.defaultBlockState(),3);
                    if(machine.getPattern().checkPatternAt(machine.getMultiblockState(),false)){inputs++;break;}
                    level.setBlock(cell,previous,3);
                }
            }
            check(inputs>0 && (!id.endsWith("assembly_line") || outputs>0), id+" replaced actual required input/output positions", checks);
            check(machine.getPattern().checkPatternAt(machine.getMultiblockState(), false), id+" real production pattern accepts cloud endpoints at required ports", checks);
            if (id.endsWith("assembly_line")) continue;
            machine.onStructureFormed();
            FluidHatchPartMachine fluid = null;
            for (var part : machine.getParts()) if (part instanceof FluidHatchPartMachine hatch
                    && PartAbility.IMPORT_FLUIDS.isApplicable(part.self().getBlockState().getBlock())) { fluid=hatch; break; }
            if (fluid==null) {
                var hatch = GTRegistries.MACHINES.get(new ResourceLocation("gtceu:ev_input_hatch"));
                for (var cell : cells) {
                    if (MetaMachine.getMachine(level, cell)!=null) continue;
                    var previous=level.getBlockState(cell); level.setBlock(cell,hatch.defaultBlockState(),3);
                    if (machine.getPattern().checkPatternAt(machine.getMultiblockState(),false)) { fluid=(FluidHatchPartMachine)MetaMachine.getMachine(level,cell); break; }
                    level.setBlock(cell,previous,3);
                }
                check(fluid!=null,id+" accepts auxiliary physical input hatch alongside cloud",checks);
                machine.getPattern().checkPatternAt(machine.getMultiblockState(),false); machine.onStructureFormed();
            }
            check(IvMachineScope.crossRecipeEnabled(machine),id+" cloud executor remains enabled with ordinary fluid inputs",checks);
            var material=id.equals("cold_ice_freezer") ? com.gregtechceu.gtceu.common.data.GTMaterials.Ice : com.gregtechceu.gtceu.common.data.GTMaterials.Blaze;
            long amount=(long)(Math.pow(2,machine.getTier()-2)*10);
            var logic=(IvNativeAccess)machine.getRecipeLogic();
            long offset=(long)field(machine.getHolder(),"offset").get(machine.getHolder());
            field(machine.getHolder(),"offset").set(machine.getHolder(),-level.getGameTime());
            fluid.tank.setFluidInTank(0,material.getFluid(amount));
            check(logic.iv$onWorking() && fluid.tank.getFluidInTank(0).isEmpty(),id+" native periodic callback consumes exact coolant from ordinary hatch",checks);
            check(!logic.iv$onWorking(),id+" empty coolant rejects cloud progress despite upstream true return",checks);
            field(machine.getHolder(),"offset").set(machine.getHolder(),1-level.getGameTime());
            check(!logic.iv$onWorking(),id+" missing coolant remains paused between periodic callbacks",checks);
            fluid.tank.setFluidInTank(0,material.getFluid(amount));
            field(machine.getHolder(),"offset").set(machine.getHolder(),-level.getGameTime());
            check(logic.iv$onWorking() && fluid.tank.getFluidInTank(0).isEmpty(),id+" resupply resumes and consumes exact coolant",checks);
            field(machine.getHolder(),"offset").set(machine.getHolder(),offset);
        }
    }
    public static void storageFailure(ServerLevel level, CloudPatternBufferMachine child, List<String> checks) throws Exception {
        var store=child.cloudStore(); var library=store.library(child.cloudLibraryId());
        var node=child.cloudNode(); var before=((net.minecraft.world.level.saveddata.SavedData)node).save(new CompoundTag());
        var root=level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("repair-fault");
        Files.createDirectories(root); var blocker=root.resolve("blocked"); Files.writeString(blocker,"preserve");
        var saved = (net.minecraft.world.level.saveddata.SavedData) library;
        saved.setDirty(); saved.save(blocker.resolve("authority.dat").toFile());
        check(!library.healthy() && saved.isDirty(),"Injected authority write failure isolates dirty library",checks);
        check(!child.cloudReady(true) && child.getAvailablePatterns().isEmpty(),"Faulted shared authority is unavailable to AE without throwing",checks);
        CloudPatternAllocator.availabilityChanged(child);
        check(!child.cloudReady(true),"Allocator peer notification after I/O failure cannot crash server",checks);
        store.deactivate(node.id(),child);
        check(((net.minecraft.world.level.saveddata.SavedData)node).save(new CompoundTag()).getCompound("data").getCompound("ivState").equals(before.getCompound("data").getCompound("ivState")),"Faulted unload retains exact job state",checks);
        ((net.minecraft.world.level.saveddata.SavedData)library).save(root.resolve("authority.dat").toFile());
        check(library.healthy(),"Successful durable retry restores authority health",checks);
        store.activate(node.id(),child,level.dimension().location(),child.getPos().asLong());
        var route = node.sourceRoute();
        store.bindNodeSnapshot(node,child.iv$getState()::save,CompoundTag::new,route::copy);
        Files.delete(root.resolve("authority.dat")); Files.delete(blocker); Files.delete(root);
    }
    private static Field field(Object target,String name) throws Exception {
        for(Class<?> type=target.getClass();type!=null;type=type.getSuperclass()) try {var f=type.getDeclaredField(name);f.setAccessible(true);return f;} catch(NoSuchFieldException ignored) {}
        throw new NoSuchFieldException(name);
    }
    private static void check(boolean valid,String label,List<String> checks) {if(!valid)throw new AssertionError(label);checks.add(label);}
}
