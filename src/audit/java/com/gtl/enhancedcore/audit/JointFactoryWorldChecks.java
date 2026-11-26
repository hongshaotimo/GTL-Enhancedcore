package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.common.machine.multiblock.part.EnergyHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import com.gtl.enhancedcore.common.machine.IntegratedUniversalFactoryMachine;
import com.gtl.enhancedcore.common.recipe.ThreadLimitedRecipeLogic;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Opt-in real WorldEdit clipboard / Forge multiblock test. Never opens a pre-existing world. */
@Mod("enhancedcore_audit")
public final class JointFactoryWorldChecks {
    private static final String PLAN = "0225_universal_joint_factory_mv_r2";
    private static final Path ROOT = Path.of("C:/IDEA/GTL-Enhancedcore/_work/joint-factory-mv-20261125").toAbsolutePath().normalize();
    private final List<String> checks = Collections.synchronizedList(new ArrayList<>());
    private int stage, base, age, camera, waitFrames, settle, photoAge;
    private volatile boolean inWorld, positioned, ready;
    private volatile String failure;
    private boolean armed, finished;
    private final Map<BlockPos,BlockState> blanks = new LinkedHashMap<>();
    private final double[][] view = {{42,24,-27,17,5,15},{10,1,-18,17,5.5,14},{43,10,45,19,6,20},{12,1,-.8,16.3,2.35,5.7}};
    private final String[] files={"overview","north-ground","service-side","console"};
    private IntegratedUniversalFactoryMachine machine;
    private int serverStep; private long energyBefore;
    private EnergyHatchPartMachine energy; private ItemBusPartMachine intake,output;

