package com.gtl.enhancedcore.common.machine;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockDisplayText;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.recipe.logic.OCParams;
import com.gregtechceu.gtceu.api.recipe.logic.OCResult;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAEFluidList;
import com.gtl.enhancedcore.common.recipe.MachineDiagnostics;
import com.gtl.enhancedcore.common.recipe.RetryableRecipeLogic;

import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** Steam-powered processing: 64 parallels, 100-bucket buffer, one bucket per successful start. */
public class IndustrialSteamPlatformMachine extends WorkableElectricMultiblockMachine {

    /** matchContext 中外壳等级的键。 */
    public static final String HULL_TIER_KEY = "SHPHullTier";
    /** 内部蒸汽缓存容量：100 桶（mB）。用户 2026-07-28 要求从 100000 桶改为 100 桶。 */
    public static final long STEAM_CAPACITY_MB = 100L * 1000L;
    /** 每次启动配方消耗的蒸汽：1 桶（mB）。 */
    public static final long STEAM_PER_RECIPE_MB = 1000L;
    /** 支持的最高外壳等级。 */
    public static final int MAX_HULL_TIER = GTValues.HV;
    /**
     * 固定并行数（2026-09-01 用户重新调整：并行固定 64，提升机器外壳仅提升电压，不再影响并行）。
     * 历史：2/4/8/16（按外壳递增）→ 64/128/256/512（按外壳递增）→ 固定 64。
     */
    public static final int FIXED_PARALLEL = 64;

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            IndustrialSteamPlatformMachine.class, WorkableElectricMultiblockMachine.MANAGED_FIELD_HOLDER);

    /** 内部蒸汽缓存（mB），跨存档持久化。 */
    @Persisted
    private long steamStored;
    /** 结构成型时从 matchContext 读出的外壳等级；未成型时为 ULV。 */
    private int hullTier = GTValues.ULV;
    /** 最近一次尝试启动配方时蒸汽是否不足（GUI / Jade 显示用）。 */
    private boolean steamInsufficient;
    @Nullable
    private TickableSubscription refillSubs;
    private int refillCounter;
    /** 以内部蒸汽缓存支付配方 EU 的能源处理器（GTL-Extend GeneralPurposeSteamEngine 同款思路）。 */
    @Nullable
    private SteamCacheEnergyHandler steamEnergyHandler;

    public IndustrialSteamPlatformMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    // ==================== 并行公式（唯一实现，注册端与运行端共用） ====================

    public int getCurrentParallel() {
        return FIXED_PARALLEL;
    }

    public int getHullTier() {
        return this.hullTier;
    }

    /**
     * 有效电压等级 = 外壳等级 + 1（HV 封顶）。2026-09-01 用户要求“外壳增加一级”：
     * 装 ULV 外壳得 LV 电压、装 LV 得 MV……装 HV 仍为 HV（已到上限）。
     * 只影响电压/超频与配方 EUt 上限，并行仍按外壳实际等级查 PARALLELS。
     */
    public int getEffectiveTier() {
        return Math.min(MAX_HULL_TIER, this.hullTier + 1);
    }

    public long getSteamStoredMb() {
        return this.steamStored;
    }

    public long getSteamCapacityMb() {
        return STEAM_CAPACITY_MB;
    }

    public boolean isSteamInsufficient() {
        return this.steamInsufficient;
    }

    // ==================== 结构生命周期 ====================

    @Override
    public void onStructureFormed() {
        // 隐藏机制：先读出外壳等级再调 super——super 计算机器 tier 时会调到
        // 被覆写的 getMaxVoltage()，从而把机器电压等级锁定为外壳等级（不上 tooltip）。
        Object tier = this.getMultiblockState().getMatchContext().get(HULL_TIER_KEY);
        if (tier instanceof Number number) {
            this.hullTier = Math.max(GTValues.ULV, Math.min(MAX_HULL_TIER, number.intValue()));
        } else {
            this.hullTier = GTValues.ULV;
        }
        super.onStructureFormed();
        this.steamInsufficient = false;
        this.registerSteamEnergyHandler();
    }

    /**
     * 隐藏机制：机器电压等级 = 结构中外壳等级 + 1（HV 封顶，见 {@link #getEffectiveTier()}）。
     * 父类据此计算 tier / 超频等级，无需能源仓。
     */
    @Override
    public long getMaxVoltage() {
        return GTValues.V[this.getEffectiveTier()];
    }

    /**
     * 隐藏机制配套：超频电压也走外壳等级。父类实现从能源仓读电压，无能源仓时返回 0，
     * 而 GTLCore MultipleRecipesLogic.getRecipe() 遇到 maxEUt ≤ 0 直接放弃找配方——
     * 这正是"完全不运行"的根因，必须覆写。
     */
    @Override
    public long getOverclockVoltage() {
        return GTValues.V[this.getEffectiveTier()];
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        this.hullTier = GTValues.ULV;
        this.steamInsufficient = false;
        if (this.getCapabilitiesProxy().contains(IO.IN, EURecipeCapability.CAP)) {
            List<IRecipeHandler<?>> euHandlers = this.getCapabilitiesProxy().get(IO.IN, EURecipeCapability.CAP);
            if (euHandlers != null && this.steamEnergyHandler != null) {
                euHandlers.remove(this.steamEnergyHandler);
            }
        }
    }

    /**
     * 把蒸汽能源处理器注册进 EU 输入能力表：配方耗电全部由内部蒸汽缓存支付，
     * 不再需要能源仓供电。蒸汽成本仍是每次启动配方扣 1 桶（beforeWorking 扣除），
     * 处理器本身只负责"有蒸汽就放行 EU"，不额外耗汽。
     */
    private void registerSteamEnergyHandler() {
        if (this.steamEnergyHandler == null) {
            this.steamEnergyHandler = new SteamCacheEnergyHandler();
        }
        if (!this.getCapabilitiesProxy().contains(IO.IN, EURecipeCapability.CAP)) {
            this.getCapabilitiesProxy().put(IO.IN, EURecipeCapability.CAP, new ArrayList<>());
        }
        List<IRecipeHandler<?>> euHandlers = this.getCapabilitiesProxy().get(IO.IN, EURecipeCapability.CAP);
        if (euHandlers != null && !euHandlers.contains(this.steamEnergyHandler)) {
            euHandlers.add(this.steamEnergyHandler);
        }
        this.recipeLogic.updateTickSubscription();
    }

    // ==================== 蒸汽补给（每秒从流体输入仓抽蒸汽进内部缓存） ====================

    @Override
    public void onLoad() {
        super.onLoad();
        if (!this.isRemote()) {
            this.refillSubs = this.subscribeServerTick(this.refillSubs, this::refillTick);
        }
    }

    private void refillTick() {
        ++this.refillCounter;
        if (this.refillCounter < 20) {
            return;
        }
        this.refillCounter = 0;
        // 旧存档在容量下调前可能存了超限蒸汽，这里钳制到当前上限。
        if (this.steamStored < 0 || this.steamStored > STEAM_CAPACITY_MB) {
            this.steamStored = Math.max(0, Math.min(STEAM_CAPACITY_MB, this.steamStored));
            this.markDirty();
        }
        if (!this.isFormed() || this.steamStored >= STEAM_CAPACITY_MB) {
            return;
        }
        long drained = this.pullSteamFromHatches(STEAM_CAPACITY_MB - this.steamStored);
        if (drained > 0L && this.steamStored >= STEAM_PER_RECIPE_MB) {
            this.steamInsufficient = false;
        }
    }

    /**
     * 从结构的流体输入仓抽取蒸汽（forge:steam 标签，兼容任意蒸汽变体）到内部缓存。
     * 库存仓按原生槽位实际返回量记账，避免上游配方接口丢失部分抽取的剩余量。
     *
     * @return 实际抽到的 mB 数
     */
    private long pullSteamFromHatches(long wanted) {
        if (wanted <= 0L) {
            return 0L;
        }
        List<IRecipeHandler<?>> inputTanks = new ArrayList<>();
        if (this.getCapabilitiesProxy().contains(IO.IN, FluidRecipeCapability.CAP)) {
            List<IRecipeHandler<?>> handlers = this.getCapabilitiesProxy().get(IO.IN, FluidRecipeCapability.CAP);
            if (handlers != null) {
                inputTanks.addAll(handlers);
            }
        }
        if (this.getCapabilitiesProxy().contains(IO.BOTH, FluidRecipeCapability.CAP)) {
            List<IRecipeHandler<?>> handlers = this.getCapabilitiesProxy().get(IO.BOTH, FluidRecipeCapability.CAP);
            if (handlers != null) {
                inputTanks.addAll(handlers);
            }
        }
        if (inputTanks.isEmpty()) {
            return 0L;
        }
        long room = Math.min(wanted, STEAM_CAPACITY_MB - this.steamStored);
        long drained = 0;
        var seen = Collections.newSetFromMap(new IdentityHashMap<IRecipeHandler<?>, Boolean>());
        var steam = FluidIngredient.of(GTMaterials.Steam.getFluidTag(), 1);
        for (IRecipeHandler<?> handler : inputTanks) {
            if (drained >= room) break;
            if (!seen.add(handler)) continue;
            if (handler instanceof ExportOnlyAEFluidList stocking && stocking.isStocking()) {
                for (var slot : stocking.getInventory()) {
                    if (drained >= room) break;
                    var configured = slot.getConfig();
                    var cached = slot.getStock();
                    // A filter change can precede the next stock snapshot.
                    if (configured == null || cached == null || !configured.what().equals(cached.what())) continue;
                    var available = slot.getFluid();
                    if (available.isEmpty() || !steam.test(available)) continue;
                    // Keep the slot's filter, online/work switch and original AE action source.
                    var request = available.copy(Math.min(room - drained, available.getAmount()));
                    long amount = slot.drain(request, false).getAmount();
                    this.storeSteam(amount);
                    drained += amount;
                }
                continue;
            }
            long request = room - drained;
            var demand = new ArrayList<FluidIngredient>();
            demand.add(FluidIngredient.of(GTMaterials.Steam.getFluidTag(), request));
            @SuppressWarnings("unchecked")
            IRecipeHandler<FluidIngredient> fluidHandler = (IRecipeHandler<FluidIngredient>) handler;
            var left = fluidHandler.handleRecipe(IO.IN, null, demand, null, false);
            long amount = left == null || left.isEmpty() ? request : request - left.getFirst().getAmount();
            amount = Math.max(0, Math.min(request, amount));
            this.storeSteam(amount);
            drained += amount;
        }
        return drained;
    }

    private void storeSteam(long amount) {
        if (amount > 0) {
            this.steamStored += amount;
            this.markDirty();
            this.recipeLogic.updateTickSubscription();
        }
    }

    // ==================== 配方生命周期：启动扣 1 桶蒸汽 ====================

    @Override
    public boolean beforeWorking(@Nullable GTRecipe recipe) {
        if (!super.beforeWorking(recipe)) {
            return false;
        }
        if (this.steamStored < STEAM_PER_RECIPE_MB) {
            this.pullSteamFromHatches(STEAM_CAPACITY_MB - this.steamStored);
        }
        if (this.steamStored < STEAM_PER_RECIPE_MB) {
            this.steamInsufficient = true;
            return false;
        }
        this.steamInsufficient = false;
        this.markDirty();
        return true;
    }

    /**
     * 配方修饰器：单配方按外壳等级并行（用户 2026-07-28 要求删除跨配方线程，
     * 不要 MultipleRecipesLogic 的多配方合并）。并行倍增由 GTLCore RecipeModifierList
     * 统一 applyParallel；配方基础 EUt 超外壳电压时拒绝运行。
     */
    public static GTRecipe recipeModifier(MetaMachine machine, GTRecipe recipe, OCParams params, OCResult result) {
        int hullTier = machine instanceof IndustrialSteamPlatformMachine platform
                ? platform.getHullTier()
                : GTValues.ULV;
        long baseEUt = RecipeHelper.getInputEUt(recipe);
        int effectiveTier = Math.min(MAX_HULL_TIER, Math.max(GTValues.ULV, hullTier) + 1);
        if (baseEUt > GTValues.V[effectiveTier]) {
            return null;
        }
        result.init(baseEUt, recipe.duration, FIXED_PARALLEL, params.getOcAmount());
        return recipe;
    }

    @Override
    public RecipeLogic createRecipeLogic(Object... args) {
        return new SteamRecipeLogic(this);
    }

    @Override
    public boolean keepSubscribing() {
        return true;
    }

    @Override
    public void onUnload() {
        if (this.refillSubs != null) {
            this.refillSubs.unsubscribe();
            this.refillSubs = null;
        }
        super.onUnload();
    }

    private static final class SteamRecipeLogic extends RetryableRecipeLogic {
        private final IndustrialSteamPlatformMachine platform;

        private SteamRecipeLogic(IndustrialSteamPlatformMachine platform) {
            super(platform);
            this.platform = platform;
        }

        @Override
        protected boolean requiresStoredEnergy() {
            return false;
        }

        @Override
        protected Component rejectedModifierReason(GTRecipe recipe) {
            return RecipeHelper.getInputEUt(recipe) > platform.getOverclockVoltage()
                    ? MachineDiagnostics.voltage(platform, recipe)
                    : super.rejectedModifierReason(recipe);
        }

        @Override
        protected Component rejectedStartReason(GTRecipe recipe) {
            return platform.steamStored < STEAM_PER_RECIPE_MB
                    ? Component.translatable("gtl_enhancedcore.diagnostic.steam_detail",
                            platform.steamStored, STEAM_PER_RECIPE_MB)
                    : MachineDiagnostics.text("start_rejected");
        }

        @Override
        public void setupRecipe(GTRecipe recipe) {
            // GTLCore's setupRecipe calls beforeWorking before consuming inputs.
            // Charge only after both admission and input extraction succeeded.
            this.setStatus(Status.IDLE);
            super.setupRecipe(recipe);
            if (this.getStatus() == Status.WORKING && this.getLastRecipe() == recipe) {
                platform.steamStored -= STEAM_PER_RECIPE_MB;
                platform.markDirty();
            }
        }
    }

    // ==================== GUI 显示 ====================

    /**
     * 自建显示文本，不调用父类版本：父类（电力多方块）会显示无意义的 0 EU 能源行。
     * 保留 GTCEu 标准的状态/模式/并行/进度行，追加蒸汽缓存与蒸汽不足警告。
     */
    @Override
    public void addDisplayText(List<Component> textList) {
        MultiblockDisplayText.builder(textList, this.isFormed())
                .setWorkingStatus(this.recipeLogic.isWorkingEnabled(), this.recipeLogic.isActive())
                .addMachineModeLine(this.getRecipeType())
                .addParallelsLine(this.getCurrentParallel())
                .addWorkingStatusLine()
                .addProgressLine(this.recipeLogic.getProgressPercent());
        if (this.isFormed()) {
            textList.add(Component.translatable("gui.gtl_enhancedcore.steam_platform.hull_tier",
                    Component.literal(GTValues.VN[this.hullTier] + " → " + GTValues.VN[this.getEffectiveTier()])
                            .withStyle(ChatFormatting.AQUA))
                    .withStyle(ChatFormatting.GRAY));
            textList.add(Component.translatable("gui.gtl_enhancedcore.steam_platform.steam_stored",
                    Component.literal(String.valueOf(this.steamStored)).withStyle(ChatFormatting.AQUA),
                    Component.literal(String.valueOf(STEAM_CAPACITY_MB)).withStyle(ChatFormatting.DARK_AQUA))
                    .withStyle(ChatFormatting.GRAY));
            if (this.steamInsufficient) {
                textList.add(Component.translatable("gui.gtl_enhancedcore.steam_platform.low_steam")
                        .withStyle(ChatFormatting.RED));
            }
        }
        this.getDefinition().getAdditionalDisplay().accept(this, textList);
        // IDisplayUIMachine 默认实现的等价内联（父类已实现该接口，Java 禁止 X.super 调用）。
        for (IMultiPart part : this.getParts()) {
            part.addMultiText(textList);
        }
    }

    // ==================== 蒸汽支付 EU 的能源处理器 ====================

    /**
     * EURecipeCapability 处理器：配方的 EU 消耗由内部蒸汽缓存担保。
     * 只要缓存里还有至少一次启动所需的蒸汽，所有 EU 需求全额放行（返回 null）；
     * 蒸汽不足时原样返回未满足量，RecipeLogic 据此判定缺能并暂停。
     */
    private class SteamCacheEnergyHandler implements IRecipeHandler<Long> {

        @Override
        @Nullable
        public List<Long> handleRecipeInner(IO io, GTRecipe recipe, List<Long> left, @Nullable String slotName,
                boolean simulate) {
            if (io != IO.IN) {
                return left;
            }
            // Admission and payment are handled once by SteamRecipeLogic.setupRecipe.
            return null;
        }

        @Override
        public List<Object> getContents() {
            return Collections.singletonList(IndustrialSteamPlatformMachine.this.steamStored);
        }

        @Override
        public double getTotalContentAmount() {
            return IndustrialSteamPlatformMachine.this.steamStored;
        }

        @Override
        public RecipeCapability<Long> getCapability() {
            return EURecipeCapability.CAP;
        }
    }
}
