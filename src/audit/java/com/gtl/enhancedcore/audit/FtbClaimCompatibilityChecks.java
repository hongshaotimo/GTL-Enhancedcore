package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import com.gtl.enhancedcore.client.ftbchunks.ClaimMode;
import com.gtl.enhancedcore.client.ftbchunks.ClaimModeState;
import com.gtl.enhancedcore.client.ftbchunks.RectangleSelectionState;
import com.gtl.enhancedcore.mixin.ftbchunks.client.ChunkScreenAccessor;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbchunks.client.gui.ChunkScreen;
import dev.ftb.mods.ftbchunks.client.map.MapDimension;
import dev.ftb.mods.ftblibrary.math.XZ;
import dev.ftb.mods.ftblibrary.ui.ScreenWrapper;
import dev.ftb.mods.ftblibrary.ui.Widget;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Full native claim screen and server packets in a fresh, explicitly marked audit world. */
@Mod("enhancedcore_audit")
public final class FtbClaimCompatibilityChecks {
    private static final Path ROOT = Path.of("C:/IDEA/GTL-Enhancedcore/_audit/gtlcore-compat-20261107");
    private static final int[][] SIZES = {{960,600,2},{1280,720,3},{640,480,2}};
    private final List<String> checks = new ArrayList<>();
    private final List<Object> scenarios = new ArrayList<>();
    private Path directory;
    private ChunkScreen gui;
    private Screen screen;
    private List<Widget> buttons;
    private Set<XZ> expected;
    private int stage, frames, ticks, sizeIndex, scenario, x, y;
    private boolean done, renderActive;
    private boolean serverCheckScheduled;
    private volatile Boolean serverMatches;
    private volatile Throwable serverError;
    private volatile boolean fixtureReady = true;

    public FtbClaimCompatibilityChecks() {
        MinecraftForge.EVENT_BUS.addListener(this::tick);
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (!renderActive || done || event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.getOverlay() != null || mc.screen != screen) return;
        try {
            // Native root render receives controlled coordinates; GLFW held-state is audit-agent input.
            var graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
            screen.render(graphics, x, y, 0);
            graphics.flush();
            frames++;
        } catch (Throwable failure) { finish(failure); }
    }

