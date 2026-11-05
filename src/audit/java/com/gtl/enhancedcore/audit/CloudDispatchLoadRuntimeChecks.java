package com.gtl.enhancedcore.audit;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.crafting.*;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.api.util.AEColor;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.client.gui.me.common.MEStorageScreen;
import appeng.core.definitions.*;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import appeng.menu.me.common.MEStorageMenu;
import com.google.gson.GsonBuilder;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import com.gtl.enhancedcore.common.cloud.*;
import com.gtl.enhancedcore.common.cloud.store.CloudPatternStore;
import com.gtl.enhancedcore.common.recipe.iv.IvBuffers;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.TickEvent;
import org.gtlcore.gtlcore.integration.ae2.pattern.PatternQuickUploadMetadata;
import org.gtlcore.gtlcore.mixin.ae2.logic.ExecutingCraftingJobAccessor;

/** Observation-only real-AE material dispatch benchmark. It never edits CPU inventories or task counters. */
public final class CloudDispatchLoadRuntimeChecks {
    private static final int LEAVES = Integer.getInteger("gtl.enhancedcore.cloudDispatchLoadLeaves", 32);
    private static final long QUANTITY = Long.getLong("gtl.enhancedcore.cloudDispatchLoadQuantity", 4096L);
    private record RecipeRow(List<Item> inputs, Item output, int duration) {}
    private static final List<RecipeRow> recipes = new ArrayList<>();
    private static final List<Item> sources = new ArrayList<>();
    private static final List<UUID> entries = new ArrayList<>();
    private static final Map<String, List<Long>> tickMicros = new LinkedHashMap<>();
    private static final Map<String, List<Long>> retainedProductionEndMicros = new LinkedHashMap<>();
    private static final Map<String, List<Long>> excludedHelperEndMicros = new LinkedHashMap<>();
    private static final ConcurrentMap<String, List<Long>> frameMicros = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, LongAdder> stacks = new ConcurrentHashMap<>();
    private static final List<Map<String, Object>> samples = new ArrayList<>();
    private static final List<Map<String, Object>> tickTimingSamples = new ArrayList<>();
    private static final List<Map<String, Object>> terminalReports = Collections.synchronizedList(new ArrayList<>());
    private static final Map<String, Field> counterFields = new LinkedHashMap<>();
    private static final Map<String, Object> observationMetadata = new LinkedHashMap<>();
    private static String reportFile = "cloud-dispatch-load.json";
    private static ServerLevel level;
    private static CloudPatternLibraryMachine main;
    private static List<CloudPatternBufferMachine> children;
    private static ServerPlayer actor, realPlayer;
    private static List<String> checks;
    private static Path directory;
    private static BlockPos terminalPos;
    private static Thread serverThread, renderThread;
    private static ScheduledExecutorService sampler;
    private static Future<ICraftingPlan> calculation;
    private static CraftingCPUCluster cpu;
    private static ICraftingLink link;
    private static Item result;
    private static volatile String phase = "inactive";
    private static volatile boolean active;
    private static volatile TerminalProbe terminalProbe;
    private static long tickStart, framePrevious, auxiliaryStart, auxiliaryNanos, auxiliaryProductionNanos, retainedProductionNanos;
    private static String startPhase;
    private static int ticks, stage, since, uploaded, readySince, probeSerial;
    private static long planBytes, cpuStorage;
    private static Field cpuJob;
    private static final class TerminalProbe {
        final int id; final String phase; final int expectedCraftables;
        volatile long requested, serverMenu, screen, repository;
        volatile int clientTicks, screenFrames, repositoryRenderStart, menuId = -1;
        volatile boolean captured;
        TerminalProbe(int id, String phase, int expectedCraftables) { this.id=id; this.phase=phase; this.expectedCraftables=expectedCraftables; }
    }
    private CloudDispatchLoadRuntimeChecks() {}

