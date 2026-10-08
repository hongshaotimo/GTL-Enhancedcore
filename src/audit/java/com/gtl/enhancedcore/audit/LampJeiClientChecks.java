package com.gtl.enhancedcore.audit;

import appeng.api.stacks.AEItemKey;
import com.gregtechceu.gtceu.api.item.LampBlockItem;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.block.LampBlock;
import com.gregtechceu.gtceu.integration.jei.multipage.MultiblockInfoCategory;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.item.LampConfiguration;
import com.gtl.enhancedcore.integration.terminal.LampPlacement;
import com.lowdragmc.lowdraglib.gui.widget.SceneWidget;
import com.lowdragmc.lowdraglib.jei.ModularWrapper;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import java.lang.reflect.Field;
import java.util.*;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.gtlcore.gtlcore.api.gui.PatternPreviewWidget;

/** Opt-in JEI registry, focused recipe, native giant material-list and AE pattern checks. */
@Mod.EventBusSubscriber(modid = "enhancedcore_audit", value = Dist.CLIENT)
public final class LampJeiClientChecks {
    private static boolean connecting, done;
    private static int settle, checks;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (done || !Boolean.getBoolean("gtl.enhancedcore.lampAudit") || event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        try {
            mc.options.pauseOnLostFocus = false;
            if (!connecting && mc.screen instanceof TitleScreen) {
                connecting = true;
                ConnectScreen.startConnecting(mc.screen, mc, ServerAddress.parseString("127.0.0.1:25654"),
                        new ServerData("Lamp audit", "127.0.0.1:25654", false), false);
            }
            if (mc.level == null || mc.player == null || PreviewJeiAudit.runtime == null || ++settle < 100) return;
            done = true;
            ingredients();
            materials("gtl_enhancedcore:dragon_field_proliferation_core");
            materials("gtl_enhancedcore:infinity_singularity_compressor");
            materials("gtceu:space_elevator");
            materials("gtladditions:space_elevator_mkii");
            log("COMPLETE checks=" + checks);
            mc.stop();
        } catch (Throwable error) {
            done = true;
            GTLEnhancedcore.LOGGER.error("[LAMP_CLIENT] FAIL", error);
            mc.stop();
        }
    }

