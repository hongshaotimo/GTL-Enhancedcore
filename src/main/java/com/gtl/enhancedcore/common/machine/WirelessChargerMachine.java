package com.gtl.enhancedcore.common.machine;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IMachineLife;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockDisplayText;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.EnergyHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.electric.HullMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.hepdd.gtmthings.api.misc.WirelessEnergyManager;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import java.math.BigInteger;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 无线充能器 —— 仅 LV（低压）/MV（高压）两台多方块，均用专用 schem 结构（无仓室位）。
 * 2026-08-13 用户要求移除 MV 以上全部无线充能器（HV~MAX）。
 *
 * 规则（见 功能与需求.md「无线充能器」）：
 * - 从放置者所属 GTMThings 无线电网拉电，不接有线能源仓。
 * - 固定以机器所在区块为中心，扫描 11×11 共 121 个区块，每 20 tick 一次。
 * - 辐散功耗：121 × 当前电压 × 1A（每 tick 累计，每 20 tick 结算）。
 * - 辐散供能：扫描区块内所有 GT 机器的能源容器，按缺电量从无线电网拉电并补满。
 * - 支持仓室：维护仓；不支持：并行控制仓、能源仓、激光靶仓。
 */
public class WirelessChargerMachine extends WorkableElectricMultiblockMachine implements IMachineLife {

    @NotNull
    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER =
            new ManagedFieldHolder(WirelessChargerMachine.class, WorkableElectricMultiblockMachine.MANAGED_FIELD_HOLDER);

    private static final int SCAN_INTERVAL = 20;
    /** 固定扫描半径 5（11×11 = 121 区块）。 */
    private static final int CHUNK_RADIUS = 5;
    private static final int CHUNK_COUNT = (2 * CHUNK_RADIUS + 1) * (2 * CHUNK_RADIUS + 1);


    private final int tier;

    @Persisted
    @DescSynced
    @Nullable
    private UUID userId;

    @Persisted
    @DescSynced
    private long totalDistributed;

    @Persisted
    @DescSynced
    private long lastDistributed;

    @Persisted
    private long accumulatedConsumption;

    private transient TickableSubscription chargerSubs;
    private int scanTimer;
    @DescSynced
    private boolean charging;
    @DescSynced
    private boolean wirelessPowerMissing;

    public WirelessChargerMachine(IMachineBlockEntity holder, int tier, Object... args) {
        super(holder, args);
        this.tier = tier;
    }

    @Override
    @NotNull
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    @Override
    public int getTier() {
        return this.tier;
    }

    @Override
    public long getMaxVoltage() {
        return GTValues.V[this.tier];
    }

    @Override
    public long getOverclockVoltage() {
        return GTValues.V[this.tier];
    }

    // ==================== 放置者绑定 ====================

    @Override
    public void onMachinePlaced(@Nullable LivingEntity player, @NotNull ItemStack stack) {
        if (player != null && !this.isRemote()) {
            this.userId = player.getUUID();
            this.markDirty();
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!this.isRemote()) {
            if (this.userId == null) {
                this.tryAutoBindUserId();
            }
            this.chargerSubs = this.subscribeServerTick(this.chargerSubs, this::chargerTick);
        }
    }

    @Override
    public void onUnload() {
        super.onUnload();
        if (this.chargerSubs != null) {
            this.chargerSubs.unsubscribe();
            this.chargerSubs = null;
        }
    }

    private void tryAutoBindUserId() {
        Level level = this.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        double x = this.getPos().getX() + 0.5;
        double y = this.getPos().getY() + 0.5;
        double z = this.getPos().getZ() + 0.5;
        ServerPlayer nearest = serverLevel.players().stream()
                .min(Comparator.comparingDouble(p -> p.distanceToSqr(x, y, z)))
                .orElse(null);
        if (nearest != null && nearest.distanceToSqr(x, y, z) <= 64.0) {
            this.userId = nearest.getUUID();
            this.markDirty();
        }
    }

    // ==================== 充能逻辑 ====================

    private void chargerTick() {
        if (!this.isFormed() || !this.recipeLogic.isWorkingEnabled()) {
            this.charging = false;
            return;
        }
        if (this.userId == null) {
            this.charging = false;
            this.tryAutoBindUserId();
            return;
        }

        long voltage = GTValues.V[this.tier];
        long perTick = (long) CHUNK_COUNT * voltage;
        this.accumulatedConsumption = saturatingAdd(this.accumulatedConsumption, perTick);
        this.markDirty();

        if (++this.scanTimer < SCAN_INTERVAL) {
            return;
        }
        this.scanTimer = 0;

        if (this.accumulatedConsumption > 0) {
            if (!WirelessEnergyManager.addEUToGlobalEnergyMap(
                    this.userId, BigInteger.valueOf(-this.accumulatedConsumption), this)) {
                this.accumulatedConsumption = 0;
                this.lastDistributed = 0;
                this.charging = false;
                this.wirelessPowerMissing = true;
                return;
            }
            this.accumulatedConsumption = 0;
        }

        this.charging = true;
        this.wirelessPowerMissing = false;
        this.lastDistributed = distributeEnergy();
        this.totalDistributed = saturatingAdd(this.totalDistributed, this.lastDistributed);
        this.markDirty();
    }

    private long distributeEnergy() {
        Level level = this.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return 0;
        }

