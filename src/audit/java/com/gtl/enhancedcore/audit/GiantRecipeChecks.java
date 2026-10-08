package com.gtl.enhancedcore.audit;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableEnergyContainer;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.logic.OCParams;
import com.gregtechceu.gtceu.api.recipe.logic.OCResult;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ParallelHatchPartMachine;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.machine.*;
import com.gtl.enhancedcore.common.recipe.FusionParallelPolicy;
import com.gtl.enhancedcore.common.recipe.MachineDiagnostics;
import com.gtl.enhancedcore.common.recipe.SingleRecipeParallel;
import com.gtl.enhancedcore.integration.jade.SingleRecipeParallelProvider;
import com.gtladd.gtladditions.api.machine.IThreadModifierMachine;
import com.gtladd.gtladditions.common.machine.multiblock.part.ThreadPartMachine;
import com.lowdragmc.lowdraglib.misc.ItemStackTransfer;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import com.mojang.authlib.GameProfile;
import com.hepdd.gtmthings.api.misc.WirelessEnergyManager;
import java.math.BigInteger;
import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.registries.ForgeRegistries;
import org.gtlcore.gtlcore.api.machine.trait.IBatchMachine;
import org.gtlcore.gtlcore.api.machine.trait.IRecipeStatus;
import org.gtlcore.gtlcore.api.machine.trait.IRecipeCapabilityMachine;
import org.gtlcore.gtlcore.api.recipe.IGTRecipe;
import org.gtlcore.gtlcore.api.recipe.RecipeResult;
import org.gtlcore.gtlcore.api.recipe.RecipeRunnerHelper;
import snownee.jade.impl.BlockAccessorImpl;

/** Test-only: unmodified production geometry, real hatches, native modifiers and server recipe ticks. */
public final class GiantRecipeChecks {
    private static final List<Fixture> fixtures = new ArrayList<>();
    private static int ticks, checks, caseTicks, lifecycleTicks;
    private static boolean restart, initialized, prepared, displayChecked;
    private static Fixture running;
    private static int scenario;
    private static long expected;
    private static final String BOOT_ID = UUID.randomUUID().toString();
    private static final UUID FUSION_OWNER = UUID.fromString("85c2f477-ec84-4eb4-a4e4-202610010016");
    private static final UUID OTHER_OWNER = UUID.fromString("85c2f477-ec84-4eb4-a4e4-202610010017");
    private static final BigInteger NETWORK_SUPPLY = new BigInteger("1000000000000000000");
    private static final BigInteger OTHER_SUPPLY = BigInteger.valueOf(123456789L);
    private static BigInteger cycleBalance;
    private static long highestParallel;
    private static int restartStage, pendingTicks, pendingPhase, outageTicks, outageProgress;
    private static int fusionBatchStartTick, fusionFinishedBatches;
    private static long observedFusionOutput;
    private static GTRecipe observedFusionRecipe;
    private static long fusionBatchEUt, fusionBatchParallel;
    private static BigInteger fusionBatchWealth;
    /** {@code fusion} = 恒星约束聚变堆（无线电网取电、无限并行/线程）；{@code !fusion} = 超构化学扭曲仪（同配方通道）。 */
    private record Fixture(WorkableElectricMultiblockMachine machine, NotifiableFluidTank input,
                           NotifiableFluidTank output, ParallelHatchPartMachine hatch, ThreadPartMachine engine,
                           boolean fusion, BlockPos port) {}
    private GiantRecipeChecks() {}

    public static void setup(MinecraftServer server, boolean saved) {
        restart = saved;
        place(server.overworld(), GTLEnhancedcoreMachines.getStellarConfinementFusionReactor(), 0, saved);
        place(server.overworld(), GTLEnhancedcoreMachines.getHyperstructuralChemicalDistorter(), 320, saved);
    }

    private static Block block(String id) {
        var key = new ResourceLocation(id);
        check(ForgeRegistries.BLOCKS.containsKey(key), "Unknown fixture block " + id);
        return ForgeRegistries.BLOCKS.getValue(key);
    }

