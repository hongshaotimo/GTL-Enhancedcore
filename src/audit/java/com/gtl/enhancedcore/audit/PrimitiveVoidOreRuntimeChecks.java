package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.recipe.PrimitiveVoidOreRouting;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.gtlcore.gtlcore.api.machine.trait.IRecipeCapabilityMachine;
import org.gtlcore.gtlcore.api.recipe.RecipeRunnerHelper;
import org.gtlcore.gtlcore.common.data.GTLRecipeTypes;
import org.gtlcore.gtlcore.common.machine.multiblock.noenergy.PrimitiveOreMachine;

/** Dedicated fixture only: native registered controller, original pattern, real hatches and recipe IO. */
@Mod("enhancedcore_audit")
@Mod.EventBusSubscriber(modid = "enhancedcore_audit")
public final class PrimitiveVoidOreRuntimeChecks {
    private static final List<String> CHECKS = new ArrayList<>();
    private static final List<Map<String, Object>> CASES = new ArrayList<>();
    private static final List<Fixture> FIXTURES = new ArrayList<>();
    private static final ResourceKey<Level> CUSTOM = ResourceKey.create(Registries.DIMENSION,
            new ResourceLocation("enhancedcore_audit", "void_test"));
    private static List<GTRecipe> recipes;
    private static int ticks;
    private static boolean finished;
    public PrimitiveVoidOreRuntimeChecks() {}

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (!Boolean.getBoolean("gtl.primitiveVoidOre.audit") || finished
                || event.phase != TickEvent.Phase.END || ++ticks < 20) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        Path fixture = Path.of(System.getProperty("gtl.primitiveVoidOre.fixture")).toAbsolutePath().normalize();
        try {
            Path allowed = Path.of("C:/IDEA/GTL-Enhancedcore/_audit/primitive-void-ore-20261107").toAbsolutePath().normalize();
            if (!fixture.getParent().equals(allowed)
                    || !server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().equals(fixture.resolve("audit-world"))
                    || !Files.isRegularFile(fixture.resolve("primitive-void-ore.marker")))
                throw new AssertionError("Refused non-fixture world");
            if (ticks > 1600) throw new AssertionError("Native formation timeout: " + FIXTURES.stream()
                    .map(f -> f.level.dimension() + " formed=" + f.machine.isFormed() + " error="
                            + f.machine.getMultiblockState().error).toList());
            if (FIXTURES.isEmpty()) {
                check(server.isDedicatedServer(), "Real dedicated Forge server");
                recipes = server.getRecipeManager().getAllRecipesFor(GTLRecipeTypes.PRIMITIVE_VOID_ORE_RECIPES);
                inspectRecipes();
                for (ResourceKey<Level> key : List.of(Level.OVERWORLD, Level.NETHER, Level.END, CUSTOM)) {
                    ServerLevel level = server.getLevel(key);
                    check(level != null, "Real server dimension loaded: " + key.location());
                    FIXTURES.add(new Fixture(level));
                }
                return;
            }
            if (FIXTURES.stream().anyMatch(f -> !f.machine.isFormed() || f.machine.getMultiblockState().hasError()
                    || f.machine.getCapabilitiesProxy().isEmpty())) return;
            for (Fixture f : FIXTURES) f.run();
            finish(server, fixture, null);
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(server, fixture, failure);
        }
    }

    private static void inspectRecipes() {
        check(recipes.size() == 3, "Exactly three native primitive void ore recipes replace the circuitless recipe");
        check(GTLRecipeTypes.PRIMITIVE_VOID_ORE_RECIPES.getMaxInputs(ItemRecipeCapability.CAP) == 1,
                "Recipe type exposes one real circuit input slot");
        for (int circuit = 1; circuit <= 3; circuit++) {
            GTRecipe recipe = recipe(circuit);
            check(recipe.id.equals(new ResourceLocation("gtl_enhancedcore", "primitive_void_ore/dimension_" + circuit)),
                    "Stable final route recipe ID: " + recipe.id);
            check(recipe.duration == 200 && recipe.conditions.isEmpty(),
                    "Route " + circuit + " retains 200 ticks and has no location condition");
            var items = recipe.getInputContents(ItemRecipeCapability.CAP);
            check(items.size() == 1 && items.getFirst().chance == 0, "Route " + circuit + " has one non-consumable circuit");
            Ingredient ingredient = (Ingredient) items.getFirst().content;
            for (int candidate = 0; candidate <= 4; candidate++)
                check(ingredient.test(IntCircuitBehaviour.stack(candidate)) == (candidate == circuit),
                        "Route " + circuit + " strictly matches circuit " + candidate);
            var fluids = recipe.getInputContents(FluidRecipeCapability.CAP);
            check(fluids.size() == 1 && fluids.getFirst().content instanceof FluidIngredient fluid
                            && fluid.getAmount() == 1000 && fluid.test(GTMaterials.Steam.getFluid(1000)),
                    "Route " + circuit + " consumes exactly 1000 mB steam");
            check(recipe.tickInputs.isEmpty(), "Route " + circuit + " adds no tick cost");
        }
    }

    private static GTRecipe recipe(int circuit) {
        return recipes.stream().filter(r -> PrimitiveVoidOreRouting.target(r) == circuit).findFirst().orElseThrow();
    }

    private static final class Fixture {
        final ServerLevel level;
        final PrimitiveOreMachine machine;
        final ItemBusPartMachine input;
        final ItemBusPartMachine output;
        final FluidHatchPartMachine steam;

        Fixture(ServerLevel world) {
            level = world;
            MultiblockMachineDefinition definition = (MultiblockMachineDefinition) GTRegistries.MACHINES.get(
                    new ResourceLocation("gtceu", "primitive_void_ore"));
            check(definition != null, "Primitive machine definition registered in " + level.dimension().location());
            var blocks = definition.getMatchingShapes().getFirst().getBlocks();
            check(blocks.length == 3 && blocks[0].length == 3 && blocks[0][0].length == 3,
                    "Original three-by-three-by-three geometry retained in " + level.dimension().location());
            BlockPos origin = new BlockPos(160, 80, 160);
            PrimitiveOreMachine found = null;
            for (int x = 0; x < blocks.length; x++) for (int y = 0; y < blocks[x].length; y++)
                for (int z = 0; z < blocks[x][y].length; z++) {
                    BlockPos pos = origin.offset(x, y, z);
                    level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
                    var info = blocks[x][y][z];
                    level.setBlock(pos, info == null ? Blocks.AIR.defaultBlockState() : info.getBlockState(), 2 | 16);
                    MetaMachine actual = MetaMachine.getMachine(level, pos);
                    if (actual != null && info != null && info.getBlockEntity(pos) instanceof IMachineBlockEntity template)
                        actual.setFrontFacing(template.getMetaMachine().getFrontFacing());
                    if (actual instanceof PrimitiveOreMachine primitive && primitive.getDefinition() == definition) found = primitive;
                }
            if (found == null) throw new AssertionError("No real primitive controller in native shape");
            machine = found; machine.setWorkingEnabled(false);
            check(machine.getPattern().checkPatternAt(machine.getMultiblockState(), true),
                    "Original dirt shape matches before hatch substitution in " + level.dimension().location());
            Map<BlockPos, TraceabilityPredicate> predicates = machine.getMultiblockState().getMatchContext().get("predicates");
            Set<BlockPos> used = new HashSet<>();
            BlockPos in = replace(predicates, used, "gtceu:lv_input_bus");
            BlockPos out = replace(predicates, used, "gtceu:uv_output_bus");
            BlockPos fluid = replace(predicates, used, "gtceu:lv_input_hatch");
            input = (ItemBusPartMachine) MetaMachine.getMachine(level, in);
            output = (ItemBusPartMachine) MetaMachine.getMachine(level, out);
            steam = (FluidHatchPartMachine) MetaMachine.getMachine(level, fluid);
            check(machine.getPattern().checkPatternAt(machine.getMultiblockState(), true),
                    "Unmodified native pattern accepts input, output and steam hatches in " + level.dimension().location());
        }

        private BlockPos replace(Map<BlockPos, TraceabilityPredicate> predicates, Set<BlockPos> used, String id) {
            MachineDefinition part = GTRegistries.MACHINES.get(new ResourceLocation(id));
            if (part == null) throw new AssertionError("Missing fixture part " + id);
            for (var entry : predicates.entrySet()) {
                if (used.contains(entry.getKey())) continue;
                var options = new ArrayList<>(entry.getValue().common); options.addAll(entry.getValue().limited);
                boolean allowed = options.stream().filter(p -> p.candidates != null).flatMap(p -> Arrays.stream(p.candidates.get()))
                        .anyMatch(info -> info.getBlockState().is(part.getBlock()));
                if (!allowed) continue;
                used.add(entry.getKey());
                level.setBlock(entry.getKey(), part.defaultBlockState(), 2 | 16);
                check(true, "Real native candidate " + id + " accepted at " + entry.getKey() + " in " + level.dimension().location());
                return entry.getKey();
            }
            throw new AssertionError("Native pattern has no distinct hatch position accepting " + id);
        }

        void run() throws Exception {
            String where = level.dimension().location().toString();
            check(machine.getParts().contains(input) && machine.getParts().contains(output) && machine.getParts().contains(steam),
                    "Native formation binds all three real hatches in " + where);
            check(machine.getCapabilitiesProxy().get(IO.IN, ItemRecipeCapability.CAP).contains(input.getInventory()),
                    "Input bus inventory bound through native item capability in " + where);
            for (int invalid : List.of(-1, 0, 4)) {
                reset(); input.getInventory().setStackInSlot(0, invalid < 0 ? ItemStack.EMPTY : IntCircuitBehaviour.stack(invalid));
                steam.tank.setFluidInTank(0, GTMaterials.Steam.getFluid(1000)); refresh();
                for (GTRecipe recipe : recipes) check(!RecipeRunnerHelper.matchRecipeInputNocache(machine, recipe),
                        "Real inputs reject circuit " + invalid + " for " + recipe.id + " in " + where);
                machine.setWorkingEnabled(true); machine.getRecipeLogic().findAndHandleRecipe();
                check(machine.getRecipeLogic().getLastRecipe() == null && steam.tank.getFluidInTank(0).getAmount() == 1000
                                && count() == 0, "Circuit " + invalid + " cannot start or consume steam in " + where);
            }
            Method route = Arrays.stream(machine.getClass().getDeclaredMethods())
                    .filter(m -> m.getName().contains("selectedDimension") && m.getParameterCount() == 1).findFirst().orElseThrow();
            route.setAccessible(true);
            reset();
            check(route.invoke(machine, level) == null, "No active recipe has no fallback route in " + where);
            machine.onWorking();
            check(count() == 0, "Direct native callback with no selected route produces no ore in " + where);
            for (int circuit = 1; circuit <= 3; circuit++) {
                reset();
                ItemStack selector = IntCircuitBehaviour.stack(circuit);
                input.getInventory().setStackInSlot(0, selector.copy());
                steam.tank.setFluidInTank(0, GTMaterials.Steam.getFluid(1000)); refresh();
                GTRecipe selected = recipe(circuit);
                for (GTRecipe candidate : recipes) check(RecipeRunnerHelper.matchRecipeInputNocache(machine, candidate)
                                == (candidate == selected), "Real inputs select circuit " + circuit + " against " + candidate.id + " in " + where);
                check(steam.tank.getFluidInTank(0).getAmount() == 1000, "Matching only simulates steam IO in " + where);
                machine.setWorkingEnabled(true);
                check(machine.getRecipeLogic().checkMatchedRecipeAvailable(selected), "Real recipe logic starts circuit " + circuit + " in " + where);
                check(steam.tank.getFluidInTank(0).isEmpty(), "Native setup pays exactly 1000 mB steam for circuit " + circuit + " in " + where);
                check(ItemStack.matches(selector, input.getInventory().getStackInSlot(0)), "Native setup preserves circuit " + circuit + " in " + where);
                ResourceKey<Level> target = switch (circuit) { case 1 -> Level.OVERWORLD; case 2 -> Level.NETHER; default -> Level.END; };
                check(route.invoke(machine, level) == target, "Merged redirect chooses " + target.location() + " from " + where);
                Set<Item> pool = pool(circuit);
                Set<String> seen = new HashSet<>();
                for (int tick = 0; tick < 64; tick++) {
                    clearOutput();
                    machine.getRecipeLogic().handleRecipeWorking();
                    check(count() == 64, "One native working tick outputs exactly 64 raw ore: " + where + " circuit=" + circuit + " tick=" + tick);
                    for (int slot = 0; slot < output.getInventory().getSlots(); slot++) {
                        ItemStack stack = output.getInventory().getStackInSlot(slot);
                        if (stack.isEmpty()) continue;
                        check(pool.contains(stack.getItem()), "Output belongs to original target ore pool: " + where + " circuit=" + circuit);
                        seen.add(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()).toString());
                    }
                }
                check(machine.getRecipeLogic().getProgress() == 64, "Native progress advances once per working tick in " + where + " circuit=" + circuit);
                check(ItemStack.matches(selector, input.getInventory().getStackInSlot(0)), "64 working ticks preserve selector circuit " + circuit + " in " + where);
                CASES.add(Map.of("actualDimension", where, "circuit", circuit, "targetDimension", target.location().toString(),
                        "workingTicks", 64, "rawOrePerTick", 64, "nativePoolSize", pool.size(), "observedItems", seen));
            }
            reset();
        }

        private Set<Item> pool(int circuit) throws Exception {
            Field field = PrimitiveOreMachine.class.getDeclaredField(switch (circuit) {
                case 1 -> "OVERWORLD_ORE"; case 2 -> "NETHER_ORE"; default -> "END_ORE"; });
            field.setAccessible(true);
            @SuppressWarnings("unchecked") List<Material> materials = (List<Material>) field.get(machine);
            check(materials.size() == switch (circuit) { case 1 -> 67; case 2 -> 35; default -> 51; },
                    "Original native ore pool size retained for circuit " + circuit);
            Set<Item> result = new HashSet<>();
            for (Material material : materials) result.add(ChemicalHelper.get(TagPrefix.rawOre, material, 64).getItem());
            return result;
        }
        private void refresh() { ((IRecipeCapabilityMachine) machine).upDate(); }
        private void reset() {
            machine.setWorkingEnabled(false); machine.getRecipeLogic().resetRecipeLogic();
            for (int slot = 0; slot < input.getInventory().getSlots(); slot++) input.getInventory().setStackInSlot(slot, ItemStack.EMPTY);
            clearOutput(); refresh();
        }
        private int count() { int result = 0; for (int slot = 0; slot < output.getInventory().getSlots(); slot++)
            result += output.getInventory().getStackInSlot(slot).getCount(); return result; }
        private void clearOutput() { for (int slot = 0; slot < output.getInventory().getSlots(); slot++)
            output.getInventory().setStackInSlot(slot, ItemStack.EMPTY); }
    }

    private static void check(boolean value, String description) {
        if (!value) throw new AssertionError(description);
        CHECKS.add(description);
    }
    private static void finish(MinecraftServer server, Path fixture, Throwable failure) {
        finished = true;
        for (Fixture f : FIXTURES) f.machine.setWorkingEnabled(false);
        var report = new LinkedHashMap<String, Object>();
        report.put("status", failure == null ? "PASS" : "FAIL"); report.put("assertions", CHECKS.size());
        report.put("checks", CHECKS); report.put("cases", CASES); report.put("serverTicks", ticks);
        if (failure != null) report.put("failure", failure.toString());
        report.put("boundary", "Fresh dedicated Forge world; original registered 3x3x3 pattern with real input/output/steam hatches and native formation; three real loaded recipes and real IO payments. 64 direct native RecipeLogic.handleRecipeWorking calls per route per actual world. Custom dimension supplied only by fixture datapack. No player save, socket client, restart, or GUI rendering tested.");
        try { Files.writeString(fixture.resolve("report.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
        catch (Exception writeFailure) { throw new RuntimeException(writeFailure); }
        GTLEnhancedcore.LOGGER.info("[PRIMITIVE_VOID_ORE] {} assertions={} cases={}", report.get("status"), CHECKS.size(), CASES.size());
        server.halt(false);
    }
}
