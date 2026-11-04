package com.gtl.enhancedcore.audit;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.google.gson.GsonBuilder;
import com.gtl.enhancedcore.common.cloud.*;
import com.gtl.enhancedcore.common.cloud.store.CloudLedger;
import com.gtl.enhancedcore.common.cloud.store.CloudPatternStore;
import com.gtl.enhancedcore.common.cloud.store.CloudMachineIdentity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gtl.enhancedcore.common.recipe.iv.IvBuffers;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.nbt.*;
import net.minecraft.core.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.storage.LevelResource;
import org.gtlcore.gtlcore.integration.ae2.pattern.PatternQuickUploadMetadata;

/** Upload click through real AE craftability; 4096 unrelated originals, real production geometry and disk. */
public final class CloudUploadLatencyRuntimeChecks {
    private static final Path ROOT = Path.of("C:/IDEA/GTL-Enhancedcore/_audit/cloud-upload-latency-20261029").toAbsolutePath().normalize();
    private static ServerLevel level;
    private static CloudPatternLibraryMachine main;
    private static CloudPatternBufferMachine child;
    private static ServerPlayer player;
    private static CloudPatternStore.Library library;
    private static List<String> checks;
    private static final Map<String,Object> metrics = new LinkedHashMap<>();
    private static CompletableFuture<Void> saved;
    private static UUID entryId;
    private static ItemStack expected;
    private static int stage, ticks, background, clickedAt, quiet;
    private static long clickedNanos;
    private static final List<CloudPatternBufferMachine> endpoints = new ArrayList<>();
    private static final List<WorkableElectricMultiblockMachine> controllers = new ArrayList<>();
    private static final List<appeng.blockentity.networking.CreativeEnergyCellBlockEntity> cells = new ArrayList<>();
    private static final Map<BlockPos,net.minecraft.world.level.block.state.BlockState> template = new LinkedHashMap<>();
    private static BlockPos templateCore, childOffset;
    private static int requestedEndpoints;
    private static int observedIncrementalScans, maxPriorityEntries;
    private static boolean observedFullScan, observedUnexpectedPriority;
    private static net.minecraft.world.inventory.AbstractContainerMenu previousMenu;
    private CloudUploadLatencyRuntimeChecks() {}
    private static Path directory(MinecraftServer server) {
        var dir = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getParent();
        if (!dir.startsWith(ROOT) || dir.equals(ROOT) || !Files.isRegularFile(dir.resolve("cloud-upload-latency.marker")))
            throw new IllegalStateException("Latency audit refuses unmarked/player world");
        return dir;
    }
    private static void check(boolean valid, String label) {
        if (!valid) throw new AssertionError("upload_latency:" + label + " metrics=" + metrics);
        checks.add("upload_latency:" + label);
    }
    public static void begin(ServerLevel world, CloudPatternLibraryMachine core, CloudPatternBufferMachine buffer,
                             ServerPlayer actor, List<String> results) {
        directory(world.getServer()); level=world; main=core; child=buffer; player=actor; checks=results;
        library=child.cloudStore().library(main.libraryId()); previousMenu=player.containerMenu;
        metrics.clear(); ticks=0; background=0; quiet=0; stage=0;observedIncrementalScans=0;maxPriorityEntries=0;observedFullScan=false;observedUnexpectedPriority=false;
        check(main.isFormed() && child.cloudReady(true), "real_formed_controller_core_and_cloud_child_ready");
        requestedEndpoints=Integer.getInteger("gtl.cloudUploadLatencyEndpoints",1);
        if(requestedEndpoints!=1&&requestedEndpoints!=48)throw new IllegalArgumentException("Only 1 or 48 real endpoints supported");
        endpoints.clear();controllers.clear();cells.clear();template.clear();endpoints.add(child);
        var original=IvBuffers.controller(child);controllers.add(original);templateCore=original.getPos();childOffset=child.getPos().subtract(templateCore);
        for(var pos:original.getMultiblockState().getCache())template.put(pos.subtract(templateCore),level.getBlockState(pos));
        template.put(BlockPos.ZERO,original.getBlockState());
        if(requestedEndpoints>1)stage=-1;
    }
    private static Object field(Object object,String name) throws Exception {
        Class<?> type=object.getClass();while(type!=null){try{var f=type.getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(NoSuchFieldException missing){type=type.getSuperclass();}}
        throw new NoSuchFieldException(name);
    }
    private static boolean baselineReady() throws Exception {
        for(var endpoint:endpoints)if(!endpoint.cloudReady(true)||CloudPatternAllocator.isScanning(endpoint)
                ||(long)field(endpoint,"lastLibraryRevision")!=library.allocationRevision())return false;
        return true;
    }
    private static void observeScans() throws Exception {
        var queuesField=CloudPatternAllocator.class.getDeclaredField("QUEUES");queuesField.setAccessible(true);
        var queue=((Map<?,?>)queuesField.get(null)).get(level.getServer());if(queue==null)return;
        for(var scan:((Map<?,?>)field(queue,"scans")).values())try{
            boolean incremental=(boolean)field(scan,"incremental");
            if(incremental)observedIncrementalScans++;else observedFullScan=true;
            var priority=(List<?>)field(scan,"priorityEntries");maxPriorityEntries=Math.max(maxPriorityEntries,priority.size());
            if(priority.stream().anyMatch(id->!id.equals(entryId)))observedUnexpectedPriority=true;
        }catch(NoSuchFieldException baseline){metrics.put("incrementalDiagnostics","unsupported by baseline core");return;}
        metrics.put("observedIncrementalScans",observedIncrementalScans);metrics.put("maxPriorityEntries",maxPriorityEntries);
        metrics.put("observedFullScan",observedFullScan);metrics.put("observedUnexpectedPriority",observedUnexpectedPriority);
    }
    private static void buildEndpoint() {
        int index=endpoints.size()-1;var origin=new BlockPos(256+(index%8)*8,100,320+(index/8)*8);
        for(var offset:template.keySet()) {var pos=origin.offset(offset);level.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true);level.getChunk(pos);}
        for(var row:template.entrySet()) {var pos=origin.offset(row.getKey());if(!level.isEmptyBlock(pos))throw new AssertionError("Clone overlaps occupied fixture block: "+pos);level.setBlock(pos,row.getValue(),3);}
        var machine=(WorkableElectricMultiblockMachine)MetaMachine.getMachine(level,origin);machine.setFrontFacing(Direction.NORTH);
        var buffer=(CloudPatternBufferMachine)MetaMachine.getMachine(level,origin.offset(childOffset));buffer.setFrontFacing(Direction.NORTH);buffer.onMachinePlaced(player,ItemStack.EMPTY);
        for(var offset:template.keySet())if(MetaMachine.getMachine(level,origin.offset(offset)) instanceof com.gregtechceu.gtceu.common.machine.multiblock.part.MaintenanceHatchPartMachine maintenance)maintenance.setMaintenanceProblems((byte)0);
        var cellPos=buffer.getPos().north();if(!level.isEmptyBlock(cellPos))throw new AssertionError("Clone cell overlaps geometry");
        level.setBlock(cellPos,appeng.core.definitions.AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(),3);
        cells.add((appeng.blockentity.networking.CreativeEnergyCellBlockEntity)level.getBlockEntity(cellPos));
        controllers.add(machine);endpoints.add(buffer);
    }
    private static ItemStack pattern(boolean target, int index) {
        var result=PatternDetailsHelper.encodeProcessingPattern(new GenericStack[]{new GenericStack(AEItemKey.of(Items.APPLE),1)},
                new GenericStack[]{new GenericStack(AEItemKey.of(target?Items.GOLD_INGOT:Items.IRON_NUGGET),1)});
        result.getOrCreateTag().putString("uploadLatencyAudit", target?"target":"background-"+index);
        PatternQuickUploadMetadata.writeRecipeTypeId(result, target?child.cloudMode():new net.minecraft.resources.ResourceLocation("gtceu","assembler"));
        return result;
    }
    public static boolean step() throws Exception {
        if (++ticks>1800) throw new AssertionError("Upload latency fixture timeout stage="+stage+" metrics="+metrics);
        var store=child.cloudStore();
        if(stage==-1) {
            if(endpoints.size()<requestedEndpoints) {buildEndpoint();return false;}
            for(var cell:cells) {var node=cell.getMainNode().getNode();if(node==null)return false;if(node.getGrid()!=main.serviceGrid())appeng.api.networking.GridHelper.createConnection(main.serviceGrid().getPivot(),node);}
            for(int i=0;i<endpoints.size();i++) {var endpoint=endpoints.get(i);var controller=controllers.get(i);
                if(!controller.isFormed()||!endpoint.cloudReady(true)||CloudPatternAllocator.isScanning(endpoint))return false;
                if(endpoint.getGrid()!=main.serviceGrid()||!library.id().equals(endpoint.cloudLibraryId())||IvBuffers.controller(endpoint)!=controller)throw new AssertionError("Real endpoint binding/grid/controller differs at "+i);
            }
            if(endpoints.stream().map(CloudPatternBufferMachine::cloudNodeId).distinct().count()!=requestedEndpoints
                    ||controllers.stream().map(MetaMachine::getPos).distinct().count()!=requestedEndpoints)throw new AssertionError("Endpoint/controller identities must be distinct");
            if(controllers.stream().map(controller->((CloudMachineIdentity)controller).cloud$machineInstance(false)).filter(Objects::nonNull).distinct().count()!=requestedEndpoints
                    ||endpoints.stream().anyMatch(endpoint->!endpoint.cloudMode().equals(child.cloudMode())))throw new AssertionError("48 unique machine instances and identical claimed recipe modes required");
            check(true,"48_real_distinct_formed_LCR_controllers_cloud_nodes_powered_AE_endpoints_are_ready_on_same_network");stage=0;
        }
        if (stage==0) {
            library.beginImportBatch(player.getUUID());
            try { for(int n=0;n<128 && background<4096;n++,background++) {
                var op=UUID.randomUUID(); store.upload(library.id(),player.getUUID(),pattern(false,background),new CompoundTag(),op);
                store.acknowledge(library.id(),player.getUUID(),op);
            }} finally {library.endImportBatch();}
            if(background==4096) { saved=store.checkpointLibraryAsync(library.id()); stage=1; }
            return false;
        }
        if(stage==1) {
            if(!saved.isDone() || !baselineReady() || !CloudLibraryService.catalogReady(library,level)) return false;
            saved.join(); if(++quiet<5)return false;
            check(library.entryCount()==4096 && library.storedCount()==4096,"4096_unrelated_originals_seeded_and_durable");
            check(!main.serviceGrid().getCraftingService().isCraftable(AEItemKey.of(Items.GOLD_INGOT)),"gold_not_craftable_before_real_upload");
            player.setPos(main.getPos().getX()+.5,main.getPos().getY()+.5,main.getPos().getZ()-1);
            var ui=main.createUI(player);ui.initWidgets();
            player.containerMenu=new com.lowdragmc.lowdraglib.gui.modular.ModularUIContainer(ui,93);
            expected=pattern(true,0);player.containerMenu.setCarried(expected.copy());
            check(main.menuAuthorized(player),"actual_core_menu_authorizes_actor");
            clickedAt=ticks;clickedNanos=System.nanoTime();
            check(main.acceptTerminalOriginal(player,main.serviceGrid()),"real_core_upload_accepts_carried_original");
            metrics.put("uploadCallMillis",(System.nanoTime()-clickedNanos)/1_000_000.0);
            check(player.containerMenu.getCarried().isEmpty(),"core_upload_debits_exactly_one_original");
            entryId=library.entryIdPage(4096,1).getFirst();observeScans();stage=2;return false;
        }
        var entry=library.entry(entryId);
        observeScans();
        if(entry.status()!=CloudLedger.Status.EXTERNAL_PENDING&&!metrics.containsKey("availableTicks")) {
            metrics.put("availableTicks",ticks-clickedAt);metrics.put("availableMillis",(System.nanoTime()-clickedNanos)/1_000_000.0);
        }
        if(entry.lease()!=null&&!metrics.containsKey("leasedTicks")) {metrics.put("leasedTicks",ticks-clickedAt);metrics.put("leasedMillis",(System.nanoTime()-clickedNanos)/1_000_000.0);}
        if(!main.serviceGrid().getCraftingService().isCraftable(AEItemKey.of(Items.GOLD_INGOT))) return false;
        metrics.put("craftableTicks",ticks-clickedAt);metrics.put("craftableMillis",(System.nanoTime()-clickedNanos)/1_000_000.0);
        metrics.put("backgroundEntries",background);metrics.put("libraryEntries",library.entryCount());metrics.put("realEndpoints",endpoints.size());
        writeMetrics();
        check(entry.lease()!=null && endpoints.stream().anyMatch(endpoint->entry.lease().nodeId().equals(endpoint.cloudNodeId())),"actual_AE_craftability_uses_target_cloud_lease");
        check(main.serviceGrid().getCraftingService().getCraftingFor(AEItemKey.of(Items.GOLD_INGOT)).stream().anyMatch(pattern -> {
            var definition=pattern.getDefinition().getTag();if(definition==null)return false;
            var route=definition.getCompound(com.gtl.enhancedcore.common.recipe.iv.IvBuffers.ROUTE_KEY);
            return route.hasUUID("lease") && route.getUUID("lease").equals(entry.lease().id())
                    && route.getCompound("original").equals(expected.save(new CompoundTag()));
        }),"real_AE_crafting_index_contains_exact_uploaded_original_and_current_lease_route");
        var owner=endpoints.stream().filter(endpoint->entry.lease().nodeId().equals(endpoint.cloudNodeId())).findFirst().orElseThrow();
        check(main.serviceGrid().getCraftingService().getCraftingFor(AEItemKey.of(Items.GOLD_INGOT)).stream().anyMatch(pattern->{
            for(var provider:((appeng.me.service.CraftingService)main.serviceGrid().getCraftingService()).getProviders(pattern))if(provider==owner)return true;
            return false;
        }),
                "real_AE_provider_index_contains_actual_lease_owner_cloud_machine");
        var ref=new CompoundTag();ref.putUUID("library",library.id());ref.putUUID("node",entry.lease().nodeId());ref.putUUID("entry",entry.id());ref.put("original",expected.save(new CompoundTag()));
        store.checkpointLibrary(library.id());
        NbtIo.writeCompressed(ref,directory(level.getServer()).resolve("upload-latency-ref.dat").toFile());
        check(ticks-clickedAt<=Integer.getInteger("gtl.cloudUploadLatencyMaxTicks",40),"real_upload_to_AE_craftability_within_40_ticks");
        check(((Number)metrics.get("craftableMillis")).doubleValue()<=Long.getLong("gtl.cloudUploadLatencyMaxMillis",2000L),"real_upload_to_AE_craftability_within_2000ms");
        return true;
    }
    private static void writeMetrics() throws Exception {
        Files.writeString(directory(level.getServer()).resolve("upload-latency-metrics.json"),new GsonBuilder().setPrettyPrinting().create().toJson(metrics));
    }
    public static void close() {
        if(player!=null&&previousMenu!=null)player.containerMenu=previousMenu;
        if(level!=null)try{writeMetrics();}catch(Exception failure){failure.printStackTrace();}
    }
    public static void reload(MinecraftServer server) throws Exception {
        var directory=directory(server);var result=new LinkedHashMap<String,Object>();var results=new ArrayList<String>();
        try {
            var ref=NbtIo.readCompressed(directory.resolve("upload-latency-ref.dat").toFile());
            var store=CloudPatternStore.get(server.overworld());var library=store.library(ref.getUUID("library"));
            var entry=library.entry(ref.getUUID("entry"));var node=store.node(ref.getUUID("node"));
            if(!library.healthy()||!node.healthy()||library.storedCount()!=4097)throw new AssertionError("Restart lost healthy ownership or original count");
            results.add("Independent JVM reload retains 4097 originals and healthy authority/node");
            if(!entry.original().save(new CompoundTag()).equals(ref.getCompound("original"))||entry.lease()==null||!entry.lease().nodeId().equals(node.id()))throw new AssertionError("Restart lost exact uploaded original/lease");
            results.add("Independent JVM reload retains exact uploaded original and unique cloud lease");
            result.put("status","PASS");
        } catch(Throwable failure){result.put("status","FAIL");result.put("error",failure.toString());failure.printStackTrace();}
        result.put("checks",results);Files.writeString(directory.resolve("upload-latency-reload.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));
        server.halt(false);
    }
}
