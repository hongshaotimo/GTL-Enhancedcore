package com.gtl.enhancedcore.audit;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.common.block.CoilBlock;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.data.GTLEnhancedcoreItems;
import com.gtl.enhancedcore.common.item.PatternGeneratorBehavior;
import com.gtl.enhancedcore.common.registration.RetiredDebugPatternTool;
import com.gtl.enhancedcore.common.structure.DistorterCoils;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.MissingMappingsEvent;
import org.gtlcore.gtlcore.common.data.GTLItems;
import org.gtlcore.gtlcore.common.item.PatternTestBehavior;

/** Opt-in registry/native-predicate smoke check at the title screen; never opens a world. */
@Mod.EventBusSubscriber(modid = "enhancedcore_audit", value = Dist.CLIENT)
public final class CoilDebugToolClientChecks {
    private static boolean done;
    private static int assertions;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (done || !Boolean.getBoolean("gtl.enhancedcore.coilDebugAudit") || event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (!(mc.screen instanceof TitleScreen)) return;
        done = true;
        try {
            check(mc.level == null, "No player world opened");
            check(mc.gameDirectory.toPath().toAbsolutePath().normalize().toString().contains("coil-debug-tool-client"),
                    "Must use the isolated test directory");
            registry();
            coils();
            LucidPreviewProbe.run();
            GTLEnhancedcore.LOGGER.info("[COIL_DEBUG_CLIENT] COMPLETE assertions={}", assertions);
        } catch (Throwable error) {
            GTLEnhancedcore.LOGGER.error("[COIL_DEBUG_CLIENT] FAIL", error);
        } finally {
            mc.stop();
        }
    }

    private static void registry() throws Exception {
        check(!ForgeRegistries.ITEMS.containsKey(RetiredDebugPatternTool.OLD_ID), "Old item must not be registered");
        var replacement = GTLEnhancedcoreItems.PATTERN_GENERATOR.get();
        check(GTLItems.DEBUG_PATTERN_TEST.get() == replacement, "Legacy Java field is a valid replacement alias");
        check(replacement.getComponents().contains(PatternGeneratorBehavior.INSTANCE), "Replacement behavior retained");
        check(replacement.getComponents().stream().noneMatch(PatternTestBehavior.class::isInstance), "Old behavior not attached");
        check(GTLItems.DEBUG_STRUCTURE_WRITER.isPresent() && GTLItems.PATTERN_MODIFIER.isPresent(), "Neighboring tools retained");
        var mapping = new MissingMappingsEvent.Mapping<Item>(ForgeRegistries.ITEMS, ForgeRegistries.ITEMS,
                RetiredDebugPatternTool.OLD_ID, 999999);
        RetiredDebugPatternTool.migrate(new MissingMappingsEvent(Registries.ITEM, ForgeRegistries.ITEMS, List.of(mapping)));
        var action = MissingMappingsEvent.Mapping.class.getDeclaredField("action");
        var target = MissingMappingsEvent.Mapping.class.getDeclaredField("target");
        action.setAccessible(true);
        target.setAccessible(true);
        check(action.get(mapping) == MissingMappingsEvent.Action.REMAP && target.get(mapping) == replacement,
                "Missing-mapping handler remaps old items to the enhanced generator");
        GTLEnhancedcore.LOGGER.info("[COIL_DEBUG_CLIENT] REGISTRY removed=true replacement_behavior_only=true migration=REMAP");
    }

    private static void coils() {
        var predicate = DistorterCoils.create();
        int minimum = CoilBlock.CoilType.TRITANIUM.getCoilTemperature();
        var accepted = new HashSet<Block>();
        for (var entry : GTCEuAPI.HEATING_COILS.entrySet()) {
            var block = entry.getValue().get();
            var state = new SampleState(block.defaultBlockState());
            boolean allowed = entry.getKey().getCoilTemperature() >= minimum;
            check(predicate.test(state) == allowed, "Minimum temperature: " + entry.getKey().getName());
            if (allowed) {
                accepted.add(block);
                check(state.getMatchContext().get("CoilType") == entry.getKey(), "Actual upgraded CoilType stored");
                check(predicate.test(state), "Repeated same-type coils accepted");
            } else {
                check(state.getMatchContext().get("CoilType") == null, "Rejected coil does not poison context");
            }
        }
        check(accepted.size() > 1, "Real modpack offers higher-temperature coils");
        var candidates = new HashSet<Block>();
        Arrays.stream(predicate.common.getFirst().candidates.get())
                .forEach(info -> candidates.add(info.getBlockState().getBlock()));
        check(candidates.equals(accepted), "Preview/terminal candidates match all accepted coil types");
        for (var left : accepted) for (var right : accepted) {
            var state = new SampleState(left.defaultBlockState());
            check(predicate.test(state), "First coil accepted");
            state.current = right.defaultBlockState();
            check(predicate.test(state) == (left == right), "Mixed coil sets rejected");
        }
        check(!predicate.test(new SampleState(Blocks.AIR.defaultBlockState())), "Air is not a coil");
        GTLEnhancedcore.LOGGER.info("[COIL_DEBUG_CLIENT] COILS minimum={} accepted={} uniformity=true actual_temperature=true",
                minimum, accepted.stream().map(ForgeRegistries.BLOCKS::getKey).sorted().toList());
    }

    private static final class SampleState extends MultiblockState {
        private BlockState current;
        SampleState(BlockState current) {
            super(null, BlockPos.ZERO);
            clean();
            this.current = current;
            setError(null);
        }
        @Override public BlockState getBlockState() { return current; }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }
}
