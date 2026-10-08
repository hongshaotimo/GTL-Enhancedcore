package com.gtl.enhancedcore.mixin.performance.client;

import net.minecraft.Util;
import org.spongepowered.asm.mixin.Mixin;

/** The plugin sets the worker property before Util's static initializer; no executor is replaced. */
@Mixin(Util.class)
public abstract class WorkerBootstrapMixin {}
