package com.gtl.enhancedcore.common.machine;

import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gtladd.gtladditions.api.machine.logic.GTLAddMultipleWirelessRecipesLogic;
import com.gtladd.gtladditions.api.machine.wireless.GTLAddWirelessWorkableElectricMultipleRecipesMachine;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * 恒星约束聚变堆：取电与并行模型对齐 {@code gtladditions:forge_of_the_antichrist}（用户 2026-10-05 指定），
 * 只取其中三点，其余 forge 特性（连续运行预热、输出倍率、EU 折扣、模块、递归反演）一律不实现：
 * <ul>
 *   <li><b>电网抽电</b>：基类 {@code SelfWirelessNetworkHandler} 直接从放置者所属无线电网扣除 EU，
 *       不需要也不接受能源仓/激光靶仓；用数据棒可重新绑定归属。</li>
 *   <li><b>无限并行</b>：并行上限取 GTLCore {@code getMaxParallel() = Integer.MAX_VALUE}，
 *       实际批量由原料、输出空间与电网电力决定。</li>
 *   <li><b>无限线程</b>：{@link InfiniteThreadLogic} 把多配方逻辑的线程上限重写为 {@code Integer.MAX_VALUE}，
 *       可同时加工多个不同聚变配方，不回落 128 + Ω。</li>
 * </ul>
 * 仓室（用户 2026-10-05 指定）：结构只接受输入/输出仓室，维护仓、并行控制仓、Ω-天球分歧引擎、
 * 能源仓与激光靶仓都无法成型。
 * 因为不再继承 GTCEu {@code FusionReactorMachine}，原生 {@code eu_to_start} 启动热量（预热）不再检查；
 * 单批时长取基类 {@code limitedDuration} 默认值 20 tick（固定 1 秒），界面不提供修改入口。
 */
public class StellarConfinementFusionReactorMachine extends GTLAddWirelessWorkableElectricMultipleRecipesMachine {

    /** 单批固定时长：1 秒 = 20 tick（GTLAdditions limitedDuration 默认值）。 */
    public static final int FIXED_DURATION_TICKS = 20;

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            StellarConfinementFusionReactorMachine.class,
            GTLAddWirelessWorkableElectricMultipleRecipesMachine.getMANAGED_FIELD_HOLDER());

    /** 旧控制器（2.9.2～3.1.0）把放置者存在 {@code userId}。字段名保留，旧存档升级后迁移到基类 {@code uuid}，
     * 否则老机器会失去电网绑定。 */
    @Persisted
    @Nullable
    private UUID userId;

    public StellarConfinementFusionReactorMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
    }

    /** 线程不设上限：重写多配方逻辑的 128 基础值。 */
    @Override
    public RecipeLogic createRecipeLogic(Object... args) {
        return new InfiniteThreadLogic(this);
    }

    @Override
    public GTLAddMultipleWirelessRecipesLogic getRecipeLogic() {
        return (GTLAddMultipleWirelessRecipesLogic) super.getRecipeLogic();
    }

    /**
     * GUI 与物品提示同一口径：直接写“无限”，不再显示基类的
     * {@code gtceu.multiblock.parallel} / {@code gtladditions.multiblock.threads} 数字行。
     */
    @Override
    protected void addParallelDisplay(List<Component> textList) {
        if (!isFormed()) return;
        textList.add(Component.translatable("tooltip.gtl_enhancedcore.stellar_confinement_fusion_reactor.parallel")
                .withStyle(ChatFormatting.GOLD));
    }

    /** 无限跨配方线程：并行上限仍取 GTLCore 的 {@code Integer.MAX_VALUE}，批量由原料/输出/电网电力收敛。 */
    public static final class InfiniteThreadLogic extends GTLAddMultipleWirelessRecipesLogic {
        InfiniteThreadLogic(StellarConfinementFusionReactorMachine machine) {
            super(machine);
        }

        @Override
        public int getMultipleThreads() {
            return Integer.MAX_VALUE;
        }
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!isRemote()) migrateLegacyOwner();
    }

    @Override
    public void onStructureFormed() {
        if (!isRemote()) {
            migrateLegacyOwner();
            setLimitedDuration(FIXED_DURATION_TICKS);
        }
        super.onStructureFormed();
    }

    /** 缺电暂停时不丢已付款进度（沿用本机器原行为；forge 的预热仍未实现）。 */
    public boolean dampingWhenWaiting() {
        return false;
    }

    /** 多配方引擎自身按 limitedDuration 合批，不再叠加时间窗批处理。 */
    public boolean supportsBatchProcessing() {
        return false;
    }

    public boolean canConfigureBatchProcessing() {
        return false;
    }

    public boolean isBatchEnabled() {
        return false;
    }

    /** 固定 20 tick：关闭基类的时长配置器，避免玩家改掉“固定 1 秒”。 */
    @Override
    protected IFancyConfigurator createConfigurators() {
        return null;
    }

    /**
     * 旧存档兼容：先把遗留 {@code userId} 迁移到基类 {@code uuid}；仍无归属时，
     * 沿用既有的“8 格内最近玩家首次绑定”策略。只做服务器侧一次性绑定，不覆盖已有归属。
     */
    private void migrateLegacyOwner() {
        if (getUuid() != null) return;
        if (userId != null) {
            setUuid(userId);
            refreshTier();
            markDirty();
            return;
        }
        if (!(getLevel() instanceof ServerLevel level)) return;
        var pos = getPos();
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 0.5D;
        double z = pos.getZ() + 0.5D;
        var nearest = level.players().stream()
                .filter(player -> player.distanceToSqr(x, y, z) <= 64.0D)
                .min(Comparator.comparingDouble(player -> player.distanceToSqr(x, y, z)));
        nearest.ifPresent(player -> {
            setUuid(player.getUUID());
            refreshTier();
            markDirty();
        });
    }
}
