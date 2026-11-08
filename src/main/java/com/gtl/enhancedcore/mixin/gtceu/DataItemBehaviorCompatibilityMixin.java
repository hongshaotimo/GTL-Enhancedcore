package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.common.item.DataItemBehavior;
import org.spongepowered.asm.mixin.Mixin;

/** Runs the compatibility priority adjustment before Mixin prepares foreign injections. */
@Mixin(value = DataItemBehavior.class, remap = false)
public abstract class DataItemBehaviorCompatibilityMixin {}
