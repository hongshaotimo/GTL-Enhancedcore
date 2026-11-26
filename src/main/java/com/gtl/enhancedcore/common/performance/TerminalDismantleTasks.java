package com.gtl.enhancedcore.common.performance;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.config.TerminalDismantleConfig;
import com.gtl.enhancedcore.mixin.gtladditions.TerminalDismantleRemovedStructureAccessor;
import com.gtl.enhancedcore.mixin.gtladditions.TerminalDismantleStructureModifyAccessor;
import com.gtl.enhancedcore.mixin.gtlcore.TerminalDismantlePatternAccessor;
import com.gtladd.gtladditions.api.machine.feature.IStructureTierMachine;
import com.gtladd.gtladditions.common.machine.trait.StructureModifyTrait;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayDeque;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import org.gtlcore.gtlcore.api.pattern.AdvancedBlockPattern;
import org.gtlcore.gtlcore.common.item.UltimateTerminalBehavior.AutoBuildSetting;
import org.gtlcore.gtlcore.integration.ae2.WirelessTerminalGridResolver;

/** Server-thread jobs. Never retains a per-block world snapshot or requests a chunk ticket. */
@Mod.EventBusSubscriber(modid = GTLEnhancedcore.MOD_ID)
public final class TerminalDismantleTasks {
    private static final ArrayDeque<Job> JOBS = new ArrayDeque<>();
    private static final DismantleResumeLayouts<IMultiController, TraceabilityPredicate[][][]> RESUME =
            new DismantleResumeLayouts<>();
    private static Job active;
    private static TerminalDismantleConfig.Loader config;
    private static long configTick;

    private TerminalDismantleTasks() {}

    public static void submit(AdvancedBlockPattern pattern, IMultiController controller, Player player,
            AutoBuildSetting setting) {
        if (!(player instanceof ServerPlayer serverPlayer) || !(player.level() instanceof ServerLevel level)) return;
        if (active != null && (active.player == player || active.controller == controller)) {
            message(player, "busy");
            return;
        }
        for (Job job : JOBS) {
            if (job.player == player || job.controller == controller) {
                message(player, "busy");
                return;
            }
        }
        if (JOBS.size() + (active == null ? 0 : 1) >= configuration().current().maxConcurrentTasks()) {
            message(player, "busy");
            return;
        }
        JOBS.add(new Job(pattern, controller, serverPlayer, level, setting));
        message(player, "queued");
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        TerminalDismantleConfig.Settings settings = configuration().poll(configTick++);
        if (JOBS.isEmpty()) return;
        DismantleBudget budget = new DismantleBudget(settings.maxScansPerTick(), settings.maxRemovalsPerTick(),
                settings.budgetMilliseconds() * 1_000_000L, System::nanoTime);
        int turns = JOBS.size();
        while (turns-- > 0 && budget.available()) {
            Job job = JOBS.removeFirst();
            active = job;
            boolean keep = true;
            try {
                String invalid = job.invalidReason();
                if (invalid != null) {
                    job.stopReason = invalid;
                    job.finish(false);
                    continue;
                }
                while (budget.scan()) {
                    int outcome = job.step();
                    if (outcome < 0) {
                        keep = false;
                        job.finish(true);
                        break;
                    }
                    if (outcome == 2) {
                        if (job.waitingSince == Long.MIN_VALUE) job.waitingSince = job.level.getGameTime();
                        if (job.level.getGameTime() - job.waitingSince >= settings.unloadedWaitTicks()) {
                            keep = false;
                            job.stopReason = "unloaded";
                            job.finish(false);
                        }
                        break;
                    }
                    job.waitingSince = Long.MIN_VALUE;
                    if (outcome == 1) budget.removed();
                }
            } catch (RuntimeException failure) {
                keep = false;
                job.stopReason = "error";
                GTLEnhancedcore.LOGGER.error("Terminal dismantle stopped at {} after {} removals",
                        job.origin, job.removed, failure);
                job.finish(false);
            } finally {
                active = null;
            }
            if (keep) JOBS.addLast(job);
        }
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Runnable cleanup = () -> {
            RESUME.clear();
            JOBS.removeIf(job -> {
                if (job.level != level) return false;
                job.stopReason = "world_unloaded";
                job.finish(false);
                return true;
            });
        };
        if (level.getServer().isSameThread()) cleanup.run();
        else level.getServer().execute(cleanup);
    }

