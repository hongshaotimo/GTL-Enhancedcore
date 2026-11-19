package com.gtl.enhancedcore.audit;

import com.google.gson.Gson;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

/** Real framebuffer only; no manual calls to the machine renderer. */
@Mod("enhancedcore_audit")
public final class ChargerFieldPair {
    static final BlockPos POS = new BlockPos(0,80,0);
    static final ResourceLocation ID = new ResourceLocation("gtl_enhancedcore:mv_wireless_charger");
    final Path dir = Path.of("").toAbsolutePath().normalize();
    final String pack = System.getProperty("charger.pack", "");
    int stage, frames;
    CompletableFuture<Void> task;
    public ChargerFieldPair() { if (Boolean.getBoolean("charger.pair")) MinecraftForge.EVENT_BUS.register(this); }
    static void require(boolean ok, String reason) { if (!ok) throw new IllegalStateException(reason); }

    @SubscribeEvent public void frame(TickEvent.RenderTickEvent e) throws Exception {
        if (stage == 5 || e.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        try {
            if (stage == 0) {
                if (mc.screen == null || mc.getOverlay() != null) return;
                require(dir.getParent().equals(Path.of("C:/IDEA/GTL-Enhancedcore/_work/charger-field-ab")) &&
                    mc.gameDirectory.toPath().toRealPath().equals(dir.toRealPath()) && Files.isRegularFile(dir.resolve("pair.marker")) &&
                    !Files.exists(dir.resolve("saves/field-pair")), "Not a fresh isolated directory");
                require(GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(), GLFW.GLFW_VISIBLE) == 0, "Game window not hidden");
                stage = 1;
                mc.createWorldOpenFlows().createFreshLevel("field-pair", new LevelSettings("Field pair", GameType.CREATIVE,
                    false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT), new WorldOptions(712L,false,false),
                    r -> r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
                return;
            }
            if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;
            var server = mc.getSingleplayerServer();
            if (stage == 1) {
                var uuid = mc.player.getUUID();
                task = CompletableFuture.runAsync(() -> {
                    var w = server.overworld();
                    w.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);
                    w.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false,server);
                    w.setDayTime(6000); w.setWeatherParameters(60000,0,false,false);
                    for (var p : BlockPos.betweenClosed(-10,78,-5,10,90,-5)) w.setBlockAndUpdate(p, Blocks.GRAY_CONCRETE.defaultBlockState());
                    w.setBlockAndUpdate(new BlockPos(0,81,8),Blocks.STONE.defaultBlockState());
                    server.getPlayerList().getPlayer(uuid).connection.teleport(.5,82,8.5,180,0);
                },server);
                mc.options.setCameraType(CameraType.FIRST_PERSON); mc.options.hideGui = true;
                stage = 2; return;
            }
            if (!task.isDone()) return;
            task.join();
            if (mc.screen != null || mc.getOverlay() != null) { frames = 0; return; }
            if (++frames < 120) return;
            var camera = mc.gameRenderer.getMainCamera(); var p = camera.getPosition();
            require(Math.abs(p.x-.5)<.001 && Math.abs(p.y-83.62)<.002 && Math.abs(p.z-8.5)<.001 &&
                Math.abs(Math.abs(camera.getYRot())-180)<.001 && Math.abs(camera.getXRot())<.001,"Camera moved");
            boolean present = stage == 3;
            require(present ? ID.equals(ForgeRegistries.BLOCKS.getKey(mc.level.getBlockState(POS).getBlock())) && mc.level.getBlockEntity(POS)!=null : mc.level.isEmptyBlock(POS), "Unexpected client block state");
            var api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            boolean active = (boolean)api.getMethod("isShaderPackInUse").invoke(api.getMethod("getInstance").invoke(null));
            String name = String.valueOf(Class.forName("net.irisshaders.iris.Iris").getMethod("getCurrentPackName").invoke(null));
            require(active == !pack.isEmpty() && (!active || pack.equals(name)), "Shader mismatch: active="+active+", name="+name);
            String shot = present ? "charger" : "air";
            try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(dir.resolve(shot+".png")); }
            Files.writeString(dir.resolve(shot+".json"),new Gson().toJson(Map.of("camera",List.of(p.x,p.y,p.z,camera.getYRot(),camera.getXRot()),"shaderActive",active,"pack",name)));
            if (!present) {
                task = CompletableFuture.runAsync(() -> require(server.overworld().setBlockAndUpdate(POS,
                    Objects.requireNonNull(ForgeRegistries.BLOCKS.getValue(ID)).defaultBlockState()),"Placement failed"), server);
                stage = 3; frames = 0;
            } else finish(mc,"PASS","Captured pair; field visibility requires image review");
        } catch (Throwable err) { finish(mc,"FAIL",err.toString()); }
    }
    void finish(Minecraft mc,String status,String note) throws Exception {
        stage = 5; Files.writeString(dir.resolve("capture.json"),new Gson().toJson(Map.of("status",status,"detail",note))); mc.stop();
    }
}
