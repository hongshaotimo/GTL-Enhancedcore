package com.gtl.enhancedcore.common.machine;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;

/** Same isolated-order scheduler as the four IV machines; fixed threads/parallel, native maintenance multiplier. */
public final class IntegratedUniversalFactoryMachine extends TieredParallelMachine {
    public static final int THREADS = 10;
    public static final int PARALLEL = 64;

    public IntegratedUniversalFactoryMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
    }

    @Override
    public int getThreadsForTier() {
        return THREADS;
    }

    @Override
    public int getMaxParallel() {
        return PARALLEL;
    }

    @Override
    public boolean ivMaintenancePenalty() {
        return false;
    }
}
