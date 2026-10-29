package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.capability.IOpticalComputationProvider;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gtl.enhancedcore.common.machine.hatch.CreativeComputationReceiverHatchMachine;
import com.gtl.enhancedcore.common.machine.hatch.CreativeComputationSource;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.gtlcore.gtlcore.api.machine.computation.ComputationNode;
import org.gtlcore.gtlcore.api.machine.computation.ComputationScheduler;

/** Real registered hatch and transformed scheduler; synthetic world only, no player save. */
@Mod("enhancedcore_audit")
public final class CreativeComputationRuntimeChecks {
    private boolean done;
    private final List<String> checks = new ArrayList<>();

    public CreativeComputationRuntimeChecks() { MinecraftForge.EVENT_BUS.addListener(this::tick); }

    private void check(boolean valid, String label) {
        if (!valid) throw new AssertionError(label);
        checks.add(label);
    }

    private void tick(TickEvent.ClientTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END || !Boolean.getBoolean("gtl.creativeComputationAudit")) return;
        var mc = Minecraft.getInstance();
        if (mc.getOverlay() != null || mc.screen == null) return;
        done = true;
        var directory = mc.gameDirectory.toPath().toAbsolutePath().normalize();
        var result = new LinkedHashMap<String, Object>();
        var holders = new ArrayList<MetaMachineBlockEntity>();
        try {
            check(directory.startsWith(Path.of("C:/IDEA/GTL-Enhancedcore/_audit/creative-unlimited-20261029"))
                    && Files.isRegularFile(directory.resolve("creative-computation.marker"))
                    && mc.level == null && mc.getSingleplayerServer() == null,
                    "Marked isolated title-screen fixture; no player save opened");
            var world = world();
            var controllerHolder = place("gtceu:mage_assembler", new BlockPos(0, 80, 0), world, holders);
            var hatchHolder = place("gtl_enhancedcore:creative_computation_receiver_hatch", new BlockPos(1, 80, 0), world, holders);
            var controller = (IMultiController) controllerHolder.getMetaMachine();
            var machine = hatchHolder.getMetaMachine();
            check(machine instanceof CreativeComputationReceiverHatchMachine, "Actual registered creative hatch supplier used");
            var part = (IMultiPart) machine;
            var node = (ComputationNode) machine.getTraits().stream().filter(ComputationNode.class::isInstance).findFirst().orElseThrow();
            var source = (CreativeComputationSource) node.gtlcore$localComputationSources().getFirst();
            check(!part.isFormed() && !node.gtlcore$computationOnline() && source.gtlcore$availableComputation() == 0,
                    "Unformed registered hatch supplies no computation");
            part.addedToController(controller);
            check(part.isFormed() && node.gtlcore$computationOnline() && source.unlimited(),
                    "Formed loaded registered hatch exposes the unlimited production source");
            check(source == node.gtlcore$localComputationSources().getFirst(), "All local routes retain the exact same physical source identity");
            check(((IOpticalComputationProvider) node).getMaxCWUt(new ArrayList<>()) == Integer.MAX_VALUE,
                    "Legacy int maximum saturates to its ABI maximum instead of 2.1 billion");
            check(source.gtlcore$computationCapacity() == Long.MAX_VALUE && source.gtlcore$availableComputation() == Long.MAX_VALUE,
                    "Legacy long getters expose the full ABI maximum");

            BigInteger huge = BigInteger.TEN.pow(100).add(BigInteger.valueOf(37));
            var wide = source.withdrawComputation(huge);
            check(wide != null && wide.amount().equals(huge), "Actual source supplies 10^100+37 CWU in one BigInteger request without truncation");
            check(source.deliveredComputation().equals(huge), "BigInteger delivered counter records the exact 101-digit supplied amount");
            wide.refund().run(); wide.refund().run();
            check(source.deliveredComputation().equals(BigInteger.ZERO), "BigInteger refund executes exactly once");

            var scheduler = new ComputationScheduler();
            scheduler.advance(world.auditTick);
            Object first = new Object(), second = new Object(), third = new Object();
            var sources = List.<org.gtlcore.gtlcore.api.machine.computation.ComputationSource>of(source, source);
            check(scheduler.offer(first, sources, Long.MAX_VALUE, Long.MAX_VALUE) == Long.MAX_VALUE,
                    "Real transformed scheduler reserves Long.MAX_VALUE for first owner");
            check(scheduler.offer(second, sources, Long.MAX_VALUE, Long.MAX_VALUE) == Long.MAX_VALUE,
                    "Second overlapping owner independently reserves Long.MAX_VALUE in the same tick");
            check(scheduler.available(third, sources) == Long.MAX_VALUE,
                    "Shared routes and reservations leave unlimited supply available for further owners");
            try (var firstPayment = scheduler.withdraw(first, Long.MAX_VALUE)) {
                check(firstPayment != null && firstPayment.amount() == Long.MAX_VALUE, "First actual scheduler transaction draws the full reservation");
                firstPayment.commit();
            }
            try (var secondPayment = scheduler.withdraw(second, Long.MAX_VALUE)) {
                check(secondPayment != null && secondPayment.amount() == Long.MAX_VALUE, "Second actual scheduler transaction draws despite first owner's payment");
                secondPayment.commit();
            }
            var twiceLong = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO);
            check(source.deliveredComputation().equals(twiceLong), "Same-tick committed supply exceeds Long.MAX_VALUE with exact BigInteger accounting");
            check(scheduler.offer(third, sources, Long.MAX_VALUE, Long.MAX_VALUE) == Long.MAX_VALUE, "Unlimited supply remains reusable after two full-long payments");
            var cancelled = scheduler.withdraw(third, Long.MAX_VALUE);
            check(cancelled != null, "Failed-EU fixture obtains real uncommitted scheduler transaction");
            cancelled.close(); cancelled.close();
            check(source.deliveredComputation().equals(twiceLong), "Closing an uncommitted scheduler transaction refunds precisely once");

