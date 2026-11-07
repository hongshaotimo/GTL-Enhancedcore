package com.gtl.enhancedcore.audit;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gtladd.gtladditions.api.machine.IWidePatternSlot;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;

/** Title-screen audit of real transformed ADD slots, without creating or opening a world. */
public final class SuperBufferWideStockChecks {
    private SuperBufferWideStockChecks() {}

    public static void run(List<String> checks) throws Exception {
        var definition = GTRegistries.MACHINES.get(new ResourceLocation(
                "gtladditions:me_super_pattern_buffer"));
        require(definition != null, "Wide stock audit finds registered ADD super buffer", checks);
        var holder = (MetaMachineBlockEntity) definition.getBlockEntityType().create(
                BlockPos.ZERO, definition.defaultBlockState());
        require(holder != null && holder.getLevel() == null,
                "Wide stock audit constructs a real holder without a world", checks);
        var machine = holder.getMetaMachine();
        var slots = (Object[]) find(machine.getClass(), "internalInventory").get(machine);
        require(slots.length >= 2 && slots[0] instanceof IWidePatternSlot
                        && slots[1] instanceof IWidePatternSlot,
                "Registered ADD machine constructs actual independent wide slots", checks);
        var original = slots[0];
        var restored = slots[1];
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var water = AEFluidKey.of(Fluids.WATER);
        var expected = BigInteger.ZERO;
        for (long amount : new long[] {Long.MAX_VALUE, Long.MAX_VALUE, 17L}) {
            var items = new KeyCounter(); items.add(iron, amount);
            var fluids = new KeyCounter(); fluids.add(water, amount);
            var inputs = new KeyCounter[] {items, fluids};
            require(((IWidePatternSlot) original).canPushWide(inputs),
                    "Actual wide slot admits positive long batch " + amount, checks);
            call(original, "pushPattern", new Class<?>[] {KeyCounter[].class}, (Object) inputs);
            expected = expected.add(BigInteger.valueOf(amount));
            exact(original, iron, expected, "Pushed item stock", checks);
            exact(original, water, expected, "Pushed fluid stock", checks);
            views(original, iron, water, expected, checks);
        }

        var tag = (CompoundTag) call(original, "serializeNBT", new Class<?>[0]);
        require(tag.contains("wideInput") && !tag.contains("inventory")
                        && !tag.contains("fluidInventory"),
                "Real wide slot serializes original wideInput schema", checks);
        call(restored, "deserializeNBT", new Class<?>[] {CompoundTag.class}, tag);
        exact(restored, iron, expected, "Restored item stock", checks);
        exact(restored, water, expected, "Restored fluid stock", checks);
        require(((IWidePatternSlot) original).wideInputs().get(iron)
                        != ((IWidePatternSlot) restored).wideInputs().get(iron)
                        && ((IWidePatternSlot) original).wideInputs().get(water)
                        != ((IWidePatternSlot) restored).wideInputs().get(water),
                "NBT round trip gives independent wide item and fluid balances", checks);
        views(restored, iron, water, expected, checks);
        var initial = expected;
        for (long amount : new long[] {Long.MAX_VALUE, Long.MAX_VALUE, 17L}) {
            var itemNeed = new Object2LongOpenHashMap<Ingredient>();
            itemNeed.put(Ingredient.of(Items.IRON_INGOT), amount);
            var fluidNeed = new Object2LongOpenHashMap<FluidIngredient>();
            fluidNeed.put(FluidIngredient.of(1L, Fluids.WATER), amount);
            require((Boolean) call(restored, "handleItemInternal",
                            new Class<?>[] {Object2LongMap.class, int.class, boolean.class}, itemNeed, -1, true),
                    "Real item handler simulates wide batch " + amount, checks);
            require((Boolean) call(restored, "handleFluidInternal",
                            new Class<?>[] {Object2LongMap.class, boolean.class}, fluidNeed, true),
                    "Real fluid handler simulates wide batch " + amount, checks);
            exact(restored, iron, expected, "Item simulation leaves stock unchanged", checks);
            exact(restored, water, expected, "Fluid simulation leaves stock unchanged", checks);
            require((Boolean) call(restored, "handleItemInternal",
                            new Class<?>[] {Object2LongMap.class, int.class, boolean.class}, itemNeed, -1, false)
                            && itemNeed.isEmpty(),
                    "Real item handler consumes the requested long batch once", checks);
            require((Boolean) call(restored, "handleFluidInternal",
                            new Class<?>[] {Object2LongMap.class, boolean.class}, fluidNeed, false)
                            && fluidNeed.isEmpty(),
                    "Real fluid handler consumes the requested long batch once", checks);
            expected = expected.subtract(BigInteger.valueOf(amount));
            exact(restored, iron, expected, "Consumed item remainder", checks);
            exact(restored, water, expected, "Consumed fluid remainder", checks);
            views(restored, iron, water, expected, checks);
        }
        require(((IWidePatternSlot) restored).wideInputs().isEmpty(),
                "Three real item/fluid batches remove fully consumed wide balances", checks);
        exact(original, iron, initial, "Consuming restored slot preserves original item NBT source", checks);
        exact(original, water, initial, "Consuming restored slot preserves original fluid NBT source", checks);
    }

