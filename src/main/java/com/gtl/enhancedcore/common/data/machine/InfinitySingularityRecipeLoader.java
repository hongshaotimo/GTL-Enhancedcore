package com.gtl.enhancedcore.common.data.machine;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.config.ConfigFiles;
import com.gtl.enhancedcore.common.config.SingularityRecipeConfig;
import com.gtl.enhancedcore.common.config.SingularityRecipeConfig.Entry;
import com.gtl.enhancedcore.common.data.GTLEnhancedcoreRecipeTypes;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 无限奇点压缩器配方加载器。
 * 从 config/GTL-Enhancedcore/infinity_singularity_recipes.json 读取材料清单，
 * 在 GTCEu 附属配方阶段（GTLEnhancedcoreGTAddon.addRecipes）用标准 GTRecipeBuilder.save(provider)
 * 输出配方 —— 与普通机器完全一致：配方经 GTDynamicDataPack 进入 RecipeManager，
 * 因此 JEI 能显示，GTRecipeLookup 由 GTCEu 自行填充（禁止手工 addRecipe）。
 * 输出物品为 ExtendedAE 无限元件（expatternprovider:infinity_cell + record NBT），
 * 与 ExtendedAE 原版 getRecordCell 生成的 NBT 一致（{"#c":"ae2:i"/"ae2:f","id":"..."}）。
 * <p>
 * 文件格式：每行 {@code "物品或流体ID"数量}，支持 # 注释与空行：
 * <pre>
 * "gtceu:bronze_ingot"6400
 * "fluid:minecraft:water"64000
 * </pre>
 * 不带前缀时自动判定类型。
 */
public final class InfinitySingularityRecipeLoader {

    private static final Path FILE = FMLPaths.CONFIGDIR.get()
            .resolve("GTL-Enhancedcore").resolve("infinity_singularity_recipes.json");
    private static final ResourceLocation INFINITY_CELL =
            new ResourceLocation("expatternprovider", "infinity_cell");
    /** 固定耗时：1200 秒。 */
    private static final int DURATION = 24000;

    private static volatile List<Entry> materials = List.of();

    private InfinitySingularityRecipeLoader() {}

    public static List<Entry> getMaterials() { return materials; }

    /** 确保默认配置文件存在。 */
    public static void ensureDefaultConfig() {
        try {
            if (!Files.exists(FILE)) {
                createDefaultConfig();
            }
        } catch (RuntimeException ex) {
            GTLEnhancedcore.LOGGER.warn("[InfinitySingularity] Could not create default config", ex);
        }
    }

    /**
     * GTCEu 附属配方阶段回调：解析配置并按标准链路输出配方。
     * 此时物品/流体注册已完成，类型判定与 NBT 构建均可靠。
     */
    public static void registerRecipes(Consumer<FinishedRecipe> provider) {
        materials = List.of();
        GTRecipeType recipeType = GTLEnhancedcoreRecipeTypes.INFINITY_SINGULARITY;
        if (recipeType == null) {
            GTLEnhancedcore.LOGGER.error("[InfinitySingularity] recipe type not registered");
            return;
        }
        Item cellItem = ForgeRegistries.ITEMS.getValue(INFINITY_CELL);
        if (cellItem == null || cellItem == Items.AIR) {
            GTLEnhancedcore.LOGGER.error("[InfinitySingularity] {} not found, no recipe registered", INFINITY_CELL);
            return;
        }
        List<Entry> entries = parseAndCache();
        List<Entry> registered = new ArrayList<>();
        int count = 0;
        for (Entry entry : entries) {
            if (saveRecipe(provider, recipeType, cellItem, entry)) {
                count++;
                registered.add(entry);
            }
        }
        materials = List.copyOf(registered);
        GTLEnhancedcore.LOGGER.info("[InfinitySingularity] Registered {} recipes", count);
    }

