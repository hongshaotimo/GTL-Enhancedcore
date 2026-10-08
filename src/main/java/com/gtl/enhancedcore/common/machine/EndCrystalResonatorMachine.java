package com.gtl.enhancedcore.common.machine;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.TieredEnergyMachine;
import com.gregtechceu.gtceu.api.machine.feature.IMachineLife;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableEnergyContainer;
import com.hepdd.gtmthings.api.misc.WirelessEnergyManager;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import java.math.BigInteger;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 末晶谐振器 —— LV~MAX 共 14 台单方块无线发电机。
 *
 * 规则（见 API标准.md「末晶谐振器」）：
 * - 顶面正上方存在末影水晶即持续发电，不消耗水晶。
 * - 发电量 = 对应电压 × 安培数，每 tick 累计，每 20 tick 结算到 GTMThings 无线电网。
 * - 只向放置者所属队伍结算，不接有线输出。
 * - 电量缓存 accumulatedEU 持久化，避免卸载区块时丢电。
 * - 电压/安培/容量统一由本类的静态方法计算，注册端与逻辑端共用同一份公式（禁止各写一遍）。
 */
public class EndCrystalResonatorMachine extends TieredEnergyMachine implements IMachineLife {

    @NotNull
    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER =
            new ManagedFieldHolder(EndCrystalResonatorMachine.class, TieredEnergyMachine.MANAGED_FIELD_HOLDER);

    /** 无线结算周期（tick）。每 20 tick（1 秒）向电网提交一次。 */
    private static final int WIRELESS_FLUSH_INTERVAL = 20;

    /** 待结算电量缓存。持久化，防止卸载区块丢失。 */
    @Persisted
    private long accumulatedEU;

    /** 放置者 UUID。GTMThings 内部会自行映射为 FTB 队伍 UUID，此处只存玩家 UUID。 */
    @Persisted
    @Nullable
    private UUID userId;

    private int wirelessFlushTicks;
    private boolean crystalPresent;

    public EndCrystalResonatorMachine(@NotNull IMachineBlockEntity holder, int tier, Object... args) {
        super(holder, tier, args);
        this.subscribeServerTick(null, this::checkEnergy);
    }

    // ==================== 统一电力公式（唯一真源） ====================

    /** LV 为 16A，其余等级为 64A。 */
    public static long amperageForTier(int tier) {
        return tier == GTValues.LV ? 16L : 64L;
    }

    /** 缓存容量：16384 EU 起，电压每高一级翻倍。 */
    public static long capacityForTier(int tier) {
        return 16384L << (tier - 1);
    }

    /** 标称发电量（EU/t）= 电压 × 安培。 */
    public static long generationForTier(int tier) {
        return GTValues.V[tier] * amperageForTier(tier);
    }

    // ==================== 能量容器 ====================

    @NotNull
    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    @NotNull
    @Override
    protected NotifiableEnergyContainer createEnergyContainer(@NotNull Object[] args) {
        long voltage = GTValues.V[this.getTier()];
        long amperage = amperageForTier(this.getTier());
        long capacity = capacityForTier(this.getTier());
        return NotifiableEnergyContainer.emitterContainer((MetaMachine) this, capacity, voltage, amperage);
    }

    @Override
    protected boolean isEnergyEmitter() {
        return true;
    }

    @Override
    protected long getMaxInputOutputAmperage() {
        return amperageForTier(this.getTier());
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
        if (!this.isRemote() && this.userId == null) {
            this.tryAutoBindUserId();
        }
    }

    /** 旧机器缺失 UUID 时，绑定 8 格内最近的玩家。 */
    private void tryAutoBindUserId() {
        Level level = this.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        double x = this.getPos().getX() + 0.5;
        double y = this.getPos().getY() + 0.5;
        double z = this.getPos().getZ() + 0.5;
        ServerPlayer nearest = serverLevel.players().stream()
                .min(Comparator.comparingDouble(player -> player.distanceToSqr(x, y, z)))
                .orElse(null);
        if (nearest != null && nearest.distanceToSqr(x, y, z) <= 64.0) {
            this.userId = nearest.getUUID();
            this.markDirty();
        }
    }

    // ==================== 发电逻辑 ====================

    /**
     * 精确判定：只认正上方那一格坐标里的末影水晶。
     * 不使用 inflate 膨胀盒，避免一颗水晶被判定给相邻多台机器（白嫖漏洞）。
     */
    private boolean hasCrystalAbove() {
        Level level = this.getLevel();
        if (level == null) {
            return false;
        }
        BlockPos above = this.getPos().above();
        List<EndCrystal> crystals = level.getEntitiesOfClass(EndCrystal.class, new AABB(above),
                crystal -> crystal.isAlive() && crystal.blockPosition().equals(above));
        return !crystals.isEmpty();
    }

    private void checkEnergy() {
        if (this.getOffsetTimer() % 20L == 0L) {
            this.crystalPresent = this.hasCrystalAbove();
            if (this.userId == null) {
                this.tryAutoBindUserId();
            }
        }
        if (!this.crystalPresent) {
            return;
        }
        long generated = generationForTier(this.getTier());
        this.accumulatedEU = saturatingAdd(this.accumulatedEU, generated);
        this.markDirty();

        if (++this.wirelessFlushTicks < WIRELESS_FLUSH_INTERVAL || this.userId == null) {
            return;
        }
        this.wirelessFlushTicks = 0;
        if (this.accumulatedEU > 0L
                && WirelessEnergyManager.addEUToGlobalEnergyMap(this.userId, BigInteger.valueOf(this.accumulatedEU), this)) {
            // 无线 API 返回成功后才清空缓存；失败时保留电量等待下次结算。
            this.accumulatedEU = 0L;
            this.markDirty();
        }
    }

    /** 饱和加法：溢出时钳制到 Long.MAX_VALUE，避免变成负数。 */
    private static long saturatingAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    // ==================== 访问器 ====================

    @Override
    public int getTier() {
        return this.tier;
    }

    public long getAccumulatedEU() {
        return this.accumulatedEU;
    }

    @Nullable
    public UUID getUserId() {
        return this.userId;
    }
}
