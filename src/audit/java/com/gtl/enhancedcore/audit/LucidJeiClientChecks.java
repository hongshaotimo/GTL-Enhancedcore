package com.gtl.enhancedcore.audit;

import com.gregtechceu.gtceu.api.block.IMachineBlock;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.integration.jei.multipage.MultiblockInfoCategory;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.mixin.gtceu.BlockPatternAccessor;
import com.lowdragmc.lowdraglib.gui.widget.SceneWidget;
import com.lowdragmc.lowdraglib.jei.ModularWrapper;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.gtlcore.gtlcore.api.gui.PatternPreviewWidget;

/** Checks the actual JEI lookup, preview widget and rendered page in an isolated client world. */
@Mod.EventBusSubscriber(modid = "enhancedcore_audit", value = Dist.CLIENT)
public final class LucidJeiClientChecks {
    private static final ResourceLocation LUCID_ID = new ResourceLocation("gtladditions:lucid_etchdreamer");
    private static final ResourceLocation CASING_ID = new ResourceLocation("gtlcore:iridium_casing");
    private static boolean done;
    private static boolean opened;
    private static int ticks;
    private static int frames;
    private static long openedAt;
    private static SceneWidget scene;

    private LucidJeiClientChecks() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (done || !Boolean.getBoolean("gtl.enhancedcore.lucidJeiAudit")
                || event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        try {
            mc.options.pauseOnLostFocus = false;
            if (mc.level == null || mc.player == null || PreviewJeiAudit.runtime == null) return;
            if (!opened && ++ticks < 100) return;
            if (!opened) open();
            if (opened && System.nanoTime() - openedAt > 180_000_000_000L) {
                throw new AssertionError("JEI preview did not finish rendering within 180 seconds");
            }
        } catch (Throwable error) {
            done = true;
            GTLEnhancedcore.LOGGER.error("[LUCID_JEI_CLIENT] FAIL", error);
            mc.stop();
        }
    }

    private static void open() throws Exception {
        var definition = (MultiblockMachineDefinition) GTRegistries.MACHINES.get(LUCID_ID);
        if (definition == null || !definition.isRenderXEIPreview()) {
            throw new AssertionError("Lucid machine is absent or JEI preview is disabled");
        }
        var manager = PreviewJeiAudit.runtime.getRecipeManager();
        var wrappers = manager.createRecipeLookup(MultiblockInfoCategory.RECIPE_TYPE).get()
                .filter(recipe -> recipe.definition == definition).toList();
        GTLEnhancedcore.LOGGER.info("[LUCID_JEI_CLIENT] recipeWrappers={}", wrappers.size());
        if (wrappers.size() != 1) throw new AssertionError("Missing or duplicate JEI structure page");
        var wrapper = wrappers.getFirst();
        var widget = (PatternPreviewWidget) ((ModularWrapper<?>) wrapper).getWidget();
        Field patterns = PatternPreviewWidget.class.getDeclaredField("patterns");
        patterns.setAccessible(true);
        Object[] pagePatterns = (Object[]) patterns.get(widget);
        int pages = pagePatterns.length;
        GTLEnhancedcore.LOGGER.info("[LUCID_JEI_CLIENT] previewPages={}", pages);
        if (pages != 1) throw new AssertionError("JEI structure widget has no rendered pattern");
        checkHatchPanel(definition);
        checkJeiPage(pagePatterns[0], definition);
        Field sceneField = PatternPreviewWidget.class.getDeclaredField("sceneWidget");
        sceneField.setAccessible(true);
        scene = (SceneWidget) sceneField.get(widget);
        scene.useCacheBuffer();
        PreviewJeiAudit.runtime.getRecipesGui().showRecipes(
                manager.getRecipeCategory(MultiblockInfoCategory.RECIPE_TYPE), List.of(wrapper), List.of());
        openedAt = System.nanoTime();
        opened = true;
        GTLEnhancedcore.LOGGER.info("[LUCID_JEI_CLIENT] OPEN");
    }

    private static void checkHatchPanel(MultiblockMachineDefinition definition) {
        var pattern = definition.getPatternFactory().get();
        var matches = ((BlockPatternAccessor) pattern).gtlEnhancedcore$getBlockMatches();
        if (matches.length != 233 || matches[156].length != 233 || matches[156][104].length != 233
                || !matches[156][104][84].isController) {
            throw new AssertionError("Lucid controller predicate or panel dimensions changed");
        }
        Block casing = requiredBlock(CASING_ID);
        Block legacyController = requiredBlock(LUCID_ID);
        int hatchSlots = 0;
        for (int y = 102; y <= 106; y++) {
            for (int x = 82; x <= 86; x++) {
                if (x == 84 && y == 104) continue;
                var predicate = matches[156][y][x];
                if (predicate == null || predicate.isController
                        || !predicate.test(new SampleState(casing.defaultBlockState()))
                        || !predicate.test(new SampleState(legacyController.defaultBlockState()))) {
                    throw new AssertionError("Lucid X predicate rejects casing or legacy controller at " + x + "," + y);
                }
                hatchSlots++;
            }
        }
        if (hatchSlots != 24) throw new AssertionError("Lucid hatch panel has " + hatchSlots + " X positions");
        GTLEnhancedcore.LOGGER.info(
                "[LUCID_JEI_CLIENT] hatchSlots={} casingAllowed=true legacyControllerAllowed=true", hatchSlots);
    }

