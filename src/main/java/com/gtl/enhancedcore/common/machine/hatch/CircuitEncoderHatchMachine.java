package com.gtl.enhancedcore.common.machine.hatch;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IDistinctPart;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.multiblock.part.TieredIOPartMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableRecipeHandlerTrait;
import com.gregtechceu.gtceu.api.machine.trait.ICapabilityTrait;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.ingredient.IntCircuitIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.SizedIngredient;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.network.C2SCircuitEncoderPacket;
import com.gtl.enhancedcore.network.GTLEnhancedcoreNetworkHandler;
import com.lowdragmc.lowdraglib.LDLib;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 电路编码仓（GTL-Enhancedcore）：
 * 同时选择多个编程电路（0~32，默认全选）虚拟供给多方块机器，电路不消耗。
 * 挂 PartAbility.IMPORT_ITEMS，可装入任何接受物品输入仓的仓室位；
 * 匹配走 gtlcore handle-part 体系（IDistinctPart → getRecipeHandlers → RecipeHandlePart）。
 *
 * getContents() 必须返回 IntCircuitIngredient（不是 ItemStack）：ItemRecipeCapability.compressIngredients
 * 的 ItemStack 分支用 ItemStack.isSameItem 去重（只比物品不比 NBT），多个电路 ItemStack 会被压成 1 个，
 * 导致配方查找树里只留下第一个电路的键（表现为「只能选中一个电路生效」）。
 * IntCircuitIngredient 走 Ingredient 分支，按 IngredientEquality（StrictNBTIngredient.test 比较配置号）区分，
 * 并被 convertToMapIngredient 转成 MapItemStackNBTIngredient（含 NBT 的特殊键），33 个电路各自成键。
 */