    @SubscribeEvent
    public static void starting(ServerStartingEvent event) {
        JOBS.clear();
        RESUME.clear();
        active = null;
        config = null;
        configTick = 0;
        configuration().poll(0);
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        JOBS.clear();
        RESUME.clear();
        active = null;
        config = null;
        configTick = 0;
    }

    private static TerminalDismantleConfig.Loader configuration() {
        if (config == null) config = new TerminalDismantleConfig.Loader(
                FMLPaths.CONFIGDIR.get().resolve("GTL-Enhancedcore").resolve(TerminalDismantleConfig.FILE),
                warning -> GTLEnhancedcore.LOGGER.warn(warning));
        return config;
    }

    public static boolean busy(Player player) {
        if (!(player instanceof ServerPlayer)) return false;
        if (active != null && active.player == player) return true;
        for (Job job : JOBS) if (job.player == player) return true;
        return false;
    }

    private static void message(Player player, String key, Object... args) {
        Component text = Component.translatable("gtl_enhancedcore.terminal.dismantle." + key, args);
        player.displayClientMessage(text, true);
    }

    private static boolean loaded(ServerLevel level, BlockPos pos) {
        return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
    }

    private static final class Job {
        private final AdvancedBlockPattern pattern;
        private final boolean tiered;
        private final TerminalDismantlePatternAccessor access;
        private final IMultiController controller;
        private final ServerPlayer player;
        private final ServerLevel level;
        private final AutoBuildSetting setting;
        private final ItemStack terminal;
        private final ItemStack tool;
        private final BlockPos origin;
        private final Direction facing;
        private final Direction upwards;
        private final TraceabilityPredicate[][][] matches;
        private final int[] center;
        private final MultiblockState temporary;
        private DismantleLayoutPlanner planner;
        private DismantleLayoutPlanner.Layout layout;
        private DismantleLayoutPlanner.Slice slice;
        private int sliceCell;
        private int sliceScore;
        private DismantleCursor cursor;
        private long waitingSince = Long.MIN_VALUE;
        private long removed;
        private long examined;
        private long totalCells;
        private boolean initialized;
        private long storageTick = Long.MIN_VALUE;
        private MEStorage cachedStorage;
        private boolean invalidated;
        private long lastProgress;
        private DismantleEmptyProbe emptyProbe;
        private final BlockPos.MutableBlockPos probePos = new BlockPos.MutableBlockPos();
        private boolean probeUnloaded;
        private boolean emptyLoadedRegion;
        private String stopReason = "changed";
        private final DismantleMaterialIndex materials;
        private boolean verifyingResume;

        private Job(AdvancedBlockPattern pattern, IMultiController controller, ServerPlayer player,
                ServerLevel level, AutoBuildSetting setting) {
            // The controller's working pattern may exclude upper tiers or captured cells.
            if (controller.self() instanceof IStructureTierMachine machine) {
                this.pattern = AdvancedBlockPattern.getAdvancedBlockPattern(machine.getStructureTierTrait().getLayout().getPattern());
                tiered = true;
            } else {
                this.pattern = pattern;
                tiered = false;
            }
            this.access = (TerminalDismantlePatternAccessor) this.pattern;
            this.controller = controller;
            this.player = player;
            this.level = level;
            this.setting = setting;
            terminal = player.getMainHandItem();
            tool = terminal.copy();
            origin = controller.self().getPos().immutable();
            facing = controller.self().getFrontFacing();
            upwards = controller.self().getUpwardsFacing();
            matches = access.enhancedcore$matches();
            materials = new DismantleMaterialIndex(matches);
            center = access.enhancedcore$center();
            temporary = new MultiblockState(level, origin);
            lastProgress = level.getGameTime();
        }

