package com.gtl.enhancedcore.common.item;

import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.world.campus.CampusDeviceBlocks;
import com.gtl.enhancedcore.common.world.campus.CampusPlanAccess;
import com.gtl.enhancedcore.common.world.campus.CampusTeleportNetwork;
import com.gtl.enhancedcore.common.world.campus.CentralTeleporterBlockEntity;
import com.gtl.enhancedcore.common.world.plot.PlotChunkGenerator;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/** The real campus centre issues a binding; remote use keeps that world's exact plan. */
@Mod.EventBusSubscriber(modid = GTLEnhancedcore.MOD_ID)
public final class PortableCentralTeleporterItem extends Item {
    private static final String BINDING = "CampusBinding";
    public record Binding(ResourceLocation dimension, BlockPos origin, String layoutIdentity) {}

    public PortableCentralTeleporterItem(Properties properties) { super(properties.stacksTo(1)); }

    @Nullable
    public static Binding binding(ItemStack stack) {
        if (!(stack.getItem() instanceof PortableCentralTeleporterItem)) return null;
        CompoundTag root = stack.getTag();
        if (root == null || !root.contains(BINDING, CompoundTag.TAG_COMPOUND)) return null;
        CompoundTag tag = root.getCompound(BINDING);
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
        if (dimension == null || !tag.contains("Origin", CompoundTag.TAG_LONG) || tag.getString("Layout").isEmpty()) return null;
        return new Binding(dimension, BlockPos.of(tag.getLong("Origin")), tag.getString("Layout"));
    }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) CampusTeleportNetwork.openPortable(serverPlayer, hand);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }

    @Override public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() instanceof ServerPlayer player) {
            if (context.getLevel().getBlockState(context.getClickedPos()).is(CampusDeviceBlocks.CENTRAL_TELEPORTER.get()))
                bind(player, context.getHand(), context.getClickedPos());
            else CampusTeleportNetwork.openPortable(player, context.getHand());
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
    }

    private static void bind(ServerPlayer player, InteractionHand hand, BlockPos origin) {
        var level = player.serverLevel();
        if (!player.isAlive() || player.isSpectator()
                || player.distanceToSqr(origin.getX() + .5, origin.getY() + .5, origin.getZ() + .5) > 64
                || !(level.getChunkSource().getGenerator() instanceof PlotChunkGenerator generator)
                || !generator.settings().isCampus()
                || !origin.equals(new BlockPos(0, generator.settings().surfaceY() + 1, 0))
                || !(level.getBlockEntity(origin) instanceof CentralTeleporterBlockEntity device)
                || !level.dimension().location().equals(device.boundDimension())) {
            message(player, "bind_centre"); return;
        }
        CompoundTag tag = new CompoundTag();
        tag.putString("Dimension", level.dimension().location().toString());
        tag.putLong("Origin", origin.asLong());
        tag.putString("Layout", CampusPlanAccess.layoutIdentity(generator.settings()));
        player.getItemInHand(hand).getOrCreateTag().put(BINDING, tag);
        player.getInventory().setChanged();
        message(player, "bound", level.dimension().location().toString());
    }

    public static void message(ServerPlayer player, String key, Object... args) {
        player.displayClientMessage(Component.translatable("item.gtl_enhancedcore.portable_central_teleporter." + key, args), true);
    }

    @SubscribeEvent public static void rightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getItemStack().getItem() instanceof PortableCentralTeleporterItem) {
            event.setUseBlock(Event.Result.DENY);
            event.setUseItem(Event.Result.ALLOW);
        }
    }

    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.gtl_enhancedcore.portable_central_teleporter.usage").withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("item.gtl_enhancedcore.portable_central_teleporter.limit").withStyle(ChatFormatting.WHITE));
        tooltip.add(Component.translatable("item.gtl_enhancedcore.portable_central_teleporter.navigation").withStyle(ChatFormatting.WHITE));
        Binding binding = binding(stack);
        tooltip.add(Component.translatable("item.gtl_enhancedcore.portable_central_teleporter." + (binding == null ? "unbound" : "bound"),
                binding == null ? "" : binding.dimension().toString()).withStyle(ChatFormatting.GRAY));
    }
}
