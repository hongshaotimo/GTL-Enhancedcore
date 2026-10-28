package com.gtl.enhancedcore.audit;

import appeng.items.tools.powered.WirelessTerminalItem;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gtl.enhancedcore.common.cloud.CloudPatternLibraryMachine;
import com.gtl.enhancedcore.common.cloud.CloudWirelessBinding;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.registries.ForgeRegistries;

/** Isolated Forge checks. No registration or gameplay helper enters the production artifact. */
public final class CloudWirelessBindingRuntimeChecks {
    private CloudWirelessBindingRuntimeChecks() {}
    private static void require(boolean condition, String label, List<String> checks) {
        if (!condition) throw new AssertionError("wireless_binding:" + label);
        checks.add("wireless_binding:" + label);
    }
    public static void run(ServerLevel level, List<String> checks) {
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("51477dfc-94a1-42ba-8596-a080a57af348"), "CloudWirelessAudit"));
        player.getInventory().clearContent();
        require(!CloudWirelessBinding.isTerminal(new ItemStack(Items.STICK)), "ordinary_item_not_terminal", checks);
        require(CloudWirelessBinding.find(player, null).state() == CloudWirelessBinding.State.MISSING,
                "no_terminal_fails_closed", checks);
        int recognized = 0;
        for (var item : ForgeRegistries.ITEMS.getValues()) {
            if (!(item instanceof WirelessTerminalItem)) continue;
            ItemStack terminal = new ItemStack(item);
            CompoundTag before = terminal.save(new CompoundTag());
            require(CloudWirelessBinding.isTerminal(terminal), "actual_terminal_type_" + ForgeRegistries.ITEMS.getKey(item), checks);
            require(CloudWirelessBinding.find(player, terminal).state() != CloudWirelessBinding.State.READY,
                    "unlinked_terminal_rejected_" + ForgeRegistries.ITEMS.getKey(item), checks);
            require(before.equals(terminal.save(new CompoundTag())), "terminal_read_preserves_nbt_" + ForgeRegistries.ITEMS.getKey(item), checks);
            recognized++;
        }
        require(recognized >= 2, "actual_ae_native_terminal_registry_present", checks);
        BlockPos distant = new BlockPos(29_000_000, 64, 29_000_000);
        require(level.getChunkSource().getChunkNow(distant.getX() >> 4, distant.getZ() >> 4) == null,
                "fixture_remote_chunk_initially_unloaded", checks);
        CompoundTag address = new CompoundTag();
        address.putString("dimension", level.dimension().location().toString());
        address.putLong("position", distant.asLong()); address.putUUID("nodeOwner", player.getUUID());
        address.putString("blockEntity", "ae2:wireless_access_point");
        require(CloudWirelessBinding.resolve(level, address, player.getUUID()) == null,
                "unloaded_address_stays_offline", checks);
        require(level.getChunkSource().getChunkNow(distant.getX() >> 4, distant.getZ() >> 4) == null,
                "resolve_does_not_force_load_chunk", checks);
        require(CloudWirelessBinding.resolve(level, new CompoundTag(), player.getUUID()) == null,
                "missing_address_identity_denied", checks);
        var definition = GTRegistries.MACHINES.get(new ResourceLocation("gtl_enhancedcore", "cloud_pattern_library"));
        var holder = (MetaMachineBlockEntity) definition.getBlockEntityType().create(new BlockPos(120, 64, 120), definition.defaultBlockState());
        holder.setLevel(level);
        try {
            var main = (CloudPatternLibraryMachine) holder.getMetaMachine();
            main.onMachinePlaced(player, ItemStack.EMPTY);
            require(main.owner().equals(player.getUUID()) && main.libraryId() == null,
                    "placement_without_terminal_does_not_create_service_library", checks);
            require(main.serviceGrid() == null && !main.networkAvailable(null), "unbound_controller_cannot_serve", checks);
            CompoundTag drop = new CompoundTag(); main.saveCustomPersistedData(drop, true);
            require(!drop.contains("wirelessBinding") && !drop.contains("libraryId") && !drop.contains("owner"),
                    "drops_carry_no_wireless_authority", checks);
        } finally { holder.setLevel(null); }
    }
}
