package com.gtl.enhancedcore.common.machine.galaxy;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.IMachineLife;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockDisplayText;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import org.gtlcore.gtlcore.api.machine.multiblock.NoEnergyMultiblockMachine;
import com.hepdd.gtmthings.api.misc.WirelessEnergyManager;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.gtlcore.gtlcore.utils.MachineIO;

/** Eye-of-Harmony ownership/payment path, charged once per running server tick. */
public class StellarVortexSpaceStationMachine extends NoEnergyMultiblockMachine implements IMachineLife,
        com.gtladd.gtladditions.api.machine.IAstralArrayInteractionMachine {
    private static final ManagedFieldHolder FIELDS = new ManagedFieldHolder(StellarVortexSpaceStationMachine.class, NoEnergyMultiblockMachine.MANAGED_FIELD_HOLDER);
    public static final long EU_PER_TICK = GTValues.V[GTValues.MAX];
    @Persisted @DescSynced private UUID userId;
    @Persisted @DescSynced private int astralArrayCount;
    @DescSynced private boolean fieldActive;
    @DescSynced private long fieldStart;
    @DescSynced private boolean circuitReady;
    @DescSynced private int fieldFailure;
    private long checkedTick = Long.MIN_VALUE;
    public StellarVortexSpaceStationMachine(IMachineBlockEntity holder, Object... args) { super(holder, args); }
    @Override public ManagedFieldHolder getFieldHolder() { return FIELDS; }
    // Like HarmonyMachine: there is no physical EU recipe handler. The single payment
    // path below powers a continuous field; the recipe type documents its requirements.
    @Override public RecipeLogic createRecipeLogic(Object... args) { return new FieldLogic(this); }
    @Override public void onMachinePlaced(LivingEntity placer, ItemStack stack) {
        if (placer != null && !isRemote()) { userId = placer.getUUID(); markDirty(); }
    }
    public UUID getUserId() { return userId; }
    @Override public void onLoad() {
        super.onLoad();
        if (!isRemote()) StellarFieldTicker.register(this);
    }
    @Override public void onUnload() {
        stopField();
        StellarFieldTicker.unregister(this);
        super.onUnload();
    }
    @Override public void onStructureInvalid() { stopField(); circuitReady = false; super.onStructureInvalid(); }
    @Override public void onPartUnload() { stopField(); circuitReady = false; super.onPartUnload(); }
    private void stopField() { fieldActive = false; }
    void tickField() {
        boolean active = ensurePaid();
        if (recipeLogic.isWorkingEnabled()) {
            if (active) recipeLogic.setStatus(RecipeLogic.Status.WORKING);
            else recipeLogic.setWaiting(failureText());
        }
    }
    /** The world ticker owns payment; repeated invocations in one world tick are idempotent. */
    public boolean ensurePaid() {
        if (isRemote()) return fieldActive;
        long now = currentFieldTick();
        if (checkedTick == now) return fieldActive;
        checkedTick = now;
        boolean wasActive = fieldActive;
        fieldActive = false;
        fieldFailure = 0;
        if (!isFormed() || !isRecipeLogicAvailable() || !recipeLogic.isWorkingEnabled()) return false;
        // Only the server inspects recipe proxies. Clients do not own the input
        // capability map or the server's multiblock pattern validation state.
        circuitReady = circuitAvailable();
        if (userId == null) { fieldFailure = 1; return false; }
        if (!getParts().stream().anyMatch(p -> p instanceof GalaxyConnectionHatch h && h.isSource() && h.isWorkingEnabled())) {
            fieldFailure = 2; return false;
        }
        if (!circuitReady) { fieldFailure = 3; return false; }
        fieldActive = payFieldTick();
        if (!fieldActive) fieldFailure = 4;
        if (fieldActive && !wasActive) fieldStart = now;
        return fieldActive;
    }
    protected long currentFieldTick() { return getLevel().getGameTime(); }
    protected boolean circuitAvailable() { return MachineIO.notConsumableCircuit(this, 1); }
    protected boolean payFieldTick() { return WirelessEnergyManager.addEUToGlobalEnergyMap(userId, -EU_PER_TICK, this); }
    public boolean isFieldActive() {
        if (isRemote()) return fieldActive;
        return fieldActive && isFormed() && isRecipeLogicAvailable() && recipeLogic.isWorkingEnabled();
    }
    private Component failureText() {
        return Component.translatable(switch (fieldFailure) {
            case 1 -> "gtl_enhancedcore.galaxy.owner_missing";
            case 2 -> "gtl_enhancedcore.galaxy.source_missing";
            case 3 -> "gtl_enhancedcore.galaxy.circuit_missing";
            case 4 -> "gtl_enhancedcore.galaxy.power_missing";
            default -> "gtl_enhancedcore.galaxy.offline";
        });
    }
    public long getFieldStart() { return fieldStart; }
    @Override public int getAstralArrayCount() { return astralArrayCount; }
    @Override public int increaseAstralArrayCount(int amount) {
        if (amount <= 0 || astralArrayCount >= 64) return 0;
        int accepted = Math.min(amount, 64 - astralArrayCount);
        astralArrayCount += accepted;
        markDirty();
        return accepted;
    }
    @Override public void addDisplayText(List<Component> text) {
        MultiblockDisplayText.builder(text, isFormed()).setWorkingStatus(recipeLogic.isWorkingEnabled(), isFieldActive())
                .addMachineModeLine(getRecipeType()).addWorkingStatusLine();
        text.add(Component.translatable("gtl_enhancedcore.galaxy.circuit_status",
                circuitReady ? Component.translatable("gtl_enhancedcore.galaxy.circuit_ready")
                        : Component.translatable("gtl_enhancedcore.galaxy.circuit_missing")));
        text.add(Component.translatable("gtl_enhancedcore.galaxy.array_status", astralArrayCount, 64));
        text.add(Component.translatable("gtl_enhancedcore.galaxy.power_status"));
        text.add(Component.translatable(userId == null ? "gtl_enhancedcore.galaxy.owner_missing" : isFieldActive() ? "gtl_enhancedcore.galaxy.online" : "gtl_enhancedcore.galaxy.offline"));
        if (isFormed() && recipeLogic.isWorkingEnabled() && fieldFailure != 0) text.add(failureText());
        getDefinition().getAdditionalDisplay().accept(this, text);
        getParts().forEach(part -> part.addMultiText(text));
    }
    public boolean supportsBatchProcessing() { return false; }
    public boolean canConfigureBatchProcessing() { return false; }
    private static final class FieldLogic extends RecipeLogic {
        private static final ManagedFieldHolder FIELDS = new ManagedFieldHolder(FieldLogic.class, RecipeLogic.MANAGED_FIELD_HOLDER);
        FieldLogic(StellarVortexSpaceStationMachine machine) { super(machine); }
        @Override public ManagedFieldHolder getFieldHolder() { return FIELDS; }
        // The world ticker owns field updates even when GTLCore skips a machine
        // tick under its 40 ms wall-clock throttle. No recipe ticker is needed.
        @Override public void updateTickSubscription() { }
        @Override public void setWorkingEnabled(boolean enabled) {
            super.setWorkingEnabled(enabled);
            if (!enabled) ((StellarVortexSpaceStationMachine)getMachine()).stopField();
        }
        @Override public void serverTick() { }
        @Override public void findAndHandleRecipe() { }
        @Override public void onRecipeFinish() { }
        @Override public boolean isActive() { return ((StellarVortexSpaceStationMachine)getMachine()).isFieldActive(); }
    }
}
