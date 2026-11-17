package com.gtl.enhancedcore.audit;

import appeng.api.stacks.AEItemKey;
import com.google.gson.GsonBuilder;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.CWURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gtl.enhancedcore.common.machine.galaxy.GalaxyPower;
import com.gtl.enhancedcore.common.recipe.GalaxyNativeRecipePreparation;
import com.gtl.enhancedcore.common.recipe.GalaxyRecipeBatch;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.api.recipe.GTRecipeSerializer;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import com.gtl.enhancedcore.common.data.machine.HydrogenPeroxideRecipeLoader;
import com.gtl.enhancedcore.common.machine.galaxy.GalaxyLinks;
import com.gtl.enhancedcore.common.recipe.iv.*;
import com.hepdd.gtmthings.api.misc.WirelessEnergyManager;
import com.hepdd.gtmthings.data.WirelessEnergySavaedData;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;

/** One Forge title-screen check: production recipe loader and registered isolated controllers. */
@Mod("enhancedcore_audit")
public final class PlayerRecipePowerChecks {
    private final List<String> checks=new ArrayList<>();
    private final Map<String,Object> evidence=new LinkedHashMap<>();
    private boolean done;
    public PlayerRecipePowerChecks() {MinecraftForge.EVENT_BUS.addListener(this::tick);}
    private void tick(TickEvent.ClientTickEvent event) {
        if(done || event.phase!=TickEvent.Phase.END || !Boolean.getBoolean("gtl.playerRecipePower.audit")) return;
        var mc=Minecraft.getInstance();if(mc.getOverlay()!=null || mc.screen==null) return;done=true;
        Path directory=mc.gameDirectory.toPath().toAbsolutePath().normalize();
        var result=new LinkedHashMap<String,Object>();
        try {
            check(directory.getParent().equals(Path.of("C:/IDEA/GTL-Enhancedcore/_audit/player-recipe-power-20261117"))
                    && Files.isRegularFile(directory.resolve("player-recipe-power.marker"))
                    && mc.level==null && mc.getSingleplayerServer()==null,"Marked title-screen fixture; no player world");
            peroxide();power();result.put("status","PASS");
        } catch(Throwable error) {error.printStackTrace();result.put("status","FAIL");result.put("error",error.toString());}
        result.put("checks",checks);result.put("evidence",evidence);
        result.put("scope","Actual Forge registrations/Mixins, production peroxide loader and real native prepare/advance; synthetic loaded parts/link/payment account. No player world, structure formation or GUI performance measurement.");
        try {Files.writeString(directory.resolve("player-recipe-power.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));}
        catch(Exception error) {error.printStackTrace();}mc.stop();
    }
    private void peroxide() {
        List<FinishedRecipe> emitted=new ArrayList<>();HydrogenPeroxideRecipeLoader.registerRecipes(emitted::add);
        var bulk=emitted.stream().filter(r->r.getId().getPath().endsWith("hydrogen_peroxide_bulk")).findFirst().orElseThrow();
        var recipe=GTRecipeSerializer.SERIALIZER.fromJson(bulk.getId(),bulk.serializeRecipe());
        boolean circuit=false;
        var selected=GTItems.INTEGRATED_CIRCUIT.asStack();selected.getOrCreateTag().putInt("Configuration",24);
        var different=selected.copy();different.getOrCreateTag().putInt("Configuration",23);
        for(var content:recipe.inputs.getOrDefault(ItemRecipeCapability.CAP,List.of())) {
            var ingredient=(Ingredient)content.content;
            if(ingredient.test(selected)) {
                circuit=true;check(content.chance==0,"Production bulk peroxide circuit 24 is nonconsumable");
                check(!ingredient.test(different),"Actual circuit ingredient accepts 24 and rejects 23");
            }
        }
        check(circuit,"Production bulk peroxide retains circuit 24 selector");
        evidence.put("peroxide",bulk.serializeRecipe());
    }
    private void power() throws Exception {
        var previousData=WirelessEnergySavaedData.INSTANCE;
        var stat=Class.forName("com.xingmot.gtmadvancedhatch.integration.gtmt.newmonitor.EnergyStat").getDeclaredField("server");
        stat.setAccessible(true);Object previousServer=stat.get(null);
        var field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);
        stat.set(null,((sun.misc.Unsafe)field.get(null)).allocateInstance(net.minecraft.client.server.IntegratedServer.class));
        WirelessEnergySavaedData.INSTANCE=new WirelessEnergySavaedData(null);
        try {
            for(String id:List.of("gtceu:mage_assembler","gtceu:dimensionally_transcendent_mixer")) isolated(id);
            ordinary();
        } finally {stat.set(null,previousServer);WirelessEnergySavaedData.INSTANCE=previousData;}
    }
    private void ordinary() throws Exception {
        var f=StellarIvGuiChecks.fixture("gtl_enhancedcore:platinum_refining_matrix",false,false,true);
        var machine=f.machine();
        check(machine.getRecipeLogic().getClass()==com.gregtechceu.gtceu.api.machine.trait.RecipeLogic.class,
                "Ordinary candidate retains exact registered RecipeLogic class");
        UUID owner=UUID.randomUUID();set(GalaxyLinks.linkedStation(machine),"userId",owner);
        WirelessEnergyManager.setUserEU(owner,BigInteger.TEN.pow(72));
        var recipe=GTRecipeBuilder.of(new ResourceLocation("gtl_enhancedcore","ordinary_power_probe"),machine.getRecipeType())
                .EUt(GTValues.V[GTValues.MAX]).duration(400).buildRawRecipe();
        var build=GalaxyRecipeBatch.class.getDeclaredMethod("build",WorkableElectricMultiblockMachine.class,List.class,boolean.class,int.class);
        build.setAccessible(true);
        var projected=(GTRecipe)build.invoke(null,machine,List.of(recipe),false,GalaxyNativeRecipePreparation.minimumDuration(recipe));
        BigInteger expected=BigInteger.valueOf(RecipeHelper.getInputEUt(recipe)).multiply(BigInteger.valueOf(recipe.duration));
        check(projected!=null && projected.duration==20 && new BigInteger(projected.data.getString(GalaxyPower.LEFT_TAG)).equals(expected),
                "Ordinary real batch builder projects long single candidate to 20 ticks with unchanged total EU");
        recipe.duration=4;
        var shortRecipe=(GTRecipe)build.invoke(null,machine,List.of(recipe),false,GalaxyNativeRecipePreparation.minimumDuration(recipe));
        check(shortRecipe!=null && shortRecipe.duration==4,"Ordinary real batch builder retains faster native four-tick recipe");
        var research=recipe.copy();research.data=recipe.data.copy();research.data.putBoolean("duration_is_total_cwu",true);
        check(!GalaxyNativeRecipePreparation.energyOnly(research),"Total CWU is excluded from energy-only projection");
        var cwu=recipe.copy();cwu.tickInputs.put(CWURecipeCapability.CAP,List.of(new Content(1,10000,10000,0,null,null)));
        check(!GalaxyNativeRecipePreparation.energyOnly(cwu),"CWU tick input is excluded from energy-only projection");
        var output=recipe.copy();output.tickOutputs.put(EURecipeCapability.CAP,List.of(new Content(1L,10000,10000,0,null,null)));
        check(!GalaxyNativeRecipePreparation.energyOnly(output),"Tick output is excluded from energy-only projection");
        evidence.put("ordinary",Map.of("duration",projected.duration,"totalEU",expected.toString(),"shortTicks",shortRecipe.duration));
    }
    private void isolated(String id) throws Exception {
        var f=StellarIvGuiChecks.fixture(id,false,false,true);var machine=f.machine();var logic=machine.getRecipeLogic();
        check(GalaxyLinks.unlimited(machine) && IvMachineScope.crossRecipeEnabled(machine)
                && !IvMachineScope.batching(machine) && IvMachineScope.modifierThreads(machine)==0,id+" real isolation with 64 arrays and no omega");
        UUID owner=UUID.randomUUID();set(GalaxyLinks.linkedStation(machine),"userId",owner);
        BigInteger initial=BigInteger.TEN.pow(72);WirelessEnergyManager.setUserEU(owner,initial);
        var input=AEItemKey.of(Items.APPLE);
        var recipe=GTRecipeBuilder.of(new ResourceLocation("gtl_enhancedcore","power_single_probe"),machine.getRecipeType())
                .inputItems(Items.APPLE).outputItems(Items.GOLD_NUGGET).EUt(GTValues.V[GTValues.MAX]).duration(20000000).buildRawRecipe();
        var nativeRecipe=new IvNativeMatchContext(machine,Map.of(input,1L),Map.of(),1,1).prepare(recipe);
        check(nativeRecipe!=null && nativeRecipe.duration>20,id+" native single-copy timing reproduces long duration");
        BigInteger expected=BigInteger.valueOf(RecipeHelper.getInputEUt(nativeRecipe)).multiply(BigInteger.valueOf(nativeRecipe.duration));
        var job=new IvJob(0,recipe,new CompoundTag(),Map.of(input,1L),Map.of(),1);
        var buffer=(MEPatternBufferPartMachine)f.holders().get(1).getMetaMachine();
        var engine=((IvNativeAccess)logic).iv$engine();
        Class<?> refType=Class.forName(IvNativeEngine.class.getName()+"$Ref");
        var constructor=refType.getDeclaredConstructor(MEPatternBufferPartMachine.class,IvJob.class);constructor.setAccessible(true);
        var ref=constructor.newInstance(buffer,job);
        var prepare=IvNativeEngine.class.getDeclaredMethod("prepare",refType,long.class,long.class,String.class);prepare.setAccessible(true);
        prepare.invoke(engine,ref,1L,1L,"player-power");
        check(job.active() && !job.halted && job.duration==20,id+" high-balance single operation uses 20 ticks");
        check(job.energyTotal.equals(expected) && job.nativeWireless && job.inventory.isEmpty() && job.remaining==0,
                id+" preserves modified total EU and consumes one input once");
        var advance=IvNativeEngine.class.getDeclaredMethod("advance",List.class);advance.setAccessible(true);
        for(int tick=1;tick<=20;tick++) {
            advance.invoke(engine,List.of(ref));
            check(!job.halted && (tick<20?job.active():!job.active()),id+" paid progress/completion tick "+tick);
        }
        check(WirelessEnergyManager.getUserEU(owner).equals(initial.subtract(expected)),id+" cumulative payment equals exact original total EU");
        check(job.energyLeft.signum()==0 && job.completedOperations==1 && job.pending.values().stream().mapToLong(Long::longValue).sum()==1,
                id+" completes exactly one output and one operation");
        evidence.put(id,Map.of("controller",machine.getClass().getName(),"logic",logic.getClass().getName(),
                "nativeTicks",nativeRecipe.duration,"projectedTicks",20,"totalEU",expected.toString(),"paidEU",initial.subtract(WirelessEnergyManager.getUserEU(owner)).toString()));
    }
    private static void set(Object target,String name,Object value) throws Exception {
        Class<?> type=target.getClass();
        while(type!=null) {try {Field f=type.getDeclaredField(name);f.setAccessible(true);f.set(target,value);return;}catch(NoSuchFieldException ignored){type=type.getSuperclass();}}
        throw new NoSuchFieldException(name);
    }
    private void check(boolean value,String message) {if(!value) throw new AssertionError(message);checks.add(message);}
}
