package com.gtl.enhancedcore.audit;

import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.api.config.TypeFilter;
import appeng.api.config.ViewItems;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.client.gui.me.common.Repo;
import appeng.client.gui.widgets.ISortSource;
import appeng.menu.me.common.GridInventoryEntry;
import com.gtl.enhancedcore.client.performance.RepoRebuildControl;
import java.math.BigInteger;
import java.nio.file.Files;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fml.loading.FMLPaths;
import org.gtlcore.gtlcore.client.ae2.PreciseRepoAmounts;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachineBase;
import org.gtlcore.gtlcore.utils.datastructure.Int128;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

/** Called by the isolated title-screen audit; no event entry or player/world creation. */
public final class AePerformanceChecks {
    private AePerformanceChecks() {}
    public static void run(List<String> checks) throws Exception {
        var sort = new Sort();
        var repo = new Repo(() -> 0, sort);
        repo.setPower(true);
        require(repo instanceof RepoRebuildControl && repo instanceof PreciseRepoAmounts,
                "AE native Repo carries both performance and GTLCore exact interfaces", checks);
        var depth = Repo.class.getDeclaredField("enhancedcore$batchDepth"); depth.setAccessible(true);
        var pending = Repo.class.getDeclaredField("enhancedcore$pendingView"); pending.setAccessible(true);
        var rebuilds = new AtomicInteger(); repo.setUpdateViewListener(rebuilds::incrementAndGet);
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var gold = AEItemKey.of(Items.GOLD_INGOT);
        var water = AEFluidKey.of(Fluids.WATER);
        var huge = BigInteger.ONE.shiftLeft(180);
        // Simulate the packet/precise-snapshot batching scope on a native transformed Repo.
        depth.setInt(repo, 1);
        try {
            repo.handleUpdate(true, List.of(new GridInventoryEntry(11, iron, Long.MAX_VALUE, 0, false),
                    new GridInventoryEntry(12, gold, Long.MAX_VALUE, 0, false),
                    new GridInventoryEntry(13, water, 1000, 0, false)));
            repo.handleUpdate(false, List.of(new GridInventoryEntry(13, null, 2000, 0, false)));
            ((PreciseRepoAmounts) repo).gtlcore$setPreciseAmounts(Map.of(iron, huge, gold, huge.shiftRight(1)));
        } finally { depth.setInt(repo, 0); }
        require(rebuilds.get() == 0 && pending.getBoolean(repo),
                "AE inventory packets plus exact snapshot queue one rebuild", checks);
        require(repo.get(0).getSerial() == 13 && rebuilds.get() == 1 && !pending.getBoolean(repo),
                "AE click-slot read flushes one complete normalized view", checks);
        require(repo.get(1).getSerial() == 12 && repo.get(2).getSerial() == 11,
                "AE precise values beyond long and fluid units sort ascending", checks);
        sort.direction = SortDir.DESCENDING; repo.updateView();
        require(repo.get(0).getSerial() == 11 && repo.get(2).getSerial() == 13,
                "AE exact descending reverses full normalized order", checks);
        repo.setPaused(true);
        long first = repo.get(0).getSerial();
        depth.setInt(repo, 1);
        try { ((PreciseRepoAmounts) repo).gtlcore$setPreciseAmounts(Map.of(iron, BigInteger.ONE, gold, huge)); }
        finally { depth.setInt(repo, 0); }
        ((RepoRebuildControl) repo).enhancedcore$flushView();
        require(repo.get(0).getSerial() == first, "AE Shift pause preserves slot identity after exact update", checks);
        repo.setPaused(false);
        require(repo.get(0).getSerial() == 12, "AE unpause applies current exact order", checks);
        repo.clear();
        require(repo.size() == 0 && repo.getAllEntries().isEmpty() && !pending.getBoolean(repo),
                "AE clear releases view/cache without retaining pending inventory", checks);

        patternSlotCapacities(checks);
    }

