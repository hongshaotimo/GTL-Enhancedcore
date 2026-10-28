package com.gtl.enhancedcore.common.cloud;

import java.util.LinkedHashSet;
import java.util.Set;

/** Per-menu bounded issuance history; foreign handles stay on upstream's own action path. */
public final class CloudPatternAccessHandles {
    private final Set<Long> issued = new LinkedHashSet<>();
    public void issue(long handle) {
        if(handle>=0) throw new IllegalArgumentException("Cloud terminal handles must be negative");
        issued.add(handle);
        if(issued.size()>128) issued.remove(issued.iterator().next());
    }
    public boolean owns(long handle) { return issued.contains(handle); }
}