        private String invalidReason() {
            if (player.hasDisconnected()) return "offline";
            if (player.level() != level) return "dimension";
            if (!loaded(level, origin)) return "controller_unloaded";
            if (MetaMachine.getMachine(level, origin) != controller.self()) return "controller";
            if (controller.self().getFrontFacing() != facing || controller.self().getUpwardsFacing() != upwards)
                return "direction";
            return null;
        }

        /** 0 = read/control step, 1 = committed removal, 2 = unloaded/locked pause, -1 = done. */
        private int step() {
            if (level.getGameTime() - lastProgress >= 40) {
                lastProgress = level.getGameTime();
                String phase = waitingSince != Long.MIN_VALUE ? "waiting" : !materials.done() ? "indexing" : emptyProbe != null ? "probing"
                        : layout == null ? "planning" : "scan_progress";
                player.displayClientMessage(Component.translatable("gtl_enhancedcore.terminal.dismantle." + phase,
                        phase.equals("waiting") ? removed : examined, totalCells, removed), true);
            }
            if (!initialized) {
                if (!controller.self().getPatternLock().tryLock()) return 2;
                try {
                    MultiblockState state = controller.getMultiblockState();
                    DismantleLayoutPlanner.Layout formed = formedLayout(state);
                    if (!restoreCapturedBlocks()) return 2;
                    if (formed != null) {
                        layout = formed;
                        startCursor();
                    } else if ((layout = RESUME.find(controller, matches, facing.ordinal(), upwards.ordinal())) != null) {
                        verifyingResume = true;
                        startCursor();
                    } else {
                        planner = new DismantleLayoutPlanner(access.enhancedcore$repetitions(), center[2],
                                controller.self().allowFlip());
                        emptyProbe = createEmptyProbe();
                    }
                    initialized = true;
                } finally { controller.self().getPatternLock().unlock(); }
                return 0;
            }
            if (!materials.done()) {
                materials.advance();
                return 0;
            }
            if (emptyProbe != null) return probeStep();
            if (layout == null) return planStep();
            if (cursor.done()) {
                if (!verifyingResume) return -1;
                // The old bounds speed up resumption, but cannot prove that no new repeats were built.
                verifyingResume = false;
                layout = null;
                cursor = null;
                planner = new DismantleLayoutPlanner(access.enhancedcore$repetitions(), center[2],
                        controller.self().allowFlip());
                emptyProbe = createEmptyProbe();
                return 0;
            }
            TraceabilityPredicate predicate = matches[cursor.aisleIndex()][cursor.y()][cursor.x()];
            BlockPos pos = position(cursor.x(), cursor.y(), cursor.z(), layout.flipped());
            if (loaded(level, pos)) {
                var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
                if (level.isOutsideBuildHeight(pos) || !chunk.getSection(level.getSectionIndex(pos.getY()))
                        .getStates().maybeHas(state -> !state.isAir() && materials.mayContain(state.getBlock()))) {
                    BlockPos next = position(cursor.x() + 1, cursor.y(), cursor.z(), layout.flipped());
                    int span = emptySpan(pos, next);
                    examined += cursor.skipX(span);
                    return 0;
                }
            }
            if (!predicate.isAny() && !pos.equals(origin)) {
                if (!loaded(level, pos)) return 2;
                BlockState state = level.getBlockState(pos);
                if (!state.isAir() && !setting.shouldPreserveDuringDismantle(state.getBlock())
                        && access.enhancedcore$valid(state, pos, predicate, temporary)) {
                    boolean changed = remove(pos, state);
                    cursor.advance();
                    examined++;
                    return changed ? 1 : 0;
                }
            }
            cursor.advance();
            examined++;
            return 0;
        }

