package com.gtl.enhancedcore.audit;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.helpers.patternprovider.PatternContainer;
import com.gregtechceu.gtceu.api.item.MetaMachineItem;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.data.GTLEnhancedcoreItems;
import com.gtl.enhancedcore.common.gui.PatternGeneratorGhostWidget;
import com.gtl.enhancedcore.common.gui.PatternGeneratorWidget;
import com.gtl.enhancedcore.common.item.PatternGeneratorBehavior;
import com.gtl.enhancedcore.common.item.PatternGeneratorRecipes;
import com.gtl.enhancedcore.common.item.PatternGeneratorSettings;
import com.gtl.enhancedcore.common.item.PatternGeneratorTransfer;
import com.gtl.enhancedcore.common.recipe.MvCircuitAssemblerRecipe;
import com.lowdragmc.lowdraglib.gui.factory.HeldItemUIFactory;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.gtlcore.gtlcore.integration.ae2.pattern.PatternEncoderMetadata;
import org.gtlcore.gtlcore.integration.ae2.pattern.PatternQuickUploadMetadata;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;

/** Real recipes, menus, drops and inventories in an isolated Forge world. Never in production. */
public final class PatternGeneratorChecks {
    private static int checks;
    private static net.minecraft.world.entity.item.ItemEntity visibilityProbe;
    private static void check(boolean value, String reason) {
        checks++;
        if (!value) throw new IllegalStateException(reason);
    }

