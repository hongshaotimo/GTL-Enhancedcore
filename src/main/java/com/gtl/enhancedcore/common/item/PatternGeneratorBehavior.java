package com.gtl.enhancedcore.common.item;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.item.component.IItemUIFactory;
import com.gregtechceu.gtceu.api.item.component.IItemLifeCycle;
import com.gtl.enhancedcore.common.gui.PatternGeneratorWidget;
import com.lowdragmc.lowdraglib.gui.factory.HeldItemUIFactory;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

/** Stateless behavior: each menu owns its settings, preview and generation queue. */
public final class PatternGeneratorBehavior implements IItemUIFactory, IItemLifeCycle {
    public static final PatternGeneratorBehavior INSTANCE = new PatternGeneratorBehavior();

    @Override
    public ModularUI createUI(HeldItemUIFactory.HeldItemHolder holder, Player player) {
        return new ModularUI(PatternGeneratorWidget.WIDTH, PatternGeneratorWidget.HEIGHT, holder, player)
                .background(GuiTextures.BACKGROUND).widget(new PatternGeneratorWidget(holder));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Item item, net.minecraft.world.level.Level level,
                                                 Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            PatternGeneratorTransfer.cancel(serverPlayer);
            var settings = PatternGeneratorSettings.load(player.getItemInHand(hand));
            settings.transferArmed = false;
            player.getItemInHand(hand).getOrCreateTag().put(PatternGeneratorSettings.TAG, settings.write());
            HeldItemUIFactory.INSTANCE.openUI(serverPlayer, hand);
        }
        return new InteractionResultHolder<>(InteractionResult.SUCCESS, player.getItemInHand(hand));
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        if (!PatternGeneratorSettings.load(stack).transferArmed) return InteractionResult.PASS;
        if (context.getPlayer() instanceof ServerPlayer player) PatternGeneratorTransfer.begin(player, context);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void inventoryTick(ItemStack stack, net.minecraft.world.level.Level level,
                              net.minecraft.world.entity.Entity entity, int slot, boolean selected) {
        if (entity instanceof ServerPlayer player) PatternGeneratorTransfer.tick(player, stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("message.gtl_enhancedcore.pattern_generator.right_click_air"), true);
        }
        return InteractionResult.SUCCESS;
    }
}
