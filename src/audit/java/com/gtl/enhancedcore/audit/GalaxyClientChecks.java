package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gtl.enhancedcore.common.machine.GTLEnhancedcoreMachines;
import com.gtl.enhancedcore.common.machine.galaxy.GalaxyConnectionHatch;
import com.gtl.enhancedcore.mixin.gtceu.BlockPatternAccessor;
import com.gtl.enhancedcore.client.renderer.StellarVortexRenderer;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Task-owned title-screen checks; never opens or reads any player world. */
@Mod("enhancedcore_audit")
public final class GalaxyClientChecks {
    private boolean done;
    private final List<String> checks = new ArrayList<>();
    private final Map<String,Object> result = new LinkedHashMap<>();
    public GalaxyClientChecks() { MinecraftForge.EVENT_BUS.addListener(this::tick); }
    private void tick(TickEvent.ClientTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END || !Boolean.getBoolean("gtl.enhancedcore.galaxyAudit")) return;
        var mc = Minecraft.getInstance();
        if (mc.getOverlay() != null || mc.screen == null) return;
        done = true;
        Path directory=mc.gameDirectory.toPath().toAbsolutePath().normalize();
        try {
            check(directory.startsWith(Path.of("C:/IDEA/GTL-Enhancedcore/_work/stellar-fixes-20261020/runtime")),"Task-owned runtime directory");
            check(Files.isRegularFile(directory.resolve("galaxy.marker")),"Explicit isolation marker");
            check(mc.level==null && mc.getSingleplayerServer()==null,"No world opened");
            retirement();
            registrations();
            transformed();
            List<String> failures=new ArrayList<>();
            group("capacity",this::capacities,failures);
            group("pairing",()->checks.addAll(GalaxyPairingChecks.run()),failures);
            group("field",()->StellarFieldChecks.run(this::check),failures);
            group("shader",()->shader(directory),failures);
            group("BER",()->StellarRenderChecks.run(directory,checks,result),failures);
            if(!failures.isEmpty()) { result.put("failures",failures);throw new AssertionError(failures.toString()); }
            result.put("status","PASS");
        } catch(Throwable failure) {
            result.put("status","FAIL");result.put("error",failure.toString());failure.printStackTrace();
        }
        result.put("checks",checks);
        result.put("scope","Real Forge registration/merged Mixin, real flash onUse with synthetic endpoints, deterministic field ticks, stitched overlays and registered BER offscreen draw; no world opened, gameplay not exercised");
        try { Files.writeString(directory.resolve("galaxy-checks.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result)); }
        catch(Exception error) { error.printStackTrace(); }
        mc.stop();
    }
    @FunctionalInterface private interface Checked { void run() throws Exception; }
    private void group(String name,Checked action,List<String> failures) {
        try { action.run(); } catch(Throwable error) { failures.add(name+": "+error);error.printStackTrace(); }
    }
    private void retirement() {
        for(String path:List.of("light_hunter_space_station","nexus_satellite_factory_mk1","nexus_satellite_factory_mk2","nexus_satellite_factory_mk3","nexus_satellite_factory_mk4")) {
            var id=new ResourceLocation("gtladditions",path);
            var definition=(MultiblockMachineDefinition)GTRegistries.MACHINES.get(id);
            check(definition!=null,"Retired ID remains registered: "+path);
            var pattern=((BlockPatternAccessor)definition.getPatternFactory().get()).gtlEnhancedcore$getBlockMatches();
            check(pattern.length==1 && pattern[0].length==1 && pattern[0][0].length==2,"Retired pattern occupies two cells: "+path);
            check(definition.getMatchingShapes().isEmpty(),"Retired preview is empty: "+path);
            var probe=MultiblockMachineDefinition.createDefinition(id);
            probe.setBlockSupplier(definition::getBlock);
            probe.setPatternFactory(()->{throw new AssertionError("Retired original pattern supplier invoked");});
            probe.setShapes(()->{throw new AssertionError("Retired original preview supplier invoked");});
            check(probe.getPatternFactory().get()!=null && probe.getMatchingShapes().isEmpty(),"Original suppliers discarded before evaluation: "+path);
        }
    }
    private void registrations() {
        var station=GTLEnhancedcoreMachines.getStellarVortexStation();
        var sourceHatch=GTLEnhancedcoreMachines.getGalaxySourceHatch();
        var targetHatch=GTLEnhancedcoreMachines.getGalaxyTargetHatch();
        check(station!=null && sourceHatch!=null && targetHatch!=null,"All three new definitions registered");
        check(com.gregtechceu.gtceu.api.machine.multiblock.PartAbility.IMPORT_ITEMS.isApplicable(sourceHatch.getBlock())
                        && com.gregtechceu.gtceu.api.machine.multiblock.PartAbility.IMPORT_ITEMS.isApplicable(targetHatch.getBlock()),
                "Both connection hatches match standard input-hatch ability predicates");
        check(!com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine.class.isAssignableFrom(GalaxyConnectionHatch.class),"Connection hatches have no item input inventory");
        var ordinaryInput=com.gregtechceu.gtceu.api.machine.multiblock.PartAbility.IMPORT_ITEMS.getAllBlocks().stream()
                .filter(block->!GalaxyConnectionHatch.isConnectionPortBlock(block)).findFirst().orElseThrow();
        check(!com.gtl.enhancedcore.common.recipe.iv.IvNativeHatches.materials(targetHatch.getBlock())
                        && com.gtl.enhancedcore.common.recipe.iv.IvNativeHatches.materials(ordinaryInput),
                "Connection ports stay separate from ordinary material-port accounting");
        var materialCandidates=com.gtl.enhancedcore.common.recipe.iv.IvBufferRegistry.materialPorts(-1)
                .limited.getFirst().candidates.get();
        check(Arrays.stream(materialCandidates).anyMatch(info->info.getBlockState().is(targetHatch.getBlock())),
                "IV material-port predicates retain the connection hatch candidate");
        var pattern=(BlockPatternAccessor)station.getPatternFactory().get();
        var cells=pattern.gtlEnhancedcore$getBlockMatches();
        check(cells.length==199 && cells[0].length==216 && cells[0][0].length==199,"New station pattern is 199 x 216 x 199");
        result.put("controllerOffset",pattern.enhanced$getCenterOffset());
        var preview=station.getMatchingShapes().getFirst().getBlocks();
        int present=0,sourceCount=0,controllers=0;
        for(var plane:preview)for(var row:plane)for(var info:row) {
            if(info==null)continue;present++;
            if(info.getBlockState().is(GTLEnhancedcoreMachines.getGalaxySourceHatch().getBlock()))sourceCount++;
            if(info.getBlockState().is(station.getBlock())) { controllers++;check(info.hasBlockEntity(),"Preview controller retains its machine entity"); }
        }
        check(present<199*216*199/2 && present>10000,"Preview unconstrained cells stay null");
        check(sourceCount==1 && controllers==1,"Preview contains exactly one source hatch and one controller");
        result.put("previewNonNullCells",present);
        check(station.getRecipeTypes().length==1 && station.getRecipeTypes()[0].registryName.getPath().equals("brilliant_stars"),"Dedicated Brilliant Stars recipe mode");
    }
    private void transformed() throws Exception {
        for(String name:List.of(
                "com.gtladd.gtladditions.api.machine.multiblock.GTLAddCoilWorkableElectricMultipleRecipesMultiblockMachine",
                "com.gtladd.gtladditions.api.machine.multiblock.GTLAddCoilWorkableElectricParallelHatchMultipleRecipesMachine",
                "com.gtladd.gtladditions.api.machine.multiblock.GTLAddWorkableElectricParallelHatchMultipleRecipesMachine",
                "com.gtladd.gtladditions.api.machine.mutable.MutableCoilElectricMultiblockMachine",
                "com.gtladd.gtladditions.api.machine.mutable.MutableCoilElectricParallelHatchMultiblockMachine",
                "com.gtladd.gtladditions.api.machine.mutable.MutableElectricMultiblockMachine",
                "com.gtladd.gtladditions.api.machine.mutable.MutableElectricParallelHatchMultiblockMachine",
                "com.gtladd.gtladditions.api.machine.wireless.GTLAddWirelessCoilWorkableElectricMultipleRecipesMultiblockMachine",
                "com.gtladd.gtladditions.api.machine.wireless.GTLAddWirelessCoilWorkableElectricParallelHatchMultipleRecipesMachine",
                "com.gtladd.gtladditions.api.machine.wireless.GTLAddWirelessWorkableElectricParallelHatchMultipleRecipesMachine",
                "com.gtladd.gtladditions.common.machine.multiblock.MultiBlockMachine$28$1",
                "com.gtladd.gtladditions.common.machine.multiblock.MultiBlockMachine$32$1",
                "com.gtladd.gtladditions.common.machine.multiblock.controller.AdvancedSpaceElevatorModuleMachine",
                "com.gtladd.gtladditions.common.machine.multiblock.controller.ApocalypticTorsionQuantumMatrix",
                "com.gtladd.gtladditions.common.machine.multiblock.controller.BiologicalSimulationLaboratory",
                "com.gtladd.gtladditions.common.machine.multiblock.controller.DimensionFocusInfinityCraftingArray",
                "com.gtladd.gtladditions.common.machine.multiblock.controller.HeartOfTheUniverse",
                "com.gtladd.gtladditions.common.machine.multiblock.controller.MacroAtomicResonantFragmentStripper",
                "com.gtladd.gtladditions.common.machine.multiblock.controller.OreProcessorMachine",
                "com.gtladd.gtladditions.common.machine.multiblock.controller.SkeletonShiftRiftEngine",
                "com.gtladd.gtladditions.common.machine.multiblock.controller.TaixuTurbidArray",
                "com.gtladd.gtladditions.common.machine.multiblock.controller.bs.BiosphereIIIModule",
                "com.gtladd.gtladditions.common.machine.multiblock.controller.mutable.CreateAggregation",
                "com.gtladd.gtladditions.common.machine.multiblock.controller.mutable.DoorOfCreate",
                "com.gtladd.gtladditions.common.machine.multiblock.controller.mutable.MutableFusionReactorMachine",
                "com.gtladd.gtladditions.common.machine.multiblock.controller.mutable.MutablePCBFactoryMachine",
                "com.gtladd.gtladditions.common.machine.multiblock.controller.mutable.MutableSuprachronalAssemblyLineMachine",
                "com.gtladd.gtladditions.common.machine.multiblock.controller.mutable.MutableTierCasingMachine",
                "com.gtladd.gtladditions.common.modify.MutableMultiBlockModify$setOtherMutable$3$1",
                "com.gtladd.gtladditions.common.modify.MutableMultiBlockModify$setOtherMutable$7$1",
                "org.gtlcore.gtlcore.api.machine.multiblock.CoilWorkableElectricMultipleRecipesMachine",
                "org.gtlcore.gtlcore.common.machine.multiblock.electric.CoilWorkableElectricMultipleRecipesMultiblockMachine",
                "org.gtlcore.gtlcore.common.machine.multiblock.electric.WorkableElectricMultipleRecipesMachine",
                "org.gtlcore.gtlcore.common.machine.multiblock.electric.WorkableElectricParallelHatchMultipleRecipesMachine")) {
            Class<?> type=Class.forName(name,false,GalaxyClientChecks.class.getClassLoader());
            check(Arrays.stream(type.getDeclaredMethods()).anyMatch(m->m.getName().contains("galaxy$machineParallel")),"Capacity override transformed: "+type.getName());
        }
        for(String name:List.of(
                "com.gtladd.gtladditions.api.machine.logic.GTLAddMultipleRecipesLogic",
                "com.gtladd.gtladditions.api.machine.logic.MutableRecipesLogic",
                "com.gtladd.gtladditions.common.machine.multiblock.controller.ApocalypticTorsionQuantumMatrix$Companion$ApocalypticTorsionQuantumMatrixLogic",
                "com.gtladd.gtladditions.common.machine.multiblock.controller.HeartOfTheUniverse$Companion$HeartOfTheUniverseLogic",
                "com.gtladd.gtladditions.common.machine.multiblock.controller.OreProcessorMachine$Companion$OreProcessorRecipeLogic",
                "com.gtladd.gtladditions.common.machine.multiblock.controller.TaixuTurbidArray$Companion$TaixuTurbidArrayLogic")) {
            Class<?> type=Class.forName(name,false,GalaxyClientChecks.class.getClassLoader());
            check(Arrays.stream(type.getMethods()).anyMatch(m->m.getName().contains("galaxy$threads")) || threadMixinInHierarchy(type),"Capacity override transformed or delegates to enhanced base: "+type.getName());
        }

        for(String name:List.of("com.gregtechceu.gtceu.api.machine.trait.RecipeLogic",
                "org.gtlcore.gtlcore.common.machine.trait.MultipleRecipesLogic",
                "com.gtladd.gtladditions.api.machine.logic.MutableRecipesLogic",
                "com.gtladd.gtladditions.api.machine.logic.GTLAddMultipleRecipesLogic")) {
            Class<?> type=Class.forName(name);
            check(Arrays.stream(type.getDeclaredMethods()).anyMatch(m->m.getName().contains("galaxy$")),"Galaxy Mixin merged into "+type.getSimpleName());
        }
    }
    private void capacities() throws Exception {
        var field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);
        var unsafe=(sun.misc.Unsafe)field.get(null);
        var hatch=(ProbeHatch)unsafe.allocateInstance(ProbeHatch.class);
        var machine=(ProbeMachine)unsafe.allocateInstance(ProbeMachine.class);
        machine.hatch=hatch;hatch.linked=true;
        check(com.gtl.enhancedcore.common.machine.galaxy.GalaxyLinks.active(machine),"Loaded-port resolver accepts an active source without an IV whitelist entry");
        Object mutable=null;
        for(String name:List.of("org.gtlcore.gtlcore.common.machine.trait.MultipleRecipesLogic",
                "com.gtladd.gtladditions.api.machine.logic.MutableRecipesLogic",
                "com.gtladd.gtladditions.api.machine.logic.GTLAddMultipleRecipesLogic")) {
            Class<?> type=Class.forName(name);Object logic=unsafe.allocateInstance(type);setProbeOwner(logic,machine);
            if(name.contains("MutableRecipesLogic"))mutable=logic;
            for(String handler:List.of("galaxy$capacity","galaxy$wirelessCapacity","galaxy$budget")) {
                var method=Arrays.stream(type.getDeclaredMethods()).filter(m->m.getName().contains(handler) && Arrays.equals(m.getParameterTypes(),new Class<?>[]{long.class})).findFirst();
                if(method.isEmpty())continue;
                method.get().setAccessible(true);
                check((Long)method.get().invoke(logic,17L)==(long)Integer.MAX_VALUE*Integer.MAX_VALUE,"Effective active budget: "+type.getSimpleName()+" / "+handler);
                hatch.linked=false;
                check((Long)method.get().invoke(logic,17L)==17L,"Disconnected budget preserves original: "+type.getSimpleName()+" / "+handler);
                hatch.linked=true;
            }
            if(!name.endsWith("MultipleRecipesLogic") || name.contains("gtladditions")) {
                var getter=type.getMethod("getMultipleThreads");
                check((Integer)getter.invoke(logic)==Integer.MAX_VALUE,"Active target grants unlimited threads: "+type.getSimpleName());
                hatch.linked=false;
                int original=(Integer)getter.invoke(logic);
                hatch.linked=true;
                check((Integer)getter.invoke(logic)==Integer.MAX_VALUE && original < Integer.MAX_VALUE,
                        "Reconnecting target restores unlimited thread capacity: "+type.getSimpleName());
                for(String handler:List.of("galaxy$capacity","galaxy$wirelessCapacity","galaxy$budget")) {
                    var method=Arrays.stream(type.getDeclaredMethods()).filter(m->m.getName().contains(handler) && Arrays.equals(m.getParameterTypes(),new Class<?>[]{long.class})).findFirst();
                    if(method.isEmpty())continue;
                    method.get().setAccessible(true);
                    check((Long)method.get().invoke(logic,17L)==17L,"ADD computed ordinary budget is not enhanced twice: "+type.getSimpleName()+" / "+handler);
                }
                if(name.endsWith("GTLAddMultipleRecipesLogic")) {
                    var logicField=com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine.class.getDeclaredField("recipeLogic");
                    logicField.setAccessible(true);logicField.set(machine,logic);
                    check(com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.threads(machine)==Integer.MAX_VALUE,"GUI/Jade thread projection uses target capacity once");
                    machine.nativeGuard=true;
                    check((Integer)getter.invoke(logic)==Integer.MAX_VALUE,"IV early-return guard still receives target capacity");
                    hatch.linked=false;
                    check((Integer)getter.invoke(logic)==1,"Disconnected IV guard preserves single recipe thread");
                    hatch.linked=true;machine.nativeGuard=false;
                }
            }
            check(!com.gtl.enhancedcore.common.recipe.GalaxyRecipeBatch.applicable((com.gregtechceu.gtceu.api.machine.trait.RecipeLogic)logic),"Existing multi-recipe engine cannot enter ordinary re-batching: "+type.getSimpleName());
        }
        var ordinary=(com.gregtechceu.gtceu.api.machine.trait.RecipeLogic)unsafe.allocateInstance(com.gregtechceu.gtceu.api.machine.trait.RecipeLogic.class);setProbeOwner(ordinary,machine);
        check(com.gtl.enhancedcore.common.recipe.GalaxyRecipeBatch.applicable(ordinary),"Ordinary electric multi receives batch fallback");
        Class<?> type=mutable.getClass();
        var start=Arrays.stream(type.getDeclaredMethods()).filter(m->m.getName().contains("galaxy$startMode")).findFirst().orElseThrow();start.setAccessible(true);
        var paid=Arrays.stream(type.getDeclaredMethods()).filter(m->m.getName().contains("galaxy$paidMode")).findFirst().orElseThrow();paid.setAccessible(true);
        check((Boolean)start.invoke(mutable,false),"Live link enables temporary ADD mode");
        hatch.linked=false;
        check((Boolean)paid.invoke(mutable,false),"Disconnect keeps paid batch in its original state machine");
        var restore=Arrays.stream(type.getDeclaredMethods()).filter(m->m.getName().contains("galaxy$restoreAfterPaidBatch")).findFirst().orElseThrow();restore.setAccessible(true);
        var callback=new org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<com.gregtechceu.gtceu.api.recipe.GTRecipe>("probe",true);
        restore.invoke(mutable,callback);
        check(callback.isCancelled() && !(Boolean)paid.invoke(mutable,false),"After paid batch, disconnected ADD mode restores the original setting");
    }
    private static boolean threadMixinInHierarchy(Class<?> type) {
        for(;type!=null;type=type.getSuperclass())
            if(Arrays.stream(type.getDeclaredMethods()).anyMatch(m->m.getName().contains("galaxy$threads")))return true;
        return false;
    }
    private static void setProbeOwner(Object logic,Object machine) throws Exception {
        // RecipeLogic hides MachineTrait.machine with a differently typed field;
        // the superclass getter and recipe callbacks must observe the same owner.
        for(Class<?> type=logic.getClass();type!=null;type=type.getSuperclass()) {
            for(var field:type.getDeclaredFields()) if(field.getName().equals("machine") && field.getType().isInstance(machine)) {
                field.setAccessible(true);field.set(logic,machine);
            }
        }
    }
    private static final class ProbeMachine extends com.gtladd.gtladditions.api.machine.multiblock.GTLAddWorkableElectricMultipleRecipesMachine {
        ProbeHatch hatch;
        boolean nativeGuard;
        private ProbeMachine(com.gregtechceu.gtceu.api.machine.IMachineBlockEntity holder){super(holder);}
        @Override public int getMaxParallel(){return 7;}
        @Override public boolean isFormed(){return true;}
        @Override public boolean isRecipeLogicAvailable(){return true;}
        @Override public List<com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart> getParts(){return List.of(hatch);}
        @Override public MultiblockMachineDefinition getDefinition(){return (MultiblockMachineDefinition)GTRegistries.MACHINES.get(new ResourceLocation(nativeGuard?"gtceu:mega_extractor":"gtceu:electric_blast_furnace"));}
    }
    private static final class ProbeHatch extends GalaxyConnectionHatch {
        boolean linked;
        private ProbeHatch(com.gregtechceu.gtceu.api.machine.IMachineBlockEntity holder){super(holder,false);}
        @Override public boolean linkActive(){return linked;}
    }
    private void shader(Path directory) throws Exception {
        var field=StellarVortexRenderer.class.getDeclaredField("shader");field.setAccessible(true);
        check(field.get(null)!=null,"Stellar Vortex GLSL compiled and linked");
        var mc=Minecraft.getInstance();
        var projection=new Matrix4f(RenderSystem.getProjectionMatrix());
        var target=new TextureTarget(1280,960,true,Minecraft.ON_OSX);
        try {
            target.setClearColor(.003F,.006F,.015F,1);target.clear(Minecraft.ON_OSX);target.bindWrite(true);
            RenderSystem.setProjectionMatrix(new Matrix4f().perspective((float)Math.toRadians(52),1280F/960F,.1F,2000F),VertexSorting.DISTANCE_TO_ORIGIN);
            var view=new Matrix4f().lookAt(new Vector3f(410,235,410),new Vector3f(0,0,0),new Vector3f(0,1,0));
            StellarVortexRenderer.render(view,4.5,600,1);
            try(var picture=Screenshot.takeScreenshot(target)) {
                picture.writeToFile(directory.resolve("galaxy-shader.png"));
                int lit=0;
                for(int y=0;y<picture.getHeight();y+=4)for(int x=0;x<picture.getWidth();x+=4) {
                    int color=picture.getPixelRGBA(x,y);
                    if((color&255)>25 || (color>>8&255)>25 || (color>>16&255)>25)lit++;
                }
                result.put("litSamples",lit);check(lit>200,"Offscreen renderer produced visible field geometry");
            }
        } finally {
            target.destroyBuffers();mc.getMainRenderTarget().bindWrite(true);
            RenderSystem.setProjectionMatrix(projection,VertexSorting.DISTANCE_TO_ORIGIN);
        }
    }
    private void check(boolean valid,String label) { if(!valid)throw new AssertionError(label);checks.add(label); }
}
