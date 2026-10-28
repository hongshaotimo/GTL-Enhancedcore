package com.gtl.enhancedcore.common.util;

import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;

public final class MachineTooltips {
    private static final Component SOURCE = Component.translatable(TooltipPolicy.SOURCE_KEY);

    private MachineTooltips() {}

    public static BiConsumer<ItemStack, List<Component>> create(String page, GTRecipeType... recipes) {
        return create(page, Map.of(), recipes);
    }

    public static BiConsumer<ItemStack, List<Component>> create(String page, Map<String, Object[]> arguments,
                                                                GTRecipeType... recipes) {
        var layout = components(page, arguments, recipes);
        // 自有机器：署名行永远最后一行，动态彩虹（2026-10-25 用户定稿格式）。
        return (stack, lines) -> {
            var selected = new ArrayList<>(layout.select(expanded(), hint()));
            selected.add(Component.translatable(TooltipPolicy.CREDIT_KEY));
            appendDistinct(lines, selected);
        };
    }

    public static BiConsumer<ItemStack, List<Component>> modify(BiConsumer<ItemStack, List<Component>> original,
                                                                String page, Map<String, Object[]> arguments,
                                                                Set<String> replaced, Set<String> mandatory,
                                                                GTRecipeType... recipes) {
        var layout = components(page, arguments, recipes);
        var summary = new ArrayList<>(layout.summary());
        summary.addFirst(SOURCE);
        var additions = new TooltipPolicy.Layout<>(summary, layout.details(), layout.recipeRows());
        boolean replaceCatalog = !layout.recipeRows().isEmpty() || page.equals("suprachronal_module");
        return (stack, lines) -> {
            var inherited = new ArrayList<Component>();
            if (original != null) original.accept(stack, inherited);
            var retained = inherited.stream().filter(line -> !containsAny(line, replaced)
                    && (!replaceCatalog || !recipeRow(line))).toList();
            var selected = TooltipPolicy.compose(additions, normalizeRecipes(retained), ignored -> false,
                    line -> containsAny(line, mandatory), true, null);
            for (Component line : selected) lines.add(displayCopy(line));
        };
    }

    public static BiConsumer<ItemStack, List<Component>> modify(BiConsumer<ItemStack, List<Component>> original,
                                                                String page, Map<String, Object[]> arguments) {
        return modify(original, page, arguments, TooltipPolicy.replacedKeys(page), TooltipPolicy.mandatoryKeys(page));
    }

    public static BiConsumer<ItemStack, List<Component>> modify(BiConsumer<ItemStack, List<Component>> original, String page) {
        return modify(original, page, Map.of());
    }

    public static BiConsumer<ItemStack, List<Component>> modifyNative(BiConsumer<ItemStack, List<Component>> original,
                                                                      String path) {
        return modifyNative(original, path, new GTRecipeType[0]);
    }

    public static BiConsumer<ItemStack, List<Component>> modifyNative(BiConsumer<ItemStack, List<Component>> original,
                                                                       String path, GTRecipeType... recipes) {
        var actualRecipes = recipes == null ? new GTRecipeType[0] : recipes;
        // 物品层拿不到总成与 Ω 状态；这里只声明超级总成模式的基准线程数。
        // GTLCore MultipleRecipesLogic 原有 64 条与新增 128 条相加，其余目标只有新增 128 条。
        var threadLine = "gtl_enhancedcore.tooltip.iv_native_threads";
        var arguments = Map.of(threadLine,
                new Object[]{com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.baseThreadsFor(path)});
        var combined = original;
        for (String page : TooltipPolicy.nativePages(path)) {
            if (page.equals("native_isolation")) {
                combined = modify(combined, page, arguments, TooltipPolicy.replacedKeys(page),
                        TooltipPolicy.nativeSummaryKeys(path), actualRecipes);
                continue;
            }
            var replaced = TooltipPolicy.replacedKeys(page);
            if (actualRecipes.length == 0) {
                replaced = replaced.stream().filter(key -> !key.equals(TooltipPolicy.RECIPE_HEADER)
                        && !key.equals(TooltipPolicy.RECIPE_GROUP)).collect(Collectors.toUnmodifiableSet());
            }
            combined = modify(combined, page, arguments, replaced, TooltipPolicy.mandatoryKeys(page), actualRecipes);
        }
        return combined;
    }

    public static BiConsumer<ItemStack, List<Component>> modifyWithRecipes(BiConsumer<ItemStack, List<Component>> original,
                                                                           String page, GTRecipeType... recipes) {
        return modify(original, page, Map.of(), TooltipPolicy.replacedKeys(page), TooltipPolicy.mandatoryKeys(page), recipes);
    }