    private static void patternSlotCapacities(List<String> checks) throws Exception {
        var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); unsafeField.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        var definition = GTRegistries.MACHINES.get(new ResourceLocation("gtladditions:me_super_pattern_buffer"));
        require(definition != null, "Pattern slot audit uses real registered super-buffer definition", checks);
        // Real block entity construction creates its machine while holder.level is still null.
        // MetaMachine.isRemote then uses LDLib.isRemote (physical client AND Minecraft thread).
        var actual = (MetaMachineBlockEntity) definition.getBlockEntityType().create(BlockPos.ZERO, definition.defaultBlockState());
        var actualMachine = (MEPatternBufferPartMachine) actual.getMetaMachine();
        var inventory = find(actualMachine.getClass(), "internalInventory");
        Object[] slots = (Object[]) inventory.get(actualMachine);
        require(actual.getLevel() == null && actualMachine.isRemote() && slots.length > 1,
                "Client pattern slot construction resolves side before holder level is assigned", checks);
        require(capacity(field(slots[0], "itemInventory")) == 2 && capacity(field(slots[0], "fluidInventory")) == 2,
                "Actual client super-buffer starts item/fluid maps at capacity two", checks);
        for (String name : List.of("itemInventory", "fluidInventory", "virtualItemSupply", "virtualFluidSupply",
                "configuredVirtualItems", "configuredVirtualFluids")) {
            Object first = field(slots[0], name), second = field(slots[1], name);
            require(first != second && capacity(first) == 2 && capacity(second) == 2,
                    "Client pattern slots retain independent mutable " + name, checks);
        }
        var ordinaryClient = new MEPatternBufferPartMachine(actual, 2, IO.IN);
        var ordinarySlots = (Object[]) inventory.get(ordinaryClient);
        require(ordinaryClient.isRemote() && capacity(field(ordinarySlots[0], "itemInventory")) == 2,
                "Ordinary client pattern buffer uses the same small mutable base collections", checks);

        // A real holder with a synthetic server Level proves client physical side does not
        // shrink server-side slots in an integrated-server construction context.
        var serverWorld = (TrackedDummyWorld) unsafe.allocateInstance(TrackedDummyWorld.class);
        ObfuscationReflectionHelper.setPrivateValue(Level.class, serverWorld, false, "f_46443_");
        set(serverWorld, "proxyWorld", new WeakReference<Level>(null));
        actual.setLevel(serverWorld);
        var serverMachine = new MEPatternBufferPartMachine(actual, 2, IO.IN);
        var serverSlots = (Object[]) inventory.get(serverMachine);
        require(!serverMachine.isRemote() && capacity(field(serverSlots[0], "itemInventory")) == 32
                        && capacity(field(serverSlots[0], "configuredVirtualItems")) == 32,
                "Server-level pattern slot construction retains upstream capacity 32", checks);