        private DismantleLayoutPlanner.Layout formedLayout(MultiblockState state) {
            if (tiered) {
                if (!controller.isFormed()) return null;
                int[][] limits = access.enhancedcore$repetitions();
                int[] repetitions = new int[limits.length];
                for (int i = 0; i < limits.length; i++) {
                    if (limits[i][0] != limits[i][1]) return null;
                    repetitions[i] = limits[i][0];
                }
                // Match StructureTierLayout.offset for the full, fixed layout.
                return new DismantleLayoutPlanner.Layout(-center[4], repetitions,
                        controller.self().isFlipped(), Long.MAX_VALUE);
            }
            if (!TerminalDismantleGuard.canReuseFormedLayout(controller, pattern, state)) return null;
            int[] repetitions = state.getMatchContext().get("gtlcore:matched_repetitions");
            return new DismantleLayoutPlanner.Layout(state.getMatchContext().getInt("gtlcore:matched_min_z"),
                    repetitions.clone(), state.isNeededFlip(), Long.MAX_VALUE);
        }

        private boolean restoreCapturedBlocks() {
            // The native restore calls getChunk before writing. Wait until every captured chunk is present.
            LongOpenHashSet checkedChunks = new LongOpenHashSet();
            var traits = controller.self().getTraits();
            for (var trait : traits) {
                if (!(trait instanceof StructureModifyTrait modify) || !modify.getHasRemovedStructure()) continue;
                var captured = ((TerminalDismantleStructureModifyAccessor) (Object) modify).enhancedcore$removedStructure();
                var positions = ((TerminalDismantleRemovedStructureAccessor) (Object) captured)
                        .enhancedcore$capturedBlocks().keySet().iterator();
                while (positions.hasNext()) {
                    long pos = positions.nextLong();
                    int x = BlockPos.getX(pos) >> 4;
                    int z = BlockPos.getZ(pos) >> 4;
                    if (checkedChunks.add(ChunkPos.asLong(x, z)) && level.getChunkSource().getChunkNow(x, z) == null)
                        return false;
                }
            }
            for (var trait : traits) {
                if (trait instanceof StructureModifyTrait modify && modify.getHasRemovedStructure()) modify.restore();
            }
            return true;
        }

        private int planStep() {
            if (slice == null) {
                planner.advance();
                if (planner.result() != null) {
                    layout = planner.result();
                    planner = null;
                    if (!layout.hasMatch()) return -1;
                    startCursor();
                    return 0;
                }
                slice = planner.needed();
                sliceCell = sliceScore = 0;
                return 0;
            }
            int width = matches[slice.aisleIndex()][0].length;
            int height = matches[slice.aisleIndex()].length;
            int y = sliceCell / width;
            int x = sliceCell % width;
            TraceabilityPredicate predicate = matches[slice.aisleIndex()][y][x];
            if (predicate.isAny()) {
                // A million ignored pattern cells are not a million world reads or scan tokens.
                int end = Math.min(width * height, sliceCell + 8192);
                do { sliceCell++; }
                while (sliceCell < end && matches[slice.aisleIndex()][sliceCell / width][sliceCell % width].isAny());
                if (sliceCell == width * height) {
                    planner.supply(slice, sliceScore);
                    slice = null;
                }
                return 0;
            }
            if (!predicate.isAny()) {
                BlockPos pos = position(x, y, slice.z(), slice.flipped());
                if (!loaded(level, pos)) return 2;
                var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
                if (level.isOutsideBuildHeight(pos) || !chunk.getSection(level.getSectionIndex(pos.getY()))
                        .getStates().maybeHas(state -> !state.isAir() && materials.mayContain(state.getBlock()))) {
                    int span = Math.min(width - x, emptySpan(pos, position(x + 1, y, slice.z(), slice.flipped())));
                    sliceCell += span;
                    if (sliceCell == width * height) {
                        planner.supply(slice, sliceScore);
                        slice = null;
                    }
                    return 0;
                }
                BlockState state = level.getBlockState(pos);
                if (TerminalDismantleGuard.candidate(state.isAir(), pos.equals(origin),
                        setting.shouldPreserveDuringDismantle(state.getBlock()))
                        && access.enhancedcore$valid(state, pos, predicate, temporary)) sliceScore++;
            }
            if (++sliceCell == width * height) {
                planner.supply(slice, sliceScore);
                slice = null;
            }
            return 0;
        }