    private static void place(ServerLevel world, MultiblockMachineDefinition definition, int offset, boolean saved) {
        var shape = definition.getMatchingShapes().getFirst().getBlocks();
        int solids = 0;
        int ports = 0;
        boolean fusion = definition.getId().getPath().equals("stellar_confinement_fusion_reactor");
        var fixtureParts = new ArrayList<Block>();
        if (!fusion) fixtureParts.add((Block) GTMachines.ENERGY_INPUT_HATCH[GTValues.UV].get());
        // 恒星堆只允许输入/输出仓室；扭曲仪还要能源仓、并行仓、维护仓与 Ω 引擎。
        fixtureParts.add((Block) GTMachines.FLUID_IMPORT_HATCH[GTValues.UV].get());
        fixtureParts.add((Block) GTMachines.FLUID_EXPORT_HATCH[GTValues.UV].get());
        fixtureParts.add((Block) GTMachines.ITEM_IMPORT_BUS[GTValues.IV].get());
        fixtureParts.add((Block) GTMachines.ITEM_EXPORT_BUS[GTValues.IV].get());
        if (!fusion) fixtureParts.addAll(List.of(
                block("gtceu:max_parallel_hatch"),
                (Block) GTMachines.AUTO_MAINTENANCE_HATCH.get(),
                block("gtladditions:thread_modifier_hatch")
        ));
        WorkableElectricMultiblockMachine controller = null;
        BlockPos core = null;
        BlockPos firstPort = null;
        for (int x = 0; x < shape.length; x++) for (int z = 0; z < shape[x][0].length; z++) {
            if ((x & 15) == 0 && (z & 15) == 0) world.setChunkForced((offset+x)>>4, z>>4, true);
            for (int y = 0; y < shape[x].length; y++) {
                var info = shape[x][y][z];
                if (info == null || info.getBlockState().isAir()) continue;
                var state = info.getBlockState();
                String id = ForgeRegistries.BLOCKS.getKey(state.getBlock()).toString();
                var pos = new BlockPos(offset+x, 80+y, z);
                if (state.getBlock() instanceof MetaMachineBlock && !id.equals(definition.getId().toString())) {
                    state = (ports < fixtureParts.size() ? fixtureParts.get(ports) : block("gtlcore:iridium_casing")).defaultBlockState();
                    if (firstPort == null) firstPort = pos;
                    ports++;
                }
                if (id.equals(definition.getId().toString())) {
                    core = pos;
                    continue;
                }
                if (!saved) world.setBlock(pos, state, 2 | 16);
                solids++;
            }
        }
        check(core != null, "Missing preview controller");
        check(ports >= fixtureParts.size(), "Insufficient preview hatch positions");
        if (!saved) world.setBlock(core, ((Block) definition.get()).defaultBlockState(), 3);
        controller = (WorkableElectricMultiblockMachine) MetaMachine.getMachine(world, core);
        check(controller != null, "Missing placed controller");
        controller.setWorkingEnabled(false);
        if (controller instanceof StellarConfinementFusionReactorMachine reactor) {
            if (!saved) {
                var owner = FakePlayerFactory.get(world, new GameProfile(FUSION_OWNER, "fusion_grid_audit"));
                reactor.onMachinePlaced(owner, new ItemStack((Block) definition.get()));
            }
            check(FUSION_OWNER.equals(reactor.getUuid()), "Fusion wireless owner was not bound/persisted");
            if (!saved) WirelessEnergyManager.setUserEU(OTHER_OWNER, OTHER_SUPPLY);
        }
        // Parts are collected after the native asynchronous structure check.
        fixtures.add(new Fixture(controller, null, null, null, null, fusion, firstPort));
        log("placed " + definition.getId() + " size=" + shape.length + "x" + shape[0].length
                + "x" + shape[0][0].length + " solids=" + solids + " restart=" + saved);
    }