        // Ordinary buffers persist their long maps. ADD super buffers deliberately remove these
        // two inherited NBT fields and persist their separate Int128 wideInputs instead.
        Object first = ordinarySlots[0], second = ordinarySlots[1];
        var firstItems = (it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap<AEItemKey>) field(first, "itemInventory");
        var firstFluids = (it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap<AEFluidKey>) field(first, "fluidInventory");
        var secondItems = (it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap<AEItemKey>) field(second, "itemInventory");
        var secondFluids = (it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap<AEFluidKey>) field(second, "fluidInventory");
        var firstConfigured = (java.util.Set<AEItemKey>) field(first, "configuredVirtualItems");
        var secondConfigured = (java.util.Set<AEItemKey>) field(second, "configuredVirtualItems");
        var iron = AEItemKey.of(Items.IRON_INGOT); var gold = AEItemKey.of(Items.GOLD_INGOT);
        var water = AEFluidKey.of(Fluids.WATER);
        for (var item : BuiltInRegistries.ITEM) {
            if (firstItems.size() >= 40) break;
            if (item != Items.AIR) firstItems.put(AEItemKey.of(item), 42);
        }
        require(firstItems.size() >= 40 && capacity(firstItems) > 2,
                "Small client pattern maps automatically grow for populated inventories", checks);
        firstItems.clear(); firstItems.put(iron, 91);
        firstFluids.put(water, 123456L);
        firstConfigured.add(gold);
        ((java.util.Set<AEFluidKey>) field(first, "configuredVirtualFluids")).add(water);
        ((java.util.Set<AEItemKey>) field(first, "virtualItemSupply")).add(gold);
        ((java.util.Set<AEFluidKey>) field(first, "virtualFluidSupply")).add(water);
        var tag = serializeAndLoad(first, second);
        require(tag.contains("inventory") && tag.contains("fluidInventory") && !tag.contains("wideInput"),
                "Ordinary pattern slot NBT retains its original long-map schema", checks);
        require(secondItems.getLong(iron) == 91 && secondFluids.getLong(water) == 123456L
                        && ((java.util.Set<?>) field(second, "virtualItemSupply")).contains(gold)
                        && ((java.util.Set<?>) field(second, "virtualFluidSupply")).contains(water),
                "Small client pattern collections preserve real InternalSlot NBT round trip", checks);
        secondConfigured.add(gold);
        ((java.util.Set<AEFluidKey>) field(second, "configuredVirtualFluids")).add(water);
        firstItems.removeLong(iron); firstConfigured.remove(gold);
        require(secondItems.getLong(iron) == 91 && secondConfigured.contains(gold),
                "Pattern slot remove/deserialize retains independent slot contents", checks);
        widePatternRoundTrip(slots, checks);
    }

    private static void widePatternRoundTrip(Object[] slots, List<String> checks) throws Exception {
        Object first = slots[0], second = slots[1];
        var firstInputs = (Map<appeng.api.stacks.AEKey, Int128>) field(first, "inputs");
        var secondInputs = (Map<appeng.api.stacks.AEKey, Int128>) field(second, "inputs");
        var iron = AEItemKey.of(Items.IRON_INGOT); var gold = AEItemKey.of(Items.GOLD_INGOT);
        var water = AEFluidKey.of(Fluids.WATER);
        var hugeItem = new Int128(1L, 91L);
        firstInputs.put(iron, hugeItem); firstInputs.put(water, new Int128(123456L));
        ((java.util.Set<AEItemKey>) field(first, "virtualItemSupply")).add(gold);
        ((java.util.Set<AEFluidKey>) field(first, "virtualFluidSupply")).add(water);
        var tag = serializeAndLoad(first, second);
        require(tag.contains("wideInput") && !tag.contains("inventory") && !tag.contains("fluidInventory"),
                "ADD super-buffer NBT keeps its original wideInput schema", checks);
        require(firstInputs != secondInputs && hugeItem.equals(secondInputs.get(iron))
                        && new Int128(123456L).equals(secondInputs.get(water))
                        && ((java.util.Set<?>) field(second, "virtualItemSupply")).contains(gold)
                        && ((java.util.Set<?>) field(second, "virtualFluidSupply")).contains(water),
                "Actual ADD super-buffer preserves beyond-long item/fluid and virtual-supply NBT", checks);
        hugeItem.add(1); firstInputs.remove(water);
        ((java.util.Set<?>) field(first, "virtualItemSupply")).remove(gold);
        require(new Int128(1L, 91L).equals(secondInputs.get(iron)) && secondInputs.containsKey(water)
                        && ((java.util.Set<?>) field(second, "virtualItemSupply")).contains(gold),
                "ADD super-buffer NBT creates independent Int128 values and virtual-supply sets", checks);
    }

    private static net.minecraft.nbt.CompoundTag serializeAndLoad(Object first, Object second) throws Exception {
        var serialize = first.getClass().getMethod("serializeNBT");
        serialize.setAccessible(true);
        var tag = (net.minecraft.nbt.CompoundTag) serialize.invoke(first);
        var deserialize = second.getClass().getMethod("deserializeNBT", net.minecraft.nbt.CompoundTag.class);
        deserialize.setAccessible(true);
        deserialize.invoke(second, tag);
        return tag;
    }

    private static Object field(Object object, String name) throws Exception { return find(object.getClass(), name).get(object); }
    private static void set(Object object, String name, Object value) throws Exception { find(object.getClass(), name).set(object, value); }
    private static int capacity(Object collection) throws Exception { return find(collection.getClass(), "n").getInt(collection); }
    private static Field find(Class<?> type, String name) throws NoSuchFieldException {
        for (; type != null; type = type.getSuperclass()) {
            try { var field = type.getDeclaredField(name); field.setAccessible(true); return field; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
    private static final class Sort implements ISortSource {
        SortDir direction = SortDir.ASCENDING;
        @Override public SortOrder getSortBy() { return SortOrder.AMOUNT; }
        @Override public SortDir getSortDir() { return direction; }
        @Override public ViewItems getSortDisplay() { return ViewItems.ALL; }
        @Override public TypeFilter getTypeFilter() { return TypeFilter.ALL; }
    }
    private static void require(boolean value, String label, List<String> checks) {
        if (!value) throw new AssertionError(label);
        checks.add(label);
    }
}
