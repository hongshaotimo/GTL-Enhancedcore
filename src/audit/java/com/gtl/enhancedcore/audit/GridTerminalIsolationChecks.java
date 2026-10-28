package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.misc.EnergyContainerList;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import com.gtl.enhancedcore.common.machine.TieredParallelMachine;
import com.gtl.enhancedcore.common.recipe.GridEnergyInputs;
import com.gtl.enhancedcore.common.recipe.GridEnergyRecipeInput;
import com.gtl.enhancedcore.common.recipe.iv.IvJob;
import com.gtl.enhancedcore.common.recipe.iv.IvNativeAccess;
import com.gtl.enhancedcore.common.recipe.iv.IvNativeEngine;
import com.gtl.enhancedcore.common.recipe.iv.IvPresentation;
import com.gtl.enhancedcore.common.recipe.iv.IvRecipeLogic;
import com.gtladd.gtladditions.common.machine.multiblock.part.WirelessEnergyNetworkTerminalPartMachineBase;
import com.gtladd.gtladditions.common.machine.trait.NetworkEnergyContainer;
import com.hepdd.gtmthings.api.misc.WirelessEnergyManager;
import com.hepdd.gtmthings.data.WirelessEnergySavaedData;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;

/** Real transformed ADD terminal and IV engine calls in synthetic levels, at a marked title screen. */
@Mod("enhancedcore_audit")
public final class GridTerminalIsolationChecks {
    private static final Path ROOT = Path.of("C:/IDEA/GTL-Enhancedcore/_work/grid-terminal-isolation-20261023")
            .toAbsolutePath().normalize();
    private static final BigInteger LONG = BigInteger.valueOf(Long.MAX_VALUE);
    private static final BigInteger SUPPLY = LONG.multiply(BigInteger.valueOf(16)).add(BigInteger.valueOf(12345));
    private boolean done;

    public GridTerminalIsolationChecks() { MinecraftForge.EVENT_BUS.addListener(this::tick); }