    private static final class AuditPlayer extends ServerPlayer {
        final List<String> messages = new ArrayList<>();
        AuditPlayer(MinecraftServer server) {
            super(server, server.overworld(), new GameProfile(UUID.randomUUID(), "PatternAuthor"));
            connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), this);
            setPos(9000.5, 80, 0.5);
        }
        @Override public void displayClientMessage(Component message, boolean overlay) { messages.add(Component.Serializer.toJson(message)); }
    }

    private static ItemStack item(String id) { return new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation(id))); }
    private static Object field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }
    private static void action(PatternGeneratorWidget widget, int id, Consumer<FriendlyByteBuf> write) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try { write.accept(buffer); widget.handleClientAction(id, buffer); }
        finally { buffer.release(); }
    }
    private static void white(PatternGeneratorWidget widget) {
        white(widget, "minecraft:apple", "minecraft:amethyst_shard");
    }
    private static void white(PatternGeneratorWidget widget, String input, String output) {
        action(widget, 103, buffer -> {
            buffer.writeUtf(input, 256); buffer.writeUtf(output, 256);
            for (int i = 0; i < 4; i++) buffer.writeLongArray(new long[0]);
        });
    }
    private static PatternGeneratorWidget widget(AuditPlayer player, PatternGeneratorSettings settings) {
        ItemStack tool = GTLEnhancedcoreItems.PATTERN_GENERATOR.asStack();
        tool.getOrCreateTag().put(PatternGeneratorSettings.TAG, settings.write());
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        var widget = new PatternGeneratorWidget(new HeldItemUIFactory.HeldItemHolder(player, InteractionHand.MAIN_HAND));
        if (settings.inputWhite.isBlank() && settings.outputWhite.isBlank()) white(widget);
        else white(widget, settings.inputWhite, settings.outputWhite);
        return widget;
    }

    public static void prepare(MinecraftServer server) {
        var level = server.overworld();
        for (int x = (9000 >> 4) - 1; x <= (9000 >> 4) + 1; x++) for (int z = -1; z <= 1; z++) {
            level.setChunkForced(x, z, true); level.getChunk(x, z);
        }
        visibilityProbe = new net.minecraft.world.entity.item.ItemEntity(level, 9000.5, 80, 0.5, new ItemStack(Items.DIRT));
        visibilityProbe.setNoGravity(true);
        level.addFreshEntity(visibilityProbe);
    }

    public static boolean ready(MinecraftServer server) {
        return visibilityProbe != null && server.overworld().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                visibilityProbe.getBoundingBox().inflate(1)).contains(visibilityProbe);
    }

    public static int run(MinecraftServer server) throws Exception {
        checks = 0;
        var level = server.overworld();
        level.setChunkForced(9000 >> 4, 0, true);
        var player = new AuditPlayer(server);
        try {
            check(ready(server), "Disposable fixture's entity section is visible before testing ground drops");
            visibilityProbe.discard(); visibilityProbe = null;
            machineTypes();
            acquisition(server);
            settingsAndGhosts();
            quantities(player);
            var settings = new PatternGeneratorSettings();
            settings.machine = item("gtceu:mv_bender");
            var widget = widget(player, settings);
            check((int) field(widget, "count") == 3, "Any-circuit preview includes none, 1 and 32");
            action(widget, 102, buffer -> buffer.writeVarInt(0));
            check((int) field(widget, "count") == 1, "No-circuit preview is not any-circuit");
            action(widget, 102, buffer -> buffer.writeVarInt(32));
            check((int) field(widget, "count") == 1, "Circuit 32 preview finds its recipe");
            action(widget, 102, buffer -> buffer.writeVarInt(-1));
            int stale = (int) field(widget, "revision") - 1;
            action(widget, 108, buffer -> buffer.writeVarInt(stale));
            check(!(boolean) field(widget, "running"), "Stale preview cannot generate patterns");
            int current = (int) field(widget, "revision");
            action(widget, 107, buffer -> { buffer.writeVarInt(current); buffer.writeBoolean(false); });
            action(widget, 108, buffer -> buffer.writeVarInt(current));
            check(!(boolean) field(widget, "running"), "Unselected recipes do not generate");
            action(widget, 107, buffer -> { buffer.writeVarInt(current); buffer.writeBoolean(true); });

            ItemStack blanks = AEItems.BLANK_PATTERN.stack(7);
            player.getInventory().setItem(9, blanks);
            ItemStack existing = PatternDetailsHelper.encodeProcessingPattern(
                    new GenericStack[]{new GenericStack(AEItemKey.of(Items.DIRT), 1)},
                    new GenericStack[]{new GenericStack(AEItemKey.of(Items.STONE), 1)});
            player.getInventory().setItem(10, existing);
            action(widget, 108, buffer -> buffer.writeVarInt(current));
            widget.detectAndSendChanges();
            check(blanks.getCount() == 7 && player.getInventory().getItem(10) == existing,
                    "Generating is free and retains existing blank and encoded patterns");
            var saved = PatternGeneratorSettings.load(player.getMainHandItem());
            check(PatternGeneratorTransfer.available(player, saved.generatorId) == 3, "Exactly three physical patterns generated");
            for (ItemStack pattern : generated(player, saved.generatorId)) metadata(pattern, player);
            check((int) field(widget, "selected") == 0, "Generated choices are cleared to prevent accidental regeneration");

            var wire = new FriendlyByteBuf(Unpooled.buffer());
            try {
                widget.writeInitialData(wire);
                byte[] data = new byte[wire.readableBytes()];
                wire.getBytes(wire.readerIndex(), data);
                Files.write(Path.of("pattern-generator-state.bin"), data);
                net.minecraft.nbt.NbtIo.write(saved.write(), Path.of("pattern-generator-settings.nbt").toFile());
                var copy = new PatternGeneratorWidget(new HeldItemUIFactory.HeldItemHolder(player, InteractionHand.MAIN_HAND));
                copy.readInitialData(wire);
                check(wire.readableBytes() == 0, "Menu-state packet reads every written byte");
                check((int) field(copy, "count") == 3 && (int) field(copy, "availablePatterns") == 3, "Menu-state packet preserves preview and available patterns");
            } finally { wire.release(); }
            var previewMenu = new PatternGeneratorWidget(new HeldItemUIFactory.HeldItemHolder(player, InteractionHand.MAIN_HAND));
            action(previewMenu, 104, buffer -> {});
            previewMenu.detectAndSendChanges();
            net.minecraft.nbt.NbtIo.write(PatternGeneratorSettings.load(player.getMainHandItem()).write(), Path.of("pattern-generator-settings.nbt").toFile());
            var stateOnly = new FriendlyByteBuf(Unpooled.buffer());
            try {
                var method = PatternGeneratorWidget.class.getDeclaredMethod("writeState", FriendlyByteBuf.class);
                method.setAccessible(true);
                method.invoke(previewMenu, stateOnly);
                byte[] data = new byte[stateOnly.readableBytes()];
                stateOnly.readBytes(data);
                Files.write(Path.of("pattern-generator-state-only.bin"), data);
            } finally { stateOnly.release(); }
            action(previewMenu, 111, b -> b.writeVarInt(10));
            var tenColumns = new FriendlyByteBuf(Unpooled.buffer());
            try {
                var method = PatternGeneratorWidget.class.getDeclaredMethod("writeState", FriendlyByteBuf.class); method.setAccessible(true);
                method.invoke(previewMenu, tenColumns); byte[] data = new byte[tenColumns.readableBytes()]; tenColumns.readBytes(data);
                Files.write(Path.of("pattern-generator-state-ten-columns.bin"), data);
            } finally { tenColumns.release(); }
            action(previewMenu, 111, b -> b.writeVarInt(3));

            action(widget, 110, buffer -> {});
            check(PatternGeneratorSettings.load(player.getMainHandItem()).transferArmed, "Fill button arms this tool");
            transferInventories(player, saved);

            // Every ordinary inventory slot is full, including the retained tool in slot 0.
            player.getInventory().clearContent();
            settings = new PatternGeneratorSettings(); settings.machine = item("gtceu:mv_bender");
            widget = widget(player, settings);
            for (int slot = 1; slot < 36; slot++) player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
            int expected = (int) field(widget, "revision");
            action(widget, 108, buffer -> buffer.writeVarInt(expected));
            widget.detectAndSendChanges();
            saved = PatternGeneratorSettings.load(player.getMainHandItem());
            check(generated(player, saved.generatorId).isEmpty(), "Full backpack receives no generated stack");
            int groundCount = PatternGeneratorTransfer.available(player, saved.generatorId);
            check(groundCount == 3, "Full backpack drops all three patterns and finishes: available=" + groundCount
                    + ", generated=" + field(widget, "generated") + ", failed=" + field(widget, "failed") + ", pending=" + saved.pending.size());
            check(!(boolean) field(widget, "running") && saved.pending.isEmpty(), "Full inventory never pauses generation");
            var chestPos = new BlockPos(9002, 80, 0);
            level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3);
            var chest = (ChestBlockEntity) level.getBlockEntity(chestPos);
            chest.clearContent();
            action(widget, 110, buffer -> {});
            begin(player, chestPos);
            check(PatternGeneratorTransfer.available(player, saved.generatorId) == 0, "Fill moves generated drops from the ground");
            check(patterns(chest) == 3, "All three dropped patterns arrive once in the chest");
            for (int slot = 0; slot < chest.getContainerSize(); slot++) if (!chest.getItem(slot).isEmpty()) metadata(chest.getItem(slot), player);
            GTLEnhancedcore.LOGGER.info("[PATTERN_GENERATOR] FREE_GENERATION blank_cost=0 existing_patterns_kept=true mode=true author=true overflow_drops=3 ground_transfer=3");
            batchesAndToolIdentity(player);
            pagesTagsAndPresets(player);
            return checks;
        } catch (Exception error) {
            GTLEnhancedcore.LOGGER.error("[PATTERN_GENERATOR] Detailed test failure", error);
            throw error;
        } finally {
            PatternGeneratorTransfer.cancel(player);
            for (int x = (9000 >> 4) - 1; x <= (9000 >> 4) + 1; x++) for (int z = -1; z <= 1; z++) level.setChunkForced(x, z, false);
        }
    }

    private static void metadata(ItemStack pattern, AuditPlayer player) {
        modeAndAuthor(pattern, player);
        var details = PatternDetailsHelper.decodePattern(pattern, player.level());
        check(details != null && Arrays.stream(details.getOutputs()).anyMatch(output -> output.what().equals(AEItemKey.of(Items.AMETHYST_SHARD)) && output.amount() == 3), "AE pattern decodes original output amount without a multiplier");
        check(Arrays.stream(details.getInputs()).anyMatch(input -> Arrays.stream(input.getPossibleInputs())
                .anyMatch(stack -> stack.what().equals(AEItemKey.of(Items.APPLE)) && stack.amount() * input.getMultiplier() == 2)), "AE input amount remains two apples");
    }

    private static void modeAndAuthor(ItemStack pattern, AuditPlayer player) {
        check(PatternQuickUploadMetadata.readRecipeTypeIds(pattern).contains(GTRecipeTypes.BENDER_RECIPES.registryName), "Generated pattern retains its actual machine mode");
        var author = PatternEncoderMetadata.readEncoder(pattern).orElseThrow();
        check(author.id().equals(player.getUUID()) && author.name().equals(player.getName().getString()), "Pattern retains generating player's UUID and name");
    }

    private static void batchesAndToolIdentity(AuditPlayer player) throws Exception {
        player.getInventory().clearContent();
        var settings = new PatternGeneratorSettings();
        settings.machine = item("gtceu:mv_bender");
        settings.inputWhite = "minecraft:carrot"; settings.outputWhite = "minecraft:diamond";
        var widget = widget(player, settings);
        check((int) field(widget, "count") == 17, "Bulk preview contains all 17 distinct live recipes");
        int revision = (int) field(widget, "revision");
        action(widget, 108, buffer -> buffer.writeVarInt(revision));
        widget.detectAndSendChanges();
        var tool = player.getMainHandItem();
        settings = PatternGeneratorSettings.load(tool);
        check(settings.pending.size() == 9 && PatternGeneratorTransfer.available(player, settings.generatorId) == 8,
                "First generation update creates eight physical patterns and persists nine pending recipes");
        // Recreate the held-item menu from its saved NBT, like closing and reopening it mid-batch.
        var resumed = new PatternGeneratorWidget(new HeldItemUIFactory.HeldItemHolder(player, InteractionHand.MAIN_HAND));
        white(resumed, settings.inputWhite, settings.outputWhite);
        int resumedRevision = (int) field(resumed, "revision");
        action(resumed, 108, buffer -> buffer.writeVarInt(resumedRevision));
        resumed.detectAndSendChanges(); resumed.detectAndSendChanges();
        check(PatternGeneratorTransfer.available(player, settings.generatorId) == 17,
                "Resuming the remaining nine recipes yields exactly 17 patterns, without regenerating the first eight");
        check(PatternGeneratorSettings.load(tool).pending.isEmpty(), "Resumed generation finishes its saved queue");
        for (ItemStack pattern : generated(player, settings.generatorId)) modeAndAuthor(pattern, player);
        var otherSettings = new PatternGeneratorSettings();
        var foreign = generated(player, settings.generatorId).getFirst().copyWithCount(1);
        PatternGeneratorTransfer.mark(foreign, otherSettings);
        player.getInventory().placeItemBackInInventory(foreign);
        check(PatternGeneratorTransfer.available(player, otherSettings.generatorId) == 1, "Second tool's pattern retains a separate identity");
        var pos = new BlockPos(9006, 80, 0);
        player.serverLevel().setBlock(pos, Blocks.CHEST.defaultBlockState(), 3);
        var chest = (ChestBlockEntity) player.serverLevel().getBlockEntity(pos);
        chest.clearContent();
        action(resumed, 110, buffer -> {});
        begin(player, pos);
        check(patterns(chest) == 8 && PatternGeneratorTransfer.available(player, settings.generatorId) == 9,
                "First fill update moves eight actual sources");
        PatternGeneratorBehavior.INSTANCE.inventoryTick(tool, player.level(), player, 0, true);
        check(patterns(chest) == 8, "A second lifecycle callback in the same world tick does not transfer a second batch");
        // Advance the isolated world's clock deterministically to exercise separate lifecycle ticks.
        for (int tick = 0; tick < 2; tick++) {
            ((net.minecraft.world.level.storage.ServerLevelData) player.serverLevel().getLevelData())
                    .setGameTime(player.serverLevel().getGameTime() + 1);
            PatternGeneratorBehavior.INSTANCE.inventoryTick(tool, player.level(), player, 0, true);
        }
        check(patterns(chest) == 17 && PatternGeneratorTransfer.available(player, settings.generatorId) == 0,
                "Three fill ticks move exactly 17 patterns with no duplication or loss");
        check(PatternGeneratorTransfer.available(player, otherSettings.generatorId) == 1,
                "Bulk fill leaves the other tool's generated pattern untouched");
        check(!PatternGeneratorSettings.load(tool).transferArmed, "Successful bulk fill disarms the tool");
        for (int slot = 0; slot < chest.getContainerSize(); slot++) if (!chest.getItem(slot).isEmpty()) modeAndAuthor(chest.getItem(slot), player);
        GTLEnhancedcore.LOGGER.info("[PATTERN_GENERATOR] BATCHES generated=17 resumed=9 filled=17 separate_tool_kept=1");
    }

    private static List<ItemStack> generated(AuditPlayer player, UUID id) {
        var result = new ArrayList<ItemStack>();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            var stack = player.getInventory().getItem(slot);
            if (PatternGeneratorTransfer.belongs(stack, id)) result.add(stack);
        }
        return result;
    }

    private static void begin(AuditPlayer player, BlockPos pos) {
        begin(player, pos, Direction.UP);
    }

    private static void begin(AuditPlayer player, BlockPos pos, Direction side) {
        var context = new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), side, pos, false));
        check(PatternGeneratorBehavior.INSTANCE.onItemUseFirst(player.getMainHandItem(), context) == InteractionResult.SUCCESS,
                "Armed tool intercepts right-click before the target GUI opens");
    }

    private static int patterns(ChestBlockEntity chest) {
        int count = 0;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) if (chest.getItem(slot).is(AEItems.PROCESSING_PATTERN.stack().getItem())) count += chest.getItem(slot).getCount();
        return count;
    }

    private static void transferInventories(AuditPlayer player, PatternGeneratorSettings settings) throws Exception {
        var level = player.serverLevel();
        BlockPos chestPos = new BlockPos(9002, 80, 0);
        level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3);
        var chest = (ChestBlockEntity) level.getBlockEntity(chestPos);
        for (int slot = 0; slot < 26; slot++) chest.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        begin(player, chestPos);
        check(patterns(chest) == 1 && PatternGeneratorTransfer.available(player, settings.generatorId) == 2, "Partial insertion conserves three real patterns");
        begin(player, chestPos);
        check(patterns(chest) == 1 && PatternGeneratorTransfer.available(player, settings.generatorId) == 2, "Repeated right-click on a full container does not duplicate or lose anything");
        check(chest.getItem(0).is(Items.COBBLESTONE) && chest.getItem(0).getCount() == 64, "Existing target contents remain untouched");

        BlockPos furnace = new BlockPos(9003, 80, 0);
        level.setBlock(furnace, Blocks.FURNACE.defaultBlockState(), 3);
        begin(player, furnace, Direction.DOWN);
        check(PatternGeneratorTransfer.available(player, settings.generatorId) == 2, "Rejecting inventory leaves both source patterns intact");

        BlockPos superPos = new BlockPos(9004, 80, 0);
        level.setBlock(superPos, ForgeRegistries.BLOCKS.getValue(new ResourceLocation("gtladditions:me_super_pattern_buffer")).defaultBlockState(), 3);
        var superBuffer = (PatternContainer) MetaMachine.getMachine(level, superPos);
        // Keep one pattern for the following provider check.
        ItemStack heldBack = generated(player, settings.generatorId).getFirst().copy();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) if (PatternGeneratorTransfer.belongs(player.getInventory().getItem(slot), settings.generatorId)) {
            player.getInventory().setItem(slot, ItemStack.EMPTY); break;
        }
        begin(player, superPos);
        check(superBuffer.getTerminalPatternInventory().getStackInSlot(0).is(AEItems.PROCESSING_PATTERN.stack().getItem()), "Super assembly's real pattern inventory accepts the generated stack");
        metadata(superBuffer.getTerminalPatternInventory().getStackInSlot(0), player);
        player.getInventory().placeItemBackInInventory(heldBack);
        settings.transferArmed = true;
        player.getMainHandItem().getOrCreateTag().put(PatternGeneratorSettings.TAG, settings.write());
        BlockPos provider = new BlockPos(9005, 80, 0);
        level.setBlock(provider, AEBlocks.PATTERN_PROVIDER.block().defaultBlockState(), 3);
        begin(player, provider);
        var patterns = (PatternContainer) level.getBlockEntity(provider);
        check(patterns.getTerminalPatternInventory().getStackInSlot(0).is(AEItems.PROCESSING_PATTERN.stack().getItem()), "AE provider uses its actual dedicated pattern slots");
        metadata(patterns.getTerminalPatternInventory().getStackInSlot(0), player);
        check(PatternGeneratorTransfer.available(player, settings.generatorId) == 0, "Successful transfer consumes only the real moved sources");
        check(player.getInventory().getItem(9).is(AEItems.BLANK_PATTERN.stack().getItem()) && player.getInventory().getItem(9).getCount() == 7,
                "Fill never touches blank patterns");
        check(player.getInventory().getItem(10).is(AEItems.PROCESSING_PATTERN.stack().getItem()), "Fill never touches existing unrelated patterns");
    }

    private static void settingsAndGhosts() {
        var tool = GTLEnhancedcoreItems.PATTERN_GENERATOR.asStack();
        var settings = new PatternGeneratorSettings();
        settings.generatorId = UUID.randomUUID(); settings.transferArmed = true; settings.circuit = 32;
        settings.inputBlack[0] = new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1);
        settings.outputBlack[6] = new GenericStack(AEFluidKey.of(Fluids.WATER), 1);
        tool.getOrCreateTag().put(PatternGeneratorSettings.TAG, settings.write());
        var loaded = PatternGeneratorSettings.load(ItemStack.of(tool.save(new CompoundTag())));
        check(loaded.generatorId.equals(settings.generatorId) && loaded.transferArmed && loaded.circuit == 32, "NBT round-trip keeps independent tool identity, fill mode and circuit 32");
        check(loaded.inputBlack[0].equals(settings.inputBlack[0]) && loaded.outputBlack[6].equals(settings.outputBlack[6]), "Item and fluid ghost slots persist");
        var legacy = new CompoundTag(); legacy.putInt("circuit", 0); legacy.putInt("scale", 512);
        loaded.read(legacy);
        check(loaded.circuit == -1 && !loaded.write().contains("scale"), "Legacy unrestricted 0 migrates and multiplier is removed");
        ItemStack bucket = new ItemStack(Items.WATER_BUCKET, 3);
        check(PatternGeneratorGhostWidget.identify(bucket).what().equals(AEFluidKey.of(Fluids.WATER)), "Dragging a water bucket identifies the fluid");
        check(bucket.getCount() == 3 && bucket.is(Items.WATER_BUCKET), "Ghost identification does not consume or drain the real stack");
        check(PatternGeneratorGhostWidget.identify(FluidStack.create(Fluids.WATER, 1000)).what().equals(AEFluidKey.of(Fluids.WATER)), "Fluid ingredient is accepted directly");
        check(PatternGeneratorGhostWidget.identify(new ItemStack(Items.IRON_INGOT)).what().equals(AEItemKey.of(Items.IRON_INGOT)), "Item ingredient is accepted directly");
        check(loaded.previewColumns == 3, "Legacy settings default to three columns");
        settings.inputInclude[0] = new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1);
        settings.inputTag[0] = "forge:ingots";
        settings.outputInclude[0] = new GenericStack(AEFluidKey.of(Fluids.WATER), 1);
        settings.outputTag[0] = "minecraft:water";
        loaded.read(settings.write());
        check(loaded.inputTag[0].equals("forge:ingots") && loaded.outputTag[0].equals("minecraft:water"), "Valid item and fluid tag selections survive NBT");
        check(PatternGeneratorSettings.validTag(settings.inputInclude[0], "minecraft:water").isEmpty(), "Fluid tags cannot be attached to an item ghost");
        check(PatternGeneratorSettings.includes(List.of(new com.gtl.enhancedcore.common.item.PatternGeneratorFilter.Material("minecraft:gold_ingot", false, 0)), loaded.inputInclude[0], loaded.inputTag[0]), "Tag filtering accepts other members of the selected item tag");
        check(!PatternGeneratorSettings.includes(List.of(new com.gtl.enhancedcore.common.item.PatternGeneratorFilter.Material("minecraft:gold_ingot", false, 0)), loaded.inputInclude[0], ""), "Exact filtering rejects a different member of the same tag");
        check(!PatternGeneratorSettings.includes(List.of(new com.gtl.enhancedcore.common.item.PatternGeneratorFilter.Material("minecraft:water", false, 0)), loaded.outputInclude[0], loaded.outputTag[0]), "Tag filtering does not cross the item/fluid domain");
        settings.pending.add("kubejs:injected_queue");
        var safe = settings.configuration();
        check(!safe.contains("pending") && !safe.contains("generatorId") && !safe.contains("transferArmed"), "Preset export contains configuration only");
        var identity = UUID.randomUUID(); loaded.generatorId = identity;
        loaded.applyConfiguration(settings.write());
        check(loaded.pending.isEmpty() && !loaded.transferArmed && identity.equals(loaded.generatorId), "Preset import strips injected queue/transfer state and preserves the current tool UUID");
    }

    private static void pagesTagsAndPresets(AuditPlayer player) throws Exception {
        player.getInventory().clearContent();
        var settings = new PatternGeneratorSettings(); settings.machine = item("gtceu:mv_bender");
        settings.inputWhite = "minecraft:golden_carrot"; settings.outputWhite = "minecraft:emerald";
        settings.generatorId = UUID.randomUUID();
        var widget = widget(player, settings);
        check((int) field(widget, "count") == 151 && ((List<?>) field(widget, "previewPage")).size() == 45, "Default live menu page contains 45 of 151 recipes");
        for (int columns = 1; columns <= 10; columns++) {
            final int value = columns;
            action(widget, 111, b -> b.writeVarInt(value));
            check(((List<?>) field(widget, "previewPage")).size() == columns * 15, "Column selector sends exactly fifteen previews per column");
            int revision = (int) field(widget, "revision");
            action(widget, 105, b -> { b.writeVarInt(revision); b.writeVarInt(1); });
            check((int) field(widget, "page") == 1 && ((List<?>) field(widget, "previewPage")).size() == Math.min(151 - columns * 15, columns * 15), "Next page exposes the correct tail");
        }
        var live = PatternGeneratorRecipes.recipes(player, GTRecipeTypes.BENDER_RECIPES);
        var selectedIds = List.of(live.stream().filter(e -> e.id().endsWith("pattern_generator_grid_0")).findFirst().orElseThrow().id(),
                live.stream().filter(e -> e.id().endsWith("pattern_generator_grid_150")).findFirst().orElseThrow().id(), "kubejs:missing_preset_recipe");
        var config = ((PatternGeneratorSettings) field(widget, "settings")).write();
        config.putUUID("generatorId", UUID.randomUUID()); config.putBoolean("transferArmed", true);
        var pending = new net.minecraft.nbt.ListTag(); pending.add(net.minecraft.nbt.StringTag.valueOf("kubejs:injected_queue")); config.put("pending", pending);
        // Audit captures server messages without requiring a real network client.
        var updates = new java.util.HashMap<Integer, byte[]>();
        widget.setGui(new com.lowdragmc.lowdraglib.gui.modular.ModularUI(PatternGeneratorWidget.WIDTH, PatternGeneratorWidget.HEIGHT,
                new HeldItemUIFactory.HeldItemHolder(player, InteractionHand.MAIN_HAND), player));
        widget.setUiAccess(new com.lowdragmc.lowdraglib.gui.modular.WidgetUIAccess() {
            @Override public boolean attemptMergeStack(ItemStack stack, boolean a, boolean b) { return false; }
            @Override public void writeClientAction(com.lowdragmc.lowdraglib.gui.widget.Widget source, int id, Consumer<FriendlyByteBuf> writer) {}
            @Override public void writeUpdateInfo(com.lowdragmc.lowdraglib.gui.widget.Widget source, int id, Consumer<FriendlyByteBuf> writer) {
                var packet = new FriendlyByteBuf(Unpooled.buffer());
                try { writer.accept(packet); byte[] bytes = new byte[packet.readableBytes()]; packet.readBytes(bytes); updates.put(id, bytes); }
                finally { packet.release(); }
            }
        });
        Consumer<FriendlyByteBuf> importData = b -> { b.writeVarInt(7); b.writeNbt(config); b.writeVarInt(selectedIds.size()); selectedIds.forEach(id -> b.writeUtf(id, 256)); };
        action(widget, 115, importData);
        Files.write(Path.of("pattern-generator-preset-preview.bin"), updates.get(121));
        var inspect = new FriendlyByteBuf(Unpooled.wrappedBuffer(updates.get(121)));
        try {
            check(inspect.readVarInt() == 7 && inspect.readBoolean(), "Preset preview returns its request token");
            inspect.readItem(); inspect.readUtf(256);
            check(inspect.readVarInt() == 2 && inspect.readVarInt() == 1, "Shared preview identifies existing and missing recipes");
        } finally { inspect.release(); }
        action(widget, 114, importData);
        var applied = (PatternGeneratorSettings) field(widget, "settings");
        check(applied.generatorId.equals(settings.generatorId) && !applied.transferArmed && applied.pending.isEmpty(), "Menu import cannot overwrite live identity or inject queued work");
        check(!(boolean) field(widget, "ready"), "Imported localized filters wait for client-side name resolution");
        white(widget, settings.inputWhite, settings.outputWhite);
        check((int) field(widget, "selected") == 2, "Import preserves the exact checked subset through localized-filter resolution");
        int revision = (int) field(widget, "revision");
        action(widget, 113, b -> { b.writeVarInt(revision); b.writeUtf("shared mapping", 48); });
        Files.write(Path.of("pattern-generator-preset-file.bin"), updates.get(120));
        var exported = new FriendlyByteBuf(Unpooled.wrappedBuffer(updates.get(120)));
        try {
            check(exported.readBoolean(), "Export acknowledges a valid named selection");
            check(exported.readUtf(48).equals("shared mapping") && exported.readUtf(64).equals(player.getName().getString()) && exported.readUtf(64).equals(player.getUUID().toString()), "Server attributes the saved mapping to the saving player");
            exported.readUtf(64); var saved = exported.readNbt();
            check(!saved.contains("pending") && !saved.contains("generatorId") && !saved.contains("transferArmed"), "Network export never copies a live task or tool identity");
            check(exported.readVarInt() == 2, "Export records only the selected existing recipe IDs");
        } finally { exported.release(); }
        var bad = config.copy(); bad.putString("recipeType", "gtceu:assembler");
        action(widget, 114, b -> { b.writeVarInt(8); b.writeNbt(bad); b.writeVarInt(1); b.writeUtf(selectedIds.getFirst(), 256); });
        check(((PatternGeneratorSettings) field(widget, "settings")).recipeType.equals("gtceu:bender"), "Machine cannot import an unsupported recipe mode");
        var include = PatternGeneratorWidget.class.getDeclaredMethod("setInclude", boolean.class, GenericStack.class); include.setAccessible(true);
        include.invoke(widget, true, new GenericStack(AEItemKey.of(Items.APPLE), 1));
        check((int) field(widget, "count") == 0, "Include ghost and text filters must both match");
        action(widget, 104, b -> {});
        check(applied.inputInclude[0] == null && applied.inputTag[0].isEmpty(), "Clear filters resets both ghost inclusion and its tag");
        GTLEnhancedcore.LOGGER.info("[PATTERN_GENERATOR] PAGES_TAGS_PRESETS columns=1-10 rows=15 defaults=45 exact_subset=2 missing=1 identity_safe=true");
    }

    private static void quantities(AuditPlayer player) {
        var recipe = player.server.getRecipeManager().getAllRecipesFor(GTRecipeTypes.BENDER_RECIPES).stream()
                .filter(candidate -> candidate.id.getPath().endsWith("pattern_generator_audit_thirty_two")).findFirst().orElseThrow();
        var copy = recipe.copy();
        long itemAmount = (long) Integer.MAX_VALUE + 17, fluidAmount = (long) Integer.MAX_VALUE + 23;
        var circuit = IntCircuitBehaviour.stack(32);
        copy.inputs.put(ItemRecipeCapability.CAP, new ArrayList<>(List.of(
                new Content(LongIngredient.create(Ingredient.of(Items.APPLE), itemAmount), 10000, 10000, 0, null, null),
                new Content(Ingredient.of(circuit), 0, 10000, 0, null, null))));
        copy.inputs.put(FluidRecipeCapability.CAP, new ArrayList<>(List.of(
                new Content(FluidIngredient.of(fluidAmount, Fluids.WATER), 10000, 10000, 0, null, null))));
        var described = PatternGeneratorRecipes.describe(copy);
        check(described.circuit() == 32, "Preview reads programmed circuit NBT");
        check(described.encodedInputs().stream().anyMatch(input -> input.what().equals(AEItemKey.of(Items.APPLE)) && input.amount() == itemAmount), "Item counts above int range remain exact");
        check(described.encodedInputs().stream().anyMatch(input -> input.what().equals(AEFluidKey.of(Fluids.WATER)) && input.amount() == fluidAmount), "Fluid counts above int range remain exact");
        var encoded = PatternGeneratorRecipes.encode(described, player);
        var decoded = PatternDetailsHelper.decodePattern(encoded, player.level());
        check(decoded != null && Arrays.stream(decoded.getInputs()).anyMatch(input -> Arrays.stream(input.getPossibleInputs())
                .anyMatch(stack -> stack.what().equals(AEItemKey.of(Items.APPLE)) && stack.amount() * input.getMultiplier() == itemAmount)),
                "Encoded pattern retains item amounts above the int range");
        check(Arrays.stream(decoded.getInputs()).anyMatch(input -> Arrays.stream(input.getPossibleInputs())
                .anyMatch(stack -> stack.what().equals(AEFluidKey.of(Fluids.WATER)) && stack.amount() * input.getMultiplier() == fluidAmount)),
                "Encoded pattern retains fluid amounts above the int range");
        check(decoded != null && Arrays.stream(decoded.getInputs()).flatMap(input -> Arrays.stream(input.getPossibleInputs()))
                .anyMatch(input -> input.what() instanceof AEItemKey key && IntCircuitBehaviour.isIntegratedCircuit(key.toStack())
                        && IntCircuitBehaviour.getCircuitConfiguration(key.toStack()) == 32), "Encoded sample retains circuit configuration 32");
    }

    private static void machineTypes() {
        int nativeMachines = 0, localMachines = 0, multiType = 0;
        for (var item : ForgeRegistries.ITEMS.getValues()) if (item instanceof MetaMachineItem machine) {
            var expected = machine.getDefinition().getRecipeTypes();
            var types = PatternGeneratorRecipes.machineTypes(new ItemStack(item));
            check(types.equals(expected == null ? List.of() : Arrays.stream(expected).filter(type -> type != null && type.registryName != null).distinct().toList()),
                    "Every registered GT machine exposes all actual recipe types: " + machine.getDefinition().getId());
            if (machine.getDefinition().getId().getNamespace().equals("gtl_enhancedcore")) localMachines++;
            else nativeMachines++;
            if (types.size() > 1) multiType++;
        }
        check(nativeMachines > 100 && localMachines > 5 && multiType > 5, "Recognition covers native, mixed-in and local machines without a five-machine list");
        GTLEnhancedcore.LOGGER.info("[PATTERN_GENERATOR] TYPES native={} local={} multiple={}", nativeMachines, localMachines, multiType);
    }

    private static void acquisition(MinecraftServer server) {
        var found = server.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING).stream()
                .filter(candidate -> candidate.getResultItem(server.registryAccess()).is(item(MvCircuitAssemblerRecipe.ID).getItem())).toList();
        for (var candidate : found) GTLEnhancedcore.LOGGER.info("[PATTERN_GENERATOR] ACQUISITION_RECIPE id={} class={} ingredients={}",
                candidate.getId(), candidate.getClass().getName(), candidate.getIngredients().stream().map(Ingredient::toJson).toList());
        check(found.size() == 1, "Exactly one crafting recipe produces the MV circuit assembler");
        var recipe = server.getRecipeManager().byKey(new ResourceLocation(MvCircuitAssemblerRecipe.RECIPE_ID)).orElseThrow();
        long circuits = recipe.getIngredients().stream().filter(ingredient -> ingredient.toJson().toString().contains("gtceu:circuits/mv")).count();
        check(circuits == 2 && recipe.getResultItem(server.registryAccess()).is(item("gtceu:mv_circuit_assembler").getItem()),
                "Live MV circuit assembler acquisition uses two MV circuits");
        check(recipe.getIngredients().stream().noneMatch(ingredient -> ingredient.toJson().toString().contains("gtceu:circuits/hv")), "Live MV assembler has no higher-tier circuit requirement");
        var other = server.getRecipeManager().byKey(new ResourceLocation("gtceu:shaped/hv_circuit_assembler")).orElseThrow();
        check(other.getIngredients().stream().anyMatch(ingredient -> ingredient.toJson().toString().contains("gtceu:circuits/ev")), "HV assembler's EV circuits remain unchanged");
        GTLEnhancedcore.LOGGER.info("[PATTERN_GENERATOR] ACQUISITION mv_circuit_assembler=MVx2 other_tiers_unchanged=true");
    }
}