    public static boolean step(MinecraftServer server) throws Exception {
        ticks++;
        if (!initialized) {
            if (ticks < 100) return false;
            for (int index = 0; index < fixtures.size(); index++) {
                var machine = fixtures.get(index).machine();
                check(machine.isFormed(), "Full giant did not form: " + machine.getDefinition().getId()
                        + " error=" + machine.getMultiblockState().error);
                NotifiableFluidTank input = null, output = null;
                ParallelHatchPartMachine hatch = null;
                ThreadPartMachine engine = null;
                for (var part : machine.getParts()) {
                    if (part instanceof ParallelHatchPartMachine parallel) hatch = parallel;
                    if (part instanceof ThreadPartMachine thread) engine = thread;
                    for (var trait : part.getRecipeHandlers()) if (trait instanceof NotifiableFluidTank tank) {
                        if (tank.getHandlerIO() == IO.IN) input = tank;
                        if (tank.getHandlerIO() == IO.OUT) output = tank;
                    }
                }
                boolean fusion = machine instanceof StellarConfinementFusionReactorMachine;
                check(input != null && output != null, "Missing IO hatches: input=" + input + " output=" + output
                        + " parts=" + machine.getParts().stream().map(p -> p.self().getDefinition().getId() + ":"
                                + p.getRecipeHandlers().stream().map(h -> h.getClass().getName()).toList()).toList());
                if (fusion) {
                    check(hatch == null && engine == null,
                            "Wireless fusion formed with a parallel/thread hatch: hatch=" + hatch + " engine=" + engine);
                } else {
                    check(hatch != null && engine != null,
                            "Missing real hatch/engine: hatch=" + hatch + " engine=" + engine
                            + " parts=" + machine.getParts().stream().map(p -> p.self().getDefinition().getId() + ":"
                                    + p.getRecipeHandlers().stream().map(h -> h.getClass().getName()).toList()).toList());
                }
                var fixture = new Fixture(machine, input, output, hatch, engine, fusion, fixtures.get(index).port());
                fixtures.set(index, fixture);
                if (savedOutputCheck(fixture)) log("saved native output/engine settings restored");
                if (!restart || !fixture.fusion()) directChecks(fixture);
                if (fixture.fusion()) check(machine.getParts().stream().noneMatch(
                        p -> p.self().getDefinition().getId().getPath().contains("energy")
                                || p.self().getDefinition().getId().getPath().contains("laser")),
                        "Wireless fusion formed with an energy/laser hatch");
            }
            initialized = true;
            return false;
        }
        if (restart && restartStage < 2) return resumePending(server);
        if (scenario >= fixtures.size() * 3) {
            lifecycleTicks++;
            if (lifecycleTicks == 1) {
                for (var f : fixtures) {
                    f.machine().setWorkingEnabled(false);
                    var world = f.machine().getLevel();
                    if (f.fusion()) {
                        // 恒星堆只有输入输出仓：拿掉一个 IO 口仍应成型。
                        world.setBlock(f.port(), block("gtlcore:iridium_casing").defaultBlockState(), 3);
                    } else {
                        world.setBlock(f.engine().getPos(), block("gtlcore:iridium_casing").defaultBlockState(), 3);
                        world.setBlock(f.hatch().getPos(), block("gtlcore:iridium_casing").defaultBlockState(), 3);
                    }
                }
            }
            if (lifecycleTicks == 100) {
                for (var f : fixtures) {
                    check(f.machine().isFormed(), "Optional hatch removal broke formation");
                    check(((IThreadModifierMachine) f.machine()).getAdditionalThread() == 0, "Removed engine retained threads");
                    if (f.fusion()) {
                        // 恒星堆并行/线程由控制器自身提供，不依赖并行仓或 Ω 引擎。
                        var reactor = (StellarConfinementFusionReactorMachine) f.machine();
                        check(reactor.getMaxParallel() == Integer.MAX_VALUE,
                                "Wireless fusion lost the unbounded parallel cap");
                        check(reactor.getRecipeLogic().getMultipleThreads() == Integer.MAX_VALUE,
                                "Wireless fusion lost its infinite cross-recipe threads");
                        check(reactor.getLimitedDuration() == 20, "Wireless fusion lost the fixed 20-tick batch");
                        check(reactor.getWirelessNetworkEnergyHandler() != null,
                                "Wireless fusion lost its grid energy handler");
                        f.machine().getLevel().setBlock(f.port(), block("gtceu:max_parallel_hatch").defaultBlockState(), 3);
                    } else {
                        check(SingleRecipeParallel.limit(f.machine(), base(f))
                                        == base(f) * SingleRecipeParallel.lanes((IThreadModifierMachine) f.machine()),
                                "Removed hatch/engine did not fall back to base parallel x fixed lanes");
                        var world = f.machine().getLevel();
                        world.setBlock(f.engine().getPos(), block("gtladditions:thread_modifier_hatch").defaultBlockState(), 3);
                        world.setBlock(f.hatch().getPos(), block("gtceu:max_parallel_hatch").defaultBlockState(), 3);
                    }
                }
            }
            // 恒星堆（first fixture）：只允许输入输出仓，逐个验证维护仓/并行仓/Ω 引擎/能源仓/激光靶仓都不能成型。
            if (lifecycleTicks == 200) {
                var f = fixtures.getFirst();
                check(!f.machine().isFormed(), "Wireless fusion accepted a parallel hatch");
                f.machine().getLevel().setBlock(f.port(),
                        ((Block) GTMachines.AUTO_MAINTENANCE_HATCH.get()).defaultBlockState(), 3);
            }
            if (lifecycleTicks == 300) {
                var f = fixtures.getFirst();
                check(!f.machine().isFormed(), "Wireless fusion accepted a maintenance hatch");
                f.machine().getLevel().setBlock(f.port(), block("gtladditions:thread_modifier_hatch").defaultBlockState(), 3);
            }
            if (lifecycleTicks == 400) {
                var f = fixtures.getFirst();
                check(!f.machine().isFormed(), "Wireless fusion accepted an omega thread hatch");
                f.machine().getLevel().setBlock(f.port(),
                        ((Block) GTMachines.ENERGY_INPUT_HATCH[GTValues.UV].get()).defaultBlockState(), 3);
            }
            if (lifecycleTicks == 500) {
                var f = fixtures.getFirst();
                check(!f.machine().isFormed(), "Wireless fusion accepted a wired energy hatch");
                var laser = PartAbility.INPUT_LASER.getAllBlocks().stream().findFirst().orElseThrow();
                f.machine().getLevel().setBlock(f.port(), laser.defaultBlockState(), 3);
            }
            if (lifecycleTicks == 600) {
                var f = fixtures.getFirst();
                check(!f.machine().isFormed(), "Wireless fusion accepted a laser target hatch");
                f.machine().getLevel().setBlock(f.port(),
                        ((Block) GTMachines.FLUID_IMPORT_HATCH[GTValues.UV].get()).defaultBlockState(), 3);
                log("fusion_io_only_hatches_rejected=OK");
            }
            if (lifecycleTicks < 700) return false;
            if (lifecycleTicks == 700) for (int i = 0; i < fixtures.size(); i++) {
                var f = fixtures.get(i);
                check(f.machine().isFormed(), "Restored hatch did not form");
                var world = f.machine().getLevel();
                if (f.fusion()) {
                    var next = new Fixture(f.machine(), f.input(), f.output(), null, null, true, f.port());
                    check(((StellarConfinementFusionReactorMachine) next.machine()).getMaxParallel() == Integer.MAX_VALUE,
                            "Wireless fusion lost the unbounded parallel cap after reformation");
                    check(SingleRecipeParallel.current(next.machine()) == 0, "Idle machine reports an active batch");
                    fixtures.set(i, next);
                    continue;
                }
                var next = new Fixture(f.machine(), f.input(), f.output(),
                        (ParallelHatchPartMachine) MetaMachine.getMachine(world, f.hatch().getPos()),
                        (ThreadPartMachine) MetaMachine.getMachine(world, f.engine().getPos()), false, f.port());
                engine(next, 1);
                next.hatch().setCurrentParallel(4);
                check(((IThreadModifierMachine) next.machine()).getAdditionalThread() > 0, "Restored engine failed to bind");
                check(SingleRecipeParallel.current(next.machine()) == 0, "Idle machine reports an active batch");
                fixtures.set(i, next);
            }
            if (!restart) return savePending(server);
            check(WirelessEnergyManager.getUserEU(OTHER_OWNER).equals(OTHER_SUPPLY), "Fusion charged another player's grid");
            log("COMPLETE checks=" + checks + " full_giants=2 native_cycles=6 restart=" + restart
                    + " wireless_owner_and_paid_inflight_restart=OK");
            return true;
        }
        running = fixtures.get(scenario / 3);
        if (!prepared) {
            prepareCycle(running, scenario % 3);
            prepared = true;
            caseTicks = 0;
            return false;
        }
        caseTicks++;
        int mode = scenario % 3;
        if (mode == 1 && caseTicks == 25) {
            check(running.input().getFluidInTank(0).getAmount() == cycleInput(running), "Blocked output consumed inputs");
            clear(running.output());
            observedFusionOutput = 0;
            log("cleared full output");
        }
        if (mode == 2 && caseTicks == 25) {
            check(running.input().getFluidInTank(0).getAmount() == cycleInput(running), "Unpowered cycle consumed input");
            check(MachineDiagnostics.current(running.machine()) != null, "No power diagnostic");
            if (running.fusion()) seedNetwork(running);
            log("power restored");
        }
        if (!running.fusion() && (mode != 2 || caseTicks >= 25)) charge(running);
        if (running.fusion() && (mode != 1 || caseTicks >= 25)) observeFusionBatch(running);
        if ((mode != 1 || caseTicks >= 25) && (mode != 2 || caseTicks >= 25)
                && running.machine().getRecipeLogic().isIdle()
                && running.input().getFluidInTank(0).getAmount() > 0) {
            startCycle(running);
        }
        if (!running.machine().getRecipeLogic().isIdle()) {
            long count = SingleRecipeParallel.current(running.machine());
            var active = running.machine().getRecipeLogic().getLastRecipe();
            // 恒星堆的批量由原料/输出/电网决定（并行上限为 Integer.MAX_VALUE），扭曲仪才套并行仓公式。
            long activeBound = running.fusion()
                    ? cycleInput(running)
                    : Math.min(cycleInput(running), SingleRecipeParallel.limit(running.machine(), base(running)));
            check(count > 0 && count <= activeBound, "Active count exceeds hatch/supply bound: " + count
                    + " id=" + active.id + " EU=" + RecipeHelper.getInputEUt(active) + " duration=" + active.duration
                    + " data=" + active.data + " batch=" + IGTRecipe.of(active).getBatchSize()
                    + " bound=" + activeBound);
            highestParallel = Math.max(highestParallel, count);
            if (running.fusion()) check(running.machine().getRecipeLogic().getDuration() == 20, "Fusion not fixed20");
            if (!displayChecked) {
                displayChecks(running, count);
                displayChecked = true;
            }
        }
        if (running.output().getFluidInTank(0).getAmount() == expected) {
            check(displayChecked, "No active tick checked GUI/Jade");
            check(running.input().getFluidInTank(0).isEmpty(), "Produced output without consuming exactly the supplied input");
            check(running.output().getFluidInTank(0).getFluid() == Fluids.LAVA, "Wrong output fluid");
            if (!running.fusion()) {
                check(itemCount(running, IO.IN) == 0, "Mixed recipe did not consume exactly 11 items");
                check(itemCount(running, IO.OUT) == 22, "Mixed recipe output duplicated/lost");
            }
            running.machine().setWorkingEnabled(false);
            if (running.fusion()) {
                check(fusionFinishedBatches == 1, "Material-bound fusion must deliver exactly one batch");
                check(highestParallel == cycleInput(running),
                        "Fusion did not apply the full material-bound batch: " + highestParallel);
                check(WirelessEnergyManager.getUserEU(FUSION_OWNER).compareTo(cycleBalance) < 0,
                        "Fusion processed without charging its owner's grid");
                check(WirelessEnergyManager.getUserEU(OTHER_OWNER).equals(OTHER_SUPPLY), "Fusion charged a different grid");
            }
            log("native cycle OK " + running.machine().getDefinition().getId() + " mode=" + mode
                    + " output=" + expected + " ticks=" + caseTicks);
            scenario++;
            prepared = false;
        }
        check(caseTicks < 320, "Cycle stalled " + running.machine().getDefinition().getId()
                + " status=" + running.machine().getRecipeLogic().getStatus()
                + " input=" + running.input().getFluidInTank(0).getAmount()
                + " items=" + itemCount(running, IO.IN)
                + " output=" + running.output().getFluidInTank(0).getAmount()
                + " diagnostic=" + MachineDiagnostics.current(running.machine()));
        return false;
    }