            check(scheduler.offer(first, sources, 1, 2) == 2, "Small elastic research demand receives its exact maximum");
            part.removedFromController(controller);
            check(!node.gtlcore$computationOnline() && source.gtlcore$availableComputation() == 0
                            && source.withdrawComputation(huge) == null && scheduler.withdraw(first, 2) == null,
                    "Losing formation invalidates reserved payment and rejects even arbitrary-width direct requests");
            part.addedToController(controller);
            world.loaded = false;
            check(!node.gtlcore$computationOnline() && source.withdrawComputation(huge) == null,
                    "Unloaded chunk rejects supply from the real registered hatch");
            world.loaded = true;
            scheduler.offer(first, sources, 1, 2); scheduler.forget(first);
            check(scheduler.withdraw(first, 2) == null, "forget releases creative reservations without allowing stale withdrawal");
            scheduler.offer(first, sources, 1, 2); scheduler.invalidate();
            check(scheduler.withdraw(first, 2) == null, "Topology invalidate clears creative reservations");
            scheduler.offer(first, sources, 1, 2);
            world.auditTick += 13; scheduler.advance(world.auditTick);
            check(scheduler.withdraw(first, 2) == null, "Creative reservation expires after upstream demand lifetime");
            check(source.deliveredComputation().equals(BigInteger.ZERO), "New physical tick clears only per-tick delivered statistics");
            check(scheduler.offer(first, sources, 0, 1) == 0 && scheduler.offer(first, sources, 2, 1) == 0,
                    "Zero and inverted computation demands are rejected by the transformed scheduler");
            check(source.withdrawComputation(BigInteger.ZERO) == null && source.withdrawComputation(BigInteger.ONE.negate()) == null,
                    "Nonpositive arbitrary-width requests never deliver work");
            var oldEpoch = source.withdrawComputation(huge);
            world.auditTick++;
            var nextEpoch = source.withdrawComputation(BigInteger.ONE);
            oldEpoch.refund().run();
            check(source.deliveredComputation().equals(BigInteger.ONE), "Old-tick refund cannot subtract from next-tick delivered work");
            nextEpoch.refund().run();
            check(source.deliveredComputation().signum() == 0, "Current-tick wide receipt remains refundable");

            var finite = new CreativeComputationSource(() -> world.auditTick, () -> 100L, () -> true);
            var finiteScheduler = new ComputationScheduler(); finiteScheduler.advance(world.auditTick);
            var finiteSources = List.<org.gtlcore.gtlcore.api.machine.computation.ComputationSource>of(finite);
            check(finiteScheduler.offer(first, finiteSources, 75, 75) == 75
                            && finiteScheduler.available(second, finiteSources) == 25,
                    "Transformed scheduler preserves finite-source shared reservation limits");
            check(finiteScheduler.offer(second, finiteSources, 50, 50) == 0,
                    "Ordinary finite sources do not inherit unlimited creative supply");
            try (var finitePayment = finiteScheduler.withdraw(first, 75)) {
                check(finitePayment != null, "Finite-source withdrawal still follows the upstream scheduler");
                finitePayment.commit();
            }
            check(finite.gtlcore$availableComputation() == 25, "Finite-source payment still consumes its real physical budget");