    private void tick(TickEvent.ClientTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END || !Boolean.getBoolean("gtl.enhancedcore.ftbClaimAudit")) return;
        var mc = Minecraft.getInstance();
        try {
            if (++ticks > 10000) throw new AssertionError("FTB compatibility audit timed out at " + stage);
            if (stage == 0) {
                if (mc.getOverlay() != null) return;
                if (mc.screen instanceof com.gtl.enhancedcore.client.gui.ChangelogScreen) { mc.screen.onClose(); return; }
                if (!(mc.screen instanceof TitleScreen)) return;
                directory = mc.gameDirectory.toPath().toAbsolutePath().normalize();
                require(directory.startsWith(ROOT) && !directory.equals(ROOT)
                        && Files.isRegularFile(directory.resolve("ftb-claim.marker")), "Explicit isolated fixture marker");
                require(mc.level == null && mc.getSingleplayerServer() == null, "Starts at title with no player world");
                checks.addAll(DataItemBehaviorCompatibilityChecks.run());
                Files.createDirectories(directory.resolve("screenshots"));
                require(!Files.exists(directory.resolve("saves/ftb-claim-audit")), "Fresh isolated world required");
                stage = 1;
                mc.createWorldOpenFlows().createFreshLevel("ftb-claim-audit",
                        new LevelSettings("FTB Claim Compatibility", GameType.CREATIVE, false, Difficulty.PEACEFUL,
                                true, new GameRules(), WorldDataConfiguration.DEFAULT),
                        new WorldOptions(20261107L, false, false), registry -> registry.registryOrThrow(Registries.WORLD_PRESET)
                                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
                return;
            }
            if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null || mc.getOverlay() != null) return;
            if (stage == 1) {
                if (MapDimension.getCurrent().isEmpty()) return;
                mc.setScreen(null); open(); stage = 2; return;
            }
            if (frames < 6) return;
            switch (stage) {
                case 2 -> {
                    if(serverError!=null) throw new AssertionError("Claim fixture",serverError);
                    if(!fixtureReady) return;
                    require(buttons.size() == 961, "Native screen contains 31x31 chunk buttons at size " + sizeIndex);
                    // Switch modes only through the actual root mouse event and injected native button.
                    ClaimMode wanted = (scenario % 4) < 2 ? ClaimMode.RECTANGLE : ClaimMode.FREEHAND;
                    if (ClaimModeState.current() != wanted) {
                        Widget mode = gui.getWidgets().stream().filter(w -> w.getX() == gui.getWidth() - 18
                                && w.getY() == 2).findFirst().orElseThrow();
                        point(mode); gui.updateGui(x,y,0); screen.mouseClicked(x,y,0); screen.mouseReleased(x,y,0);
                        require(ClaimModeState.current() == wanted, "Native mode button changes mode");
                    }
                    System.setProperty("gtl.enhancedcore.ftbAudit.key.341", scenario % 2 == 1 ? "1" : "0");
                    require(Screen.hasControlDown() == (scenario % 2 == 1), "Controlled Ctrl key reaches native Screen");
                    point(buttons.get(14 * 31 + 14)); gui.updateGui(x,y,0);
                    int mouse = scenario < 4 ? 0 : 1;
                    System.setProperty("gtl.enhancedcore.ftbAudit.mouse." + mouse, "1");
                    screen.mouseClicked(x,y,mouse); frames=0; stage=3;
                }
                case 3 -> {
                    require(selection().size() == 1, "Mouse-down selects start chunk");
                    point(buttons.get(16 * 31 + 16));
                    screen.mouseDragged(x,y,scenario < 4 ? 0 : 1,2,2); frames=0; stage=4;
                }
                case 4 -> {
                    int count = (scenario % 4) < 2 ? 9 : 2;
                    require(selection().size() == count, "Drag selects " + count + " chunks with mode/Ctrl " + scenario);
                    expected = new HashSet<>(selection());
                    capture("size" + sizeIndex + "-scenario" + scenario + "-drag");
                    int mouse = scenario < 4 ? 0 : 1;
                    System.setProperty("gtl.enhancedcore.ftbAudit.mouse." + mouse,"0");
                    screen.mouseReleased(x,y,mouse);
                    require(selection().isEmpty() && RectangleSelectionState.getFirstChunk() == null,
                            "Native release clears selection and Enhancedcore start state");
                    serverMatches=null; serverError=null;serverCheckScheduled=false;
                    frames=0; stage=5;
                }
                case 5 -> {
                    if(!serverCheckScheduled) {
                        serverCheckScheduled=true;
                        mc.getSingleplayerServer().execute(() -> {
                            try {
                                var manager=FTBChunksAPI.api().getManager();
                                Set<XZ> actual=new HashSet<>();
                                for(var claim:manager.getAllClaimedChunks()) actual.add(XZ.of(claim.getPos().x(),claim.getPos().z()));
                                // Right-click starts with the selected claim fixture; left-click starts empty.
                                serverMatches = scenario < 4 ? actual.equals(expected) : actual.isEmpty();
                                for(var claim:List.copyOf(manager.getAllClaimedChunks()))
                                    claim.unclaim(mc.getSingleplayerServer().createCommandSourceStack(),false);
                            } catch(Throwable failure) {serverError=failure;}
                        });
                        return;
                    }
                    if(serverError!=null) throw new AssertionError("Server claim validation",serverError);
                    if(serverMatches==null) return;
                    require(serverMatches,"Server receives exact native selection without Ctrl expansion");
                    scenarios.add(java.util.Map.of("size",sizeIndex,"scenario",scenario,"selected",expected.size(),
                            "ctrl",scenario%2==1,"mode",ClaimModeState.current().name(),"mouse",scenario<4?"left":"right"));
                    if(++scenario==8) {scenario=0;if(++sizeIndex==SIZES.length){finish(null);return;}}
                    open(); stage=2;
                }
                default -> throw new AssertionError("Unexpected stage");
            }
        } catch(Throwable failure) {finish(failure);}
    }

    private void open() {
        var mc=Minecraft.getInstance(); renderActive=false;
        System.setProperty("gtl.enhancedcore.ftbAudit.mouse.0","0");
        System.setProperty("gtl.enhancedcore.ftbAudit.mouse.1","0");
        int[] size=SIZES[sizeIndex]; mc.options.guiScale().set(size[2]);
        GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),size[0],size[1]); mc.resizeDisplay();
        ChunkScreen.openChunkScreen();
        require(mc.screen instanceof ScreenWrapper,"Native openChunkScreen creates ScreenWrapper");
        screen=mc.screen; gui=(ChunkScreen)((ScreenWrapper)screen).getGui();
        buttons=gui.getWidgets().stream().filter(w->w.getClass().getName().endsWith("ChunkScreen$ChunkButton")).toList();
        x=0;y=0;frames=0;renderActive=true;
        if(scenario>=4) {
            fixtureReady=false;serverError=null;
            var server=mc.getSingleplayerServer(); var player=mc.player.getUUID();
            var base=mc.player.chunkPosition();
            server.execute(()->{
                try {
                var p=server.getPlayerList().getPlayer(player);
                boolean rectangle=(scenario%4)<2;
                for(int dz=-1;dz<=1;dz++) for(int dx=-1;dx<=1;dx++)
                    if(rectangle||(dx==-1&&dz==-1)||(dx==1&&dz==1))
                        FTBChunksAPI.api().claimAsPlayer(p,p.level().dimension(),new net.minecraft.world.level.ChunkPos(base.x+dx,base.z+dz),false);
                int count=FTBChunksAPI.api().getManager().getAllClaimedChunks().size();
                if(count!=(rectangle?9:2)) throw new AssertionError("Right-click fixture claims "+count);
                fixtureReady=true;
                }catch(Throwable failure){serverError=failure;}
            });
        }
    }
    private Set<XZ> selection(){return ((ChunkScreenAccessor)gui).getSelectedChunks();}
    private void point(Widget button){x=button.getX()+button.getWidth()/2;y=button.getY()+button.getHeight()/2;}
    private void capture(String name) throws Exception {
        try(var image=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
            image.writeToFile(directory.resolve("screenshots/"+name+".png"));
        }
    }
    private void require(boolean value,String message){if(!value) throw new AssertionError(message);checks.add(message);}
    private void finish(Throwable failure) {
        if(done)return;done=true;renderActive=false;
        if(failure!=null)failure.printStackTrace();
        try {
            var result=new LinkedHashMap<String,Object>();result.put("status",failure==null?"PASS":"FAIL");
            result.put("error",failure==null?null:failure.toString());result.put("checks",checks);result.put("scenarios",scenarios);
            result.put("scope","Fresh isolated world; native ChunkScreen open/render/root mouse events and server packets. GLFW held mouse/Ctrl state supplied by audit-only agent; no physical desktop input or player saves.");
            if(directory!=null)Files.writeString(directory.resolve("ftb-claim-checks.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));
        }catch(Exception error){error.printStackTrace();}
        Minecraft.getInstance().stop();
    }
}
