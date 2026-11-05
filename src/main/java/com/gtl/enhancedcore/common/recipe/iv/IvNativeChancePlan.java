package com.gtl.enhancedcore.common.recipe.iv;

import appeng.api.stacks.*;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtladd.gtladditions.api.recipe.ChanceParallelLogic;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import it.unimi.dsi.fastutil.objects.*;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.function.Predicate;
import org.gtlcore.gtlcore.api.machine.multiblock.ParallelMachine;
import org.gtlcore.gtlcore.api.machine.trait.*;
import org.gtlcore.gtlcore.api.recipe.IGTRecipe;

/** Runs ADD's exact chance-aware admission against one order's projected stock.
 * Native chance caches are copied for planning and only installed at settlement. */
final class IvNativeChancePlan {
    private final Map<RecipeCapability<?>, Object2IntMap<?>> chances = new IdentityHashMap<>();
    private final ParallelMachine holder;

    @SuppressWarnings({"unchecked", "rawtypes"})
    IvNativeChancePlan(WorkableElectricMultiblockMachine machine, Map<AEKey, Long> input) {
        machine.getRecipeLogic().getChanceCaches().forEach((cap, cache) -> {
            Object2IntMap copy = cap.makeChanceCache();
            if (copy != null && cache != null) copy.putAll(cache);
            chances.put(cap, copy);
        });
        var part = new RecipeHandlePart(List.of()) {
            @Override @SuppressWarnings("unchecked")
            public <T extends Predicate<S>, S> Object2LongMap<S> getSelfContent(RecipeCapability<T> cap, boolean confirm) {
                var result = new Object2LongOpenHashMap<S>();
                input.forEach((key, amount) -> {
                    Object value = cap == ItemRecipeCapability.CAP && key instanceof AEItemKey item ? item.toStack(1)
                            : cap == FluidRecipeCapability.CAP && key instanceof AEFluidKey fluid
                                    ? FluidStack.create(fluid.getFluid(), 1, fluid.getTag()) : null;
                    if (value != null) result.put((S)value, amount.longValue());
                });
                return result;
            }
            @Override public <T extends Predicate<S>, S> Object2LongMap<S> getContentWithShared(RecipeCapability<T> cap, boolean confirm) {
                return getSelfContent(cap, confirm);
            }
        };
        holder = (ParallelMachine)Proxy.newProxyInstance(IvNativeChancePlan.class.getClassLoader(),
                new Class<?>[]{ParallelMachine.class, IRecipeCapabilityMachine.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getMaxParallel" -> ((ParallelMachine)machine).getMaxParallel();
                    case "needConfirmMEStock", "emptyRecipeHandlePart", "emptyHandlePart" -> false;
                    case "isDistinct" -> true;
                    case "getActiveRecipeHandle" -> part;
                    case "getSharedRecipeHandlePart" -> null;
                    default -> throw new UnsupportedOperationException("Native chance projection: " + method.getName());
                });
    }

    long maximum(WorkableElectricMultiblockMachine machine, GTRecipe recipe, long limit) {
        return ChanceParallelLogic.INSTANCE.getMaxParallel(holder, recipe, limit, chances,
                recipe.recipeType.getChanceFunction(), IGTRecipe.of(recipe).getEuTier(), machine.getTier());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    void commit(WorkableElectricMultiblockMachine machine) {
        chances.forEach((cap, planned) -> {
            Object2IntMap actual = machine.getRecipeLogic().getChanceCaches().get(cap);
            if (actual != null && planned != null) actual.putAll(planned);
        });
    }

    static boolean guaranteedInputsFit(GTRecipe recipe, Map<AEKey, Long> stock, Map<AEKey, Long> virtual) {
        var required = recipe.copy();
        required.inputs.replaceAll((cap, contents) -> contents.stream()
                .filter(content -> content.chance == 0 || content.chance >= content.maxChance).toList());
        return IvRecipeInputs.plan(required, stock, virtual, 1) != null;
    }
}
