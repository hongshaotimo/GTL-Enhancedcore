package com.gtl.enhancedcore.common.item;

import appeng.api.parts.IPartHost;
import appeng.helpers.patternprovider.PatternContainer;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

/** Moves real generated stacks, using each target's insertion rules and bounded server ticks. */
public final class PatternGeneratorTransfer {
    private static final String SOURCE = "GTLPatternGeneratorSource";
    private static final String PREFIX = "message.gtl_enhancedcore.pattern_generator.";
    private static final int PER_TICK = 8;
    private static final double DROP_RANGE = 8;
    private static final Map<ServerPlayer, Transfer> TRANSFERS = new WeakHashMap<>();

    private static final class Transfer {
        final UUID generator;
        final BlockPos pos;
        final Direction side;
        final Vec3 hit;
        final BlockEntity blockEntity;
        int inserted;
        long lastTick = -1;

        Transfer(UUID generator, UseOnContext context) {
            this.generator = generator;
            pos = context.getClickedPos().immutable();
            side = context.getClickedFace();
            hit = context.getClickLocation();
            blockEntity = context.getLevel().getBlockEntity(pos);
        }
    }

    private PatternGeneratorTransfer() {}

    public static void mark(ItemStack pattern, PatternGeneratorSettings settings) {
        if (settings.generatorId == null) settings.generatorId = UUID.randomUUID();
        pattern.getOrCreateTag().putUUID(SOURCE, settings.generatorId);
    }

    public static boolean belongs(ItemStack pattern, UUID generator) {
        return generator != null && !pattern.isEmpty() && pattern.hasTag()
                && pattern.getTag().hasUUID(SOURCE) && generator.equals(pattern.getTag().getUUID(SOURCE));
    }