    private static void checkJeiPage(Object page, MultiblockMachineDefinition definition) throws Exception {
        if (page == null) throw new AssertionError("Lucid JEI page is null");
        Field blockMapField = page.getClass().getDeclaredField("blockMap");
        blockMapField.setAccessible(true);
        Map<?, ?> blocks = (Map<?, ?>) blockMapField.get(page);
        BlockPos controllerPos = null;
        int controllerBlocks = 0;
        for (var entry : blocks.entrySet()) {
            if (entry.getValue() instanceof BlockInfo info
                    && info.getBlockState().getBlock() instanceof IMachineBlock machineBlock
                    && machineBlock.getDefinition() == definition) {
                controllerPos = (BlockPos) entry.getKey();
                controllerBlocks++;
            }
        }
        if (controllerBlocks != 1) {
            throw new AssertionError("JEI preview contains " + controllerBlocks + " lucid controller blocks");
        }
        Block casing = requiredBlock(CASING_ID);
        int casingSlots = 0;
        int hatchSlots = 0;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                if (dx == 0 && dy == 0) continue;
                Object value = blocks.get(controllerPos.offset(dx, dy, 0));
                if (!(value instanceof BlockInfo info)) {
                    throw new AssertionError("JEI X preview is empty at " + dx + "," + dy);
                }
                if (info.getBlockState().is(casing)) {
                    if (info.hasBlockEntity()) {
                        throw new AssertionError("JEI X casing unexpectedly has a block entity");
                    }
                    casingSlots++;
                } else {
                    if (!info.hasBlockEntity()) {
                        throw new AssertionError("JEI X hatch lacks its block entity at " + dx + "," + dy);
                    }
                    hatchSlots++;
                }
            }
        }
        if (casingSlots != 20 || hatchSlots != 4) {
            throw new AssertionError("JEI preview has " + casingSlots + " X casings and " + hatchSlots + " hatches");
        }

        Field partsField = page.getClass().getDeclaredField("parts");
        partsField.setAccessible(true);
        int controllerItems = 0;
        for (Object group : (List<?>) partsField.get(page)) {
            for (Object value : (List<?>) group) {
                if (value instanceof ItemStack stack && stack.is(requiredBlock(LUCID_ID).asItem())) {
                    controllerItems += stack.getCount();
                }
            }
        }
        if (controllerItems != 1) {
            throw new AssertionError("JEI material list counts " + controllerItems + " lucid controllers");
        }
        GTLEnhancedcore.LOGGER.info(
                "[LUCID_JEI_CLIENT] controllerBlocks={} controllerItems={} xCasings={} xHatches={}",
                controllerBlocks, controllerItems, casingSlots, hatchSlots);
    }

    private static Block requiredBlock(ResourceLocation id) {
        if (!ForgeRegistries.BLOCKS.containsKey(id)) throw new AssertionError("Missing audit block " + id);
        return ForgeRegistries.BLOCKS.getValue(id);
    }

    private static final class SampleState extends MultiblockState {
        private final BlockState current;

        SampleState(BlockState current) {
            super(null, BlockPos.ZERO);
            clean();
            this.current = current;
            setError(null);
        }

        @Override public BlockState getBlockState() { return current; }
    }

    @SubscribeEvent
    public static void frame(TickEvent.RenderTickEvent event) {
        if (done || !opened || !Boolean.getBoolean("gtl.enhancedcore.lucidJeiAudit")
                || event.phase != TickEvent.Phase.END) return;
        try {
            if (scene.getRenderer().isCompiling() || ++frames < 20) return;
            int positions = scene.getRenderer().renderedBlocksMap.keySet().stream()
                    .mapToInt(java.util.Collection::size).sum();
            if (positions < 300_000) throw new AssertionError("JEI page rendered too few blocks: " + positions);
            var mc = Minecraft.getInstance();
            Screenshot.grab(mc.gameDirectory, "lucid-jei-preview.png", mc.getMainRenderTarget(), message -> {});
            GTLEnhancedcore.LOGGER.info("[LUCID_JEI_CLIENT] COMPLETE positions={} frames={}", positions, frames);
            done = true;
            mc.stop();
        } catch (Throwable error) {
            done = true;
            GTLEnhancedcore.LOGGER.error("[LUCID_JEI_CLIENT] FAIL", error);
            Minecraft.getInstance().stop();
        }
    }
}
