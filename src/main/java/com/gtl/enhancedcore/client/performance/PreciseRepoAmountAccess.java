package com.gtl.enhancedcore.client.performance;

import appeng.api.stacks.AEKey;
import appeng.client.gui.me.common.Repo;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.math.BigInteger;
import java.util.Map;

/** Resolves fix4's merged field after Repo transformation; Mixin shadows resolve before field merging. */
public final class PreciseRepoAmountAccess {
    private PreciseRepoAmountAccess() {}

    private static final class Access {
        private static final VarHandle EXACT = resolve();
        private static VarHandle resolve() {
            try {
                return MethodHandles.privateLookupIn(Repo.class, MethodHandles.lookup())
                        .findVarHandle(Repo.class, "gtlcore$exact", Map.class);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("GTLCore fix4 Repo exact-amount field is unavailable", failure);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<AEKey, BigInteger> get(Repo repo) {
        return (Map<AEKey, BigInteger>) Access.EXACT.get(repo);
    }
}
