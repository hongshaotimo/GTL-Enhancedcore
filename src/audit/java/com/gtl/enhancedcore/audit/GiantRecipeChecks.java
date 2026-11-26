package com.gtl.enhancedcore.audit;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ParallelHatchPartMachine;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.machine.GTLEnhancedcoreMachines;
import com.gtl.enhancedcore.common.machine.StellarConfinementFusionReactorMachine;
import com.gtl.enhancedcore.common.recipe.MachineDiagnostics;
import com.gtl.enhancedcore.common.recipe.SingleRecipeParallel;
import com.gtl.enhancedcore.integration.jade.SingleRecipeParallelProvider;
import com.gtladd.gtladditions.common.machine.multiblock.part.ThreadPartMachine;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import com.mojang.authlib.GameProfile;
import com.hepdd.gtmthings.api.misc.WirelessEnergyManager;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.registries.ForgeRegistries;
import org.gtlcore.gtlcore.api.machine.trait.IBatchMachine;
import org.gtlcore.gtlcore.api.machine.trait.IRecipeCapabilityMachine;
import org.gtlcore.gtlcore.api.recipe.IGTRecipe;
import snownee.jade.impl.BlockAccessorImpl;

/** Test-only: unmodified production geometry, real hatches, native modifiers and server recipe ticks. */
public final class GiantRecipeChecks {
    private static Fixture fixture;
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
    private static final long CYCLE_INPUT = 71;
    private static long highestParallel;
    private static int restartStage, pendingTicks, pendingPhase, outageTicks, outageProgress;
    private static int fusionBatchStartTick, fusionFinishedBatches;
    private static long observedFusionOutput;
    private static GTRecipe observedFusionRecipe;
    private static long fusionBatchEUt, fusionBatchParallel;
    private static BigInteger fusionBatchWealth;
    private record Fixture(StellarConfinementFusionReactorMachine machine, NotifiableFluidTank input,
                           NotifiableFluidTank output, BlockPos port) {}
    private GiantRecipeChecks() {}

    public static void setup(MinecraftServer server, boolean saved) {
        restart = saved;
        place(server.overworld(), GTLEnhancedcoreMachines.getStellarConfinementFusionReactor(), saved);
    }

    private static Block block(String id) {
        var key = new ResourceLocation(id);
        check(ForgeRegistries.BLOCKS.containsKey(key), "Unknown fixture block " + id);
        return ForgeRegistries.BLOCKS.getValue(key);
    }

