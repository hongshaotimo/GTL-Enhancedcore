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
        return (stack, lines) -> appendDistinct(lines, layout.select(expanded(), hint()));
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

    private static TooltipPolicy.Layout<Component> components(String page, Map<String, Object[]> arguments,
                                                               GTRecipeType[] recipes) {
        var definition = TooltipPolicy.page(page);
        var copiedArguments = new HashMap<String, Object[]>();
        arguments.forEach((key, values) -> copiedArguments.put(key, values.clone()));
        var summary = new ArrayList<>(definition.summary().stream().map(key -> line(key, copiedArguments)).toList());
        var details = definition.details().stream().map(key -> line(key, copiedArguments)).toList();
        var catalog = new ArrayList<Component>();
        if (recipes != null && recipes.length > 0) {
            var names = new ArrayList<Component>();
            for (GTRecipeType recipe : recipes) {
                if (recipe == null || recipe.registryName == null) continue;
                var id = recipe.registryName;
                names.add(Component.translatable(id.getNamespace() + "." + id.getPath()));
            }
            catalog.addAll(recipeRows(names));
            if (page.equals("native_isolation") && names.stream().distinct().count() > 1) {
                summary.addFirst(Component.translatable(TooltipPolicy.CROSS_RECIPE_KEY));
            }
        }
        return new TooltipPolicy.Layout<>(summary, details, catalog);
    }

    private static Component line(String key, Map<String, Object[]> arguments) {
        if (key.equals(TooltipPolicy.CROSS_RECIPE_KEY) || key.equals(TooltipPolicy.SOURCE_KEY)) {
            return Component.translatable(key);
        }
        var component = Component.translatable(key, arguments.getOrDefault(key, new Object[0]));
        if (key.endsWith(".intro") || key.endsWith(".summary") || key.endsWith(".tips") || key.endsWith(".efficient")) {
            return component.withStyle(ChatFormatting.AQUA);
        }
        if (key.endsWith(".limit") || key.endsWith(".muffler") || key.endsWith(".no_nbt")
                || key.endsWith(".iv_break") || key.endsWith(".iv_maintenance_penalty")
                || key.endsWith(".iv_only_super_buffer") || key.endsWith(".iv_native.1")
                || key.endsWith(".no_laser_input") || key.endsWith(".no_parallel_hatch")
                || key.endsWith(".only_laser_hatch") || key.endsWith(".laser_requires_energy_hatch")
                || key.endsWith(".no_maintenance_hatch")
                || key.endsWith(".suprachronal_no_modules") || key.endsWith(".suprachronal_module_disabled")) {
            return component.withStyle(ChatFormatting.YELLOW);
        }
        if (key.endsWith(".supports_laser_hatch") || key.endsWith(".parallel_hatch")) {
            return component.withStyle(ChatFormatting.GREEN);
        }
        return component.withStyle(ChatFormatting.GRAY);
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
            rows.add(Component.translatable(TooltipPolicy.RECIPE_HEADER, names.withStyle(ChatFormatting.GRAY))
                    .withStyle(ChatFormatting.AQUA));
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
            if (key.equals(TooltipPolicy.CROSS_RECIPE_KEY) || key.equals(TooltipPolicy.SOURCE_KEY)) return key;
        }
        return null;
    }
}