    private static boolean savePending(MinecraftServer server) throws Exception {
        var f = fixtures.getFirst();
        pendingTicks++;
        if (pendingTicks == 1) {
            prepareCycle(f, 0);
            return false;
        }
        var logic = f.machine().getRecipeLogic();
        if (logic.isIdle()) startCycle(f);
        if (logic.getLastRecipe() == null || logic.getProgress() < 5) {
            check(pendingTicks < 100, "Could not create a paid in-flight fusion save");
            return false;
        }
        check(logic.getDuration() == 20 && SingleRecipeParallel.current(f.machine()) == cycleInput(f),
                "Saved in-flight fusion is not one material-bound batch at 20 tick");
        if (pendingPhase == 0) {
            outageProgress = logic.getProgress();
            WirelessEnergyManager.setUserEU(FUSION_OWNER, BigInteger.ZERO);
            pendingPhase = 1;
            return false;
        }
        if (pendingPhase == 1) {
            outageTicks++;
            check(logic.getProgress() == outageProgress, "Loss of grid power advanced or damped a paid fusion batch");
            check(f.input().getFluidInTank(0).isEmpty() && f.output().getFluidInTank(0).isEmpty(),
                    "Grid outage paid inputs again or produced free output");
            if (outageTicks < 25) return false;
            seedNetwork(f);
            pendingPhase = 2;
            log("paid_inflight_grid_outage_and_progress_preservation=OK progress=" + outageProgress);
            return false;
        }
        if (logic.getProgress() < outageProgress + 3) return false;
        check(f.input().getFluidInTank(0).isEmpty() && f.output().getFluidInTank(0).isEmpty(),
                "Saved in-flight fusion payment/output incorrect");
        f.machine().setWorkingEnabled(false);
        var data = new CompoundTag();
        data.putString("bootId", BOOT_ID);
        data.putInt("progress", logic.getProgress());
        data.putString("balance", WirelessEnergyManager.getUserEU(FUSION_OWNER).toString());
        f.machine().getHolder().self().getPersistentData().put("fusionWirelessAudit", data);
        f.machine().markDirty();
        check(server.saveEverything(true, true, true), "Could not save the real paid fusion fixture");
        log("paid_inflight_save progress=" + logic.getProgress() + " parallel=" + cycleInput(f) + " owner=" + FUSION_OWNER
                + " balance=" + data.getString("balance"));
        log("COMPLETE checks=" + checks + " full_giants=2 native_cycles=6 restart=false paid_inflight_saved=true");
        return true;
    }