    private static void place(ServerLevel world, MultiblockMachineDefinition definition, boolean saved) {
        var shape = definition.getMatchingShapes().getFirst().getBlocks();
        int solids = 0;
        int ports = 0;
        var fixtureParts = List.of((Block) GTMachines.FLUID_IMPORT_HATCH[GTValues.UV].get(),
                (Block) GTMachines.FLUID_EXPORT_HATCH[GTValues.UV].get(),
                (Block) GTMachines.ITEM_IMPORT_BUS[GTValues.IV].get(),
                (Block) GTMachines.ITEM_EXPORT_BUS[GTValues.IV].get());
        BlockPos core = null;
        BlockPos firstPort = null;
        for (int x = 0; x < shape.length; x++) for (int z = 0; z < shape[x][0].length; z++) {
            if ((x & 15) == 0 && (z & 15) == 0) world.setChunkForced(x >> 4, z >> 4, true);
            for (int y = 0; y < shape[x].length; y++) {
                var info = shape[x][y][z];
                if (info == null || info.getBlockState().isAir()) continue;
                var state = info.getBlockState();
                String id = ForgeRegistries.BLOCKS.getKey(state.getBlock()).toString();
                var pos = new BlockPos(x, 80 + y, z);
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
        var controller = (StellarConfinementFusionReactorMachine) MetaMachine.getMachine(world, core);
        check(controller != null, "Missing placed controller");
        controller.setWorkingEnabled(false);
        if (!saved) {
            var owner = FakePlayerFactory.get(world, new GameProfile(FUSION_OWNER, "fusion_grid_audit"));
            controller.onMachinePlaced(owner, new ItemStack((Block) definition.get()));
            WirelessEnergyManager.setUserEU(OTHER_OWNER, OTHER_SUPPLY);
        }
        check(FUSION_OWNER.equals(controller.getUuid()), "Fusion wireless owner was not bound/persisted");
        // Parts are collected after the native asynchronous structure check.
        fixture = new Fixture(controller, null, null, firstPort);
        log("placed " + definition.getId() + " size=" + shape.length + "x" + shape[0].length
                + "x" + shape[0][0].length + " solids=" + solids + " restart=" + saved);
    }

    public static boolean step(MinecraftServer server) throws Exception {
        ticks++;
        if (!initialized) {
            if (ticks < 100) return false;
            var machine = fixture.machine();
            check(machine.isFormed(), "Full giant did not form: " + machine.getDefinition().getId()
                    + " error=" + machine.getMultiblockState().error);
            NotifiableFluidTank input = null, output = null;
            for (var part : machine.getParts()) {
                check(!(part instanceof ParallelHatchPartMachine) && !(part instanceof ThreadPartMachine),
                        "Wireless fusion formed with a parallel/thread hatch");
                for (var trait : part.getRecipeHandlers()) if (trait instanceof NotifiableFluidTank tank) {
                    if (tank.getHandlerIO() == IO.IN) input = tank;
                    if (tank.getHandlerIO() == IO.OUT) output = tank;
                }
            }
            check(input != null && output != null, "Missing IO hatches: input=" + input + " output=" + output);
            fixture = new Fixture(machine, input, output, fixture.port());
            if (restart) checkSavedBatch(fixture);
            else directChecks(fixture);
            check(machine.getParts().stream().noneMatch(
                    p -> p.self().getDefinition().getId().getPath().contains("energy")
                            || p.self().getDefinition().getId().getPath().contains("laser")),
                    "Wireless fusion formed with an energy/laser hatch");
            initialized = true;
            return false;
        }
        if (restart && restartStage < 2) return resumePending(server);
        if (scenario >= 3) {
            lifecycleTicks++;
            var f = fixture;
            if (lifecycleTicks == 1) {
                f.machine().setWorkingEnabled(false);
                f.machine().getLevel().setBlock(f.port(), block("gtlcore:iridium_casing").defaultBlockState(), 3);
            }
            if (lifecycleTicks == 100) {
                check(f.machine().isFormed(), "Optional hatch removal broke formation");
                var reactor = f.machine();
                check(reactor.getAdditionalThread() == 0, "Wireless fusion retained external threads");
                check(reactor.getMaxParallel() == Integer.MAX_VALUE, "Wireless fusion lost the unbounded parallel cap");
                check(reactor.getRecipeLogic().getMultipleThreads() == Integer.MAX_VALUE,
                        "Wireless fusion lost its infinite cross-recipe threads");
                check(reactor.getLimitedDuration() == 20, "Wireless fusion lost the fixed 20-tick batch");
                check(reactor.getWirelessNetworkEnergyHandler() != null, "Wireless fusion lost its grid energy handler");
                reactor.getLevel().setBlock(f.port(), block("gtceu:max_parallel_hatch").defaultBlockState(), 3);
            }
            // Only material IO hatches are valid. Probe other hatch types in the same port.
            if (lifecycleTicks == 200) {
                check(!f.machine().isFormed(), "Wireless fusion accepted a parallel hatch");
                f.machine().getLevel().setBlock(f.port(),
                        ((Block) GTMachines.AUTO_MAINTENANCE_HATCH.get()).defaultBlockState(), 3);
            }
            if (lifecycleTicks == 300) {
                check(!f.machine().isFormed(), "Wireless fusion accepted a maintenance hatch");
                f.machine().getLevel().setBlock(f.port(), block("gtladditions:thread_modifier_hatch").defaultBlockState(), 3);
            }
            if (lifecycleTicks == 400) {
                check(!f.machine().isFormed(), "Wireless fusion accepted an omega thread hatch");
                f.machine().getLevel().setBlock(f.port(),
                        ((Block) GTMachines.ENERGY_INPUT_HATCH[GTValues.UV].get()).defaultBlockState(), 3);
            }
            if (lifecycleTicks == 500) {
                check(!f.machine().isFormed(), "Wireless fusion accepted a wired energy hatch");
                var laser = PartAbility.INPUT_LASER.getAllBlocks().stream().findFirst().orElseThrow();
                f.machine().getLevel().setBlock(f.port(), laser.defaultBlockState(), 3);
            }
            if (lifecycleTicks == 600) {
                check(!f.machine().isFormed(), "Wireless fusion accepted a laser target hatch");
                f.machine().getLevel().setBlock(f.port(),
                        ((Block) GTMachines.FLUID_IMPORT_HATCH[GTValues.UV].get()).defaultBlockState(), 3);
                log("fusion_io_only_hatches_rejected=OK");
            }
            if (lifecycleTicks < 700) return false;
            check(f.machine().isFormed(), "Restored hatch did not form");
            check(f.machine().getMaxParallel() == Integer.MAX_VALUE,
                    "Wireless fusion lost the unbounded parallel cap after reformation");
            check(SingleRecipeParallel.current(f.machine()) == 0, "Idle machine reports an active batch");
            if (!restart) return savePending(server);
            check(WirelessEnergyManager.getUserEU(OTHER_OWNER).equals(OTHER_SUPPLY), "Fusion charged another player's grid");
            log("COMPLETE checks=" + checks + " full_giants=1 native_cycles=3 restart=" + restart
                    + " wireless_owner_and_paid_inflight_restart=OK");
            return true;
        }
        running = fixture;
        if (!prepared) {
            prepareCycle(running, scenario % 3);
            prepared = true;
            caseTicks = 0;
            return false;
        }
        caseTicks++;
        int mode = scenario % 3;
        if (mode == 1 && caseTicks == 25) {
            check(running.input().getFluidInTank(0).getAmount() == CYCLE_INPUT, "Blocked output consumed inputs");
            clear(running.output());
            observedFusionOutput = 0;
            log("cleared full output");
        }
        if (mode == 2 && caseTicks == 25) {
            check(running.input().getFluidInTank(0).getAmount() == CYCLE_INPUT, "Unpowered cycle consumed input");
            check(MachineDiagnostics.current(running.machine()) != null, "No power diagnostic");
            seedNetwork();
            log("power restored");
        }
        if (mode != 1 || caseTicks >= 25) observeFusionBatch(running);
        if ((mode != 1 || caseTicks >= 25) && (mode != 2 || caseTicks >= 25)
                && running.machine().getRecipeLogic().isIdle()
                && running.input().getFluidInTank(0).getAmount() > 0) {
            startCycle(running);
        }
        if (!running.machine().getRecipeLogic().isIdle()) {
            long count = SingleRecipeParallel.current(running.machine());
            var active = running.machine().getRecipeLogic().getLastRecipe();
            long activeBound = CYCLE_INPUT;
            check(count > 0 && count <= activeBound, "Active count exceeds material supply: " + count
                    + " id=" + active.id + " EU=" + RecipeHelper.getInputEUt(active) + " duration=" + active.duration
                    + " data=" + active.data + " batch=" + IGTRecipe.of(active).getBatchSize()
                    + " bound=" + activeBound);
            highestParallel = Math.max(highestParallel, count);
            check(running.machine().getRecipeLogic().getDuration() == 20, "Fusion not fixed20");
            if (!displayChecked) {
                displayChecks(running, count);
                displayChecked = true;
            }
        }
        if (running.output().getFluidInTank(0).getAmount() == expected) {
            check(displayChecked, "No active tick checked GUI/Jade");
            check(running.input().getFluidInTank(0).isEmpty(), "Produced output without consuming exactly the supplied input");
            check(running.output().getFluidInTank(0).getFluid() == Fluids.LAVA, "Wrong output fluid");
            running.machine().setWorkingEnabled(false);
            check(fusionFinishedBatches == 1, "Material-bound fusion must deliver exactly one batch");
            check(highestParallel == CYCLE_INPUT, "Fusion did not apply the full material-bound batch: " + highestParallel);
            check(WirelessEnergyManager.getUserEU(FUSION_OWNER).compareTo(NETWORK_SUPPLY) < 0,
                    "Fusion processed without charging its owner's grid");
            check(WirelessEnergyManager.getUserEU(OTHER_OWNER).equals(OTHER_SUPPLY), "Fusion charged a different grid");
            log("native cycle OK " + running.machine().getDefinition().getId() + " mode=" + mode
                    + " output=" + expected + " ticks=" + caseTicks);
            scenario++;
            prepared = false;
        }
        check(caseTicks < 320, "Cycle stalled " + running.machine().getDefinition().getId()
                + " status=" + running.machine().getRecipeLogic().getStatus()
                + " input=" + running.input().getFluidInTank(0).getAmount()
                + " output=" + running.output().getFluidInTank(0).getAmount()
                + " diagnostic=" + MachineDiagnostics.current(running.machine()));
        return false;
    }

    private static boolean savePending(MinecraftServer server) throws Exception {
        var f = fixture;
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
        check(logic.getDuration() == 20 && SingleRecipeParallel.current(f.machine()) == CYCLE_INPUT,
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
            seedNetwork();
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
        log("paid_inflight_save progress=" + logic.getProgress() + " parallel=" + CYCLE_INPUT + " owner=" + FUSION_OWNER
                + " balance=" + data.getString("balance"));
        log("COMPLETE checks=" + checks + " full_giants=1 native_cycles=3 restart=false paid_inflight_saved=true");
        return true;
    }

    private static boolean resumePending(MinecraftServer server) throws Exception {
        var f = fixture;
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

    private static void checkSavedBatch(Fixture f) {
        var saved = f.machine().getHolder().self().getPersistentData().getCompound("fusionWirelessAudit");
        check(!saved.isEmpty(), "Missing paid fusion restart snapshot");
        check(!BOOT_ID.equals(saved.getString("bootId")), "Fusion restart audit reused the same JVM");
        var logic = f.machine().getRecipeLogic();
        check(FUSION_OWNER.equals(f.machine().getUuid()),
                "Grid owner lost on restart");
        check(!logic.isWorkingEnabled() && logic.getLastRecipe() != null, "Paused paid fusion batch lost on restart");
        check(logic.getProgress() == saved.getInt("progress") && logic.getDuration() == 20,
                "Paid fusion progress/duration changed on restart");
        check(SingleRecipeParallel.current(f.machine()) == CYCLE_INPUT, "Saved paid fusion parallel count changed");
        check(f.input().getFluidInTank(0).isEmpty() && f.output().getFluidInTank(0).isEmpty(),
                "Restart refunded inputs or duplicated pending fusion output");
        check(WirelessEnergyManager.getUserEU(FUSION_OWNER).equals(new BigInteger(saved.getString("balance"))),
                "Paid fusion grid balance changed while paused/restarting");
        check(WirelessEnergyManager.getUserEU(OTHER_OWNER).equals(OTHER_SUPPLY), "Other grid balance changed on restart");
    }

    private static void directChecks(Fixture f) {
        var machine = f.machine();
        machine.getRecipeLogic().resetRecipeLogic();
        machine.setWorkingEnabled(false);
        ((IBatchMachine) machine).setBatchEnabled(false);
        clear(f.output());
        fusionWirelessChecks(f);
    }

    /**
     * 恒星堆对齐 {@code gtladditions:forge_of_the_antichrist} 的电与并行模型（用户 2026-10-05 指定）：
     * 电网抽电、无限并行（GTLCore {@code Integer.MAX_VALUE}）、无限线程（重写为 {@code Integer.MAX_VALUE}）。
     * 其批量由多配方逻辑内部生成，不走 {@code fullModifyRecipe}，因此这里直接核对契约字段。
     */
    private static void fusionWirelessChecks(Fixture f) {
        var machine = f.machine();
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
        seedNetwork();
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
        new SingleRecipeParallelProvider().appendServerData(data, accessor);
        check(data.getCompound("enhancedSingleRecipeParallel").isEmpty(),
                "Wireless fusion must not expose the same-recipe lane component");
        machine.createUI(player);
    }

    private static boolean startCycle(Fixture f) {
        // Fusion's own multi-recipe logic assembles and pays for each batch.
        f.machine().setWorkingEnabled(true);
        ((IRecipeCapabilityMachine) f.machine()).upDate();
        return !f.machine().getRecipeLogic().isIdle();
    }

    private static void prepareCycle(Fixture f, int mode) throws Exception {
        displayChecked = false;
        caseTicks = 0;
        fusionFinishedBatches = 0;
        observedFusionOutput = 0;
        observedFusionRecipe = null;
        f.machine().getRecipeLogic().resetRecipeLogic();
        clear(f.input());
        clear(f.output());
        f.input().setFluidInTank(0, FluidStack.create(Fluids.WATER, CYCLE_INPUT));
        expected = CYCLE_INPUT * 7;
        highestParallel = 0;
        WirelessEnergyManager.setUserEU(FUSION_OWNER, mode == 2 ? BigInteger.ZERO : NETWORK_SUPPLY);
        if (mode == 1) f.output().setFluidInTank(0, FluidStack.create(Fluids.LAVA, f.output().getTankCapacity(0)));
        boolean started = startCycle(f);
        log("cycle setup " + f.machine().getDefinition().getId() + " mode=" + mode + " started=" + started);
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
        fusionBatchWealth = WirelessEnergyManager.getUserEU(FUSION_OWNER);
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
            BigInteger paid = fusionBatchWealth.subtract(WirelessEnergyManager.getUserEU(FUSION_OWNER));
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

    private static void seedNetwork() {
        WirelessEnergyManager.setUserEU(FUSION_OWNER, NETWORK_SUPPLY);
    }
    private static void clear(NotifiableFluidTank tank) {
        for (int i = 0; i < tank.getTanks(); i++) tank.setFluidInTank(i, FluidStack.empty());
    }
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new IllegalStateException(message);
    }
    private static void log(String message) { GTLEnhancedcore.LOGGER.info("[GIANT_RECIPE] {}", message); }
}