    @SuppressWarnings("unchecked")
    private static void views(Object slot, AEItemKey iron, AEFluidKey water,
                              BigInteger expected, List<String> checks) throws Exception {
        long amount = expected.min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact();
        var itemInventory = (Object2LongMap<AEItemKey>) call(slot, "getItemInventory", new Class<?>[0]);
        var fluidInventory = (Object2LongMap<AEFluidKey>) call(slot, "getFluidInventory", new Class<?>[0]);
        require(itemInventory.getLong(iron) == amount && fluidInventory.getLong(water) == amount,
                "Actual AE key views expose positive bounded item/fluid batches", checks);
        var items = (Object2LongMap<ItemStack>) call(slot, "getItemStackInputMap", new Class<?>[0]);
        var fluids = (Object2LongMap<FluidStack>) call(slot, "getFluidStackInputMap", new Class<?>[0]);
        require(items.size() == (amount == 0 ? 0 : 1)
                        && items.object2LongEntrySet().stream().allMatch(entry ->
                        AEItemKey.of(entry.getKey()).equals(iron) && entry.getLongValue() == amount),
                "Actual item recipe map retains exact bounded batch", checks);
        require(fluids.size() == (amount == 0 ? 0 : 1)
                        && fluids.object2LongEntrySet().stream().allMatch(entry ->
                        entry.getKey().getFluid() == Fluids.WATER && entry.getLongValue() == amount),
                "Actual fluid recipe map retains exact bounded batch", checks);
        var limitItems = (List<ItemStack>) call(slot, "getLimitItemStackInput", new Class<?>[0]);
        var limitFluids = (List<FluidStack>) call(slot, "getLimitFluidStackInput", new Class<?>[0]);
        require(limitItems.size() == (amount == 0 ? 0 : 1)
                        && limitItems.stream().allMatch(stack -> AEItemKey.of(stack).equals(iron)
                        && stack.getCount() == (int) Math.min(Integer.MAX_VALUE, amount)),
                "Actual overridden item limit getter stays positive", checks);
        require(limitFluids.size() == (amount == 0 ? 0 : 1)
                        && limitFluids.stream().allMatch(stack -> stack.getFluid() == Fluids.WATER
                        && stack.getAmount() == amount),
                "Actual overridden fluid limit getter stays positive", checks);
    }

    private static void exact(Object slot, AEKey key, BigInteger expected,
                              String label, List<String> checks) {
        var stock = ((IWidePatternSlot) slot).wideInputs().get(key);
        require(stock == null ? expected.signum() == 0 : stock.toBigInteger().equals(expected),
                label + " = " + expected, checks);
    }

    private static Object call(Object target, String name, Class<?>[] parameters, Object... arguments)
            throws Exception {
        var method = target.getClass().getMethod(name, parameters);
        method.setAccessible(true);
        return method.invoke(target, arguments);
    }

    private static Field find(Class<?> type, String name) throws NoSuchFieldException {
        for (; type != null; type = type.getSuperclass()) {
            try { var field = type.getDeclaredField(name); field.setAccessible(true); return field; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }

    private static void require(boolean value, String label, List<String> checks) {
        if (!value) throw new AssertionError(label);
        checks.add(label);
    }
}
