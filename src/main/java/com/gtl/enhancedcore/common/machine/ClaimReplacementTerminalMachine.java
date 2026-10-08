package com.gtl.enhancedcore.common.machine;

import com.gtl.enhancedcore.common.config.GTLConfig;
import com.gtl.enhancedcore.common.util.BlockReplacementTransaction;
import com.gtl.enhancedcore.network.C2SClaimReplacementPacket;
import com.gtl.enhancedcore.network.GTLEnhancedcoreNetworkHandler;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IFancyUIMachine;
import com.gregtechceu.gtceu.api.machine.feature.IInteractedMachine;
import com.gregtechceu.gtceu.api.machine.feature.IMachineLife;
import com.hepdd.gtmthings.utils.TeamUtil;
import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import org.gtlcore.gtlcore.integration.ae2.WirelessTerminalGridResolver;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import com.lowdragmc.lowdraglib.LDLib;
import com.lowdragmc.lowdraglib.misc.ItemStackTransfer;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.PhantomSlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

/**
 * 领地置换终端（单方块机器，GT Fancy UI）：
 * - 绑定：右键网络配置闪存绑定（uuid=FTB队伍ID + 频率），放置时自动绑定放置者队伍。
 * - 检测：扫描当前维度内“绑定队伍已认领且强加载”的区块，统计与“被替换物”同 ID 的方块。
 * - 替换：每 replace_interval_ticks 处理 1 个（默认 5 tick），复制旧方块全部共有属性，
 *   旧方块进入操作玩家背包（满则掉落），新方块按玩家放置流程放置（音效/事件/方块实体初始化，
 *   绑定类方块会自动绑定玩家数据）。
 * - 仅支持强加载区块；不支持复制 NBT；全局配置可关闭（GTLConfig）。
 */
