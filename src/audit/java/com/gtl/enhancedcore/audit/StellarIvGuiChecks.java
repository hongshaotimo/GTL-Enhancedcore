package com.gtl.enhancedcore.audit;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gtl.enhancedcore.common.machine.galaxy.GalaxyConnectionHatch;
import com.gtl.enhancedcore.common.machine.galaxy.GalaxyLinks;
import com.gtl.enhancedcore.common.machine.galaxy.StellarVortexSpaceStationMachine;
import com.gtl.enhancedcore.common.recipe.iv.IvBufferAccess;
import com.gtl.enhancedcore.common.recipe.iv.IvBuffers;
import com.gtl.enhancedcore.common.recipe.iv.IvMachineScope;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

/** Real registered suppliers and real UI trees in synthetic levels; never opens a player world. */
public final class StellarIvGuiChecks {
    private StellarIvGuiChecks() {}

    public static List<String> run() throws Exception {
        var checks = new ArrayList<String>();
        var failures = new ArrayList<String>();
        for (String id : List.of("gtceu:component_assembly_line", "gtceu:mage_assembler",
                "gtceu:suprachronal_assembly_line", "gtl_enhancedcore:platinum_refining_matrix")) {
            for (boolean receiver : List.of(false, true)) {
              for (int arrays : List.of(-1, 0, 63, 64)) {
                boolean full64 = arrays == 64;
                String label = id + " receiver=" + receiver + " arrays=" + arrays;
                try {
                var server = fixture(id, false, receiver, arrays);
                var client = fixture(id, true, receiver, arrays);
                checks.add(label + " registered supplier=" + server.machine.getClass().getName()
                        + ", logic=" + server.machine.getRecipeLogic().getClass().getName());
                require(GalaxyLinks.unlimited(server.machine) == full64 && !GalaxyLinks.unlimited(client.machine),
                        label + " source-side resolver requires 64 real station arrays", checks);
                int baseThreads = IvMachineScope.threads(client.machine);
                // A live link temporarily enables ADD multiple mode. Compare with its
                // native getter, not the disconnected single-mode GUI fallback.
                if (!IvMachineScope.nativeTarget(server.machine)
                        && server.machine.getRecipeLogic() instanceof com.gtladd.gtladditions.api.machine.logic.MutableRecipesLogic<?> mutable
                        && mutable.isMultipleRecipeMode())
                    baseThreads = ((com.gtladd.gtladditions.api.machine.logic.MutableRecipesLogic<?>)client.machine.getRecipeLogic()).getMultipleThreads();
                int expectedThreads = full64 ? Integer.MAX_VALUE
                        : arrays >= 0 ? baseThreads + GalaxyLinks.EXTRA_THREADS : baseThreads;
                require(IvMachineScope.threads(server.machine) == expectedThreads,
                        label + " actual thread getter and GUI/Jade scope apply the bonus once: actual="
                                + IvMachineScope.threads(server.machine) + " expected=" + expectedThreads, checks);
                require(IvMachineScope.parallel(server.machine) == (full64 ? Integer.MAX_VALUE
                                : IvMachineScope.parallel(client.machine)),
                        label + " finite array count preserves native parallel limit", checks);
                boolean expectedIv = IvMachineScope.nativeTarget(server.machine);
                require(IvMachineScope.crossRecipeEnabled(server.machine) == expectedIv
                                && IvMachineScope.crossRecipeEnabled(client.machine) == expectedIv,
                        label + " real registered controller preserves expected IV scope=" + expectedIv, checks);
                require(server.machine.getSubTabs().stream().noneMatch(GalaxyConnectionHatch.class::isInstance),
                        label + " connection hatches have no controller subpages", checks);
                require(server.machine.shouldOpenUI(server.player, net.minecraft.world.InteractionHand.MAIN_HAND, null),
                        label + " controller accepts GUI opening with receiver installed", checks);
                var serverUi = ui(server);
                var clientUi = ui(client);
                checks.add(label + " batch helper server/client="
                        + org.gtlcore.gtlcore.api.recipe.BatchProcessing.canConfigureBatchProcessing(server.machine) + "/"
                        + org.gtlcore.gtlcore.api.recipe.BatchProcessing.canConfigureBatchProcessing(client.machine)
                        + " controller=" + ((org.gtlcore.gtlcore.api.machine.trait.IBatchMachine)server.machine).canConfigureBatchProcessing()
                        + "/" + ((org.gtlcore.gtlcore.api.machine.trait.IBatchMachine)client.machine).canConfigureBatchProcessing());
                var serverTree = tree(serverUi.mainGroup);
                var clientTree = tree(clientUi.mainGroup);
                require(serverTree.equals(clientTree), label + " server/client widget topology is identical: "
                        + serverTree.size() + " widgets; first mismatch=" + mismatch(serverTree, clientTree), checks);
                var data = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    serverUi.mainGroup.writeInitialData(data);
                    int size = data.readableBytes();
                    clientUi.mainGroup.readInitialData(data);
                    require(data.readableBytes() == 0, label + " initial GUI synchronization consumes all " + size + " bytes", checks);
                } finally { data.release(); }
                for (var holder : server.holders) holder.setLevel(null);
                for (var holder : client.holders) holder.setLevel(null);
                } catch (Throwable failure) {
                    failures.add(label + ": " + failure);
                    failure.printStackTrace();
                }
              }
            }
        }
        var result = new java.util.LinkedHashMap<String, Object>();
        result.put("checks", checks); result.put("failures", failures);
        result.put("status", failures.isEmpty() ? "PASS" : "FAIL");
        java.nio.file.Files.writeString(net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath()
                .resolve("stellar-iv-gui-checks.json"), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(result));
        if (!failures.isEmpty()) throw new AssertionError(failures.toString());
        return checks;
    }

    private static ModularUI ui(Fixture fixture) {
        ModularUI ui = fixture.machine.createUI(fixture.player);
        ui.initWidgets();
        return ui;
    }

    static List<String> power() throws Exception {
        var checks = new ArrayList<String>();
        var statistics = Class.forName("com.xingmot.gtmadvancedhatch.integration.gtmt.newmonitor.EnergyStat")
                .getDeclaredField("server");
        statistics.setAccessible(true);
        Object previousServer = statistics.get(null);
        var previousData = com.hepdd.gtmthings.data.WirelessEnergySavaedData.INSTANCE;
        var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); unsafeField.setAccessible(true);
        var unsafe = (sun.misc.Unsafe)unsafeField.get(null);
        statistics.set(null, unsafe.allocateInstance(net.minecraft.client.server.IntegratedServer.class));
        com.hepdd.gtmthings.data.WirelessEnergySavaedData.INSTANCE = new com.hepdd.gtmthings.data.WirelessEnergySavaedData(null);
        try {
            for (String id : List.of("gtceu:component_assembly_line", "gtceu:mage_assembler",
                    "gtl_enhancedcore:hydrothermal_leaching_complex", "gtl_enhancedcore:platinum_refining_matrix")) {
                var f = fixture(id, false, false, true);
                var machine = f.machine();
                var station = GalaxyLinks.linkedStation(machine);
                require(station != null, id + " actual linked station resolves", checks);
                var owner = UUID.randomUUID(); set(station, "userId", owner);
                var balance = java.math.BigInteger.TEN.pow(46).multiply(java.math.BigInteger.TWO);
                var payment = java.math.BigInteger.TEN.pow(32).add(java.math.BigInteger.valueOf(17));
                com.hepdd.gtmthings.api.misc.WirelessEnergyManager.setUserEU(owner, balance);
                require(com.gtl.enhancedcore.common.machine.galaxy.GalaxyPower.availablePower(machine).equals(balance),
                        id + " power equals 2E46 grid balance without long or MAX+16 clipping", checks);
                var handler = ((com.gtladd.gtladditions.api.machine.IWirelessElectricMultiblockMachine)machine).getWirelessNetworkEnergyHandler();
                require(handler != null && handler.getMaxAvailableEnergy().equals(balance), id + " ADD receives full BigInteger budget", checks);
                require(com.gtl.enhancedcore.common.machine.galaxy.GalaxyPower.pay(machine, payment)
                                && com.hepdd.gtmthings.api.misc.WirelessEnergyManager.getUserEU(owner).equals(balance.subtract(payment)),
                        id + " pays 1E32+17 EU exactly once", checks);
                var receipt = com.gtl.enhancedcore.common.machine.galaxy.GalaxyPower.withdraw(machine, payment);
                require(receipt != null, id + " wide payment receipt accepted", checks);
                receipt.refund();
                require(com.hepdd.gtmthings.api.misc.WirelessEnergyManager.getUserEU(owner).equals(balance.subtract(payment)),
                        id + " wide receipt refunds exact original owner amount", checks);
                require(!com.gtl.enhancedcore.common.machine.galaxy.GalaxyPower.pay(machine, balance),
                        id + " insufficient grid rejects atomically", checks);
                var huge = java.math.BigInteger.TEN.pow(72).add(java.math.BigInteger.valueOf(43));
                com.hepdd.gtmthings.api.misc.WirelessEnergyManager.setUserEU(owner, huge);
                if (id.equals("gtceu:mage_assembler") || id.equals("gtl_enhancedcore:hydrothermal_leaching_complex"))
                    isolatedPower(f, owner, checks);
                com.hepdd.gtmthings.api.misc.WirelessEnergyManager.setUserEU(owner, huge);
                var lines = new ArrayList<Component>();
                com.gtl.enhancedcore.common.recipe.iv.IvMachineDisplay.wrap(machine, ignored -> {}).accept(lines);
                var powerLine = lines.stream().filter(c -> c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                        && t.getKey().equals("gtl_enhancedcore.galaxy.max_power")).findFirst().orElseThrow();
                var shown = (Component)((net.minecraft.network.chat.contents.TranslatableContents)powerLine.getContents()).getArgs()[0];
                require(machine.getTier() == com.gregtechceu.gtceu.api.GTValues.MAX
                                && machine.getOverclockVoltage() == com.gregtechceu.gtceu.api.GTValues.V[com.gregtechceu.gtceu.api.GTValues.MAX]
                                && ((net.minecraft.network.chat.contents.TranslatableContents)powerLine.getContents()).getArgs().length == 1,
                        id + " actual recipe tier is MAX and maximum power has no suffix", checks);
                require(shown.getStyle().getHoverEvent().getValue(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT).getString()
                                .equals(com.gregtechceu.gtceu.utils.FormattingUtil.formatNumbers(huge)),
                        id + " real GUI callback refreshes exact 1E72+43 maximum power", checks);
                if (id.equals("gtl_enhancedcore:platinum_refining_matrix")) {
                    var recipe = com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder.of(
                            new ResourceLocation("gtl_enhancedcore", "wide_tick_probe"), machine.getRecipeType()).duration(2).buildRawRecipe();
                    recipe.data.putString(com.gtl.enhancedcore.common.machine.galaxy.GalaxyPower.EU_TAG, payment.toString());
                    recipe.data.putString(com.gtl.enhancedcore.common.machine.galaxy.GalaxyPower.LEFT_TAG, payment.add(java.math.BigInteger.valueOf(7)).toString());
                    require(machine.getRecipeLogic().handleTickRecipe(recipe).isSuccess()
                                    && com.hepdd.gtmthings.api.misc.WirelessEnergyManager.getUserEU(owner).equals(huge.subtract(payment)),
                            "ordinary RecipeLogic tick consumes BigInteger EU above long", checks);
                    require(machine.getRecipeLogic().handleTickRecipe(recipe).isSuccess()
                                    && com.hepdd.gtmthings.api.misc.WirelessEnergyManager.getUserEU(owner).equals(huge.subtract(payment).subtract(java.math.BigInteger.valueOf(7))),
                            "ordinary final tick pays only seven EU remaining", checks);
                }
                var link = machine.getParts().stream().filter(LinkProbe.class::isInstance).map(LinkProbe.class::cast).findFirst().orElseThrow();
                link.active = false;
                var before = com.hepdd.gtmthings.api.misc.WirelessEnergyManager.getUserEU(owner);
                require(com.gtl.enhancedcore.common.machine.galaxy.GalaxyPower.availablePower(machine).signum() == 0
                                && !com.gtl.enhancedcore.common.machine.galaxy.GalaxyPower.pay(machine, payment)
                                && com.hepdd.gtmthings.api.misc.WirelessEnergyManager.getUserEU(owner).equals(before),
                        id + " disconnect exposes zero power and makes no debit", checks);
                for (var holder : f.holders()) holder.setLevel(null);
            }
        } finally { statistics.set(null, previousServer); com.hepdd.gtmthings.data.WirelessEnergySavaedData.INSTANCE = previousData; }
        return checks;
    }

    private static void isolatedPower(Fixture f, UUID owner, List<String> checks) throws Exception {
        var machine = f.machine(); var logic = machine.getRecipeLogic();
        var buffer = (org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine)f.holders().get(1).getMetaMachine();
        long count = 1L << 34;
        var inputKey = appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.APPLE);
        var recipe = com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder.of(
                new ResourceLocation("gtl_enhancedcore", "stellar_isolated_power"), machine.getRecipeType())
                .inputItems(net.minecraft.world.item.Items.APPLE).outputItems(net.minecraft.world.item.Items.GOLD_NUGGET)
                .EUt(com.gregtechceu.gtceu.api.GTValues.V[com.gregtechceu.gtceu.api.GTValues.MAX]).duration(20000000).buildRawRecipe();
        var job = new com.gtl.enhancedcore.common.recipe.iv.IvJob(0, recipe, new net.minecraft.nbt.CompoundTag(),
                java.util.Map.of(inputKey, count), java.util.Map.of(), count);
        Object engine = logic instanceof com.gtl.enhancedcore.common.recipe.iv.IvRecipeLogic ? logic
                : ((com.gtl.enhancedcore.common.recipe.iv.IvNativeAccess)logic).iv$engine();
        Class<?> engineClass = engine instanceof com.gtl.enhancedcore.common.recipe.iv.IvRecipeLogic
                ? com.gtl.enhancedcore.common.recipe.iv.IvRecipeLogic.class : com.gtl.enhancedcore.common.recipe.iv.IvNativeEngine.class;
        Class<?> refType = Class.forName(engineClass.getName() + "$Ref");
        var constructor = refType.getDeclaredConstructor(org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine.class,
                com.gtl.enhancedcore.common.recipe.iv.IvJob.class); constructor.setAccessible(true);
        var ref = constructor.newInstance(buffer, job);
        boolean custom = engineClass == com.gtl.enhancedcore.common.recipe.iv.IvRecipeLogic.class;
        var prepare = custom ? engineClass.getDeclaredMethod("prepare", refType, long.class,
                com.gtl.enhancedcore.common.machine.TieredParallelMachine.class, String.class)
                : engineClass.getDeclaredMethod("prepare", refType, long.class, long.class, String.class);
        prepare.setAccessible(true);
        if (custom) prepare.invoke(engine, ref, count, machine, "wide-test");
        else prepare.invoke(engine, ref, count, count, "wide-test");
        var eut = custom ? job.ivGalaxyEUt : job.nativeWirelessEUt;
        require(job.active() && !job.halted && eut.compareTo(java.math.BigInteger.valueOf(Long.MAX_VALUE)) > 0,
                machine.getDefinition().getId() + " real prepare creates a single batch above long: " + eut + " error=" + job.error, checks);
        require(job.parallel == count && job.inventory.isEmpty() && job.remaining == 0,
                machine.getDefinition().getId() + " native admission commits exactly " + count + " input operations", checks);
        var before = com.hepdd.gtmthings.api.misc.WirelessEnergyManager.getUserEU(owner);
        var energyBefore = job.energyLeft;
        var advance = custom ? engineClass.getDeclaredMethod("advance", List.class, com.gtl.enhancedcore.common.machine.TieredParallelMachine.class)
                : engineClass.getDeclaredMethod("advance", List.class); advance.setAccessible(true);
        if (custom) advance.invoke(engine, List.of(ref), machine); else advance.invoke(engine, List.of(ref));
        require(job.elapsed == 1 && job.energyLeft.equals(energyBefore.subtract(eut))
                        && com.hepdd.gtmthings.api.misc.WirelessEnergyManager.getUserEU(owner).equals(before.subtract(eut)),
                machine.getDefinition().getId() + " actual engine tick deducts the entire wide batch once", checks);
    }

    static Fixture fixture(String id, boolean client, boolean receiver, boolean full64) throws Exception {
        return fixture(id, client, receiver, full64 ? 64 : -1);
    }
    static Fixture fixture(String id, boolean client, boolean receiver, int arrays) throws Exception {
        var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); unsafeField.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        var world = (AuditWorld) unsafe.allocateInstance(AuditWorld.class);
        set(world, "blockEntities", new java.util.HashMap<>());
        set(world, "renderedBlocks", new java.util.HashMap<>());
        set(world, "proxyWorld", new java.lang.ref.WeakReference<Level>(null));
        ObfuscationReflectionHelper.setPrivateValue(Level.class, world, client, "f_46443_");
        ObfuscationReflectionHelper.setPrivateValue(Level.class, world, Level.OVERWORLD, "f_46421_");
        var holders = new ArrayList<MetaMachineBlockEntity>();
        var controller = place(id, new BlockPos(0, 80, 0), world, holders);
        var machine = (WorkableElectricMultiblockMachine) controller.getMetaMachine();
        set(machine, "isFormed", true);
        machine.getMultiblockState().error = null;
        var parts = new ArrayList<IMultiPart>();
        var buffer = place("gtladditions:me_super_pattern_buffer", new BlockPos(1, 80, 0), world, holders).getMetaMachine();
        parts.add((IMultiPart) buffer);
        IvBuffers.state(buffer).owner = "fixture/" + id;
        ((IvBufferAccess) buffer).iv$syncDedicated(true);
        parts.add((IMultiPart) place("gtl_enhancedcore:galaxy_connection_target_hatch", new BlockPos(2, 80, 0), world, holders).getMetaMachine());
        if (receiver) parts.add((IMultiPart) place("gtceu:wireless_data_receiver_hatch", new BlockPos(3, 80, 0), world, holders).getMetaMachine());
        if (arrays >= 0) {
            var station = (StellarVortexSpaceStationMachine) place("gtl_enhancedcore:stellar_vortex_space_station",
                    new BlockPos(40, 80, 0), world, holders).getMetaMachine();
            set(station, "astralArrayCount", arrays);
            var link = (LinkProbe) unsafe.allocateInstance(LinkProbe.class);
            link.active = !client; link.station = station;
            parts.add(link);
        }
        set(machine, "parts", parts);
        set(machine, "partPositions", parts.stream().map(p -> p.self().getPos()).toArray(BlockPos[]::new));
        // Real supplier/controller/recipe logic retained; only loaded part membership is seeded.
        var player = (AuditPlayer) unsafe.allocateInstance(AuditPlayer.class);
        player.inventory = new net.minecraft.world.entity.player.Inventory(player);
        ObfuscationReflectionHelper.setPrivateValue(net.minecraft.world.entity.Entity.class, player, world, "f_19853_");
        return new Fixture(machine, player, holders);
    }

    private static MetaMachineBlockEntity place(String id, BlockPos pos, TrackedDummyWorld world,
            List<MetaMachineBlockEntity> holders) {
        MachineDefinition definition = GTRegistries.MACHINES.get(new ResourceLocation(id));
        if (definition == null) throw new AssertionError("Missing real definition: " + id);
        var holder = (MetaMachineBlockEntity) definition.getBlockEntityType().create(pos, definition.defaultBlockState());
        holder.setLevel(world);
        world.blockEntities.put(pos, holder);
        world.renderedBlocks.put(pos, new com.lowdragmc.lowdraglib.utils.BlockInfo(definition.defaultBlockState()));
        holders.add(holder);
        return holder;
    }

    private static void set(Object object, String name, Object value) throws Exception {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(object, value); return; }
            catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }
    private static List<String> tree(Widget widget) {
        var result = new ArrayList<String>();
        visit(widget, "", result);
        return result;
    }
    private static void visit(Widget widget, String prefix, List<String> result) {
        result.add(prefix + widget.getClass().getName());
        if (widget instanceof WidgetGroup group)
            for (int i = 0; i < group.widgets.size(); i++) visit(group.widgets.get(i), prefix + i + "/", result);
    }
    private static String mismatch(List<String> a, List<String> b) {
        for (int i = 0; i < Math.min(a.size(), b.size()); i++) if (!a.get(i).equals(b.get(i))) return i + ":" + a.get(i) + " / " + b.get(i);
        return a.size() == b.size() ? "none" : "length=" + a.size() + "/" + b.size()
                + "; server tail=" + a.subList(Math.min(a.size(), b.size()), a.size())
                + "; client tail=" + b.subList(Math.min(a.size(), b.size()), b.size());
    }
    private static void require(boolean valid, String label, List<String> checks) {
        if (!valid) throw new AssertionError(label); checks.add(label);
    }
    record Fixture(WorkableElectricMultiblockMachine machine, Player player, List<MetaMachineBlockEntity> holders) {}
    private static final class AuditWorld extends TrackedDummyWorld {
        private net.minecraft.core.RegistryAccess auditRegistryAccess = net.minecraft.core.RegistryAccess.EMPTY;
        private AuditWorld() { super(null); }
        @Override public Level getLevel() { return this; }
        @Override public net.minecraft.core.RegistryAccess registryAccess() {
            return auditRegistryAccess == null ? net.minecraft.core.RegistryAccess.EMPTY : auditRegistryAccess;
        }
        @Override public net.minecraft.world.flag.FeatureFlagSet enabledFeatures() {
            return net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS;
        }
        @Override public net.minecraft.world.item.crafting.RecipeManager getRecipeManager() {
            return new net.minecraft.world.item.crafting.RecipeManager();
        }
        @Override public long getGameTime() { return 1000; }
        @Override public net.minecraft.core.BlockPos getSharedSpawnPos() { return BlockPos.ZERO; }
        @Override public void blockEntityChanged(BlockPos pos) {}
        @Override public void setBlocksDirty(BlockPos pos, net.minecraft.world.level.block.state.BlockState before,
                net.minecraft.world.level.block.state.BlockState after) {}
        @Override public net.minecraft.world.level.block.state.BlockState getBlockState(BlockPos pos) {
            var block = renderedBlocks.get(pos);
            return block == null ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState() : block.getBlockState();
        }
        @Override public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(BlockPos pos) { return blockEntities.get(pos); }
    }
    private static final class AuditPlayer extends Player {
        net.minecraft.world.entity.player.Inventory inventory;
        private AuditPlayer(Level world) { super(world, BlockPos.ZERO, 0, new GameProfile(UUID.randomUUID(), "StellarUiAudit")); }
        @Override public net.minecraft.world.entity.player.Inventory getInventory() { return inventory; }
        @Override public boolean isSpectator() { return false; }
        @Override public boolean isCreative() { return true; }
    }
    /** Sole seam is cross-dimensional loaded-chunk resolution; the target/controller/logic/GUI remain registered objects. */
    private static final class LinkProbe extends GalaxyConnectionHatch {
        boolean active; StellarVortexSpaceStationMachine station;
        private LinkProbe(com.gregtechceu.gtceu.api.machine.IMachineBlockEntity holder) { super(holder, false); }
        @Override public boolean linkActive() { return active; }
        @Override public StellarVortexSpaceStationMachine sourceStation() { return active ? station : null; }
        @Override public BlockPos getPos() { return new BlockPos(4, 80, 0); }
    }
}