        BlockPos machinePos = this.getPos();
        int machineChunkX = machinePos.getX() >> 4;
        int machineChunkZ = machinePos.getZ() >> 4;

        long distributed = 0;

        for (int dx = -CHUNK_RADIUS; dx <= CHUNK_RADIUS; dx++) {
            for (int dz = -CHUNK_RADIUS; dz <= CHUNK_RADIUS; dz++) {
                int chunkX = machineChunkX + dx;
                int chunkZ = machineChunkZ + dz;

                LevelChunk chunk = serverLevel.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) continue;

                for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
                    BlockPos pos = entry.getKey();
                    if (pos.equals(machinePos)) {
                        continue;
                    }

                    // 屏蔽能源仓：不给能源仓（EnergyHatchPartMachine）充能
                    com.gregtechceu.gtceu.api.machine.MetaMachine machine =
                            com.gregtechceu.gtceu.api.machine.MetaMachine.getMachine(serverLevel, pos);
                    // 白名单：只充 GT 体系内的单方块机器——非 GT 机器方块一律跳过
                    if (machine == null) {
                        continue;
                    }
                    if (machine instanceof EnergyHatchPartMachine) {
                       continue;
                    }
                    // 屏蔽末晶谐振器（发电机，不需要充电）
                    if (machine instanceof EndCrystalResonatorMachine) {
                       continue;
                    }
                    // 屏蔽所有等级的机器外壳（GTMachines.HULL = HullMachine）
                    if (machine instanceof HullMachine) {
                       continue;
                    }
                    // 多方块控制器与仓室/部件不属于“小型单方块机器”，一律跳过
                    if (machine instanceof IMultiController) {
                       continue;
                    }
                    if (machine instanceof IMultiPart) {
                       continue;
                    }

                    IEnergyContainer ec = null;
                    for (Direction dir : Direction.values()) {
                        ec = GTCapabilityHelper.getEnergyContainer(serverLevel, pos, dir);
                        if (ec != null) {
                            break;
                        }
                    }
                    if (ec == null) {
                       continue;
                    }

                    // 电压门控：目标机器额定输入电压超过本机电压等级时不供电
                    if (ec.getInputVoltage() <= 0 || ec.getInputVoltage() > GTValues.V[this.tier]) {
                       continue;
                    }

                    long stored = ec.getEnergyStored();
                    long capacity = ec.getEnergyCapacity();
                    long needed = capacity - stored;
                    if (needed <= 0) {
                       continue;
                    }

                    if (WirelessEnergyManager.addEUToGlobalEnergyMap(
                            this.userId, BigInteger.valueOf(-needed), this)) {
                        long accepted = Math.max(0L, Math.min(needed, ec.addEnergy(needed)));
                        if (accepted < needed) {
                            WirelessEnergyManager.addEUToGlobalEnergyMap(
                                    this.userId, BigInteger.valueOf(needed - accepted), this);
                        }
                        distributed = saturatingAdd(distributed, accepted);
                   }
                }
            }
        }

       return distributed;
    }

    // ==================== 显示文本 ====================

    @Override
    public void addDisplayText(java.util.List<Component> textList) {
        if (!this.isFormed()) {
            super.addDisplayText(textList);
            return;
        }
        // 不调用 super：无线充能器无能源仓，父类会显示无意义的 0 EU 能源行。
        MultiblockDisplayText.builder(textList, true)
                .setWorkingStatus(this.recipeLogic.isWorkingEnabled(), this.charging)
                .addWorkingStatusLine();
        long voltage = GTValues.V[this.tier];
        long selfCost = (long) CHUNK_COUNT * voltage;
        textList.add(Component.translatable("gui.gtl_enhancedcore.wireless_charger.tier",
                Component.literal(GTValues.VN[this.tier]).withStyle(ChatFormatting.LIGHT_PURPLE))
                .withStyle(ChatFormatting.GRAY));
        textList.add(Component.translatable("gui.gtl_enhancedcore.wireless_charger.selected",
                Component.literal(String.valueOf(CHUNK_COUNT)).withStyle(ChatFormatting.AQUA))
                .withStyle(ChatFormatting.GRAY));
        textList.add(Component.translatable("gui.gtl_enhancedcore.wireless_charger.self_cost",
                Component.literal(selfCost + " EU/t").withStyle(ChatFormatting.YELLOW))
                .withStyle(ChatFormatting.GRAY));
        textList.add(Component.translatable("gui.gtl_enhancedcore.wireless_charger.last_distributed",
                Component.literal(this.lastDistributed + " EU").withStyle(ChatFormatting.GREEN))
                .withStyle(ChatFormatting.GRAY));
        textList.add(Component.translatable("gui.gtl_enhancedcore.wireless_charger.total_distributed",
                Component.literal(this.totalDistributed + " EU").withStyle(ChatFormatting.GREEN))
                .withStyle(ChatFormatting.GRAY));
        com.gtl.enhancedcore.common.recipe.MachineDiagnostics.append(this, textList);
    }

    @Nullable
    public Component getDiagnostic() {
        if (userId == null) return com.gtl.enhancedcore.common.recipe.MachineDiagnostics.text("wireless_owner");
        return wirelessPowerMissing ? com.gtl.enhancedcore.common.recipe.MachineDiagnostics.text("wireless_power") : null;
    }

    // ==================== 工具方法 ====================

    private static long saturatingAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    @Nullable
    public UUID getUserId() {
        return this.userId;
    }
}
