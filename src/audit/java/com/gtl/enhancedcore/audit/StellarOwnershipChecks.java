package com.gtl.enhancedcore.audit;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import com.gtl.enhancedcore.common.recipe.GalaxyIndependentRecipeAccess;
import com.gtl.enhancedcore.common.recipe.GalaxyIndependentRecipeEngine;
import com.gtl.enhancedcore.common.recipe.iv.IvNativeAccess;
import com.gtl.enhancedcore.common.recipe.iv.IvMachineScope;
import com.gtl.enhancedcore.common.recipe.iv.IvBuffers;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

/** Focused ownership guard; call from a marked title-screen audit, never a player world. */
public final class StellarOwnershipChecks {
    private StellarOwnershipChecks() {}

    @SuppressWarnings("unchecked")
    public static List<String> run() throws Exception {
        check(Minecraft.getInstance().level == null && Minecraft.getInstance().getSingleplayerServer() == null,
                "Ownership probe requires the title screen");
        var fixture = StellarIvGuiChecks.fixture("gtceu:mage_assembler", false, false, true);
        var machine = fixture.machine();
        try {
            var logic = machine.getRecipeLogic();
            var owner = (GalaxyIndependentRecipeAccess) logic;
            var isolatedParts = new ArrayList<>(machine.getParts());
            check(IvMachineScope.crossRecipeEnabled(machine), "Registered isolated buffer enables real IV mode");
            var ordinaryParts = isolatedParts.stream().filter(part -> !IvBuffers.compatible(part)).toList();
            field(machine, "parts").set(machine, new ArrayList<>(ordinaryParts));
            field(machine, "partPositions").set(machine,
                    ordinaryParts.stream().map(part -> part.self().getPos()).toArray(BlockPos[]::new));
            check(GalaxyIndependentRecipeEngine.supports(logic), "Real registered trait supports stellar work without the buffer");
            var recipe = GTRecipeBuilder.of(new ResourceLocation("gtl_enhancedcore", "stellar_ownership_probe"),
                    machine.getRecipeType()).inputItems(Items.APPLE).outputItems(Items.GOLD_NUGGET)
                    .EUt(0).duration(4).buildRawRecipe();
            var engine = new GalaxyIndependentRecipeEngine(logic);
            var jobType = Class.forName(GalaxyIndependentRecipeEngine.class.getName() + "$Job");
            var constructor = jobType.getDeclaredConstructor(GTRecipe.class, GTRecipe.class, long.class, int.class);
            constructor.setAccessible(true);
            ((List<Object>) field(engine, "jobs").get(engine)).add(constructor.newInstance(recipe, recipe, 1L, 1));
            field(logic, "galaxy$independent").set(logic, engine);
            field(logic, "lastRecipe").set(logic, recipe);
            field(logic, "progress").setInt(logic, 1);
            field(logic, "duration").setInt(logic, 4);
            field(machine, "parts").set(machine, isolatedParts);
            field(machine, "partPositions").set(machine,
                    isolatedParts.stream().map(part -> part.self().getPos()).toArray(BlockPos[]::new));
            check(IvMachineScope.crossRecipeEnabled(machine) && !GalaxyIndependentRecipeEngine.supports(logic),
                    "Reattaching the real isolated buffer disables new stellar admission");
            var mode = field(logic, "galaxy$independent").getDeclaringClass()
                    .getDeclaredMethod("galaxy$independentMode");
            mode.setAccessible(true);
            check((boolean) mode.invoke(logic), "Existing work retains tick ownership after eligibility changes");
            check(owner.galaxy$blockNativeSearch(), "Existing work blocks native search after eligibility changes");
            logic.findAndHandleRecipe();
            check(logic.getLastRecipe() == recipe && logic.getProgress() == 1 && logic.getDuration() == 4,
                    "Native search cannot consume or replace the owned display recipe");
            var access = (IvNativeAccess) logic;
            var callbackRecipe = recipe.copy();
            try (var ignored = access.iv$recipeContext(callbackRecipe, recipe, 2, 4)) {
                check(logic.getLastRecipe() == callbackRecipe && logic.getProgress() == 2,
                        "Owned callbacks see their job context");
            }
            check(logic.getLastRecipe() == recipe && logic.getProgress() == 1,
                    "Owned callback context restores the display recipe");
            logic.resetRecipeLogic();
            logic.inValid();
            check(owner.galaxy$hasIndependentJobs() && logic.getLastRecipe() == recipe,
                    "Native reset and invalidation preserve paid independent work");
            logic.setWorkingEnabled(false);
            check(owner.galaxy$hasIndependentJobs() && (boolean) mode.invoke(logic),
                    "User pause retains the paid work owner");
            logic.setWorkingEnabled(true);
            field(machine, "isFormed").setBoolean(machine, false);
            logic.updateTickSubscription();
            check(owner.galaxy$hasIndependentJobs() && (boolean) mode.invoke(logic),
                    "Structure loss retains ownership while unsubscribed");
            var restored = new GalaxyIndependentRecipeEngine(logic);
            restored.load(engine.save());
            field(logic, "galaxy$independent").set(logic, restored);
            check(owner.galaxy$hasIndependentJobs() && (boolean) mode.invoke(logic)
                            && owner.galaxy$blockNativeSearch(),
                    "Reloaded work retains ownership while new admission is unsupported");
            return List.of("owned work precedes admission support",
                    "native search/reset cannot claim the display", "context and reload retain ownership");
        } finally {
            fixture.holders().forEach(holder -> holder.setLevel(null));
        }
    }

    private static Field field(Object owner, String name) throws Exception {
        for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
            try { var result = type.getDeclaredField(name); result.setAccessible(true); return result; }
            catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
