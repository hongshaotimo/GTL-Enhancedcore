package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.recipe.*;
import com.gregtechceu.gtceu.common.block.CoilBlock;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import com.gtl.enhancedcore.common.machine.galaxy.*;
import com.gtl.enhancedcore.common.recipe.*;
import com.hepdd.gtmthings.api.misc.WirelessEnergyManager;
import com.hepdd.gtmthings.data.WirelessEnergySavaedData;
import com.lowdragmc.lowdraglib.gui.modular.*;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import org.gtlcore.gtlcore.api.machine.trait.IBatchMachine;
import org.gtlcore.gtlcore.api.recipe.IGTRecipe;
import org.lwjgl.glfw.GLFW;

/** One title-screen reproduction using the registered distorter and transformed RecipeLogic. */
@Mod("enhancedcore_audit")
public final class StellarIndependentChecks {
    private static final int[][] SIZES = {{960,600,2},{1280,720,3},{640,480,2}};
    private final List<String> checks = new ArrayList<>();
    private final List<String> screenshots = new ArrayList<>();
    private StellarIvGuiChecks.Fixture fixture;
    private WirelessEnergySavaedData previousData;
    private Object previousServer;
    private Field statistics;
    private Path directory;
    private AuditScreen screen;
    private int viewport, frames, stable;
    private boolean done, resizing;