        private DismantleEmptyProbe createEmptyProbe() {
            long before = 0, after = 0;
            int[][] limits = access.enhancedcore$repetitions();
            for (int i = 0; i < limits.length; i++) {
                if (i < center[2]) before += limits[i][1];
                else after += limits[i][1];
            }
            if (before > Integer.MAX_VALUE || after < 1 || after > Integer.MAX_VALUE) return null;
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (int flip = 0; flip <= (controller.self().allowFlip() ? 1 : 0); flip++)
                for (int x : new int[] {0, matches[0][0].length - 1})
                    for (int y : new int[] {0, matches[0].length - 1})
                        for (int z : new int[] {-(int)before, (int)after - 1}) {
                            BlockPos pos = position(x, y, z, flip != 0);
                            minX = Math.min(minX, pos.getX()); maxX = Math.max(maxX, pos.getX());
                            minY = Math.min(minY, pos.getY()); maxY = Math.max(maxY, pos.getY());
                            minZ = Math.min(minZ, pos.getZ()); maxZ = Math.max(maxZ, pos.getZ());
                        }
            minY = Math.max(minY, level.getMinBuildHeight());
            maxY = Math.min(maxY, level.getMaxBuildHeight() - 1);
            return minY <= maxY ? new DismantleEmptyProbe(minX, minY, minZ, maxX, maxY, maxZ) : null;
        }

        private int probeStep() {
            if (emptyProbe.done()) {
                emptyLoadedRegion = true;
                return -1;
            }
            var chunk = level.getChunkSource().getChunkNow(emptyProbe.chunkX(), emptyProbe.chunkZ());
            if (chunk == null) {
                probeUnloaded = true;
                emptyProbe.skipSection();
                return 0;
            }
            var section = chunk.getSection(level.getSectionIndex(emptyProbe.sectionY() * 16));
            if (!section.getStates().maybeHas(state -> !state.isAir() && materials.mayContain(state.getBlock()))) {
                emptyProbe.skipSection();
                return 0;
            }
            if (!emptyProbe.cells()) {
                emptyProbe.beginCells();
                return 0;
            }
            probePos.set(emptyProbe.x(), emptyProbe.y(), emptyProbe.z());
            BlockState state = chunk.getBlockState(probePos);
            if (materials.mayContain(state.getBlock()) && TerminalDismantleGuard.candidate(state.isAir(), probePos.equals(origin),
                    setting.shouldPreserveDuringDismantle(state.getBlock()))) {
                emptyProbe = null;
                return 0;
            }
            emptyProbe.advanceCell();
            return 0;
        }

        private BlockPos position(int x, int y, int z, boolean flipped) {
            return access.enhancedcore$offset(x - center[0], y - center[1], z, facing, upwards, flipped).offset(origin);
        }

        private void startCursor() {
            examined = 0;
            cursor = new DismantleCursor(matches[0][0].length, matches[0].length,
                    layout.repetitions(), layout.minZ());
            totalCells = (long)matches[0][0].length * matches[0].length
                    * java.util.Arrays.stream(layout.repetitions()).asLongStream().sum();
            RESUME.remember(controller, matches, facing.ordinal(), upwards.ordinal(), layout);
        }

        private static int emptySpan(BlockPos pos, BlockPos next) {
            int coordinate, direction;
            if (pos.getX() != next.getX()) { coordinate = pos.getX(); direction = next.getX() - coordinate; }
            else if (pos.getY() != next.getY()) { coordinate = pos.getY(); direction = next.getY() - coordinate; }
            else { coordinate = pos.getZ(); direction = next.getZ() - coordinate; }
            return TerminalDismantleGuard.emptySpan(coordinate, direction);
        }

