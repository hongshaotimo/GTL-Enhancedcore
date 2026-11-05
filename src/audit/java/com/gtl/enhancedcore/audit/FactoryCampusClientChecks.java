package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import com.gtl.enhancedcore.client.gui.ChangelogScreen;
import com.gtl.enhancedcore.client.plot.CampusPlanScreen;
import com.gtl.enhancedcore.client.plot.CreateWorldPlotScreen;
import com.gtl.enhancedcore.client.plot.PlotPreviewRenderer;
import com.gtl.enhancedcore.client.plotinfo.PersonalPlotWorldScreen;
import com.gtl.enhancedcore.common.world.campus.CampusDeviceBlocks;
import com.gtl.enhancedcore.common.world.campus.CampusTeleportNetwork;
import com.gtl.enhancedcore.common.world.campus.CentralTeleporterBlockEntity;
import com.gtl.enhancedcore.common.world.campus.VirtualMarkerBlockEntity;
import com.gtl.enhancedcore.common.world.personalplot.PersonalPlotNetwork;
import com.gtl.enhancedcore.common.world.personalplot.PersonalPlotWorlds;
import com.gtl.enhancedcore.common.world.plot.FactoryArchitecture;
import com.gtl.enhancedcore.common.world.plot.FactoryCampus;
import com.gtl.enhancedcore.common.world.plot.CampusPresetRegistry;
import com.gtl.enhancedcore.common.world.plot.PlannedCampus;
import com.gtl.enhancedcore.common.world.plot.PlotChunkGenerator;
import com.gtl.enhancedcore.common.world.plot.PlotLayout;
import com.gtl.enhancedcore.common.world.plot.PlotSettings;
import com.gtl.enhancedcore.common.world.plot.PlotPresetShare;
import com.gtl.enhancedcore.common.world.plotinfo.PlotWorldInfoNetwork;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Production entry clicks and a few real generated sites, confined to a fresh opt-in fixture. */
@Mod("enhancedcore_audit")
public final class FactoryCampusClientChecks {
    private static final Path ROOT = Path.of("C:/IDEA/GTL-Enhancedcore/_audit/factory-campus-20261104").toAbsolutePath().normalize();
    private static final String WORLD = "factory-campus-fresh";
    private static final String KEY = "gtl_enhancedcore.create_world_plot.";
    private static final int[][] SIZES = {{960, 600, 2}, {1280, 720, 3}, {640, 480, 2}};
    private static final long ATLAS_WAIT_NANOS = 90_000_000_000L;
    private static final long TELEPORTER_WAIT_NANOS = 30_000_000_000L;
    private final List<String> checks = Collections.synchronizedList(new ArrayList<>());
    private final List<String> failures = Collections.synchronizedList(new ArrayList<>());
    private final List<Map<String, Object>> captures = new ArrayList<>();
    private final List<Map<String, Object>> samples = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, BlockState> palette = new LinkedHashMap<>();
    private Path directory;
    private Screen dashboard;
    private CreateWorldPlotScreen editor;
    private int stage, sizeIndex, ticks, startupTicks, frames, viewIndex, sceneTicks, stableTicks;
    private int atlasReadyFrame = -1;
    private long atlasWaitStartedAt;
    private long teleporterWaitStartedAt;
    private UUID teleporterSessionId;
    private CampusPlanScreen teleporterScreen;
    private String lastStatistics = "";
    private boolean initialized, done, worldOpened;
    private boolean teleporterRightClicked, teleporterOpened, teleporterClosed;
    private volatile boolean sampled, moving, serverStopped;
    private volatile boolean teleporterPrepared, teleporterSessionVerified, teleporterSessionClosed, teleporterCheckPending;
    private List<FactoryArchitecture.Hall> sites;
    private List<View> views;
    private record View(String name, double x, double y, double z, float yaw, float pitch,
                        int distance, PlannedCampus.Bounds target) {}