    private static boolean resumePending(MinecraftServer server) throws Exception {
        var f = fixtures.getFirst();
        var logic = f.machine().getRecipeLogic();
        pendingTicks++;
        if (restartStage == 0) {
            f.machine().setWorkingEnabled(true);
            restartStage = 1;
            log("paid_inflight_restored progress=" + logic.getProgress() + " parallel=" + SingleRecipeParallel.current(f.machine()));
        }
        if (logic.isIdle() && f.input().getFluidInTank(0).getAmount() > 0) startCycle(f);
        if (f.output().getFluidInTank(0).getAmount() == 497) {
            check(f.input().getFluidInTank(0).isEmpty(), "Restarted paid fusion batch repeated input payment");
            f.machine().setWorkingEnabled(false);
            check(WirelessEnergyManager.getUserEU(OTHER_OWNER).equals(OTHER_SUPPLY), "Restarted fusion used another grid");
            log("paid_inflight_restart_delivery=OK output=497 no_double_input=true");
            directChecks(f);
            restartStage = 2;
            return false;
        }
        check(pendingTicks < 160, "Saved paid fusion did not resume and deliver exactly once");
        return false;
    }

    private static boolean savedOutputCheck(Fixture f) {
        if (!restart) return false;
        if (f.fusion()) {
            var saved = f.machine().getHolder().self().getPersistentData().getCompound("fusionWirelessAudit");
            check(!saved.isEmpty(), "Missing paid fusion restart snapshot");
            check(!BOOT_ID.equals(saved.getString("bootId")), "Fusion restart audit reused the same JVM");
            var logic = f.machine().getRecipeLogic();
            check(FUSION_OWNER.equals(((StellarConfinementFusionReactorMachine) f.machine()).getUuid()),
                    "Grid owner lost on restart");
            check(!logic.isWorkingEnabled() && logic.getLastRecipe() != null, "Paused paid fusion batch lost on restart");
            check(logic.getProgress() == saved.getInt("progress") && logic.getDuration() == 20,
                    "Paid fusion progress/duration changed on restart");
            check(SingleRecipeParallel.current(f.machine()) == cycleInput(f), "Saved paid fusion parallel count changed");
            check(f.input().getFluidInTank(0).isEmpty() && f.output().getFluidInTank(0).isEmpty(),
                    "Restart refunded inputs or duplicated pending fusion output");
            check(WirelessEnergyManager.getUserEU(FUSION_OWNER).equals(new BigInteger(saved.getString("balance"))),
                    "Paid fusion grid balance changed while paused/restarting");
            check(WirelessEnergyManager.getUserEU(OTHER_OWNER).equals(OTHER_SUPPLY), "Other grid balance changed on restart");
            return true;
        }
        check(f.output().getFluidInTank(0).getAmount() == 77, "Output lost/duplicated after real restart");
        check(f.input().getFluidInTank(0).isEmpty(), "Consumed input returned on restart");
        check(f.hatch().getCurrentParallel() == 4, "Hatch setting not persisted");
        check(((IThreadModifierMachine) f.machine()).getAdditionalThread() > 0, "Engine failed to rebind on restart");
        check(itemCount(f, IO.IN) == 0 && itemCount(f, IO.OUT) == 22, "Mixed item IO changed after restart");
        return true;
    }