    public StellarIndependentChecks() {
        MinecraftForge.EVENT_BUS.addListener(this::tick);
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }
    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.END && screen != null
                && Minecraft.getInstance().screen == screen) frames++;
    }
    private void tick(TickEvent.ClientTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END || !Boolean.getBoolean("gtl.stellarIndependent.audit")) return;
        var mc = Minecraft.getInstance();
        if (mc.getOverlay() != null || mc.screen == null) return;
        try {
            if (directory == null) {
                directory = mc.gameDirectory.toPath().toAbsolutePath().normalize();
                require(directory.getParent().equals(Path.of("C:/IDEA/GTL-Enhancedcore/_audit/stellar-independent-20261112"))
                        && Files.isRegularFile(directory.resolve("stellar-independent.marker")), "Explicit task fixture");
                require(mc.level == null && mc.player == null && mc.getSingleplayerServer() == null, "No player world opened");
                checks.addAll(StellarCommonChecks.run());
                setup();
            }
            if (screen == null) {
                var size = SIZES[viewport];
                if (!resizing) {
                    mc.setScreen(new TitleScreen()); mc.options.guiScale().set(size[2]);
                    mc.options.forceUnicodeFont().set(false);
                    GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),size[0],size[1]); mc.resizeDisplay();
                    resizing = true; stable = 0; return;
                }
                if (mc.getWindow().getWidth() != size[0] || mc.getWindow().getHeight() != size[1]) {
                    mc.resizeDisplay(); stable = 0; return;
                }
                if (++stable < 2) return;
                screen = new AuditScreen(); mc.setScreen(screen); frames = 0; return;
            }
            if (frames < 12) return;
            require(screen.draws > 0, "Production ModularUI rendered at " + Arrays.toString(SIZES[viewport]));
            var imagePath = "stellar-independent-" + viewport + ".png";
            try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(directory.resolve(imagePath)); }
            screenshots.add(imagePath); mc.setScreen(new TitleScreen()); screen = null; resizing = false;
            if (++viewport == SIZES.length) finish(null);
        } catch (Throwable failure) { failure.printStackTrace(); finish(failure); }
    }

    private void setup() throws Exception {
        previousData = WirelessEnergySavaedData.INSTANCE;
        WirelessEnergySavaedData.INSTANCE = new WirelessEnergySavaedData(null);
        statistics = Class.forName("com.xingmot.gtmadvancedhatch.integration.gtmt.newmonitor.EnergyStat").getDeclaredField("server");
        statistics.setAccessible(true); previousServer = statistics.get(null);
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        statistics.set(null, ((sun.misc.Unsafe)field.get(null)).allocateInstance(net.minecraft.client.server.IntegratedServer.class));
        fixture = StellarIvGuiChecks.fixture("gtl_enhancedcore:hyperstructural_chemical_distorter", false, false, true);
        var machine = fixture.machine(); var logic = machine.getRecipeLogic();
        var parts = machine.getParts().stream().filter(p -> p instanceof GalaxyConnectionHatch).toList();
        set(machine,"parts",new ArrayList<>(parts));
        set(machine,"partPositions",parts.stream().map(p -> p.self().getPos()).toArray(net.minecraft.core.BlockPos[]::new));
        set(machine,"coilType",CoilBlock.CoilType.TRITANIUM);
        ((IBatchMachine)machine).setBatchEnabled(false);
        require(GalaxyIndependentRecipeEngine.supports(logic) && GalaxyLinks.unlimited(machine), "Registered non-IV distorter resolves the 64-array source");
        UUID owner = UUID.randomUUID(); set(GalaxyLinks.linkedStation(machine),"userId",owner);
        BigInteger initial = BigInteger.TEN.pow(30); WirelessEnergyManager.setUserEU(owner,initial);
        var input = new NotifiableItemStackHandler(machine,8,IO.IN);
        var output = new NotifiableItemStackHandler(machine,8,IO.OUT);
        machine.getCapabilitiesProxy().put(IO.IN,ItemRecipeCapability.CAP,List.of(input));
        machine.getCapabilitiesProxy().put(IO.OUT,ItemRecipeCapability.CAP,List.of(output));
        set(machine,"normalCapabilities",new EnumMap<IO,List<org.gtlcore.gtlcore.api.machine.trait.RecipeHandlePart>>(Map.of(
                IO.IN,List.of(org.gtlcore.gtlcore.api.machine.trait.RecipeHandlePart.of(IO.IN,List.of(input))),
                IO.OUT,List.of(org.gtlcore.gtlcore.api.machine.trait.RecipeHandlePart.of(IO.OUT,List.of(output))))));
        var shortOrigin = GTRecipeBuilder.of(new ResourceLocation("gtl_enhancedcore","stellar_audit_short"),machine.getRecipeType())
                .inputItems(Items.APPLE).outputItems(Items.GOLD_NUGGET).EUt(0).duration(4).blastFurnaceTemp(2000).buildRawRecipe();
        var longOrigin = GTRecipeBuilder.of(new ResourceLocation("gtl_enhancedcore","stellar_audit_long"),machine.getRecipeType())
                .inputItems(Items.CARROT).outputItems(Items.IRON_NUGGET).EUt(0).duration(9).blastFurnaceTemp(3000).buildRawRecipe();
        var runners = machine.getRecipeType().getCustomRecipeLogicRunners();
        com.gregtechceu.gtceu.api.recipe.GTRecipeType.ICustomRecipeLogic shortRunner = holder -> shortOrigin;
        com.gregtechceu.gtceu.api.recipe.GTRecipeType.ICustomRecipeLogic longRunner = holder -> longOrigin;
        runners.add(shortRunner); runners.add(longRunner);
        input.setStackInSlot(0,new ItemStack(Items.APPLE,3)); input.setStackInSlot(1,new ItemStack(Items.CARROT,5));
        try {
            var prepared = GalaxyRecipeBatch.prepareIndependent(machine,logic,0,Set.of(),0,2,BigInteger.ZERO);
            require(prepared.size() == 2 && prepared.stream().map(p -> p.recipe().data.getInt("ebf_temp")).distinct().count() == 2,
                    "Real preparation admits distinct recipe temperatures together");
            require(input.getStackInSlot(0).getCount() == 3 && input.getStackInSlot(1).getCount() == 5,
                    "Preparation leaves inputs untouched");
        } finally { runners.remove(shortRunner); runners.remove(longRunner); }
        input.setStackInSlot(0,ItemStack.EMPTY); input.setStackInSlot(1,ItemStack.EMPTY);

        // Seed only paid native work. All tick payment/output and persistence run through production code.
        var engine = new GalaxyIndependentRecipeEngine(logic);
        var seed = new CompoundTag(); var jobs = new ListTag();
        jobs.add(job(shortOrigin,paid(shortOrigin,Items.GOLD_NUGGET,3,4,13),0));
        jobs.add(job(longOrigin,paid(longOrigin,Items.IRON_NUGGET,5,9,17),0));
        seed.put("jobs",jobs); seed.putBoolean("cycleActive",true); engine.load(seed);
        set(logic,"galaxy$independent",engine);
        var overheated=seed.copy(); var hotJobs=overheated.getList("jobs",Tag.TAG_COMPOUND);
        int unsupportedTemperature=CoilBlock.CoilType.TRITANIUM.getCoilTemperature()+1;
        for(int i=0;i<hotJobs.size();i++) {
            var entry=hotJobs.getCompound(i);
            var recipe=GTRecipeSerializer.CODEC.parse(NbtOps.INSTANCE,entry.get("recipe")).getOrThrow(false,ignored -> {});
            recipe.data.putInt("ebf_temp",unsupportedTemperature);
            entry.put("recipe",GTRecipeSerializer.CODEC.encodeStart(NbtOps.INSTANCE,recipe).getOrThrow(false,ignored -> {}));
        }
        engine.load(overheated); logic.serverTick();
        require(WirelessEnergyManager.getUserEU(owner).equals(initial)
                        && engine.save().getList("jobs",Tag.TAG_COMPOUND).stream()
                                .allMatch(entry -> ((CompoundTag)entry).getInt("elapsed")==0),
                "Insufficient coil temperature pauses both paid jobs without EU payment; " + state(logic,engine));
        engine.load(seed);
        logic.serverTick();
        require(WirelessEnergyManager.getUserEU(owner).equals(initial.subtract(BigInteger.valueOf(30))),
                "Two jobs deduct exactly one combined tick of real wireless EU; actualPaid="
                        + initial.subtract(WirelessEnergyManager.getUserEU(owner)) + "; " + state(logic,engine));
        require(SingleRecipeParallel.current(machine) == 8 && SingleRecipeParallel.galaxyRecipes(machine) == 2,
                "Production display reports eight operations in two recipes");
        var display = logic.getLastRecipe();
        var renderState = new CompoundTag(); logic.saveCustomPersistedData(renderState,false);
        for(int i=0;i<3;i++) logic.serverTick();
        require(count(output,Items.GOLD_NUGGET) == 3 && count(output,Items.IRON_NUGGET) == 0,
                "Short job outputs independently while long job remains active");
        var saved = new CompoundTag(); logic.saveCustomPersistedData(saved,false);
        require(saved.getCompound("gtleIndependentRecipes").getList("jobs",Tag.TAG_COMPOUND).size() == 1,
                "Persisted state keeps only the unfinished job");
        logic.loadCustomPersistedData(saved);
        for(int i=0;i<5;i++) logic.serverTick();
        require(count(output,Items.GOLD_NUGGET) == 3 && count(output,Items.IRON_NUGGET) == 5,
                "Reload resumes progress and delivers each frozen output once");
        require(WirelessEnergyManager.getUserEU(owner).equals(initial.subtract(BigInteger.valueOf(205))),
                "Total wireless payment equals 4x13 plus 9x17 EU; actualPaid="
                        + initial.subtract(WirelessEnergyManager.getUserEU(owner)) + "; " + state(logic,engine));
        logic.serverTick();
        require(count(output,Items.GOLD_NUGGET) == 3 && count(output,Items.IRON_NUGGET) == 5,
                "A further idle tick does not repeat output");
        var refillState = new CompoundTag(); var refillJobs = new ListTag();
        var partialLong = paid(longOrigin,Items.IRON_NUGGET,5,9,17);
        partialLong.data.putString(GalaxyPower.LEFT_TAG,"85");
        refillJobs.add(job(longOrigin,partialLong,4)); refillState.put("jobs",refillJobs); refillState.putBoolean("cycleActive",true);
        var freshEngine = new GalaxyIndependentRecipeEngine(logic); freshEngine.load(refillState);
        set(logic,"galaxy$independent",freshEngine);
        input.setStackInSlot(0,new ItemStack(Items.APPLE,2)); runners.add(shortRunner);
        try {
            logic.serverTick();
            require(freshEngine.save().getList("jobs",Tag.TAG_COMPOUND).size() == 2 && input.getStackInSlot(0).isEmpty(),
                    "A free recipe slot accepts new input while the long job continues");
        } finally { runners.remove(shortRunner); }
        logic.loadCustomPersistedData(renderState);
        ((com.gtl.enhancedcore.common.recipe.iv.IvNativeAccess)logic).iv$publish(new CompoundTag(),"",display,1,4,
                com.gregtechceu.gtceu.api.machine.trait.RecipeLogic.Status.WORKING);
    }
    private GTRecipe paid(GTRecipe origin,Item item,int amount,int duration,int eut) {
        var recipe = GTRecipeBuilder.of(origin.id,origin.recipeType).outputItems(new ItemStack(item,amount))
                .EUt(0).duration(duration).blastFurnaceTemp(origin.data.getInt("ebf_temp")).buildRawRecipe();
        recipe.data.putBoolean(GalaxyRecipeBatch.MARKER,true);
        recipe.data.putLong(GalaxyRecipeBatch.OPERATIONS_TAG,amount);
        recipe.data.putLong(GalaxyRecipeBatch.RECIPES_TAG,1);
        recipe.data.putString(GalaxyPower.EU_TAG,Integer.toString(eut));
        recipe.data.putString(GalaxyPower.LEFT_TAG,Integer.toString(eut*duration));
        IGTRecipe.of(recipe).setHasTick(true); IGTRecipe.of(recipe).setBatchProcessed(true);
        return recipe;
    }
    private CompoundTag job(GTRecipe origin,GTRecipe recipe,int elapsed) {
        var tag = new CompoundTag(); tag.put("origin",GTRecipeSerializer.CODEC.encodeStart(NbtOps.INSTANCE,origin).getOrThrow(false,ignored -> {}));
        tag.put("recipe",GTRecipeSerializer.CODEC.encodeStart(NbtOps.INSTANCE,recipe).getOrThrow(false,ignored -> {}));
        tag.putString("recipeId",recipe.id.toString()); tag.putString("originId",origin.id.toString());
        tag.putLong("capacity",1); tag.putInt("elapsed",elapsed); return tag;
    }
    private String state(com.gregtechceu.gtceu.api.machine.trait.RecipeLogic logic,GalaxyIndependentRecipeEngine engine) {
        var entries=engine.save().getList("jobs",Tag.TAG_COMPOUND); var jobs=new ArrayList<String>();
        for(int i=0;i<entries.size();i++) {
            var entry=entries.getCompound(i); var recipe=GTRecipeSerializer.CODEC.parse(NbtOps.INSTANCE,entry.get("recipe")).getOrThrow(false,ignored -> {});
            jobs.add(entry.getString("recipeId") + ":elapsed=" + entry.getInt("elapsed") + "/" + recipe.duration
                    + ",left=" + recipe.data.getString(GalaxyPower.LEFT_TAG) + ",eut=" + recipe.data.getString(GalaxyPower.EU_TAG)
                    + ",hasTick=" + recipe.hasTick());
        }
        return "status=" + logic.getStatus() + ",enabled=" + logic.isWorkingEnabled() + ",jobs=" + jobs
                + ",diagnostic=" + ((com.gtl.enhancedcore.common.recipe.iv.IvNativeAccess)logic).iv$summary();
    }
    private int count(NotifiableItemStackHandler output,Item item) {
        int total=0; for(int i=0;i<output.getSlots();i++) if(output.getStackInSlot(i).is(item)) total+=output.getStackInSlot(i).getCount(); return total;
    }
    private void finish(Throwable failure) {
        done=true; var mc=Minecraft.getInstance(); mc.setScreen(new TitleScreen());
        if(fixture != null) fixture.holders().forEach(holder -> holder.setLevel(null));
        if(directory != null) {
            if(previousData != null) WirelessEnergySavaedData.INSTANCE=previousData;
            try { if(statistics != null) statistics.set(null,previousServer); } catch(Exception ignored) {}
            var report=new LinkedHashMap<String,Object>(); report.put("status",failure==null ? "PASS":"FAIL");
            report.put("checks",checks); report.put("screenshots",screenshots); report.put("worldOpened",false);
            if(failure!=null) report.put("failure",failure.toString());
            try { Files.writeString(directory.resolve("stellar-independent.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
            catch(Exception error) { error.printStackTrace(); }
        }
        mc.stop();
    }
    private void require(boolean valid,String label) { if(!valid) throw new AssertionError(label); checks.add(label); }
    private static void set(Object object,String name,Object value) throws Exception {
        for(Class<?> type=object.getClass();type!=null;type=type.getSuperclass()) try {
            Field field=type.getDeclaredField(name); field.setAccessible(true); field.set(object,value); return;
        } catch(NoSuchFieldException ignored) {}
        throw new NoSuchFieldException(name);
    }
    private static final class MachineGui extends ModularUIGuiContainer {
        MachineGui(ModularUI ui) { super(ui,1); }
        void initialize(Minecraft mc) { minecraft=mc; font=mc.font; width=mc.getWindow().getGuiScaledWidth();
            height=mc.getWindow().getGuiScaledHeight(); super.init(); }
        void menuTick() { super.containerTick(); }
    }
    private final class AuditScreen extends Screen {
        final ModularUI ui; final MachineGui gui; int draws;
        AuditScreen() {
            super(Component.literal("Stellar independent processing"));
            ui=fixture.machine().createUI(fixture.player()); ui.initWidgets();
            var buffer=new FriendlyByteBuf(Unpooled.buffer());
            try { ui.mainGroup.writeInitialData(buffer); ui.mainGroup.readInitialData(buffer); }
            finally { buffer.release(); }
            fixture.player().containerMenu=ui.getModularUIContainer(); gui=new MachineGui(ui);
        }
        @Override protected void init() {
            gui.initialize(minecraft); ui.mainGroup.setUiAccess(new WidgetUIAccess() {
                public boolean attemptMergeStack(ItemStack stack,boolean a,boolean b) { return false; }
                public void writeUpdateInfo(Widget source,int action,Consumer<FriendlyByteBuf> writer) {}
                public void writeClientAction(Widget source,int action,Consumer<FriendlyByteBuf> writer) {}
            });
        }
        @Override public void tick() { gui.menuTick(); }
        @Override public void render(GuiGraphics graphics,int x,int y,float partial) {
            graphics.fill(0,0,width,height,0xff24313b); gui.renderWithTooltip(graphics,-100,-100,partial); draws++;
        }
    }
}