    /** 读取配置文件并重新解析（每次强制，避免早期解析失败被缓存）。 */
    public static List<Entry> parseAndCache() {
        ensureDefaultConfig();
        try {
            SingularityRecipeConfig.Result parsed = SingularityRecipeConfig.parse(
                    Files.readString(FILE, StandardCharsets.UTF_8),
                    InfinitySingularityRecipeLoader::isItem, InfinitySingularityRecipeLoader::isFluid);
            parsed.errors().forEach(error -> GTLEnhancedcore.LOGGER.warn("[InfinitySingularity] {}", error));
            return parsed.entries();
        } catch (IOException e) {
            GTLEnhancedcore.LOGGER.error("[InfinitySingularity] Failed to read {}", FILE, e);
            return List.of();
        }
    }

    /** 解析文本，填充 materialIds / materialTypes 并返回条目列表。 */


    private static boolean isItem(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        Item item = location == null ? null : ForgeRegistries.ITEMS.getValue(location);
        return item != null && item != Items.AIR;
    }

    private static boolean isFluid(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        Fluid fluid = location == null ? null : ForgeRegistries.FLUIDS.getValue(location);
        return fluid != null && fluid != Fluids.EMPTY;
    }

    private static void createDefaultConfig() {
        try {
            Files.createDirectories(FILE.getParent());
            String example = "# 无限奇点压缩器配方：每行一条，\"ID\"数量\n"
                    + "\"gtceu:bronze_ingot\"6400\n"
                    + "# 流体用 fluid: 前缀\n"
                    + "\"fluid:minecraft:water\"64000\n";
            ConfigFiles.writeAtomically(FILE, example);
            GTLEnhancedcore.LOGGER.info("[InfinitySingularity] Created default config at {}", FILE);
        } catch (IOException ex) {
            GTLEnhancedcore.LOGGER.warn("[InfinitySingularity] Could not create default config", ex);
        }
    }

    /** 构建并保存单条配方；输入非法时返回 false。 */
    private static boolean saveRecipe(Consumer<FinishedRecipe> provider, GTRecipeType recipeType,
                                      Item cellItem, Entry entry) {
        ResourceLocation location = ResourceLocation.tryParse(entry.id());
        if (location == null) {
            GTLEnhancedcore.LOGGER.warn("[InfinitySingularity] Invalid id: {}", entry.id());
            return false;
        }
        GTRecipeBuilder builder = recipeType.recipeBuilder(GTLEnhancedcore.id(recipePath(entry)));
        builder.EUt(GTValues.V[GTValues.UIV] * 2650L);
        builder.duration(DURATION);
        if (entry.fluid()) {
            Fluid fluid = ForgeRegistries.FLUIDS.getValue(location);
            if (fluid == null || fluid == Fluids.EMPTY) {
                GTLEnhancedcore.LOGGER.warn("[InfinitySingularity] Unknown fluid id: {}", entry.id());
                return false;
            }
            builder.inputFluids(FluidIngredient.of(entry.count(), fluid));
        } else {
            Item item = ForgeRegistries.ITEMS.getValue(location);
            if (item == null || item == Items.AIR) {
                GTLEnhancedcore.LOGGER.warn("[InfinitySingularity] Unknown item id: {}", entry.id());
                return false;
            }
            builder.inputItems(item, entry.count());
        }
        builder.outputItems(createCellStack(cellItem, entry));
        builder.save(provider);
        return true;
    }

    /** 配方 ID 路径：命名空间与非法字符统一转下划线，保证 ResourceLocation 合法且唯一。 */
    private static String recipePath(Entry entry) {
        return entry.recipePath();
    }

    /** 无限元件输出（record NBT 与 ExtendedAE getRecordCell 一致）。 */
    private static ItemStack createCellStack(Item cellItem, Entry entry) {
        CompoundTag record = new CompoundTag();
        record.putString("#c", entry.fluid() ? "ae2:f" : "ae2:i");
        record.putString("id", entry.id());
        CompoundTag tag = new CompoundTag();
        tag.put("record", record);
        ItemStack output = new ItemStack(cellItem);
        output.setTag(tag);
        return output;
    }

}