    private static void directChecks(Fixture f) throws Exception {
        var machine = f.machine();
        machine.getRecipeLogic().resetRecipeLogic();
        machine.setWorkingEnabled(false);
        ((IBatchMachine) machine).setBatchEnabled(false);
        clear(f.output());
        if (!f.fusion()) engine(f, 0);
        charge(f);
        if (f.fusion()) {
            fusionWirelessChecks(f);
            return;
        }
        int temperature = machine instanceof HyperstructuralChemicalDistorterMachine chemical
                ? chemical.getCoilType().getCoilTemperature() : 0;
        if (temperature > 0) {
            check(temperature > 1800, "Production tritanium coils not captured");
            var tooHot = recipe(f, 32, 1_000_000, temperature + 1, 0);
            check(modify(f, tooHot) == null, "Temperature requirement bypassed");
            check(((IRecipeStatus) machine.getRecipeLogic()).getRecipeStatus() == RecipeResult.FAIL_NO_ENOUGH_TEMPERATURE,
                    "Missing temperature failure status");
        }
        f.input().setFluidInTank(0, FluidStack.create(Fluids.WATER, 20_000));
        var raw = recipe(f, 32, 1_000_000, temperature, 1_000_000);
        log("routing " + machine.getDefinition().getId() + " maxVoltage=" + machine.getMaxVoltage()
                + " ocVoltage=" + machine.getOverclockVoltage() + " input=" + f.input().getFluidInTank(0)
                + " hatch=" + f.hatch().getDefinition().getId());
        f.hatch().setCurrentParallel(1);
        long supply = f.input().getFluidInTank(0).getAmount();
        var single = modify(f, raw);
        check(single != null && IGTRecipe.of(single).getRealParallels()
                        == Math.min(SingleRecipeParallel.limit(machine, base(f)), supply),
                "Base parallel ignored the fixed lanes or the material bound: " + parallel(single));
        f.hatch().setCurrentParallel(4);
        var four = modify(f, raw);
        check(four != null && IGTRecipe.of(four).getRealParallels()
                        == Math.min(SingleRecipeParallel.limit(machine, base(f)), supply),
                "Hatch4 ineffective: " + parallel(four)
                + " setting=" + f.hatch().getCurrentParallel() + " limit=" + SingleRecipeParallel.limit(machine, base(f))
                + " inputBound=" + org.gtlcore.gtlcore.api.recipe.IParallelLogic.getMaxParallel(machine, raw, 4)
                + " outputBound=" + org.gtlcore.gtlcore.api.recipe.IParallelLogic.getMinParallel(machine, raw, 4)
                + " matches=" + RecipeRunnerHelper.matchRecipe(machine, raw)
                + " parts=" + machine.getParts().stream().map(p -> p.self().getDefinition().getId()).toList());
        check(four.duration > 0 && RecipeHelper.getInputEUt(four) > 0, "Invalid duration/EU");
        check(raw.duration == 1_000_000 && IGTRecipe.of(raw).getRealParallels() == 1, "Mutated registered recipe");
        if (temperature > 0) {
            var warm = modify(f, recipe(f, 32, 1_000_000, temperature - 100, 0));
            check(warm != null && IGTRecipe.of(warm).getRealParallels() == 16, "Coil x hatch not applied exactly once");
        }
        f.hatch().setCurrentParallel(1);
        engine(f, 1);
        long lanes = SingleRecipeParallel.lanes((IThreadModifierMachine) machine);
        check(lanes > 1, "Loaded engine not bound");
        int omegaThreads = ((IThreadModifierMachine) machine).getAdditionalThread();
        check(omegaThreads > 0 && lanes == 1L + omegaThreads,
                "Loaded engine did not add exactly its omega threads: 1+" + omegaThreads + " vs " + lanes);
        long engineLimit = SingleRecipeParallel.limit(machine, base(f));
        check(engineLimit == base(f) * lanes,
                "Engine lanes missing from the formula limit: expected=" + base(f) * lanes + " actual=" + engineLimit);
        check(f.input().getTankCapacity(0) >= 20_000, "Engine fixture input tank too small");
        check(f.output().getTankCapacity(0) - f.output().getFluidInTank(0).getAmount() >= 19 * 7,
                "Engine fixture output tank too small");
        f.input().setFluidInTank(0, FluidStack.create(Fluids.WATER, 19));
        var withEngine = modify(f, raw);
        check(withEngine != null && IGTRecipe.of(withEngine).getRealParallels() == Math.min(engineLimit, 19),
                "Engine not applied: expected=" + Math.min(engineLimit, 19) + " actual=" + parallel(withEngine));
        f.hatch().setCurrentParallel(Integer.MAX_VALUE);
        field(ThreadPartMachine.class, "threadCount").setInt(f.engine(), Integer.MAX_VALUE);
        check(SingleRecipeParallel.limit(machine, Integer.MAX_VALUE) == Integer.MAX_VALUE, "Combined limit overflow");
        engine(f, 0);
        f.hatch().setCurrentParallel(1);
        check(SingleRecipeParallel.limit(machine, 1) == SingleRecipeParallel.lanes((IThreadModifierMachine) machine),
                "Empty engine must fall back to the base lane");
        f.hatch().setCurrentParallel(Integer.MAX_VALUE);
        f.input().setFluidInTank(0, FluidStack.create(Fluids.WATER, 3));
        var tail = modify(f, raw);
        check(tail != null && IGTRecipe.of(tail).getRealParallels() == 3, "MAX hatch/tail overflow");
        f.output().setFluidInTank(0, FluidStack.create(Fluids.LAVA, f.output().getTankCapacity(0) - 7));
        var limited = modify(f, raw);
        check(limited != null && IGTRecipe.of(limited).getRealParallels() == 1, "Output-space bound not respected");
        clear(f.output());
        f.input().setFluidInTank(0, FluidStack.create(Fluids.WATER, 20_000));
        var power = modify(f, recipe(f, machine.getMaxVoltage(), 1_000_000, temperature, 1_000_000));
        check(power != null && IGTRecipe.of(power).getRealParallels() <= 2,
                "Parallel bypassed native voltage budget " + parallel(power));
        machine.getRecipeLogic().resetRecipeLogic();
        machine.setWorkingEnabled(false);
        log("direct OK " + machine.getDefinition().getId() + " temperature=" + temperature
                + " engineLanes=" + lanes + " maxVoltage=" + machine.getMaxVoltage()
                + " ocVoltage=" + machine.getOverclockVoltage());
    }

    /**
     * 恒星堆对齐 {@code gtladditions:forge_of_the_antichrist} 的电与并行模型（用户 2026-10-05 指定）：
     * 电网抽电、无限并行（GTLCore {@code Integer.MAX_VALUE}）、无限线程（重写为 {@code Integer.MAX_VALUE}）。
     * 其批量由多配方逻辑内部生成，不走 {@code fullModifyRecipe}，因此这里直接核对契约字段。
     */
    private static void fusionWirelessChecks(Fixture f) throws Exception {
        var machine = (StellarConfinementFusionReactorMachine) f.machine();
        check(FUSION_OWNER.equals(machine.getUuid()), "Wireless fusion owner binding was lost");
        check(machine.getMaxVoltage() == 0L, "Wireless fusion must not depend on wired energy voltage");
        check(machine.getMaxParallel() == Integer.MAX_VALUE, "Wireless fusion parallel cap is not unbounded");
        check(machine.getRecipeLogic().getMultipleThreads() == Integer.MAX_VALUE,
                "Wireless fusion thread cap is not unbounded: " + machine.getRecipeLogic().getMultipleThreads());
        check(machine.getLimitedDuration() == 20, "Wireless fusion batch duration is not fixed at 20 ticks");
        var handler = machine.getWirelessNetworkEnergyHandler();
        check(handler != null, "Wireless fusion lost its grid energy handler");
        WirelessEnergyManager.setUserEU(FUSION_OWNER, BigInteger.ZERO);
        check(!handler.isOnline(), "Empty grid still reports wireless power");
        seedNetwork(f);
        check(handler.isOnline(), "Seeded grid is not reported as online");
        check(handler.getMaxAvailableEnergy().equals(NETWORK_SUPPLY),
                "Wireless handler did not expose the grid balance: " + handler.getMaxAvailableEnergy());
        check(WirelessEnergyManager.getUserEU(OTHER_OWNER).equals(OTHER_SUPPLY),
                "Seeding one grid charged another owner");
        ((IBatchMachine) machine).setBatchEnabled(true);
        check(!((IBatchMachine) machine).isBatchEnabled() && !((IBatchMachine) machine).supportsBatchProcessing(),
                "Fixed20 reactor enabled time-window batch");
        log("wireless fusion OK maxParallel=" + machine.getMaxParallel()
                + " threads=" + machine.getRecipeLogic().getMultipleThreads()
                + " duration=" + machine.getLimitedDuration() + " maxVoltage=" + machine.getMaxVoltage()
                + " grid=" + handler.getMaxAvailableEnergy());
    }

