package com.gtl.enhancedcore.audit;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.gregtechceu.gtceu.api.item.tool.ToolHelper;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import com.gtl.enhancedcore.common.recipe.iv.IvBufferMethods;
import com.gtl.enhancedcore.common.recipe.iv.IvBufferTransfer;
import com.gtl.enhancedcore.common.recipe.iv.IvBuffers;
import com.gtl.enhancedcore.common.recipe.iv.IvJob;
import com.gtl.enhancedcore.common.recipe.iv.IvSlotAccess;
import com.gtladd.gtladditions.common.machine.multiblock.part.MESuperPatternBufferPartMachine;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.registries.ForgeRegistries;
import org.gtlcore.gtlcore.common.item.MEPatternBufferCutBehavior;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;
import org.gtlcore.gtlcore.integration.ae2.pattern.PatternQuickUploadMetadata;

/** Runs against an empty, real formed IV assembly. Only the opt-in audit helper contains this class. */
public final class IvPatternBufferTransferChecks {
    private static int checks;
    private IvPatternBufferTransferChecks() {}

    private static final class MessagePlayer extends FakePlayer {
        private String message;
        private MessagePlayer(ServerLevel level) {
            super(level, new GameProfile(new UUID(0, 422), "IvTransferAudit"));
        }
        @Override public void displayClientMessage(Component message, boolean actionBar) {
            this.message = Component.Serializer.toJson(message);
        }
    }

