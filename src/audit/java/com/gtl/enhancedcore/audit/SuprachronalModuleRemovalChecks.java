package com.gtl.enhancedcore.audit;

import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.misc.EnergyContainerList;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gtl.enhancedcore.common.util.SuprachronalModuleRemoval;
import com.gtladd.gtladditions.api.machine.logic.MutableRecipesLogic;
import com.gtladd.gtladditions.common.machine.multiblock.controller.mutable.MutableSuprachronalAssemblyLineMachine;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.gtlcore.gtlcore.api.machine.multiblock.IModularMachineHost;
import org.gtlcore.gtlcore.api.machine.multiblock.IModularMachineModule;
import org.gtlcore.gtlcore.api.machine.trait.IRecipeCapabilityMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.SuprachronalAssemblyLineMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.SuprachronalAssemblyLineModuleMachine;

public final class SuprachronalModuleRemovalChecks {
    private SuprachronalModuleRemovalChecks() {}

    public static int run(MutableSuprachronalAssemblyLineMachine controller,
                          SuprachronalAssemblyLineModuleMachine module) throws ReflectiveOperationException {
        return run(controller, module, null);
    }

    public static int run(MutableSuprachronalAssemblyLineMachine controller,
                          SuprachronalAssemblyLineModuleMachine module,
                          IModularMachineHost<?> otherHost) throws ReflectiveOperationException {
        Checks checks = new Checks();
        checks.require(controller.getDefinition().getId().toString().equals("gtceu:suprachronal_assembly_line"),
                "audit fixture must use the registered mutable suprachronal controller");
        checks.require(module.getDefinition().getId().toString().equals("gtceu:suprachronal_assembly_line_module"),
                "the legacy extension controller remains registered");
        checks.require(controller.isFormed() && controller.isRecipeLogicAvailable(),
                "the main structure must remain formed and available in the isolated fixture");
        ControllerState controllerState = ControllerState.capture(controller);
        OtherHostState otherState = otherHost == null ? null : OtherHostState.capture(otherHost, checks);
        RecipeLogic moduleLogic = module.getRecipeLogic();
        Object previousRecipe = moduleLogic.getLastRecipe();
        Object previousStatus = moduleLogic.getStatus();
        int previousProgress = moduleLogic.getProgress();
        int previousDuration = moduleLogic.getDuration();
        long previousEnergy = module.getEnergyContainer().getEnergyStored();

        verifyController(controller, module, checks);
        seedLegacyConnection(controller, module);
        checks.require(controller.getFormedModuleCount() == 0, "a restored host-side module record has no capacity");
        verifyDisconnected(module, checks);
        checks.require(rawModules(controller).isEmpty(), "host-side legacy membership is physically cleared");

        seedLegacyConnection(controller, module);
        checks.require(module.getParallel() == 0, "a stale live module cannot borrow the main parallel hatch");
        verifyDisconnected(module, checks);
        checks.require(rawModules(controller).isEmpty(), "module-first cleanup removes the host counterpart");

        writeField(SuprachronalAssemblyLineModuleMachine.class, module, "hostPosition", controller.getPos());
        checks.require(!module.findAndConnectToHost(), "persisted hostPosition cannot restore an old connection");
        verifyDisconnected(module, checks);
        module.connectToHost(controller);
        checks.require(!module.isValidHost(controller), "newly formed extension controllers reject the main host");
        verifyDisconnected(module, checks);
        controller.addModule(module);
        checks.require(controller.getModules().isEmpty(), "direct host-side attachment is rejected");
        module.setHost(controller);
        checks.require(module.getHost() == null, "the concrete host setter cannot bypass attachment removal");
        controller.scanAndConnectModules();
        verifyController(controller, module, checks);

        moduleLogic.updateTickSubscription();
        checks.require(module.getRecipeLogic() == moduleLogic, "the module recipe-logic object is not replaced");
        checks.require(moduleLogic.getLastRecipe() == previousRecipe && moduleLogic.getStatus() == previousStatus,
                "disabling the extension does not erase its saved recipe or status");
        checks.require(moduleLogic.getProgress() == previousProgress && moduleLogic.getDuration() == previousDuration,
                "the availability gate does not discard saved paid progress");
        checks.require(module.getEnergyContainer().getEnergyStored() == previousEnergy,
                "disconnecting and unsubscribing the legacy extension do not consume its energy");
        controllerState.verify(controller, checks);
        if (otherState != null) otherState.verify(otherHost, checks);
        return checks.assertions;
    }

    public static int verifyPreview(Object pattern) throws ReflectiveOperationException {
        Checks checks = new Checks();
        Class<?> patternClass = pattern.getClass();
        checks.require(patternClass.getName().equals("org.gtlcore.gtlcore.api.gui.PatternPreviewWidget$MBPattern"),
                "preview verification requires the native metadata object");
        Object controller = readField(patternClass, pattern, "controllerBase");
        checks.require(controller instanceof MutableSuprachronalAssemblyLineMachine,
                "preview metadata must be built from the actual mutable suprachronal controller");
        checks.require(Boolean.FALSE.equals(readField(patternClass, pattern, "hasModule")),
                "the native module-toggle flag is disabled");
        checks.require(((Set<?>) readField(patternClass, pattern, "moduleOnlyBlocks")).isEmpty(),
                "module-only blocks are absent from the main preview and material aggregation");
        return checks.assertions;
    }

