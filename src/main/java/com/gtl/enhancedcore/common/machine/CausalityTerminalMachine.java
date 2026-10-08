package com.gtl.enhancedcore.common.machine;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.recipe.MachineRecipeIO;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.gui.fancy.FancyMachineUIWidget;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyUIProvider;
import com.gregtechceu.gtceu.api.gui.fancy.TabsWidget;
import com.gregtechceu.gtceu.api.gui.fancy.TooltipsPanel;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.PhantomSlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;

import net.minecraft.ChatFormatting;
import com.lowdragmc.lowdraglib.misc.ItemStackTransfer;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 因果重构终端：MAX 级悬空太极巨构。
 *
 * 核心规则：
 * - 不走 RecipeMap：内置 GUI 样板编辑器（Phantom 槽手写"泥土→钻石"映射），
 *   机器内部维护"样本映射字典"，checkRecipe 直接对比输入仓物品。
 * - 匹配成功即凭空产出目标物品，跳过一切熔炉/加工台过程。
 * - 运行期间每 tick 强制耗电 {@link #EUT}（V[MAX]×163840A），断供立即取消本次运行。
 * - 配方耗时固定 100 秒（{@link #RUN_TICKS} tick），不受任何加速设备与 GTLCore 耗时配置影响
 *   ——逻辑完全自写，不经过 RecipeLogic/超频管线。
 * - 每次运行消耗样本×1、鱼大×1000。
 * - 每轮固定 0.1% 成功率；成功产出 1 个，失败不产出。
 */
public class CausalityTerminalMachine extends WorkableElectricMultiblockMachine {

    /** 运行功耗：MAX 电压 × 163840A。 */
    public static final long EUT = GTValues.V[GTValues.MAX] * 163840L;
    /** 固定耗时：100 秒 = 2000 tick。 */
    public static final int RUN_TICKS = 20 * 100;
    public static final int FISHBIG_COST = 1000;
    /** 映射行数（编辑器行数 = 字典上限）。 */
    public static final int MAX_MAPPINGS = 6;
    /** "鱼大"物品 ID。 */
    public static final ResourceLocation FISHBIG_ID = new ResourceLocation("expatternprovider", "fishbig");

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            CausalityTerminalMachine.class, WorkableElectricMultiblockMachine.MANAGED_FIELD_HOLDER);

    /** 样板编辑器幽灵槽：2 个一组（输入, 输出），共 MAX_MAPPINGS 组。 */
    @Persisted
    @DescSynced
    private final ItemStackTransfer ghostSlots = new ItemStackTransfer(MAX_MAPPINGS * 2);
    /** 旧版手工镜像，仅用于把已有存档迁移进 ghostSlots；迁移后保持空值。 */
    @Persisted
    private CompoundTag ghostTag = new CompoundTag();
    /** 样本映射字典：输入物品 → 输出物品。由幽灵槽重建，不直接序列化。 */
    private final Map<Item, Item> mappings = new LinkedHashMap<>();
    /** 页面列表和侧栏共用同一个实例，确保点击侧栏后能定位到已注册页面。 */
    private final IFancyUIProvider patternEditorTab = new PatternEditorTab();

    /** 当前运行的目标物品；空堆叠 = 空闲，失败的运行也保留目标直到计时结束。 */
    @Persisted
    private ItemStack pendingOutput = ItemStack.EMPTY;
    /** 开始时抽签并保存结果。旧存档已支付的待产物按原结果完成，不重新抽签。 */
    @Persisted
    private boolean pendingSuccess = true;
    /** 运行进度（0~RUN_TICKS），@DescSynced 同步到客户端供 GUI 显示。 */
    @Persisted
    @DescSynced
    private int progress;

    private final Random random = new Random();
    private boolean runPartsReady;
    @Nullable
    private TickableSubscription runSubs;

    public CausalityTerminalMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    // ==================== 电压（MAX 级，无能源仓 tier 计算走外壳电压） ====================

    @Override
    public long getMaxVoltage() {
        return GTValues.V[GTValues.MAX];
    }

    @Override
    public long getOverclockVoltage() {
        return GTValues.V[GTValues.MAX];
    }

    // ==================== 字典重建（幽灵槽 → 映射） ====================

    private boolean hasGhostSlotData() {
        for (int slot = 0; slot < this.ghostSlots.getSlots(); slot++) {
            if (!this.ghostSlots.getStackInSlot(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void rebuildMappings() {
        this.mappings.clear();
        for (int row = 0; row < MAX_MAPPINGS; row++) {
            ItemStack in = this.ghostSlots.getStackInSlot(row * 2);
            ItemStack out = this.ghostSlots.getStackInSlot(row * 2 + 1);
            if (!in.isEmpty() && !out.isEmpty()) {
                this.mappings.put(in.getItem(), out.getItem());
            }
        }
    }

    /** PhantomSlotWidget 的服务端变更回调：映射立即生效，并标记机器数据需要保存。 */
    private void onGhostSlotsChanged() {
        if (this.isRemote()) {
            return;
        }
        rebuildMappings();
        this.markDirty();
    }

    // ==================== 运行逻辑（每 tick，自写管线，不走 RecipeMap） ====================

    @Override
    public void onLoad() {
        this.runPartsReady = false;
        super.onLoad();
        if (!this.isRemote()) {
            if (!this.ghostTag.isEmpty()) {
                if (!hasGhostSlotData()) {
                    this.ghostSlots.deserializeNBT(this.ghostTag);
                }
                this.ghostTag = new CompoundTag();
                this.markDirty();
            }
            rebuildMappings();
            this.runSubs = this.subscribeServerTick(this.runSubs, this::runTick);
        }
    }

    @Override
    public void onUnload() {
        this.runPartsReady = false;
        super.onUnload();
        if (this.runSubs != null) {
            this.runSubs.unsubscribe();
            this.runSubs = null;
        }
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        this.runPartsReady = true;
    }

    @Override
    public void onPartUnload() {
        this.runPartsReady = false;
        super.onPartUnload();
    }

    @Override
    public void onStructureInvalid() {
        this.runPartsReady = false;
        super.onStructureInvalid();
        if (!this.isRemote()) cancelRun();
    }

    private void runTick() {
        // Loading a controller before its other chunks is not a player cancellation.
        if (!this.isRecipeLogicAvailable()) return;
        // The persisted formed flag can be restored before the energy/IO parts are rebound.
        if (!this.runPartsReady) return;
        if (!this.recipeLogic.isWorkingEnabled()) {
            cancelRun();
            return;
        }
        if (!this.pendingOutput.isEmpty()) {
            if (this.energyContainer == null || this.energyContainer.getEnergyStored() < EUT) {
                cancelRun();
                return;
            }
            this.energyContainer.removeEnergy(EUT);
            this.progress = Math.min(RUN_TICKS, this.progress + 1);
            this.markDirty();
            if (this.progress >= RUN_TICKS) tryFinish();
            return;
        }
        tryStart();
    }

    private void cancelRun() {
        if (this.pendingOutput.isEmpty() && this.progress == 0) return;
        this.pendingOutput = ItemStack.EMPTY;
        this.pendingSuccess = true;
        this.progress = 0;
        this.markDirty();
    }

    /** 合并检查样本与鱼大，材料齐全才扣料；样本也是鱼大时需 1001 个。 */
    private void tryStart() {
        if (this.mappings.isEmpty() || this.energyContainer == null || this.energyContainer.getEnergyStored() < EUT) return;
        Item catalyst = ForgeRegistries.ITEMS.getValue(FISHBIG_ID);
        if (catalyst == null || catalyst == Items.AIR) return;
        List<IRecipeHandler<?>> inputs = itemHandlers(IO.IN);
        GTRecipe context = ioContext();
        for (Map.Entry<Item, Item> mapping : this.mappings.entrySet()) {
            List<ItemStack> required = mapping.getKey() == catalyst
                    ? List.of(new ItemStack(catalyst, FISHBIG_COST + 1))
                    : List.of(new ItemStack(mapping.getKey()), new ItemStack(catalyst, FISHBIG_COST));
            // One combined simulation prevents a sample being consumed without its catalyst.
            if (!MachineRecipeIO.transfer(inputs, IO.IN, context, required, true).isEmpty()) continue;
            if (!MachineRecipeIO.transfer(inputs, IO.IN, context, required, false).isEmpty()) return;
            this.pendingOutput = new ItemStack(mapping.getValue());
            this.pendingSuccess = rollOutputSuccess(this.random);
            this.progress = 0;
            this.markDirty();
            return;
        }
    }

    private GTRecipe ioContext() {
        return this.getRecipeType().recipeBuilder(GTLEnhancedcore.id("causality_transfer")).duration(1).buildRawRecipe();
    }

    /** 运行结束：把输出塞进输出仓；塞不下就等下一 tick（持续耗电）。 */
    private void tryFinish() {
        if (!this.pendingSuccess) {
            cancelRun();
            return;
        }
        List<Ingredient> remainder = MachineRecipeIO.transfer(itemHandlers(IO.OUT), IO.OUT,
                ioContext(), List.of(this.pendingOutput), false);
        int count = MachineRecipeIO.remainingCount(remainder);
        if (count == 0) {
            cancelRun();
        } else if (count != this.pendingOutput.getCount()) {
            // Persist only the uninserted remainder; retrying the original stack duplicated outputs.
            this.pendingOutput.setCount(count);
            this.markDirty();
        }
    }

    private List<IRecipeHandler<?>> itemHandlers(IO io) {
        List<IRecipeHandler<?>> list = new ArrayList<>();
        if (this.getCapabilitiesProxy().contains(io, ItemRecipeCapability.CAP)) {
            List<IRecipeHandler<?>> handlers = this.getCapabilitiesProxy().get(io, ItemRecipeCapability.CAP);
            if (handlers != null) {
                list.addAll(handlers);
            }
        }
        return list;
    }

    private static boolean rollOutputSuccess(Random random) {
        return random.nextInt(1000) == 0;
    }

    // ==================== 显示文本（进度与状态） ====================

    public int getProgressPercent() {
        return (int) Math.min(100L, (long) this.progress * 100 / RUN_TICKS);
    }

    public Component getDiagnostic() {
        if (this.energyContainer == null || this.energyContainer.getEnergyStored() < EUT) {
            return com.gtl.enhancedcore.common.recipe.MachineDiagnostics.text("power");
        }
        if (this.progress >= RUN_TICKS) return com.gtl.enhancedcore.common.recipe.MachineDiagnostics.text("output");
        if (!this.pendingOutput.isEmpty()) return null;
        return com.gtl.enhancedcore.common.recipe.MachineDiagnostics.text(
                this.mappings.isEmpty() ? "mapping" : "sample");
    }

    @Override
    public void addDisplayText(List<Component> textList) {
        if (!this.isFormed()) {
            textList.add(Component.translatable("gui.gtl_enhancedcore.causality_terminal.incomplete")
                    .withStyle(ChatFormatting.RED));
        } else if (!this.recipeLogic.isWorkingEnabled()) {
            textList.add(Component.translatable("gui.gtl_enhancedcore.causality_terminal.disabled")
                    .withStyle(ChatFormatting.RED));
        } else if (this.progress > 0) {
            int percent = getProgressPercent();
            int remainingSeconds = Math.max(0, RUN_TICKS - this.progress) / 20;
            textList.add(Component.translatable("gui.gtl_enhancedcore.causality_terminal.running")
                    .withStyle(ChatFormatting.GREEN));
            textList.add(Component.translatable("gui.gtl_enhancedcore.causality_terminal.progress",
                    Component.literal(percent + "%").withStyle(ChatFormatting.AQUA))
                    .withStyle(ChatFormatting.GRAY));
            textList.add(Component.translatable("gui.gtl_enhancedcore.causality_terminal.remaining",
                    Component.literal(remainingSeconds + "s").withStyle(ChatFormatting.AQUA))
                    .withStyle(ChatFormatting.GRAY));
            textList.add(Component.translatable("gui.gtl_enhancedcore.causality_terminal.power_usage",
                    Component.literal(EUT + " EU/t").withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.GRAY));
        } else {
            textList.add(Component.translatable("gui.gtl_enhancedcore.causality_terminal.idle")
                    .withStyle(ChatFormatting.GRAY));
        }
        this.getDefinition().getAdditionalDisplay().accept(this, textList);
        for (IMultiPart part : this.getParts()) {
            part.addMultiText(textList);
        }
    }

    // ==================== GUI：内置样板编辑器页签 ====================

    /**
     * 因果终端不走 RecipeLogic，只有 1 个空占位配方类型；父类（WorkableMultiblockMachine）
     * 的机器模式/配色子页签对本机无意义，且 GTLCore 的 MachineModeFancyConfiguratorMixin
     * 注入的 "Color" 选项会被标签截断为 "colo"。返回空列表隐藏全部父类子页签。
     */
    @Override
    public List<IFancyUIProvider> getSubTabs() {
        return new ArrayList<>();
    }

    @Override
    public void attachSideTabs(TabsWidget sideTabs) {
        super.attachSideTabs(sideTabs);
        sideTabs.attachSubTab(this.patternEditorTab);
    }

    /** 样板编辑器页签：MAX_MAPPINGS 行"输入 → 输出"幽灵槽，放入即登记映射。 */
    private class PatternEditorTab implements IFancyUIProvider {

        @Override
        public Widget createMainPage(FancyMachineUIWidget widget) {
            WidgetGroup group = new WidgetGroup(0, 0, 190, 126);
            group.addWidget(new LabelWidget(4, 4, "gui.gtl_enhancedcore.causality_terminal.editor_header"));
            for (int row = 0; row < MAX_MAPPINGS; row++) {
                int y = 18 + row * 18;
                group.addWidget(new PhantomSlotWidget(CausalityTerminalMachine.this.ghostSlots, row * 2, 4, y)
                        .setClearSlotOnRightClick(true)
                        .setChangeListener(CausalityTerminalMachine.this::onGhostSlotsChanged));
                group.addWidget(new LabelWidget(26, y + 4,
                        "gui.gtl_enhancedcore.causality_terminal.mapping_arrow"));
                group.addWidget(new PhantomSlotWidget(CausalityTerminalMachine.this.ghostSlots, row * 2 + 1, 42, y)
                        .setClearSlotOnRightClick(true)
                        .setChangeListener(CausalityTerminalMachine.this::onGhostSlotsChanged));
            }
            return group;
        }

        @Override
        public IGuiTexture getTabIcon() {
            return new ItemStackTexture(new ItemStack(Items.DIAMOND));
        }

        @Override
        public Component getTitle() {
            return Component.translatable("gui.gtl_enhancedcore.causality_terminal.editor");
        }

        @Override
        public void attachTooltips(TooltipsPanel tooltipsPanel) {
        }
    }
}