    private static void ingredients() {
        var runtime = PreviewJeiAudit.runtime;
        var manager = runtime.getIngredientManager();
        var helper = manager.getIngredientHelper(VanillaTypes.ITEM_STACK);
        Set<String> global = new HashSet<>();
        int items = 0, focused = 0;
        for (var item : ForgeRegistries.ITEMS) {
            if (!(item instanceof LampBlockItem lamp)) continue;
            items++;
            var variants = manager.getAllItemStacks().stream().filter(s -> s.is(item)).toList();
            Set<String> available = new HashSet<>();
            for (var stack : variants) available.add(helper.getUniqueId(stack, UidContext.Ingredient));
            check(available.size() == 8, "Missing/extra JEI variants for " + ForgeRegistries.ITEMS.getKey(item) + ": " + available);
            for (int i = 0; i < 8; i++) {
                var expected = lamp.getBlock().getStackFromIndex(i);
                var uid = helper.getUniqueId(expected, UidContext.Ingredient);
                check(global.add(uid) && available.contains(uid), "JEI color/border/configuration collision");
                var canonical = manager.getIngredientByUid(VanillaTypes.ITEM_STACK, uid).orElseThrow();
                check(LampConfiguration.subtype(canonical).equals(LampConfiguration.subtype(expected)), "Canonical JEI stack changed switches");
                SteamLampElevatorChecks.pattern(Minecraft.getInstance().level, canonical);
                var focus = runtime.getJeiHelpers().getFocusFactory().createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, expected);
                int matching = 0;
                for (var category : runtime.getRecipeManager().createRecipeCategoryLookup().limitFocus(List.of(focus)).get().toList())
                    matching += recipes(category, focus, expected);
                check(matching > 0, "No matching JEI recipe for lamp " + uid);
                focused++;
            }
            check(helper.getUniqueId(new ItemStack(item), UidContext.Recipe)
                    .equals(helper.getUniqueId(lamp.getBlock().getStackFromIndex(4), UidContext.Recipe)), "Ordinary no-NBT lamp lookup differs");
        }
        check(items == 32 && global.size() == 256, "Incomplete registry coverage");
        log("registered_items=32 variants=256 focused_recipe_lookups=" + focused + " ordinary_noNBT_AE_patterns=OK");
    }

    private static <T> int recipes(IRecipeCategory<T> category, IFocus<ItemStack> focus, ItemStack expected) {
        var runtime = PreviewJeiAudit.runtime;
        var manager = runtime.getRecipeManager();
        int count = 0;
        var group = runtime.getJeiHelpers().getFocusFactory().createFocusGroup(List.of(focus));
        for (T recipe : manager.createRecipeLookup(category.getRecipeType()).limitFocus(List.of(focus)).get().toList()) {
            var layout = manager.createRecipeLayoutDrawable(category, recipe, group).orElseThrow();
            var outputs = layout.getRecipeSlotsView().getSlotViews(RecipeIngredientRole.OUTPUT).stream()
                    .flatMap(slot -> slot.getItemStacks()).filter(stack -> stack.getItem() instanceof LampBlockItem).toList();
            check(!outputs.isEmpty(), "No lamp in focused recipe output " + category.getRecipeType());
            for (var output : outputs) check(ItemStack.isSameItemSameTags(output, expected),
                    "Focused JEI recipe changed lamp NBT " + category.getRecipeType() + ": " + output.getTag()
                            + " expected=" + expected.getTag());
            count++;
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private static void materials(String id) throws Exception {
        var definition = (MultiblockMachineDefinition) GTRegistries.MACHINES.get(new ResourceLocation(id));
        var wrapper = PreviewJeiAudit.runtime.getRecipeManager().createRecipeLookup(MultiblockInfoCategory.RECIPE_TYPE).get()
                .filter(recipe -> recipe.definition == definition).findFirst().orElseThrow();
        var widget = (PatternPreviewWidget) ((ModularWrapper<?>) wrapper).getWidget();
        Object[] patterns = (Object[]) field(widget, "patterns");
        check(patterns.length > 0, "No JEI structure for " + id);
        for (Object pattern : patterns) {
            var blocks = (Map<BlockPos, BlockInfo>) field(pattern, "blockMap");
            var parts = (List<List<ItemStack>>) field(pattern, "parts");
            Map<AEItemKey, Long> expected = new HashMap<>(), actual = new HashMap<>();
            for (var info : blocks.values()) {
                var state = info.getBlockState();
                if (state.getBlock() instanceof LampBlock)
                    expected.merge(AEItemKey.of(LampPlacement.returnedStack(state)), 1L, Long::sum);
            }
            for (var group : parts) for (var stack : group) {
                if (stack.getItem() instanceof LampBlockItem) {
                    actual.merge(AEItemKey.of(stack), (long) stack.getCount(), Long::sum);
                    SteamLampElevatorChecks.pattern(Minecraft.getInstance().level, stack);
                }
                if (id.contains("space_elevator")) check(!stack.getDescriptionId().contains("maintenance"), "Elevator JEI still lists mandatory maintenance");
            }
            check(expected.equals(actual), "Giant materials lost exact lamp NBT/count " + id + " expected=" + expected + " actual=" + actual);
            if (id.contains("dragon_field")) check(!expected.isEmpty(), "Giant contains no test lamps");
            log("materials=" + id + " positions=" + blocks.size() + " exact_lamps=" + actual.values().stream().mapToLong(Long::longValue).sum());
        }
        var scene = (SceneWidget) field(widget, "sceneWidget");
        if (scene != null) scene.getRenderer().deleteCacheBuffer();
    }

    private static Object field(Object value, String name) throws Exception {
        Field field = value.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(value);
    }
    private static void check(boolean valid, String reason) {
        if (!valid) throw new IllegalStateException(reason);
        checks++;
    }
    private static void log(String message) { GTLEnhancedcore.LOGGER.info("[LAMP_CLIENT] {}", message); }
}