    private void tick(TickEvent.ClientTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END || !Boolean.getBoolean("gtl.enhancedcore.gridTerminalAudit")) return;
        var mc = Minecraft.getInstance();
        var directory = mc.gameDirectory.toPath().toAbsolutePath().normalize();
        if (mc.getOverlay() != null || !(mc.screen instanceof TitleScreen) || mc.level != null
                || mc.getSingleplayerServer() != null || !directory.startsWith(ROOT) || directory.equals(ROOT)
                || !Files.isRegularFile(directory.resolve("grid-terminal.marker"))) return;
        done = true;
        var checks = new ArrayList<String>();
        var report = new LinkedHashMap<String, Object>();
        try {
            run(checks);
            report.put("status", "PASS");
        } catch (Throwable failure) {
            report.put("status", "FAIL"); report.put("error", failure.toString()); failure.printStackTrace();
        }
        report.put("checks", checks);
        report.put("scope", "Real Forge Mixin, registered ADD input terminal, nested energy proxies and both real IV advance methods; synthetic levels only, no player world");
        try { Files.writeString(directory.resolve("grid-terminal-checks.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
        catch (Exception error) { error.printStackTrace(); }
        mc.stop();
    }

    public static List<String> run() throws Exception {
        var checks = new ArrayList<String>(); run(checks); return checks;
    }

    private static void run(List<String> checks) throws Exception {
        try (var statistics = new StatisticsContext()) {
        checks.add("Installed detached zero-tick statistics server and in-memory SavedData; no server thread, levels or persistence opened");
        for (String id : List.of("gtceu:suprachronal_assembly_line", "gtl_enhancedcore:plasma_machine_tool")) {
            var fixture = StellarIvGuiChecks.fixture(id, false, true, true);
            UUID owner = UUID.randomUUID();
            UUID other = UUID.randomUUID();
            BigInteger previous = WirelessEnergyManager.getUserEU(owner);
            BigInteger previousOther = WirelessEnergyManager.getUserEU(other);
            MetaMachineBlockEntity terminalHolder = null;
            try {
                var machine = fixture.machine();
                // Use the registered supplier, preserving every upstream and runtime Mixin layer.
                for (var entry : GTRegistries.MACHINES.entries()) {
                    if (!entry.getKey().getNamespace().equals("gtladditions")
                            || !entry.getKey().getPath().contains("network")
                            || !entry.getKey().getPath().contains("terminal")) continue;
                    var definition = entry.getValue();
                    var holder = (MetaMachineBlockEntity) definition.getBlockEntityType()
                            .create(new BlockPos(9, 80, 0), definition.defaultBlockState());
                    holder.setLevel(machine.getLevel());
                    if (holder.getMetaMachine() instanceof WirelessEnergyNetworkTerminalPartMachineBase terminal
                            && terminal.getEnergyContainer().getHandlerIO() == IO.IN) {
                        terminalHolder = holder; checks.add("registered terminal=" + entry.getKey()); break;
                    }
                    holder.setLevel(null);
                }
                require(terminalHolder != null, "Real registered ADD input terminal exists", checks);
                var terminal = (WirelessEnergyNetworkTerminalPartMachineBase) terminalHolder.getMetaMachine();
                NetworkEnergyContainer input = terminal.getEnergyContainer();
                require((Object) input instanceof GridEnergyRecipeInput,
                        "NetworkEnergyContainer implements runtime GridEnergyRecipeInput", checks);
                var grid = (GridEnergyRecipeInput) (Object) input;
                set(terminal, "uuid", null);
                require(!grid.gridEnergyConnected() && grid.gridEnergyAvailable().signum() == 0,
                        "Unbound terminal cannot advertise or spend a network balance", checks);
                WirelessEnergyManager.setUserEU(owner, SUPPLY);
                WirelessEnergyManager.setUserEU(other, BigInteger.valueOf(77));
                terminal.setUUID(owner);
                require(grid.gridEnergyConnected() && grid.gridEnergyAvailable().equals(SUPPLY),
                        "Bound UUID reads exact balance above Long.MAX_VALUE", checks);
                require(grid.gridEnergyUnboundedPower(), "ADD terminal advertises wide aggregate power to both IV engines", checks);
                var output = new NetworkEnergyContainer(terminal, IO.OUT);
                var outputGrid = (GridEnergyRecipeInput) (Object) output;
                require(!outputGrid.gridEnergyConnected() && outputGrid.gridEnergyAvailable().signum() == 0
                                && !outputGrid.gridEnergyTransfer(BigInteger.ONE.negate())
                                && GridEnergyInputs.findIn(List.of(new EnergyContainerList(List.of(output)))) == null
                                && grid.gridEnergyAvailable().equals(SUPPLY),
                        "Output terminal cannot be discovered or charged as a recipe input", checks);
                var inner = new EnergyContainerList(List.of(input));
                var aggregate = new EnergyContainerList(List.of(inner, input));
                machine.getCapabilitiesProxy().put(IO.IN, EURecipeCapability.CAP, List.of(input));
                set(machine, "energyContainer", aggregate);
                require(GridEnergyInputs.findIn(List.of(aggregate)) == grid && GridEnergyInputs.find(machine) == grid
                                && GridEnergyInputs.energyInputs(machine).size() == 1,
                        "Nested real EnergyContainerList discovers terminal and deduplicates physical handler", checks);
                require(GridEnergyInputs.power(input) == Long.MAX_VALUE && GridEnergyInputs.stored(machine) == Long.MAX_VALUE,
                        "Legacy long getters saturate while network ledger remains exact", checks);
                var request = new ArrayList<>(List.of(Long.MAX_VALUE, Long.MAX_VALUE));
                WirelessEnergyManager.setUserEU(owner, BigInteger.ZERO);
                require(input.handleRecipeInner(IO.IN, null, new ArrayList<>(request), null, true).equals(request),
                        "Empty ordinary terminal preserves the full debt above Long.MAX_VALUE for the next handler", checks);
                WirelessEnergyManager.setUserEU(owner, LONG);
                require(input.handleRecipeInner(IO.IN, null, new ArrayList<>(request), null, false).equals(List.of(Long.MAX_VALUE))
                                && grid.gridEnergyAvailable().signum() == 0,
                        "Partially funded ordinary terminal pays exactly its balance and preserves the entire residual debt", checks);
                WirelessEnergyManager.setUserEU(owner, SUPPLY);
                var simulated = input.handleRecipeInner(IO.IN, null, request, null, true);
                require(simulated == null || simulated.isEmpty(), "Ordinary recipe simulation accepts aggregate EU above long", checks);
                require(grid.gridEnergyAvailable().equals(SUPPLY), "Simulation leaves exact BigInteger ledger unchanged", checks);
                var executed = input.handleRecipeInner(IO.IN, null, new ArrayList<>(request), null, false);
                require((executed == null || executed.isEmpty()) && grid.gridEnergyAvailable().equals(SUPPLY.subtract(LONG.multiply(BigInteger.TWO))),
                        "Ordinary recipe execution deducts two long entries exactly once", checks);
                require(grid.gridEnergyTransfer(LONG.multiply(BigInteger.TWO)) && grid.gridEnergyAvailable().equals(SUPPLY),
                        "BigInteger refund restores exact balance", checks);
                long debit = Long.MAX_VALUE - 31;
                require(GridEnergyInputs.remove(aggregate, grid, debit) == debit
                                && grid.gridEnergyAvailable().equals(SUPPLY.subtract(BigInteger.valueOf(debit))),
                        "IV payment bridge deducts exact long request from wide network ledger", checks);
                require(GridEnergyInputs.refund(aggregate, grid, debit) == debit && grid.gridEnergyAvailable().equals(SUPPLY),
                        "IV refund bridge restores captured payment source", checks);
                terminal.setUUID(other);
                require(grid.gridEnergyAvailable().equals(BigInteger.valueOf(77)) && !grid.gridEnergyPay(78)
                                && WirelessEnergyManager.getUserEU(owner).equals(SUPPLY),
                        "UUID rebinding selects isolated balance; insufficient payment is atomic", checks);
                terminal.setUUID(owner);
                advance(fixture, debit, owner, checks);
            } finally {
                WirelessEnergyManager.setUserEU(owner, previous);
                WirelessEnergyManager.setUserEU(other, previousOther);
                if (terminalHolder != null) terminalHolder.setLevel(null);
                for (var holder : fixture.holders()) holder.setLevel(null);
            }
        }
        }
    }

    /**
     * GTMAdvancedHatch's real payment Mixin calls EnergyStat.update, whose only server
     * dependency is MinecraftServer.getTickCount(). Vanilla's method only reads tickCount.
     * Allocate a concrete server without invoking its constructor or starting it, so
     * the actual statistics and payment implementations execute unchanged at tick zero.
     */
    private static final class StatisticsContext implements AutoCloseable {
        private final Field serverField;
        private final Object previousServer;
        private final WirelessEnergySavaedData previousData;

        private StatisticsContext() throws Exception {
            var mc = Minecraft.getInstance();
            if (mc.level != null || mc.getSingleplayerServer() != null)
                throw new IllegalStateException("Statistics fixture requires a title screen without a live world/server");
            serverField = Class.forName("com.xingmot.gtmadvancedhatch.integration.gtmt.newmonitor.EnergyStat")
                    .getDeclaredField("server");
            serverField.setAccessible(true);
            previousServer = serverField.get(null);
            previousData = WirelessEnergySavaedData.INSTANCE;
            var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            var unsafe = (sun.misc.Unsafe) unsafeField.get(null);
            MinecraftServer clock = (MinecraftServer) unsafe.allocateInstance(IntegratedServer.class);
            if (clock.getTickCount() != 0) throw new AssertionError("Detached statistics clock must start at tick zero");
            // SavedData.setDirty only touches its own flag; no ServerLevel is needed or saved.
            var data = new WirelessEnergySavaedData(null);
            serverField.set(null, clock);
            WirelessEnergySavaedData.INSTANCE = data;
        }

        @Override public void close() throws IllegalAccessException {
            serverField.set(null, previousServer);
            WirelessEnergySavaedData.INSTANCE = previousData;
        }
    }

    private static void advance(StellarIvGuiChecks.Fixture fixture, long eut, UUID owner, List<String> checks) throws Exception {
        var machine = fixture.machine();
        var logic = machine.getRecipeLogic();
        set(machine, "tier", 14);
        logic.setWorkingEnabled(true);
        var buffer = (MEPatternBufferPartMachine) fixture.holders().get(1).getMetaMachine();
        var recipe = GTRecipeBuilder.of(new ResourceLocation("gtl_enhancedcore", "wide_terminal_audit"), machine.getRecipeType())
                .EUt(8).duration(10).buildRawRecipe();
        var jobs = new ArrayList<IvJob>();
        for (int i = 0; i < 2; i++) {
            var job = new IvJob(i, recipe.copy(), new CompoundTag(), Map.of(), Map.of(), 1);
            job.remaining = 0; job.parallel = 1; job.nativeCapacity = 1;
            job.eut = eut; job.duration = 10; job.energyLeft = BigInteger.valueOf(eut).multiply(BigInteger.TEN);
            job.energyTotal = job.energyLeft; job.supplyPower = Long.MAX_VALUE;
            // Native snapshots have already settled EU through their job ledger.
            job.nativeRecipe = recipe.copy(); job.nativeRecipe.tickInputs.remove(EURecipeCapability.CAP);
            job.validateAccounting(); jobs.add(job);
        }
        Object engine;
        if (logic instanceof IvRecipeLogic) {
            engine = logic; set(logic, "inputPower", Long.MAX_VALUE);
        } else engine = ((IvNativeAccess) logic).iv$engine();
        Class<?> engineType = engine instanceof IvRecipeLogic ? IvRecipeLogic.class : IvNativeEngine.class;
        Class<?> refType = Class.forName(engineType.getName() + "$Ref");
        var constructor = refType.getDeclaredConstructor(MEPatternBufferPartMachine.class, IvJob.class);
        constructor.setAccessible(true);
        var refs = new ArrayList<Object>();
        for (var job : jobs) refs.add(constructor.newInstance(buffer, job));
        var advance = engineType == IvRecipeLogic.class
                ? engineType.getDeclaredMethod("advance", List.class, TieredParallelMachine.class)
                : engineType.getDeclaredMethod("advance", List.class);
        advance.setAccessible(true);
        BigInteger before = WirelessEnergyManager.getUserEU(owner);
        if (engineType == IvRecipeLogic.class) advance.invoke(engine, refs, machine); else advance.invoke(engine, refs);
        BigInteger combined = BigInteger.valueOf(eut).multiply(BigInteger.TWO);
        require(combined.compareTo(LONG) > 0 && jobs.stream().allMatch(job -> job.elapsed == 1
                        && job.energyLeft.equals(BigInteger.valueOf(eut).multiply(BigInteger.valueOf(9))))
                        && WirelessEnergyManager.getUserEU(owner).equals(before.subtract(combined)),
                engineType.getSimpleName() + " advances both orders in one tick with combined payment > Long.MAX_VALUE", checks);
        require(combined.equals(get(engine, "currentGridEUt"))
                        && ((Long) get(engine, "currentEUt")) == Long.MAX_VALUE,
                engineType.getSimpleName() + " retains exact wide current EU/t and saturates legacy long EU/t without overflow", checks);
        var publish = engineType == IvRecipeLogic.class
                ? engineType.getDeclaredMethod("updateOrderSummary", List.class, TieredParallelMachine.class)
                : engineType.getDeclaredMethod("publish", List.class);
        publish.setAccessible(true);
        if (engineType == IvRecipeLogic.class) publish.invoke(engine, refs, machine); else publish.invoke(engine, refs);
        CompoundTag summary = engineType == IvRecipeLogic.class
                ? ((IvRecipeLogic) engine).getOrderSummary() : ((IvNativeAccess) logic).iv$summary();
        require(summary.getString("wideEut").equals(combined.toString()) && summary.getLong("eut") == Long.MAX_VALUE,
                engineType.getSimpleName() + " publishes precise wideEut and nonnegative legacy projection", checks);
        for (boolean detail : List.of(false, true)) {
            var text = new ArrayList<Component>();
            IvPresentation.append(text, summary, detail);
            require(text.stream().anyMatch(line -> line.getContents() instanceof TranslatableContents translated
                            && translated.getKey().equals("gtl_enhancedcore.gui.iv_grid_eut")
                            && translated.getArgs().length == 1
                            && translated.getArgs()[0] instanceof Component number
                            && number.getString().equals(IvPresentation.number(combined.toString()))),
                    engineType.getSimpleName() + " shared GUI/Jade presentation contains exact wide EU/t (detail=" + detail + ")", checks);
        }
        WirelessEnergyManager.setUserEU(owner, BigInteger.ZERO);
        var remaining = jobs.stream().map(job -> job.energyLeft).toList();
        var elapsed = jobs.stream().map(job -> job.elapsed).toList();
        // These display counters reset at the public tick entry; advance is our isolated test seam.
        set(engine, "currentGridEUt", BigInteger.ZERO); set(engine, "currentEUt", 0L);
        if (engineType == IvRecipeLogic.class) advance.invoke(engine, refs, machine); else advance.invoke(engine, refs);
        require(jobs.stream().map(job -> job.energyLeft).toList().equals(remaining)
                        && jobs.stream().map(job -> job.elapsed).toList().equals(elapsed)
                        && WirelessEnergyManager.getUserEU(owner).signum() == 0
                        && BigInteger.ZERO.equals(get(engine, "currentGridEUt"))
                        && ((Long) get(engine, "currentEUt")) == 0L,
                engineType.getSimpleName() + " pauses both orders at zero balance without progress, energy debt change or payment", checks);
    }

    private static Object get(Object object, String name) throws Exception {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(object); }
            catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }
    private static void set(Object object, String name, Object value) throws Exception {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(object, value); return; }
            catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }
    private static void require(boolean valid, String label, List<String> checks) {
        if (!valid) throw new AssertionError(label); checks.add(label);
    }
}
