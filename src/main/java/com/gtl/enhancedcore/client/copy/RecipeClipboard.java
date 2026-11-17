package com.gtl.enhancedcore.client.copy;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.client.config.CopyKeysConfig;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * 把一条配方转成可复制的 JSON 文本。
 *
 * <p>与原 mod（JEI-Copy-Recipe-Json）的区别：原 mod 只从数据包资源管理器读
 * {@code recipes/<id>.json}，对 GTCEu 这类**代码生成**的配方读不到会静默失败。
 * 这里先读数据包原始文件（保留作者手写的完整字段），读不到再按配方对象**实时生成**，
 * 因此任何配方都能复制。
 */
public final class RecipeClipboard {
    private static final com.google.gson.Gson PRETTY =
            new GsonBuilder().setPrettyPrinting().serializeNulls().create();

    private RecipeClipboard() {}

    /**
     * @return 可直接写入剪贴板的 JSON 文本；无法处理时返回 null。
     */
    @Nullable
    public static String toJson(@Nullable MinecraftServer server, ResourceLocation id) {
        return toJson(server, id, null);
    }

    @Nullable
    public static String toJson(@Nullable MinecraftServer server, ResourceLocation id,
                                @Nullable Recipe<?> displayedRecipe) {
        if (id == null) return null;
        // 1) 数据包原始 JSON 优先：保留作者写的全部字段与书写风格。
        if (server != null) {
            var raw = datapackJson(server, id);
            if (raw != null) return raw;
        }
        // 2) 兜底：从注册表里的配方对象实时生成。
        if (!CopyKeysConfig.liveGenerateRecipe()) return null;
        Recipe<?> recipe = displayedRecipe;
        if (recipe == null && server != null) recipe = server.getRecipeManager().byKey(id).orElse(null);
        return recipe == null ? null : liveJson(recipe, id);
    }

    /** 从数据包读取 recipes/&lt;namespace&gt;/&lt;path&gt;.json 并压成单行。 */
    @Nullable
    private static String datapackJson(MinecraftServer server, ResourceLocation id) {
        try {
            var resource = server.getResourceManager().getResource(
                    new ResourceLocation(id.getNamespace(), "recipes/" + id.getPath() + ".json"));
            if (resource.isEmpty()) return null;
            try (var reader = resource.get().openAsReader()) {
                var element = JsonParser.parseReader(reader);
                return PRETTY.toJson(element);
            }
        } catch (Exception failure) {
            GTLEnhancedcore.LOGGER.debug("Datapack recipe JSON unavailable for {}", id, failure);
            return null;
        }
    }

    /** 用注册表里的配方对象生成 JSON；GT 配方额外输出输入/输出/时长等可读字段。 */
    @Nullable
    private static String liveJson(Recipe<?> recipe, ResourceLocation id) {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("type", String.valueOf(ForgeRegistries.RECIPE_SERIALIZERS.getKey(recipe.getSerializer())));
            root.addProperty("_source", "live-generated");
            root.addProperty("_id", id.toString());
            if (recipe instanceof GTRecipe gt) {
                root.addProperty("gt_recipe_type", String.valueOf(gt.recipeType.registryName));
                root.addProperty("duration", gt.duration);
                root.addProperty("is_fuel", gt.isFuel);
                addContents(root, "inputs", gt.inputs);
                addContents(root, "outputs", gt.outputs);
                addContents(root, "tick_inputs", gt.tickInputs);
                addContents(root, "tick_outputs", gt.tickOutputs);
                if (!gt.conditions.isEmpty()) {
                    JsonArray conditions = new JsonArray();
                    for (var condition : gt.conditions) conditions.add(String.valueOf(condition));
                    root.add("conditions", conditions);
                }
                if (gt.data != null && !gt.data.isEmpty()) root.addProperty("data", gt.data.toString());
            }
            return PRETTY.toJson(root);
        } catch (Exception failure) {
            GTLEnhancedcore.LOGGER.debug("Live recipe JSON failed for {}", id, failure);
            return null;
        }
    }

    private static void addContents(JsonObject root, String key,
                                    Map<RecipeCapability<?>, java.util.List<Content>> contents) {
        if (contents == null || contents.isEmpty()) return;
        JsonObject group = new JsonObject();
        for (var entry : contents.entrySet()) {
            JsonArray list = new JsonArray();
            for (Content content : entry.getValue()) {
                JsonObject one = describe(entry.getKey(), content);
                if (one != null) list.add(one);
            }
            if (!list.isEmpty()) group.add(capabilityName(entry.getKey()), list);
        }
        if (group.size() > 0) root.add(key, group);
    }

    @Nullable
    private static JsonObject describe(RecipeCapability<?> capability, Content content) {
        Object value = content.content;
        if (value == null) return null;
        JsonObject one = new JsonObject();
        if (capability == ItemRecipeCapability.CAP && value instanceof ItemStack stack) {
            var itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
            one.addProperty("item", itemId == null ? "minecraft:air" : itemId.toString());
            one.addProperty("count", stack.getCount());
            if (stack.hasTag()) one.addProperty("nbt", String.valueOf(stack.getTag()));
        } else if (capability == FluidRecipeCapability.CAP && value instanceof FluidStack stack) {
            var fluidId = ForgeRegistries.FLUIDS.getKey(stack.getFluid());
            one.addProperty("fluid", fluidId == null ? "minecraft:empty" : fluidId.toString());
            one.addProperty("amount", stack.getAmount());
            if (stack.hasTag()) one.addProperty("nbt", String.valueOf(stack.getTag()));
        } else if (value instanceof ItemStack stack) {
            var itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
            one.addProperty("item", itemId == null ? "minecraft:air" : itemId.toString());
            one.addProperty("count", stack.getCount());
        } else if (value instanceof FluidStack stack) {
            var fluidId = ForgeRegistries.FLUIDS.getKey(stack.getFluid());
            one.addProperty("fluid", fluidId == null ? "minecraft:empty" : fluidId.toString());
            one.addProperty("amount", stack.getAmount());
        } else {
            one.addProperty("value", String.valueOf(value));
        }
        if (content.chance < 10000) one.addProperty("chance", content.chance);
        if (content.maxChance > 0 && content.maxChance != 10000) one.addProperty("max_chance", content.maxChance);
        if (content.tierChanceBoost != 0) one.addProperty("tier_chance_boost", content.tierChanceBoost);
        if (content.slotName != null && !content.slotName.isEmpty()) one.addProperty("slot_name", content.slotName);
        return one;
    }

    private static String capabilityName(RecipeCapability<?> capability) {
        Object name = capability.name;
        return name == null ? "unknown" : String.valueOf(name);
    }

    /**
     * 把物品或流体 ID 包成带引号的 JSON 片段（用户选定格式）。
     * 流体按 {@code "gtceu:water"} 形式给出，与物品 ID 一致，便于直接粘进 JSON 字段。
     */
    public static String quotedId(ResourceLocation id) {
        return "\"" + id + "\"";
    }

    @Nullable
    public static ResourceLocation itemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        return ForgeRegistries.ITEMS.getKey(stack.getItem());
    }

    @Nullable
    public static ResourceLocation fluidId(FluidStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        Fluid fluid = stack.getFluid();
        return BuiltInRegistries.FLUID.getKey(fluid);
    }
}
