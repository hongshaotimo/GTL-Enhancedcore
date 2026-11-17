package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtl.enhancedcore.common.recipe.iv.IvRecipeInputs;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** One fresh-world session for the concrete fixes found by the wider project review. */
@Mod("enhancedcore_audit")
public final class ProjectReviewChecks {
    private static final Path ROOT = Path.of("C:/IDEA/GTL-Enhancedcore/_audit/project-review-20261117");
    private final Map<String, Object> evidence = new LinkedHashMap<>();
    private Path directory;
    private String world, failure;
    private int stage, ticks, settled;
    private boolean done, opened;
    private volatile boolean serverDone, serverStopped;
    private volatile Throwable serverFailure;

    public ProjectReviewChecks() {
        MinecraftForge.EVENT_BUS.addListener(this::tick);
        MinecraftForge.EVENT_BUS.addListener(this::stopped);
    }

    private void stopped(ServerStoppedEvent event) { serverStopped = true; }

    private void tick(TickEvent.ClientTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END || !Boolean.getBoolean("gtl.enhancedcore.projectReview")) return;
        var mc = Minecraft.getInstance();
        try {
            if (++ticks > 18000) throw new AssertionError("Project review timeout at stage " + stage);
            if (serverFailure != null) throw new AssertionError("Server check failed", serverFailure);
            if (mc.getOverlay() != null) return;
            switch (stage) {
                case 0 -> {
                    directory = mc.gameDirectory.toPath().toAbsolutePath().normalize();
                    require(ROOT.equals(directory.getParent()) && Files.isRegularFile(directory.resolve("project-review.marker")),
                            "Marked isolated game directory required");
                    world = "project-review-" + directory.getFileName();
                    if (mc.screen instanceof com.gtl.enhancedcore.client.gui.ChangelogScreen) { mc.screen.onClose(); return; }
                    if (!(mc.screen instanceof TitleScreen)) return;
                    require(mc.level == null && mc.getSingleplayerServer() == null && mc.getConnection() == null
                            && !Files.exists(directory.resolve("saves/" + world)), "Only a brand-new isolated world may be opened");
                    var rules = new GameRules();
                    rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
                    rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
                    rules.getRule(GameRules.RULE_RANDOMTICKING).set(0, null);
                    stage = 1;
                    mc.createWorldOpenFlows().createFreshLevel(world, new LevelSettings("Project review", GameType.CREATIVE,
                            false, Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT),
                            new WorldOptions(20261117533L, false, false),
                            r -> r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
                }
                case 1 -> {
                    if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null || PreviewJeiAudit.runtime == null) return;
                    mc.setScreen(null); mc.options.pauseOnLostFocus = false;
                    opened = true; stage = 2;
                }
                case 2 -> {
                    if (++settled < 40) return;
                    boolean fusionOnly = Boolean.getBoolean("gtl.enhancedcore.projectReviewFusionOnly");
                    if (!fusionOnly) {
                        evidence.put("copyAndCreativeAcceleration", CopyKeysClientChecks.run());
                        evidence.put("ivTimeAcceleration", IvTimeAccelerationChecks.run());
                    }
                    serverDone = false;
                    var server = mc.getSingleplayerServer();
                    stage = 3;
                    server.execute(() -> {
                        try {
                            if (!fusionOnly) {
                                try {
                                    ClaimReplacementChecks.prepare(server);
                                    evidence.put("multiblockReplacementChecks", ClaimReplacementChecks.runMultiblock(server));
                                } finally { ClaimReplacementChecks.cleanup(); }
                                evidence.put("machinePermissionChecks", MachinePacketPermissionChecks.run(server));
                                evidence.put("coreRecipeInputs", recipeInputs(server));
                            }
                            evidence.put("fusionDisplay", FusionDisplayChecks.run(server));
                        } catch (Throwable error) { serverFailure = error; }
                        finally { serverDone = true; }
                    });
                }
                case 3 -> {
                    if (!serverDone) return;
                    mc.options.guiScale().set(2); mc.resizeDisplay();
                    mc.setScreen(FusionDisplayChecks.prepareUi()); settled = 0; stage = 4;
                }
                case 4 -> {
                    if (++settled < 12) return;
                    try (var screenshot = net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                        screenshot.writeToFile(directory.resolve("fusion-one-parallel.png"));
                    }
                    evidence.put("fusionScreenshot", "fusion-one-parallel.png");
                    mc.setScreen(null); disconnect(mc);
                }
                case 5 -> { if (mc.level == null && mc.getSingleplayerServer() == null && serverStopped) finish(mc); }
                default -> throw new IllegalStateException("Unknown stage " + stage);
            }
        } catch (Throwable error) {
            failure = "stage " + stage + ": " + error;
            error.printStackTrace(); serverFailure = null;
            if (stage == 5 || mc.level == null && mc.getSingleplayerServer() == null) finish(mc); else disconnect(mc);
        }
    }

    private static Map<String, Object> recipeInputs(MinecraftServer server) {
        var result = new LinkedHashMap<String, Object>();
        input(server, "block_bus", "kubejs:essence_block", 1, result);
        input(server, "conversion_simulate_card", "ae2:speed_card", 2, result);
        return result;
    }

    private static void input(MinecraftServer server, String path, String itemId, long expected, Map<String, Object> result) {
        var recipes = server.getRecipeManager().getRecipes().stream().filter(GTRecipe.class::isInstance)
                .map(GTRecipe.class::cast).filter(recipe -> recipe.getId().getPath().equals(path)
                        || recipe.getId().getPath().endsWith("/" + path)).toList();
        require(recipes.size() == 1, "Expected one final recipe for " + path + ": " + recipes.stream().map(GTRecipe::getId).toList());
        var id = new ResourceLocation(itemId);
        require(ForgeRegistries.ITEMS.containsKey(id), "Required input item not registered: " + id);
        var item = ForgeRegistries.ITEMS.getValue(id);
        long amount = 0;
        for (var content : recipes.getFirst().inputs.getOrDefault(ItemRecipeCapability.CAP, java.util.List.of())) {
            var ingredient = ItemRecipeCapability.CAP.of(content.content);
            if (ingredient.test(item.getDefaultInstance())) amount += IvRecipeInputs.itemAmount(ingredient);
        }
        require(amount == expected, "Final recipe " + recipes.getFirst().getId() + " has " + amount + " of " + itemId + ", expected " + expected);
        result.put(path, Map.of("recipe", recipes.getFirst().getId().toString(), "item", itemId, "amount", amount));
    }

    private void disconnect(Minecraft mc) {
        stage = 5;
        if (mc.level != null) mc.level.disconnect();
        mc.clearLevel(); mc.setScreen(new TitleScreen());
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private void finish(Minecraft mc) {
        done = true;
        try {
            var result = new LinkedHashMap<String, Object>();
            result.put("status", failure == null && opened && serverStopped ? "PASS" : "FAIL");
            result.put("failure", failure); result.put("evidence", evidence);
            result.put("worldOpened", opened); result.put("world", world); result.put("serverStopped", serverStopped);
            Files.writeString(directory.resolve("project-review.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result));
        } catch (Exception error) { error.printStackTrace(); }
        mc.stop();
    }
}
