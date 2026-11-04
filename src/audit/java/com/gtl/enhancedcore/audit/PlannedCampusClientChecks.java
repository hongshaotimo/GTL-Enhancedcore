package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;
import com.gtl.enhancedcore.client.plot.CreateWorldPlotScreen;
import com.gtl.enhancedcore.client.plot.CampusPlanScreen;
import com.gtl.enhancedcore.client.plotinfo.PersonalPlotWorldScreen;
import com.gtl.enhancedcore.common.world.personalplot.PersonalPlotNetwork;
import com.gtl.enhancedcore.common.world.personalplot.PersonalPlotWorlds;
import com.gtl.enhancedcore.common.world.plot.PlannedCampus;
import com.gtl.enhancedcore.common.world.plot.PlotChunkGenerator;
import com.gtl.enhancedcore.common.world.plot.PlotLayout;
import com.gtl.enhancedcore.common.world.plot.PlotSettings;
import com.gtl.enhancedcore.common.world.plotinfo.PlotWorldInfoNetwork;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import com.gtl.enhancedcore.mixin.gtceu.BlockPatternAccessor;
import java.lang.reflect.Field;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.lwjgl.glfw.GLFW;

/** Opt-in production GUI and real chunk audit, strictly confined to one newly created world. */
@Mod("enhancedcore_audit")
public final class PlannedCampusClientChecks {
    private static final Path ROOT = Path.of("C:/IDEA/GTL-Enhancedcore/_audit/planned-campus-20261103").toAbsolutePath().normalize();
    private static final String WORLD = "planned-campus-fresh";
    private static final int[][] SIZES = {{960, 600, 2}, {1280, 720, 3}, {640, 480, 2}};
    private final List<String> checks = Collections.synchronizedList(new ArrayList<>());
    private final List<String> failures = Collections.synchronizedList(new ArrayList<>());
    private final List<Map<String, Object>> captures = new ArrayList<>();
    private final List<Map<String, Object>> samples = Collections.synchronizedList(new ArrayList<>());
    private Path directory;
    private CreateWorldPlotScreen screen;
    private CampusPlanScreen atlas;
    private Screen dashboard;
    private int atlasPhase;
    private int dashboardPhase, hoverX, hoverY, stableTicks, sceneTicks;
    private String lastChunkStatistics = "";
    private int stage, sizeIndex, frames, ticks, viewIndex;
    private volatile boolean sampled, moving, serverStopped;
    private boolean selected, done;
    private List<View> views;
    private final Map<String, int[]> landmarks = new LinkedHashMap<>();
    private List<MultiblockMachineDefinition> definitions;
    private final List<Map<String, Object>> inventory = new ArrayList<>();
    private Map<String, Object> labelMetrics = Map.of();
    private int inventoryIndex;
    private final java.util.Set<Block> machineBlocks = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private record View(String name, double x, double y, double z, float yaw, float pitch,
                        int distance, PlannedCampus.Bounds target) {}

    public PlannedCampusClientChecks() {
        MinecraftForge.EVENT_BUS.addListener(this::clientTick);
        MinecraftForge.EVENT_BUS.addListener(this::renderTick);
        MinecraftForge.EVENT_BUS.addListener(this::stopped);
    }

