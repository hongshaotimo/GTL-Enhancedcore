package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.gregtechceu.gtceu.api.item.LampBlockItem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.subtypes.UidContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** One marked fresh-world session for the current release's four narrowly scoped changes. */
@Mod("enhancedcore_audit")
public final class ReleaseReviewChecks {
    private static final Path ROOT = Path.of("C:/IDEA/GTL-Enhancedcore/_audit/release-review-20261117");
    private String world;
    private final Map<String, Object> evidence = new LinkedHashMap<>();
    private Path directory;
    private int stage, ticks, settled;
    private volatile long serverTicks;
    private long waitFrom;
    private boolean done, opened;
    private volatile boolean serverDone, serverStopped;
    private volatile Throwable serverFailure;
    private String failure;

    public ReleaseReviewChecks() {
        MinecraftForge.EVENT_BUS.addListener(this::tick);
        MinecraftForge.EVENT_BUS.addListener(this::serverTick);
        MinecraftForge.EVENT_BUS.addListener(this::stopped);
    }

    private void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) serverTicks++;
    }
    private void stopped(ServerStoppedEvent event) { serverStopped = true; }

    private void tick(TickEvent.ClientTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END || !Boolean.getBoolean("gtl.enhancedcore.releaseReview")) return;
        var mc = Minecraft.getInstance();
        try {
            if (++ticks > 18000) throw new AssertionError("Release review timeout at stage " + stage);
            if (serverFailure != null) throw new AssertionError("Server check failed", serverFailure);
            if (mc.getOverlay() != null) return;
            switch (stage) {
                case 0 -> {
                    directory = mc.gameDirectory.toPath().toAbsolutePath().normalize();
                    require(ROOT.equals(directory.getParent()) && Files.isRegularFile(directory.resolve("release-review.marker")), "Marked isolated game directory required");
                    world = "release-review-" + directory.getFileName();
                    if (mc.screen instanceof com.gtl.enhancedcore.client.gui.ChangelogScreen) { mc.screen.onClose(); return; }
                    if (!(mc.screen instanceof TitleScreen)) return;
                    require(mc.level == null && mc.getSingleplayerServer() == null && mc.getConnection() == null
                            && !Files.exists(directory.resolve("saves/" + world)), "Only a brand-new isolated world may be opened");
                    evidence.put("stellarOmega", StellarOmegaChecks.run());
                    var rules = new GameRules();
                    rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
                    rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
                    rules.getRule(GameRules.RULE_RANDOMTICKING).set(0, null);
                    stage = 1;
                    mc.createWorldOpenFlows().createFreshLevel(world, new LevelSettings("Release review", GameType.CREATIVE,
                            false, Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT),
                            new WorldOptions(20261117532L, false, false),
                            r -> r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
                }
                case 1 -> {
                    if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null || PreviewJeiAudit.runtime == null) return;
                    mc.setScreen(null); mc.options.pauseOnLostFocus = false;
                    opened = true; settled = 0; stage = 2;
                }
                case 2 -> {
                    if (++settled < 40) return;
                    lamps();
                    server(mc.getSingleplayerServer(), server -> {
                        ClaimReplacementChecks.prepare(server);
                        AssemblyMirrorPermissionChecks.prepare(server);
                    });
                    stage = 3;
                }
                case 3 -> {
                    if (!serverDone) return;
                    waitFrom = serverTicks; stage = 4;
                }
                case 4 -> {
                    if (serverTicks - waitFrom < 20) return;
                    server(mc.getSingleplayerServer(), AssemblyMirrorPermissionChecks::connect);
                    stage = 5;
                }
                case 5 -> {
                    if (!serverDone) return;
                    waitFrom = serverTicks; stage = 6;
                }
                case 6 -> {
                    if (serverTicks - waitFrom < 20) return;
                    server(mc.getSingleplayerServer(), server -> {
                        try {
                            evidence.put("claimReplacementNbtChecks", ClaimReplacementChecks.runNbt(server));
                            evidence.put("mirrorPermissionChecks", AssemblyMirrorPermissionChecks.run(server));
                        } finally { cleanup(); }
                    });
                    stage = 7;
                }
                case 7 -> { if (serverDone) { book(mc); disconnect(mc); } }
                case 8 -> { if (mc.level == null && mc.getSingleplayerServer() == null && serverStopped) finish(mc); }
                default -> throw new IllegalStateException("Unknown stage " + stage);
            }
        } catch (Throwable error) {
            failure = "stage " + stage + ": " + error; error.printStackTrace(); serverFailure = null;
            if (stage == 8 || mc.level == null && mc.getSingleplayerServer() == null) finish(mc); else disconnect(mc);
        }
    }

    private void lamps() {
        var helper = PreviewJeiAudit.runtime.getIngredientManager().getIngredientHelper(VanillaTypes.ITEM_STACK);
        int items = 0;
        for (var item : ForgeRegistries.ITEMS) {
            if (!(item instanceof LampBlockItem lamp)) continue;
            items++;
            for (var context : UidContext.values()) {
                var ordinary = new ItemStack(item);
                var configured = lamp.getBlock().getStackFromIndex(4);
                var decorated = configured.copy();
                decorated.getOrCreateTag().putString("ReleaseReviewUnrelated", "same-lamp-switches");
                var uid = helper.getUniqueId(ordinary, context);
                require(uid.equals(helper.getUniqueId(configured, context)) && uid.equals(helper.getUniqueId(decorated, context)),
                        "Lamp placement-equivalent stacks differ in JEI: " + ForgeRegistries.ITEMS.getKey(item) + " / " + context);
                var variants = new HashSet<String>();
                for (int i = 0; i < 8; i++) variants.add(helper.getUniqueId(lamp.getBlock().getStackFromIndex(i), context));
                require(variants.size() == 8, "Lamp switch combinations collided in JEI");
            }
        }
        require(items == 32, "Expected the complete 32-lamp registry");
        evidence.put("jeiLamps", Map.of("items", items, "variantsPerItem", 8, "contexts", UidContext.values().length,
                "noNbtConfiguredAndUnrelatedNbtSameUid", true));
    }

    private void book(Minecraft mc) throws Exception {
        var resources = mc.getResourceManager().listResources("patchouli_books/gtmah_book", id ->
                id.getNamespace().equals("gtmadvancedhatch") && id.getPath().endsWith(".json"));
        var missing = new ArrayList<Map<String, String>>();
        for (var resource : resources.entrySet()) {
            try (var reader = resource.getValue().openAsReader()) {
                bookItems(JsonParser.parseReader(reader), resource.getKey().toString(), "", missing);
            }
        }
        evidence.put("patchouliItemPreflight", Map.of("resources", resources.size(), "missing", missing));
        require(missing.isEmpty(), "AH book contains unregistered item references: " + missing);
        // Patchouli 84.1 is embedded by AH at runtime, not a compile dependency of this project.
        var registryClass = Class.forName("vazkii.patchouli.common.book.BookRegistry");
        var registry = registryClass.getField("INSTANCE").get(null);
        var books = (Map<?, ?>) registryClass.getField("books").get(registry);
        var book = books.get(new ResourceLocation("gtmadvancedhatch", "gtmah_book"));
        require(book != null, "AH Patchouli book was not registered");
        book.getClass().getMethod("reloadContents", Level.class, boolean.class).invoke(book, mc.level, false);
        var contents = book.getClass().getMethod("getContents").invoke(book);
        boolean errored = (boolean) contents.getClass().getMethod("isErrored").invoke(contents);
        var entries = (Map<?, ?>) contents.getClass().getField("entries").get(contents);
        require(!errored && !entries.isEmpty(), "AH book compiled to empty/error contents: "
                + contents.getClass().getMethod("getException").invoke(contents));
        for (String id : new String[]{"hatch/configurable_item_bus", "hatch/configurable_fluid_hatch",
                "hatch/configurable_dual_hatch", "hatch/lock_item_output_bus", "hatch/net_energy_hatch", "update/0.1.9"})
            require(entries.containsKey(new ResourceLocation("gtmadvancedhatch", id)), "AH book lost original entry " + id);
        evidence.put("patchouliBook", Map.of("errored", errored, "entries", entries.size()));
    }

    private static void bookItems(JsonElement value, String resource, String path, List<Map<String, String>> missing) {
        if (value.isJsonObject()) {
            for (var entry : value.getAsJsonObject().entrySet()) {
                String child = path + "/" + entry.getKey();
                if ((entry.getKey().equals("icon") || entry.getKey().matches("item\\d*"))
                        && entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isString()) {
                    // Split only top-level commas: a displayed stack may include compound/list NBT.
                    String stacks = entry.getValue().getAsString();
                    int start = 0, depth = 0; char quote = 0; boolean escaped = false;
                    for (int i = 0; i < stacks.length(); i++) {
                        char c = stacks.charAt(i);
                        if (quote != 0) {
                            if (escaped) escaped = false;
                            else if (c == '\\') escaped = true;
                            else if (c == quote) quote = 0;
                            continue;
                        }
                        if (c == '"' || c == '\'') { quote = c; continue; }
                        if (c == '{' || c == '[') depth++;
                        else if (c == '}' || c == ']') depth--;
                        else if (c == ',' && depth == 0) {
                            bookItemId(stacks.substring(start, i), resource, child, missing); start = i + 1;
                        }
                    }
                    bookItemId(stacks.substring(start), resource, child, missing);
                }
                if (entry.getKey().equals("text") && entry.getValue().isJsonPrimitive()
                        && entry.getValue().getAsJsonPrimitive().isString()) {
                    // Formatting/link/key macros are not item IDs; inspect explicit IDs in prose.
                    String text = entry.getValue().getAsString().replaceAll("\\$\\([^)]*\\)", "");
                    var ids = java.util.regex.Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+").matcher(text);
                    while (ids.find()) bookItemId(ids.group(), resource, child, missing);
                }
                bookItems(entry.getValue(), resource, child, missing);
            }
        } else if (value.isJsonArray()) {
            for (int i = 0; i < value.getAsJsonArray().size(); i++)
                bookItems(value.getAsJsonArray().get(i), resource, path + "/" + i, missing);
        }
    }

    private static void bookItemId(String stack, String resource, String field, List<Map<String, String>> missing) {
        stack = stack.trim();
        if (stack.isEmpty() || stack.startsWith("#")) return;
        var match = java.util.regex.Pattern.compile("^([a-z0-9_.-]+:[a-z0-9_./-]+)").matcher(stack);
        String id = match.find() ? match.group(1) : stack;
        var location = ResourceLocation.tryParse(id);
        if (location == null || !ForgeRegistries.ITEMS.containsKey(location))
            missing.add(Map.of("resource", resource, "field", field, "item", id));
    }

    private void server(MinecraftServer server, ServerAction action) {
        serverDone = false;
        server.execute(() -> {
            try { action.run(server); }
            catch (Throwable error) {
                serverFailure = error;
                try { cleanup(); } catch (Throwable cleanupError) { error.addSuppressed(cleanupError); }
            } finally { serverDone = true; }
        });
    }
    private static void cleanup() throws Exception {
        try { AssemblyMirrorPermissionChecks.cleanup(); } finally { ClaimReplacementChecks.cleanup(); }
    }
    private void disconnect(Minecraft mc) {
        stage = 8;
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
            Files.writeString(directory.resolve("release-review.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result));
        } catch (Exception error) { error.printStackTrace(); }
        mc.stop();
    }
    @FunctionalInterface private interface ServerAction { void run(MinecraftServer server) throws Exception; }
}