    public static BiConsumer<ItemStack, List<Component>> modifyWithRecipes(BiConsumer<ItemStack, List<Component>> original,
                                                                           String page, Map<String, Object[]> arguments,
                                                                           GTRecipeType... recipes) {
        return modify(original, page, arguments, TooltipPolicy.replacedKeys(page), TooltipPolicy.mandatoryKeys(page), recipes);
    }

    public static boolean containsKey(Component component, String key) {
        return containsMatchingKey(component, key::equals);
    }

    /** Preserve the selected builder's existing rows and place an animated policy row before attribution. */
    public static void appendGalaxyCapability(BiConsumer<ItemStack, List<Component>> original,
                                              ItemStack stack, List<Component> lines,
                                              boolean allowed, boolean engineSupported) {
        appendGalaxyCapability(original, stack, lines, allowed, engineSupported, PlayerTooltipStyles::rainbow);
    }

    public static void appendGalaxyCapability(BiConsumer<ItemStack, List<Component>> original,
                                              ItemStack stack, List<Component> lines,
                                              boolean allowed, boolean engineSupported,
                                              java.util.function.Function<String, Component> animated) {
        if (original != null) original.accept(stack, lines);
        if (allowed) insertBeforeAttribution(lines, TooltipPolicy.GALAXY_ALLOWED_KEY, animated);
        if (engineSupported && lines.stream().anyMatch(line -> containsKey(
                line, "gtl_enhancedcore.tooltip.iv_native_threads")))
            insertBeforeAttribution(lines, "tooltip.gtl_enhancedcore.thread_engine.supported", Component::translatable);
    }

    private static void insertBeforeAttribution(List<Component> lines, String key,
                                               java.util.function.Function<String, Component> render) {
        lines.removeIf(line -> containsKey(line, key));
        int index = lines.size();
        for (int i = 0; i < lines.size(); i++) {
            if (containsKey(lines.get(i), TooltipPolicy.CREDIT_KEY)
                    || containsKey(lines.get(i), TooltipPolicy.SOURCE_KEY)) {
                index = i;
                break;
            }
        }
        lines.add(index, render.apply(key));
    }

    private static TooltipPolicy.Layout<Component> components(String page, Map<String, Object[]> arguments,
                                                               GTRecipeType[] recipes) {
        var definition = TooltipPolicy.page(page);
        var copiedArguments = new HashMap<String, Object[]>();
        arguments.forEach((key, values) -> copiedArguments.put(key, values.clone()));
        var summary = new ArrayList<>(definition.summary().stream().map(key -> line(key, copiedArguments)).toList());
        var details = definition.details().stream().map(key -> line(key, copiedArguments)).toList();
        var catalog = new ArrayList<Component>();
        long distinctTypes = recipes == null ? 0 : java.util.Arrays.stream(recipes)
                .filter(recipe -> recipe != null && recipe.registryName != null)
                .map(recipe -> recipe.registryName).distinct().count();
        if (distinctTypes <= 1) summary.removeIf(row -> containsKey(row, TooltipPolicy.CROSS_RECIPE_KEY));
        if (recipes != null && recipes.length > 0) {
            var names = new ArrayList<Component>();
            for (GTRecipeType recipe : recipes) {
                if (recipe == null || recipe.registryName == null) continue;
                var id = recipe.registryName;
                names.add(Component.translatable(id.getNamespace() + "." + id.getPath()));
            }
            catalog.addAll(recipeRows(names));
            if (page.equals("native_isolation") && distinctTypes > 1
                    && summary.stream().noneMatch(row -> containsKey(row, TooltipPolicy.CROSS_RECIPE_KEY))) {
                summary.addFirst(Component.translatable(TooltipPolicy.CROSS_RECIPE_KEY));
            }
        }
        return new TooltipPolicy.Layout<>(summary, details, catalog);
    }

    /**
     * Renders one declared tooltip row.
     * <p>
     * Colour convention follows GTCEu / GTLAdditions: a row is one plain-colour sentence and any
     * emphasis is carried by <em>inline</em> codes inside the translated string
     * (measured across the reference jars: {@code §7} is the body colour, {@code §f} highlights
     * numbers, {@code §6} highlights proper nouns, {@code §r} resets). The translator therefore owns
     * colour; only a few structural rows get a house colour here.
     */
    private static Component line(String key, Map<String, Object[]> arguments) {
        if (key.equals(TooltipPolicy.CROSS_RECIPE_KEY) || key.equals(TooltipPolicy.STAR_CROSS_RECIPE_KEY)
                || key.equals(TooltipPolicy.SOURCE_KEY) || key.equals(TooltipPolicy.CREDIT_KEY)) {
            return Component.translatable(key);
        }
        Object[] values = arguments.getOrDefault(key, key.endsWith(".shared")
                ? new Object[]{Component.translatable("tooltip.gtl_enhancedcore.adaptive_maintenance_hatch.forbidden")
                .withStyle(ChatFormatting.RED)} : new Object[0]);
        var component = Component.translatable(key, values);
        return component.withStyle(rowColor(key));
    }

