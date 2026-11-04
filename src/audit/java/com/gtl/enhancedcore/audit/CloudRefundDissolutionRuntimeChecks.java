package com.gtl.enhancedcore.audit;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.*;
import com.gregtechceu.gtceu.api.machine.*;
import com.gregtechceu.gtceu.api.machine.multiblock.*;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gtl.enhancedcore.common.cloud.*;
import com.gtl.enhancedcore.common.cloud.store.*;
import com.gtl.enhancedcore.common.recipe.iv.*;
import com.lowdragmc.lowdraglib.gui.modular.ModularUIContainer;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.gtlcore.gtlcore.integration.ae2.pattern.PatternQuickUploadMetadata;

/** Task-marked fresh worlds only: native dissolver simulation and actual menu/ME cancellation. */
@Mod.EventBusSubscriber(modid="enhancedcore_audit")
public final class CloudRefundDissolutionRuntimeChecks {
    private static final Path ROOT=Path.of("C:/IDEA/GTL-Enhancedcore/_audit/cloud-refund-dissolution-20261029").toAbsolutePath().normalize();
    private static final BlockPos TANK=new BlockPos(640,100,640);
    private static final AEItemKey APPLE=AEItemKey.of(Items.APPLE), GOLD=AEItemKey.of(Items.GOLD_INGOT), EMERALD=AEItemKey.of(Items.EMERALD), STONE=AEItemKey.of(Items.STONE);
    private static AEFluidKey WATER, ACID;
    private static ServerLevel level;
    private static ServerPlayer player;
    private static CloudPatternLibraryMachine main;
    private static CloudPatternBufferMachine base, child;
    private static WorkableElectricMultiblockMachine machine;
    private static appeng.blockentity.networking.CreativeEnergyCellBlockEntity cell;
    private static DriveBlockEntity drive;
    private static BlockPos cellPos, drivePos;
    private static GTRecipe recipe;
    private static ItemStack currentOriginal;
    private static List<String> checks;
    private static int stage,ticks,at;
    private static long applesBefore;
    private static long goldBaseline,waterBaseline,acidBaseline,emeraldBaseline;
    private static UUID uploaded;
    private static net.minecraft.world.inventory.AbstractContainerMenu previous;
    private static CompoundTag restart;
    private static boolean reloading, reloadReconnected;
    private CloudRefundDissolutionRuntimeChecks() {}
    private static Path directory(MinecraftServer server) {
        var dir=server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getParent();
        if(!dir.startsWith(ROOT)||dir.equals(ROOT)||!Files.isRegularFile(dir.resolve("cloud-refund-dissolution.marker")))throw new IllegalStateException("Refuses unrelated/player world");
        return dir;
    }
    private static void check(boolean valid,String label) {if(!valid)throw new AssertionError("refund_dissolution:"+label+" stage="+stage);checks.add(label);}
    public static void begin(ServerLevel world,CloudPatternLibraryMachine core,CloudPatternBufferMachine buffer,ServerPlayer actor,List<String> results) throws Exception {
        directory(world.getServer());level=world;main=core;base=buffer;player=actor;checks=results;previous=player.containerMenu;ticks=0;stage=0;
        WATER=AEFluidKey.of(GTMaterials.Water.getFluid());ACID=AEFluidKey.of(GTMaterials.SulfuricAcid.getFluid());
        base.getGrid();
        for(int x=38;x<=42;x++)for(int z=38;z<=42;z++)level.setChunkForced(x,z,true);
        var definition=(MultiblockMachineDefinition)GTRegistries.MACHINES.get(new ResourceLocation("gtceu:dissolving_tank"));
        check(definition!=null,"real_dissolving_tank_registered");level.setBlock(TANK,definition.defaultBlockState(),3);
        machine=(WorkableElectricMultiblockMachine)MetaMachine.getMachine(level,TANK);machine.setFrontFacing(Direction.NORTH);
        player.setPos(TANK.getX(),100,TANK.getZ()-8);machine.getPattern().autoBuild(player,machine.getMultiblockState());
        check(machine.getPattern().checkPatternAt(machine.getMultiblockState(),false),"native_dissolver_production_geometry_autobuild_matches_"+machine.getMultiblockState().error);
        var cells=machine.getMultiblockState().getCache().stream().map(BlockPos::immutable).toList();
        var cloud=GTRegistries.MACHINES.get(new ResourceLocation("gtl_enhancedcore:cloud_pattern_buffer"));
        for(var pos:cells){var block=level.getBlockState(pos).getBlock();
            if(PartAbility.INPUT_ENERGY.isApplicable(block))level.setBlock(pos,GTRegistries.MACHINES.get(new ResourceLocation("gtceu:ev_energy_input_hatch")).defaultBlockState(),3);
            if(PartAbility.MAINTENANCE.isApplicable(block))level.setBlock(pos,GTRegistries.MACHINES.get(new ResourceLocation("gtceu:maintenance_hatch")).defaultBlockState(),3);
        }
        outer:for(var pos:cells){if(pos.equals(TANK))continue;var old=level.getBlockState(pos);
            for(var direction:Direction.values()){if(!level.isEmptyBlock(pos.relative(direction))||!level.isEmptyBlock(pos.relative(direction,2)))continue;
                level.setBlock(pos,cloud.defaultBlockState(),3);
                if(machine.getPattern().checkPatternAt(machine.getMultiblockState(),false)){
                    child=(CloudPatternBufferMachine)MetaMachine.getMachine(level,pos);child.setFrontFacing(direction);child.onMachinePlaced(player,ItemStack.EMPTY);cellPos=pos.relative(direction);drivePos=pos.relative(direction,2);break outer;
                }level.setBlock(pos,old,3);
            }
        }
        check(child!=null,"native_dissolver_accepts_actual_cloud_at_legal_port");
        level.setBlock(cellPos,AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(),3);cell=(appeng.blockentity.networking.CreativeEnergyCellBlockEntity)level.getBlockEntity(cellPos);
        level.setBlock(drivePos,AEBlocks.DRIVE.block().defaultBlockState(),3);drive=(DriveBlockEntity)level.getBlockEntity(drivePos);
        drive.getInternalInventory().setItemDirect(0,AEItems.ITEM_CELL_1K.stack());drive.getInternalInventory().setItemDirect(1,AEItems.FLUID_CELL_1K.stack());
        recipe=GTRecipeBuilder.of(new ResourceLocation("gtl_enhancedcore:audit_refund_dissolution"),definition.getRecipeTypes()[0])
            .inputFluids(GTMaterials.Water.getFluid(1000)).inputFluids(GTMaterials.SulfuricAcid.getFluid(2000))
            .outputItems(new ItemStack(Items.GOLD_INGOT)).duration(160).EUt(8).buildRawRecipe();
        var longRecipe=GTRecipeBuilder.of(new ResourceLocation("gtl_enhancedcore:audit_refund_dissolution_long"),definition.getRecipeTypes()[0])
            .inputFluids(GTMaterials.Water.getFluid(1000)).inputFluids(GTMaterials.SulfuricAcid.getFluid(2000)).outputItems(new ItemStack(Items.EMERALD)).duration(200000).EUt(8).buildRawRecipe();
        var all=new ArrayList<net.minecraft.world.item.crafting.Recipe<?>>(level.getRecipeManager().getRecipes());all.add(recipe);all.add(longRecipe);level.getRecipeManager().replaceRecipes(all);
    }
    private static void fixMaintenance(){for(var part:machine.getParts())if(part instanceof com.gregtechceu.gtceu.common.machine.multiblock.part.MaintenanceHatchPartMachine hatch)hatch.fixAllMaintenanceProblems();}
    private static void actualPlayerRecipe(){
        var real=level.getRecipeManager().getAllRecipesFor(machine.getDefinition().getRecipeTypes()[0]).stream().filter(candidate->candidate.id.equals(new ResourceLocation("gtceu:dissolution_treatment/rare_earth_hydroxides"))).findFirst().orElseThrow(()->new AssertionError("Actual player rare-earth recipe absent from real RecipeManager"));
        check(com.gregtechceu.gtceu.api.recipe.RecipeHelper.getInputEUt(real)==480,"actual_player_rare_earth_recipe_is_registered_at_480_EUt_"+real.id);
        var snapshot=com.gregtechceu.gtceu.api.recipe.GTRecipeSerializer.CODEC.encodeStart(NbtOps.INSTANCE,real).getOrThrow(false,ignored->{});
        var input=new LinkedHashMap<AEKey,Long>();
        input.put(AEItemKey.of(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new ResourceLocation("gtceu:rare_earth_dust"))),1L);
        input.put(AEItemKey.of(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new ResourceLocation("gtceu:sodium_hydroxide_dust"))),3L);
        var phosphorus=AEFluidKey.of(net.minecraftforge.registries.ForgeRegistries.FLUIDS.getValue(new ResourceLocation("gtceu:phosphoric_acid")));input.put(phosphorus,100L);input.put(WATER,900L);
        check(IvRecipeInputs.plan(real,input,Map.of(),1)!=null,"actual_player_recipe_accepts_exact_rare_earth1_sodium_hydroxide3_phosphoric100_water900");
        check(new IvNativeMatchContext(machine,input,Map.of(),1,1).prepare(real)!=null,"actual_player_recipe_correct_ratio_native_prepare_uses_job_stock_with_empty_physical_hatches");
        input.put(WATER,1000L);var unchanged=new LinkedHashMap<>(input);
        check(new IvNativeMatchContext(machine,input,Map.of(),1,1).prepare(real)==null&&input.equals(unchanged),"actual_player_recipe_wrong_phosphoric100_water1000_rejects_without_consumption");
        check(snapshot.equals(com.gregtechceu.gtceu.api.recipe.GTRecipeSerializer.CODEC.encodeStart(NbtOps.INSTANCE,real).getOrThrow(false,ignored->{})),"actual_player_registered_recipe_is_not_mutated_by_native_context_checks");
    }
    private static void upload(CloudPatternBufferMachine endpoint,ItemStack original){currentOriginal=original.copy();var op=UUID.randomUUID();var store=endpoint.cloudStore();var entry=store.upload(endpoint.cloudLibraryId(),player.getUUID(),original,new CompoundTag(),op);store.acknowledge(endpoint.cloudLibraryId(),player.getUUID(),op);uploaded=entry.id();}
    private static ItemStack pattern(CloudPatternBufferMachine endpoint,GenericStack[] input,AEKey output){var p=PatternDetailsHelper.encodeProcessingPattern(input,new GenericStack[]{new GenericStack(output,1)});PatternQuickUploadMetadata.writeRecipeTypeId(p,endpoint.cloudMode());return p;}
    private static long stock(CloudPatternBufferMachine endpoint,AEKey key){return endpoint.getGrid().getStorageService().getInventory().getAvailableStacks().get(key);}
    private static appeng.api.crafting.IPatternDetails selected(CloudPatternBufferMachine endpoint){return endpoint.getAvailablePatterns().stream().filter(details->{var tag=details.getDefinition().getTag();return tag!=null&&tag.getCompound(IvBuffers.ROUTE_KEY).getCompound("original").equals(currentOriginal.save(new CompoundTag()));}).findFirst().orElse(null);}
    private static void dispatchFluids(long acid){var inputs=new KeyCounter();var storage=child.getGrid().getStorageService().getInventory();var source=IActionSource.ofPlayer(player);
        for(var row:Map.of(WATER,1000L,ACID,acid).entrySet()){check(storage.insert(row.getKey(),row.getValue(),Actionable.MODULATE,source)==row.getValue()&&storage.extract(row.getKey(),row.getValue(),Actionable.MODULATE,source)==row.getValue(),"real_ME_fluid_dispatch_debits_"+row.getKey());inputs.add(row.getKey(),row.getValue());}
        check(child.pushPattern(selected(child),new KeyCounter[]{inputs}),"real_dissolver_cloud_route_accepts_ratio_job_"+acid);
    }
    private static void action(CloudPatternBufferMachine endpoint,String name){action(endpoint,name,true);}
    private static void action(CloudPatternBufferMachine endpoint,String name,boolean confirm){
        player.setPos(endpoint.getPos().getX()+.5,endpoint.getPos().getY()+.5,endpoint.getPos().getZ()+.5);
        var ui=endpoint.createUI(player);ui.initWidgets();player.containerMenu=new ModularUIContainer(ui,95);
        var view=endpoint.cloudUiSnapshot(player,"","",0,"");check(!view.getString("status").endsWith("denied"),"real_child_menu_authorized_for_"+name);
        var payload=new CompoundTag();payload.putLong("revision",view.getLong("revision"));if(name.equals("cancel"))payload.putBoolean("confirm",confirm);endpoint.cloudUiAction(player,name,payload);
    }
    private static void dispatchApples(){
        var storage=base.getGrid().getStorageService().getInventory();var source=IActionSource.ofPlayer(player);applesBefore=stock(base,APPLE);
        check(storage.insert(APPLE,7,Actionable.MODULATE,source)==7&&storage.extract(APPLE,7,Actionable.MODULATE,source)==7,"real_ME_funds_seven_apple_dispatch");
        var inputs=new KeyCounter();inputs.add(APPLE,7);check(base.pushPattern(base.getAvailablePatterns().getFirst(),new KeyCounter[]{inputs}),"real_published_cloud_route_accepts_seven_operation_job");
        check(base.iv$getState().jobs.size()==1&&base.iv$getState().jobs.getFirst().inventory.get(APPLE)==7,"accepted_job_owns_exact_seven_unpaid_apples");
    }
    public static boolean step() throws Exception {
        if(++ticks>1800)throw new AssertionError("refund_dissolution fixture timeout stage="+stage);
        if(machine.isFormed()&&machine.getEnergyContainer()!=null)machine.getEnergyContainer().addEnergy(machine.getEnergyContainer().getEnergyCapacity());
        if(stage==0){
            if(cell.getMainNode().getNode()==null)return false;
            if(cell.getMainNode().getGrid()!=main.serviceGrid())appeng.api.networking.GridHelper.createConnection(main.serviceGrid().getPivot(),cell.getMainNode().getNode());
            if(!machine.isFormed()||!child.cloudReady(true))return false;fixMaintenance();
            check(IvBuffers.controller(child)==machine&&IvBuffers.genericCloudTarget(machine),"real_formed_native_dissolver_is_actual_generic_cloud_executor");
            check(machine.getParts().stream().filter(part->part instanceof com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine)
                .map(part->(com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine)part).allMatch(hatch->hatch.tank.getFluidInTank(0).isEmpty()),"real_dissolver_physical_fluid_hatches_are_empty");
            actualPlayerRecipe();
            var good=new LinkedHashMap<AEKey,Long>();good.put(WATER,1000L);good.put(ACID,2000L);
            var goodBefore=new LinkedHashMap<>(good);check(new IvNativeMatchContext(machine,good,Map.of(),1,1).prepare(recipe)!=null,"native_modifier_sees_correct_ratio_only_in_job_context");
            check(good.equals(goodBefore),"native_ratio_simulation_does_not_consume_job_stock");
            var bad=new LinkedHashMap<AEKey,Long>();bad.put(WATER,1000L);bad.put(ACID,3000L);var badBefore=new LinkedHashMap<>(bad);
            check(new IvNativeMatchContext(machine,bad,Map.of(),1,1).prepare(recipe)==null&&bad.equals(badBefore),"wrong_ratio_rejected_without_consuming_job_stock");
            check(IvNativeMatchContext.forMachine(machine)==null,"native_simulation_context_unwinds");
            goldBaseline=stock(child,GOLD);emeraldBaseline=stock(child,EMERALD);
            upload(child,pattern(child,new GenericStack[]{new GenericStack(WATER,1000),new GenericStack(ACID,2000)},GOLD));stage=1;return false;
        }
        if(stage==1){if(selected(child)==null)return false;dispatchFluids(2000);stage=2;return false;
        }
        if(stage==2){var job=child.iv$getState().jobs.getFirst();if(!job.active())return false;
            check(job.nativeRecipe!=null&&job.inventory.getOrDefault(WATER,0L)==0&&job.inventory.getOrDefault(ACID,0L)==0,"actual_native_batch_starts_and_consumes_job_fluids_once");
            check(!job.completion.isEmpty()&&stock(child,GOLD)==goldBaseline,"correct_ratio_commits_expected_unfinished_output_not_delivered_early");stage=20;return false;
        }
        if(stage==20){if(!child.iv$getState().jobs.isEmpty())return false;
            check(stock(child,GOLD)==goldBaseline+1,"correct_ratio_real_native_engine_completes_and_delivers_one_gold");goldBaseline++;
            upload(child,pattern(child,new GenericStack[]{new GenericStack(WATER,1000),new GenericStack(ACID,3000)},GOLD));stage=21;return false;
        }
        if(stage==21){if(selected(child)==null)return false;waterBaseline=stock(child,WATER);acidBaseline=stock(child,ACID);dispatchFluids(3000);at=ticks;stage=22;return false;}
        if(stage==22){if(ticks-at<10)return false;var job=child.iv$getState().jobs.getFirst();
            check(!job.active()&&job.nativeRecipe==null&&job.completion.isEmpty()&&job.inventory.equals(Map.of(WATER,1000L,ACID,3000L)),"wrong_ratio_real_accepted_job_waits_without_consuming_or_committing_batch");
            check(((IvNativeAccess)machine.getRecipeLogic()).iv$summary().toString().contains("gtceu.recipe.fail.no.ratio"),"wrong_ratio_live_engine_displays_original_native_ratio_reason");
            check(stock(child,GOLD)==goldBaseline,"wrong_ratio_live_order_has_not_delivered_output");action(child,"cancel");
            check(child.iv$getState().jobs.isEmpty()&&stock(child,WATER)==waterBaseline+1000&&stock(child,ACID)==acidBaseline+3000,"wrong_ratio_menu_cancel_returns_exact_real_ME_fluids");
            action(child,"resume");upload(child,pattern(child,new GenericStack[]{new GenericStack(WATER,1000),new GenericStack(ACID,2000)},EMERALD));stage=23;return false;
        }
        if(stage==23){if(selected(child)==null)return false;waterBaseline=stock(child,WATER);acidBaseline=stock(child,ACID);dispatchFluids(2000);stage=24;return false;}
        if(stage==24){var job=child.iv$getState().jobs.getFirst();if(!job.active())return false;
            check(job.nativeRecipe!=null&&job.inventory.isEmpty()&&!job.completion.isEmpty(),"long_correct_ratio_real_order_commits_paid_batch");
            action(child,"cancel");check(child.iv$getState().jobs.isEmpty(),"menu_cancel_stops_paid_inflight_dissolver_without_refunding_consumed_fluids");
            check(stock(child,WATER)==waterBaseline&&stock(child,ACID)==acidBaseline&&stock(child,EMERALD)==emeraldBaseline&&stock(child,GOLD)==goldBaseline,"cancel_does_not_invent_consumed_fluids_or_unfinished_output");
            IvBuffers.controller(base).getRecipeLogic().setWorkingEnabled(false);
            upload(base,pattern(base,new GenericStack[]{new GenericStack(APPLE,1)},GOLD));stage=3;return false;
        }
        if(stage==3){if(base.getAvailablePatterns().isEmpty())return false;dispatchApples();action(base,"cancel",false);
            check(base.iv$getState().jobs.size()==1&&!base.iv$getState().jobs.getFirst().cancelled&&base.iv$getState().jobs.getFirst().inventory.get(APPLE)==7,"unconfirmed_menu_action_cannot_cancel_or_refund");action(base,"cancel");
            check(base.iv$getState().jobs.isEmpty()&&stock(base,APPLE)==applesBefore+7,"actual_menu_cancel_returns_exact_unconsumed_stock_to_real_ME");
            action(base,"cancel");check(stock(base,APPLE)==applesBefore+7,"repeat_real_menu_cancel_never_duplicates_refund");action(base,"resume");stage=4;return false;
        }
        if(stage==4){if(base.getAvailablePatterns().isEmpty())return false;dispatchApples();
            var storage=base.getGrid().getStorageService().getInventory();storage.insert(STONE,Long.MAX_VALUE,Actionable.MODULATE,IActionSource.ofPlayer(player));
            check(storage.insert(APPLE,1,Actionable.SIMULATE,IActionSource.ofPlayer(player))==0,"actual_item_cells_are_full_and_reject_refunds");
            action(base,"cancel");check(base.iv$getState().jobs.size()==1&&base.iv$getState().jobs.getFirst().refunds.get(APPLE)==7,"full_actual_ME_keeps_all_seven_refund_apples");at=ticks;stage=5;return false;
        }
        if(stage==5){if(ticks-at<10)return false;check(base.iv$getState().jobs.getFirst().refunds.get(APPLE)==7,"full_disk_retries_preserve_refund_balance");
            base.getGrid().getStorageService().getInventory().extract(STONE,Long.MAX_VALUE,Actionable.MODULATE,IActionSource.ofPlayer(player));stage=6;return false;
        }
        if(stage==6){if(!base.iv$getState().jobs.isEmpty())return false;check(stock(base,APPLE)==applesBefore+7,"freeing_real_disk_capacity_drains_refund_once");action(base,"resume");stage=7;return false;}
        if(stage==7){if(base.getAvailablePatterns().isEmpty())return false;dispatchApples();
            var power=base.getPos().relative(base.getFrontFacing());
            check(level.getBlockEntity(power) instanceof appeng.blockentity.networking.CreativeEnergyCellBlockEntity,"base_real_physical_AE_cell_identified");
            level.removeBlock(power,false);at=ticks;stage=8;return false;
        }
        if(stage==8){if(ticks-at<5)return false;action(base,"cancel");
            check(base.iv$getState().jobs.size()==1&&base.iv$getState().jobs.getFirst().refunds.get(APPLE)==7,"offline_real_endpoint_retains_cancelled_refunds");
            var ref=new CompoundTag();ref.putUUID("node",base.cloudNodeId());ref.putUUID("library",base.cloudLibraryId());ref.putUUID("actor",player.getUUID());ref.putLong("base",base.getPos().asLong());ref.putLong("power",base.getPos().relative(base.getFrontFacing()).asLong());ref.putLong("main",main.getPos().asLong());ref.putLong("tankCell",cellPos.asLong());ref.putLong("tankDrive",drivePos.asLong());ref.putLong("expectedRefund",7);ref.putLong("expectedStored",applesBefore);ref.putLong("expectedGold",goldBaseline);ref.putLong("expectedEmerald",emeraldBaseline);ref.put("ledger",base.iv$getState().save());
            base.markDirty();base.cloudStore().checkpointLibrary(base.cloudLibraryId());NbtIo.writeCompressed(ref,directory(level.getServer()).resolve("refund-restart.dat").toFile());
            check(true,"offline_cancel_ledger_durably_checkpointed_for_independent_JVM");return true;
        }
        return false;
    }
    public static void close(){if(player!=null&&previous!=null)player.containerMenu=previous;}
    public static void reload(MinecraftServer server) throws Exception {
        directory(server);level=server.overworld();checks=new ArrayList<>();restart=NbtIo.readCompressed(directory(server).resolve("refund-restart.dat").toFile());
        var node=CloudPatternStore.get(level).node(restart.getUUID("node"));var state=new IvBufferState();state.load(node.ivState());
        check(node.healthy()&&state.healthy()&&state.jobs.size()==1,"independent_JVM_restores_healthy_cancelled_order");
        var job=state.jobs.getFirst();check(job.cancelled&&job.refunds.get(APPLE)==restart.getLong("expectedRefund")&&job.inventory.isEmpty()&&job.completion.isEmpty(),"independent_JVM_preserves_exact_refunds_without_resurrecting_work");
        check(state.save().equals(restart.getCompound("ledger")),"independent_JVM_preserves_exact_cancelled_ledger");
        for(String key:List.of("base","power","main","tankCell","tankDrive")){var pos=BlockPos.of(restart.getLong(key));level.getChunk(pos);level.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true);}
        player=net.minecraftforge.common.util.FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(restart.getUUID("actor"),"CloudRefundReload"));player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        base=(CloudPatternBufferMachine)MetaMachine.getMachine(level,BlockPos.of(restart.getLong("base")));
        main=(CloudPatternLibraryMachine)MetaMachine.getMachine(level,BlockPos.of(restart.getLong("main")));
        check(base!=null&&main!=null,"independent_JVM_loads_actual_saved_cloud_machines");
        var power=BlockPos.of(restart.getLong("power"));check(level.isEmptyBlock(power),"independent_JVM_retains_actual_disconnected_cell_position");
        level.setBlock(power,AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(),3);cell=(appeng.blockentity.networking.CreativeEnergyCellBlockEntity)level.getBlockEntity(power);
        ticks=0;at=0;reloadReconnected=false;reloading=true;
    }
    @SubscribeEvent public static void reloadTick(TickEvent.ServerTickEvent event) throws Exception {
        if(!reloading||event.phase!=TickEvent.Phase.END)return;
        var server=event.getServer();
        try {
            if(++ticks>400)throw new AssertionError("Independent JVM refund reconnection timeout");
            var node=cell.getMainNode().getNode();var mainNode=main.getMainNode().getNode();
            var tankCell=(appeng.blockentity.networking.CreativeEnergyCellBlockEntity)level.getBlockEntity(BlockPos.of(restart.getLong("tankCell")));
            if(node==null||mainNode==null||tankCell==null||tankCell.getMainNode().getNode()==null)return;
            if(node.getGrid()!=mainNode.getGrid())appeng.api.networking.GridHelper.createConnection(mainNode,node);
            if(tankCell.getMainNode().getNode().getGrid()!=mainNode.getGrid())appeng.api.networking.GridHelper.createConnection(mainNode,tankCell.getMainNode().getNode());
            if(!base.getMainNode().isActive())return;
            if(!reloadReconnected){action(base,"reconnect");reloadReconnected=true;}
            if(!base.iv$getState().jobs.isEmpty())return;
            check(stock(base,APPLE)==restart.getLong("expectedStored")+restart.getLong("expectedRefund"),"independent_JVM_reconnected_real_ME_receives_exact_remaining_refund");
            if(at==0){at=ticks;action(base,"cancel");return;}
            if(ticks-at<10)return;
            check(stock(base,APPLE)==restart.getLong("expectedStored")+restart.getLong("expectedRefund"),"independent_JVM_repeated_cancel_and_normal_retries_do_not_duplicate_return");
            check(stock(base,GOLD)==restart.getLong("expectedGold")&&stock(base,EMERALD)==restart.getLong("expectedEmerald"),"independent_JVM_cancelled_unfinished_batch_does_not_resurrect_output");
            reloading=false;writeReload(server,null);server.halt(false);
        }catch(Throwable failure){failure.printStackTrace();reloading=false;writeReload(server,failure);server.halt(false);}
    }
    private static void writeReload(MinecraftServer server,Throwable failure) throws Exception {var result=new LinkedHashMap<String,Object>();result.put("status",failure==null?"PASS":"FAIL");result.put("checks",checks);if(failure!=null)result.put("error",failure.toString());Files.writeString(directory(server).resolve("refund-dissolution-reload.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(result));}
}
