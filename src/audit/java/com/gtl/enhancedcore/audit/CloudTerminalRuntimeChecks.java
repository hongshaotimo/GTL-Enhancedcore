package com.gtl.enhancedcore.audit;

import appeng.helpers.IPatternTerminalLogicHost;
import appeng.parts.encoding.PatternEncodingLogic;
import com.gtl.enhancedcore.common.cloud.CloudPatternLibraryMachine;
import com.gtl.enhancedcore.common.cloud.store.CloudPatternStore;
import com.gtl.enhancedcore.common.cloud.transfer.*;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.FakePlayer;

/** Real AE inventory/serialization checks; called only in the isolated Forge fixture. */
public final class CloudTerminalRuntimeChecks {
    private CloudTerminalRuntimeChecks() {}
    private static final class Host implements IPatternTerminalLogicHost {
        final ServerLevel level;
        final PatternEncodingLogic logic;
        Host(ServerLevel level) { this.level = level; logic = new PatternEncodingLogic(this); }
        public PatternEncodingLogic getLogic() { return logic; }
        public Level getLevel() { return level; }
        public void markForSave() {}
    }
    private static void check(boolean value, String label, List<String> failures) { if (!value) throw new AssertionError("terminal_upload:" + label); failures.add("terminal_upload:" + label); }
    public static void run(ServerLevel level, CloudPatternLibraryMachine main, FakePlayer player, ItemStack pattern, List<String> failures) {
        try {
            player = net.minecraftforge.common.util.FakePlayerFactory.get(level, new com.mojang.authlib.GameProfile(UUID.fromString("e55da1de-7d39-439d-8fba-927cbcab0cc2"), "TerminalAudit"));
            UUID libraryId = CloudPatternStore.get(level).defaultLibrary(player.getUUID());
            var library = CloudPatternStore.get(level).library(libraryId);
            int initial = library.entryCount();
            Host host = new Host(level); host.logic.getEncodedPatternInv().setItemDirect(0, pattern.copyWithCount(1));
            UUID deniedLibrary = CloudPatternStore.get(level).defaultLibrary(UUID.randomUUID());
            boolean denied = false;
            try { CloudTerminalUpload.upload(player, deniedLibrary, host.logic); } catch (SecurityException expected) { denied = true; }
            check(denied && !host.logic.getEncodedPatternInv().isEmpty() && !CloudTerminalUpload.quarantined(host.logic)
                    && !((CloudTerminalSource)host.logic).cloud$sourceState().hasUUID("identity"), "denied_preflight_preserves_original", failures);
            var crafting = (net.minecraft.world.item.crafting.CraftingRecipe)level.getRecipeManager()
                    .byKey(new net.minecraft.resources.ResourceLocation("minecraft", "oak_planks")).orElseThrow();
            ItemStack[] inputs = new ItemStack[9]; java.util.Arrays.fill(inputs, ItemStack.EMPTY);
            inputs[0] = new ItemStack(net.minecraft.world.item.Items.OAK_LOG);
            ItemStack craftingPattern = appeng.api.crafting.PatternDetailsHelper.encodeCraftingPattern(crafting, inputs,
                    new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS, 4), false, false);
            host.logic.getEncodedPatternInv().setItemDirect(0, craftingPattern);
            boolean craftingRejected = false;
            try { CloudTerminalUpload.upload(player, libraryId, host.logic); } catch (IllegalArgumentException expected) { craftingRejected = true; }
            check(craftingRejected && ItemStack.matches(host.logic.getEncodedPatternInv().getStackInSlot(0), craftingPattern)
                    && !CloudTerminalUpload.quarantined(host.logic) && library.entryCount() == initial,
                    "crafting_preflight_preserves_original", failures);
            host.logic.getEncodedPatternInv().setItemDirect(0, pattern.copyWithCount(1));
            ((CloudTerminalSource)host.logic).cloud$sourceState().putUUID("identity", UUID.randomUUID());
            CompoundTag before = new CompoundTag(); host.logic.writeToNBT(before);
            CloudTerminalUpload.upload(player, libraryId, host.logic);
            check(host.logic.getEncodedPatternInv().isEmpty() && library.entryCount() == initial + 1, "one_original_debited", failures);
            CompoundTag after = new CompoundTag(); host.logic.writeToNBT(after);
            Host reloaded = new Host(level); reloaded.logic.readFromNBT(after);
            check(reloaded.logic.getEncodedPatternInv().isEmpty() && library.entryCount() == initial + 1, "processed_replay_idempotent", failures);
            Host rollback = new Host(level); rollback.logic.readFromNBT(before);
            check(rollback.logic.getEncodedPatternInv().isEmpty() && !CloudTerminalUpload.quarantined(rollback.logic)
                    && library.entryCount() == initial + 1, "exact_rollback_redebit", failures);
            boolean emptyRejected = false;
            try { CloudTerminalUpload.upload(player, libraryId, host.logic); } catch (IllegalArgumentException expected) { emptyRejected = true; }
            check(emptyRejected && library.entryCount() == initial + 1, "empty_double_click_no_copy", failures);
            // A committed receipt lets recovery preserve an unrelated pattern now in the encoded slot.
            Host changed = new Host(level);
            changed.logic.getEncodedPatternInv().setItemDirect(0, new ItemStack(net.minecraft.world.item.Items.DIRT));
            CompoundTag different = new CompoundTag(); changed.logic.writeToNBT(different);
            different.put("gtlCloudSource", before.getCompound("gtlCloudSource").copy());
            changed.logic.readFromNBT(different);
            check(!CloudTerminalUpload.quarantined(changed.logic)
                    && ItemStack.matches(changed.logic.getEncodedPatternInv().getStackInSlot(0), new ItemStack(net.minecraft.world.item.Items.DIRT))
                    && ((CloudTerminalSource)changed.logic).cloud$sourceState().getLong("processedGeneration") == 1
                    && library.entryCount() == initial + 1, "acknowledged_source_mismatch_preserves_current", failures);
            for (int generation = 2; generation <= 3; generation++) {
                host.logic.getEncodedPatternInv().setItemDirect(0, pattern.copyWithCount(1));
                CloudTerminalUpload.upload(player, libraryId, host.logic);
            }
            var sourceState = ((CloudTerminalSource)host.logic).cloud$sourceState();
            check(sourceState.getLong("processedGeneration") == 3 && !sourceState.contains("processed")
                    && sourceState.toString().length() < 1024, "constant_source_marker_size", failures);
            var directory = level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .resolve("data/gtl_cloud_terminal").resolve(sourceState.getUUID("identity").toString());
            try (var files = java.nio.file.Files.list(directory)) {
                check(files.filter(java.nio.file.Files::isRegularFile).count() == 2, "bounded_head_and_previous_files", failures);
            }
            Host old = new Host(level); old.logic.readFromNBT(before);
            check(CloudTerminalUpload.quarantined(old.logic) && old.logic.getEncodedPatternInv().isEmpty()
                    && library.entryCount() == initial + 3, "multi_generation_rollback_fails_closed", failures);
        } catch (Throwable failure) { throw new AssertionError("Terminal upload runtime checks failed", failure); }
    }
}