    public JointFactoryWorldChecks() {
        if (!Boolean.getBoolean("gtl.enhancedcore.jointFactoryAudit")) return;
        MinecraftForge.EVENT_BUS.addListener(this::client);
        MinecraftForge.EVENT_BUS.addListener(this::serverTick);
    }
    private BlockPos at(int x,int y,int z) {return new BlockPos(x,base+y,z);}
    private void check(boolean yes,String message) {if(!yes) throw new AssertionError(message); checks.add(message);}
    private boolean match(ServerLevel level) { return machine.getPattern().checkPatternAt(new MultiblockState(level,machine.getPos()),true); }
    private BlockState state(String id) { return Objects.requireNonNull(ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id))).defaultBlockState(); }
    private void bad(ServerLevel w,BlockPos pos,String block) {
        BlockState original=w.getBlockState(pos); w.setBlockAndUpdate(pos,state(block));
        check(!match(w),"Rejects " + block + " at " + pos.toShortString()); w.setBlockAndUpdate(pos,original);
    }
    // WorldEdit is present in the instance but is intentionally not a production compile dependency.
    private static Class<?> we(String path) throws Exception {return Class.forName("com.sk89q.worldedit."+path);}
    private void pasteFile(ServerLevel level,Path file) throws Exception {
        var format=we("extent.clipboard.io.ClipboardFormats").getMethod("findByFile",java.io.File.class).invoke(null,file.toFile());
        check(format!=null,"The installed WorldEdit recognizes the Sponge schematic");
        try(var in=Files.newInputStream(file);var reader=(AutoCloseable)we("extent.clipboard.io.ClipboardFormat").getMethod("getReader",java.io.InputStream.class).invoke(format,in)){
            var clip=we("extent.clipboard.io.ClipboardReader").getMethod("read").invoke(reader);
            var clipClass=we("extent.clipboard.Clipboard");var v=we("math.BlockVector3");var make=v.getMethod("at",int.class,int.class,int.class);
            check(make.invoke(null,16,1,-2).equals(clipClass.getMethod("getOrigin").invoke(clip)),"Clipboard origin is the north entry, one block above floor base");
            var holder=we("session.ClipboardHolder").getConstructor(clipClass).newInstance(clip);
            var world=we("forge.ForgeAdapter").getMethod("adapt",ServerLevel.class).invoke(null,level);
            var api=we("WorldEdit").getMethod("getInstance").invoke(null);
            try(var session=(AutoCloseable)we("WorldEdit").getMethod("newEditSession",we("world.World")).invoke(api,world)){
                var builder=we("session.ClipboardHolder").getMethod("createPaste",we("extent.Extent")).invoke(holder,session);
                we("session.PasteBuilder").getMethod("to",v).invoke(builder,make.invoke(null,16,base+1,-2));
                var op=we("session.PasteBuilder").getMethod("build").invoke(builder);
                we("function.operation.Operations").getMethod("complete",we("function.operation.Operation")).invoke(null,op);
            }
        }
    }
    private void prepare(ServerLevel w,ServerPlayer player) throws Exception {
        w.getServer().getPlayerList().op(player.getGameProfile());
        for(int x=-2;x<=3;x++)for(int z=-2;z<=3;z++) w.setChunkForced(x,z,true);
        base=w.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,16,5);
        w.setDayTime(5500); w.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,w.getServer());
        w.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,w.getServer());
        w.setWeatherParameters(6000,0,false,false);
        // Paste point = console centreline just outside the north walk. Positive y offset is one floor tile.
        player.teleportTo(16.5,base+1,-1.5); player.setGameMode(GameType.SPECTATOR);
        pasteFile(w,Minecraft.getInstance().gameDirectory.toPath().resolve("config/worldedit/schematics/"+PLAN+".schem"));
        check(MetaMachine.getMachine(w,at(16,2,5)) instanceof IntegratedUniversalFactoryMachine,"Actual WorldEdit paste placed the controller at its designed anchor");
    }
    private void serverTick(TickEvent.ServerTickEvent tick) {
        if(tick.phase!=TickEvent.Phase.END||!inWorld||failure!=null||ready) return;
        try {
            var server=tick.getServer(); var w=server.overworld(); var player=server.getPlayerList().getPlayers().get(0);
            if(++age>1300) throw new AssertionError("Timed out in server phase "+serverStep+" base="+base+" at console="+w.getBlockState(at(16,2,5))+" checks="+checks);
            if(serverStep==0){ prepare(w,player); serverStep=1; return; }
            if(serverStep==1){
                if(!(MetaMachine.getMachine(w,at(16,2,5)) instanceof IntegratedUniversalFactoryMachine c)) return;
                machine=c; if(!match(w)) throw new AssertionError("Pasted file rejected: "+diagnosis(w));
                if(!machine.isFormed()) return;
                check(machine.getParts().size()==6 && lanes()==1,"WorldEdit paste formed with six parts and one production lane");
                bad(w,at(5,2,7),"gtceu:lv_output_bus"); bad(w,at(32,2,12),"gtceu:lv_output_hatch");
                bad(w,at(2,2,25),"gtceu:lv_input_bus"); bad(w,at(29,2,28),"gtceu:lv_input_hatch");
                bad(w,at(7,2,7),"gtladditions:me_super_pattern_buffer"); bad(w,at(25,2,6),"gtceu:hv_energy_input_hatch");
                bad(w,at(16,2,4),"minecraft:stone");
                var spare=Map.of(at(7,2,7),"gtceu:mv_input_bus",at(32,2,15),"gtceu:lv_input_hatch",at(32,2,17),"gtceu:mv_input_hatch",at(31,2,28),"gtceu:lv_output_hatch");
                spare.forEach((p,id)->{blanks.put(p,w.getBlockState(p)); w.setBlockAndUpdate(p,state(id));});
                check(match(w),"Matching LV/MV expansions pass the full pattern"); serverStep=2; return;
            }
            if(serverStep==2){if(!machine.isFormed()||lanes()!=10) return;
                check(machine.getParts().size()==10,"Fully equipped workshop exposes ten real parts and ten lanes");
                blanks.forEach(w::setBlockAndUpdate); serverStep=3; return;}
            if(serverStep==3){if(!machine.isFormed()||lanes()!=1||machine.getParts().size()!=6) return;
                intake=(ItemBusPartMachine)MetaMachine.getMachine(w,at(5,2,7)); output=(ItemBusPartMachine)MetaMachine.getMachine(w,at(2,2,25));
                energy=(EnergyHatchPartMachine)MetaMachine.getMachine(w,at(25,2,6));
                var recipe=GTRecipeBuilder.of(new ResourceLocation("gtl_enhancedcore","joint_workshop_io_check"),machine.getRecipeType())
                    .inputItems(Items.APPLE).outputItems(Items.GOLD_NUGGET).duration(20).EUt(8).buildRawRecipe();
                var recipes=new ArrayList<>(w.getRecipeManager().getRecipes()); recipes.add(recipe); w.getRecipeManager().replaceRecipes(recipes);
                check(machine.getRecipeType().getLookup().addRecipe(recipe),"Registered one disposable test recipe in the selected family");
                energyBefore=energy.energyContainer.getEnergyCapacity(); energy.energyContainer.setEnergyStored(energyBefore);
                intake.getInventory().setStackInSlot(0,new ItemStack(Items.APPLE)); machine.setWorkingEnabled(true); machine.getRecipeLogic().markLastRecipeDirty();
                serverStep=4;return;}
            if(serverStep==4){if(!output.getInventory().getStackInSlot(0).is(Items.GOLD_NUGGET)) return;
                check(output.getInventory().getStackInSlot(0).getCount()==1 && intake.getInventory().getStackInSlot(0).isEmpty() && energy.energyContainer.getEnergyStored()<energyBefore,"One test input arrived through gantry, consumed breaker EU and yielded one output at drum");
                ready=true;}
        }catch(Throwable e){e.printStackTrace(); failure=e.toString();}
    }
    private String diagnosis(ServerLevel w){var s=new MultiblockState(w,machine.getPos());machine.getPattern().checkPatternAt(s,true); return s.getPos()+" actual="+s.getBlockState()+" err="+s.error;}
    private int lanes(){return ((ThreadLimitedRecipeLogic)machine.getRecipeLogic()).getMultipleThreads();}
    private void client(TickEvent.ClientTickEvent tick){
        if(tick.phase!=TickEvent.Phase.END||finished) return;
        var mc=Minecraft.getInstance(); Path dir=mc.gameDirectory.toPath().toAbsolutePath().normalize();
        try {
            if(failure!=null){finish(mc,dir);return;}
            if(stage==0){if(mc.getOverlay()!=null||mc.screen==null)return;
                if(!dir.startsWith(ROOT)||dir.equals(ROOT)||!Files.isRegularFile(dir.resolve("joint.marker"))||Files.exists(dir.resolve("saves/workshop"))||mc.level!=null) throw new AssertionError("Fresh isolated run required");
                stage=1; mc.options.pauseOnLostFocus=false;
                mc.createWorldOpenFlows().createFreshLevel("workshop",new LevelSettings("0225 Workshop",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(225L,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
            }else if(stage==1){if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return; mc.setScreen(null); mc.options.hideGui=true; stage=2; inWorld=true;}
            else if(stage==2 && ready){
                if(camera==view.length){inWorld=false; mc.level.disconnect();mc.clearLevel();mc.setScreen(new TitleScreen());stage=3;return;}
                if(!armed){var v=view[camera]; armed=true; positioned=false;waitFrames=0;settle=0;photoAge=0;
                    var server=mc.getSingleplayerServer();var id=mc.player.getUUID();server.execute(()->{var p=server.getPlayerList().getPlayer(id);
                        double dx=v[3]-v[0],dz=v[5]-v[2],dy=v[4]-v[1]-p.getEyeHeight();
                        p.teleportTo(server.overworld(),v[0],base+v[1],v[2],(float)-Math.toDegrees(Math.atan2(dx,dz)),(float)-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz)))); positioned=true;}); return;}
                if(!positioned||++waitFrames<45)return;
                if(mc.screen!=null) mc.setScreen(null);
                if(++photoAge<160 && !mc.levelRenderer.hasRenderedAllChunks()){settle=0;return;}
                if(++settle<8)return;
                try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(dir.resolve(files[camera]+".png"));}
                checks.add("In-game view: "+files[camera]); camera++;armed=false;
            }else if(stage==3 && mc.getSingleplayerServer()==null)finish(mc,dir);
        }catch(Throwable e){e.printStackTrace();failure=e.toString();finish(mc,dir);}
    }
    private void finish(Minecraft mc,Path dir){finished=true;
        try{Files.writeString(dir.resolve("joint-world.json"),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("status",failure==null?"PASS":"FAIL","error",failure==null?"":failure,"baseY",base,"checks",List.copyOf(checks))));}catch(Exception e){e.printStackTrace();}mc.stop();}
}