    public static int run(Object controller, Object part) throws Exception {
        int start = checks;
        var machine = (WorkableElectricMultiblockMachine) controller;
        var buffer = (MEPatternBufferPartMachine) part;
        var state = IvBuffers.state(buffer);
        check(machine.isFormed() && IvBuffers.bind((MESuperPatternBufferPartMachine) buffer, machine), "Probe requires an exclusive formed IV assembly");
        check(IvBufferTransfer.allowPaste(buffer, new CompoundTag()), "Probe requires drained storage");
        for (int i = 0; i < buffer.getPatternInventory().getSlots(); i++)
            check(buffer.getPatternInventory().getStackInSlot(i).isEmpty(), "Probe cannot alter existing patterns");
        CompoundTag originalState = state.save();
        String originalName = buffer.getCustomName();
        var world = (ServerLevel) buffer.getLevel();
        var player = new MessagePlayer(world);
        var tool = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("gtlcore", "me_pattern_buffer_cut")));
        var context = new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(buffer.getPos()), Direction.UP, buffer.getPos(), false));
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        var key = AEItemKey.of(new ItemStack(Items.APPLE));
        ItemStack pattern = PatternDetailsHelper.encodeProcessingPattern(new GenericStack[]{new GenericStack(key, 1)},
                new GenericStack[]{new GenericStack(AEItemKey.of(new ItemStack(Items.GOLD_INGOT)), 1)});
        PatternQuickUploadMetadata.writeRecipeTypeId(pattern, machine.getRecipeTypes()[0].registryName);
        try {
            buffer.getPatternInventory().setStackInSlot(0, pattern.copy());
            ((IvBufferMethods) buffer).iv$patternChanged(0);
            UUID identity = state.identity;
            String owner = state.owner;
            state.accepting = false;
            GTRecipe recipe = GTRecipeBuilder.of(new ResourceLocation("gtl_enhancedcore", "transfer_guard"), machine.getRecipeTypes()[0])
                    .inputItems(new ItemStack(Items.APPLE)).outputItems(new ItemStack(Items.GOLD_INGOT)).duration(400).EUt(32).buildRawRecipe();
            var constructor = IvJob.class.getDeclaredConstructor(UUID.class, int.class, GTRecipe.class, CompoundTag.class,
                    Map.class, Map.class, long.class);
            constructor.setAccessible(true);
            IvJob job = constructor.newInstance(new UUID(0, 423), 0, recipe, pattern.save(new CompoundTag()),
                    Map.of(key, 5L), Map.of(), 5L);
            state.jobs.add(job);
            String ledger = state.save().toString();
            player.setShiftKeyDown(true);
            MEPatternBufferCutBehavior.INSTANCE.onItemUseFirst(tool, context);
            check(!MEPatternBufferCutBehavior.hasCutData(tool) && ledger.equals(state.save().toString()), "Busy cut moved or changed the ledger");
            check(player.message.contains("gtl_enhancedcore.diagnostic.iv_cut"), "Tool hides the busy-cut reason");
            check(ItemStack.isSameItemSameTags(pattern, buffer.getPatternInventory().getStackInSlot(0)), "Rejected cut cleared the pattern");
            check(!buffer.pasteFromTag(new CompoundTag()), "Busy target accepted paste");
            check(ledger.equals(state.save().toString()), "Rejected paste changed the ledger");
            state.jobs.clear();

            CompoundTag healthy = state.save();
            state.load(new CompoundTag());
            check(!buffer.cutToTag(new CompoundTag()).contains("cut") && !buffer.pasteFromTag(new CompoundTag()), "Quarantined ledger moved");
            check(!state.healthy(), "Transfer guard cleared quarantine");
            state.load(healthy);
            Object slot = ((Object[]) buffer.getInternalInventory())[0];
            @SuppressWarnings("unchecked")
            var slotItems = (Object2LongMap<AEItemKey>) slot.getClass().getMethod("getItemInventory").invoke(slot);
            slotItems.put(key, 7L);
            check(!buffer.cutToTag(new CompoundTag()).contains("cut") && !buffer.pasteFromTag(new CompoundTag()), "Legacy input was exposed by transfer");
            check(slotItems.getLong(key) == 7, "Rejected transfer consumed legacy input");
            slotItems.clear();
            buffer.getBuffer().put(key, 11L);
            check(!buffer.cutToTag(new CompoundTag()).contains("cut") && !buffer.pasteFromTag(new CompoundTag()), "Legacy output was exposed by transfer");
            check(buffer.getBuffer().getLong(key) == 11, "Rejected transfer consumed legacy output");
            buffer.getBuffer().clear();

            buffer.getSharedCatalystInventory().setStackInSlot(0, new ItemStack(Items.DIAMOND, 7));
            buffer.getSharedCatalystTank().setFluidInTank(0, FluidStack.create(Fluids.WATER, 37));
            buffer.getSharedCircuitInventory().setStackInSlot(0, new ItemStack(Items.REDSTONE));
            check(!buffer.pasteFromTag(new CompoundTag()), "Paste overwrote shared physical stock");
            check(buffer.getSharedCatalystInventory().getStackInSlot(0).getCount() == 7
                    && buffer.getSharedCatalystTank().getFluidInTank(0).getAmount() == 37,
                    "Rejected paste changed shared stock");
            MEPatternBufferCutBehavior.INSTANCE.onItemUseFirst(tool, context);
            check(MEPatternBufferCutBehavior.hasCutData(tool), "Idle formed source could not cut");
            var payload = ToolHelper.getBehaviorsTag(tool).getCompound("cut");
            check(!payload.contains(IvBuffers.SAVE_KEY), "Tool copied the isolated ledger");
            check(buffer.getPatternInventory().getStackInSlot(0).isEmpty()
                    && buffer.getSharedCatalystInventory().getStackInSlot(0).isEmpty()
                    && buffer.getSharedCatalystTank().getFluidInTank(0).isEmpty()
                    && buffer.getSharedCircuitInventory().getStackInSlot(0).isEmpty(), "Cut did not remove exactly the transferred contents");
            check(buffer.getAvailablePatterns().isEmpty(), "Cut kept publishing the old route");
            check(identity.equals(state.identity) && owner.equals(state.owner) && !state.accepting, "Cut changed source identity/owner/admission state");

            CompoundTag proxyPayload = payload.copy();
            proxyPayload.putLongArray("proxies", new long[]{buffer.getPos().asLong()});
            check(!buffer.pasteFromTag(proxyPayload), "IV target accepted a proxy binding");
            CompoundTag ledgerPayload = payload.copy();
            ledgerPayload.put(IvBuffers.SAVE_KEY, healthy);
            check(!buffer.pasteFromTag(ledgerPayload), "IV target accepted a ledger payload");
            String held = ToolHelper.getBehaviorsTag(tool).toString();
            buffer.getSharedCatalystInventory().setStackInSlot(0, new ItemStack(Items.EMERALD, 3));
            player.setShiftKeyDown(false);
            MEPatternBufferCutBehavior.INSTANCE.onItemUseFirst(tool, context);
            check(held.equals(ToolHelper.getBehaviorsTag(tool).toString()), "Failed paste consumed the tool payload");
            check(buffer.getSharedCatalystInventory().getStackInSlot(0).getCount() == 3, "Failed paste lost target stock");
            check(player.message.contains("gtl_enhancedcore.diagnostic.iv_paste"), "Tool hides the blocked-paste reason");
            buffer.getSharedCatalystInventory().setStackInSlot(0, ItemStack.EMPTY);
            MEPatternBufferCutBehavior.INSTANCE.onItemUseFirst(tool, context);
            check(!MEPatternBufferCutBehavior.hasCutData(tool), "Successful paste did not consume the payload");
            check(machine.isFormed(), "Configuration transfer broke formation");
            check(ItemStack.isSameItemSameTags(pattern, buffer.getPatternInventory().getStackInSlot(0)), "Paste lost pattern metadata");
            check(buffer.getSharedCatalystInventory().getStackInSlot(0).getCount() == 7
                    && buffer.getSharedCatalystTank().getFluidInTank(0).getAmount() == 37
                    && buffer.getSharedCircuitInventory().getStackInSlot(0).is(Items.REDSTONE), "Paste did not conserve shared catalysts/circuit");
            check(identity.equals(state.identity) && owner.equals(state.owner) && !state.accepting && state.jobs.isEmpty(),
                    "Paste imported controller ownership, identity, admission or orders");
            List<Component> tips = new ArrayList<>();
            MEPatternBufferCutBehavior.INSTANCE.appendHoverText(tool, world, tips, TooltipFlag.Default.NORMAL);
            check(tips.stream().anyMatch(tip -> Component.Serializer.toJson(tip).contains("pattern_buffer_cut.iv_idle_transfer")), "Tool does not explain IV transfer conditions");
            return checks - start;
        } finally {
            state.jobs.clear();
            for (int i = 0; i < buffer.getPatternInventory().getSlots(); i++) buffer.getPatternInventory().setStackInSlot(i, ItemStack.EMPTY);
            for (Object slot : buffer.getInternalInventory()) ((IvSlotAccess) slot).iv$discardStock();
            buffer.getBuffer().clear();
            for (int i = 0; i < buffer.getSharedCatalystInventory().getSlots(); i++) buffer.getSharedCatalystInventory().setStackInSlot(i, ItemStack.EMPTY);
            for (int i = 0; i < buffer.getSharedCatalystTank().getTanks(); i++) buffer.getSharedCatalystTank().setFluidInTank(i, FluidStack.empty());
            for (int i = 0; i < buffer.getSharedCircuitInventory().getSlots(); i++) buffer.getSharedCircuitInventory().setStackInSlot(i, ItemStack.EMPTY);
            state.load(originalState);
            buffer.setCustomName(originalName);
            ((IvBufferMethods) buffer).iv$refreshPatterns();
            buffer.markDirty();
        }
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