            var definition = machine.getDefinition();
            var tips = new ArrayList<Component>();
            definition.getTooltipBuilder().accept(definition.asStack(), tips);
            check(tips.stream().anyMatch(c -> c.getString().contains("无限")
                            || c.getString().toLowerCase(java.util.Locale.ROOT).contains("unlimited"))
                            && tips.stream().noneMatch(c -> c.getString().contains("2,100,000,000")),
                    "Registered item tooltip visibly describes unlimited supply instead of the old 2.1 billion limit");
            for (String lang : List.of("en_us", "zh_cn")) {
                var resource = new ResourceLocation("gtl_enhancedcore", "lang/" + lang + ".json");
                String intro;
                try (var reader = mc.getResourceManager().openAsReader(resource)) {
                    intro = JsonParser.parseReader(reader).getAsJsonObject()
                            .get("tooltip.gtl_enhancedcore.creative_computation_receiver_hatch.intro").getAsString();
                }
                check(!intro.contains("2,100,000,000") && (intro.contains("无限") || intro.toLowerCase(java.util.Locale.ROOT).contains("unlimited")),
                        lang + " shipped language resource declares unlimited computation without the old numeric cap");
            }
            result.put("suppliedBigInteger", huge.toString());
            result.put("sameTickCommitted", twiceLong.toString());
            result.put("status", "PASS");
        } catch (Throwable failure) {
            result.put("status", "FAIL"); result.put("error", failure.toString()); failure.printStackTrace();
        } finally { holders.forEach(holder -> holder.setLevel(null)); }
        result.put("checks", checks);
        result.put("scope", "Actual registered creative hatch/container and transformed GTLCore scheduler in controlled synthetic world; no player save, physical structure matching or production recipe chain");
        try { Files.writeString(directory.resolve("creative-computation.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result)); }
        catch (Exception failure) { failure.printStackTrace(); }
        mc.stop();
    }

    private static MetaMachineBlockEntity place(String id, BlockPos pos, AuditWorld world,
                                                List<MetaMachineBlockEntity> holders) {
        var definition = GTRegistries.MACHINES.get(new ResourceLocation(id));
        if (definition == null) throw new AssertionError("Missing registered fixture: " + id);
        var holder = (MetaMachineBlockEntity) definition.getBlockEntityType().create(pos, definition.defaultBlockState());
        holder.setLevel(world); world.blockEntities.put(pos, holder);
        world.renderedBlocks.put(pos, new BlockInfo(definition.defaultBlockState())); holders.add(holder);
        return holder;
    }

    private static AuditWorld world() throws Exception {
        var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); unsafeField.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        var world = (AuditWorld) unsafe.allocateInstance(AuditWorld.class);
        set(world, "blockEntities", new HashMap<>()); set(world, "renderedBlocks", new HashMap<>());
        set(world, "proxyWorld", new java.lang.ref.WeakReference<Level>(null));
        ObfuscationReflectionHelper.setPrivateValue(Level.class, world, false, "f_46443_");
        ObfuscationReflectionHelper.setPrivateValue(Level.class, world, Level.OVERWORLD, "f_46421_");
        world.loaded = true; world.auditTick = 1000;
        return world;
    }

    private static void set(Object object, String name, Object value) throws Exception {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(object, value); return; }
            catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }

    private static final class AuditWorld extends TrackedDummyWorld {
        boolean loaded; long auditTick;
        private AuditWorld() { super(null); }
        @Override public Level getLevel() { return this; }
        @Override public RegistryAccess registryAccess() { return RegistryAccess.EMPTY; }
        @Override public FeatureFlagSet enabledFeatures() { return FeatureFlags.DEFAULT_FLAGS; }
        @Override public net.minecraft.world.item.crafting.RecipeManager getRecipeManager() { return new net.minecraft.world.item.crafting.RecipeManager(); }
        @Override public long getGameTime() { return auditTick; }
        @Override public boolean hasChunkAt(BlockPos pos) { return loaded; }
        @Override public BlockPos getSharedSpawnPos() { return BlockPos.ZERO; }
        @Override public void blockEntityChanged(BlockPos pos) {}
        @Override public void setBlocksDirty(BlockPos pos, BlockState before, BlockState after) {}
        @Override public BlockState getBlockState(BlockPos pos) {
            var block = renderedBlocks.get(pos); return block == null ? Blocks.AIR.defaultBlockState() : block.getBlockState();
        }
        @Override public BlockEntity getBlockEntity(BlockPos pos) { return blockEntities.get(pos); }
    }
}