    private static void displayChecks(Fixture f, long expectedCount) {
        var machine = f.machine();
        var lines = new ArrayList<Component>();
        machine.addDisplayText(lines);
        check(SingleRecipeParallel.current(machine) == expectedCount, "GUI count wrong");
        var world = (ServerLevel) machine.getLevel();
        var player = FakePlayerFactory.get(world, new GameProfile(UUID.randomUUID(), "giant_audit"));
        var pos = machine.getPos();
        var data = new CompoundTag();
        var accessor = new BlockAccessorImpl.Builder().level(world).player(player).serverData(data)
                .serverConnected(true).showDetails(true).blockState(world.getBlockState(pos))
                .blockEntity(() -> world.getBlockEntity(pos))
                .hit(new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false)).build();
        if (!f.fusion()) {
            // 同配方通道机器才有该行与 Jade 组件；无线多配方机器不走 SingleRecipeParallel。
            check(lines.stream().anyMatch(c -> Component.Serializer.toJson(c).contains("parallel.current")),
                    "GUI actual count missing");
            new SingleRecipeParallelProvider().appendServerData(data, accessor);
            check(data.getCompound("enhancedSingleRecipeParallel").getLong("current") == expectedCount,
                    "Jade actual count wrong");
        } else {
            new SingleRecipeParallelProvider().appendServerData(data, accessor);
            check(data.getCompound("enhancedSingleRecipeParallel").isEmpty(),
                    "Wireless fusion must not expose the same-recipe lane component");
        }
        machine.createUI(player);
    }

    private static long parallel(GTRecipe recipe) { return recipe == null ? -1 : IGTRecipe.of(recipe).getRealParallels(); }
    private static GTRecipe modify(Fixture f, GTRecipe recipe) {
        // These direct probes change inventories repeatedly within one server tick.
        ((IRecipeCapabilityMachine) f.machine()).upDate();
        RecipeRunnerHelper.matchRecipe(f.machine(), recipe);
        return f.machine().fullModifyRecipe(recipe.copy(), new OCParams(), new OCResult());
    }
    private static boolean startCycle(Fixture f) throws Exception {
        if (f.fusion()) {
            // 无线多配方机器由自身 RecipeLogic 拉取批次；普通 checkMatchedRecipeAvailable 会绕过合批逻辑。
            f.machine().setWorkingEnabled(true);
            ((IRecipeCapabilityMachine) f.machine()).upDate();
            return !f.machine().getRecipeLogic().isIdle();
        }
        var recipe = cycleRecipe(f);
        return RecipeRunnerHelper.matchRecipe(f.machine(), recipe)
                && f.machine().getRecipeLogic().checkMatchedRecipeAvailable(recipe);
    }
    private static GTRecipe recipe(Fixture f, long eut, int duration, int temp, long start) {
        return GTRecipeBuilder.of(new ResourceLocation("gtl_enhancedcore", "audit_giant_" + (f.fusion() ? "fusion" : "chemical")),
                f.machine().getRecipeType()).inputFluids(FluidStack.create(Fluids.WATER, 1))
                .outputFluids(FluidStack.create(Fluids.LAVA, 7)).EUt(eut).duration(duration)
                .blastFurnaceTemp(temp).fusionStartEU(start).buildRawRecipe();
    }
    private static GTRecipe cycleRecipe(Fixture f) {
        int temperature = f.machine() instanceof HyperstructuralChemicalDistorterMachine chemical
                ? chemical.getCoilType().getCoilTemperature() : 0;
        var builder = GTRecipeBuilder.of(new ResourceLocation("gtl_enhancedcore",
                        f.fusion() ? "audit_giant_fusion" : "audit_giant_mixed_cycle"), f.machine().getRecipeType())
                .inputFluids(FluidStack.create(Fluids.WATER, 1)).outputFluids(FluidStack.create(Fluids.LAVA, 7))
                .EUt(32768).duration(20).blastFurnaceTemp(temperature).fusionStartEU(1_000_000);
        if (!f.fusion()) builder.inputItems(Items.APPLE).outputItems(Items.GOLD_INGOT, 2);
        return builder.buildRawRecipe();
    }
    private static void prepareCycle(Fixture f, int mode) throws Exception {
        displayChecked = false;
        caseTicks = 0;
        fusionFinishedBatches = 0;
        observedFusionOutput = 0;
        observedFusionRecipe = null;
        f.machine().getRecipeLogic().resetRecipeLogic();
        if (!f.fusion()) {
            // 恒星堆没有并行仓与 Ω 引擎，批量完全由原料/输出/电网决定。
            engine(f, 0);
            f.hatch().setCurrentParallel(4);
        }
        clear(f.input());
        clear(f.output());
        f.input().setFluidInTank(0, FluidStack.create(Fluids.WATER, cycleInput(f)));
        items(f, 11);
        expected = cycleInput(f) * 7;
        highestParallel = 0;
        if (f.fusion()) {
            WirelessEnergyManager.setUserEU(FUSION_OWNER, mode == 2 ? BigInteger.ZERO : NETWORK_SUPPLY);
            cycleBalance = NETWORK_SUPPLY;
        }
        if (mode == 1) f.output().setFluidInTank(0, FluidStack.create(Fluids.LAVA, f.output().getTankCapacity(0)));
        if (mode == 2) {
            f.machine().getEnergyContainer().removeEnergy(Long.MAX_VALUE);
            if (f.fusion()) WirelessEnergyManager.setUserEU(FUSION_OWNER, BigInteger.ZERO);
        } else charge(f);
        ((IRecipeCapabilityMachine) f.machine()).upDate();
        f.machine().setWorkingEnabled(true);
        var raw = cycleRecipe(f);
        check(raw.duration == 20 && RecipeHelper.getInputEUt(raw) == 32768, "Malformed synthetic cycle");
        boolean started = startCycle(f);
        log("cycle setup " + f.machine().getDefinition().getId() + " mode=" + mode + " started=" + started);
    }
    private static BigInteger storedWealth(Fixture f) {
        return WirelessEnergyManager.getUserEU(FUSION_OWNER);
    }
    private static void beginFusionBatch(Fixture f) throws Exception {
        var logic = f.machine().getRecipeLogic();
        observedFusionRecipe = logic.getLastRecipe();
        fusionBatchStartTick = caseTicks;
        check(logic.getDuration() == 20, "Fusion batch does not use the fixed 20-tick duration: " + logic.getDuration());
        fusionBatchParallel = SingleRecipeParallel.current(f.machine());
        check(fusionBatchParallel > 0, "Fusion batch has no material-bound parallel count");
        // 无线机器没有内部缓存，支付账本只有所属电网余额；合成配方 32768 EU/t × 并行。
        fusionBatchEUt = 32768L * fusionBatchParallel;
        fusionBatchWealth = storedWealth(f);
    }
    private static void observeFusionBatch(Fixture f) throws Exception {
        var logic = f.machine().getRecipeLogic();
        long output = f.output().getFluidInTank(0).getAmount();
        if (observedFusionRecipe == null && !logic.isIdle() && logic.getLastRecipe() != null
                && output == observedFusionOutput) {
            // 多配方逻辑自行开工，这里只负责在批次首次可见时记账。
            beginFusionBatch(f);
        }
        if (output > observedFusionOutput) {
            check(observedFusionRecipe != null, "Fusion delivered without a recorded paid batch");
            BigInteger paid = fusionBatchWealth.subtract(storedWealth(f));
            BigInteger required = BigInteger.valueOf(fusionBatchEUt).multiply(BigInteger.valueOf(20));
            check(paid.equals(required),
                    "Fusion wireless payment differed from 20 x EUt: paid=" + paid + " required=" + required);
            check(output - observedFusionOutput == fusionBatchParallel * 7,
                    "Fusion batch output did not match its material-bound parallel: " + fusionBatchParallel);
            log("BATCH index=" + fusionFinishedBatches + " mode=" + (scenario % 3)
                    + " case_ticks=" + (caseTicks - fusionBatchStartTick) + " parallel=" + fusionBatchParallel
                    + " EUt=" + fusionBatchEUt + " paid=" + paid + " output=" + (output - observedFusionOutput));
            fusionFinishedBatches++;
            observedFusionOutput = output;
            observedFusionRecipe = null;
        } else if (observedFusionRecipe != null) {
            check(logic.getLastRecipe() == observedFusionRecipe, "Active fusion recipe changed before output delivery");
        }
    }

    private static void charge(Fixture f) throws Exception {
        for (var part : f.machine().getParts()) for (var handler : part.getRecipeHandlers())
            if (handler instanceof NotifiableEnergyContainer energy) energy.setEnergyStored(energy.getEnergyCapacity());
        if (f.fusion()) seedNetwork(f);
    }
    private static int base(Fixture f) { return 1; }
    private static long cycleInput(Fixture f) { return f.fusion() ? 71 : 11; }
    private static void seedNetwork(Fixture f) {
        WirelessEnergyManager.setUserEU(FUSION_OWNER, NETWORK_SUPPLY);
    }
    private static void clear(NotifiableFluidTank tank) {
        for (int i = 0; i < tank.getTanks(); i++) tank.setFluidInTank(i, FluidStack.empty());
    }
    private static NotifiableItemStackHandler itemHandler(Fixture f, IO io) {
        // Circuit configuration slots are also item handlers, but are not material inventories.
        return f.machine().getParts().stream().filter(ItemBusPartMachine.class::isInstance)
                .map(ItemBusPartMachine.class::cast).map(ItemBusPartMachine::getInventory)
                .filter(h -> h.getHandlerIO() == io).findFirst().orElseThrow();
    }
    private static void items(Fixture f, int amount) {
        for (IO io : List.of(IO.IN, IO.OUT)) {
            var inventory = itemHandler(f, io);
            for (int slot = 0; slot < inventory.getSlots(); slot++) inventory.setStackInSlot(slot, ItemStack.EMPTY);
            if (io == IO.IN && !f.fusion()) inventory.setStackInSlot(0, new ItemStack(Items.APPLE, amount));
        }
    }
    private static int itemCount(Fixture f, IO io) {
        var inventory = itemHandler(f, io);
        int count = 0;
        for (int slot = 0; slot < inventory.getSlots(); slot++) count += inventory.getStackInSlot(slot).getCount();
        return count;
    }
    private static Field field(Class<?> owner, String name) throws Exception {
        var field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
    private static void engine(Fixture f, int count) throws Exception {
        var inventory = (ItemStackTransfer) field(ThreadPartMachine.class, "astralArrayInventory").get(f.engine());
        var item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("gtladditions", "astral_array"));
        check(item != null, "Missing engine array item");
        inventory.setStackInSlot(0, count == 0 ? ItemStack.EMPTY : new ItemStack(item, count));
        // This is the same callback invoked by the engine's native SlotWidget.
        var recalc = ThreadPartMachine.class.getDeclaredMethod("reCalculateThreadCount");
        recalc.setAccessible(true);
        recalc.invoke(f.engine());
        f.engine().markDirty();
    }
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new IllegalStateException(message);
    }
    private static void log(String message) { GTLEnhancedcore.LOGGER.info("[GIANT_RECIPE] {}", message); }
}
