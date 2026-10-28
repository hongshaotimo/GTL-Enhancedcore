package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gtl.enhancedcore.common.registration.RetiredMachines;
import com.gtl.enhancedcore.common.structure.ChamberPositions;
import com.gtl.enhancedcore.mixin.gtceu.BlockPatternAccessor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;

/** Real transformed classes and registered patterns, in a fresh isolated title screen only. */
@Mod("enhancedcore_audit")
public final class RuntimeChamberClientChecks {
    private boolean done;
    private final List<String> checks = new ArrayList<>();
    public RuntimeChamberClientChecks() { MinecraftForge.EVENT_BUS.addListener(this::tick); }
    private void tick(TickEvent.ClientTickEvent event) {
        if(done || event.phase != TickEvent.Phase.END || !Boolean.getBoolean("gtl.enhancedcore.chamberRuntimeAudit")) return;
        var mc=Minecraft.getInstance();
        if(mc.getOverlay()!=null || mc.screen==null) return;
        done=true;
        Path directory=mc.gameDirectory.toPath().toAbsolutePath().normalize();
        Map<String,Object> result=new LinkedHashMap<>();
        try {
            check(directory.startsWith(Path.of("C:/IDEA/GTL-Enhancedcore/_audit/request-fixes-20261027"))
                    && Files.isRegularFile(directory.resolve("chamber-runtime.marker"))
                    && mc.level==null && mc.player==null && mc.getSingleplayerServer()==null,
                    "Explicit isolated title screen, no player world");
            merged("com.gregtechceu.gtceu.api.machine.MachineDefinition","retired$inertFactory");
            merged("com.gregtechceu.gtceu.api.machine.multiblock.PartAbility","retired$noAbility");
            merged("net.minecraft.world.item.crafting.RecipeManager","retired$removeRecipes");
            merged("com.gregtechceu.gtceu.api.machine.trait.RecipeLogic","galaxy$pay","galaxy$batch","galaxy$customCandidate");
            merged("com.gtladd.gtladditions.utils.RecipeCalculationHelper","galaxy$powerBudget");
            merged("org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAeNetworkRuntime","cloud$registeredTarget","cloud$actualTarget");
            merged("appeng.menu.implementations.PatternAccessTermMenu","cloud$actions","cloud$sync","cloud$take");
            merged("com.glodblock.github.extendedae.client.gui.GuiExPatternTerminal","cloud$replaceProjection","cloud$pages","cloud$search");
            merged("com.lowdragmc.lowdraglib.gui.widget.SceneWidget","enhanced$draw","enhanced$clear");
            merged("org.gtlcore.gtlcore.api.gui.PatternPreviewWidget","enhanced$chambers");
            merged("org.gtlcore.gtlcore.client.preview.PreviewControls","enhanced$includeFootprintHeight","gtlEnhancedcore$drawFootprintAboveHeight");
            Class<?> memory=Class.forName("org.gtlcore.gtlcore.api.gui.PatternPreviewWidget$MBPattern");
            check(com.gtl.enhancedcore.client.preview.PreviewPatternMemory.class.isAssignableFrom(memory),
                    "Real MBPattern implements chamber memory contract");
            merged("com.gregtechceu.gtceu.api.pattern.BlockPattern","enhanced$chamberOffset");
            var wireless=Class.forName("org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAeNetworkRuntime")
                    .getDeclaredMethod("isWirelessMeTargetId",net.minecraft.resources.ResourceLocation.class);
            wireless.setAccessible(true);
            check(Boolean.TRUE.equals(wireless.invoke(null,new net.minecraft.resources.ResourceLocation("gtl_enhancedcore","cloud_pattern_buffer"))),
                    "Real wireless runtime accepts cloud buffer ID");
            for(String id:RetiredMachines.ids()) {
                var definition=GTRegistries.MACHINES.get(new net.minecraft.resources.ResourceLocation(id));
                check(definition!=null,"Retired shell retained: "+id);
                var entity=definition.getBlockEntityType().create(BlockPos.ZERO,definition.defaultBlockState());
                check(entity instanceof IMachineBlockEntity,"Retired shell creates holder: "+id);
                var machine=((IMachineBlockEntity)entity).getMetaMachine();
                check(machine.getClass().getName().equals(id.equals(RetiredMachines.ARRAY)
                        ? "com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine"
                        : "com.gregtechceu.gtceu.api.machine.MetaMachine"),"Retired shell inert factory: "+id);
            }
            Map<String,Object> patterns=new LinkedHashMap<>();
            var expected=Map.of("gtl_enhancedcore:high_flux_breeder_reactor",new int[]{47,55,48,112},
                    "gtl_enhancedcore:dragon_field_proliferation_core",new int[]{252,108,120,24},
                    "gtl_enhancedcore:cloud_pattern_library",new int[]{49,33,45,0},
                    "gtladditions:skeleton_shift_rift_engine",new int[]{31,31,48,80});
            for(String id:List.of("gtl_enhancedcore:high_flux_breeder_reactor","gtl_enhancedcore:dragon_field_proliferation_core",
                    "gtl_enhancedcore:cloud_pattern_library","gtladditions:skeleton_shift_rift_engine")) {
                var definition=(MultiblockMachineDefinition)GTRegistries.MACHINES.get(new net.minecraft.resources.ResourceLocation(id));
                check(definition!=null,"Registered requested machine: "+id);
                var pattern=definition.getPatternFactory().get();
                var grid=((BlockPatternAccessor)pattern).gtlEnhancedcore$getBlockMatches();
                var unique=new IdentityHashMap<TraceabilityPredicate,Boolean>();
                ChamberPositions classifier=new ChamberPositions(); int parts=0,controllers=0;
                for(var aisle:grid) for(var row:aisle) for(var predicate:row) {
                    if(predicate.isController) controllers++;
                    if(unique.computeIfAbsent(predicate,classifier::isChamber)) parts++;
                }
                int[] required=expected.get(id);
                check(grid[0][0].length==required[0] && grid[0].length==required[1] && grid.length==required[2],
                        "Real requested dimensions: "+id);
                check(controllers==1,"One controller in runtime pattern: "+id);
                check(parts==required[3],"Exact real chamber candidate count: "+id);
                patterns.put(id,Map.of("size",List.of(grid[0][0].length,grid[0].length,grid.length),"chamberPositions",parts,"uniquePredicates",unique.size()));
            }
            result.put("patterns",patterns);
            footprintHeight();
            result.put("cloudIntegration", CloudIntegrationReviewChecks.run(this::check));
            ChamberPreviewUiChecks.run(this::check);
            result.put("chamberPreviewUi", "PASS");
            result.put("status","PASS");
        } catch(Throwable failure) { result.put("status","FAIL");result.put("error",failure.toString());failure.printStackTrace(); }
        result.put("checks",checks);result.put("worldOpened",false);
        result.put("visualScreenshotVerified",false);
        result.put("scope","Title-screen transform, real palette/part candidates, inert factories, wireless selection and header-height logic; no world interaction or visual screenshot");
        try { Files.writeString(directory.resolve("chamber-runtime.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result),StandardCharsets.UTF_8); }
        catch(Exception failure) { failure.printStackTrace(); }
        mc.stop();
    }
    private void footprintHeight() throws Exception {
        var wrapper=(com.gtl.enhancedcore.client.preview.LazyMultiblockPreviewWidget)
                com.gtl.enhancedcore.client.preview.LazyMultiblockPreviewWidget.create(null);
        var footprint=wrapper.getClass().getDeclaredField("footprintText");footprint.setAccessible(true);
        var widget=new com.lowdragmc.lowdraglib.gui.widget.WidgetGroup(0,0,160,160);wrapper.addWidget(widget);
        var controls=new org.gtlcore.gtlcore.client.preview.PreviewControls(widget,null,()->5,
                ()->new org.gtlcore.gtlcore.client.preview.PreviewHeight.Dimensions(5,5,5));
        var fullscreen=controls.getClass().getDeclaredField("fullscreen");fullscreen.setAccessible(true);fullscreen.setBoolean(controls,true);
        var width=controls.getClass().getDeclaredField("width");width.setAccessible(true);width.setInt(controls,320);
        int base=controls.headerHeight();
        footprint.set(wrapper,"Memory footprint line one and a deliberately longer second part for wrapping");
        int extra=Minecraft.getInstance().font.split(net.minecraft.network.chat.Component.literal(wrapper.footprintText()),280).size()
                *Minecraft.getInstance().font.lineHeight;
        check(controls.headerHeight()==base+extra && extra>0,"Fullscreen header reserves every footprint line above native height/dimensions");
    }
    private void merged(String name,String... suffixes) throws Exception {
        Class<?> type=Class.forName(name);
        for(String suffix:suffixes) check(java.util.Arrays.stream(type.getDeclaredMethods()).anyMatch(m->m.getName().contains(suffix)),
                "Merged method "+name+"#"+suffix);
    }
    private void check(boolean condition,String name) { if(!condition) throw new AssertionError(name);checks.add(name); }
}