    private void stopped(ServerStoppedEvent event) { serverStopped = true; }
    private void renderTick(TickEvent.RenderTickEvent event) {
        if (!done && event.phase == TickEvent.Phase.END && directory != null
                && Minecraft.getInstance().getOverlay() == null) frames++;
    }
    private void clientTick(TickEvent.ClientTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END || !Boolean.getBoolean("gtl.enhancedcore.plannedCampusAudit")) return;
        var mc = Minecraft.getInstance();
        try {
            if (directory == null) {
                if (mc.getOverlay() != null || mc.screen == null) return;
                directory = mc.gameDirectory.toPath().toAbsolutePath().normalize();
                require(directory.startsWith(ROOT) && !directory.equals(ROOT)
                        && Files.isRegularFile(directory.resolve("planned-campus.marker")), "Explicit isolated campus fixture");
                require(mc.level == null && mc.getSingleplayerServer() == null, "No existing world is open");
                require(!Files.exists(directory.resolve("saves/" + WORLD)), "Fresh world path has never existed");
                Files.createDirectories(directory.resolve("screenshots"));
                mc.options.pauseOnLostFocus = false;
                if (Boolean.getBoolean("gtl.enhancedcore.plannedCampusInventoryOnly")) {
                    definitions = GTRegistries.MACHINES.values().stream().filter(MultiblockMachineDefinition.class::isInstance)
                            .map(MultiblockMachineDefinition.class::cast).sorted(Comparator.comparing(value -> value.getId().toString())).toList();
                    GTRegistries.MACHINES.values().forEach(definition -> machineBlocks.add(definition.getBlock()));
                    stage = -1; return;
                }
                verifySnapshotCodec(); verifySignLabels(mc);
                require(!PlotSettings.plannedCampus().showMachineFrames(), "Production campus defaults to no tall helper frames");
                if (Boolean.getBoolean("gtl.enhancedcore.plannedCampusWorldOnly")) advanceToWorld(mc);
                else openEditor(mc);
                return;
            }
            if (++ticks > 24000) throw new AssertionError("Campus audit exceeded 24000 client ticks at stage " + stage);
            if (mc.getOverlay() != null) return;
            if (stage == -1) {
                if (inventoryIndex < definitions.size()) {
                    var definition = definitions.get(inventoryIndex++);
                    try { inventory.add(inventory(definition)); }
                    catch (Throwable failure) { record("inventory " + definition.getId(), failure); }
                } else {
                    Files.writeString(directory.resolve("campus-machine-inventory.json"), new GsonBuilder().setPrettyPrinting()
                            .create().toJson(Map.of("registered", definitions.size(), "machines", inventory, "failures", failures)));
                    finish(mc);
                }
            } else if (stage == 0) {
                if (frames < 20) return;
                require(mc.screen == screen, "Production editor is active");
                inspectFixedWidgets();
                capture((selected ? "overview" : "entry") + "-" + SIZES[sizeIndex][0] + "x" + SIZES[sizeIndex][1]);
                if (!selected) {
                    AbstractWidget entrance = ((Screen) screen).children().stream().filter(AbstractWidget.class::isInstance)
                            .map(AbstractWidget.class::cast).filter(widget -> widget.getMessage().getString().equals(
                                    Component.translatable("gtl_enhancedcore.create_world_plot.campus_preset").getString()))
                            .findFirst().orElse(null);
                    if (entrance == null) entrance = ((Screen) screen).children().stream().filter(AbstractWidget.class::isInstance)
                            .map(AbstractWidget.class::cast).filter(widget -> widget.getMessage().getString().contains("巨构")
                                    || widget.getMessage().getString().toLowerCase().contains("campus")).findFirst().orElseThrow();
                    require(entrance.visible && entrance.active, "Campus entrance is visible and enabled");
                    require(((Screen) screen).mouseClicked(entrance.getX() + entrance.getWidth() / 2.0,
                            entrance.getY() + entrance.getHeight() / 2.0, 0), "Real campus button click is accepted");
                    require(((PlotSettings) field(CreateWorldPlotScreen.class, "draft").get(screen)).isCampus(), "Button selects campus settings");
                    selected = true; frames = 0;
                } else {
                    clickEditor("campus_frames_off");
                    require(((PlotSettings) field(CreateWorldPlotScreen.class, "draft").get(screen)).showMachineFrames(),
                            "Real frame button enables tall helper frames in production draft");
                    stage = 7; frames = 0;
                }
            } else if (stage == 7) {
                if (frames < 20) return;
                capture("frames-enabled-" + SIZES[sizeIndex][0] + "x" + SIZES[sizeIndex][1]);
                clickEditor("campus_frames_on");
                require(!((PlotSettings) field(CreateWorldPlotScreen.class, "draft").get(screen)).showMachineFrames(),
                        "Real frame button returns production draft to default disabled state");
                clickEditor("campus_open_plan");
                require(mc.screen instanceof CampusPlanScreen, "Real editor button opens production campus atlas");
                atlas = (CampusPlanScreen) mc.screen; atlasPhase = 0; stage = 5; frames = 0;
                GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), 0, 0);
            } else if (stage == 1) {
                if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
                stage = 2; mc.setScreen(null);
                resize(mc, 1280, 720, 2);
                mc.options.hideGui = true;
                mc.getSingleplayerServer().execute(() -> {
                    try { sampleWorld(mc.getSingleplayerServer().overworld()); }
                    catch (Throwable failure) { record("chunk generation", failure); }
                    finally { sampled = true; }
                });
            } else if (stage == 2) {
                if (!sampled) return;
                if (!failures.isEmpty()) { disconnect(mc); return; }
                views = views(); viewIndex = 0; move(mc); stage = 3;
            } else if (stage == 3) {
                if (moving || mc.screen != null || mc.player == null) return;
                var view = views.get(viewIndex);
                if (Math.abs(mc.player.getX() - view.x()) > 2 || Math.abs(mc.player.getZ() - view.z()) > 2) return;
                mc.player.setYRot(view.yaw()); mc.player.setXRot(view.pitch());
                mc.player.yRotO = view.yaw(); mc.player.xRotO = view.pitch();
                if (!sceneReady(mc, view)) return;
                require(mc.level.hasChunkAt(mc.player.blockPosition()),
                        "Scene camera is in a real generated chunk: " + view.name());
                capture("world-" + view.name());
                if (++viewIndex < views.size()) move(mc); else disconnect(mc);
            } else if (stage == 4) {
                if (mc.getSingleplayerServer() != null || mc.level != null || !serverStopped) return;
                require(serverStopped, "Integrated server fully stopped before final result"); finish(mc);
            } else if (stage == 5) {
                if (atlasPhase == 2) pointer(mc, hoverX, hoverY);
                if (frames < 20) return;
                require(mc.screen == atlas, "Production campus atlas remains active");
                String size = SIZES[sizeIndex][0] + "x" + SIZES[sizeIndex][1];
                if (atlasPhase == 0) {
                    capture("atlas-overview-" + size); click(atlas, "campus_ae_short");
                    require(field(CampusPlanScreen.class, "zoom").getDouble(atlas) > 1, "AE focus changes atlas scale");
                } else if (atlasPhase == 1) {
                    capture("atlas-ae-focus-" + size);
                    int mx = ((Screen) atlas).width / 2, my = (field(CampusPlanScreen.class, "mapTop").getInt(atlas)
                            + field(CampusPlanScreen.class, "mapBottom").getInt(atlas)) / 2;
                    double before = field(CampusPlanScreen.class, "zoom").getDouble(atlas);
                    require(((Screen) atlas).mouseScrolled(mx, my, -2), "Atlas map handles real zoom scroll");
                    require(field(CampusPlanScreen.class, "zoom").getDouble(atlas) < before, "Atlas scroll visibly changes scale");
                    require(!((Screen) atlas).mouseScrolled(0, 0, 2), "Atlas leaves outside viewport scroll unhandled");
                    ((Screen) atlas).mouseScrolled(mx, my, 40);
                    var target = PlannedCampus.plots().stream().filter(plot -> plot.stage().equals("early"))
                            .min(Comparator.comparingLong(plot -> (long)plot.width() * plot.depth())).orElseThrow();
                    double side = invokeDouble(atlas, "side");
                    double cx = field(CampusPlanScreen.class, "centerX").getDouble(atlas);
                    double cz = field(CampusPlanScreen.class, "centerZ").getDouble(atlas);
                    double targetX = target.x() + target.width() / 2.0, targetZ = target.z() + target.depth() / 2.0;
                    double dx = (cx - targetX) * side / PlannedCampus.bounds().width();
                    double dy = (cz - targetZ) * side / PlannedCampus.bounds().depth();
                    require(((Screen) atlas).mouseClicked(mx, my, 0), "Atlas drag starts with a real pointer press");
                    require(((Screen) atlas).mouseDragged(mx + dx, my + dy, 0, dx, dy), "Atlas handles real map drag");
                    require(((Screen) atlas).mouseReleased(mx + dx, my + dy, 0), "Atlas drag releases the pointer");
                    require(Math.abs(field(CampusPlanScreen.class, "centerX").getDouble(atlas) - targetX) < .01,
                            "Atlas drag moves map to exact machine position");
                    require(((Screen) atlas).mouseClicked(mx, my, 0), "Atlas map selection uses production click path");
                    require(((Screen) atlas).mouseReleased(mx, my, 0), "Atlas map selection commits on pointer release");
                    require(field(CampusPlanScreen.class, "selected").get(atlas) != null, "Dragged atlas can select a real machine");
                    double hoverSide = invokeDouble(atlas, "side");
                    double hoverTargetX = target.x() + target.width() / 2.0, hoverTargetZ = target.z() + target.depth() / 2.0;
                    double screenX = ((Screen) atlas).width - 40;
                    double mapTop = field(CampusPlanScreen.class, "mapTop").getInt(atlas);
                    double mapBottom = field(CampusPlanScreen.class, "mapBottom").getInt(atlas);
                    double screenY = (mapTop + mapBottom) / 2.0;
                    double centerX = hoverTargetX - (screenX - ((Screen) atlas).width / 2.0)
                            * PlannedCampus.bounds().width() / hoverSide;
                    double centerZ = hoverTargetZ - (screenY - (mapTop + mapBottom) / 2.0)
                            * PlannedCampus.bounds().depth() / hoverSide;
                    field(CampusPlanScreen.class, "centerX").setDouble(atlas, centerX);
                    field(CampusPlanScreen.class, "centerZ").setDouble(atlas, centerZ);
                    hoverX = (int)screenX; hoverY = (int)screenY; pointer(mc, hoverX, hoverY);
                    var hovered = CampusPlanScreen.class.getDeclaredMethod("hovered", double.class, double.class);
                    hovered.setAccessible(true);
                    var hoveredTarget = (com.gtl.enhancedcore.common.world.campus.CampusPlanAccess.Target)
                            hovered.invoke(atlas, screenX, screenY);
                    require(hoveredTarget != null && target.equals(hoveredTarget.plot()),
                            "Visible tooltip target stays inside hover viewport");
                } else {
                    capture("atlas-zoom-drag-hover-" + size); ((Screen) atlas).onClose();
                    require(mc.screen == screen, "Atlas Back returns to the same editor");
                    openDashboard(mc);
                }
                atlasPhase++; frames = 0;
            } else if (stage == 6) {
                if (frames < 20) return;
                String size = SIZES[sizeIndex][0] + "x" + SIZES[sizeIndex][1];
                if (dashboardPhase == 0) {
                    require(mc.screen == dashboard, "Production READY dashboard is active");
                    for (var child : dashboard.children()) if (child instanceof AbstractWidget widget && widget.visible)
                        require(widget.getX() >= 0 && widget.getY() >= 0 && widget.getX() + widget.getWidth() <= dashboard.width
                                && widget.getY() + widget.getHeight() <= dashboard.height, "Dashboard button stays inside logical viewport: " + widget.getMessage().getString());
                    capture("dashboard-ready-" + size); click(dashboard, "campus_map_button");
                    require(mc.screen instanceof CampusPlanScreen, "READY dashboard map button opens production atlas");
                } else {
                    capture("dashboard-map-" + size); mc.screen.onClose();
                    require(mc.screen == dashboard, "Dashboard map Back returns to same READY dashboard");
                    if (++sizeIndex < SIZES.length) { stage = 0; openEditor(mc); }
                    else advanceToWorld(mc);
                }
                dashboardPhase++; frames = 0;
            }
        } catch (Throwable failure) { record("client lifecycle", failure); finish(mc); }
    }
    private void openDashboard(Minecraft mc) throws Exception {
        var settings = PlotSettings.plannedCampus();
        var snapshot = new PersonalPlotNetwork.Snapshot(false, false, false, true, true, false,
                settings, settings, PersonalPlotWorlds.State.READY, settings, false, false, "");
        var constructor = PersonalPlotWorldScreen.class.getDeclaredConstructor(PersonalPlotNetwork.Snapshot.class);
        constructor.setAccessible(true); dashboard = constructor.newInstance(snapshot);
        mc.setScreen(dashboard); stage = 6; dashboardPhase = 0; frames = 0; pointer(mc, 0, 0);
        checks.add("READY dashboard uses a controlled production Snapshot; no create/travel packet is sent");
    }
    private void pointer(Minecraft mc, int x, int y) throws Exception {
        double px = (double)x * mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth();
        double py = (double)y * mc.getWindow().getScreenHeight() / mc.getWindow().getGuiScaledHeight();
        GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), px, py);
        ObfuscationReflectionHelper.findField(MouseHandler.class, "f_91507_").setDouble(mc.mouseHandler, px);
        ObfuscationReflectionHelper.findField(MouseHandler.class, "f_91508_").setDouble(mc.mouseHandler, py);
        if (mc.screen != null) mc.screen.mouseMoved(x, y);
    }

    private void advanceToWorld(Minecraft mc) {
        if (Boolean.getBoolean("gtl.enhancedcore.plannedCampusUiOnly")) { finish(mc); return; }
        mc.setScreen(new TitleScreen()); stage = 1; frames = 0;
        var settings = worldSettings(); var rules = new GameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        rules.getRule(GameRules.RULE_RANDOMTICKING).set(0, null);
        mc.createWorldOpenFlows().createFreshLevel(WORLD,
                new LevelSettings("Planned Campus Audit", GameType.CREATIVE, false, Difficulty.PEACEFUL,
                        true, rules, WorldDataConfiguration.DEFAULT), new WorldOptions(20261103L, false, false),
                registry -> registry.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                        .value().createWorldDimensions().replaceOverworldGenerator(registry,
                                new PlotChunkGenerator(registry.registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS), settings)));
    }
    private void click(Screen active, String key) {
        String text = Component.translatable("gtl_enhancedcore.create_world_plot." + key).getString();
        var button = active.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                .filter(widget -> widget.getMessage().getString().equals(text)).findFirst().orElseThrow();
        require(button.visible && button.active && active.mouseClicked(button.getX() + button.getWidth() / 2.0,
                button.getY() + button.getHeight() / 2.0, 0), "Real button click: " + key);
    }
    private void clickEditor(String key) throws Exception {
        String text = Component.translatable("gtl_enhancedcore.create_world_plot." + key).getString();
        var button = ((Screen)screen).children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                .filter(widget -> widget.getMessage().getString().equals(text)).findFirst().orElseThrow();
        @SuppressWarnings("unchecked") var contentY = (Map<AbstractWidget, Integer>)field(CreateWorldPlotScreen.class, "contentY").get(screen);
        var scroll = CreateWorldPlotScreen.class.getDeclaredMethod("updateScroll", int.class); scroll.setAccessible(true);
        scroll.invoke(screen, contentY.get(button));
        click(screen, key);
    }
    private static double invokeDouble(Object value, String methodName) throws Exception {
        var method = value.getClass().getDeclaredMethod(methodName); method.setAccessible(true); return ((Number)method.invoke(value)).doubleValue();
    }

    private void openEditor(Minecraft mc) {
        int[] size = SIZES[sizeIndex]; resize(mc, size[0], size[1], size[2]);
        screen = new CreateWorldPlotScreen(new TitleScreen(), PlotSettings.defaults(),
                Component.translatable("screen.gtl_enhancedcore.personal_plot.editor_title"),
                Component.translatable("gui.done"), ignored -> {});
        mc.setScreen(screen); selected = false; frames = 0;
        GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), 0, 0);
    }
    private void verifySnapshotCodec() throws Exception {
        Class<?> type = PlotWorldInfoNetwork.Snapshot.class;
        var encode = type.getDeclaredMethod("encode", type, FriendlyByteBuf.class); encode.setAccessible(true);
        var decode = type.getDeclaredMethod("decode", FriendlyByteBuf.class); decode.setAccessible(true);
        require("4".equals(field(PlotWorldInfoNetwork.class, "PROTOCOL").get(null)),
                "Read-only plot snapshot uses protocol 4 for the frame flag");
        for (var settings : new PlotSettings[]{null, PlotSettings.plannedCampus().withMachineFrames(false),
                PlotSettings.plannedCampus().withMachineFrames(true)}) {
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                encode.invoke(null, new PlotWorldInfoNetwork.Snapshot(settings), buffer);
                var decoded = (PlotWorldInfoNetwork.Snapshot) decode.invoke(null, buffer);
                require(java.util.Objects.equals(settings, decoded.settings()),
                        "Real snapshot codec preserves " + (settings == null ? "null settings" : "machineFrames=" + settings.showMachineFrames()));
                require(buffer.readableBytes() == 0, "Real snapshot decoder consumes every byte for "
                        + (settings == null ? "null settings" : "machineFrames=" + settings.showMachineFrames()));
            } finally { buffer.release(); }
        }
    }
    private void verifySignLabels(Minecraft mc) throws Exception {
        int lines = 0, widest = 0, machines = 0;
        String widestText = "";
        try (var reader = new InputStreamReader(PlannedCampus.class.getResourceAsStream(
                "/data/gtl_enhancedcore/worldgen/campus_sign_labels.json"), StandardCharsets.UTF_8)) {
            var labels = JsonParser.parseReader(reader).getAsJsonObject();
            for (var entry : labels.entrySet()) {
                machines++;
                var expected = entry.getValue().getAsJsonObject().getAsJsonArray(mc.options.languageCode);
                for (int line = 0; line < 4; line++) {
                    String key = "gtl_enhancedcore.campus.sign.machine." + entry.getKey().replace(':', '.') + "." + line;
                    String text = Component.translatable(key).getString();
                    if (!text.equals(expected.get(line).getAsString())) throw new AssertionError("Loaded sign translation mismatch: " + key);
                    int measured = mc.font.width(text);
                    if (measured > 90) throw new AssertionError("Sign line exceeds 90 actual font pixels: " + key + " width=" + measured + " " + text);
                    if (measured > widest) { widest = measured; widestText = text; }
                    lines++;
                }
            }
        }
        labelMetrics = Map.of("locale", mc.options.languageCode, "machines", machines, "lines", lines,
                "maxFontWidth", widest, "widestLine", widestText);
        checks.add("All " + lines + " sign lines match loaded " + mc.options.languageCode + " translations and actual Font.width <=90; max=" + widest);
    }
    private Map<String, Object> inventory(MultiblockMachineDefinition definition) {
        var pattern = definition.getPatternFactory().get();
        var access = (BlockPatternAccessor) pattern;
        var grid = access.gtlEnhancedcore$getBlockMatches();
        int depth = grid.length, height = depth == 0 ? 0 : grid[0].length;
        int width = height == 0 ? 0 : grid[0][0].length;
        int minimumDepth = 0, maximumDepth = 0;
        for (var repetition : pattern.aisleRepetitions) { minimumDepth += repetition[0]; maximumDepth += repetition[1]; }
        int[] center = access.enhanced$getCenterOffset();
        var origin = access.enhanced$chamberOffset(0, 0, 0, Direction.NORTH, Direction.UP, false);
        var maximum = access.enhanced$chamberOffset(Math.max(0, width - 1), Math.max(0, height - 1),
                Math.max(0, maximumDepth - 1), Direction.NORTH, Direction.UP, false);
        var minimum = new BlockPos(Math.min(origin.getX(), maximum.getX()), Math.min(origin.getY(), maximum.getY()),
                Math.min(origin.getZ(), maximum.getZ()));
        var controller = access.enhanced$chamberOffset(center[0], center[1], center[4], Direction.NORTH, Direction.UP, false).subtract(minimum);
        List<Integer> size = List.of(Math.abs(maximum.getX() - origin.getX()) + 1,
                Math.abs(maximum.getY() - origin.getY()) + 1, Math.abs(maximum.getZ() - origin.getZ()) + 1);
        var result = new LinkedHashMap<String, Object>();
        result.put("id", definition.getId().toString()); result.put("gridSize", List.of(width, height, depth));
        result.put("name", definition.getName()); result.put("translationKey", definition.getDescriptionId());
        result.put("tier", definition.getTier());
        result.put("recipeTypes", definition.getRecipeTypes() == null ? List.of()
                : java.util.Arrays.stream(definition.getRecipeTypes()).map(type -> type.registryName.toString()).toList());
        result.put("size", size); result.put("minimumAisleDepth", minimumDepth); result.put("maximumAisleDepth", maximumDepth);
        result.put("centerOffset", center); result.put("groundOffset", List.of(controller.getX(), controller.getY(), controller.getZ()));
        result.put("frontFacing", "north"); result.put("axes", java.util.Arrays.stream(pattern.structureDir).map(Enum::name).toList());
        result.put("repeats", pattern.aisleRepetitions);
        result.put("patternClass", pattern.getClass().getName());
        var predicates = Collections.newSetFromMap(new java.util.IdentityHashMap<TraceabilityPredicate, Boolean>());
        for (var plane : grid) for (var row : plane) Collections.addAll(predicates, row);
        var simplePredicates = Collections.newSetFromMap(new java.util.IdentityHashMap<SimplePredicate, Boolean>());
        for (var predicate : predicates) if (predicate != null) {
            simplePredicates.addAll(predicate.common); simplePredicates.addAll(predicate.limited);
        }
        var materials = new java.util.TreeSet<String>();
        var materialErrors = new ArrayList<String>();
        for (var simple : simplePredicates) {
            if (simple.candidates == null) continue;
            try {
                var candidates = simple.candidates.get();
                if (candidates == null) continue;
                for (var candidate : candidates) {
                    if (candidate == null || candidate.getBlockState().isAir()) continue;
                    var block = candidate.getBlockState().getBlock();
                    var id = BuiltInRegistries.BLOCK.getKey(block);
                    if (!machineBlocks.contains(block) && !java.util.Set.of("ae2", "extendedae", "extendedae_plus").contains(id.getNamespace()))
                        materials.add(id.toString());
                }
            } catch (Throwable failure) {
                String description = definition.getId() + " candidate " + simple.getClass().getName() + ": " + failure;
                materialErrors.add(description); record("inventory materials", failure);
            }
        }
        result.put("materials", materials); result.put("materialErrors", materialErrors);
        result.put("uniquePredicates", predicates.size()); result.put("uniqueSimplePredicates", simplePredicates.size());
        System.out.println("[CAMPUS_INVENTORY] " + definition.getId() + " " + size + " controller=" + controller);
        return result;
    }
    private void inspectFixedWidgets() throws Exception {
        @SuppressWarnings("unchecked") List<AbstractWidget> fixed = (List<AbstractWidget>) field(CreateWorldPlotScreen.class, "fixedWidgets").get(screen);
        for (var widget : fixed) if (widget.visible) require(widget.getX() >= 0 && widget.getY() >= 0
                && widget.getX() + widget.getWidth() <= ((Screen) screen).width && widget.getY() + widget.getHeight() <= ((Screen) screen).height,
                "Fixed control inside logical viewport: " + widget.getMessage().getString());
    }
    private static PlotSettings worldSettings() {
        return PlotSettings.plannedCampus().withMachineFrames(Boolean.getBoolean("gtl.enhancedcore.plannedCampusFrames"));
    }
    private void sampleWorld(ServerLevel level) throws Exception {
        PlotSettings settings = worldSettings();
        require(level.getChunkSource().getGenerator() instanceof PlotChunkGenerator plot && plot.settings().equals(settings),
                "Real world uses production campus chunk generator");
        require(PlannedCampus.plots().size() >= 30, "Real campus dedicated machine catalogue populated");
        long dedicatedIds = PlannedCampus.plots().stream().map(PlannedCampus.Plot::id).distinct().count();
        require(PlannedCampus.sharedMachineCount() > 0, "Compact machines have a shared workshop allocation");
        try (var reader = new InputStreamReader(PlannedCampus.class.getResourceAsStream(PlannedCampus.CATALOG), StandardCharsets.UTF_8)) {
            var catalog = JsonParser.parseReader(reader).getAsJsonObject();
            require(dedicatedIds == catalog.getAsJsonArray("reservations").size()
                            && PlannedCampus.sharedMachineCount() == catalog.getAsJsonArray("effectiveSharedIds").size(),
                    "Real generated reservations use the selected capacities and omit module/upgrade duplicates");
        }
        checks.add("Loaded production campus plot count=" + PlannedCampus.plots().size());
        List<PlannedCampus.Plot> selectedPlots = new ArrayList<>();
        PlannedCampus.plots().stream().collect(java.util.stream.Collectors.groupingBy(plot -> String.valueOf(plot.stage()),
                        java.util.TreeMap::new, java.util.stream.Collectors.toList()))
                .values().forEach(group -> selectedPlots.add(group.getFirst()));
        selectedPlots.add(PlannedCampus.plots().stream().max(Comparator.comparingLong(plot -> (long)plot.width() * plot.depth())).orElseThrow());
        selectedPlots.add(PlannedCampus.plots().stream().max(Comparator.comparingInt(PlannedCampus.Plot::controllerY)).orElseThrow());
        List<int[]> points = new ArrayList<>();
        points.add(new int[]{0, 0}); points.add(new int[]{-1, -1}); points.add(new int[]{-16, 16}); points.add(new int[]{16, -16});
        for (var plot : selectedPlots.stream().distinct().toList()) points.add(new int[]{plot.controllerX(), plot.controllerZ()});
        var largest = selectedPlots.get(selectedPlots.size() - 2);
        points.add(new int[]{largest.x(), largest.z()});
        discoverLandmarks(settings);
        points.addAll(landmarks.values());
        int[] crossing = landmarks.get("intersection");
        for (int offset : new int[]{-24, -8, 0, 1, 8, 24}) {
            points.add(new int[]{crossing[0] + offset, crossing[1]});
            points.add(new int[]{crossing[0], crossing[1] + offset});
        }
        for (var workshop : PlannedCampus.workshops()) {
            int x = workshop.x() + workshop.width() / 2, z = workshop.z() + workshop.depth() / 2;
            points.add(new int[]{x, z});
            require(PlannedCampus.plotAt(x, z) == null && PlannedCampus.blockAboveAt(settings, x, 1, z) == null,
                    "Shared courtyard remains open for free placement: " + workshop.stage() + " at " + x + "," + z);
        }
        for (var plot : selectedPlots.stream().distinct().toList()) {
            points.add(new int[]{plot.x() - 1, plot.z() - 1});
            points.add(new int[]{plot.maxX() + 1, plot.maxZ() + 1});
            int frameY = Math.max(2, plot.height() / 2);
            String expectedFrame = PlannedCampus.blockAboveAt(settings, plot.x() - 1, frameY, plot.z() - 1);
            require((expectedFrame != null) == settings.showMachineFrames(),
                    "Tall helper corner follows persisted setting=" + settings.showMachineFrames() + " for " + plot.id());
            require("minecraft:smooth_quartz".equals(PlotLayout.surfaceBlock(settings, plot.x() - 1, plot.z() - 1)),
                    "Permanent ground frame is one block outside machine bounds for " + plot.id());
        }
        for (int[] point : points) {
            int x = point[0], z = point[1];
            var controllerPlot = PlannedCampus.plotAt(x, z);
            boolean controller = controllerPlot != null && x == controllerPlot.controllerX() && z == controllerPlot.controllerZ();
            int top = Math.max(PlannedCampus.maxDecorationYAt(settings, x, z), controller ? controllerPlot.controllerY() + 1 : 0);
            require(level.getBlockState(new BlockPos(x, settings.bedrockY(), z)).is(Blocks.BEDROCK), "Bedrock generated at " + x + "," + z);
            for (int dy = 0; dy <= Math.max(2, top + 1); dy++) {
                String expected = dy == 0 ? PlotLayout.surfaceBlock(settings, x, z) : PlannedCampus.blockAboveAt(settings, x, dy, z);
                if (controller && dy == controllerPlot.controllerY() + 1) expected = "minecraft:oak_sign";
                if (expected == null) expected = "minecraft:air";
                var position = new BlockPos(x, settings.surfaceY() + dy, z);
                var state = level.getBlockState(position);
                String actual = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                int properties = expected.indexOf('[');
                String expectedId = properties < 0 ? expected : expected.substring(0, properties);
                require(actual.equals(expectedId), "Generated block " + position + " = " + expected + " actual=" + actual);
                if (properties >= 0) for (String entry : expected.substring(properties + 1, expected.length() - 1).split(",")) {
                    String[] pair = entry.split("=");
                    var property = state.getBlock().getStateDefinition().getProperty(pair[0]);
                    require(property != null && state.getValue(property).toString().equals(pair[1]),
                            "Generated block preserves authored property " + entry + " at " + position);
                }
            }
            samples.add(Map.of("x", x, "z", z, "surface", PlotLayout.surfaceBlock(settings, x, z),
                    "decorationHeight", PlannedCampus.maxDecorationYAt(settings, x, z)));
        }
        for (var plot : selectedPlots.stream().distinct().toList()) {
            var expected = new ArrayList<String>();
            for (int line = 0; line < 4; line++) expected.add(Component.translatable(
                    "gtl_enhancedcore.campus.sign.machine." + plot.id().replace(':', '.') + "." + line).getString());
            checkSign(level, new BlockPos(plot.signX(), settings.surfaceY() + 1, plot.signZ()), expected, plot);
            checkSign(level, new BlockPos(plot.controllerX(), settings.surfaceY() + plot.controllerY() + 1, plot.controllerZ()), expected, plot);
            checkSign(level, new BlockPos(plot.signX() + 2, settings.surfaceY() + 1, plot.signZ()), List.of(
                    "#" + (plot.slotIndex() + 1), plot.width() + " × " + plot.height() + " × " + plot.depth(),
                    plot.controllerX() + " " + (settings.surfaceY() + plot.controllerY()) + " " + plot.controllerZ(),
                    Component.translatable("gtl_enhancedcore.campus.sign.controller").getString()), plot);
        }
        var water = landmarks.get("water");
        var waterPos = new BlockPos(water[0], settings.surfaceY(), water[1]);
        require(level.getBlockState(waterPos).is(Blocks.WATER) && level.getFluidState(waterPos).isSource(),
                "Reflecting garden is a real source water block in a generated chunk");
        require(!level.getBlockState(waterPos.below()).isAir(), "Reflecting pool has a sealed solid floor");
        for (var side : Direction.Plane.HORIZONTAL) {
            var neighbor = level.getBlockState(waterPos.relative(side));
            require(neighbor.is(Blocks.WATER) || neighbor.isSolid(), "Water bank is sealed toward " + side);
        }
        var tree = landmarks.get("tree");
        require(tree[2] >= 12, "Authored tree reaches at least twelve blocks above ground");
        require(level.getBlockState(new BlockPos(tree[0], settings.surfaceY() + 1, tree[1])).is(Blocks.OAK_LOG),
                "Tall tree has a real rooted trunk");
        var lamp = landmarks.get("lamp");
        boolean lampLight = false, lampShape = false;
        for (int x = lamp[0] - 3; x <= lamp[0] + 3; x++) for (int z = lamp[1] - 3; z <= lamp[1] + 3; z++)
            for (int dy = 1; dy <= lamp[2] + 1; dy++) {
                String id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(new BlockPos(x, settings.surfaceY() + dy, z)).getBlock()).toString();
                lampLight |= id.equals("minecraft:sea_lantern");
                lampShape |= id.endsWith("_stairs") || id.endsWith("_slab");
            }
        require(lampLight && lampShape, "Road lamp has real lighting and shaped stair/slab detail");
        level.setDayTime(6000); level.setWeatherParameters(0, 6000, false, false);
        checks.add("Chunk samples cover stage representatives, maximum footprint, highest controller and negative chunk seams; all plots geometry covered separately by pure Java regression");
    }
    private void checkSign(ServerLevel level, BlockPos position, List<String> expected, PlannedCampus.Plot plot) {
        require(level.getBlockState(position).is(Blocks.OAK_SIGN), "Generated machine sign exists at " + position);
        require(level.getBlockEntity(position) instanceof SignBlockEntity, "Generated sign block entity loads at " + position);
        var sign = (SignBlockEntity) level.getBlockEntity(position);
        var messages = sign.getFrontText().getMessages(false);
        require(messages.length == 4, "Generated sign has exactly four rows: " + plot.id());
        for (int line = 0; line < 4; line++) require(messages[line].getString().equals(expected.get(line)),
                "Generated sign preserves exact row " + line + " at " + position + " for " + plot.id());
    }
    @SuppressWarnings("unchecked")
    private void discoverLandmarks(PlotSettings settings) throws Exception {
        Class<?> holder = Class.forName(PlannedCampus.class.getName() + "$Holder");
        Object plan = field(holder, "PLAN").get(null);
        var index = (Map<?, ?>) field(plan.getClass(), "index").get(plan);
        for (String kind : List.of("trees", "lamps")) {
            Object nearest = null; long nearestDistance = Long.MAX_VALUE;
            for (Object local : index.values()) for (Object item : (List<?>) field(local.getClass(), kind).get(local)) {
                int x = field(item.getClass(), "x").getInt(item), z = field(item.getClass(), "z").getInt(item);
                String base = PlannedCampus.blockAboveAt(settings, x, 1, z);
                if (kind.equals("trees") && !"minecraft:oak_log".equals(base)) continue;
                long distance = (long)x * x + (long)z * z;
                if (distance < nearestDistance) { nearest = item; nearestDistance = distance; }
            }
            require(nearest != null, "Production plan contains a real " + kind + " landmark");
            int x = field(nearest.getClass(), "x").getInt(nearest), z = field(nearest.getClass(), "z").getInt(nearest);
            landmarks.put(kind.equals("trees") ? "tree" : "lamp", new int[]{x, z, PlannedCampus.maxDecorationYAt(settings, x, z)});
            if (kind.equals("lamps")) {
                boolean vertical = field(nearest.getClass(), "vertical").getBoolean(nearest);
                int inward = field(nearest.getClass(), "inward").getInt(nearest);
                landmarks.put("lamp-stair", new int[]{x + (vertical ? inward : 0), z + (vertical ? 0 : inward), 8});
                landmarks.put("lamp-light", new int[]{x + (vertical ? 3 * inward : 0), z + (vertical ? 0 : 3 * inward), 8});
            }
        }
        for (var garden : PlannedCampus.aeGardenBounds()) {
            for (int z = garden.minZ(); z <= garden.maxZ() && !landmarks.containsKey("water"); z++)
                for (int x = garden.minX(); x <= garden.maxX(); x++) if ("minecraft:water".equals(PlotLayout.surfaceBlock(settings, x, z))
                        && "minecraft:water".equals(PlotLayout.surfaceBlock(settings, x + 1, z))
                        && "minecraft:water".equals(PlotLayout.surfaceBlock(settings, x - 1, z))
                        && "minecraft:water".equals(PlotLayout.surfaceBlock(settings, x, z + 1))
                        && "minecraft:water".equals(PlotLayout.surfaceBlock(settings, x, z - 1))) {
                    landmarks.put("water", new int[]{x, z, 0}); break;
                }
            if (landmarks.containsKey("water")) break;
        }
        require(landmarks.containsKey("water"), "Production garden exposes real water instead of a glass substitute");
        var streets = (List<?>) field(plan.getClass(), "streets").get(plan);
        int[] crossing = null; long nearestDistance = Long.MAX_VALUE;
        for (Object vertical : streets) if (field(vertical.getClass(), "vertical").getBoolean(vertical))
            for (Object horizontal : streets) if (!field(horizontal.getClass(), "vertical").getBoolean(horizontal)) {
                int x = field(vertical.getClass(), "axis").getInt(vertical) - 1;
                int z = field(horizontal.getClass(), "axis").getInt(horizontal) - 1;
                if (x < field(horizontal.getClass(), "start").getInt(horizontal) || x > field(horizontal.getClass(), "end").getInt(horizontal)
                        || z < field(vertical.getClass(), "start").getInt(vertical) || z > field(vertical.getClass(), "end").getInt(vertical)
                        || PlannedCampus.aeCoreBounds().contains(x, z) || PlannedCampus.plotAt(x, z) != null) continue;
                long distance = (long)x * x + (long)z * z;
                if (distance < nearestDistance) { nearestDistance = distance; crossing = new int[]{x, z, 0}; }
            }
        require(crossing != null, "Production road graph exposes an unobstructed intersection outside the AE reserve");
        landmarks.put("intersection", crossing);
        for (var landmark : landmarks.entrySet()) checks.add("Production landmark " + landmark.getKey() + "=" + java.util.Arrays.toString(landmark.getValue()));
    }
    private List<View> views() {
        double y = worldSettings().surfaceY();
        var maximum = PlannedCampus.plots().stream().max(Comparator.comparingLong(plot -> (long)plot.width() * plot.depth())).orElseThrow();
        var highest = PlannedCampus.plots().stream().max(Comparator.comparingInt(PlannedCampus.Plot::controllerY)).orElseThrow();
        var middle = PlannedCampus.plots().get(PlannedCampus.plots().size() / 2);
        var workshop = PlannedCampus.workshops().stream().min(Comparator.comparingLong(pad -> (long)pad.x() * pad.x() + (long)pad.z() * pad.z())).orElseThrow();
        var result = new ArrayList<View>();
        var ae = PlannedCampus.aeCoreBounds();
        result.add(lookAt("ae-plaza", ae.maxX() + 80, y + 110, ae.maxZ() + 100,
                (ae.minX() + ae.maxX()) / 2.0, y, (ae.minZ() + ae.maxZ()) / 2.0, 20, ae));
        result.add(lookAt("shared-courtyard", workshop.x() + workshop.width() / 2.0 + 85, y + 100,
                workshop.z() + workshop.depth() / 2.0 + 100, workshop.x() + workshop.width() / 2.0,
                y, workshop.z() + workshop.depth() / 2.0, 14, new PlannedCampus.Bounds(workshop.x(), workshop.z(), workshop.x() + workshop.width() - 1, workshop.z() + workshop.depth() - 1)));
        result.add(lookAt("mid-late", middle.x() + middle.width() / 2.0 + 70, y + 105,
                middle.z() + middle.depth() / 2.0 + 70, middle.x() + middle.width() / 2.0, y,
                middle.z() + middle.depth() / 2.0, 14, middle.bounds()));
        result.add(lookAt("largest-megastructure", maximum.x() + maximum.width() / 2.0,
                y + maximum.height() + 110, maximum.maxZ() + 90, maximum.x() + maximum.width() / 2.0,
                y + maximum.height() / 2.0, maximum.z() + maximum.depth() / 2.0, 24, maximum.bounds()));
        result.add(lookAt("highest-controller", highest.controllerX() + 25, y + highest.controllerY() + 24,
                highest.controllerZ() - 34, highest.controllerX() + .5, y + highest.controllerY(), highest.controllerZ() + .5,
                16, around(highest.controllerX(), highest.controllerZ(), 80)));
        result.add(new View("largest-entrance-sign", maximum.signX() + .5, y + 1.4, maximum.signZ() - 2.3, 0, 25, 12,
                around(maximum.signX(), maximum.signZ(), 16)));
        var crossing = landmarks.get("intersection");
        result.add(lookAt("road-intersection", crossing[0] + 34, y + 35, crossing[1] + 38,
                crossing[0] + .5, y, crossing[1] + .5, 12, around(crossing[0], crossing[1], 65)));
        result.add(lookAt("road-eye-level", crossing[0] + .5, y + 1.6, crossing[1] + 28,
                crossing[0] + .5, y + 1.6, crossing[1] - 25, 12, around(crossing[0], crossing[1], 65)));
        var lamp = landmarks.get("lamp");
        result.add(lookAt("lamp-detail", lamp[0] + 8, y + 3.5, lamp[1] + 11,
                lamp[0] + .5, y + lamp[2] / 2.0, lamp[1] + .5, 12, around(lamp[0], lamp[1], 30)));
        var tree = landmarks.get("tree");
        result.add(lookAt("tree-detail", tree[0] + 14, y + 5, tree[1] + 18,
                tree[0] + .5, y + 6, tree[1] + .5, 12, around(tree[0], tree[1], 35)));
        var water = landmarks.get("water");
        result.add(lookAt("water-garden", water[0] + 22, y + 15, water[1] + 25,
                water[0] + .5, y, water[1] + .5, 12, around(water[0], water[1], 64)));
        return List.copyOf(result);
    }
    private static PlannedCampus.Bounds around(int x, int z, int radius) {
        return new PlannedCampus.Bounds(x - radius, z - radius, x + radius, z + radius);
    }
    private static View lookAt(String name, double x, double y, double z, double tx, double ty, double tz,
                               int distance, PlannedCampus.Bounds target) {
        double dx = tx - x, dy = ty - y, dz = tz - z;
        return new View(name, x, y, z, (float)Math.toDegrees(Math.atan2(-dx, dz)),
                (float)-Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))), distance, target);
    }
    private boolean sceneReady(Minecraft mc, View view) {
        if (++sceneTicks > 2400) throw new AssertionError("Scene did not reach loaded, rendered stability: " + view.name()
                + " camera=" + mc.player.position() + " target=" + view.target() + " " + mc.levelRenderer.getChunkStatistics());
        if (sceneTicks < 120) return false;
        for (int x = view.target().minX() >> 4; x <= view.target().maxX() >> 4; x++)
            for (int z = view.target().minZ() >> 4; z <= view.target().maxZ() >> 4; z++)
                if (!mc.level.hasChunk(x, z)) return false;
        int rendered = mc.levelRenderer.countRenderedChunks();
        String stats = mc.levelRenderer.getChunkStatistics();
        if (rendered < 40 || !mc.levelRenderer.hasRenderedAllChunks() || !stats.equals(lastChunkStatistics)) {
            stableTicks = 0; lastChunkStatistics = stats; return false;
        }
        if (++stableTicks < 30) return false;
        return true;
    }
    private void move(Minecraft mc) {
        moving = true; frames = 0; sceneTicks = stableTicks = 0; lastChunkStatistics = ""; View view = views.get(viewIndex);
        mc.options.renderDistance().set(view.distance());
        mc.options.broadcastOptions();
        mc.levelRenderer.allChanged();
        mc.getSingleplayerServer().execute(() -> {
            try {
                var player = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                mc.getSingleplayerServer().getPlayerList().setViewDistance(view.distance());
                mc.getSingleplayerServer().getPlayerList().setSimulationDistance(5);
                player.setGameMode(GameType.SPECTATOR);
                player.teleportTo(player.serverLevel(), view.x(), view.y(), view.z(), view.yaw(), view.pitch());
            } catch (Throwable failure) { record("camera", failure); }
            finally { moving = false; }
        });
    }
    private void disconnect(Minecraft mc) {
        stage = 4; mc.options.hideGui = false; mc.level.disconnect(); mc.clearLevel(); mc.setScreen(new TitleScreen());
    }
    private static void resize(Minecraft mc, int width, int height, int scale) {
        mc.options.guiScale().set(scale); GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), width, height); mc.resizeDisplay();
    }
    private void capture(String name) throws Exception {
        var mc = Minecraft.getInstance(); String file = "screenshots/" + name + ".png";
        if (stage == 0 || stage == 5 || stage == 7) {
            int[] expected = SIZES[sizeIndex];
            require(mc.getWindow().getWidth() == expected[0] && mc.getWindow().getHeight() == expected[1], "Actual framebuffer size matches " + name);
            if (sizeIndex == 2) require(mc.getWindow().getGuiScaledWidth() == 320 && mc.getWindow().getGuiScaledHeight() == 240,
                    "Actual editor logical minimum is 320x240");
        }
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(directory.resolve(file)); }
        captures.add(Map.of("name", name, "file", file, "width", mc.getWindow().getWidth(), "height", mc.getWindow().getHeight(),
                "logicalWidth", mc.getWindow().getGuiScaledWidth(), "logicalHeight", mc.getWindow().getGuiScaledHeight(),
                "scale", mc.options.guiScale().get(), "renderedFrames", frames));
        if (stage == 3) checks.add("Screenshot " + name + " actual camera=" + mc.player.position() + " yaw=" + mc.player.getYRot()
                + " pitch=" + mc.player.getXRot() + " view=" + mc.options.renderDistance().get() + " rendered="
                + mc.levelRenderer.countRenderedChunks() + " " + mc.levelRenderer.getChunkStatistics());
    }
    private static Field field(Class<?> type, String name) throws Exception { var field = type.getDeclaredField(name); field.setAccessible(true); return field; }
    private void require(boolean value, String description) { if (!value) throw new AssertionError(description); checks.add(description); }
    private void record(String group, Throwable failure) { failures.add(group + ": " + failure); failure.printStackTrace(); }
    private void finish(Minecraft mc) {
        done = true; var report = new LinkedHashMap<String, Object>();
        report.put("status", failures.isEmpty() ? "PASS" : "FAIL"); report.put("checks", checks); report.put("failures", failures);
        report.put("screenshots", captures); report.put("samples", samples); report.put("serverStopped", serverStopped);
        report.put("inventoryCount", inventory.size());
        report.put("signLabelMetrics", labelMetrics);
        report.put("machineFrames", worldSettings().showMachineFrames());
        report.put("landmarks", landmarks);
        report.put("language", mc.options.languageCode);
        report.put("scope", Boolean.getBoolean("gtl.enhancedcore.plannedCampusInventoryOnly")
                ? "All live registered multiblock factories, axes, repetition ranges and north-facing maximum dimensions/anchor. No preview shapes or worlds opened."
                : Boolean.getBoolean("gtl.enhancedcore.plannedCampusUiOnly")
                ? "Production editor at three actual window/GUI sizes; no worlds opened."
                : "Production editor and campus sampler; actual water, shaped lamps, tall trees, road intersection, outer ground frames and configured tall frames in fresh isolated overworld chunks using PlotChunkGenerator. No player saves or private dimension creation/restart were tested.");
        try { Files.writeString(directory.resolve("planned-campus-checks.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
        catch (Exception failure) { failure.printStackTrace(); }
        mc.stop();
    }
}
