package com.gtl.enhancedcore.common.recipe;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gtl.enhancedcore.common.machine.CausalityTerminalMachine;
import com.gtl.enhancedcore.common.machine.IndustrialSteamPlatformMachine;
import com.gtl.enhancedcore.common.machine.WeatherAnchorMachine;
import com.gtl.enhancedcore.common.machine.WirelessChargerMachine;
import com.gtl.enhancedcore.common.recipe.iv.IvNativeAccess;
import com.gtl.enhancedcore.common.recipe.iv.IvMachineScope;
import com.gtl.enhancedcore.common.recipe.iv.IvRecipeLogic;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.gtlcore.gtlcore.api.machine.trait.IRecipeStatus;
import org.gtlcore.gtlcore.api.recipe.IGTRecipe;
import org.gtlcore.gtlcore.api.recipe.RecipeResult;

public final class MachineDiagnostics {
    public static final int MAX_DETAILS = 4;

    private MachineDiagnostics() {}

    public static Component current(MetaMachine machine) {
        var details = currentDetails(machine);
        return details.isEmpty() ? null : details.getFirst();
    }

    public static List<Component> currentDetails(MetaMachine machine) {
        if (!(machine instanceof WorkableMultiblockMachine workable)) return List.of();
        if (!workable.isFormed()) return List.of(Component.translatable("gtceu.multiblock.invalid_structure"));
        var logic = workable.getRecipeLogic();
        if (!workable.isRecipeLogicAvailable()) return List.of(text("parts_unloaded"));
        if (logic instanceof IvRecipeLogic iv && !iv.isWorkingEnabled() && iv.getDiagnosticReason() != null)
            return List.of(iv.getDiagnosticReason());
        if (!logic.isWorkingEnabled()) return List.of(text("paused"));
        if (machine instanceof WirelessChargerMachine charger) return singleton(charger.getDiagnostic());
        if (machine instanceof WeatherAnchorMachine weather) return weather.isAnchoring() ? List.of() : List.of(text("power"));
        if (machine instanceof CausalityTerminalMachine terminal) return singleton(terminal.getDiagnostic());
        var details = new ArrayList<Component>();
        if (logic instanceof IvRecipeLogic iv) {
            addUnique(details, iv.getDiagnosticReason());
            appendOrders(details, iv.getOrderSummary());
            if (iv.getQueuedTasks() > 0 && iv.getInputPower() <= 0) addUnique(details, power(workable, 0));
            return List.copyOf(details);
        }
        if (machine instanceof WorkableElectricMultiblockMachine electric && IvMachineScope.nativeTarget(electric)) {
            var nativeLogic = (IvNativeAccess) logic;
            for (var reason : nativeLogic.iv$details()) addUnique(details, reason);
            String message = nativeLogic.iv$message();
            if (details.isEmpty() && !message.isEmpty()) addUnique(details, Component.translatable(message));
            appendOrders(details, nativeLogic.iv$summary());
            return List.copyOf(details);
        }
        if (machine instanceof IndustrialSteamPlatformMachine steam && logic.isIdle()
                && steam.getSteamStoredMb() < IndustrialSteamPlatformMachine.STEAM_PER_RECIPE_MB)
            addUnique(details, Component.translatable("gtl_enhancedcore.diagnostic.steam_detail",
                    steam.getSteamStoredMb(), IndustrialSteamPlatformMachine.STEAM_PER_RECIPE_MB));
        if (logic.isWorking()) return List.of();
        if (logic instanceof RetryableRecipeLogic retryable) addUnique(details, retryable.getDiagnosticReason());
        if (logic instanceof ThreadLimitedRecipeLogic threaded) addUnique(details, threaded.getDiagnosticReason());
        if (logic instanceof IRecipeStatus status) {
            addUnique(details, reason(workable, status.getWorkingStatus()));
            if (details.isEmpty()) addUnique(details, reason(workable, status.getRecipeStatus()));
        }
        if (details.isEmpty() && logic.isWaiting() && !logic.getFancyTooltip().isEmpty())
            addUnique(details, logic.getFancyTooltip().getFirst());
        if (details.isEmpty()) addUnique(details, text(logic.isWaiting() ? "waiting" : "no_recipe"));
        return List.copyOf(details);
    }

    private static List<Component> singleton(Component reason) {
        return reason == null ? List.of() : List.of(reason);
    }

    private static void appendOrders(List<Component> details, CompoundTag summary) {
        var rows = summary.getList("orders", Tag.TAG_COMPOUND);
        for (int index = 0; index < rows.size() && details.size() < MAX_DETAILS; index++) {
            var row = rows.getCompound(index);
            Component reason = decode(row.getString("reason"));
            if (reason != null) addUnique(details, Component.translatable("gtl_enhancedcore.diagnostic.order_reason",
                    row.getInt("slot"), reason));
        }
    }