    public FactoryCampusClientChecks() {
        MinecraftForge.EVENT_BUS.addListener(this::tick);
        MinecraftForge.EVENT_BUS.addListener(this::render);
        MinecraftForge.EVENT_BUS.addListener(this::stopped);
    }
    private void stopped(ServerStoppedEvent event) { serverStopped = true; }
    private void render(TickEvent.RenderTickEvent event) {
        if (!done && initialized && event.phase == TickEvent.Phase.END && Minecraft.getInstance().getOverlay() == null) frames++;
    }
    private void tick(TickEvent.ClientTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END || !Boolean.getBoolean("gtl.enhancedcore.factoryCampusAudit")) return;
        var mc = Minecraft.getInstance();
        try {
            if (!initialized) {
                Path proposed = mc.gameDirectory.toPath().toAbsolutePath().normalize();
                if (!proposed.startsWith(ROOT) || proposed.equals(ROOT)
                        || !Files.isRegularFile(proposed.resolve("factory-campus.marker")))
                    throw new IllegalStateException("Factory audit requires its isolated fixture marker");
                directory = proposed;
                if (mc.getOverlay() != null) return;
                if (mc.screen instanceof ChangelogScreen) { mc.screen.onClose(); return; }
                if (!(mc.screen instanceof TitleScreen)) {
                    if (++startupTicks > 1200) throw new AssertionError("Unexpected startup screen: " + mc.screen);
                    return;
                }
                require(mc.level == null && mc.getSingleplayerServer() == null && mc.getConnection() == null,
                        "Audit starts at the title screen without a player world or connection");
                require(!Files.exists(directory.resolve("saves/" + WORLD)), "The isolated world path has never existed");
                Files.createDirectories(directory.resolve("screenshots"));
                mc.options.pauseOnLostFocus = false;
                verifyTransport(); verifyPaletteAndLabels(mc);
                sites = selectedSites(); initialized = true;
                if (Boolean.getBoolean("gtl.enhancedcore.factoryCampusWorldOnly")) createWorld(mc);
                else openDashboard(mc);
                return;
            }
            if (++ticks > 24000) throw new AssertionError("Factory audit exceeded 24000 client ticks at stage " + stage);
            if (mc.getOverlay() != null) return;
            if (stage == 9) {
                if (mc.level == null && mc.getSingleplayerServer() == null && serverStopped) finish(mc);
                return;
            }
            if (stage < 6 && frames < 18) return;
            switch (stage) {
                case 0 -> {
                    bounds(dashboard); capture(mc, "op-dashboard-" + sizeName());
                    click(dashboard, "screen.gtl_enhancedcore.personal_plot.edit_template");
                    require(mc.screen instanceof CreateWorldPlotScreen, "Real OP dashboard opens the production preset editor");
                    editor = (CreateWorldPlotScreen) mc.screen;
                    require(field(CreateWorldPlotScreen.class, "page").getInt(editor) == 0, "Production editor starts on the presets page");
                    stage = 1;
                }
                case 1 -> {
                    bounds(editor); scrollTo("factory_preset"); capture(mc, "factory-entry-" + sizeName());
                    var entrance = button(editor, KEY + "factory_preset");
                    require(mc.font.width(entrance.getMessage()) <= entrance.getWidth() - 10,
                            "Full factory preset name fits the actual translated button width");
                    click(editor, KEY + "factory_preset");
                    require(draft().equals(PlotSettings.fileCampus("quartz_works")), "Real registry factory button selects its explicit file-backed recipe");
                    stage = 2;
                }
                case 2 -> {
                    bounds(editor); capture(mc, "factory-layout-" + sizeName());
                    click(editor, KEY + "page_materials"); stage = 3;
                }
                case 3 -> {
                    capture(mc, "factory-materials-" + sizeName());
                    click(editor, KEY + "page_layout"); scrollTo("campus_open_plan");
                    click(editor, KEY + "campus_open_plan");
                    require(mc.screen instanceof CampusPlanScreen, "Real factory layout opens the production atlas");
                    require(field(CampusPlanScreen.class, "searchIndex").getInt(mc.screen) == -1
                                    && ((List<?>) field(CampusPlanScreen.class, "searchMatches").get(mc.screen)).isEmpty(),
                            "Initial empty atlas search keeps no selection without probing an immutable list with null");
                    atlasWaitStartedAt = System.nanoTime(); atlasReadyFrame = -1;
                    stage = 4;
                }
                case 4 -> {
                    if (!atlasReady(mc)) return;
                    capture(mc, "factory-atlas-" + sizeName()); mc.screen.onClose();
                    require(mc.screen == editor && draft().roadStyle() == PlotSettings.RoadStyle.CAMPUS_FILE
                                    && draft().campusPresetId().equals("quartz_works"),
                            "Atlas Back preserves the selected factory preset");
                    click(editor, "gui.cancel"); require(mc.screen == dashboard, "Cancel returns through the real dashboard flow");
                    if (++sizeIndex < SIZES.length) openDashboard(mc);
                    else if (Boolean.getBoolean("gtl.enhancedcore.factoryCampusUiOnly")) { finish(mc); return; }
                    else createWorld(mc);
                }
                case 6 -> {
                    if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
                    mc.setScreen(null); resize(mc, 1280, 720, 2); mc.options.hideGui = false;
                    prepareTeleporter(mc); stage = 10;
                }
                case 7 -> {
                    if (!sampled) return;
                    if (!failures.isEmpty()) { disconnect(mc); return; }
                    views = makeViews(); viewIndex = 0; move(mc); stage = 8;
                }
                case 8 -> {
                    if (moving || mc.screen != null || mc.player == null) return;
                    var view = views.get(viewIndex);
                    if (Math.abs(mc.player.getX() - view.x()) > 2 || Math.abs(mc.player.getZ() - view.z()) > 2) return;
                    mc.player.setYRot(view.yaw()); mc.player.setXRot(view.pitch());
                    mc.player.yRotO = view.yaw(); mc.player.xRotO = view.pitch();
                    if (!sceneReady(mc, view)) return;
                    capture(mc, "world-" + view.name());
                    if (++viewIndex < views.size()) move(mc); else disconnect(mc);
                }
                case 10 -> {
                    requireTeleporterWait("The generated central teleporter and audit player did not become ready for a real right click");
                    if (!teleporterPrepared) return;
                    if (!failures.isEmpty()) { disconnect(mc); return; }
                    var origin = teleporterOrigin();
                    if (mc.player == null || mc.level == null || mc.gameMode == null
                            || mc.player.distanceToSqr(.5, origin.getY(), 3.5) > 1
                            || !mc.level.hasChunkAt(origin)
                            || !mc.level.getBlockState(origin).is(CampusDeviceBlocks.CENTRAL_TELEPORTER.get())
                            || !(mc.level.getBlockEntity(origin) instanceof CentralTeleporterBlockEntity device)
                            || !mc.level.dimension().location().equals(device.boundDimension())) return;
                    require(!mc.player.isSpectator() && mc.screen == null,
                            "Fresh-world player is in normal creative play beside the generated central teleporter");
                    var result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(origin), Direction.SOUTH, origin, false));
                    require(result.consumesAction(), "Real local-player right click consumes the generated central teleporter block use");
                    teleporterRightClicked = true; teleporterWaitStartedAt = System.nanoTime(); stage = 11;
                }
                case 11 -> {
                    requireTeleporterWait("Central teleporter right click did not open its production CampusPlanScreen through the server Open packet");
                    if (!(mc.screen instanceof CampusPlanScreen screen)) return;
                    Object session = field(CampusPlanScreen.class, "session").get(screen);
                    require(session != null && field(CampusPlanScreen.class, "settings").get(screen).equals(PlotSettings.fileCampus("quartz_works"))
                                    && field(session.getClass(), "origin").get(session).equals(teleporterOrigin())
                                    && field(session.getClass(), "dimension").get(session).equals(mc.level.dimension().location())
                                    && !((Screen) screen).isPauseScreen(),
                            "Real server Open packet opens the factory teleporter screen with the generated origin and current dimension");
                    teleporterSessionId = (UUID) field(session.getClass(), "id").get(session);
                    teleporterScreen = screen; teleporterOpened = true; teleporterWaitStartedAt = System.nanoTime(); stage = 12;
                    mc.getSingleplayerServer().execute(() -> {
                        try { verifyTeleporterSession(mc); }
                        catch (Throwable failure) { record("central teleporter server session", failure); }
                        finally { teleporterSessionVerified = true; }
                    });
                }
                case 12 -> {
                    requireTeleporterWait("Central teleporter server session did not verify after the real right click");
                    if (!teleporterSessionVerified || frames < 18) return;
                    if (!failures.isEmpty()) { disconnect(mc); return; }
                    require(mc.screen == teleporterScreen, "Server-opened central teleporter remains the active screen until the user closes it");
                    capture(mc, "world-central-teleporter-right-click");
                    require(((Screen) teleporterScreen).keyPressed(GLFW.GLFW_KEY_E, 0, 0) && mc.screen == null
                                    && field(CampusPlanScreen.class, "sessionClosed").getBoolean(teleporterScreen),
                            "Actual unfocused E key closes the real teleporter screen and sends its session close packet");
                    teleporterWaitStartedAt = System.nanoTime(); stage = 13;
                }
                case 13 -> {
                    requireTeleporterWait("Closing the central teleporter did not remove its actual server-owned session");
                    if (!failures.isEmpty()) { disconnect(mc); return; }
                    if (!teleporterSessionClosed) {
                        if (!teleporterCheckPending) {
                            teleporterCheckPending = true;
                            mc.getSingleplayerServer().execute(() -> {
                                try {
                                    var player = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                                    teleporterSessionClosed = serverTeleporterSession(mc.getSingleplayerServer(), player.getUUID()) == null;
                                } catch (Throwable failure) { record("central teleporter session close", failure); }
                                finally { teleporterCheckPending = false; }
                            });
                        }
                        return;
                    }
                    require(teleporterSessionClosed, "Actual C2S close packet removes the factory central-teleporter session before world sampling and disconnect");
                    teleporterClosed = true; mc.options.hideGui = true; stage = 7;
                    mc.getSingleplayerServer().execute(() -> {
                        try { sampleWorld(mc.getSingleplayerServer().overworld()); }
                        catch (Throwable failure) { record("generated chunks", failure); }
                        finally { sampled = true; }
                    });
                }
                default -> throw new IllegalStateException("Unknown factory audit stage " + stage);
            }
            frames = 0;
        } catch (Throwable failure) {
            record("client lifecycle", failure);
            if (mc.level != null || mc.getSingleplayerServer() != null) disconnect(mc); else finish(mc);
        }
    }

    private static BlockPos teleporterOrigin() { return new BlockPos(0, PlotSettings.fileCampus("quartz_works").surfaceY() + 1, 0); }
    private void requireTeleporterWait(String message) {
        if (System.nanoTime() - teleporterWaitStartedAt > TELEPORTER_WAIT_NANOS) throw new AssertionError(message + " (30 second timeout)");
    }
    private void prepareTeleporter(Minecraft mc) {
        teleporterWaitStartedAt = System.nanoTime();
        mc.getSingleplayerServer().execute(() -> {
            try {
                var level = mc.getSingleplayerServer().overworld(); var origin = teleporterOrigin();
                level.getChunk(origin.getX() >> 4, origin.getZ() >> 4);
                require(level.getBlockState(origin).is(CampusDeviceBlocks.CENTRAL_TELEPORTER.get())
                                && level.getBlockEntity(origin) instanceof CentralTeleporterBlockEntity device
                                && level.dimension().location().equals(device.boundDimension()),
                        "Production fresh-world generator creates and binds the real central teleporter at the factory origin");
                var player = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                player.setGameMode(GameType.CREATIVE);
                player.setItemInHand(InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
                player.setItemInHand(InteractionHand.OFF_HAND, net.minecraft.world.item.ItemStack.EMPTY);
                player.teleportTo(level, .5, origin.getY(), 3.5, 180, 0);
            } catch (Throwable failure) { record("central teleporter setup", failure); }
            finally { teleporterPrepared = true; }
        });
    }
    private static Object serverTeleporterSession(net.minecraft.server.MinecraftServer server, UUID player) throws Exception {
        var servers = (Map<?, ?>) field(CampusTeleportNetwork.class, "SERVERS").get(null);
        Object state = servers.get(server);
        return state == null ? null : ((Map<?, ?>) field(state.getClass(), "sessions").get(state)).get(player);
    }
    private void verifyTeleporterSession(Minecraft mc) throws Exception {
        var server = mc.getSingleplayerServer(); var player = server.getPlayerList().getPlayers().getFirst();
        Object session = serverTeleporterSession(server, player.getUUID());
        String identity = com.gtl.enhancedcore.common.world.campus.CampusPlanAccess.layoutIdentity(PlotSettings.fileCampus("quartz_works"));
        require(session != null && field(session.getClass(), "id").get(session).equals(teleporterSessionId)
                        && field(session.getClass(), "origin").get(session).equals(teleporterOrigin())
                        && field(session.getClass(), "dimension").get(session).equals(player.level().dimension())
                        && field(session.getClass(), "layoutIdentity").get(session).equals(identity),
                "Generated-device right click retains the matching server session and full " + identity.length() + "-character factory layout identity");
    }

    private boolean atlasReady(Minecraft mc) throws Exception {
        if (!(mc.screen instanceof CampusPlanScreen)) throw new AssertionError("Factory atlas closed before its image became ready");
        var cache = (PlotPreviewRenderer.AtlasCached) field(CampusPlanScreen.class, "map").get(mc.screen);
        long elapsed = System.nanoTime() - atlasWaitStartedAt;
        if (elapsed > ATLAS_WAIT_NANOS) throw new AssertionError("Factory atlas did not finish image capture and texture upload within 90 seconds: "
                + sizeName() + " ready=" + cache.ready() + " sourceBuilds=" + cache.sourceBuilds()
                + " textureUploads=" + cache.textureUploads() + " renderedFrames=" + frames);
        if (!cache.ready()) { atlasReadyFrame = -1; return false; }
        if (atlasReadyFrame < 0) { atlasReadyFrame = frames; return false; }
        if (frames - atlasReadyFrame < 3) return false;
        require(cache.textureUploads() > 0, "Atlas screenshot follows completed asynchronous image capture, uploaded texture and three rendered frames: " + sizeName());
        return true;
    }

    private void verifyTransport() throws Exception {
        require("5".equals(field(PlotWorldInfoNetwork.class, "PROTOCOL").get(null)), "Plot-info binary wire uses protocol 5");
        require("2".equals(field(PersonalPlotNetwork.class, "PROTOCOL").get(null)), "Personal-dimension share wire advances its actual protocol 1 to 2");
        require("5".equals(field(CampusTeleportNetwork.class, "PROTOCOL").get(null)), "Teleporter share wire uses protocol 5");
        var infoEncode = PlotWorldInfoNetwork.Snapshot.class.getDeclaredMethod("encode", PlotWorldInfoNetwork.Snapshot.class, FriendlyByteBuf.class);
        var infoDecode = PlotWorldInfoNetwork.Snapshot.class.getDeclaredMethod("decode", FriendlyByteBuf.class);
        var personalEncode = PersonalPlotNetwork.class.getDeclaredMethod("encode", PersonalPlotNetwork.Snapshot.class, FriendlyByteBuf.class);
        var personalDecode = PersonalPlotNetwork.class.getDeclaredMethod("decode", FriendlyByteBuf.class);
        infoEncode.setAccessible(true); infoDecode.setAccessible(true); personalEncode.setAccessible(true); personalDecode.setAccessible(true);
        Class<?> openType = java.util.Arrays.stream(CampusTeleportNetwork.class.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals("Open")).findFirst().orElseThrow();
        var openConstructor = openType.getDeclaredConstructor(java.util.UUID.class, BlockPos.class,
                net.minecraft.resources.ResourceLocation.class, PlotSettings.class, String.class);
        var openEncode = openType.getDeclaredMethod("encode", openType, FriendlyByteBuf.class);
        var openDecode = openType.getDeclaredMethod("decode", FriendlyByteBuf.class);
        openConstructor.setAccessible(true); openEncode.setAccessible(true); openDecode.setAccessible(true);
        var factorySettings = PlotSettings.fileCampus("quartz_works");
        String factoryIdentity = com.gtl.enhancedcore.common.world.campus.CampusPlanAccess.layoutIdentity(factorySettings);
        var openBuffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var packet = openConstructor.newInstance(java.util.UUID.randomUUID(), new BlockPos(1, 2, 3),
                    new net.minecraft.resources.ResourceLocation("gtl_enhancedcore", "factory_audit"), factorySettings, factoryIdentity);
            openEncode.invoke(null, packet, openBuffer);
            Object decoded = openDecode.invoke(null, openBuffer);
            require(field(openType, "settings").get(decoded).equals(factorySettings)
                            && field(openType, "layoutIdentity").get(decoded).equals(factoryIdentity)
                            && openBuffer.readableBytes() == 0,
                    "Actual central-teleporter Open codec preserves quartz_works settings and full layout identity with no trailing bytes");
        } finally { openBuffer.release(); }
        var longIdentityBuffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var packet = openConstructor.newInstance(java.util.UUID.randomUUID(), new BlockPos(1, 2, 3),
                    new net.minecraft.resources.ResourceLocation("gtl_enhancedcore", "factory_audit"), factorySettings,
                    "x".repeat(1025));
            try {
                openEncode.invoke(null, packet, longIdentityBuffer);
                throw new AssertionError("Open codec accepted a layout identity beyond its declared bound");
            } catch (java.lang.reflect.InvocationTargetException expected) {
                require(expected.getCause() instanceof io.netty.handler.codec.EncoderException,
                        "Actual central-teleporter Open codec rejects an oversized layout identity at write time");
            }
        } finally { longIdentityBuffer.release(); }
        var cases = new ArrayList<PlotSettings>(); cases.add(null); cases.add(PlotSettings.defaults());
        cases.add(PlotSettings.plannedCampus()); cases.add(PlotSettings.factoryCampus());
        for (var definition : CampusPresetRegistry.definitions()) cases.add(PlotSettings.fileCampus(definition.id()));
        for (var settings : cases) {
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                infoEncode.invoke(null, new PlotWorldInfoNetwork.Snapshot(settings), buffer);
                var decoded = (PlotWorldInfoNetwork.Snapshot) infoDecode.invoke(null, buffer);
                require(java.util.Objects.equals(settings, decoded.settings()) && buffer.readableBytes() == 0,
                        "Actual binary plot snapshot preserves every field and trailing file ID: " + (settings == null ? "null" : settings.campusKey()));
                buffer.clear();
                var snapshot = new PersonalPlotNetwork.Snapshot(true, true, false, false, false, settings != null,
                        settings, settings, PersonalPlotWorlds.State.NONE, settings, false, false, "");
                personalEncode.invoke(null, snapshot, buffer);
                require(snapshot.equals(personalDecode.invoke(null, buffer)) && buffer.readableBytes() == 0,
                        "Actual personal-dimension snapshot preserves all three nullable file-aware settings slots");
                if (settings != null) {
                    buffer.clear(); buffer.writeUtf(PlotPresetShare.encode(settings), PlotPresetShare.MAX_TEXT_LENGTH);
                    require(PlotPresetShare.decode(buffer.readUtf(PlotPresetShare.MAX_TEXT_LENGTH)).equals(settings)
                                    && buffer.readableBytes() == 0, "Shared settings consume the complete bounded UTF wire value");
                }
            } finally { buffer.release(); }
        }
    }
    private void verifyPaletteAndLabels(Minecraft mc) throws Exception {
        for (String id : FactoryCampus.materials()) {
            var state = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), id, false).blockState();
            require(!state.isAir(), "Authored palette resolves a real registered block state: " + id);
            palette.put(id, state);
        }
        for (var hall : FactoryCampus.halls()) {
            for (var line : signLines(hall)) {
                require(!line.getString().startsWith("gtl_enhancedcore."), "Factory sign translation exists: " + line.getString());
                require(mc.font.width(line) <= 90, "Factory sign line fits the real 90-pixel sign width: " + line.getString());
            }
            int x = hall.signX(), z = hall.signZ();
            require(FactoryCampus.blockAboveAt(PlotSettings.fileCampus("quartz_works"), x, 1, z) == null,
                    "Factory sign stands in clear supported forecourt space: " + hall.id());
        }
    }
    private List<Component> signLines(FactoryArchitecture.Hall hall) {
        int index = FactoryCampus.halls().indexOf(hall) + 1;
        return List.of(Component.translatable(KEY + "factory_preset"),
                Component.translatable("gtl_enhancedcore.factory.department." + hall.department()),
                Component.translatable("gtl_enhancedcore.factory.stage." + hall.stage()),
                Component.translatable(hall.outdoor() ? "gtl_enhancedcore.factory.sign.outdoor" : "gtl_enhancedcore.factory.sign.hall",
                        String.format(java.util.Locale.ROOT, "%02d", index)));
    }
    private static List<FactoryArchitecture.Hall> selectedSites() {
        var byDistance = Comparator.comparingDouble((FactoryArchitecture.Hall h) ->
                Math.pow(h.x() + h.width() / 2.0, 2) + Math.pow(h.z() + h.depth() / 2.0, 2));
        var indoor = FactoryCampus.halls().stream().filter(h -> !h.outdoor() && !h.id().startsWith("shared-")
                        && h.width() <= 180 && h.depth() <= 180)
                .sorted(byDistance).toList();
        if (indoor.isEmpty()) throw new AssertionError("No representative indoor factory hall");
        var selected = new ArrayList<FactoryArchitecture.Hall>(); selected.add(indoor.getFirst());
        indoor.stream().filter(h -> h.variant() != selected.getFirst().variant()).findFirst().ifPresent(selected::add);
        selected.add(FactoryCampus.halls().stream().filter(FactoryArchitecture.Hall::outdoor).min(byDistance).orElseThrow());
        return List.copyOf(selected);
    }
    private void openDashboard(Minecraft mc) throws Exception {
        var size = SIZES[sizeIndex]; resize(mc, size[0], size[1], size[2]);
        var snapshot = new PersonalPlotNetwork.Snapshot(true, true, false, false, false, false,
                null, null, PersonalPlotWorlds.State.NONE, null, false, false, "");
        var constructor = PersonalPlotWorldScreen.class.getDeclaredConstructor(PersonalPlotNetwork.Snapshot.class);
        constructor.setAccessible(true); dashboard = constructor.newInstance(snapshot); mc.setScreen(dashboard);
        stage = 0; frames = 0; GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), 0, 0);
    }
    private PlotSettings draft() throws Exception { return (PlotSettings) field(CreateWorldPlotScreen.class, "draft").get(editor); }
    private static AbstractWidget button(Screen screen, String key) {
        var label = Component.translatable(key).getString();
        return screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                .filter(widget -> widget.getMessage().getString().equals(label)).findFirst().orElseThrow();
    }
    private void click(Screen screen, String key) {
        var button = button(screen, key);
        require(button.active && button.visible && screen.mouseClicked(button.getX() + button.getWidth() / 2.0,
                button.getY() + button.getHeight() / 2.0, 0), "Production mouse click: " + key);
        screen.mouseReleased(button.getX() + button.getWidth() / 2.0, button.getY() + button.getHeight() / 2.0, 0);
    }
    @SuppressWarnings("unchecked")
    private void scrollTo(String key) throws Exception {
        var button = button(editor, KEY + key);
        var positions = (Map<AbstractWidget, Integer>) field(CreateWorldPlotScreen.class, "contentY").get(editor);
        var update = CreateWorldPlotScreen.class.getDeclaredMethod("updateScroll", int.class); update.setAccessible(true);
        update.invoke(editor, positions.get(button));
    }
    @SuppressWarnings("unchecked")
    private void bounds(Screen screen) throws Exception {
        for (var child : screen.children()) if (child instanceof AbstractWidget widget && widget.visible)
            require(widget.getX() >= 0 && widget.getX() + widget.getWidth() <= screen.width,
                    "Visible control fits the real logical width: " + widget.getMessage().getString());
        if (screen instanceof CreateWorldPlotScreen) {
            var fixed = (List<AbstractWidget>) field(CreateWorldPlotScreen.class, "fixedWidgets").get(screen);
            for (var widget : fixed) require(widget.getX() >= 0 && widget.getY() >= 0
                            && widget.getX() + widget.getWidth() <= screen.width && widget.getY() + widget.getHeight() <= screen.height,
                    "Fixed editor control fits the complete logical viewport: " + widget.getMessage().getString());
        }
    }
    private void createWorld(Minecraft mc) {
        mc.setScreen(new TitleScreen()); stage = 6; frames = 0; worldOpened = true;
        var rules = new GameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        rules.getRule(GameRules.RULE_RANDOMTICKING).set(0, null);
        mc.createWorldOpenFlows().createFreshLevel(WORLD,
                new LevelSettings("Quartz Works Audit", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules,
                        WorldDataConfiguration.DEFAULT), new WorldOptions(20261104L, false, false),
                registry -> registry.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                        .value().createWorldDimensions().replaceOverworldGenerator(registry,
                                new PlotChunkGenerator(registry.registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS),
                                        PlotSettings.fileCampus("quartz_works"))));
    }
    private void sampleWorld(ServerLevel level) throws Exception {
        var settings = PlotSettings.fileCampus("quartz_works");
        require(level.getChunkSource().getGenerator() instanceof PlotChunkGenerator generator && generator.settings().equals(settings),
                "Fresh isolated world uses the production factory generator");
        level.setDayTime(6000); level.setWeatherParameters(0, 6000, false, false);
        for (var hall : sites) {
            sampleColumn(level, hall.x() + hall.width() / 2, hall.z() + hall.depth() / 2);
            sampleColumn(level, hall.x() + 5, hall.z() + 20);
            sampleColumn(level, hall.x() + hall.width() / 2, hall.z() + 3);
            int sx = hall.signX(), sz = hall.signZ();
            var pos = new BlockPos(sx, settings.surfaceY() + 1, sz);
            level.getChunk(sx >> 4, sz >> 4);
            require(level.getBlockEntity(pos) instanceof SignBlockEntity, "Actual generated factory sign loads: " + hall.id());
            var sign = (SignBlockEntity) level.getBlockEntity(pos);
            var lines = sign.getFrontText().getMessages(false); var expected = signLines(hall);
            for (int row = 0; row < 4; row++) require(lines[row].getString().equals(expected.get(row).getString()),
                    "Actual factory sign row " + row + " matches the selected indoor/outdoor site: " + hall.id());
        }
        var chosen = new LinkedHashSet<PlannedCampus.Plot>();
        for (String stage : List.of("early", "mid", "late_north", "late_south"))
            FactoryCampus.plots().stream().filter(p -> p.stage().equals(stage)).min(Comparator.comparingLong(p ->
                    (long) p.controllerX() * p.controllerX() + (long) p.controllerZ() * p.controllerZ())).ifPresent(chosen::add);
        chosen.add(FactoryCampus.plots().stream().max(Comparator.comparingInt(PlannedCampus.Plot::controllerY)).orElseThrow());
        for (var plot : chosen) {
            var pos = new BlockPos(plot.controllerX(), settings.surfaceY() + plot.controllerY(), plot.controllerZ());
            level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
            require(level.getBlockState(pos).is(CampusDeviceBlocks.VIRTUAL_MARKER.get()), "Actual marker occupies the full-envelope controller anchor: " + plot.id());
            require(level.getBlockEntity(pos) instanceof VirtualMarkerBlockEntity, "Actual marker BE loads: " + plot.id());
            var marker = (VirtualMarkerBlockEntity) level.getBlockEntity(pos);
            require(marker.machineId().equals(plot.id()) && marker.slotIndex() == plot.slotIndex()
                            && marker.width() == plot.width() && marker.height() == plot.height() && marker.depth() == plot.depth(),
                    "Actual marker metadata preserves machine ID, capacity and upgrade/module envelope: " + plot.id());
            samples.add(Map.of("kind", "controller", "id", plot.id(), "position", pos.toShortString()));
        }
    }
    private void sampleColumn(ServerLevel level, int x, int z) throws Exception {
        var settings = PlotSettings.fileCampus("quartz_works"); level.getChunk(x >> 4, z >> 4);
        int ceiling = FactoryCampus.maxDecorationYAt(settings, x, z);
        var device = FactoryCampus.plots().stream().filter(p -> p.controllerX() == x && p.controllerZ() == z).findFirst().orElse(null);
        for (int dy = 0; dy <= ceiling + 1; dy++) {
            String id = PlotLayout.blockIdAt(settings, x, settings.surfaceY() + dy, z);
            var expected = id.equals("minecraft:air") ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState() : palette.get(id);
            if (device != null && dy == device.controllerY()) expected = CampusDeviceBlocks.VIRTUAL_MARKER.get().defaultBlockState();
            if (expected == null) throw new AssertionError("Sampler returned a state absent from its palette: " + id);
            var pos = new BlockPos(x, settings.surfaceY() + dy, z);
            require(level.getBlockState(pos).equals(expected), "Actual floor, roof and shaped state matches sampler at " + pos + " = " + id);
        }
        samples.add(Map.of("kind", "column", "x", x, "z", z, "ceiling", ceiling));
    }
    private List<View> makeViews() {
        var result = new ArrayList<View>(); int surface = PlotSettings.fileCampus("quartz_works").surfaceY();
        for (int i = 0; i < sites.size(); i++) {
            var hall = sites.get(i); double cx = hall.x() + hall.width() / 2.0, cz = hall.z() + hall.depth() / 2.0;
            int distance = Math.max(8, Math.min(16, (Math.max(hall.width(), hall.depth()) + 31) / 32 + 4));
            String prefix = (hall.outdoor() ? "open-site-" : "factory-hall-") + (i + 1);
            double targetY = surface + (hall.outdoor() ? 1 : hall.clearHeight() / 2.0);
            result.add(lookAt(prefix + "-overview", cx + 36, surface + (hall.outdoor() ? 120 : hall.clearHeight() + 70), cz - 48,
                    cx, targetY, cz, distance, hall.bounds()));
            result.add(lookAt(prefix + "-entrance", cx + 18, surface + 3, hall.z() - 25,
                    cx, surface + 8, hall.z() + 15, 8, new PlannedCampus.Bounds((int) cx - 32, hall.z() - 3, (int) cx + 32, hall.z() + 28)));
        }
        result.add(lookAt("central-dispatch-overview", -32, surface + 240, -32,
                0, surface + 6, 0, 20, new PlannedCampus.Bounds(-276, -276, 275, 275)));
        result.add(lookAt("central-east-entry", 55, surface + 8, -.5,
                120, surface + 9, -.5, 14, new PlannedCampus.Bounds(79, -86, 270, 86)));
        return List.copyOf(result);
    }
    private static View lookAt(String name, double x, double y, double z, double tx, double ty, double tz,
                               int distance, PlannedCampus.Bounds target) {
        double dx = tx - x, dy = ty - y, dz = tz - z;
        return new View(name, x, y, z, (float) Math.toDegrees(Math.atan2(-dx, dz)),
                (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))), distance, target);
    }
    private void move(Minecraft mc) {
        moving = true; frames = sceneTicks = stableTicks = 0; lastStatistics = ""; var view = views.get(viewIndex);
        mc.options.renderDistance().set(view.distance()); mc.options.broadcastOptions(); mc.levelRenderer.allChanged();
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
    private boolean sceneReady(Minecraft mc, View view) {
        if (++sceneTicks > 1800) throw new AssertionError("Scene failed to finish loading/rendering: " + view.name() + " " + mc.levelRenderer.getChunkStatistics());
        if (sceneTicks < 100) return false;
        for (int x = view.target().minX() >> 4; x <= view.target().maxX() >> 4; x++)
            for (int z = view.target().minZ() >> 4; z <= view.target().maxZ() >> 4; z++) if (!mc.level.hasChunk(x, z)) return false;
        String statistics = mc.levelRenderer.getChunkStatistics();
        if (mc.levelRenderer.countRenderedChunks() < 8 || !mc.levelRenderer.hasRenderedAllChunks() || !statistics.equals(lastStatistics)) {
            stableTicks = 0; lastStatistics = statistics; return false;
        }
        return ++stableTicks >= 12;
    }
    private void disconnect(Minecraft mc) {
        stage = 9; mc.options.hideGui = false;
        if (mc.screen instanceof CampusPlanScreen) mc.screen.onClose();
        if (mc.level != null) mc.level.disconnect();
        mc.clearLevel(); mc.setScreen(new TitleScreen());
    }
    private static void resize(Minecraft mc, int width, int height, int scale) {
        mc.options.guiScale().set(scale); GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), width, height);
        GLFW.glfwPollEvents(); mc.resizeDisplay();
    }
    private String sizeName() { return SIZES[sizeIndex][0] + "x" + SIZES[sizeIndex][1] + "-gui" + SIZES[sizeIndex][2]; }
    private void capture(Minecraft mc, String name) throws Exception {
        if (stage < 6) {
            var size = SIZES[sizeIndex];
            require(mc.getWindow().getWidth() == size[0] && mc.getWindow().getHeight() == size[1], "Actual framebuffer matches requested size: " + name);
            if (sizeIndex == 2) require(mc.getWindow().getGuiScaledWidth() == 320 && mc.getWindow().getGuiScaledHeight() == 240,
                    "Actual factory screen logical minimum is 320x240");
        }
        String file = "screenshots/" + name + ".png";
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            require(image.getWidth() == mc.getWindow().getWidth() && image.getHeight() == mc.getWindow().getHeight(),
                    "Saved screenshot uses the complete actual framebuffer: " + name);
            image.writeToFile(directory.resolve(file));
        }
        var item = new LinkedHashMap<String, Object>(); item.put("file", file); item.put("width", mc.getWindow().getWidth());
        item.put("height", mc.getWindow().getHeight()); item.put("logicalWidth", mc.getWindow().getGuiScaledWidth());
        item.put("logicalHeight", mc.getWindow().getGuiScaledHeight()); item.put("frames", frames);
        if (stage == 4) {
            var cache = (PlotPreviewRenderer.AtlasCached) field(CampusPlanScreen.class, "map").get(mc.screen);
            item.put("atlasReady", cache.ready()); item.put("atlasTextureUploads", cache.textureUploads());
            item.put("atlasWaitMillis", (System.nanoTime() - atlasWaitStartedAt) / 1_000_000L);
        }
        if (mc.player != null) { item.put("camera", mc.player.position().toString()); item.put("chunkStatistics", mc.levelRenderer.getChunkStatistics()); }
        captures.add(item);
    }
    private static Field field(Class<?> type, String name) throws Exception { var field = type.getDeclaredField(name); field.setAccessible(true); return field; }
    private void require(boolean value, String text) { if (!value) throw new AssertionError(text); checks.add(text); }
    private void record(String group, Throwable error) { failures.add(group + ": " + error); error.printStackTrace(); }
    private void finish(Minecraft mc) {
        done = true;
        if (directory != null) try {
            var report = new LinkedHashMap<String, Object>(); report.put("status", failures.isEmpty() ? "PASS" : "FAIL");
            report.put("checks", checks); report.put("failures", failures); report.put("screenshots", captures); report.put("samples", samples);
            report.put("paletteStates", palette.size()); report.put("language", mc.options.languageCode);
            report.put("worldOpened", worldOpened); report.put("serverStopped", serverStopped);
            report.put("centralTeleporter", Map.of("rightClicked", teleporterRightClicked, "opened", teleporterOpened,
                    "closedOnServer", teleporterClosed, "origin", teleporterOrigin().toShortString()));
            report.put("sites", sites == null ? List.of() : sites);
            report.put("scope", "Real OP dashboard and production factory preset clicks at three framebuffer/GUI sizes, actual loaded palette and sign-font widths. Optional fresh isolated world right-clicks the generated central teleporter through the real client/server Open packet, verifies the live session and closes it with E, then samples controller markers, shaped blocks, roofs and indoor/outdoor signs with a few local camera views. No player world, server-template packet, full-island generation, real machines or performance benchmark.");
            Files.writeString(directory.resolve("factory-campus-checks.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
        } catch (Exception error) { error.printStackTrace(); }
        mc.stop();
    }
}
