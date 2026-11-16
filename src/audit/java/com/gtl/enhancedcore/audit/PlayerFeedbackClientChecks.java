package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import com.gtl.enhancedcore.client.plot.CampusPlanScreen;
import com.gtl.enhancedcore.common.item.PortableCentralTeleporterItem;
import com.gtl.enhancedcore.common.world.campus.CampusDeviceBlocks;
import com.gtl.enhancedcore.common.world.campus.CampusPlanAccess;
import com.gtl.enhancedcore.common.world.plot.PlotChunkGenerator;
import com.gtl.enhancedcore.common.world.plot.PlotSettings;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

/** A single fresh campus; fixtures only equip/move the player, all actions use native entry points. */
@Mod("enhancedcore_audit")
public final class PlayerFeedbackClientChecks {
    private static final Path ROOT = Path.of("C:/IDEA/GTL-Enhancedcore/_audit/player-feedback-20261116");
    private static final PlotSettings SETTINGS = PlotSettings.plannedCampus();
    private static final BlockPos CENTRE = new BlockPos(0, SETTINGS.surfaceY() + 1, 0);
    private static final InteractionHand HAND = InteractionHand.MAIN_HAND;
    private static final int[][] SIZES = {{960,600,2},{1280,720,3},{640,480,2}};
    private final List<String> checks = new ArrayList<>(), failures = new ArrayList<>();
    private final List<Map<String,Object>> captures = new ArrayList<>();
    private Path directory;
    private int stage, frames, ticks, size;
    private long stageStart = System.nanoTime();
    private boolean done;
    private volatile boolean serverDone, serverStopped;
    private volatile Throwable serverFailure;
    private ItemStack bound;
    private Screen screen;
    private CampusPlanAccess.Target destination;
    private double clickX, clickY;

