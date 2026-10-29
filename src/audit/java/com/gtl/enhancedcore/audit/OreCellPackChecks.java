package com.gtl.enhancedcore.audit;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.me.cells.BasicCellInventory;
import com.google.gson.GsonBuilder;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.registries.*;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;

/** Production KJS reload and real AE cell inventory in a fresh marked world. */
@Mod("enhancedcore_audit")
public final class OreCellPackChecks {
    private final List<String> checks = new ArrayList<>();
    private final Map<String, Object> report = new LinkedHashMap<>();
    private int state, ticks;
    private CompletableFuture<Void> pending;
    public OreCellPackChecks() { MinecraftForge.EVENT_BUS.addListener(this::tick); }
    private void check(boolean valid, String label) {
        if (!valid) throw new AssertionError(label);
        checks.add(label);
    }
    private void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("gtl.oreCellPackAudit") || state == 9 || event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        var dir = mc.gameDirectory.toPath().toAbsolutePath().normalize();
        try {
            if (state == 0) {
                if (mc.getOverlay() != null || mc.screen == null) return;
                check(dir.startsWith(Path.of("C:/IDEA/GTL-Enhancedcore/_audit/ore-cell-pack-fix-20261029"))
                        && Files.isRegularFile(dir.resolve("ore-pack.marker")) && mc.level == null
                        && !Files.exists(dir.resolve("saves/ore-pack-audit")), "Marked fixture and fresh world; no player save");
                state = 1;
                mc.createWorldOpenFlows().createFreshLevel("ore-pack-audit", new LevelSettings("Ore Pack Audit", GameType.CREATIVE,
                        false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT), new WorldOptions(291029L, false, false),
                        registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
                return;
            }
            if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
            if (state == 1) {
                var server = mc.getSingleplayerServer();
                pending = CompletableFuture.runAsync(() -> {
                    var recipes = server.getRecipeManager().getRecipes().stream().filter(r -> r.getId().getNamespace().equals("kubejs")
                            && r.getId().getPath().contains("infinity_ore_")).toList();
                    check(recipes.size() == 1, "Exactly one ore pack recipe after real KJS registration: " + recipes.size());
                    var recipe = (GTRecipe) recipes.getFirst();
                    check(recipe.getType().toString().contains("electric_implosion_compressor"), "Single recipe uses electric implosion compressor");
                    var outputs = recipe.getOutputContents(ItemRecipeCapability.CAP);
                    check(outputs.size() == 1, "Single output slot");
                    var pack = ItemRecipeCapability.CAP.of(outputs.getFirst().content).getItems()[0].copy();
                    check(BuiltInRegistries.ITEM.getKey(pack.getItem()).toString().equals("ae2:portable_item_cell_16k") && pack.getCount() == 1,
                            "Output is one portable 16k item cell");
                    var inputIds = new TreeSet<String>();
                    for (var content : recipe.getInputContents(ItemRecipeCapability.CAP)) {
                        check(content.chance == 0, "Input is not consumed");
                        for (var stack : ItemRecipeCapability.CAP.of(content.content).getItems()) {
                            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(); inputIds.add(id);
                            if (id.equals("gtceu:programmed_circuit")) check(stack.getTag().getInt("Configuration") == 1, "Circuit configuration is 1");
                        }
                    }
                    check(inputIds.equals(Set.of("kubejs:cosmic_fragment", "gtceu:programmed_circuit")), "Only circuit and cosmic fragment inputs");
                    var prefixes = List.of("moon_stone", "mars_stone", "venus_stone", "mercury_stone", "glacio_stone", "titanstone", "plutostone", "iostone",
                            "ganymedestone", "enceladusstone", "ceresstone", "granite", "diorite", "andesite", "red_granite", "marble", "deepslate", "tuff", "sand",
                            "red_sand", "gravel", "basalt", "netherrack", "blackstone", "endstone", "basaltic", "granitic", "mars", "glacio", "venus", "moon");
                    var expected = new TreeSet<String>();
                    var ores = BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, new ResourceLocation("forge:ores"))).orElseThrow();
                    ores.forEach(holder -> {
                        var id = BuiltInRegistries.ITEM.getKey(holder.value()); String path = id.getPath();
                        String base = path.endsWith("_ore") ? path.substring(0, path.length() - 4) : path;
                        if (prefixes.stream().noneMatch(prefix -> base.startsWith(prefix + "_"))) expected.add(id.toString());
                    });
                    check(expected.size() > 63, "Real ore catalog covers more than 63 types: " + expected.size());
                    var tag = pack.getTag(); var keys = tag.getList("keys", Tag.TAG_COMPOUND); var amounts = tag.getLongArray("amts");
                    var actual = new TreeSet<String>();
                    for (int index = 0; index < keys.size(); index++) {
                        var key = keys.getCompound(index);
                        check(key.getString("id").equals("expatternprovider:infinity_cell") && amounts[index] == 1, "One infinite cell for entry " + index);
                        actual.add(key.getCompound("tag").getCompound("record").getString("id"));
                    }
                    check(actual.equals(expected) && keys.size() == actual.size() && amounts.length == keys.size() && tag.getLong("ic") == expected.size(),
                            "Pack keys, counts and complete original base ore catalog agree");
                    var inventory = BasicCellInventory.createInventory(pack, null);
                    var counter = new KeyCounter(); inventory.getAvailableStacks(counter);
                    check(counter.size() == expected.size(), "Real AE inventory reads every packed type: " + counter.size());
                    inventory.persist(); var reloaded = BasicCellInventory.createInventory(pack.copy(), null);
                    var restored = new KeyCounter(); reloaded.getAvailableStacks(restored);
                    check(restored.size() == expected.size(), "AE persist and reopen retain every type");
                    for (var entry : restored) check(reloaded.extract(entry.getKey(), 1, Actionable.MODULATE, IActionSource.empty()) == 1, "Extractable real infinite cell " + entry.getKey());
                    var remaining = new KeyCounter(); reloaded.getAvailableStacks(remaining);
                    check(remaining.isEmpty(), "Every packed infinite cell can be extracted");
                    report.put("oreCount", expected.size()); report.put("ores", expected); report.put("recipeId", recipe.id.toString());
                    try { Files.writeString(dir.resolve("output-pack.snbt"), pack.save(new net.minecraft.nbt.CompoundTag()).toString()); }
                    catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
                    var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                    player.getInventory().selected = 0; player.getInventory().setItem(0, pack.copy());
                    pack.getItem().use(server.overworld(), player, InteractionHand.MAIN_HAND);
                }, server);
                state = 2;
            } else if (state == 2 && pending.isDone()) {
                pending.join(); state = 3; ticks = 0;
            } else if (state == 3 && ++ticks >= 40) {
                check(mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>, "Actual portable cell menu opens");
                Screenshot.grab(dir.toFile(), "all-ores-pack.png", mc.getMainRenderTarget(), ignored -> {});
                finish(mc, dir, null);
            }
        } catch (Throwable failure) { finish(mc, dir, failure); }
    }
    private void finish(Minecraft mc, Path dir, Throwable failure) {
        state = 9; report.put("status", failure == null ? "PASS" : "FAIL"); report.put("checks", checks);
        if (failure != null) { report.put("error", failure.toString()); failure.printStackTrace(); }
        try { Files.writeString(dir.resolve("ore-pack.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
        catch (Exception error) { error.printStackTrace(); }
        mc.stop();
    }
}
