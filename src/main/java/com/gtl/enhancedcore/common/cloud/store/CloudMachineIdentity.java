package com.gtl.enhancedcore.common.cloud.store;

import java.util.UUID;

/** Lazy identity for actual server enrollment; reads and structure previews never create one. */
public interface CloudMachineIdentity {
    UUID cloud$machineInstance(boolean enroll);
}
