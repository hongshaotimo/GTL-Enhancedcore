package com.gtl.enhancedcore.common.config;

import com.glodblock.github.extendedae.config.EPPConfig;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.data.machine.InfinitySingularityRecipeLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.HashSet;
import java.util.Set;

/** Reconciles only materials added by this mod; user-supplied ExtendedAE entries remain intact. */
public final class InfinityCellConfigInjector {
    private static final OwnedConfigEntries<Item> ADDED_ITEMS = new OwnedConfigEntries<>();
    private static final OwnedConfigEntries<Fluid> ADDED_FLUIDS = new OwnedConfigEntries<>();

    private InfinityCellConfigInjector() {}

    public static void inject() {
        Set<Item> wantedItems = new HashSet<>();
        Set<Fluid> wantedFluids = new HashSet<>();
        for (var entry : InfinitySingularityRecipeLoader.getMaterials()) {
            ResourceLocation id = new ResourceLocation(entry.id());
            if (entry.fluid()) wantedFluids.add(ForgeRegistries.FLUIDS.getValue(id));
            else wantedItems.add(ForgeRegistries.ITEMS.getValue(id));
        }
        ADDED_ITEMS.reconcile(EPPConfig.infCellItem, wantedItems);
        ADDED_FLUIDS.reconcile(EPPConfig.infCellFluid, wantedFluids);
        GTLEnhancedcore.LOGGER.debug("Reconciled {} item and {} fluid infinity cell materials",
                wantedItems.size(), wantedFluids.size());
    }
}
