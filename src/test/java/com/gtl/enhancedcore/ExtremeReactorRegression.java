package com.gtl.enhancedcore;

import com.google.gson.JsonParser;
import com.gtl.enhancedcore.common.recipe.EquipmentRecipeCatalog;
import com.gtl.enhancedcore.common.recipe.ExtremeReactorFuel;
import com.gtl.enhancedcore.common.recipe.ExtremeReactorValues;
import com.gtl.enhancedcore.common.recipe.WirelessGenerationBuffer;
import com.gtl.enhancedcore.common.structure.StructureData;
import com.gtl.enhancedcore.common.util.TooltipPolicy;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/** Generation accounting, supplied geometry and UXV recipe/port contracts. No Minecraft registry is booted here. */
public final class ExtremeReactorRegression {
    private static final Path JAVA = Path.of("src/main/java/com/gtl/enhancedcore");
    private static int assertions;

    private ExtremeReactorRegression() {}

    public static int run() throws Exception {
        assertions = 0;
        fuelCycle();
        failedDeposits();
        persistenceAndOverflow();
        geometry();
        portsAndRecipes();
        textureStates();
        return assertions;
    }

    private static void fuelCycle() {
        check(ExtremeReactorFuel.GENERATION_EUT == 500_000_000_000_000L,
                "Output is the requested 100x 5E12 EU/t");
        check(ExtremeReactorFuel.FLUID_ID.equals("gtceu:concentration_mixing_hyper_fuel_2")
                        && ExtremeReactorFuel.AMOUNT_MB == 1 && ExtremeReactorFuel.DURATION_TICKS == 200,
                "Fuel units and burn duration follow the advanced hyper reactor");
        var buffer = new WirelessGenerationBuffer();
        var deposits = new ArrayList<BigInteger>();
        var output = List.of(ExtremeReactorFuel.GENERATION_EUT);
        for (int tick = 1; tick <= ExtremeReactorFuel.DURATION_TICKS; tick++) {
            String before = buffer.save();
            check(buffer.accept(output, true) && buffer.save().equals(before),
                    "Recipe matching never generates EU");
            check(buffer.accept(output, false), "A committed generator tick is accepted");
            if (tick % ExtremeReactorFuel.GRID_FLUSH_INTERVAL == 0) {
                check(buffer.flush(amount -> deposits.add(amount)), "Each full second settles once");
            }
        }
        check(deposits.size() == 10 && deposits.stream().allMatch(amount ->
                        amount.equals(BigInteger.valueOf(10_000_000_000_000_000L))),
                "Twenty committed ticks generate exactly 10 quadrillion EU per deposit");
        check(deposits.stream().reduce(BigInteger.ZERO, BigInteger::add)
                        .equals(new BigInteger("100000000000000000")) && buffer.pendingEU().signum() == 0,
                "A complete 1 mB burn produces 100 quadrillion EU without leftover credit");
    }

    private static void failedDeposits() {
        var buffer = new WirelessGenerationBuffer();
        buffer.accept(List.of(100_000_000_000_000L), false);
        String owed = buffer.save();
        for (int attempt = 0; attempt < 4; attempt++) {
            check(!buffer.flush(amount -> false) && buffer.save().equals(owed),
                    "Grid failure neither clears nor increases the generated balance");
        }
        try {
            buffer.flush(amount -> { throw new IllegalStateException("Injected grid failure"); });
            throw new AssertionError("Expected the injected failure");
        } catch (IllegalStateException expected) {
            check(buffer.save().equals(owed), "An exception before successful credit preserves the pending amount");
        }
        var deposits = new ArrayList<BigInteger>();
        check(buffer.flush(deposits::add) && buffer.pendingEU().signum() == 0,
                "Recovery deposits the retained balance");
        check(!buffer.flush(deposits::add) && deposits.size() == 1,
                "Repeated settlement cannot credit a cleared balance again");
        var restored = new WirelessGenerationBuffer();
        restored.load(buffer.save());
        check(!restored.flush(deposits::add) && deposits.size() == 1,
                "A saved successful settlement stays cleared after reload");
        buffer.accept(List.of(10L), false);
        check(buffer.flush(amount -> {
            buffer.accept(List.of(7L), false);
            return true;
        }) && buffer.pendingEU().equals(BigInteger.valueOf(7)),
                "A settlement only removes the amount accepted by that deposit");
    }

