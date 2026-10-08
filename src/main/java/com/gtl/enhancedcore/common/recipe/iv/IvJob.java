package com.gtl.enhancedcore.common.recipe.iv;

import appeng.api.stacks.AEKey;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeSerializer;
import java.math.BigInteger;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;

/** One accepted AE dispatch, including all smart-multiplied operations. Owned only by its buffer. */
public final class IvJob {
    public final UUID id;
    public final int slot;
    public final GTRecipe recipe;
    public final CompoundTag pattern;
    public final Map<AEKey, Long> inventory;
    public final Map<AEKey, Long> virtual;
    public final Map<AEKey, Long> pending = new LinkedHashMap<>();
    public final Map<AEKey, Long> completion = new LinkedHashMap<>();
    public long remaining;
    public long totalOperations;
    public long completedOperations;
    public long deliveredOperations;
    public long pendingOperations;
    public long cancelledOperations;
    public boolean cancelled;
    public final Map<AEKey, Long> refunds = new LinkedHashMap<>();
    public boolean recoveredTail;
    public long parallel;
    public long eut;
    public int duration;
    public int elapsed;
    public BigInteger energyLeft = BigInteger.ZERO;
    public BigInteger energyTotal = BigInteger.ZERO;
    public long supplyPower;
    public String error = "";
    public boolean halted;
    public String cycle = "";
    /** Native machines persist their exact modified tick requirements with the paid batch. */
    public GTRecipe nativeRecipe;
    public long nativeCapacity;
    public boolean nativeWireless;
    public BigInteger nativeWirelessEUt = BigInteger.ZERO;