@ParametersAreNonnullByDefault
public class ClaimReplacementTerminalMachine extends MetaMachine
        implements IFancyUIMachine, IInteractedMachine, IMachineLife {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            ClaimReplacementTerminalMachine.class, MetaMachine.MANAGED_FIELD_HOLDER);

    public static final String FLASH_ITEM_ID = "gtmadvancedhatch:tool_net_data_stick";
    public static final String TAG_UUID = "adaptive_net_uuid";
    public static final String TAG_FREQUENCY = "adaptive_net_frequency";

    /** 绑定队伍 UUID（来自闪存/放置者队伍）。 */
    @Persisted
    @DescSynced
    protected UUID net_uuid = new UUID(0L, 0L);
    @Persisted
    protected long frequency = 0L;
    /** 绑定玩家（物品进背包/放置归属）。 */
    @Persisted
    protected UUID ownerPlayer = null;

    /** 替换物（新物品，只用于放置）。 */
    @Persisted
    @DescSynced
    protected final ItemStackTransfer replacementSlot = new ItemStackTransfer(1);
    /** 被替换物（领地内旧方块，检测扫它）。 */
    @Persisted
    @DescSynced
    protected final ItemStackTransfer targetSlot = new ItemStackTransfer(1);

    /** AE 模式：替换物从 AE 抽取，被替换方块自动存入 AE。 */
    @Persisted
    @DescSynced
    protected boolean aeMode = false;
    @Persisted
    @DescSynced
    protected int detectCount = 0;
    @Persisted
    @DescSynced
    protected int totalCount = 0;
    @Persisted
    @DescSynced
    protected boolean detected = false;
    @Persisted
    @DescSynced
    protected boolean replacing = false;
    @Persisted
    @DescSynced
    protected int doneCount = 0;
    @Persisted
    @DescSynced
    protected int skippedCount = 0;
    @Persisted
    @DescSynced
    protected String resultKey = "";

    /** 待替换位置队列（不持久化，重启后需重新检测）。 */
    private final List<BlockPos> pending = new ArrayList<>();
    private int queueIndex = 0;
    private TickableSubscription replaceSub;
    private UUID operationPlayer;
    private ItemStack detectedReplacement = ItemStack.EMPTY;
    private ItemStack detectedTarget = ItemStack.EMPTY;

    private enum ReplacementResult {
        REPLACED(""), CHANGED("changed"), PROTECTED("protected"), UNLOADED("unloaded"),
        INVALID("replacement_not_block"), INSUFFICIENT("insufficient"), PLACE_FAILED("place_failed");

        final String key;
        ReplacementResult(String key) { this.key = key; }
    }

    public ClaimReplacementTerminalMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    // =============================== 绑定 ==================================

    @Override
    public void onMachinePlaced(@Nullable LivingEntity placer, ItemStack stack) {
        if (placer instanceof ServerPlayer player) {
            this.ownerPlayer = player.getUUID();
            this.net_uuid = TeamUtil.getTeamUUID(player.getUUID());
            ItemStack bound = findBoundFlash(player);
            if (bound != null && bound.hasTag()) {
                applyFlash(bound);
            }
            this.markDirty();
            player.displayClientMessage(Component.translatable("gtl_enhancedcore.claim_replacement.bound"), true);
        }
    }

    @Override
    public InteractionResult onUse(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack is = player.getItemInHand(hand);
        if (!is.isEmpty() && isFlash(is)) {
            if (player instanceof ServerPlayer sp) {
                if (this.ownerPlayer != null && !canOperate(sp)) return InteractionResult.FAIL;
                invalidateDetection();
                this.ownerPlayer = sp.getUUID();
                applyFlash(is);
                this.markDirty();
                sp.displayClientMessage(Component.translatable("gtl_enhancedcore.claim_replacement.bound"), true);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public boolean onLeftClick(Player player, Level world, InteractionHand hand, BlockPos pos, Direction direction) {
        ItemStack is = player.getItemInHand(hand);
        if (!is.isEmpty() && isFlash(is)) {
            if (!(player instanceof ServerPlayer serverPlayer) || !canOperate(serverPlayer)) return true;
            invalidateDetection();
            this.net_uuid = new UUID(0L, 0L);
            this.frequency = 0L;
            this.ownerPlayer = null;
            this.markDirty();
            player.sendSystemMessage(Component.translatable("gtl_enhancedcore.claim_replacement.unbound"));
            return true;
        }
        return false;
    }

    private static boolean isFlash(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        var key = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key != null && FLASH_ITEM_ID.equals(key.toString());
    }

    private static ItemStack findBoundFlash(Player player) {
        for (ItemStack stack : player.getInventory().items) {
            if (isFlash(stack)) {
                return stack;
            }
        }
        return null;
    }

    private void applyFlash(ItemStack flash) {
        CompoundTag tag = flash.getTag();
        if (tag == null) {
            return;
        }
        if (tag.hasUUID(TAG_UUID)) {
            this.net_uuid = tag.getUUID(TAG_UUID);
        }
        if (tag.contains(TAG_FREQUENCY)) {
            this.frequency = tag.getLong(TAG_FREQUENCY);
        }
        this.markDirty();
    }

    public boolean canOperate(ServerPlayer player) {
        if (this.ownerPlayer == null) return false;
        if (this.ownerPlayer.equals(player.getUUID())) return true;
        var manager = FTBTeamsAPI.api().getManager();
        var ownerTeam = manager.getTeamForPlayerID(this.ownerPlayer);
        var playerTeam = manager.getTeamForPlayerID(player.getUUID());
        return ownerTeam.isPresent() && playerTeam.isPresent()
                && ownerTeam.get().getId().equals(playerTeam.get().getId());
    }

    private void invalidateDetection() {
        stopReplace();
        this.pending.clear();
        this.queueIndex = 0;
        this.detected = false;
        this.detectCount = 0;
        this.totalCount = 0;
        this.doneCount = 0;
        this.skippedCount = 0;
        this.resultKey = "";
        this.detectedReplacement = ItemStack.EMPTY;
        this.detectedTarget = ItemStack.EMPTY;
        this.markDirty();
    }

    private boolean templatesUnchanged() {
        return ItemStack.isSameItemSameTags(this.detectedReplacement, this.replacementSlot.getStackInSlot(0))
                && ItemStack.isSameItemSameTags(this.detectedTarget, this.targetSlot.getStackInSlot(0));
    }

    private void templateChanged() {
        if (this.isRemote()) return;
        // Slot.setChanged can run without changing the ghost template.
        if (this.detected && !templatesUnchanged()) invalidateDetection();
        this.markDirty();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!this.isRemote()) invalidateDetection();
    }

    // =============================== 按钮逻辑 ==================================

    public void detectClicked(ServerPlayer player) {
        if (!canOperate(player)) return;
        if (!GTLConfig.CLAIM_REPLACEMENT_ENABLED.get()) {
            player.sendSystemMessage(Component.translatable("gtl_enhancedcore.claim_replacement.disabled"));
            return;
        }
        ItemStack repl = this.replacementSlot.getStackInSlot(0);
        ItemStack target = this.targetSlot.getStackInSlot(0);
        if (repl.isEmpty()) {
            player.sendSystemMessage(Component.translatable("gtl_enhancedcore.claim_replacement.replacement_unknown"));
            return;
        }
        if (target.isEmpty()) {
            player.sendSystemMessage(Component.translatable("gtl_enhancedcore.claim_replacement.target_unknown"));
            return;
        }
        if (this.ownerPlayer == null) {
            player.sendSystemMessage(Component.translatable("gtl_enhancedcore.claim_replacement.not_bound"));
            return;
        }
        if (!(this.getLevel() instanceof ServerLevel serverLevel) || player.level() != serverLevel) {
            return;
        }
        Block targetBlock = resolveTargetBlock(target);
        if (targetBlock == Blocks.AIR) {
            player.sendSystemMessage(Component.translatable("gtl_enhancedcore.claim_replacement.target_not_block"));
            return;
        }
        if (!(repl.getItem() instanceof BlockItem)) {
            player.sendSystemMessage(Component.translatable("gtl_enhancedcore.claim_replacement.replacement_not_block"));
            return;
        }
        if (resolveTargetBlock(repl) == targetBlock) {
            player.sendSystemMessage(Component.translatable("gtl_enhancedcore.claim_replacement.same_block"));
            return;
        }
        invalidateDetection();
        List<BlockPos> found = scanTerritory(serverLevel, targetBlock);
        this.pending.addAll(found);
        this.detectCount = found.size();
        this.totalCount = found.size();
        this.detected = true;
        this.doneCount = 0;
        this.replacing = false;
        this.detectedReplacement = repl.copy();
        this.detectedTarget = target.copy();
        this.markDirty();
        player.sendSystemMessage(Component.translatable("gtl_enhancedcore.claim_replacement.detect_result", found.size()));
    }

    private List<BlockPos> scanTerritory(ServerLevel level, Block targetBlock) {
        List<BlockPos> result = new ArrayList<>();
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();
        int teamChunks = 0;
        if (this.ownerPlayer == null || !FTBChunksAPI.api().isManagerLoaded()) {
            GTLEnhancedcore.LOGGER.debug("[领地置换] 未绑定玩家或 FTBChunks 未加载");
            return result;
        }
        // 方式一：按绑定玩家的 FTB 队伍，直接取该队伍的强加载区块
        Optional<Team> teamOpt = FTBTeamsAPI.api().getManager().getTeamForPlayerID(this.ownerPlayer);
        if (teamOpt.isPresent()) {
            Team team = teamOpt.get();
            ChunkTeamData data = FTBChunksAPI.api().getManager().getOrCreateData(team);
            for (ClaimedChunk chunk : data.getForceLoadedChunks()) {
                ChunkDimPos dimPos = chunk.getPos();
                if (dimPos == null || !dimPos.dimension().equals(level.dimension()) || !eligibleClaim(chunk)) {
                    continue;
                }
                teamChunks++;
                scanChunk(level, dimPos.getChunkPos(), targetBlock, minY, maxY, result);
            }
        }
        // 方式二（兜底）：按维度强加载表 + 队伍 UUID 比对
        if (result.isEmpty() && teamChunks == 0 && this.net_uuid != null) {
            Long2ObjectMap<UUID> forceLoaded = FTBChunksAPI.api().getManager().getForceLoadedChunks(level.dimension());
            for (Long2ObjectMap.Entry<UUID> entry : forceLoaded.long2ObjectEntrySet()) {
                if (!this.net_uuid.equals(entry.getValue())) {
                    continue;
                }
                ChunkPos cp = new ChunkPos(entry.getLongKey());
                BlockPos minPos = new BlockPos(cp.getMinBlockX(), minY, cp.getMinBlockZ());
                if (!level.isLoaded(minPos)) {
                    continue;
                }
                teamChunks++;
                scanChunk(level, cp, targetBlock, minY, maxY, result);
            }
        }
        GTLEnhancedcore.LOGGER.debug("[领地置换] 队伍强加载区块数={} 匹配方块={} 维度={}", teamChunks, result.size(), level.dimension().location());
        return result;
    }

    private void scanChunk(ServerLevel level, ChunkPos cp, Block targetBlock, int minY, int maxY, List<BlockPos> result) {
        var chunk = level.getChunkSource().getChunkNow(cp.x, cp.z);
        if (chunk == null) return;
        var sections = chunk.getSections();
        boolean[] candidates = new boolean[sections.length];
        for (int i = 0; i < sections.length; i++) {
            candidates[i] = !sections[i].hasOnlyAir() && sections[i].maybeHas(state -> state.is(targetBlock));
        }
        // Palette checks avoid reading every block of sections that cannot contain the target.
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
                    var section = sections[sectionIndex];
                    if (!candidates[sectionIndex]) continue;
                    int baseY = chunk.getSectionYFromSectionIndex(sectionIndex) << 4;
                    for (int localY = 0; localY < 16; localY++) {
                        int y = baseY + localY;
                        if (y >= minY && y < maxY && section.getBlockState(x, localY, z).is(targetBlock)) {
                            result.add(new BlockPos(cp.getMinBlockX() + x, y, cp.getMinBlockZ() + z));
                        }
                    }
                }
            }
        }
    }


    public void replaceClicked(ServerPlayer player) {
        if (!canOperate(player)) return;
        if (!GTLConfig.CLAIM_REPLACEMENT_ENABLED.get()) {
            player.sendSystemMessage(Component.translatable("gtl_enhancedcore.claim_replacement.disabled"));
            return;
        }
        if (!this.detected || this.totalCount <= 0 || this.pending.isEmpty()) {
            player.sendSystemMessage(Component.translatable("gtl_enhancedcore.claim_replacement.detect_first"));
            return;
        }
        if (this.replacementSlot.getStackInSlot(0).isEmpty()) {
            player.sendSystemMessage(Component.translatable("gtl_enhancedcore.claim_replacement.replacement_unknown"));
            return;
        }
        if (this.replacing) {
            player.sendSystemMessage(Component.translatable("gtl_enhancedcore.claim_replacement.replacing"));
            return;
        }
        if (!templatesUnchanged()) {
            invalidateDetection();
            player.sendSystemMessage(Component.translatable("gtl_enhancedcore.claim_replacement.detect_first"));
            return;
        }
        ItemStack repl = this.replacementSlot.getStackInSlot(0);
        long available;
        if (this.aeMode) {
            if (!WirelessTerminalGridResolver.hasWirelessTerminal(player)) {
                player.sendSystemMessage(Component.translatable("gtl_enhancedcore.claim_replacement.no_wireless_terminal"));
                return;
            }
            MEStorage storage = getAeStorage(player);
            if (storage == null) {
                player.sendSystemMessage(Component.translatable("gtl_enhancedcore.claim_replacement.no_wireless_terminal"));
                return;
            }
            available = storage.extract(AEItemKey.of(repl), this.totalCount, Actionable.SIMULATE, IActionSource.ofPlayer(player));
        } else {
            available = countReplacementInInventory(player, repl);
        }
        if (available <= 0L || available < this.totalCount) {
            player.sendSystemMessage(Component.translatable("gtl_enhancedcore.claim_replacement.insufficient"));
            return;
        }

        this.replacing = true;
        this.operationPlayer = player.getUUID();
        this.queueIndex = 0;
        this.doneCount = 0;
        this.skippedCount = 0;
        this.resultKey = "";
        this.replaceSub = subscribeServerTick(this.replaceSub, this::tickReplace);
        this.markDirty();
        player.sendSystemMessage(Component.translatable("gtl_enhancedcore.claim_replacement.replace_start", this.totalCount));
    }

    // =============================== 替换执行 ==================================

    private void tickReplace() {
        if (!this.replacing || !(this.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        MinecraftServer server = serverLevel.getServer();
        if (!GTLConfig.CLAIM_REPLACEMENT_ENABLED.get()) {
            finishReplace(null, "disabled");
            return;
        }
        if (server.getTickCount() % GTLConfig.CLAIM_REPLACEMENT_INTERVAL_TICKS.get() != 0) return;
        ServerPlayer player = this.operationPlayer == null ? null : server.getPlayerList().getPlayer(this.operationPlayer);
        if (player == null || player.isRemoved() || player.level() != serverLevel) {
            if (!this.resultKey.equals("waiting_player")) {
                this.resultKey = "waiting_player";
                this.markDirty();
            }
            return;
        }
        if (!canOperate(player)) {
            finishReplace(player, "permission_lost");
            return;
        }
        if (!templatesUnchanged()) {
            finishReplace(player, "templates_changed");
            return;
        }
        if (this.resultKey.equals("waiting_player")) this.resultKey = "";
        if (this.queueIndex >= this.pending.size()) {
            finishReplace(player, "complete");
            return;
        }
        BlockPos pos = this.pending.get(this.queueIndex++);
        ReplacementResult result = replaceOne(serverLevel, player, pos);
        if (result == ReplacementResult.REPLACED) {
            this.doneCount++;
        } else if (result == ReplacementResult.INSUFFICIENT || result == ReplacementResult.INVALID) {
            finishReplace(player, result.key);
            return;
        } else {
            this.skippedCount++;
            this.resultKey = result.key;
        }
        if (this.queueIndex >= this.pending.size()) finishReplace(player, "complete");
        this.markDirty();
    }

    private void finishReplace(@Nullable ServerPlayer player, String reason) {
        stopReplace();
        this.detected = false;
        this.detectCount = 0;
        this.pending.clear();
        if (reason.equals("complete")) {
            if (this.resultKey.isEmpty()) this.resultKey = "complete";
            if (player != null) player.sendSystemMessage(Component.translatable(
                    "gtl_enhancedcore.claim_replacement.finished", this.doneCount, this.skippedCount));
        } else {
            this.resultKey = reason;
        }
        if (player != null && !this.resultKey.equals("complete")) player.sendSystemMessage(
                Component.translatable("gtl_enhancedcore.claim_replacement." + this.resultKey));
        this.markDirty();
    }

    private void stopReplace() {
        this.replacing = false;
        this.operationPlayer = null;
        this.markDirty();
        if (this.replaceSub != null) {
            this.replaceSub.unsubscribe();
            this.replaceSub = null;
        }
    }

    private ReplacementResult replaceOne(ServerLevel level, ServerPlayer player, BlockPos pos) {
        if (!level.isLoaded(pos)) return ReplacementResult.UNLOADED;
        if (pos.equals(this.getPos()) || !level.mayInteract(player, pos) || !stillOwnsClaim(level, player, pos)) {
            return ReplacementResult.PROTECTED;
        }
        var targetMachine = MetaMachine.getMachine(level, pos);
        if (targetMachine instanceof org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachineBase buffer
                && com.gtl.enhancedcore.common.recipe.iv.IvBuffers.isolated(buffer)) return ReplacementResult.PROTECTED;
        BlockState old = level.getBlockState(pos);
        Block targetBlock = resolveTargetBlock(this.targetSlot.getStackInSlot(0));
        if (old.isAir() || old.getBlock() != targetBlock) {
            return ReplacementResult.CHANGED;
        }
        ItemStack repl = this.replacementSlot.getStackInSlot(0);
        if (repl.isEmpty() || !(repl.getItem() instanceof BlockItem blockItem)) {
            return ReplacementResult.INVALID;
        }
        // Honor protection mods and the IV in-flight task break guard before removing anything.
        if (old.getDestroySpeed(level, pos) < 0 || net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                new net.minecraftforge.event.level.BlockEvent.BreakEvent(level, pos, old, player))) {
            return ReplacementResult.PROTECTED;
        }
        if (level.getBlockState(pos) != old) return ReplacementResult.CHANGED;
        boolean useAe = this.aeMode;
        MEStorage storage = useAe ? getAeStorage(player) : null;
        if (useAe && storage == null) return ReplacementResult.INSUFFICIENT;
        ItemStack material = repl.copyWithCount(1);
        // 复制旧方块全部共有属性（朝向等）
        BlockState newState = blockItem.getBlock().defaultBlockState();
        for (Property<?> property : old.getProperties()) {
            if (newState.hasProperty(property)) {
                newState = copyProperty(newState, old, property);
            }
        }
        var placement = BlockReplacementTransaction.replace(level, player, pos, newState, material,
                () -> useAe ? storage.extract(AEItemKey.of(material), 1L, Actionable.MODULATE, IActionSource.ofPlayer(player)) > 0L
                        : consumeOneFromInventory(player, material));
        if (placement != BlockReplacementTransaction.Result.PLACED
                && placement != BlockReplacementTransaction.Result.PLACED_WITH_WARNING) {
            return switch (placement) {
                case CANCELED -> ReplacementResult.PROTECTED;
                case INSUFFICIENT -> ReplacementResult.INSUFFICIENT;
                default -> ReplacementResult.PLACE_FAILED;
            };
        }
        // 旧方块处置：AE 模式存入 AE（失败给玩家），否则给玩家
        ItemStack oldItem = new ItemStack(old.getBlock().asItem());
        returnItemToStorageOrPlayer(useAe ? storage : null, player, oldItem);
        if (placement == BlockReplacementTransaction.Result.PLACED_WITH_WARNING) {
            player.sendSystemMessage(Component.translatable("gtl_enhancedcore.claim_replacement.placement_warning",
                    pos.getX(), pos.getY(), pos.getZ()).withStyle(ChatFormatting.YELLOW));
        }
        level.playSound(null, pos, newState.getSoundType().getPlaceSound(), SoundSource.BLOCKS,
                (newState.getSoundType().getVolume() + 1.0F) / 2.0F, newState.getSoundType().getPitch() * 0.8F);
        return ReplacementResult.REPLACED;
    }

    private static void returnItemToStorageOrPlayer(@Nullable MEStorage storage, ServerPlayer player, ItemStack material) {
        if (material.isEmpty()) return;
        ItemStack returned = material.copyWithCount(1);
        if (storage != null) {
            try {
                if (storage.insert(AEItemKey.of(returned), 1L, Actionable.MODULATE, IActionSource.ofPlayer(player)) > 0L) return;
            } catch (RuntimeException failure) {
                GTLEnhancedcore.LOGGER.warn("Replacement return item could not reach the ME network", failure);
            }
        }
        giveItem(player, returned);
    }

    /** 给玩家物品，背包满则掉落。 */
    private static void giveItem(Player player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) {
            return;
        }
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    /** AE 模式开关（按钮）。 */
    /** 通过玩家身上的无线终端解析 AE 存储（无终端/无链接返回 null）。 */
    @Nullable
    private MEStorage getAeStorage(ServerPlayer player) {
        try {
            IGrid grid = WirelessTerminalGridResolver.find(player, player.level());
            if (grid == null) {
                return null;
            }
            IStorageService svc = grid.getStorageService();
            return svc == null ? null : svc.getInventory();
        } catch (RuntimeException failure) {
            GTLEnhancedcore.LOGGER.debug("Unable to resolve replacement terminal ME network", failure);
            return null;
        }
    }

    public void toggleAeMode(ServerPlayer player) {
        if (!canOperate(player)) return;
        invalidateDetection();
        this.aeMode = !this.aeMode;
        this.markDirty();
        if (player != null) {
            player.sendSystemMessage(Component.translatable(this.aeMode
                    ? "gtl_enhancedcore.claim_replacement.ae_mode_on"
                    : "gtl_enhancedcore.claim_replacement.ae_mode_off"));
        }
    }

    /** 停止按钮：中断当前替换。 */
    public void stopClicked(ServerPlayer player) {
        if (!canOperate(player)) return;
        finishReplace(player, "stopped");
    }


    private static <T extends Comparable<T>> BlockState copyProperty(BlockState target, BlockState source, Property<T> property) {
        return target.setValue(property, source.getValue(property));
    }

    /** 统计背包内与替换物同 ID 的物品总数（仅主背包）。 */
    /** 解析目标方块：优先按物品注册名找同 ID 方块（兼容 GT 机器部件/魔改维护仓），失败退回 Block.byItem。 */
    private static Block resolveTargetBlock(ItemStack target) {
        if (target == null || target.isEmpty()) {
            return Blocks.AIR;
        }
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(target.getItem());
        if (key != null) {
            Block byName = ForgeRegistries.BLOCKS.getValue(key);
            if (byName != null && byName != Blocks.AIR) {
                return byName;
            }
        }
        return Block.byItem(target.getItem());
    }

    private static long countReplacementInInventory(Player player, ItemStack template) {
        long count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && ItemStack.isSameItemSameTags(stack, template)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** 从背包扣除 1 个与替换物同 ID 的物品。 */
    private static boolean consumeOneFromInventory(Player player, ItemStack template) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.items.size(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && ItemStack.isSameItemSameTags(stack, template)) {
                stack.shrink(1);
                if (stack.isEmpty()) {
                    inventory.setItem(i, ItemStack.EMPTY);
                }
                return true;
            }
        }
        return false;
    }


    private boolean stillOwnsClaim(ServerLevel level, ServerPlayer player, BlockPos pos) {
        if (!FTBChunksAPI.api().isManagerLoaded()) return false;
        var team = FTBTeamsAPI.api().getManager().getTeamForPlayerID(player.getUUID());
        if (team.isEmpty()) return false;
        var claim = FTBChunksAPI.api().getManager().getChunk(
                new ChunkDimPos(level.dimension(), pos.getX() >> 4, pos.getZ() >> 4));
        return eligibleClaim(claim) && team.get().getId().equals(claim.getTeamData().getTeam().getId());
    }

    private static boolean eligibleClaim(@Nullable ClaimedChunk claim) {
        // The global force-load cache only includes offline-eligible tickets, not every marked claim.
        return claim != null && claim.isForceLoaded() && !claim.hasForceLoadExpired(System.currentTimeMillis());
    }


    @Override
    public void onUnload() {
        super.onUnload();
        stopReplace();
    }

    @Override
    public void onMachineRemoved() {
        stopReplace();
        this.replacementSlot.setStackInSlot(0, ItemStack.EMPTY);
        this.targetSlot.setStackInSlot(0, ItemStack.EMPTY);
    }

    // =============================== GUI ==================================

    @Override
    public Widget createUIWidget() {
        WidgetGroup group = new WidgetGroup(0, 0, 176, 160);
        group.addWidget(new ImageWidget(4, 4, 168, 152, GuiTextures.BACKGROUND_INVERSE));
        group.addWidget(new LabelWidget(10, 12,
                Component.translatable("gtl_enhancedcore.claim_replacement.slot_replacement").getString()));
        group.addWidget(new PhantomSlotWidget(this.replacementSlot, 0, 118, 10)
                .setBackgroundTexture(GuiTextures.SLOT)
                .setChangeListener(this::templateChanged));
        group.addWidget(new LabelWidget(10, 36,
                Component.translatable("gtl_enhancedcore.claim_replacement.slot_target").getString()));
        group.addWidget(new PhantomSlotWidget(this.targetSlot, 0, 118, 34)
                .setBackgroundTexture(GuiTextures.SLOT)
                .setChangeListener(this::templateChanged));
        var status = new LabelWidget(10, 60, this::getStatusText);
        status.setClientSideWidget();
        group.addWidget(status);
        var detail = new TextTexture(() -> this.resultKey.isEmpty() ? "" :
                Component.translatable("gtl_enhancedcore.claim_replacement." + this.resultKey).getString())
                .setWidth(152).setType(TextTexture.TextType.ROLL);
        group.addWidget(new ImageWidget(10, 73, 152, 12, detail));

        group.addWidget(new ButtonWidget(10, 92, 74, 18,
                new GuiTextureGroup(new IGuiTexture[]{GuiTextures.BUTTON,
                        new TextTexture(Component.translatable("gtl_enhancedcore.claim_replacement.detect").getString())}),
                cd -> sendAction(C2SClaimReplacementPacket.MODE_DETECT)));

        boolean canReplace = this.detected && this.totalCount > 0;
        TextTexture replaceText = new TextTexture(Component.translatable("gtl_enhancedcore.claim_replacement.replace").getString());
        if (!canReplace) {
            replaceText.setColor(ChatFormatting.GRAY.getColor());
        }
        ButtonWidget replaceButton = new ButtonWidget(92, 92, 74, 18,
                new GuiTextureGroup(new IGuiTexture[]{GuiTextures.BUTTON, replaceText}),
                cd -> sendAction(C2SClaimReplacementPacket.MODE_REPLACE));
        if (!canReplace) {
            replaceButton.setHoverTooltips(List.of(
                    Component.translatable("gtl_enhancedcore.claim_replacement.replace_disabled_tip")));
        }
        group.addWidget(replaceButton);

        TextTexture aeText = new TextTexture(modePrefix());
        if (!this.aeMode) {
            aeText.setColor(ChatFormatting.GRAY.getColor());
        }
        ButtonWidget aeButton = new ButtonWidget(10, 116, 74, 18,
                new GuiTextureGroup(new IGuiTexture[]{GuiTextures.BUTTON, aeText}),
                        cd -> sendAction(C2SClaimReplacementPacket.MODE_TOGGLE_AE));
        group.addWidget(aeButton);

        // 每 tick 刷新按钮状态（替换按钮灰色/亮色、AE 模式标签）
        group.addWidget(new WidgetGroup(0, 0, 0, 0) {
            @Override
            public void updateScreen() {
                if (!LDLib.isRemote() || ClaimReplacementTerminalMachine.this.getLevel() == null) {
                    return;
                }
                boolean can = ClaimReplacementTerminalMachine.this.detected && ClaimReplacementTerminalMachine.this.totalCount > 0;
                TextTexture rt = new TextTexture(Component.translatable("gtl_enhancedcore.claim_replacement.replace").getString());
                if (!can) {
                    rt.setColor(ChatFormatting.GRAY.getColor());
                    replaceButton.setHoverTooltips(List.of(Component.translatable("gtl_enhancedcore.claim_replacement.replace_disabled_tip")));
                } else {
                    replaceButton.setHoverTooltips(List.of());
                }
                replaceButton.setButtonTexture(new GuiTextureGroup(new IGuiTexture[]{GuiTextures.BUTTON, rt}));

                String aeLabel = Component.translatable(ClaimReplacementTerminalMachine.this.aeMode
                        ? "gtl_enhancedcore.claim_replacement.mode_ae"
                        : "gtl_enhancedcore.claim_replacement.mode_backpack").getString();
                TextTexture at = new TextTexture(aeLabel).setWidth(70);
                if (!ClaimReplacementTerminalMachine.this.aeMode) {
                    at.setColor(ChatFormatting.GRAY.getColor());
                }
                aeButton.setButtonTexture(new GuiTextureGroup(new IGuiTexture[]{GuiTextures.BUTTON, at}));
            }
        });

        group.addWidget(new ButtonWidget(92, 116, 74, 18,
                new GuiTextureGroup(new IGuiTexture[]{GuiTextures.BUTTON,
                        new TextTexture(Component.translatable("gtl_enhancedcore.claim_replacement.stop").getString())}),
                cd -> sendAction(C2SClaimReplacementPacket.MODE_STOP)));
        return group;
    }


    private void sendAction(int mode) {
        if (LDLib.isRemote() && this.getLevel() != null) {
            GTLEnhancedcoreNetworkHandler.CHANNEL.sendToServer(new C2SClaimReplacementPacket(mode, this.getPos()));
        }
    }

    private String getStatusText() {
        if (!GTLConfig.CLAIM_REPLACEMENT_ENABLED.get()) {
            return Component.translatable("gtl_enhancedcore.claim_replacement.status_disabled").getString();
        }
        if (this.replacing) {
            return modePrefix() + " | " + Component.translatable("gtl_enhancedcore.claim_replacement.status_replacing", this.doneCount, this.totalCount).getString();
        }
        if (!this.detected) {
            if (this.totalCount > 0) return Component.translatable(
                    "gtl_enhancedcore.claim_replacement.status_finished", this.doneCount, this.totalCount, this.skippedCount).getString();
            return modePrefix() + " | " + Component.translatable("gtl_enhancedcore.claim_replacement.status_idle").getString();
        }
        return modePrefix() + " | " + Component.translatable("gtl_enhancedcore.claim_replacement.status_detected", this.detectCount).getString();
    }

    private String modePrefix() {
        return this.aeMode
                ? Component.translatable("gtl_enhancedcore.claim_replacement.mode_ae").getString()
                : Component.translatable("gtl_enhancedcore.claim_replacement.mode_backpack").getString();
    }
}
