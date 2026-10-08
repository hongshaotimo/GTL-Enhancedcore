package com.gtl.enhancedcore.common.machine.hatch;

import com.gregtechceu.gtceu.api.capability.IDataAccessHatch;
import com.gtl.enhancedcore.common.gui.PagedInventoryWidget;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.fancy.FancyMachineUIWidget;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.common.machine.multiblock.part.DataAccessHatchMachine;
import com.gregtechceu.gtceu.common.recipe.condition.ResearchCondition;
import com.gregtechceu.gtceu.utils.ResearchManager;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.mojang.datafixers.util.Pair;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@ParametersAreNonnullByDefault
@Mod.EventBusSubscriber(modid = GTLEnhancedcore.MOD_ID)
public class QuantumDataAccessHatchMachine extends DataAccessHatchMachine {

    private static final int COLS = 9;
    private static final int ROWS = 9;
    private static final int SLOTS_PER_PAGE = COLS * ROWS;
    private static final int MAX_PAGES = 10;
    private static final int SLOT_COUNT = SLOTS_PER_PAGE * MAX_PAGES;

    private final Set<GTRecipe> quantumRecipes;
    private static long recipeGeneration;
    private long cachedGeneration = -1;
    private boolean quantumDataDirty = true;

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) recipeGeneration++;
    }

    public QuantumDataAccessHatchMachine(IMachineBlockEntity holder, int tier, boolean isCreative) {
        super(holder, tier, isCreative);
        this.quantumRecipes = new HashSet<>();
    }

    @Override
    protected int getInventorySize() {
        return SLOT_COUNT;
    }

    @Override
    protected NotifiableItemStackHandler createImportItemHandler() {
        if (this.isCreative()) {
            return new NotifiableItemStackHandler(this, 0, IO.BOTH);
        }
        return new NotifiableItemStackHandler(this, this.getInventorySize(), IO.BOTH) {
            @Override
            public void onContentsChanged() {
                super.onContentsChanged();
                QuantumDataAccessHatchMachine.this.quantumDataDirty = true;
            }

        }.setFilter(QuantumDataAccessHatchMachine::isSupportedDataItem);
    }

    private static boolean isSupportedDataItem(ItemStack stack) {
        // This hatch can read modules directly, including before a controller is formed.
        return ResearchManager.isStackDataItem(stack, true);
    }

    @Override
    public boolean isRecipeAvailable(GTRecipe recipe, Collection<IDataAccessHatch> seen) {
        seen.add(this);
        if (this.isCreative() || recipe.conditions.stream().noneMatch(ResearchCondition.class::isInstance)) return true;
        if (this.quantumDataDirty || this.cachedGeneration != recipeGeneration) rebuildQuantumData();
        return this.quantumRecipes.contains(recipe);
    }

    @Override
    public void addedToController(IMultiController controller) {
        super.addedToController(controller);
        this.quantumDataDirty = true;
    }

    private void rebuildQuantumData() {
        if (this.quantumRecipes == null || this.isCreative() || this.getLevel() == null || this.getLevel().isClientSide()) {
            return;
        }
        this.quantumRecipes.clear();
        for (int i = 0; i < this.importItems.getSlots(); ++i) {
            ItemStack stack = this.importItems.getStackInSlot(i);
            if (!isSupportedDataItem(stack)) continue;
            Pair<GTRecipeType, String> researchData = ResearchManager.readResearchId(stack);
            if (researchData == null || researchData.getFirst() == null) {
                continue;
            }
            Collection<GTRecipe> collection = researchData.getFirst().getDataStickEntry(researchData.getSecond());
            if (collection != null) {
                this.quantumRecipes.addAll(collection);
            }
        }
        this.cachedGeneration = recipeGeneration;
        this.quantumDataDirty = false;
    }

    @Override
    public ModularUI createUI(Player entityPlayer) {
        return new ModularUI(176, 198, this, entityPlayer)
                .widget(new FancyMachineUIWidget(this, 176, 198));
    }

    @Override
    public Widget createUIWidget() {
        return new PagedInventoryWidget(this.importItems, COLS, ROWS,
                () -> "gui.gtl_enhancedcore.quantum_data_access_hatch.title");
    }


}