    /**
     * 整行底色（2026-10-25 用户定稿，见 docs/TIPS规范.md）：风味句浅紫、用途句深青、其余一律白色。
     * 自适应维护仓的洁净/重力能力行沿用参考仓室的绿色。行内强调只用语言文件里的 §6/§b/§f…§r。
     */
    public static ChatFormatting rowColor(String key) {
        if (key.endsWith(".flavor")) return ChatFormatting.LIGHT_PURPLE;
        if (key.endsWith(".cleanroom_absolute") || key.endsWith(".cleanroom_standard")
                || key.endsWith(".cleanroom_sterile") || key.endsWith(".gravity")) {
            return ChatFormatting.GREEN;
        }
        if (key.startsWith("tooltip.gtl_enhancedcore.adaptive_maintenance_hatch.")) return ChatFormatting.WHITE;
        if (key.equals(TooltipPolicy.JOINT_FACTORY_FLAVOR)) return ChatFormatting.LIGHT_PURPLE;
        if (key.endsWith(".intro") || key.endsWith(".summary") || key.endsWith(".tips") || key.endsWith(".efficient")) {
            return ChatFormatting.DARK_AQUA;
        }
        return ChatFormatting.WHITE;
    }

    private static boolean expanded() {
        return FMLEnvironment.dist == Dist.CLIENT && Screen.hasShiftDown();
    }

    private static Component hint() {
        return Component.translatable(TooltipPolicy.SHIFT_HINT).withStyle(ChatFormatting.DARK_GRAY);
    }

    private static boolean containsAny(Component line, Set<String> keys) {
        return containsMatchingKey(line, keys::contains);
    }

    private static boolean containsMatchingKey(Component component, Predicate<String> match) {
        if (component.getContents() instanceof TranslatableContents translated) {
            if (match.test(translated.getKey())) return true;
            for (Object argument : translated.getArgs()) {
                if (argument instanceof Component nested && containsMatchingKey(nested, match)) return true;
            }
        }
        return component.getSiblings().stream().anyMatch(sibling -> containsMatchingKey(sibling, match));
    }

    private static List<Component> normalizeRecipes(List<Component> inherited) {
        var result = new ArrayList<Component>();
        for (Component line : inherited) {
            if (animatedKey(line) != null) {
                result.add(Component.translatable(animatedKey(line)));
            } else if (line.getContents() instanceof TranslatableContents contents
                    && contents.getKey().startsWith("gtceu.machine.available_recipe_map_")) {
                var recipes = new ArrayList<Component>();
                for (Object argument : contents.getArgs()) {
                    recipes.add(argument instanceof Component component ? component.copy() : Component.literal(String.valueOf(argument)));
                }
                result.addAll(recipeRows(recipes));
            } else {
                result.add(line);
            }
        }
        return result;
    }

    private static List<Component> recipeRows(List<Component> recipes) {
        var rows = new ArrayList<Component>();
        if (recipes.isEmpty()) return rows;
        for (List<Component> group : TooltipPolicy.recipeGroups(recipes)) {
            var names = Component.empty();
            for (int index = 0; index < group.size(); index++) {
                if (index > 0) names.append(Component.translatable(TooltipPolicy.RECIPE_SEPARATOR));
                names.append(group.get(index).copy());
            }
            rows.add(Component.translatable(TooltipPolicy.RECIPE_HEADER, names.withStyle(ChatFormatting.WHITE))
                    .withStyle(ChatFormatting.WHITE));
        }
        return rows;
    }

    private static boolean recipeRow(Component line) {
        return containsMatchingKey(line, key -> key.startsWith("gtceu.machine.available_recipe_map_")
                || key.equals(TooltipPolicy.RECIPE_HEADER) || key.equals(TooltipPolicy.RECIPE_GROUP));
    }

    private static void appendDistinct(List<Component> target, List<Component> selected) {
        for (Component line : selected) {
            String key = animatedKey(line);
            boolean present = key == null ? target.contains(line)
                    : target.stream().anyMatch(existing -> containsKey(existing, key));
            if (!present) target.add(displayCopy(line));
        }
    }

    private static Component displayCopy(Component line) {
        String key = animatedKey(line);
        return key == null ? line.copy() : PlayerTooltipStyles.rainbow(key);
    }

    private static String animatedKey(Component line) {
        if (line.getContents() instanceof TranslatableContents contents) {
            String key = contents.getKey();
            if (TooltipPolicy.isAnimatedKey(key)) return key;
        }
        return null;
    }
}
