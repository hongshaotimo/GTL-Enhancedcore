package com.gtl.enhancedcore.audit;

import com.google.gson.JsonParser;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.integration.jei.recipe.GTRecipeWrapper;
import com.gtl.enhancedcore.client.config.CopyKeysConfig;
import com.gtl.enhancedcore.client.copy.CopyKeysClient;
import com.gtl.enhancedcore.client.copy.RecipeClipboard;
import java.lang.reflect.Field;
import java.util.Map;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;

/** Client-thread checks against the live JEI catalogue; never writes configuration files. */
public final class CopyKeysClientChecks {
    private CopyKeysClientChecks() {}

    public static Map<String, Object> run() throws Exception {
        var mc = Minecraft.getInstance();
        require(mc.isSameThread() && mc.player != null && PreviewJeiAudit.runtime != null,
                "Connected client thread and initialized JEI required");
        Field itemFlag = field(CopyKeysConfig.class, "copyItemId");
        Field fluidFlag = field(CopyKeysConfig.class, "copyFluidId");
        Field liveFlag = field(CopyKeysConfig.class, "liveGenerateRecipe");
        Field hoveredItem = field(CopyKeysClient.class, "hoveredItem");
        Field hoveredFluid = field(CopyKeysClient.class, "hoveredFluid");
        Object oldItem = itemFlag.get(null), oldFluid = fluidFlag.get(null), oldLive = liveFlag.get(null);
        Object oldHoveredItem = hoveredItem.get(null), oldHoveredFluid = hoveredFluid.get(null);
        String clipboard = mc.keyboardHandler.getClipboard();
        try {
            creativeAcceleration(mc);
            var copyId = CopyKeysClient.class.getDeclaredMethod("handleCopyId");
            copyId.setAccessible(true);
            hoveredItem.set(null, ItemStack.EMPTY);
            hoveredFluid.set(null, new FluidStack(Fluids.WATER, 1000));
            itemFlag.set(null, false); fluidFlag.set(null, true);
            mc.keyboardHandler.setClipboard("copy-keys-audit");
            copyId.invoke(null);
            require(mc.keyboardHandler.getClipboard().equals("\"minecraft:water\""),
                    "Enabled fluid copy must work while item copy is disabled");
            itemFlag.set(null, true); fluidFlag.set(null, false);
            mc.keyboardHandler.setClipboard("copy-keys-audit");
            copyId.invoke(null);
            require(mc.keyboardHandler.getClipboard().equals("copy-keys-audit"),
                    "Disabled fluid copy must not fall through the item switch");

            GTRecipe displayed = null;
            for (var category : PreviewJeiAudit.runtime.getRecipeManager().createRecipeCategoryLookup().get().toList()) {
                if (!GTRecipeWrapper.class.isAssignableFrom(category.getRecipeType().getRecipeClass())) continue;
                displayed = firstRecipe(category);
                if (displayed != null) break;
            }
            require(displayed != null, "No real GT recipe found in the JEI catalogue");
            liveFlag.set(null, true);
            var json = JsonParser.parseString(RecipeClipboard.toJson(null, displayed.getId(), displayed)).getAsJsonObject();
            require(json.get("_id").getAsString().equals(displayed.getId().toString())
                            && json.get("duration").getAsInt() == displayed.duration
                            && json.get("gt_recipe_type").getAsString().equals(displayed.recipeType.registryName.toString())
                            && json.has("inputs"),
                    "Serverless export lost the actual displayed JEI recipe");
            require(RecipeClipboard.toJson(null, displayed.getId(), null) == null,
                    "Missing live recipe must retain the failure result");
            liveFlag.set(null, false);
            require(RecipeClipboard.toJson(null, displayed.getId(), displayed) == null,
                    "Live-generation switch must still disable serverless generation");
            return Map.of("creativeAccelerationFormula", true, "itemOffFluidOn", true, "itemOnFluidOff", true,
                    "serverlessJeiRecipe", displayed.getId().toString(), "missingAndDisabledRemainFailures", true);
        } finally {
            itemFlag.set(null, oldItem); fluidFlag.set(null, oldFluid); liveFlag.set(null, oldLive);
            hoveredItem.set(null, oldHoveredItem); hoveredFluid.set(null, oldHoveredFluid);
            mc.keyboardHandler.setClipboard(clipboard);
        }
    }

    private static <T> GTRecipe firstRecipe(IRecipeCategory<T> category) {
        return PreviewJeiAudit.runtime.getRecipeManager().createRecipeLookup(category.getRecipeType()).get()
                .filter(GTRecipeWrapper.class::isInstance).map(GTRecipeWrapper.class::cast)
                .map(wrapper -> wrapper.recipe).filter(recipe -> !recipe.inputs.isEmpty()).findFirst().orElse(null);
    }
    private static void creativeAcceleration(Minecraft mc) throws Exception {
        String script = java.nio.file.Files.readString(mc.gameDirectory.toPath()
                .resolve("kubejs/server_scripts/time_acceleration.js"));
        String marker = "if (player.isCreative()) {";
        int start = script.indexOf(marker);
        require(start >= 0, "Creative time-twister branch missing from actual instance script");
        int end = script.indexOf("} else if", start);
        require(end > start, "Creative time-twister branch boundary missing");
        String branch = script.substring(start + marker.length(), end);
        var contextClass = Class.forName("dev.latvian.mods.rhino.Context");
        var context = contextClass.getMethod("enter").invoke(null);
        var scope = contextClass.getMethod("initStandardObjects").invoke(context);
        String probe = "function accelerate(duration, progress) { var logic = {"
                + "getDuration: function(){return duration}, getProgress: function(){return progress},"
                + "setProgress: function(value){progress=value}}; " + branch + "; return progress; }"
                + "[accelerate(400,350),accelerate(400,390),accelerate(10,0)].join(',')";
        Object result = contextClass.getMethod("evaluateString", Class.forName("dev.latvian.mods.rhino.Scriptable"),
                String.class, String.class, int.class, Object.class)
                .invoke(context, scope, probe, "time-acceleration-creative-audit", 1, null);
        require("380,390,0".equals(String.valueOf(result)),
                "Actual creative acceleration branch regressed or set negative progress: " + result);
    }
    private static Field field(Class<?> owner, String name) throws Exception {
        var field = owner.getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
