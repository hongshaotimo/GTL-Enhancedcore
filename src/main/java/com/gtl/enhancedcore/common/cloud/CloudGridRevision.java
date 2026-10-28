package com.gtl.enhancedcore.common.cloud;

/** Server-runtime node membership revision; never a persistent network identity. */
public interface CloudGridRevision {
    long cloud$topologyRevision();
}