    private static void persistenceAndOverflow() {
        var uninterrupted = new WirelessGenerationBuffer();
        var interrupted = new WirelessGenerationBuffer();
        var output = List.of(ExtremeReactorFuel.GENERATION_EUT);
        for (int tick = 0; tick < 200; tick++) uninterrupted.accept(output, false);
        for (int tick = 0; tick < 87; tick++) interrupted.accept(output, false);
        var restored = new WirelessGenerationBuffer();
        restored.load(interrupted.save());
        for (int tick = 87; tick < 200; tick++) restored.accept(output, false);
        check(restored.pendingEU().equals(uninterrupted.pendingEU()),
                "Reloading a partial balance neither loses nor duplicates generated EU");
        check(!restored.accept(Arrays.asList(7L, -1L), false)
                        && restored.pendingEU().equals(uninterrupted.pendingEU()),
                "A malformed request is rejected before any partial credit");
        check(!restored.accept(Arrays.asList(7L, null), false), "Null outputs cannot become grid credit");
        var huge = new WirelessGenerationBuffer();
        check(huge.accept(List.of(Long.MAX_VALUE, Long.MAX_VALUE), true) && huge.pendingEU().signum() == 0,
                "Simulation of a total larger than long is still read-only");
        huge.accept(List.of(Long.MAX_VALUE, Long.MAX_VALUE), false);
        BigInteger expected = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO);
        check(huge.pendingEU().equals(expected), "Accumulated generation cannot overflow a long");
        var hugeRestored = new WirelessGenerationBuffer();
        hugeRestored.load(huge.save());
        check(hugeRestored.pendingEU().equals(expected), "NBT decimal representation retains arbitrary precision");
        for (String invalid : List.of("-1", "not-a-number")) {
            try {
                hugeRestored.load(invalid);
                throw new AssertionError("Expected invalid energy to be rejected");
            } catch (IllegalArgumentException expectedFailure) {
                check(hugeRestored.pendingEU().equals(expected), "Invalid serialized energy cannot replace a valid balance");
            }
        }
        hugeRestored.load("");
        check(hugeRestored.pendingEU().signum() == 0, "A new controller begins with no generated energy");
    }

    private static void geometry() throws Exception {
        var original = Path.of("SchemTool/input/extreme_reactor.schem");
        check(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(original)))
                        .equals("29710b33069444515934ccbd890991cca84076f2e99226ce38547b0c4e653c40"),
                "The supplied raw schematic remains unchanged");
        var aisles = new ArrayList<String[]>();
        try (var input = Files.newInputStream(Path.of(
                "src/main/resources/data/gtl_enhancedcore/structures/extreme_reactor.pattern.gz"))) {
            StructureData.read(input).forEachAisle(aisles::add);
        }
        check(aisles.size() == 41 && aisles.stream().allMatch(rows -> rows.length == 41
                        && Arrays.stream(rows).allMatch(row -> row.length() == 41)),
                "All three dimensions are exactly 41");
        var counts = new HashMap<Character, Integer>();
        for (int z = 0; z < 41; z++) for (int y = 0; y < 41; y++) {
            var row = aisles.get(z)[y];
            for (int x = 0; x < 41; x++) {
                char character = row.charAt(x);
                counts.merge(character, 1, Integer::sum);
                if (character == 'C') check(x == 20 && y == 20 && z == 40,
                        "The MIN_Z controller keeps its original vertical position");
                if (character == 'X') check(z == 40 && x >= 18 && x <= 22 && y >= 18 && y <= 22,
                        "Only the marked 5x5 front panel accepts hatches");
            }
        }
        check(counts.equals(Map.ofEntries(
                Map.entry(' ', 52597), Map.entry('A', 2420), Map.entry('B', 8362), Map.entry('C', 1),
                Map.entry('D', 1684), Map.entry('E', 1127), Map.entry('F', 718), Map.entry('G', 1093),
                Map.entry('I', 124), Map.entry('J', 667), Map.entry('K', 46), Map.entry('L', 42),
                Map.entry('N', 16), Map.entry('X', 24))),
                "No supplied material, lamp or port is omitted or invented");
        for (int y = 18; y <= 22; y++) for (int x = 18; x <= 22; x++) {
            check(aisles.get(40)[y].charAt(x) == (x == 20 && y == 20 ? 'C' : 'X'),
                    "The entire 5x5 controller panel is present");
        }
    }

    private static void portsAndRecipes() throws Exception {
        var structure = source("common/structure/ExtremeReactorStructure.java");
        var abilities = Pattern.compile("PartAbility\\.([A-Z_]+)").matcher(structure).results()
                .map(match -> match.group(1)).collect(java.util.stream.Collectors.toSet());
        check(abilities.equals(Set.of("MAINTENANCE", "IMPORT_ITEMS", "IMPORT_FLUIDS")),
                "The structure only accepts maintenance and input abilities");
        check(structure.contains(".where('X', block(\"gtlcore:supercritical_turbine_casing\")")
                        && structure.contains("PartAbility.MAINTENANCE).setMaxGlobalLimited(1)")
                        && structure.contains("PartAbility.IMPORT_FLUIDS).setMinGlobalLimited(1)"),
                "Empty ports use the requested casing, with at most one maintenance hatch and a required fuel input");
        check(structure.contains("LampPlacement.invertedStructureLamp")
                        && structure.contains(".where(' ', Predicates.any())"),
                "Designed lamp candidates and unconstrained blueprint air follow project conventions");
        var registration = source("common/registration/SpecialMachineRegistration.java");
        int from = registration.indexOf("public static MultiblockMachineDefinition registerExtremeReactor(");
        var definition = registration.substring(from, registration.indexOf(
                "public static MultiblockMachineDefinition", from + 1));
        check(definition.contains("ExtremeReactorMachine::new") && definition.contains(".generator(true)")
                        && definition.contains("GTLEnhancedcoreRecipeTypes.EXTREME_REACTION")
                        && definition.contains("recipe, false)")
                        && definition.contains("block/supercritical_turbine_casing")
                        && definition.contains("block/multiblock/extreme_reactor"),
                "The generator uses its dedicated type and texture without overclock or parallel modifiers");
        var loader = source("common/data/machine/ExtremeReactionRecipeLoader.java");
        // The loader and runtime share the same fixed fuel amount, duration and EU output.
        check(loader.contains(".EUt(-ExtremeReactorValues.generationEut())")
                        && loader.contains(".duration(ExtremeReactorValues.durationTicks())")
                        && loader.contains("FluidIngredient.of(ExtremeReactorValues.fuelAmountMb(), fuel)")
                        && source("GTLEnhancedcoreGTAddon.java").contains("ExtremeReactionRecipeLoader.registerRecipes(provider)"),
                "The normal dynamic data-pack callback registers fuel consumption and EU output");
        var values = source("common/recipe/ExtremeReactorValues.java");
        check(values.contains("return ExtremeReactorFuel.GENERATION_EUT;")
                        && values.contains("return ExtremeReactorFuel.DURATION_TICKS;")
                        && values.contains("return ExtremeReactorFuel.AMOUNT_MB;")
                        && values.contains("return ExtremeReactorFuel.GRID_FLUSH_INTERVAL;"),
                "Extreme reactor recipe values use the fixed fuel-cycle constants");
        check(ExtremeReactorValues.generationEut() == ExtremeReactorFuel.GENERATION_EUT
                        && ExtremeReactorValues.fuelAmountMb() == ExtremeReactorFuel.AMOUNT_MB
                        && ExtremeReactorValues.durationTicks() == ExtremeReactorFuel.DURATION_TICKS
                        && ExtremeReactorValues.gridFlushInterval() == ExtremeReactorFuel.GRID_FLUSH_INTERVAL,
                "Recipe generation and runtime settlement share the same fixed values");
        var types = source("common/data/GTLEnhancedcoreRecipeTypes.java");
        var type = types.substring(types.indexOf("GTRecipeType EXTREME_REACTION"), types.indexOf("private GTLEnhancedcoreRecipeTypes()"));
        check(type.contains("register(\"extreme_reaction\")") && type.contains(".setMaxIOSize(0, 0, 1, 0)")
                        && type.contains(".setEUIO(IO.OUT)"), "JEI treats energy as generation, never consumption");
        var machine = source("common/machine/ExtremeReactorMachine.java");
        check(machine.contains("new GridOutputHandler(this)")
                        && machine.contains("WirelessEnergyManager.addEUToGlobalEnergyMap(userId, amount, this)")
                        && machine.contains("io != IO.OUT || reactor.isRemote() || reactor.userId == null")
                        && machine.contains("generatedEnergy.accept(left, simulate)"),
                "Only server-side committed EU outputs from a bound reactor enter the grid ledger");
        check(machine.contains("generatedEnergy.save()") && machine.contains("generatedEnergy.load(tag.getString(PENDING_EU_KEY))")
                        && machine.contains("WorkableElectricMultiblockMachine.MANAGED_FIELD_HOLDER"),
                "The generated balance and inherited paid recipe progress have separate persistence");
        check(Pattern.compile("@DescSynced\\s+private long generationEUt").matcher(machine).find()
                        && Pattern.compile("@Persisted\\s+@DescSynced\\s+@Nullable\\s+private UUID userId").matcher(machine).find()
                        && !machine.contains("getLastRecipe()"),
                "Client displays use synchronized generation and grid ownership without a server-only recipe object");
        check(machine.contains("supportsBatchProcessing() { return false; }")
                        && machine.contains("canConfigureBatchProcessing() { return false; }")
                        && machine.contains("isBatchEnabled() { return false; }"),
                "Time-window batching cannot multiply fuel consumption or the fixed output");
        try (var reader = Files.newBufferedReader(Path.of("src/main/resources/data/gtl_enhancedcore/equipment_recipes.json"))) {
            var cost = EquipmentRecipeCatalog.read(reader).stream()
                    .filter(recipe -> recipe.output().equals("extreme_reactor")).findFirst().orElseThrow();
            check(cost.tier() == EquipmentRecipeCatalog.TIERS.indexOf("uxv") && cost.method().equals("assembly_line")
                            && cost.duration() == 9600, "The controller has a UXV assembly-line recipe");
            check(cost.ingredients().stream().anyMatch(item -> item.id().equals("gtceu:advanced_hyper_reactor") && item.count() == 4)
                            && cost.ingredients().stream().anyMatch(item -> item.tag()
                            && item.id().equals("gtceu:circuits/uxv") && item.count() == 16),
                    "Acquisition requires the original reactor and UXV circuits");
        }
        var tip = TooltipPolicy.page("extreme_reactor");
        check(tip.select(false, TooltipPolicy.SHIFT_HINT).containsAll(List.of(
                        "tooltip.gtl_enhancedcore.extreme_reactor.generation", "tooltip.gtl_enhancedcore.extreme_reactor.fuel",
                        "tooltip.gtl_enhancedcore.extreme_reactor.wireless", "tooltip.gtl_enhancedcore.extreme_reactor.hatches")),
                "Generation, fuel and hatch restrictions are visible without Shift");
    }

    private static void textureStates() throws Exception {
        var folder = Path.of("src/main/resources/assets/gtl_enhancedcore/textures/block/multiblock/extreme_reactor");
        var idle = ImageIO.read(folder.resolve("overlay_front.png").toFile());
        var active = ImageIO.read(folder.resolve("overlay_front_active.png").toFile());
        check(idle.getWidth() == 16 && idle.getHeight() == 16 && active.getWidth() == 16 && active.getHeight() == 64,
                "GT pixel scale is 16x16 with four complete active frames");
        check(idle.getRGB(7, 7) != active.getRGB(7, 7), "The reactor core visibly changes while working");
        for (int frame = 0; frame < 4; frame++) for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            if (x <= 2 || x >= 13 || y <= 2 || y >= 13) {
                check(idle.getRGB(x, y) == active.getRGB(x, y + 16 * frame), "Active states preserve the GT metal rim");
            }
        }
        for (var name : List.of("overlay_front_active", "overlay_front_active_emissive")) {
            var image = ImageIO.read(folder.resolve(name + ".png").toFile());
            var animation = JsonParser.parseString(Files.readString(folder.resolve(name + ".png.mcmeta")))
                    .getAsJsonObject().getAsJsonObject("animation");
            check(image.getWidth() == 16 && image.getHeight() == 64 && animation.get("frametime").getAsInt() == 3
                            && !animation.get("interpolate").getAsBoolean(),
                    "Active color and emissive frames advance together without smoothing");
        }
        for (var name : List.of("overlay_front_emissive", "overlay_front_paused", "overlay_front_paused_emissive")) {
            var image = ImageIO.read(folder.resolve(name + ".png").toFile());
            check(image.getWidth() == 16 && image.getHeight() == 16, "Idle and paused overlays are complete 16px tiles");
        }
    }

    private static String source(String relative) throws Exception {
        return Files.readString(JAVA.resolve(relative));
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
