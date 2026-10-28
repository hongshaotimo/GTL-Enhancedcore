package com.gtl.enhancedcore.common.machine.galaxy;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.multiblock.part.TieredIOPartMachine;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.registries.ForgeRegistries;

/** Input-class control port: flash pairing data is independent of any existing flash payload. */
public class GalaxyConnectionHatch extends TieredIOPartMachine implements com.gregtechceu.gtceu.api.machine.feature.IInteractedMachine {
    private static final ManagedFieldHolder FIELDS = new ManagedFieldHolder(GalaxyConnectionHatch.class, TieredIOPartMachine.MANAGED_FIELD_HOLDER);
    private static final String LINK_TAG = "gtl_enhancedcore_galaxy";
    private final boolean source;
    @Persisted private UUID sourceId;
    @Persisted private UUID targetId;
    @Persisted @DescSynced private String sourceDimension = "";
    @Persisted private long sourcePosition;
    @Persisted @DescSynced private boolean paired;
    private com.gregtechceu.gtceu.api.machine.TickableSubscription linkSubscription;
    private boolean lastLinkActive;

    public GalaxyConnectionHatch(IMachineBlockEntity holder, boolean source) {
        super(holder, GTValues.OpV, IO.IN);
        this.source = source;
    }
    @Override public ManagedFieldHolder getFieldHolder() { return FIELDS; }
    public boolean isSource() { return source; }
    public static boolean isConnectionPortBlock(Block block) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
        return id != null && id.getNamespace().equals("gtl_enhancedcore")
                && (id.getPath().equals("galaxy_connection_source_hatch")
                || id.getPath().equals("galaxy_connection_target_hatch"));
    }
    @Override public void onLoad() {
        super.onLoad();
        if (!isRemote() && source && sourceId == null) { sourceId = UUID.randomUUID(); markDirty(); }
        if (!isRemote() && !source && targetId == null) { targetId = UUID.randomUUID(); markDirty(); }
        if (!isRemote() && !source) linkSubscription = subscribeServerTick(linkSubscription, () -> {
            if (getOffsetTimer() % 5 != 0) return;
            boolean active = linkActive();
            if (active == lastLinkActive) return;
            lastLinkActive = active;
            for (var controller : GalaxyLinks.controllers(this)) {
                if (controller instanceof com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine machine) {
                    machine.getRecipeLogic().markLastRecipeDirty();
                    machine.getRecipeLogic().updateTickSubscription();
                }
            }
        });
    }
    @Override public void onUnload() {
        if (linkSubscription != null) { linkSubscription.unsubscribe(); linkSubscription = null; }
        super.onUnload();
    }
    public boolean linkActive() {
        var station = sourceStation();
        return station != null && station.isFieldActive();
    }
    public StellarVortexSpaceStationMachine sourceStation() {
        if (isRemote() || !isWorkingEnabled() || !paired || sourceId == null
                || !(getLevel() instanceof ServerLevel level)) return null;
        ResourceLocation dimension = ResourceLocation.tryParse(sourceDimension);
        if (dimension == null) return null;
        var sourceLevel = level.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        if (sourceLevel == null) return null;
        var pos = BlockPos.of(sourcePosition);
        var chunk = sourceLevel.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null || !(chunk.getBlockEntity(pos) instanceof IMachineBlockEntity holder)
                || !(holder.getMetaMachine() instanceof GalaxyConnectionHatch hatch) || !hatch.source
                || !hatch.isWorkingEnabled() || !sourceId.equals(hatch.sourceId)) return null;
        var controllers = GalaxyLinks.controllers(hatch);
        return controllers.size() == 1 && controllers.getFirst() instanceof StellarVortexSpaceStationMachine station
                ? station : null;
    }
    @Override public InteractionResult onUse(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        var stack = player.getItemInHand(hand);
        // The same flash memory used by GTLCore's wireless optical data hatches.
        if (!isDataStick(stack)) return InteractionResult.SUCCESS;
        if (world.isClientSide) return InteractionResult.SUCCESS;
        if (!source && player.isShiftKeyDown()) {
            paired = false; sourceId = null; sourceDimension = ""; sourcePosition = 0; markDirty();
            refreshControllers();
            player.displayClientMessage(Component.translatable("gtl_enhancedcore.galaxy.unpaired"), true);
            return InteractionResult.SUCCESS;
        }
        var tag = stack.getTag();
        var data = tag != null && tag.contains(LINK_TAG, Tag.TAG_COMPOUND) ? tag.getCompound(LINK_TAG) : null;
        if (validEndpoint(data) && data.getBoolean("source") != source) {
            if (source) {
                var target = findTarget((ServerLevel) world, data);
                if (target == null) {
                    player.displayClientMessage(Component.translatable("gtl_enhancedcore.galaxy.invalid_flash"), true);
                    return InteractionResult.FAIL;
                }
                target.connectTo(endpointData());
                stack.getOrCreateTag().put(LINK_TAG, endpointData());
            } else {
                connectTo(data);
            }
            player.displayClientMessage(Component.translatable("gtl_enhancedcore.galaxy.paired"), true);
            return InteractionResult.SUCCESS;
        }
        stack.getOrCreateTag().put(LINK_TAG, endpointData());
        player.displayClientMessage(Component.translatable(source
                ? "gtl_enhancedcore.galaxy.flash_written" : "gtl_enhancedcore.galaxy.target_written"), true);
        return InteractionResult.SUCCESS;
    }
    @Override public boolean shouldOpenUI(Player player, InteractionHand hand, BlockHitResult hit) { return false; }

    public static boolean isDataStick(ItemStack stack) { return stack.is(GTItems.TOOL_DATA_STICK.get()); }

    private CompoundTag endpointData() {
        if (source && sourceId == null) { sourceId = UUID.randomUUID(); markDirty(); }
        if (!source && targetId == null) { targetId = UUID.randomUUID(); markDirty(); }
        var data = new CompoundTag();
        data.putUUID("id", source ? sourceId : targetId);
        data.putBoolean("source", source);
        data.putString("dimension", getLevel().dimension().location().toString());
        data.putLong("position", getPos().asLong());
        return data;
    }

    private static boolean validEndpoint(CompoundTag data) {
        return data != null && data.hasUUID("id") && data.contains("source", Tag.TAG_BYTE)
                && data.contains("position", Tag.TAG_LONG)
                && ResourceLocation.tryParse(data.getString("dimension")) != null;
    }

    private static GalaxyConnectionHatch findTarget(ServerLevel level, CompoundTag data) {
        var targetLevel = level.getServer().getLevel(ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.tryParse(data.getString("dimension"))));
        if (targetLevel == null) return null;
        var pos = BlockPos.of(data.getLong("position"));
        var chunk = targetLevel.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null || !(chunk.getBlockEntity(pos) instanceof IMachineBlockEntity holder)
                || !(holder.getMetaMachine() instanceof GalaxyConnectionHatch hatch) || hatch.source
                || !data.getUUID("id").equals(hatch.targetId)) return null;
        return hatch;
    }

    private void connectTo(CompoundTag data) {
        sourceId = data.getUUID("id"); sourceDimension = data.getString("dimension");
        sourcePosition = data.getLong("position"); paired = true; markDirty();
        refreshControllers();
    }

    private void refreshControllers() {
        lastLinkActive = linkActive();
        for (var controller : GalaxyLinks.controllers(this)) {
            if (controller instanceof com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine machine) {
                machine.getRecipeLogic().markLastRecipeDirty();
                machine.getRecipeLogic().updateTickSubscription();
            }
        }
    }
}
