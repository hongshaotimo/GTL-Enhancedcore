package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.recipe.lookup.GTRecipeLookup;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import com.gtl.enhancedcore.common.machine.galaxy.*;
import com.gtl.enhancedcore.common.recipe.GalaxyIndependentRecipeEngine;
import com.hepdd.gtmthings.api.misc.WirelessEnergyManager;
import com.hepdd.gtmthings.data.WirelessEnergySavaedData;
import java.lang.reflect.*;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import org.gtlcore.gtlcore.api.machine.trait.IBatchMachine;
import org.gtlcore.gtlcore.api.machine.trait.RecipeHandlePart;
import org.gtlcore.gtlcore.api.recipe.IGTRecipe;

/** One real subscribed-tick handoff reproduction; synthetic world and loaded connection only. */
@Mod("enhancedcore_audit")
public final class StellarWakeupChecks {
    private final List<String> checks=new ArrayList<>();
    private final Map<String,Object> evidence=new LinkedHashMap<>();
    private boolean done;
    public StellarWakeupChecks() {MinecraftForge.EVENT_BUS.addListener(this::tick);}
    private void tick(TickEvent.ClientTickEvent event) {
        if(done || event.phase!=TickEvent.Phase.END || !Boolean.getBoolean("gtl.stellarWakeup.audit")) return;
        var mc=Minecraft.getInstance();if(mc.getOverlay()!=null || mc.screen==null) return;done=true;
        var directory=mc.gameDirectory.toPath().toAbsolutePath().normalize();var result=new LinkedHashMap<String,Object>();
        try {
            check(directory.getParent().equals(Path.of("C:/IDEA/GTL-Enhancedcore/_audit/stellar-wakeup-20261117"))
                    && Files.isRegularFile(directory.resolve("stellar-wakeup.marker")) && mc.level==null && mc.getSingleplayerServer()==null,
                    "Marked title-screen fixture; no player world");
            run();result.put("status","PASS");
        } catch(Throwable error) {error.printStackTrace();result.put("status","FAIL");result.put("error",error.toString());}
        result.put("checks",checks);result.put("evidence",evidence);
        result.put("scope",Boolean.getBoolean("gtl.stellarWakeup.recipeReview")
                ? "Production Mixins with real pattern-buffer slot payment, deterministic parallel chance, registered candidate rejection through an injected ADD admission predicate and failing input endpoint, registered controller IV-mode ownership with one injected paid Job, native-to-stellar subscribed tick handoff, and separately injected old cancelled-subscription recovery. Synthetic world and loaded-link resolution. No player world, full structure formation, cryotheum production or AE CPU tested."
                : "Registered lucid controller and real MetaMachine.executeTick dispatch. Natural handoff uses normal slot insertion without forced unsubscribe, direct RecipeLogic.serverTick or working toggle. Controlled recovery, when selected, separately injects an old cancelled antientropy subscription. Synthetic world and loaded-link resolution; no player world or full production network.");
        try {Files.writeString(directory.resolve("stellar-wakeup.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));}
        catch(Exception error) {error.printStackTrace();}mc.stop();
    }
    private void run() throws Exception {
        var previousData=WirelessEnergySavaedData.INSTANCE;
        var stats=Class.forName("com.xingmot.gtmadvancedhatch.integration.gtmt.newmonitor.EnergyStat").getDeclaredField("server");stats.setAccessible(true);
        Object previousServer=stats.get(null);var uf=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");uf.setAccessible(true);
        stats.set(null,((sun.misc.Unsafe)uf.get(null)).allocateInstance(net.minecraft.client.server.IntegratedServer.class));
        WirelessEnergySavaedData.INSTANCE=new WirelessEnergySavaedData(null);
        try {
            if(Boolean.getBoolean("gtl.stellarWakeup.recipeReview")) {
                var f=StellarIvGuiChecks.fixture("gtladditions:lucid_etchdreamer",false,false,true);
                try {
                    var selector=StellarSelectorChecks.probe(f.machine().holder);
                    evidence.put("selectorPayment",selector);
                    StellarSelectorChecks.verify(selector);
                    checks.add("Uncached pattern input simulation and payment select the same circuit slot");
                } finally {f.holders().forEach(holder->holder.setLevel(null));}
                evidence.put("probabilityInput",StellarInputRollChecks.run());
                evidence.put("probabilityAdmission",StellarChanceAdmissionChecks.run());
                evidence.put("ownedWork",StellarOwnershipChecks.run());
                handoff();
                controlledRecovery();
            } else if(Boolean.getBoolean("gtl.stellarWakeup.controlledRecovery")) controlledRecovery();
            else handoff();
        } finally {stats.set(null,previousServer);WirelessEnergySavaedData.INSTANCE=previousData;}
    }
    private void controlledRecovery() throws Exception {
        var f=StellarIvGuiChecks.fixture("gtladditions:antientropy_condensation_center",false,false,true);
        var machine=f.machine();var logic=machine.getRecipeLogic();
        var originalTarget=(GalaxyConnectionHatch)f.holders().get(2).getMetaMachine();
        var target=new LoadedTarget(originalTarget.holder,GalaxyLinks.linkedStation(machine));
        try {
            // Only endpoint resolution is synthetic. The target constructor, onLoad poll and
            // controller position lookup use production methods and the real registered holder.
            ((com.gtl.enhancedcore.mixin.gtceu.MultiblockPartControllerPositionsAccessor)(Object)target)
                    .enhancedcore$getControllerPositions().add(machine.getPos());
            set(machine,"parts",new ArrayList<>(List.of(target)));set(machine,"partPositions",new BlockPos[]{target.getPos()});
            check(GalaxyLinks.controllers(target).contains(machine),"controlledRecovery target resolves registered controller through loaded dummy world");
            check(!GalaxyIndependentRecipeEngine.supports(logic),"controlledRecovery preserves special antientropy native trait exclusion");
            target.onLoad();dispatch(target,5);
            check(subscribed(machine),"controlledRecovery initial active-link poll establishes native subscription");
            var before=snapshot(machine);
            var previous=(TickableSubscription)get(logic,"subscription");
            // Explicitly model a saved session already stranded before this release.
            previous.unsubscribe();set(logic,"recipeDirty",false);
            dispatch(target,10);
            check(subscribed(machine),"controlledRecovery stable active target poll restores deliberately cancelled subscription");
            check(!(boolean)get(logic,"recipeDirty"),"controlledRecovery stable link leaves native recipe-dirty flag unchanged");
            evidence.put("controlledRecovery",Map.of("injected",true,"injection","TickableSubscription.unsubscribe only; no recipe execution",
                    "linkResolutionSeam",LoadedTarget.class.getName(),"logicClass",logic.getClass().getName(),
                    "beforeInjection",before,"afterStablePoll",snapshot(machine),"specialCryotheumPaymentExecuted",false));
        } finally {target.onUnload();f.holders().forEach(holder->holder.setLevel(null));}
    }
    private static final class LoadedTarget extends GalaxyConnectionHatch {
        private final StellarVortexSpaceStationMachine station;
        private LoadedTarget(com.gregtechceu.gtceu.api.machine.IMachineBlockEntity holder,StellarVortexSpaceStationMachine station) {
            super(holder,false);this.station=station;
        }
        @Override public boolean linkActive() {return true;}
        @Override public StellarVortexSpaceStationMachine sourceStation() {return station;}
    }
    private void handoff() throws Exception {
        var f=StellarIvGuiChecks.fixture("gtladditions:lucid_etchdreamer",false,false,true);
        var machine=f.machine();var logic=machine.getRecipeLogic();var type=machine.getRecipeType();var previousLookup=type.getLookup();
        try {
            var parts=machine.getParts().stream().filter(GalaxyConnectionHatch.class::isInstance).toList();
            set(machine,"parts",new ArrayList<>(parts));set(machine,"partPositions",parts.stream().map(p->p.self().getPos()).toArray(BlockPos[]::new));
            ((IBatchMachine)machine).setBatchEnabled(false);
            check(GalaxyIndependentRecipeEngine.supports(logic),"Registered lucid trait supports non-isolated independent execution");
            Object probe=parts.stream().filter(p->p.getClass().getSimpleName().equals("LinkProbe")).findFirst().orElseThrow();
            UUID owner=UUID.randomUUID();set(GalaxyLinks.linkedStation(machine),"userId",owner);WirelessEnergyManager.setUserEU(owner,BigInteger.TEN.pow(30));
            set(probe,"active",false);
            var input=new NotifiableItemStackHandler(machine,4,IO.IN);var output=new NotifiableItemStackHandler(machine,4,IO.OUT);
            machine.getCapabilitiesProxy().clear();machine.getCapabilitiesProxy().put(IO.IN,ItemRecipeCapability.CAP,List.of(input));
            machine.getCapabilitiesProxy().put(IO.OUT,ItemRecipeCapability.CAP,List.of(output));
            set(machine,"normalCapabilities",new EnumMap<IO,List<RecipeHandlePart>>(Map.of(IO.IN,List.of(RecipeHandlePart.of(IO.IN,List.of(input))),IO.OUT,List.of(RecipeHandlePart.of(IO.OUT,List.of(output))))));
            set(machine,"sharedInputRecipeHandlePart",RecipeHandlePart.of(IO.IN,List.of(input)));
            var recipe=GTRecipeBuilder.of(new ResourceLocation("gtl_enhancedcore","stellar_wakeup_probe"),type)
                    .inputItems(Items.APPLE).outputItems(Items.GOLD_NUGGET).EUt(0).duration(4).buildRawRecipe();
            IGTRecipe.of(recipe).setBatchProcessed(true);
            var lookup=new GTRecipeLookup(type);set(type,"lookup",lookup);check(lookup.addRecipe(recipe),"One deterministic recipe registered in real lookup");
            input.setStackInSlot(0,new ItemStack(Items.APPLE));
            logic.setupRecipe(recipe);
            check(logic.isActive() && input.getStackInSlot(0).isEmpty(),"Real native setup consumes first input before link activation");
            set(probe,"active",true);
            check(GalaxyLinks.unlimited(machine),"Connection becomes active while original paid native batch is running");
            evidence.put("logicClass",logic.getClass().getName());evidence.put("beforeCompletion",snapshot(machine));
            for(int tick=1;tick<=100;tick++) dispatch(machine,tick);
            evidence.put("afterCompletion",snapshot(machine));
            check(count(output,Items.GOLD_NUGGET)==1,"Real subscription scheduler completes native paid batch once");
            boolean handoffSubscribed=subscribed(machine);evidence.put("naturalHandoffSubscribed",handoffSubscribed);
            for(int tick=101;tick<=140;tick++) dispatch(machine,tick);
            evidence.put("afterEmptyIdle",snapshot(machine));
            input.setStackInSlot(0,new ItemStack(Items.APPLE));
            evidence.put("afterNormalInsertion",snapshot(machine));
            for(int tick=141;tick<=200;tick++) dispatch(machine,tick);
            evidence.put("afterDeliveryWindow",snapshot(machine));evidence.put("outputs",count(output,Items.GOLD_NUGGET));
            check(handoffSubscribed,"Native completion keeps an actual scheduled active subscription");
            check(input.getStackInSlot(0).isEmpty() && count(output,Items.GOLD_NUGGET)==2,
                    "Later normal slot insertion restarts and completes through subscriptions without a working toggle");
        } finally {set(type,"lookup",previousLookup);f.holders().forEach(holder->holder.setLevel(null));}
    }
    private static long count(NotifiableItemStackHandler inventory,Item item) {
        long total=0;for(int slot=0;slot<inventory.getSlots();slot++) if(inventory.getStackInSlot(slot).is(item)) total+=inventory.getStackInSlot(slot).getCount();return total;
    }
    private static void dispatch(MetaMachine machine,long tick) throws Exception {
        // Dummy world's game time is fixed; only advance the fixture holder's clock.
        field(machine.holder,"offset").setLong(machine.holder,tick-machine.getLevel().getGameTime());
        var method=MetaMachine.class.getDeclaredMethod("executeTick");method.setAccessible(true);method.invoke(machine);
    }
    @SuppressWarnings("unchecked") private static boolean subscribed(MetaMachine machine) throws Exception {
        var subscription=(TickableSubscription)get(((com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine)machine).getRecipeLogic(),"subscription");
        return subscription!=null && subscription.isStillSubscribed()
                && (((List<TickableSubscription>)get(machine,"serverTicks")).contains(subscription)
                || ((List<TickableSubscription>)get(machine,"waitingToAdd")).contains(subscription));
    }
    private static Map<String,Object> snapshot(MetaMachine machine) throws Exception {
        var logic=((com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine)machine).getRecipeLogic();
        var subscription=(TickableSubscription)get(logic,"subscription");
        var result=new LinkedHashMap<String,Object>(Map.of("status",logic.getStatus().name(),"duration",logic.getDuration(),"progress",logic.getProgress(),
                "subscriptionPresent",subscription!=null,"stillSubscribed",subscription!=null && subscription.isStillSubscribed(),
                "inRealScheduler",subscribed(machine),"serverTicks",((List<?>)get(machine,"serverTicks")).size(),
                "waitingToAdd",((List<?>)get(machine,"waitingToAdd")).size()));
        result.put("lastRecipePresent",logic.getLastRecipe()!=null);
        result.put("suspended",logic.isSuspend());
        result.put("independentJobs",((com.gtl.enhancedcore.common.recipe.GalaxyIndependentRecipeAccess)logic).galaxy$hasIndependentJobs());
        if(subscription!=null) {
            Object runnable=get(subscription,"runnable");result.put("runnableClass",runnable.getClass().getName());
            var captures=new ArrayList<String>();for(var capture:runnable.getClass().getDeclaredFields()) {capture.setAccessible(true);Object value=capture.get(runnable);captures.add(capture.getName()+"="+(value==null?"null":value.getClass().getName()));}
            result.put("runnableCaptures",captures);
        }
        return result;
    }
    private static Object get(Object owner,String name) throws Exception {return field(owner,name).get(owner);}
    private static void set(Object owner,String name,Object value) throws Exception {field(owner,name).set(owner,value);}
    private static Field field(Object owner,String name) throws Exception {
        for(Class<?> type=owner.getClass();type!=null;type=type.getSuperclass()) try {var f=type.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(name);
    }
    private void check(boolean value,String message) {if(!value) throw new AssertionError(message);checks.add(message);}
}