        private MEStorage storage() {
            if (!setting.isAeMode()) return null;
            if (storageTick == level.getGameTime()) return cachedStorage;
            storageTick = level.getGameTime();
            IGrid grid = null;
            var bound = setting.getBoundAE();
            if (bound != null && bound.dimension().equals(level.dimension()) && loaded(level, bound.pos())) {
                var node = AdvancedBlockPattern.getGridNode(level.getBlockEntity(bound.pos()));
                if (node != null) grid = node.getGrid();
            }
            if (grid == null) grid = WirelessTerminalGridResolver.find(player, level);
            cachedStorage = grid == null ? null : grid.getStorageService().getInventory();
            return cachedStorage;
        }

        private boolean remove(BlockPos pos, BlockState state) {
            MEStorage storage = storage();
            IActionSource source = IActionSource.ofPlayer(player);
            List<ItemStack> drops = state.getBlock() instanceof LiquidBlock ? List.of()
                    : Block.getDrops(state, level, pos, level.getBlockEntity(pos), player, tool);
            // Commit before any material delivery. A rejected world mutation cannot mint a drop.
            if (!level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3)) return false;
            removed++;
            if (removed == 1 && terminal.getTag() != null) {
                terminal.getTag().putBoolean("DismantleMode", false);
                terminal.setTag(terminal.getTag());
            }
            if (state.getBlock() instanceof LiquidBlock liquid) {
                try {
                    if (storage != null) storage.insert(AEFluidKey.of(liquid.getFluid()), 1000L, Actionable.MODULATE, source);
                } finally { invalidateController(); }
                return true;
            }
            DismantleDropLedger<ItemStack> ledger = new DismantleDropLedger<>(drops);
            try {
                while (ledger.hasNext()) {
                    ItemStack drop = ledger.claim();
                    if (storage != null && !drop.isEmpty()) {
                        // A callback may throw after committing. The claimed stack is never retried/refunded.
                        long inserted = storage.insert(AEItemKey.of(drop), drop.getCount(), Actionable.MODULATE, source);
                        if (inserted > 0) drop.shrink((int) Math.min(drop.getCount(), inserted));
                    }
                    if (drop.isEmpty()) continue;
                    ItemStack remaining = access.enhancedcore$insertNested(player, drop);
                    if (!remaining.isEmpty() && !player.getInventory().add(remaining)) player.drop(remaining, false);
                }
            } finally {
                // Preserve known, not-yet-attempted drops when a callback interrupts delivery.
                while (ledger.hasNext()) {
                    ItemStack remaining = ledger.claim();
                    try {
                        if (!remaining.isEmpty()) player.drop(remaining, false);
                    } catch (RuntimeException fallbackFailure) {
                        // Do not let one modded ItemEntity callback prevent other untouched drops.
                        GTLEnhancedcore.LOGGER.error("Terminal fallback drop failed at {}; uncertain stack is not retried: {}",
                                pos, remaining, fallbackFailure);
                    }
                }
                invalidateController();
            }
            return true;
        }

        private void invalidateController() {
            if (invalidated) return;
            invalidated = true;
            if (controller instanceof WorkableMultiblockMachine machine && loaded(level, origin)) {
                try { machine.onPartUnload(); }
                catch (RuntimeException failure) {
                    GTLEnhancedcore.LOGGER.warn("Controller unload callback failed after terminal removal at {}", origin, failure);
                }
            }
        }

        private void finish(boolean complete) {
            if (complete) RESUME.forget(controller);
            if (!complete) {
                GTLEnhancedcore.LOGGER.warn("Terminal dismantle stopped: reason={}, controller={}, removed={}",
                        stopReason, origin, removed);
                if (!player.hasDisconnected()) message(player, "stopped", removed,
                        Component.translatable("gtl_enhancedcore.terminal.dismantle.reason." + stopReason));
            } else if (!player.hasDisconnected()) {
                message(player, removed == 0 ? (emptyLoadedRegion && probeUnloaded ? "empty_unloaded" : "empty") : "done", removed);
            }
        }
    }
}
