package com.gtl.enhancedcore.common.machine;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.pattern.MultiblockWorldSavedData;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gtl.enhancedcore.common.data.GTLEnhancedcoreRecipeTypes;
import com.gtl.enhancedcore.common.recipe.WeatherAnchorPower;

import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockDisplayText;
import net.minecraft.network.chat.Component;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.server.level.ServerLevel;

/**
 * ZPM 时相凝固气象锚点：大气接收盘与时相子午架。
 *
 * 工作方式：
 * - 5 个自定义配方类型（白昼/黑夜/晴天/雨天/雷暴）仅作 GUI 侧栏模式切换器，空配方表。
 * - 选定模式后每 tick 以 ZPM 4A（{@link #ANCHOR_EUT} EU/t）持续耗电，
 *   强制把主世界时间/天气写回目标值，实现"锁定并稳定"；电力不足立即停止锁定，
 *   世界恢复正常流逝。
 * - 切换模式时强制结构失效重新成型（setActiveRecipeType → onStructureInvalid + setError），
 *   与创造能源仓切换电源重置结构同款，防止卡机。
 */
public class WeatherAnchorMachine extends WorkableElectricMultiblockMachine {

    public static final int ANCHOR_TIER = GTValues.ZPM;
    public static final int ANCHOR_AMPS = 4;
    /** ZPM 4A = 524288 EU/t; low-voltage buffered energy cannot bypass the tier. */
    public static final long ANCHOR_EUT = GTValues.V[ANCHOR_TIER] * ANCHOR_AMPS;
    /** 白昼锚定时刻（ticks）。 */
    public static final long DAY_TIME = 1000L;
    /** 黑夜锚定时刻（ticks）。 */
    public static final long NIGHT_TIME = 13000L;

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            WeatherAnchorMachine.class, WorkableElectricMultiblockMachine.MANAGED_FIELD_HOLDER);

    @Nullable
    private TickableSubscription anchorSubs;
    @DescSynced
    private boolean anchoring;

    public WeatherAnchorMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    // ==================== 切换模式强制重新成型 ====================

    @Override
    public void setActiveRecipeType(int activeRecipeType) {
        int previous = this.getActiveRecipeType();
        super.setActiveRecipeType(activeRecipeType);
        // 与 onRotated 同款重成型序列：invalidate + removeMapping + addAsyncLogic。
        // 漏掉 addAsyncLogic 会导致异步重检永不调度（上轮"切换后成型不了"的根因）。
        if (previous != activeRecipeType && this.isFormed()
                && this.getLevel() instanceof ServerLevel serverLevel) {
            this.onStructureInvalid();
            MultiblockWorldSavedData mwsd = MultiblockWorldSavedData.getOrCreate(serverLevel);
            mwsd.removeMapping(this.getMultiblockState());
            mwsd.addAsyncLogic(this);
        }
    }

    // ==================== 锚定逻辑（每 tick） ====================

    @Override
    public void onLoad() {
        super.onLoad();
        if (!this.isRemote()) {
            this.anchorSubs = this.subscribeServerTick(this.anchorSubs, this::anchorTick);
        }
    }

    @Override
    public void onUnload() {
        super.onUnload();
        if (this.anchorSubs != null) {
            this.anchorSubs.unsubscribe();
            this.anchorSubs = null;
        }
    }

    private void anchorTick() {
        this.anchoring = false;
        if (!this.isFormed() || !this.recipeLogic.isWorkingEnabled()) {
            return;
        }
        if (!(this.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!WeatherAnchorPower.tryConsume(this.energyContainer, GTValues.V[ANCHOR_TIER], ANCHOR_EUT)) return;
        applyAnchor(serverLevel.getServer().overworld(), this.getRecipeType());
        this.anchoring = true;
    }

    public boolean isAnchoring() {
        return this.anchoring && this.isFormed() && this.recipeLogic.isWorkingEnabled();
    }

    @Override
    public void addDisplayText(List<Component> textList) {
        MultiblockDisplayText.builder(textList, this.isFormed())
                .setWorkingStatus(this.recipeLogic.isWorkingEnabled(), this.anchoring)
                .addEnergyUsageLine(this.energyContainer)
                .addMachineModeLine(this.getRecipeType())
                .addWorkingStatusLine();
        textList.add(Component.translatable("tooltip.gtl_enhancedcore.weather_anchor.supply"));
        this.getDefinition().getAdditionalDisplay().accept(this, textList);
        this.getParts().forEach(part -> part.addMultiText(textList));
    }

    /** 按当前模式把主世界时间/天气写回目标值。 */
    private static void applyAnchor(ServerLevel overworld, GTRecipeType mode) {
        if (mode == GTLEnhancedcoreRecipeTypes.ANCHOR_DAY) {
            overworld.setDayTime(Math.floorDiv(overworld.getDayTime(), 24000L) * 24000L + DAY_TIME);
        } else if (mode == GTLEnhancedcoreRecipeTypes.ANCHOR_NIGHT) {
            overworld.setDayTime(Math.floorDiv(overworld.getDayTime(), 24000L) * 24000L + NIGHT_TIME);
        } else if (mode == GTLEnhancedcoreRecipeTypes.ANCHOR_CLEAR) {
            overworld.setWeatherParameters(6000, 0, false, false);
        } else if (mode == GTLEnhancedcoreRecipeTypes.ANCHOR_RAIN) {
            overworld.setWeatherParameters(0, 6000, true, false);
        } else if (mode == GTLEnhancedcoreRecipeTypes.ANCHOR_THUNDER) {
            overworld.setWeatherParameters(0, 6000, true, true);
        }
    }
}