@ParametersAreNonnullByDefault
public class CircuitEncoderHatchMachine
        extends TieredIOPartMachine
        implements IDistinctPart {

    public static final int CIRCUIT_COUNT = 33;
    public static final int CIRCUIT_MAX = 32;
    public static final long ALL_CIRCUITS_MASK = (1L << CIRCUIT_COUNT) - 1;

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER =
            new ManagedFieldHolder(CircuitEncoderHatchMachine.class, TieredIOPartMachine.MANAGED_FIELD_HOLDER);

    @Persisted
    @DescSynced
    private long selectedCircuits = ALL_CIRCUITS_MASK;

    private final CircuitEncoderRecipeHandler circuitHandler;

    public CircuitEncoderHatchMachine(IMachineBlockEntity holder) {
        super(holder, 0, IO.IN);
        this.circuitHandler = new CircuitEncoderRecipeHandler(this);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    @Override
    public boolean isDistinct() {
        return false;
    }

    @Override
    public void setDistinct(boolean distinct) {
        // 电路虚拟供给不参与 distinct 拆分
    }

    public boolean isCircuitSelected(int configuration) {
        return configuration >= 0 && configuration <= CIRCUIT_MAX
                && (this.selectedCircuits & (1L << configuration)) != 0L;
    }

    public void toggleCircuit(int configuration) {
        if (configuration < 0 || configuration > CIRCUIT_MAX) {
            return;
        }
        this.selectedCircuits ^= 1L << configuration;
        this.onCircuitSelectionChanged();
    }

    public void setAllCircuits(boolean selected) {
        this.selectedCircuits = selected ? ALL_CIRCUITS_MASK : 0L;
        this.onCircuitSelectionChanged();
    }

    private void onCircuitSelectionChanged() {
        this.markDirty();
        this.circuitHandler.notifyListeners();
        for (IMultiController controller : this.getControllers()) {
            if (controller instanceof IRecipeLogicMachine recipeMachine) {
                recipeMachine.getRecipeLogic().markLastRecipeDirty();
                recipeMachine.getRecipeLogic().updateTickSubscription();
            }
        }
        GTLEnhancedcore.LOGGER.debug("[CircuitEncoder] 选择状态变化: 0x{}", Long.toHexString(this.selectedCircuits));
    }

    @Override
    public Widget createUIWidget() {
        WidgetGroup group = new WidgetGroup(0, 0, 184, 132);
        group.setBackground(GuiTextures.BACKGROUND);
        group.addWidget(new LabelWidget(9, 8,
                Component.translatable("gtl_enhancedcore.circuit_encoder.gui.title").getString()));
        group.addWidget(new ButtonWidget(100, 6, 36, 12,
                new GuiTextureGroup(GuiTextures.BUTTON,
                        new com.lowdragmc.lowdraglib.gui.texture.TextTexture(
                                Component.translatable("gtl_enhancedcore.circuit_encoder.gui.select_all").getString())),
                cd -> sendAction(C2SCircuitEncoderPacket.ACTION_SET_ALL, 1)));
        group.addWidget(new ButtonWidget(138, 6, 36, 12,
                new GuiTextureGroup(GuiTextures.BUTTON,
                        new com.lowdragmc.lowdraglib.gui.texture.TextTexture(
                                Component.translatable("gtl_enhancedcore.circuit_encoder.gui.clear_all").getString())),
                cd -> sendAction(C2SCircuitEncoderPacket.ACTION_SET_ALL, 0)));

        ButtonWidget[] buttons = new ButtonWidget[CIRCUIT_COUNT];
        int idx = 0;
        for (int row = 0; row <= 2; row++) {
            for (int col = 0; col <= 8; col++) {
                final int circuit = idx;
                buttons[circuit] = new ButtonWidget(10 + 18 * col, 48 + 18 * row, 18, 18, IGuiTexture.EMPTY,
                        cd -> sendAction(C2SCircuitEncoderPacket.ACTION_TOGGLE, circuit));
                group.addWidget(buttons[circuit]);
                idx++;
            }
        }
        for (int col = 0; col <= 5; col++) {
            final int circuit = idx;
            buttons[circuit] = new ButtonWidget(10 + 18 * col, 102, 18, 18, IGuiTexture.EMPTY,
                    cd -> sendAction(C2SCircuitEncoderPacket.ACTION_TOGGLE, circuit));
            group.addWidget(buttons[circuit]);
            idx++;
        }

        // 客户端每 tick 按同步的选中状态刷新按钮外观
        group.addWidget(new WidgetGroup(0, 0, 0, 0) {
            @Override
            public void updateScreen() {
                if (LDLib.isRemote() && CircuitEncoderHatchMachine.this.getLevel() != null) {
                    for (int i = 0; i < CIRCUIT_COUNT; i++) {
                        buttons[i].setButtonTexture(circuitButtonTexture(i));
                    }
                }
            }
        });
        return group;
    }

    private IGuiTexture circuitButtonTexture(int configuration) {
        IGuiTexture icon = new ItemStackTexture(IntCircuitBehaviour.stack(configuration)).scale(0.888f);
        if (this.isCircuitSelected(configuration)) {
            return new GuiTextureGroup(GuiTextures.SLOT, icon);
        }
        return new GuiTextureGroup(GuiTextures.SLOT, new ColorRectTexture(0x80000000), icon);
    }

    private void sendAction(int action, int value) {
        if (LDLib.isRemote() && this.getLevel() != null) {
            GTLEnhancedcoreNetworkHandler.CHANNEL.sendToServer(
                    new C2SCircuitEncoderPacket(action, value, this.getPos()));
        }
    }

    /**
     * 虚拟电路供给 handler：匹配/消耗阶段都把「已选中的电路」从待处理列表移除（电路不消耗，仅放行）；
     * 未选中的电路与普通物品一律原样放行，交给其它输入仓处理。
     */
    public static class CircuitEncoderRecipeHandler extends NotifiableRecipeHandlerTrait<Ingredient> implements ICapabilityTrait {

        private final CircuitEncoderHatchMachine machine;

        public CircuitEncoderRecipeHandler(CircuitEncoderHatchMachine machine) {
            super(machine);
            this.machine = machine;
        }

        @Override
        public List<Ingredient> handleRecipeInner(IO io, GTRecipe recipe, List<Ingredient> left,
                                                  @Nullable String slotName, boolean simulate) {
            if (io != IO.IN || left == null || left.isEmpty()) {
                return left;
            }
            Iterator<Ingredient> iterator = left.iterator();
            while (iterator.hasNext()) {
                Ingredient ingredient = iterator.next();
                int configuration = circuitConfigurationOf(ingredient);
                if (configuration >= 0 && this.machine.isCircuitSelected(configuration)) {
                    iterator.remove();
                }
            }
            return left.isEmpty() ? null : left;
        }

        /** 返回该 Ingredient 代表的编程电路配置号；不是编程电路时返回 -1。 */
        private static int circuitConfigurationOf(Ingredient ingredient) {
            Ingredient inner = ingredient instanceof SizedIngredient sized ? sized.getInner() : ingredient;
            ItemStack[] items = inner.getItems();
            if (items.length == 0 || items[0].isEmpty() || !GTItems.INTEGRATED_CIRCUIT.is(items[0].getItem())) {
                return -1;
            }
            return IntCircuitBehaviour.getCircuitConfiguration(items[0]);
        }

        @Override
        public List<Object> getContents() {
            List<Object> contents = new ArrayList<>(CIRCUIT_COUNT);
            for (int i = 0; i <= CIRCUIT_MAX; i++) {
                if (this.machine.isCircuitSelected(i)) {
                    contents.add(IntCircuitIngredient.circuitInput(i));
                }
            }
            return contents;
        }

        @Override
        public int getSize() {
            return CIRCUIT_COUNT;
        }

        @Override
        public double getTotalContentAmount() {
            return Long.bitCount(this.machine.selectedCircuits);
        }

        @Override
        public RecipeCapability<Ingredient> getCapability() {
            return ItemRecipeCapability.CAP;
        }

        @Override
        public IO getHandlerIO() {
            return IO.IN;
        }

        @Override
        public IO getCapabilityIO() {
            return IO.IN;
        }
    }
}
