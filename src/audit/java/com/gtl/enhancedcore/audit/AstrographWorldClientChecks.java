package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.*;

/** Fresh integrated world only; detached registered BE renders in the real world pipeline. */
@Mod("enhancedcore_audit")
public final class AstrographWorldClientChecks {
    private static final Path ROOT=Path.of("C:/IDEA/GTL-Enhancedcore/_work/astrograph-black-screen-20261107/validation");
    private static final BlockPos POS=new BlockPos(0,-23,75);
    private static final Vec3 CENTER=new Vec3(.5,80.5,.5);
    private final List<Map<String,Object>> rows=new ArrayList<>(), errors=new ArrayList<>();
    private int state,ticks,scenario=-1,frames,glErrors;
    private boolean done,enabled;
    private BlockEntity entity;
    private WorkableMultiblockMachine machine;
    private Class<?> iris;
    private GLDebugMessageCallback callback;
    private Map<String,Object> row;
    private java.util.concurrent.CompletableFuture<Void> reload;
    public AstrographWorldClientChecks(){ MinecraftForge.EVENT_BUS.addListener(this::tick); MinecraftForge.EVENT_BUS.addListener(this::render); }

    private void tick(TickEvent.ClientTickEvent event){
        if(done||event.phase!=TickEvent.Phase.END||!Boolean.getBoolean("gtl.astrographWorld.audit"))return;
        var mc=Minecraft.getInstance();
        try{
            var dir=mc.gameDirectory.toPath().toAbsolutePath().normalize();
            if(!dir.startsWith(ROOT)||!Files.isRegularFile(dir.resolve("astrograph-world.marker")))throw new AssertionError("Isolation marker");
            mc.options.pauseOnLostFocus=false;
            if(state==0){
                if(mc.getOverlay()!=null||mc.screen==null)return;
                if(mc.level!=null||Files.exists(dir.resolve("saves/astrograph-world-audit")))throw new AssertionError("Fresh audit world required");
                iris=Class.forName("net.irisshaders.iris.Iris");
                iris.getMethod("toggleShaders",Minecraft.class,boolean.class).invoke(null,mc,false);
                callback=GLDebugMessageCallback.create((source,type,id,severity,length,message,user)->{
                    if(type!=GL43.GL_DEBUG_TYPE_ERROR)return;
                    glErrors++;
                    if(errors.size()<12){
                        var error=new LinkedHashMap<String,Object>();error.put("scenario",scenario);error.put("id",id);
                        error.put("message",GLDebugMessageCallback.getMessage(length,message));
                        error.put("stack",Arrays.toString(Thread.currentThread().getStackTrace()));errors.add(error);
                        System.err.println("[ASTROGRAPH_WORLD_GL] "+error);new Exception("First synchronous GL origin").printStackTrace();
                    }
                });
                GL43.glDebugMessageCallback(callback,0);GL11.glEnable(GL43.GL_DEBUG_OUTPUT);GL11.glEnable(GL43.GL_DEBUG_OUTPUT_SYNCHRONOUS);
                state=1;
                mc.createWorldOpenFlows().createFreshLevel("astrograph-world-audit",new LevelSettings("Astrograph World Audit",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(48920261107L,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());return;
            }
            if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null||mc.getOverlay()!=null)return;
            if(state==1){
                if(++ticks<50)return;
                var def=(MultiblockMachineDefinition)GTRegistries.MACHINES.get(new ResourceLocation("gtladditions:arcanic_astrograph"));
                var block=def.defaultBlockState().setValue(((MetaMachineBlock)def.getBlock()).rotationState.property,Direction.SOUTH);
                entity=def.getBlockEntityType().create(POS,block);entity.setLevel(mc.level);
                machine=(WorkableMultiblockMachine)((IMachineBlockEntity)entity).getMetaMachine();
                set(MultiblockControllerMachine.class,machine,"isFormed",true);
                set(machine.getClass(),machine,"parallelAmount",Integer.MAX_VALUE);
                if(mc.getBlockEntityRenderDispatcher().getRenderer(entity)==null)throw new AssertionError("Registered actual BER missing");
                mc.setScreen(null);mc.options.hideGui=false;state=2;ticks=0;
                mc.getSingleplayerServer().execute(()->{
                    var world=mc.getSingleplayerServer().overworld();
                    for(int x=-40;x<=40;x++)for(int z=-40;z<=40;z++)
                        world.setBlock(new BlockPos(x,79,z),net.minecraft.world.level.block.Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(),3);
                });
            }
            if(state==2){
                if(reload!=null){if(!reload.isDone())return;reload.join();reload=null;}
                if(scenario<0||ticks>=70){
                    if(scenario>=0){
                        try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(dir.resolve("world-"+scenario+".png"));}
                        row.put("frames",frames);row.put("glErrors",glErrors);row.put("afterFrame",snapshot());
                        row.put("astrographMeshTimeAfterFrames",meshTime());
                    }
                    if(++scenario>=16){finish(null);return;}
                    int group=scenario/4,step=scenario%4;
                    if(step==0)iris.getMethod("toggleShaders",Minecraft.class,boolean.class).invoke(null,mc,group==1);
                    if(scenario==12)reload=mc.reloadResourcePacks();
                    enabled=step!=0;double distance=step==0?22:step==1?650:step==2?22:650;
                    double x=CENTER.x+distance*.6,z=CENTER.z+distance*.8,y=CENTER.y+8;
                    mc.player.setPos(x,y,z);mc.player.setYRot(143.13F);mc.player.setXRot(18);
                    mc.player.getAbilities().flying=true;mc.player.setDeltaMovement(Vec3.ZERO);
                    var uuid=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayer(uuid);p.getAbilities().flying=true;p.onUpdateAbilities();p.connection.teleport(x,y,z,143.13F,18);mc.getSingleplayerServer().overworld().setDayTime(6000);});
                    row=new LinkedHashMap<>();row.put("scenario",scenario);row.put("resourcesReloaded",group==3);row.put("shadersEnabled",group==1);row.put("effectEnabled",enabled);row.put("cameraDistance",distance);
                    var manager=iris.getMethod("getPipelineManager").invoke(null);var pipeline=manager.getClass().getMethod("getPipelineNullable").invoke(manager);
                    row.put("pipeline",pipeline==null?"null":pipeline.getClass().getName());rows.add(row);ticks=0;frames=0;
                }
                ticks++;
            }
        }catch(Throwable failure){finish(failure);}
    }
    private void render(RenderLevelStageEvent event){
        if(done||state!=2||entity==null||scenario<0||event.getStage()!=RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES)return;
        try{
            var mc=Minecraft.getInstance();var logic=machine.getRecipeLogic();
            set(RecipeLogic.class,logic,"isActive",enabled);set(RecipeLogic.class,logic,"status",enabled?RecipeLogic.Status.WORKING:RecipeLogic.Status.IDLE);
            if(event.getCamera().getPosition().distanceTo(CENTER)>512)return;
            var pose=event.getPoseStack();pose.pushPose();
            try{var camera=event.getCamera().getPosition();pose.translate(POS.getX()-camera.x,POS.getY()-camera.y,POS.getZ()-camera.z);
                if(frames==0)row.put("beforeBER",snapshot());
                mc.getBlockEntityRenderDispatcher().render(entity,event.getPartialTick(),pose,mc.renderBuffers().bufferSource());
                var queueOwner=Class.forName("com.gtladd.gtladditions.client.render.machine.deferred.DeferredMachineRenderer");
                var queueField=queueOwner.getDeclaredField("entries");queueField.setAccessible(true);
                var queue=queueField.get(null);
                var get=queue.getClass().getMethod("get",long.class);var entry=get.invoke(queue,POS.asLong());
                if(entry!=null)row.put("queuedDeferredEntry",entry.getClass().getName());
                if(frames==0)row.put("afterBER",snapshot());frames++;
            }finally{pose.popPose();}
        }catch(Throwable failure){finish(failure);}
    }
    private void finish(Throwable failure){
        if(done)return;done=true;var mc=Minecraft.getInstance();var report=new LinkedHashMap<String,Object>();
        report.put("status",failure==null?"PASS":"FAIL");report.put("worldOpened",true);report.put("playerWorldRead",false);report.put("scenarios",rows);report.put("glErrorCount",glErrors);report.put("firstGLErrors",errors);
        report.put("scope","Fresh flat integrated world, registered detached Astrograph BE through real dispatcher AFTER_BLOCK_ENTITIES; real HUD/crosshair; off/on/off and camera entry/exit. PASS denotes harness completion, not absence of rendering bugs.");
        if(failure!=null){report.put("error",failure.toString());failure.printStackTrace();}
        try{Files.writeString(mc.gameDirectory.toPath().resolve("astrograph-world.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception error){error.printStackTrace();}mc.stop();
    }
    private static void set(Class<?> owner,Object target,String name,Object value)throws Exception{var f=owner.getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
    private static Object meshTime()throws Exception{
        var renderer=Class.forName("com.gtl.enhancedcore.client.renderer.celestial.CelestialVfxRenderer");
        var field=renderer.getDeclaredField("ASTROGRAPH");field.setAccessible(true);var cache=field.get(null);
        var time=cache.getClass().getDeclaredField("time");time.setAccessible(true);float value=time.getFloat(cache);return Float.isFinite(value)?value:"not rendered yet";
    }
    private static Map<String,Object> snapshot(){
        var s=new LinkedHashMap<String,Object>();s.put("program",GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));s.put("drawFBO",GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING));s.put("readFBO",GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING));s.put("depth",GL11.glIsEnabled(GL11.GL_DEPTH_TEST));s.put("depthMask",GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK));s.put("blend",GL11.glIsEnabled(GL11.GL_BLEND));s.put("cull",GL11.glIsEnabled(GL11.GL_CULL_FACE));
        s.put("depthFunc",GL11.glGetInteger(GL11.GL_DEPTH_FUNC));s.put("stencil",GL11.glIsEnabled(GL11.GL_STENCIL_TEST));s.put("scissor",GL11.glIsEnabled(GL11.GL_SCISSOR_TEST));
        s.put("blendSrcRGB",GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB));s.put("blendDstRGB",GL11.glGetInteger(GL14.GL_BLEND_DST_RGB));s.put("blendSrcAlpha",GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA));s.put("blendDstAlpha",GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA));
        var shader=RenderSystem.getShader();s.put("shader",shader==null?"null":shader.getName()+"@"+shader.getId());return s;
    }
}