    public static void prepareRecipes(ServerLevel world, WorkableElectricMultiblockMachine machine) {
        if (LEAVES < 1 || LEAVES > 1024 || QUANTITY < 1) throw new IllegalArgumentException("Invalid bounded load probe size");
        recipes.clear(); sources.clear();
        var items = BuiltInRegistries.ITEM.stream().filter(item -> item != Items.AIR && item.getMaxStackSize() == 64)
                .sorted(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString())).iterator();
        var outputs = new ArrayList<Item>();
        for (int i=0;i<LEAVES;i++) {
            Item source=items.next(), first=items.next(), second=items.next(); sources.add(source);
            recipes.add(new RecipeRow(List.of(source), first, 2_000_000_000));
            recipes.add(new RecipeRow(List.of(first), second, 80)); outputs.add(second);
        }
        while (outputs.size()>1) {
            var next=new ArrayList<Item>();
            for(int i=0;i<outputs.size();i+=8) {
                Item output=items.next(); recipes.add(new RecipeRow(List.copyOf(outputs.subList(i,Math.min(i+8,outputs.size()))),output,80)); next.add(output);
            }
            outputs=next;
        }
        result=outputs.getFirst();
        var all=new ArrayList<net.minecraft.world.item.crafting.Recipe<?>>(world.getRecipeManager().getRecipes());
        for(int i=0;i<recipes.size();i++) {
            var row=recipes.get(i);
            var builder=GTRecipeBuilder.of(new ResourceLocation("gtl_enhancedcore","audit_cloud_dispatch_load_"+i),machine.getRecipeType());
            for(var input:row.inputs) builder.inputItems(input);
            var recipe=builder.outputItems(row.output).duration(row.duration).EUt(8).buildRawRecipe();
            all.add(recipe); machine.getRecipeType().getLookup().addRecipe(recipe);
        }
        world.getRecipeManager().replaceRecipes(all);
    }

    public static void begin(ServerLevel world, CloudPatternLibraryMachine library, List<CloudPatternBufferMachine> buffers,
                             ServerPlayer owner, List<String> results) throws Exception {
        beginObservations(world,library,buffers,owner,results,"cloud-dispatch-load.json");
    }
    public static void beginObservations(ServerLevel world, CloudPatternLibraryMachine library, List<CloudPatternBufferMachine> buffers,
                             ServerPlayer owner, List<String> results, String outputFile) throws Exception {
        reportFile=outputFile;observationMetadata.clear();
        level=world; main=library; children=List.copyOf(buffers); actor=owner; checks=results;
        directory=world.getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getParent().getParent();
        ticks=0; stage=0; since=0; uploaded=0; readySince=0; entries.clear(); samples.clear();tickTimingSamples.clear();terminalReports.clear();
        tickMicros.clear();retainedProductionEndMicros.clear();excludedHelperEndMicros.clear();frameMicros.clear();stacks.clear();counterFields.clear();
        require(children.size()==2 && children.stream().allMatch(child->child.cloudReady(true)),"two_real_ready_cloud_providers");
        require(!org.gtlcore.gtlcore.integration.ae2.graph.CraftingEngineRouter.useGraph(),"real_legacy_CPU_engine");
        realPlayer=world.getServer().getPlayerList().getPlayers().stream().filter(p->!p.getUUID().equals(owner.getUUID())).findFirst().orElseThrow();
        var hardware=new ArrayList<Map<String,Object>>();
        for(var child:children) {
            var machine=(WorkableElectricMultiblockMachine)IvBuffers.controller(child);
            var details=new LinkedHashMap<String,Object>();details.put("controller",machine.getDefinition().getId().toString());
            details.put("position",machine.getPos().toShortString());
            details.put("parts",machine.getParts().stream().map(part->{
                var component=part.self();var info=new LinkedHashMap<String,Object>();
                info.put("id",component.getDefinition().getId().toString());info.put("position",component.getPos().toShortString());
                if(component instanceof com.gregtechceu.gtceu.api.machine.feature.multiblock.IMaintenanceMachine maintenance)
                    {info.put("durationMultiplier",maintenance.getDurationMultiplier());info.put("maintenanceProblems",maintenance.getMaintenanceProblems());}
                return info;
            }).toList());
            var energy=machine.getEnergyContainer();
            if(energy!=null){details.put("energyInputVoltage",energy.getInputVoltage());details.put("energyInputAmperage",energy.getInputAmperage());details.put("energyCapacity",energy.getEnergyCapacity());}
            hardware.add(details);
        }
        observationMetadata.put("actualFormedHardware",hardware);
        terminalPos=main.getPos().north(2);
        world.setBlockAndUpdate(terminalPos,AEBlocks.CABLE_BUS.block().defaultBlockState());
        var host=(CableBusBlockEntity)world.getBlockEntity(terminalPos);
        require(host.addPart(AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT),null,owner)!=null,"real_glass_cable_part_installed");
        require(host.addPart((appeng.api.parts.IPartItem<?>)AEParts.TERMINAL.stack().getItem(),Direction.NORTH,owner)!=null,"real_AE_item_terminal_part_installed");
        for(var child:children) ((DriveBlockEntity)world.getBlockEntity(child.getPos().north(2))).getInternalInventory()
                .setItemDirect(0,AEItems.ITEM_CELL_256K.stack());
        for(var pos:BlockPos.betweenClosed(terminalPos.offset(-2,-1,-4),terminalPos.offset(2,-1,-2)))
            world.setBlockAndUpdate(pos,Blocks.SMOOTH_STONE.defaultBlockState());
        realPlayer.getAbilities().flying=true; realPlayer.onUpdateAbilities();
        realPlayer.connection.teleport(terminalPos.getX()+.5,terminalPos.getY(),terminalPos.getZ()-2.5,0,0);
        cpuJob=appeng.crafting.execution.CraftingCpuLogic.class.getDeclaredField("job"); cpuJob.setAccessible(true);
        var cpuOrigin=main.getPos().north().east();
        for(var cursor:BlockPos.betweenClosed(cpuOrigin.north(),cpuOrigin.offset(3,3,0))) {
            var pos=cursor.immutable();
            if(pos.equals(cpuOrigin.east()))continue; // Keep the real coprocessor installed by the shared fixture.
            require(world.isEmptyBlock(pos)||pos.equals(cpuOrigin),"bounded_physical_CPU_storage_cube_position_available_"+pos);
            world.setBlockAndUpdate(pos,AEBlocks.CRAFTING_STORAGE_256K.block().defaultBlockState());
        }
        for(Class<?> type:List.of(CloudCpuRefill.class,CloudPatternBufferMachine.class,CloudPatternAllocator.class,
                CraftingService.class,appeng.crafting.execution.CraftingCpuLogic.class))
            for(var field:type.getDeclaredFields()) if(field.getName().startsWith("ec$audit$")) {
                field.setAccessible(true); counterFields.put(type.getSimpleName()+"."+field.getName().substring(9),field);
            }
        require(!counterFields.isEmpty(),"observation_only_agent_counters_are_present");
        serverThread=Thread.currentThread(); active=true; phase="fixture_settle";
        sampler=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"CloudDispatchLoadSampler");t.setDaemon(true);return t;});
        sampler.scheduleAtFixedRate(CloudDispatchLoadRuntimeChecks::sampleStacks,10,10,TimeUnit.MILLISECONDS);
        write("RUNNING","");
    }

    public static void startTickBoundary(TickEvent.ServerTickEvent event) {
        if(active && event.phase==TickEvent.Phase.START) {tickStart=System.nanoTime();auxiliaryNanos=0;retainedProductionNanos=0;startPhase=phase;}
    }
    public static void endTickBoundary(TickEvent.ServerTickEvent event) {
        if(active && event.phase==TickEvent.Phase.END && tickStart!=0){
            long measuredMicros=Math.max(0,(System.nanoTime()-tickStart-auxiliaryNanos)/1000);
            tickMicros.computeIfAbsent(startPhase,ignored->new ArrayList<>()).add(measuredMicros);
            retainedProductionEndMicros.computeIfAbsent(startPhase,ignored->new ArrayList<>()).add(retainedProductionNanos/1000);
            excludedHelperEndMicros.computeIfAbsent(startPhase,ignored->new ArrayList<>()).add(auxiliaryNanos/1000);
            tickTimingSamples.add(Map.of("profileObservationTick",ticks,"phaseAtStart",startPhase,"phaseAtEnd",phase,
                    "serverMainThreadMicros",measuredMicros,"productionEntrypointEndWorkRetainedMicros",retainedProductionNanos/1000,"pureHelperEndWorkExcludedMicros",auxiliaryNanos/1000));
        }
    }
    public static void beginAuxiliaryEndWork(){if(active){auxiliaryStart=System.nanoTime();auxiliaryProductionNanos=0;}}
    /** Retain real production entrypoint work invoked inside helper END in the measured server tick. */
    public static void includeProductionEndWork(long nanos){if(active&&auxiliaryStart!=0)auxiliaryProductionNanos+=nanos;}
    public static void endAuxiliaryEndWork(){
        if(active&&auxiliaryStart!=0){
            long elapsed=System.nanoTime()-auxiliaryStart;
            if(auxiliaryProductionNanos<0||auxiliaryProductionNanos>elapsed)throw new AssertionError("Overlapping production END timing scope");
            auxiliaryNanos+=elapsed-auxiliaryProductionNanos;retainedProductionNanos+=auxiliaryProductionNanos;auxiliaryStart=0;
        }
    }
    public static void renderBoundary(TickEvent.RenderTickEvent event) {
        if(!active || event.phase!=TickEvent.Phase.END)return;
        renderThread=Thread.currentThread(); long now=System.nanoTime();
        var probe=terminalProbe;
        if(probe!=null && probe.requested!=0 && !probe.captured && Minecraft.getInstance().screen instanceof MEStorageScreen<?>) {
            if(probe.screen==0)probe.screen=now;
            probe.screenFrames++;
        }
        if(framePrevious!=0)frameMicros.computeIfAbsent(phase,ignored->Collections.synchronizedList(new ArrayList<>())).add((now-framePrevious)/1000);
        framePrevious=now;
    }
    private static void sampleStacks() {
        if(!active)return;
        String label=phase;
        for(var entry:Map.of("server",serverThread,"render",renderThread==null?serverThread:renderThread).entrySet()) {
            var frames=entry.getValue().getStackTrace();
            String key=label+"|"+entry.getKey()+"|"+String.join("\n",Arrays.stream(frames).limit(22).map(StackTraceElement::toString).toList());
            stacks.computeIfAbsent(key,ignored->new LongAdder()).increment();
        }
    }

    public static void clientTick() throws Exception {
        if(!active)return;
        var probe=terminalProbe; if(probe==null || probe.captured)return;
        var mc=Minecraft.getInstance(); if(mc.player==null || mc.level==null || mc.gameMode==null)return;
        if(++probe.clientTicks==1 && mc.screen!=null)mc.player.closeContainer();
        if(probe.requested==0) {
            if(probe.clientTicks<5 || mc.screen!=null || !mc.level.hasChunkAt(terminalPos)
                    || mc.player.distanceToSqr(terminalPos.getX()+.5,terminalPos.getY(),terminalPos.getZ()-2.5)>4)return;
            probe.requested=System.nanoTime();
            var hit=new BlockHitResult(new Vec3(terminalPos.getX()+.5,terminalPos.getY()+.5,terminalPos.getZ()+.01),Direction.NORTH,terminalPos,false);
            if(!mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,hit).consumesAction())throw new AssertionError("load:real terminal use was rejected");
        } else if(mc.screen instanceof MEStorageScreen<?> && mc.player.containerMenu instanceof MEStorageMenu menu) {
            var repo=menu.getClientRepo();
            long craftables=repo==null?0:repo.getAllEntries().stream().filter(e->e.isCraftable()).count();
            if(menu.isPowered() && (probe.expectedCraftables==0 || craftables>=probe.expectedCraftables) && probe.repository==0)
                {probe.repository=System.nanoTime();probe.repositoryRenderStart=probe.screenFrames;}
            if(probe.repository!=0 && probe.screen!=0 && probe.screenFrames-probe.repositoryRenderStart>=3) {
                var row=new LinkedHashMap<String,Object>(); row.put("phase",probe.phase);row.put("probe",probe.id);
                row.put("requestToServerMenuMicros",probe.serverMenu==0?-1:(probe.serverMenu-probe.requested)/1000);
                row.put("requestToActualScreenMicros",(probe.screen-probe.requested)/1000);
                row.put("requestToSyncedRepositoryMicros",(probe.repository-probe.requested)/1000);
                row.put("actualScreen",mc.screen.getClass().getName());row.put("actualMenu",menu.getClass().getName());
                row.put("syncedCraftableKeys",craftables);row.put("expectedDistinctCraftableOutputs",probe.expectedCraftables);
                row.put("powered",menu.isPowered());terminalReports.add(row);
                row.put("completedScreenRenderFramesBeforeCapture",probe.screenFrames);
                row.put("completedRepositoryRenderFramesBeforeCapture",probe.screenFrames-probe.repositoryRenderStart);
                try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                    image.writeToFile(directory.resolve("screenshots/dispatch-load-terminal-"+probe.id+".png"));
                }
                probe.captured=true; mc.player.closeContainer();
            }
        }
        if(probe.requested!=0 && System.nanoTime()-probe.requested>60_000_000_000L)
            throw new AssertionError("load:actual terminal response exceeded60seconds phase="+probe.phase);
    }
    private static void requestTerminal(int craftables) {
        if(terminalProbe!=null && !terminalProbe.captured)throw new AssertionError("load:previous terminal probe did not complete");
        terminalProbe=new TerminalProbe(++probeSerial,phase,craftables);
    }
    public static void setObservationPhase(String label){phase=label;}
    public static void captureObservation() throws Exception {serverMenuObservation();observe();}
    public static void observationTick() throws Exception {
        ticks++;serverMenuObservation();
        if(ticks%20==0)observe();
        if(ticks%100==0)write("RUNNING","");
    }
    public static void requestTerminalProbe(int craftables){requestTerminal(craftables);}
    public static boolean terminalProbeComplete(){return terminalProbe!=null&&terminalProbe.captured;}
    public static void finishObservations(String status,String error) throws Exception {stop(status,error);}
    public static void writeObservations(String status,String error) throws Exception {write(status,error);}
    public static void putObservationMetadata(String key,Object value){observationMetadata.put(key,value);}
    private static void serverMenuObservation() {
        var probe=terminalProbe;
        if(probe!=null && probe.requested!=0 && probe.serverMenu==0 && realPlayer.containerMenu instanceof MEStorageMenu menu) {
            probe.serverMenu=System.nanoTime();probe.menuId=menu.containerId;
        }
    }

    public static boolean step() throws Exception {
        try {return stepInternal();}catch(Exception|Error failure){stop("FAIL",failure.toString());throw failure;}
    }
    private static boolean stepInternal() throws Exception {
        if(++ticks>5000)throw new AssertionError("load:timeout stage="+stage+" uploaded="+uploaded);
        serverMenuObservation();
        if(ticks%20==0){observe();if(ticks%100==0)write("RUNNING","");}
        switch(stage) {
            case 0 -> {if(ticks<60)return false;phase="idle_baseline";since=ticks;stage=1;requestTerminal(0);}
            case 1 -> {
                if(ticks-since<200 || !terminalProbe.captured)return false;
                phase="real_original_upload";since=ticks;stage=2;
            }
            case 2 -> {
                if(uploaded<recipes.size()) {
                    var row=recipes.get(uploaded);
                    var pattern=PatternDetailsHelper.encodeProcessingPattern(row.inputs.stream().map(i->new GenericStack(AEItemKey.of(i),1)).toArray(GenericStack[]::new),
                            new GenericStack[]{new GenericStack(AEItemKey.of(row.output),1)});
                    PatternQuickUploadMetadata.writeRecipeTypeId(pattern,children.getFirst().cloudMode());
                    pattern.getOrCreateTag().putInt("cloudDispatchLoadRecipe",uploaded);
                    var operation=UUID.randomUUID();var store=CloudPatternStore.get(level);
                    entries.add(store.upload(main.libraryId(),actor.getUUID(),pattern,new CompoundTag(),operation).id());
                    require(pattern.isEmpty(),"physical_original_consumed_"+uploaded);
                    store.acknowledge(main.libraryId(),actor.getUUID(),operation);uploaded++;return false;
                }
                phase="catalogue_ready";stage=3;since=ticks;
            }
            case 3 -> {
                if(children.stream().anyMatch(c->c.getAvailablePatterns().size()!=recipes.size()))return false;
                if(readySince==0){readySince=ticks;requestTerminal(recipes.size());}
                if(ticks-readySince<100 || !terminalProbe.captured)return false;
                var store=CloudPatternStore.get(level);
                require(entries.stream().allMatch(id->store.library(main.libraryId()).entry(id).leases().size()==2),"every_distinct_original_has_two_actual_endpoint_leases");
                require(crafting().isCraftable(AEItemKey.of(result)),"real_AE_index_exposes_final_aggregate_output");
                for(var item:sources) {
                    long inserted=main.serviceGrid().getStorageService().getInventory().insert(AEItemKey.of(item),QUANTITY,Actionable.MODULATE,IActionSource.ofPlayer(actor));
                    require(inserted==QUANTITY,"real_ME_stores_full_source_material_"+BuiltInRegistries.ITEM.getKey(item));
                }
                phase="real_CPU_calculation";since=ticks;
                calculation=crafting().beginCraftingCalculation(level,()->IActionSource.ofPlayer(actor),AEItemKey.of(result),QUANTITY,CalculationStrategy.REPORT_MISSING_ITEMS);stage=4;
            }
            case 4 -> {
                if(!calculation.isDone())return false;
                var plan=calculation.get();
                planBytes=plan.bytes();
                require(!plan.simulation()&&plan.missingItems().isEmpty()&&plan.patternTimes().size()==recipes.size(),"actual_CPU_plan_contains_every_distinct_recipe_task_"+recipes.size());
                cpu=crafting().getCpus().stream().filter(c->c instanceof CraftingCPUCluster&&!c.isBusy()&&c.getAvailableStorage()>=plan.bytes()).map(CraftingCPUCluster.class::cast)
                        .findFirst().orElseThrow(()->new AssertionError("load:physical CPU storage insufficient planBytes="+plan.bytes()+" available="+crafting().getCpus().stream().map(ICraftingCPU::getAvailableStorage).toList()));
                cpuStorage=cpu.getAvailableStorage();
                var submitted=crafting().submitJob(plan,null,cpu,false,IActionSource.ofPlayer(actor));
                require(submitted.successful(),"real_CPU_accepts_material_load_plan_"+submitted.errorCode());link=submitted.link();
                phase="natural_dispatch_inventory_sufficient_and_missing";since=ticks;stage=5;requestTerminal(recipes.size());observe();
            }
            case 5 -> {
                observe();
                if(ticks-since<120 || !terminalProbe.captured)return false;
                require(terminalProbe.captured,"actual_terminal_responds_during_material_dispatch");
                phase="persistent_missing_intermediate_materials";since=ticks;stage=6;requestTerminal(recipes.size());
            }
            case 6 -> {
                if(ticks-since<200 || !terminalProbe.captured)return false;
                require(cpu.isBusy() && (link==null||!link.isCanceled()),"real_CPU_still_owns_waiting_intermediate_tasks");
                require(children.stream().mapToLong(c->c.iv$getState().jobs.stream().mapToLong(j->j.totalOperations).sum()).sum()==Math.multiplyExact(QUANTITY,LEAVES),"all_leaf_materials_are_owned_once_by_real_cloud_orders");
                require(samples.stream().anyMatch(s->((Number)s.getOrDefault("CPUtasksWithFullOnePatternInput",0)).longValue()>0),"observed_real_CPU_tasks_with_sufficient_inputs_before_natural_dispatch");
                require(samples.stream().anyMatch(s->((Number)s.getOrDefault("CPUtasksMissingOnePatternInput",0)).longValue()>=LEAVES),"observed_many_actual_waiting_tasks_missing_intermediate_materials");
                require(terminalReports.size()==4,"baseline_catalogue_dispatch_and_waiting_terminal_probes_completed");
                stop("PASS","");return true;
            }
            default -> throw new AssertionError("load:unknown stage");
        }
        return false;
    }

    private static void observe() throws Exception {
        var row=new LinkedHashMap<String,Object>();row.put("tick",ticks);row.put("phase",phase);
        row.put("publishedPatterns",children.stream().map(c->c.getAvailablePatterns().size()).toList());
        row.put("cloudJobs",children.stream().map(c->c.iv$getState().jobs.size()).toList());
        row.put("acceptedOperations",children.stream().map(c->c.iv$getState().jobs.stream().mapToLong(j->j.totalOperations).sum()).toList());
        row.put("unprocessedInputAmounts",children.stream().map(c->c.iv$getState().jobs.stream().flatMap(j->j.inventory.values().stream()).mapToLong(Long::longValue).sum()).toList());
        row.put("activeNativeJobs",children.stream().map(c->c.iv$getState().jobs.stream().filter(j->j.active()).count()).toList());
        row.put("remainingOperations",children.stream().map(c->c.iv$getState().jobs.stream().mapToLong(j->j.remaining).sum()).toList());
        row.put("nativeBatchEutDurationParallel",children.stream().map(c->c.iv$getState().jobs.stream().filter(j->j.active())
                .map(j->List.of(j.eut,(long)j.duration,j.parallel,j.nativeCapacity)).distinct().toList()).toList());
        int taskCount=0,sufficient=0,missing=0;long taskRepetitions=0;
        if(cpu!=null) {
            var job=(ExecutingCraftingJob)cpuJob.get(cpu.craftingLogic);
            if(job!=null)for(var task:((ExecutingCraftingJobAccessor)job).getTasks().entrySet()) {
                taskCount++;taskRepetitions+=((org.gtlcore.gtlcore.mixin.ae2.logic.ExecutingCraftingJobTaskProgressAccessor)task.getValue()).getValue();
                boolean enough=true;
                for(var input:task.getKey().getInputs()) {
                    var possible=input.getPossibleInputs();
                    if(possible.length!=1 || cpu.craftingLogic.getInventory().list.get(possible[0].what())<input.getMultiplier()){enough=false;break;}
                }
                if(enough)sufficient++;else missing++;
            }
        }
        row.put("actualCPUtasks",taskCount);row.put("actualCPUremainingPatternRepetitions",taskRepetitions);
        row.put("CPUtasksWithFullOnePatternInput",sufficient);row.put("CPUtasksMissingOnePatternInput",missing);
        var counters=new LinkedHashMap<String,Long>();for(var field:counterFields.entrySet())counters.put(field.getKey(),field.getValue().getLong(null));row.put("methodEntryCounters",counters);
        samples.add(row);
    }
    private static CraftingService crafting(){return (CraftingService)main.serviceGrid().getCraftingService();}
    private static Map<String,Object> distribution(List<Long> values) {
        var copy=new ArrayList<>(values);Collections.sort(copy);if(copy.isEmpty())return Map.of("count",0);
        return Map.of("count",copy.size(),"p50",copy.get((copy.size()-1)/2),"p95",copy.get((int)Math.ceil(copy.size()*.95)-1),
                "p99",copy.get((int)Math.ceil(copy.size()*.99)-1),"max",copy.getLast(),"mean",copy.stream().mapToLong(Long::longValue).average().orElse(0));
    }
    private static void stop(String status,String failure) throws Exception {active=false;if(sampler!=null)sampler.shutdownNow();write(status,failure);}
    private static void write(String status,String failure) throws Exception {
        var report=new LinkedHashMap<String,Object>();report.put("status",status);report.put("error",failure);report.put("ticks",ticks);report.put("stage",stage);report.put("phase",phase);
        report.put("leafCount",LEAVES);report.put("realDistinctRecipesAndPatterns",recipes.size());report.put("perLeafQuantity",QUANTITY);report.put("physicalSourceMaterialTotal",Math.multiplyExact(LEAVES,QUANTITY));
        report.put("actualPlanBytes",planBytes);report.put("actualCPUPhysicalAvailableStorage",cpuStorage);
        report.put("externalProfile",new LinkedHashMap<>(observationMetadata));
        var durations=new LinkedHashMap<String,Object>();tickMicros.forEach((key,value)->durations.put(key,distribution(value)));report.put("serverMainThreadStartToEndMicros",durations);
        var retained=new LinkedHashMap<String,Object>();retainedProductionEndMicros.forEach((key,value)->retained.put(key,distribution(value)));report.put("productionEntrypointEndWorkRetainedInServerTickMicros",retained);
        var excluded=new LinkedHashMap<String,Object>();excludedHelperEndMicros.forEach((key,value)->excluded.put(key,distribution(value)));report.put("pureHelperEndWorkExcludedFromServerTickMicros",excluded);
        var frames=new LinkedHashMap<String,Object>();frameMicros.forEach((key,value)->{synchronized(value){frames.put(key,distribution(value));}});report.put("clientRenderFrameIntervalMicros",frames);
        report.put("samples",samples);report.put("serverTickTimingSamples",tickTimingSamples);report.put("terminalInteractions",new ArrayList<>(terminalReports));
        var hotFrames=new LinkedHashMap<String,Long>();
        for(var entry:stacks.entrySet()) {
            String[] parts=entry.getKey().split("\\|",3);long count=entry.getValue().sum();
            hotFrames.merge(parts[0]+"|"+parts[1]+"|allSamples",count,Long::sum);
            for(String target:List.of("CloudCpuRefill", "cloudDispatchCapacity", "graphOperationsPerPattern", "dispatchShare", "cloudReadyFailure", "executeCrafting", "CloudPatternMatching", "CloudPatternAllocator", "CloudPatternStore", "MEStorageMenu", "MEStorageScreen", "CloudDispatchLoadRuntimeChecks", "java.util.concurrent.locks.LockSupport.park"))
                if(parts[2].contains(target))hotFrames.merge(parts[0]+"|"+parts[1]+"|"+target,count,Long::sum);
        }
        report.put("sampledHotPathFrameCounts",hotFrames);
        report.put("sampledStacks",stacks.entrySet().stream().sorted((a,b)->Long.compare(b.getValue().sum(),a.getValue().sum())).limit(100)
                .map(e->Map.of("phaseThreadStack",e.getKey(),"samples",e.getValue().sum())).toList());
        report.put("boundary","New marked isolated real two-LCR fixture; physical AE CPU is a bounded4x4x2 cuboid containing31 real256K storage blocks and one coprocessor. Real physical processing originals, real AE calculation/submission/storage and natural machine ticks. Main-thread Forge HIGHEST START-to-LOWEST END microseconds subtract exact measured pure helper END fixture/observation work; explicitly timed production generator begin/tick calls inside that helper are retained in the same measured tick, alongside natural deferred production callbacks and normal AE end listeners. Retained production and excluded pure helper durations are reported per phase and per actual tick. Phase distributions use the label captured at START; a begin call made when helper END enters a new round belongs to the preceding idle/settle bucket. Raw tick samples preserve both START and END labels and retained production time so that boundary call remains visible.10ms stack sampler and method counters are observer overhead shared by baseline and load; stacks can include helper END work and identify its frames.Only generated recipes and materials are fixture data; no CPU inventory/task/counter/recipe-logic ticks are manually changed.Terminal timing uses a real client block-use packet, first server END-observed menu, actual rendered client screen and synced repository captured after3more render frames.No player-world/layout or remote claim reproduction is established.");
        Files.writeString(directory.resolve(reportFile),new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
    private static void require(boolean value,String label){if(!value)throw new AssertionError("load:"+label);checks.add("load:"+label);}
}