    public static int verifyOtherPreview(Object pattern) throws ReflectiveOperationException {
        Checks checks = new Checks();
        Class<?> patternClass = pattern.getClass();
        checks.require(patternClass.getName().equals("org.gtlcore.gtlcore.api.gui.PatternPreviewWidget$MBPattern"),
                "negative-control preview must use native metadata");
        Object controller = readField(patternClass, pattern, "controllerBase");
        checks.require(controller instanceof IModularMachineHost<?>
                        && !(controller instanceof SuprachronalAssemblyLineMachine),
                "negative-control preview must belong to another modular machine");
        checks.require(Boolean.TRUE.equals(readField(patternClass, pattern, "hasModule")),
                "other machines retain the native module-preview toggle");
        return checks.assertions;
    }

    private static void verifyController(SuprachronalAssemblyLineMachine controller,
                                         SuprachronalAssemblyLineModuleMachine module, Checks checks) {
        checks.require(controller.getMaxModuleCount() == 0, "the controller exposes no expansion capacity");
        checks.require(controller.getModuleSet().isEmpty() && controller.getModules().isEmpty(),
                "the controller exposes no active modules");
        checks.require(controller.getFormedModuleCount() == 0 && !controller.exceedsModuleLimit(),
                "removed modules neither add capacity nor stall normal main-machine work");
        checks.require(controller.getModuleScanPositions().length == 0, "new formation has no module scan entry");
        if (FMLEnvironment.dist.isClient()) {
            checks.require(controller.getModulesForRendering().isEmpty(), "the main preview exposes no module shapes");
        }
        checks.require(!controller.isValidModule(module), "host-side module validation rejects the old extension");
        List<Component> display = new ArrayList<>();
        controller.addDisplayText(display);
        checks.require(display.stream().noneMatch(component -> component.getContents() instanceof TranslatableContents text
                        && SuprachronalModuleRemoval.isInstalledModuleCount(text.getKey())),
                "the native installed-module-count row is absent from the main GUI");
    }

    private static void verifyDisconnected(SuprachronalAssemblyLineModuleMachine module, Checks checks) {
        checks.require(module.getHost() == null && module.getHostPosition() == null,
                "legacy in-memory and persisted host links are both detached");
        checks.require(!module.isConnectedToHost(), "the old module no longer reports a connection");
        checks.require(module.getHostScanPositions().length == 0, "module-first formation cannot scan for a host");
        checks.require(module.getParallel() == 0, "the old module contributes no shared parallel capacity");
        checks.require(!module.isRecipeLogicAvailable(), "the disabled extension cannot subscribe recipe work");
    }

    private static void seedLegacyConnection(SuprachronalAssemblyLineMachine controller,
                                             SuprachronalAssemblyLineModuleMachine module)
            throws ReflectiveOperationException {
        writeField(SuprachronalAssemblyLineModuleMachine.class, module, "host", controller);
        writeField(SuprachronalAssemblyLineModuleMachine.class, module, "hostPosition", controller.getPos());
        rawModules(controller).add(module);
    }

    @SuppressWarnings("unchecked")
    private static Set<IModularMachineModule<SuprachronalAssemblyLineMachine, ?>> rawModules(
            SuprachronalAssemblyLineMachine controller) throws ReflectiveOperationException {
        return (Set<IModularMachineModule<SuprachronalAssemblyLineMachine, ?>>)
                readField(SuprachronalAssemblyLineMachine.class, controller, "modules");
    }

