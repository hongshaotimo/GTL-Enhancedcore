package com.gtl.enhancedcore.audit;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gtl.enhancedcore.common.machine.galaxy.GalaxyConnectionHatch;
import com.mojang.authlib.GameProfile;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import sun.misc.Unsafe;

/** Real onUse pairing checks with uninitialized fixtures; no server or world is opened. */
public final class GalaxyPairingChecks {
    private static final String LINK_TAG = "gtl_enhancedcore_galaxy";
    private GalaxyPairingChecks() {}

    public static List<String> run() throws Exception {
        var checks = new ArrayList<String>();
        var unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var unsafe = (Unsafe) unsafeField.get(null);
        var world = (ServerLevel) unsafe.allocateInstance(ServerLevel.class);
        // Set only the dimension key used by endpoint serialization. No server/chunks exist.
        var dimension = java.util.Arrays.stream(Level.class.getDeclaredFields())
                .filter(f -> !Modifier.isStatic(f.getModifiers()) && f.getType() == ResourceKey.class
                        && f.getGenericType().getTypeName().endsWith("<net.minecraft.world.level.Level>"))
                .findFirst().orElseThrow();
        dimension.setAccessible(true); dimension.set(world, Level.OVERWORLD);
        var source = hatch(unsafe, world, true, new BlockPos(4, 80, 7));
        var target = hatch(unsafe, world, false, new BlockPos(12, 90, 13));
        var player = (ProbePlayer) unsafe.allocateInstance(ProbePlayer.class);
        var flash = GTItems.TOOL_DATA_STICK.asStack();
        player.held = flash;
        check(checks, GalaxyConnectionHatch.isDataStick(flash), "GT flash memory is accepted for pairing");
        check(checks, !GalaxyConnectionHatch.isDataStick(new ItemStack(Items.STICK)), "Unrelated item is rejected as flash memory");
        check(checks, !com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine.class
                .isAssignableFrom(GalaxyConnectionHatch.class), "Connection hatches do not inherit an item bus");
        check(checks, !source.shouldOpenUI(player, InteractionHand.MAIN_HAND, null)
                && !target.shouldOpenUI(player, InteractionHand.MAIN_HAND, null), "Neither connection hatch opens a GUI");

        flash.getOrCreateTag().putString("unrelated_payload", "preserve-me");
        check(checks, use(source, world, player) == InteractionResult.SUCCESS, "Source onUse records real flash memory");
        var endpoint = flash.getTag().getCompound(LINK_TAG).copy();
        var valid = GalaxyConnectionHatch.class.getDeclaredMethod("validEndpoint", CompoundTag.class);
        valid.setAccessible(true);
        check(checks, (Boolean) valid.invoke(null, endpoint) && endpoint.getBoolean("source")
                && endpoint.getLong("position") == source.position.asLong(), "Source endpoint carries typed UUID, role, dimension and position");
        var broken = endpoint.copy(); broken.remove("source");
        check(checks, !(Boolean) valid.invoke(null, broken), "Payload without endpoint role is rejected");
        broken = endpoint.copy(); broken.putString("position", "bad");
        check(checks, !(Boolean) valid.invoke(null, broken), "Payload with wrong position type is rejected");
        broken = endpoint.copy(); broken.putString("dimension", "bad dimension");
        check(checks, !(Boolean) valid.invoke(null, broken), "Malformed dimension is rejected");

        check(checks, use(target, world, player) == InteractionResult.SUCCESS, "Target onUse accepts source flash payload");
        check(checks, (Boolean) get(target, "paired")
                && get(target, "sourceId").equals(endpoint.getUUID("id"))
                && get(target, "sourceDimension").equals("minecraft:overworld")
                && (Long) get(target, "sourcePosition") == source.position.asLong(), "Source-to-target onUse persists the exact connection");
        check(checks, flash.getTag().getString("unrelated_payload").equals("preserve-me")
                && flash.getTag().getCompound(LINK_TAG).equals(endpoint)
                && flash.getCount() == 1, "Pairing preserves unrelated NBT, reusable source payload and flash count");
        player.shift = true;
        check(checks, use(target, world, player) == InteractionResult.SUCCESS
                && !(Boolean) get(target, "paired") && get(target, "sourceId") == null
                && get(target, "sourceDimension").equals("") && (Long) get(target, "sourcePosition") == 0,
                "Shift-right-click clears all target connection fields");
        player.shift = false;
        player.held = GTItems.TOOL_DATA_STICK.asStack();
        check(checks, use(target, world, player) == InteractionResult.SUCCESS
                && !(Boolean) get(target, "paired"), "Unrecorded flash can start pairing at the target");
        var targetEndpoint = player.held.getTag().getCompound(LINK_TAG);
        check(checks, (Boolean) valid.invoke(null, targetEndpoint) && !targetEndpoint.getBoolean("source")
                && targetEndpoint.getUUID("id").equals(get(target, "targetId"))
                && targetEndpoint.getLong("position") == target.position.asLong(), "Target-first payload records its own endpoint identity");
        return checks;
    }

    private static ProbeHatch hatch(Unsafe unsafe, ServerLevel world, boolean source, BlockPos pos) throws Exception {
        var hatch = (ProbeHatch) unsafe.allocateInstance(ProbeHatch.class);
        hatch.world = world; hatch.position = pos;
        var sourceField = GalaxyConnectionHatch.class.getDeclaredField("source");
        sourceField.setAccessible(true); sourceField.setBoolean(hatch, source);
        var controllers = MultiblockPartMachine.class.getDeclaredField("controllerPositions");
        controllers.setAccessible(true); controllers.set(hatch, Set.of());
        return hatch;
    }

    private static Object get(GalaxyConnectionHatch hatch, String name) throws Exception {
        var field = GalaxyConnectionHatch.class.getDeclaredField(name); field.setAccessible(true); return field.get(hatch);
    }
    private static InteractionResult use(ProbeHatch hatch, ServerLevel world, ProbePlayer player) {
        return hatch.onUse(null, world, hatch.position, player, InteractionHand.MAIN_HAND, null);
    }
    private static void check(List<String> checks, boolean valid, String label) {
        if (!valid) throw new AssertionError(label); checks.add(label);
    }

    private static final class ProbeHatch extends GalaxyConnectionHatch {
        ServerLevel world; BlockPos position;
        private ProbeHatch(IMachineBlockEntity holder) { super(holder, false); }
        @Override public Level getLevel() { return world; }
        @Override public BlockPos getPos() { return position; }
        @Override public boolean linkActive() { return false; }
        @Override public void markDirty() {}
    }
    private static final class ProbePlayer extends Player {
        ItemStack held; boolean shift;
        private ProbePlayer() { super(null, BlockPos.ZERO, 0, new GameProfile(UUID.randomUUID(), "audit")); }
        @Override public ItemStack getItemInHand(InteractionHand hand) { return held; }
        @Override public boolean isShiftKeyDown() { return shift; }
        @Override public void displayClientMessage(Component message, boolean actionBar) {}
        @Override public boolean isSpectator() { return false; }
        @Override public boolean isCreative() { return false; }
    }
}