    public static int available(ServerPlayer player, UUID generator) {
        if (generator == null) return 0;
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (belongs(stack, generator)) count += stack.getCount();
        }
        for (ItemEntity entity : drops(player, generator)) count += entity.getItem().getCount();
        return count;
    }

    private static java.util.List<ItemEntity> drops(ServerPlayer player, UUID generator) {
        return player.serverLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(DROP_RANGE),
                entity -> entity.isAlive() && belongs(entity.getItem(), generator));
    }

    public static void cancel(ServerPlayer player) { TRANSFERS.remove(player); }

    public static void begin(ServerPlayer player, UseOnContext context) {
        PatternGeneratorSettings settings = PatternGeneratorSettings.load(context.getItemInHand());
        if (settings.generatorId == null || available(player, settings.generatorId) == 0 && settings.transferRecipes.isEmpty()) {
            tell(player, "fill_empty");
            return;
        }
        if (player.isSpectator() || !player.serverLevel().mayInteract(player, context.getClickedPos())) return;
        Transfer transfer = new Transfer(settings.generatorId, context);
        if (target(player, transfer) == null) {
            tell(player, "fill_unsupported");
            return;
        }
        TRANSFERS.put(player, transfer);
        tell(player, "fill_started");
        tick(player, context.getItemInHand());
    }

    public static void tick(ServerPlayer player, ItemStack tool) {
        Transfer transfer = TRANSFERS.get(player);
        if (transfer == null) return;
        var tag = tool.getTag();
        if (tag == null || !tag.contains(PatternGeneratorSettings.TAG)) return;
        var settings = tag.getCompound(PatternGeneratorSettings.TAG);
        if (!settings.hasUUID("generatorId") || !transfer.generator.equals(settings.getUUID("generatorId"))) return;
        long now = player.serverLevel().getGameTime();
        if (transfer.lastTick == now) return;
        transfer.lastTick = now;
        if (!settings.getBoolean("transferArmed") || player.isSpectator()
                || player.distanceToSqr(Vec3.atCenterOf(transfer.pos)) > 64
                || !player.serverLevel().mayInteract(player, transfer.pos)) {
            finish(player, transfer, "fill_stopped");
            return;
        }
        IItemHandler target = target(player, transfer);
        if (target == null) { finish(player, transfer, "fill_stopped"); return; }
        int moved = 0;
        var queue = PatternGeneratorSettings.load(tool);
        if (!queue.transferRecipes.isEmpty()) {
            var config = new PatternGeneratorSettings();
            config.applyConfiguration(queue.transferConfiguration);
            boolean validMode = PatternGeneratorRecipes.machineTypes(config.machine).stream()
                    .anyMatch(type -> type.registryName.toString().equals(config.recipeType));
            while (moved < PER_TICK && !queue.transferRecipes.isEmpty()) {
                String id = queue.transferRecipes.getFirst();
                try {
                    var recipe = player.server.getRecipeManager().byKey(new net.minecraft.resources.ResourceLocation(id)).orElse(null);
                    if (!validMode || !(recipe instanceof GTRecipe gtRecipe) || !gtRecipe.recipeType.registryName.toString().equals(config.recipeType))
                        throw new IllegalArgumentException("Machine mode or recipe changed: " + id);
                    ItemStack pattern = PatternGeneratorRecipes.encode(PatternGeneratorRecipes.describe(gtRecipe), player);
                    if (pattern.isEmpty()) throw new IllegalArgumentException("Empty encoded pattern: " + id);
                    if (insert(target, pattern, 1) != 1) {
                        saveQueue(player, tool, queue);
                        finish(player, transfer, "fill_partial");
                        return;
                    }
                    queue.transferRecipes.removeFirst();
                    moved++; transfer.inserted++;
                    // Commit each accepted pattern, so a later rejected recipe cannot replay it.
                    saveQueue(player, tool, queue);
                } catch (RuntimeException error) {
                    saveQueue(player, tool, queue);
                    TRANSFERS.remove(player);
                    com.gtl.enhancedcore.GTLEnhancedcore.LOGGER.warn("Pattern generator refused direct recipe {}", id, error);
                    tell(player, "fill_invalid", id);
                    return;
                }
            }
        }
        for (int slot = 0; slot < player.getInventory().getContainerSize() && moved < PER_TICK; slot++) {
            ItemStack source = player.getInventory().getItem(slot);
            if (!belongs(source, transfer.generator)) continue;
            int accepted = insert(target, source, PER_TICK - moved);
            if (accepted > 0) {
                source.shrink(accepted);
                player.getInventory().setChanged();
                moved += accepted;
                transfer.inserted += accepted;
            }
        }
        if (moved < PER_TICK) for (ItemEntity entity : drops(player, transfer.generator)) {
            ItemStack source = entity.getItem();
            int accepted = insert(target, source, PER_TICK - moved);
            if (accepted > 0) {
                ItemStack remainder = source.copy();
                remainder.shrink(accepted);
                if (remainder.isEmpty()) entity.discard();
                else entity.setItem(remainder);
                moved += accepted;
                transfer.inserted += accepted;
            }
            if (moved >= PER_TICK) break;
        }
        int remaining = available(player, transfer.generator) + queue.transferRecipes.size();
        if (remaining == 0) {
            queue.transferArmed = false;
            saveQueue(player, tool, queue);
            finish(player, transfer, "fill_complete");
        } else if (moved < PER_TICK) finish(player, transfer, "fill_partial");
    }

    private static void saveQueue(ServerPlayer player, ItemStack tool, PatternGeneratorSettings settings) {
        if (settings.transferRecipes.isEmpty()) settings.transferConfiguration = new net.minecraft.nbt.CompoundTag();
        tool.getOrCreateTag().put(PatternGeneratorSettings.TAG, settings.write());
        player.getInventory().setChanged();
    }

    private static int insert(IItemHandler target, ItemStack source, int limit) {
        ItemStack remainder = source.copyWithCount(Math.min(source.getCount(), limit));
        // Provenance is for transport only; stored patterns retain their original AE identity.
        remainder.removeTagKey(SOURCE);
        int offered = remainder.getCount();
        for (int slot = 0; slot < target.getSlots() && !remainder.isEmpty(); slot++) {
            if (target.isItemValid(slot, remainder)) remainder = target.insertItem(slot, remainder, false);
        }
        return offered - remainder.getCount();
    }

    private static IItemHandler target(ServerPlayer player, Transfer transfer) {
        var level = player.serverLevel();
        if (!level.hasChunkAt(transfer.pos) || level.getBlockEntity(transfer.pos) != transfer.blockEntity) return null;
        var machine = MetaMachine.getMachine(level, transfer.pos);
        if (machine instanceof PatternContainer patterns) return patterns.getTerminalPatternInventory().toItemHandler();
        if (transfer.blockEntity instanceof PatternContainer patterns) return patterns.getTerminalPatternInventory().toItemHandler();
        if (transfer.blockEntity instanceof IPartHost host) {
            var selected = host.selectPartWorld(transfer.hit);
            if (selected.part instanceof PatternContainer patterns) return patterns.getTerminalPatternInventory().toItemHandler();
            if (host.getPart(transfer.side) instanceof PatternContainer patterns) return patterns.getTerminalPatternInventory().toItemHandler();
        }
        if (transfer.blockEntity == null) return null;
        return transfer.blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, transfer.side)
                .orElseGet(() -> transfer.blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null));
    }

    private static void finish(ServerPlayer player, Transfer transfer, String message) {
        TRANSFERS.remove(player);
        int queued = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack tool = player.getInventory().getItem(slot);
            if (tool.getItem() != com.gtl.enhancedcore.common.data.GTLEnhancedcoreItems.PATTERN_GENERATOR.get()) continue;
            var settings = PatternGeneratorSettings.load(tool);
            if (transfer.generator.equals(settings.generatorId)) { queued = settings.transferRecipes.size(); break; }
        }
        tell(player, message, transfer.inserted, available(player, transfer.generator) + queued);
        player.containerMenu.broadcastChanges();
    }

    private static void tell(ServerPlayer player, String message, Object... args) {
        player.displayClientMessage(Component.translatable(PREFIX + message, args), true);
    }
}
