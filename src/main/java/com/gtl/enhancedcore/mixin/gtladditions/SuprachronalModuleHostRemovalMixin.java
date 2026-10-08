package com.gtl.enhancedcore.mixin.gtladditions;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gtl.enhancedcore.common.util.SuprachronalModuleRemoval;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.gtlcore.gtlcore.api.machine.multiblock.IModularMachineHost;
import org.gtlcore.gtlcore.api.machine.multiblock.IModularMachineModule;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.SuprachronalAssemblyLineMachine;
import org.gtlcore.gtlcore.utils.datastructure.ModuleRenderInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SuprachronalAssemblyLineMachine.class, remap = false)
public abstract class SuprachronalModuleHostRemovalMixin
        implements IModularMachineHost<SuprachronalAssemblyLineMachine> {
    @Override
    public <ModuleType extends IModularMachineModule<SuprachronalAssemblyLineMachine, ModuleType>>
            void addModule(ModuleType module) {
        if (module != null) module.removeFromHost((SuprachronalAssemblyLineMachine) (Object) this);
    }

    @Override
    public boolean isValidModule(MetaMachine moduleMachine) {
        return false;
    }

    @Override
    public void scanAndConnectModules() {
        safeClearModules();
    }

    @Inject(method = "getModuleSet", at = @At("RETURN"))
    private void enhanced$detachLegacyModules(
            CallbackInfoReturnable<Set<IModularMachineModule<SuprachronalAssemblyLineMachine, ?>>> callback) {
        SuprachronalModuleRemoval.detachLegacyModules(callback.getReturnValue(),
                module -> {
                    if (module.getHost() != null || module.getHostPosition() != null) {
                        module.removeFromHost((SuprachronalAssemblyLineMachine) (Object) this);
                    }
                });
    }

    @Inject(method = "getModuleScanPositions", at = @At("HEAD"), cancellable = true)
    private void enhanced$removeModuleScanPositions(CallbackInfoReturnable<BlockPos[]> callback) {
        callback.setReturnValue(new BlockPos[0]);
    }

    @Inject(method = "getModulesForRendering", at = @At("HEAD"), cancellable = true)
    private void enhanced$removeModuleRenderEntries(CallbackInfoReturnable<List<ModuleRenderInfo>> callback) {
        callback.setReturnValue(List.of());
    }

    @Inject(method = "getMaxModuleCount", at = @At("HEAD"), cancellable = true)
    private void enhanced$removeModuleCapacity(CallbackInfoReturnable<Integer> callback) {
        callback.setReturnValue(0);
    }

    @Inject(method = "addDisplayText", at = @At("RETURN"))
    private void enhanced$removeModuleCountDisplay(List<Component> text, CallbackInfo callback) {
        text.removeIf(component -> component.getContents() instanceof TranslatableContents translated
                && SuprachronalModuleRemoval.isInstalledModuleCount(translated.getKey()));
    }
}
