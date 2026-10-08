package com.gtl.enhancedcore.common.recipe.iv;

import appeng.api.stacks.AEKey;
import java.util.Map;

public interface IvSlotAccess {
    boolean iv$hasStock();
    Map<AEKey, Long> iv$virtualStock();
    void iv$discardStock();
}
