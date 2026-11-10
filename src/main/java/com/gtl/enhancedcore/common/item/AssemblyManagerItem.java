package com.gtl.enhancedcore.common.item;

import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.assembly.AssemblyMirrorNetwork;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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

/** Binds and manages mirrors while retaining handheld assembly navigation. */
@Mod.EventBusSubscriber(modid = GTLEnhancedcore.MOD_ID)
public final class AssemblyManagerItem extends Item {
    public AssemblyManagerItem(Properties properties) { super(properties.stacksTo(1)); }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) AssemblyMirrorNetwork.open(serverPlayer, hand);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }

    @Override public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                AssemblyMirrorNetwork.bindNetwork(serverPlayer, context.getHand(), context.getClickedPos(), context.getClickedFace());
            } else if (!AssemblyMirrorNetwork.useMirror(serverPlayer, context.getHand(), context.getClickedPos())) {
                AssemblyMirrorNetwork.open(serverPlayer, context.getHand());
            }
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
    }

    @SubscribeEvent public static void rightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getItemStack().getItem() instanceof AssemblyManagerItem) {
            event.setUseBlock(Event.Result.DENY);
            event.setUseItem(Event.Result.ALLOW);
        }
    }

    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.gtl_enhancedcore.assembly_manager.usage").withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("item.gtl_enhancedcore.assembly_manager.mirror_usage").withStyle(ChatFormatting.WHITE));
        tooltip.add(Component.translatable("item.gtl_enhancedcore.assembly_manager.markers").withStyle(ChatFormatting.GRAY));
        tooltip.add(com.gtl.enhancedcore.common.util.PlayerTooltipStyles.rainbow(
                com.gtl.enhancedcore.common.util.TooltipPolicy.CREDIT_KEY));
    }
}
