package com.gtl.enhancedcore.mixin.gtceu;

import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Reuses GTLCore's committed formation path under the caller's server-thread pattern lock. */
@Pseudo // The invoked method exists only after GTLCore's mandatory Mixin has merged.
@Mixin(targets = "com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine",
        remap = false, priority = 900)
public interface StructureSearchFormationInvoker {
    @Dynamic("Merged by GTLCore's MultiblockControllerMachineMixin")
    @Invoker(value = "gtlcore$updateStructure", remap = false)
    boolean enhancedcore$updateStructure(ServerLevel level, boolean controllerRemoved, String reason);
}