    public IvJob(int slot, GTRecipe recipe, CompoundTag pattern, Map<AEKey, Long> input,
                 Map<AEKey, Long> virtual, long operations) {
        this(UUID.randomUUID(), slot, recipe, pattern, input, virtual, operations);
    }
    private IvJob(UUID id, int slot, GTRecipe recipe, CompoundTag pattern, Map<AEKey, Long> input,
                  Map<AEKey, Long> virtual, long operations) {
        this.id = id; this.slot = slot; this.recipe = recipe; this.pattern = pattern.copy();
        this.inventory = new LinkedHashMap<>(input); this.virtual = new LinkedHashMap<>(virtual);
        this.remaining = operations;
        this.totalOperations = operations;
    }
    public boolean active() { return parallel > 0; }
    public boolean done() { return remaining == 0 && !active() && pending.isEmpty() && completion.isEmpty() && inventory.isEmpty() && refunds.isEmpty(); }
    /** Stop immediately. Only inventory/refunds and already completed output still belong to the player. */
    public void cancel() {
        validateAccounting();
        Map<AEKey,Long> returned = new LinkedHashMap<>(refunds);
        inventory.forEach((key,count)->returned.merge(key,count,Math::addExact));
        cancelledOperations = Math.addExact(cancelledOperations, Math.addExact(remaining, parallel));
        remaining=0; cancelled=true; refunds.clear(); refunds.putAll(returned); inventory.clear();
        // completion contains rolled, unfinished output, not refundable ingredients.
        completion.clear(); parallel=0; eut=0; duration=0; elapsed=0;
        energyLeft=BigInteger.ZERO; energyTotal=BigInteger.ZERO; supplyPower=0;
        nativeRecipe=null; nativeCapacity=0; nativeWireless=false; nativeWirelessEUt=BigInteger.ZERO;
        cycle=""; error=""; halted=false;
        validateAccounting();
    }
    /** Keep unconsumed tools/chance-input leftovers identifiable until the final batch has finished. */
    public void completeBatch() {
        validateAccounting();
        if (!active() || !pending.isEmpty() || pendingOperations != 0)
            throw new IllegalStateException("Cannot complete an inactive or undelivered batch");
        Map<AEKey,Long> output = new LinkedHashMap<>(completion);
        if (remaining == 0) inventory.forEach((key,count)->output.merge(key,count,Math::addExact));
        completedOperations = Math.addExact(completedOperations, parallel);
        pendingOperations = parallel;
        pending.putAll(output); completion.clear(); parallel=0;
        if (remaining == 0) inventory.clear();
        validateAccounting();
    }
    public void validateAccounting() {
        if (remaining < 0 || parallel < 0 || completedOperations < 0 || deliveredOperations < 0 || pendingOperations < 0
                || cancelledOperations < 0 || Math.addExact(Math.addExact(Math.addExact(remaining, parallel), completedOperations),cancelledOperations) != totalOperations
                || deliveredOperations > completedOperations || pendingOperations > completedOperations - deliveredOperations)
            throw new IllegalStateException("订单数量守恒检查失败，已停止加工");
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id); tag.putInt("slot", slot);
        tag.put("recipe", GTRecipeSerializer.CODEC.encodeStart(NbtOps.INSTANCE, recipe).getOrThrow(false, ignored -> {}));
        tag.putString("recipeId", recipe.id.toString()); tag.put("pattern", pattern.copy());
        tag.put("input", saveStock(inventory)); tag.put("virtual", saveStock(virtual));
        tag.put("pending", saveStock(pending)); tag.put("completion", saveStock(completion));
        tag.putLong("remaining", remaining); tag.putLong("parallel", parallel); tag.putLong("eut", eut);
        tag.putLong("totalOperations", totalOperations); tag.putLong("completedOperations", completedOperations);
        tag.putLong("deliveredOperations", deliveredOperations); tag.putLong("pendingOperations", pendingOperations);
        tag.putBoolean("recoveredTail", recoveredTail);
        tag.putBoolean("cancelled",cancelled); tag.putLong("cancelledOperations",cancelledOperations); tag.put("refunds",saveStock(refunds));
        tag.putInt("duration", duration); tag.putInt("elapsed", elapsed);
        tag.putString("energyLeft", energyLeft.toString()); tag.putString("error", error); tag.putBoolean("halted", halted); tag.putString("cycle", cycle);
        tag.putString("energyTotal",energyTotal.toString()); tag.putLong("supplyPower",supplyPower);
        if (nativeRecipe != null) tag.put("nativeRecipe", GTRecipeSerializer.CODEC.encodeStart(NbtOps.INSTANCE, nativeRecipe).getOrThrow(false, ignored -> {}));
        tag.putBoolean("nativeWireless", nativeWireless);
        tag.putLong("nativeCapacity", nativeCapacity);
        tag.putString("nativeWirelessEUt", nativeWirelessEUt.toString());
        return tag;
    }
    public static IvJob load(CompoundTag tag) {
        GTRecipe recipe = GTRecipeSerializer.CODEC.parse(NbtOps.INSTANCE, tag.get("recipe")).getOrThrow(false, ignored -> {});
        recipe.id = new ResourceLocation(tag.getString("recipeId"));
        IvJob job = new IvJob(tag.getUUID("id"), tag.getInt("slot"), recipe, tag.getCompound("pattern"),
                loadStock(tag.getList("input", Tag.TAG_COMPOUND)), loadStock(tag.getList("virtual", Tag.TAG_COMPOUND)), tag.getLong("remaining"));
        job.pending.putAll(loadStock(tag.getList("pending", Tag.TAG_COMPOUND)));
        job.completion.putAll(loadStock(tag.getList("completion", Tag.TAG_COMPOUND)));
        job.parallel = tag.getLong("parallel"); job.eut = tag.getLong("eut");
        job.cancelled=tag.getBoolean("cancelled"); job.cancelledOperations=tag.getLong("cancelledOperations");
        job.refunds.putAll(loadStock(tag.getList("refunds",Tag.TAG_COMPOUND)));
        if (tag.contains("totalOperations", Tag.TAG_LONG)) {
            job.totalOperations = tag.getLong("totalOperations"); job.completedOperations = tag.getLong("completedOperations");
            job.deliveredOperations = tag.getLong("deliveredOperations"); job.pendingOperations = tag.getLong("pendingOperations");
            job.recoveredTail = tag.getBoolean("recoveredTail");
        } else {
            // test1 did not retain previous batch totals. Track only the outstanding tail, without inventing history.
            job.totalOperations = Math.addExact(job.remaining, job.parallel); job.recoveredTail = true;
        }
        job.duration = tag.getInt("duration"); job.elapsed = tag.getInt("elapsed");
        job.energyLeft = new BigInteger(tag.getString("energyLeft")); job.error = tag.getString("error");
        job.energyTotal = tag.contains("energyTotal") ? new BigInteger(tag.getString("energyTotal")) : BigInteger.valueOf(job.eut).multiply(BigInteger.valueOf(job.duration));
        job.supplyPower = tag.getLong("supplyPower");
        if (tag.contains("nativeRecipe")) job.nativeRecipe = GTRecipeSerializer.CODEC.parse(NbtOps.INSTANCE, tag.get("nativeRecipe")).getOrThrow(false, ignored -> {});
        job.nativeWireless = tag.getBoolean("nativeWireless");
        job.nativeCapacity = tag.getLong("nativeCapacity");
        if (job.nativeCapacity < 0) throw new IllegalArgumentException("Invalid native capacity");
        job.nativeWirelessEUt = tag.contains("nativeWirelessEUt") ? new BigInteger(tag.getString("nativeWirelessEUt")) : BigInteger.ZERO;
        if (job.nativeWirelessEUt.signum() < 0 || job.nativeWireless && job.nativeRecipe == null)
            throw new IllegalArgumentException("Invalid native wireless ledger");
        job.halted = tag.getBoolean("halted");
        job.cycle = tag.getString("cycle");
        if (job.slot < 0 || job.remaining < 0 || job.parallel < 0 || job.eut < 0 || job.duration < 0 || job.elapsed < 0 || job.energyLeft.signum() < 0)
            throw new IllegalArgumentException("Invalid isolated task counters");
        if (job.energyTotal.signum()<0 || job.energyLeft.compareTo(job.energyTotal)>0 || job.supplyPower<0) throw new IllegalArgumentException("Invalid isolated energy ledger");
        if (job.active() && (job.duration < 1 || !job.pending.isEmpty() || job.energyLeft.compareTo((job.nativeWireless ? job.nativeWirelessEUt : BigInteger.valueOf(job.eut)).multiply(BigInteger.valueOf(job.duration))) > 0)
                || !job.active() && !job.completion.isEmpty()) throw new IllegalArgumentException("Invalid isolated task phase");
        job.validateAccounting();
        return job;
    }
    public static ListTag saveStock(Map<AEKey, Long> stock) {
        ListTag result = new ListTag();
        stock.forEach((key, amount) -> {
            if (amount <= 0) throw new IllegalArgumentException("Nonpositive isolated stock");
            CompoundTag entry = new CompoundTag(); entry.put("key", key.toTagGeneric()); entry.putLong("amount", amount); result.add(entry);
        });
        return result;
    }
    public static Map<AEKey, Long> loadStock(ListTag list) {
        Map<AEKey, Long> result = new LinkedHashMap<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            AEKey key = AEKey.fromTagGeneric(entry.getCompound("key")); long amount = entry.getLong("amount");
            if (key == null || amount <= 0) throw new IllegalArgumentException("Unresolvable isolated stock");
            result.merge(key, amount, Math::addExact);
        }
        return result;
    }
}