    private static Object readField(Class<?> owner, Object instance, String name) throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(instance);
    }

    private static void writeField(Class<?> owner, Object instance, String name, Object value)
            throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(instance, value);
    }

    private record ControllerState(Object recipeLogic, Object recipeCheck, List<GTRecipeType> recipeTypes,
                                   int parallel, int recipeType, boolean multipleMode, int threads,
                                   Object threadPart, long voltage, Object dataHatch, Object parallelHatch,
                                   EnergyState energyState) {
        private static ControllerState capture(MutableSuprachronalAssemblyLineMachine controller)
                throws ReflectiveOperationException {
            MutableRecipesLogic<?> logic = controller.getRecipeLogic();
            IRecipeCapabilityMachine capabilities = (IRecipeCapabilityMachine) (Object) controller;
            return new ControllerState(logic, readField(MutableRecipesLogic.class, logic, "recipeCheck"),
                    List.copyOf(Arrays.asList(controller.getRecipeTypes())), controller.getMaxParallel(),
                    controller.getActiveRecipeType(), logic.isMultipleRecipeMode(), logic.getMultipleThreads(),
                    controller.getThreadPartMachine(), controller.getOverclockVoltage(),
                    capabilities.getDataAccessHatch(), capabilities.getParallelHatch(),
                    EnergyState.capture(controller.getEnergyContainer()));
        }

        private void verify(MutableSuprachronalAssemblyLineMachine controller, Checks checks)
                throws ReflectiveOperationException {
            MutableRecipesLogic<?> logic = controller.getRecipeLogic();
            IRecipeCapabilityMachine capabilities = (IRecipeCapabilityMachine) (Object) controller;
            checks.require(recipeLogic == logic, "the original mutable recipe logic remains installed");
            checks.require(recipeCheck == readField(MutableRecipesLogic.class, logic, "recipeCheck")
                            && dataHatch == capabilities.getDataAccessHatch(),
                    "the original research predicate and data-access hatch are retained");
            checks.require(recipeTypes.equals(Arrays.asList(controller.getRecipeTypes()))
                            && recipeType == controller.getActiveRecipeType() && multipleMode == logic.isMultipleRecipeMode(),
                    "main-machine recipe maps and selected processing mode are retained");
            checks.require(parallel == controller.getMaxParallel() && parallelHatch == capabilities.getParallelHatch(),
                    "the main parallel hatch and its capacity are retained");
            checks.require(threads == logic.getMultipleThreads() && threadPart == controller.getThreadPartMachine(),
                    "the independent thread-modifier part remains effective");
            checks.require(voltage == controller.getOverclockVoltage(), "the main overclock voltage is retained");
            energyState.verify(controller.getEnergyContainer(), checks);
            checks.require(controller.isFormed() && controller.isRecipeLogicAvailable(),
                    "the main structure remains available after legacy module cleanup");
        }
    }

    private record EnergyState(List<IEnergyContainer> containers, List<EnergyValues> containerValues,
                               EnergyValues totals, long highestInputVoltage, int highestInputCount) {
        private static EnergyState capture(EnergyContainerList energy) throws ReflectiveOperationException {
            List<IEnergyContainer> containers = ((List<?>) readField(EnergyContainerList.class, energy,
                    "energyContainerList")).stream().map(IEnergyContainer.class::cast).toList();
            return new EnergyState(containers, containers.stream().map(EnergyValues::capture).toList(),
                    EnergyValues.capture(energy), energy.getHighestInputVoltage(), energy.getNumHighestInputContainers());
        }

        private void verify(EnergyContainerList energy, Checks checks) throws ReflectiveOperationException {
            EnergyState current = capture(energy);
            checks.require(containers.size() == current.containers.size(),
                    "the number of physical energy handlers is retained");
            for (int index = 0; index < containers.size(); index++) {
                checks.require(containers.get(index) == current.containers.get(index),
                        "the same physical energy handlers remain connected");
                checks.require(containerValues.get(index).equals(current.containerValues.get(index)),
                        "each physical energy handler retains stored energy, capacity, voltage and amperage");
            }
            checks.require(totals.equals(current.totals),
                    "aggregate stored energy, capacity, input and output voltage and amperage are retained");
            checks.require(highestInputVoltage == current.highestInputVoltage
                            && highestInputCount == current.highestInputCount,
                    "the native highest-voltage input rating is retained");
        }
    }

    private record EnergyValues(long stored, long capacity, long inputVoltage, long inputAmperage,
                                long outputVoltage, long outputAmperage) {
        private static EnergyValues capture(IEnergyContainer energy) {
            return new EnergyValues(energy.getEnergyStored(), energy.getEnergyCapacity(), energy.getInputVoltage(),
                    energy.getInputAmperage(), energy.getOutputVoltage(), energy.getOutputAmperage());
        }
    }

    private record OtherHostState(Set<?> modules, int count, int capacity, BlockPos[] scanPositions, Integer renderCount) {
        private static OtherHostState capture(IModularMachineHost<?> host, Checks checks) {
            checks.require(!(host instanceof SuprachronalAssemblyLineMachine), "negative control must be another host");
            checks.require(host.getFormedModuleCount() > 0, "negative control must retain an actual formed module");
            return new OtherHostState(Set.copyOf(host.getModules()), host.getFormedModuleCount(),
                    host.getMaxModuleCount(), host.getModuleScanPositions().clone(),
                    FMLEnvironment.dist.isClient() ? host.getModulesForRendering().size() : null);
        }

        private void verify(IModularMachineHost<?> host, Checks checks) {
            checks.require(modules.equals(host.getModules()) && count == host.getFormedModuleCount(),
                    "another host retains all module memberships and formed-module counts");
            checks.require(capacity == host.getMaxModuleCount() && Arrays.equals(scanPositions, host.getModuleScanPositions()),
                    "another host retains expansion capacity and attachment locations");
            if (renderCount != null) {
                checks.require(renderCount == host.getModulesForRendering().size(),
                        "another machine retains its module-preview descriptions");
            }
        }
    }

    private static final class Checks {
        private int assertions;

        private void require(boolean condition, String message) {
            assertions++;
            if (!condition) throw new AssertionError(message);
        }
    }
}