    public PlayerFeedbackClientChecks() {
        MinecraftForge.EVENT_BUS.addListener(this::tick);
        MinecraftForge.EVENT_BUS.addListener(this::render);
        MinecraftForge.EVENT_BUS.addListener(this::stopped);
    }
    private void stopped(ServerStoppedEvent event) { serverStopped = true; }
    private void render(TickEvent.RenderTickEvent event) {
        if (!done && event.phase == TickEvent.Phase.END && Minecraft.getInstance().getOverlay() == null) frames++;
    }
    private void next(int value) { stage=value; frames=0; stageStart=System.nanoTime(); }
    private void tick(TickEvent.ClientTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END || !Boolean.getBoolean("gtl.enhancedcore.playerFeedbackAudit")) return;
        var mc=Minecraft.getInstance();
        try {
            if (++ticks>18000 || stage>1 && System.nanoTime()-stageStart>90_000_000_000L)
                throw new AssertionError("Portable interaction timeout at stage "+stage);
            if (serverFailure!=null) throw new AssertionError("Fixture failed",serverFailure);
            if (mc.getOverlay()!=null) return;
            switch(stage) {
                case 0 -> {
                    directory=mc.gameDirectory.toPath().toAbsolutePath().normalize();
                    require(directory.getParent().equals(ROOT) && Files.isRegularFile(directory.resolve("player-feedback.marker")),"Marked isolated directory");
                    if (mc.screen instanceof com.gtl.enhancedcore.client.gui.ChangelogScreen) {mc.screen.onClose();return;}
                    if (!(mc.screen instanceof TitleScreen)) return;
                    require(mc.level==null && mc.getConnection()==null && !Files.exists(directory.resolve("saves/player-feedback")),"Only one fresh isolated world");
                    Files.createDirectories(directory.resolve("screenshots")); next(1);
                    mc.createWorldOpenFlows().createFreshLevel("player-feedback",new LevelSettings("Player Feedback",GameType.CREATIVE,false,
                            Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(20261116L,false,false),
                            r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions()
                                    .replaceOverworldGenerator(r,new PlotChunkGenerator(r.registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS),SETTINGS)));
                }
                case 1 -> {
                    if (mc.player==null || mc.gameMode==null || mc.getSingleplayerServer()==null) return;
                    mc.setScreen(null); mc.options.pauseOnLostFocus=false;
                    server(mc,p->{
                        require(p.serverLevel().getChunkSource().getGenerator() instanceof PlotChunkGenerator,"Real PlotChunkGenerator world");
                        p.serverLevel().getChunkAt(CENTRE);
                        require(p.serverLevel().getBlockState(CENTRE).is(CampusDeviceBlocks.CENTRAL_TELEPORTER.get()),"Generator placed real central device");
                        p.getInventory().selected=0;
                        p.setItemInHand(HAND,new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("gtl_enhancedcore","portable_central_teleporter"))));
                        p.teleportTo(p.serverLevel(),2.5,CENTRE.getY()+1,2.5,0,0); sync(p);
                    }); next(2);
                }
                case 2 -> {
                    if (!serverDone || frames<35 || !(mc.player.getMainHandItem().getItem() instanceof PortableCentralTeleporterItem)) return;
                    require(PortableCentralTeleporterItem.binding(mc.player.getMainHandItem())==null,"Initial item is unbound");
                    useAir(mc); next(3);
                }
                case 3 -> {
                    if (frames<30) return;
                    require(!(mc.screen instanceof CampusPlanScreen),"Unbound native use opens no teleporter screen");
                    require(mc.gameMode.useItemOn(mc.player,HAND,new BlockHitResult(Vec3.atCenterOf(CENTRE),Direction.UP,CENTRE,false)).consumesAction(),"Native right click on actual campus centre"); next(4);
                }
                case 4 -> {
                    if (frames<20 || PortableCentralTeleporterItem.binding(mc.player.getMainHandItem())==null) return;
                    require(mc.screen==null,"Centre click binds without opening block GUI");
                    var binding=PortableCentralTeleporterItem.binding(mc.player.getMainHandItem());
                    require(binding.origin().equals(CENTRE) && binding.layoutIdentity().equals(CampusPlanAccess.layoutIdentity(SETTINGS)),"Binding retains real centre and exact plan");
                    server(mc,p->{bound=p.getMainHandItem().copy();p.teleportTo(p.serverLevel(),40.5,CENTRE.getY()+1,0.5,0,0);}); next(5);
                }
                case 5 -> {
                    if (!serverDone || frames<35 || mc.player.distanceToSqr(Vec3.atCenterOf(CENTRE))<64) return;
                    useAir(mc); next(6);
                }
                case 6 -> {
                    if (!(mc.screen instanceof CampusPlanScreen) || frames<20) return;
                    screen=mc.screen; resize(mc); next(7);
                }
                case 7 -> {
                    if (frames<25) return;
                    capture(mc);
                    if (++size<SIZES.length) {resize(mc);next(7);}
                    else {choose();server(mc,p->{p.setItemInHand(HAND,ItemStack.EMPTY);sync(p);});next(8);}
                }
                case 8 -> {
                    if (!serverDone || frames<25 || !mc.player.getMainHandItem().isEmpty()) return;
                    rightClick(); next(9);
                }
                case 9 -> {
                    if (frames<25 || !(boolean)get("sessionInvalid")) return;
                    require(!(boolean)get("pending"),"Changed held item rejects the actual travel packet");
                    require(mc.player.distanceToSqr(Vec3.atCenterOf(CENTRE))>64,"Rejected request leaves remote player in place");
                    screen.onClose();server(mc,p->{p.setItemInHand(HAND,bound.copy());sync(p);}); next(10);
                }
                case 10 -> {
                    if (!serverDone || frames<30 || PortableCentralTeleporterItem.binding(mc.player.getMainHandItem())==null) return;
                    useAir(mc);next(11);
                }
                case 11 -> {
                    if (!(mc.screen instanceof CampusPlanScreen) || frames<25) return;
                    screen=mc.screen;choose();rightClick();next(12);
                }
                case 12 -> {
                    if (mc.screen instanceof CampusPlanScreen || frames<30) return;
                    server(mc,p->{
                        double dx=p.getX()-(destination.arrivalX()+.5),dz=p.getZ()-(destination.arrivalZ()+.5);
                        require(dx*dx+dz*dz<=145,"Native selected-plot travel reaches the server safe arrival area");
                        require(PortableCentralTeleporterItem.binding(p.getMainHandItem())!=null,"Successful travel retains bound item");
                    });next(13);
                }
                case 13 -> {
                    if (!serverDone) return;
                    next(14);mc.level.disconnect();mc.clearLevel();mc.setScreen(new TitleScreen());
                }
                case 14 -> {if (serverStopped) finish(mc);}
            }
        } catch(Throwable failure) {failures.add(failure.toString());failure.printStackTrace();finish(mc);}
    }
    private void server(Minecraft mc,Consumer<ServerPlayer> action) {
        serverDone=false;var id=mc.player.getUUID();var server=mc.getSingleplayerServer();
        server.execute(()->{try {action.accept(server.getPlayerList().getPlayer(id));}catch(Throwable error){serverFailure=error;}finally {serverDone=true;}});
    }
    private static void sync(ServerPlayer player) {player.getInventory().setChanged();player.inventoryMenu.broadcastChanges();}
    private void useAir(Minecraft mc) {require(mc.gameMode.useItem(mc.player,HAND).consumesAction(),"Native item air-use handled");}
    private Object get(String name) throws Exception {var f=CampusPlanScreen.class.getDeclaredField(name);f.setAccessible(true);return f.get(screen);}
    private double number(String name) throws Exception {return ((Number)get(name)).doubleValue();}
    private void choose() throws Exception {
        var plan=CampusPlanAccess.of(SETTINGS);var bounds=plan.bounds();double side=number("baseSize")*number("zoom");
        for (var target:plan.targets()) {
            if (target.kind()!=CampusPlanAccess.Kind.MACHINE) continue;
            double wx=(target.bounds().minX()+target.bounds().maxX())/2.0,wz=(target.bounds().minZ()+target.bounds().maxZ())/2.0;
            double x=(number("mapLeftEdge")+number("mapRightEdge"))/2+(wx-number("centerX"))*side/bounds.width();
            double y=(number("mapTop")+number("mapBottom"))/2+(wz-number("centerZ"))*side/bounds.depth();
            if (x<=number("mapLeftEdge") || x>=number("mapRightEdge") || y<=number("mapTop") || y>=number("mapBottom")) continue;
            require(screen.mouseClicked(x,y,0) && screen.mouseReleased(x,y,0),"Root screen left-click selects plot");
            require(target.equals(get("selected")) && !(boolean)get("pending"),"Left-click selects exact target without travelling");
            destination=target;clickX=x;clickY=y;return;
        }
        throw new AssertionError("No visible plot centre");
    }
    private void rightClick() {require(screen.mouseClicked(clickX,clickY,1),"Root screen right-click requests selected destination");screen.mouseReleased(clickX,clickY,1);}
    private void resize(Minecraft mc) {
        int[] s=SIZES[size];mc.options.guiScale().set(s[2]);GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),s[0],s[1]);GLFW.glfwPollEvents();mc.resizeDisplay();
    }
    private void capture(Minecraft mc) throws Exception {
        int[] s=SIZES[size];require(mc.getWindow().getWidth()==s[0] && mc.getWindow().getHeight()==s[1],"Real framebuffer matches requested size");
        if (size==2) require(screen.width==320 && screen.height==240,"Actual 320x240 logical minimum");
        for (var child:screen.children()) if (child instanceof AbstractWidget w && w.visible)
            require(w.getX()>=0 && w.getY()>=0 && w.getX()+w.getWidth()<=screen.width && w.getY()+w.getHeight()<=screen.height,"Visible native control fits viewport");
        String file="screenshots/portable-"+s[0]+"x"+s[1]+"-gui"+s[2]+".png";
        try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())) {image.writeToFile(directory.resolve(file));}
        captures.add(Map.of("file",file,"logicalWidth",screen.width,"logicalHeight",screen.height,"renderFrames",frames));
    }
    private void require(boolean value,String message) {if (!value) throw new AssertionError(message);checks.add(message);}
    private void finish(Minecraft mc) {
        done=true;
        try {
            var result=new LinkedHashMap<String,Object>();result.put("status",failures.isEmpty() && serverStopped && captures.size()==3?"PASS":"FAIL");
            result.put("checks",checks);result.put("failures",failures);result.put("screenshots",captures);result.put("serverStopped",serverStopped);
            result.put("scope","One new production PlotChunkGenerator world; native item use/block interaction and real server packets. Root screen mouse selection and right-click travel, changed-held-item rejection. Three rendered sizes. Wrong-dimension use and player worlds not tested.");
            if(directory==null) directory=mc.gameDirectory.toPath();
            Files.writeString(directory.resolve("player-feedback-checks.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));
        } catch(Throwable error) {error.printStackTrace();}mc.stop();
    }
}
