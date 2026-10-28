package com.gtl.enhancedcore.common.cloud;

import java.util.UUID;
import net.minecraft.world.item.ItemStack;

/** Server-only original snapshot used to reject stale terminal clicks. */
public record CloudPatternAccessRow(UUID id, long revision, ItemStack original) {}