    private static Component reason(WorkableMultiblockMachine machine, RecipeResult result) {
        if (result == null || result.isSuccess()) return null;
        if (result.equals(RecipeResult.FAIL_FIND)) return text("no_recipe");
        if (result.equals(RecipeResult.FAIL_INPUT)) return text("input");
        if (result.equals(RecipeResult.FAIL_OUTPUT)) return text("output");
        if (result.equals(RecipeResult.FAIL_VOLTAGE_TIER)) return text("voltage");
        if (result.equals(RecipeResult.FAIL_NO_ENOUGH_EU_IN)) {
            GTRecipe recipe = machine.getRecipeLogic().getLastRecipe();
            return power(machine, recipe == null ? 0 : RecipeHelper.getInputEUt(recipe));
        }
        if (result.equals(RecipeResult.FAIL_NO_ENOUGH_EU_OUT)) return text("energy_output");
        if (result.equals(RecipeResult.FAIL_NO_ENOUGH_CWU_IN)) return text("computation");
        if (result.equals(RecipeResult.FAIL_NO_FIND_RESEARCHED)) return text("research");
        if (result.equals(RecipeResult.FAIL_NO_ENOUGH_TEMPERATURE)) return text("temperature");
        if (result.equals(RecipeResult.FAIL_LACK_FLUID)) return text("fluid");
        if (result.equals(RecipeResult.FAIL_NO_SKYLIGHT)) return text("skylight");
        return result.reason() == null ? text("conditions") : result.reason();
    }

    public static Component power(WorkableMultiblockMachine machine, long required) {
        long capacity = 0;
        var seen = Collections.newSetFromMap(new IdentityHashMap<IEnergyContainer, Boolean>());
        for (var direction : List.of(IO.IN, IO.BOTH)) {
            var inputs = machine.getCapabilitiesProxy().get(direction, EURecipeCapability.CAP);
            if (inputs == null) continue;
            for (var input : inputs) if (input instanceof IEnergyContainer energy && seen.add(energy))
                capacity = RecipePowerBudget.add(capacity, RecipePowerBudget.power(energy.getInputVoltage(), energy.getInputAmperage()));
        }
        var energy = machine instanceof WorkableElectricMultiblockMachine electric ? electric.getEnergyContainer() : null;
        long stored = energy == null ? 0 : Math.max(0, energy.getEnergyStored());
        return Component.translatable("gtl_enhancedcore.diagnostic.power_detail", Math.max(0, required), capacity, stored);
    }

    public static Component voltage(WorkableElectricMultiblockMachine machine, GTRecipe recipe) {
        int required = Math.max(0, Math.min(GTValues.V.length - 1, IGTRecipe.of(recipe).getEuTier()));
        int supplied = Math.max(0, Math.min(GTValues.V.length - 1, machine.getTier()));
        return Component.translatable("gtl_enhancedcore.diagnostic.voltage_detail",
                GTValues.VNF[required], RecipeHelper.getInputEUt(recipe), GTValues.VNF[supplied], machine.getMaxVoltage());
    }

    public static Component decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) return null;
        try { return Component.Serializer.fromJson(encoded); }
        catch (RuntimeException invalid) { return null; }
    }

    public static Component conditionDetail(GTRecipe recipe, Component fallback) {
        Component reason = fallback == null ? text("conditions") : fallback;
        if (recipe.conditions.size() == 1) {
            var condition = recipe.conditions.getFirst();
            if (condition.isOr() && !condition.isReverse()) {
                Component requirement = condition.getTooltips();
                if (requirement != null) reason = requirement;
            }
        }
        return Component.translatable("gtl_enhancedcore.diagnostic.condition_detail", reason);
    }

    public static Component text(String name) {
        return Component.translatable("gtl_enhancedcore.diagnostic." + name);
    }

    private static void addUnique(List<Component> lines, Component reason) {
        if (reason == null || lines.size() >= MAX_DETAILS) return;
        String encoded = Component.Serializer.toJson(reason.copy().setStyle(Style.EMPTY));
        if (lines.stream().noneMatch(line -> Component.Serializer.toJson(line.copy().setStyle(Style.EMPTY)).equals(encoded)))
            lines.add(reason);
    }

    public static void append(MetaMachine machine, List<Component> lines) {
        for (var reason : currentDetails(machine)) {
            String encoded = Component.Serializer.toJson(reason.copy().setStyle(Style.EMPTY));
            if (lines.stream().noneMatch(line -> Component.Serializer.toJson(line.copy().setStyle(Style.EMPTY)).equals(encoded)))
                lines.add(reason.copy().withStyle(ChatFormatting.YELLOW));
        }
    }
}
