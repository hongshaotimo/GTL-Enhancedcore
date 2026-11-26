package com.gtl.enhancedcore.audit;

import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.data.GTLEnhancedcoreItems;
import com.gtl.enhancedcore.common.item.PatternGeneratorBehavior;
import com.gtl.enhancedcore.common.registration.RetiredDebugPatternTool;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
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

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }
}
