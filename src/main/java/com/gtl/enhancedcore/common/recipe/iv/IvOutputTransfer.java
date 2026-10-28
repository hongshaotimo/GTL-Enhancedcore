package com.gtl.enhancedcore.common.recipe.iv;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import net.minecraft.network.chat.Component;

/** Keep AE's payment and insertion semantics; probe a zero transfer without changing stock. */
public final class IvOutputTransfer {
    private IvOutputTransfer() {}

    public enum State {
        COMPLETE(null),
        PROGRESS("gtl_enhancedcore.diagnostic.iv_output_progress"),
        STORAGE("gtl_enhancedcore.diagnostic.iv_output_storage"),
        POWER("gtl_enhancedcore.diagnostic.iv_output_power"),
        REQUEST_LIMIT("gtl_enhancedcore.diagnostic.iv_output_request_limit"),
        REJECTED("gtl_enhancedcore.diagnostic.iv_output_rejected");

        private final String translation;
        State(String translation) { this.translation = translation; }
    }

    public record Result(long inserted, long remaining, long storable, double availableAE, State state) {
        public Component detail(AEKey key) {
            if (state == State.COMPLETE) return Component.empty();
            return Component.translatable(state.translation,
                    key.getDisplayName(), key.formatAmount(remaining, AmountFormat.FULL));
        }
    }

    public static Result transfer(IEnergySource energy, MEStorage storage, AEKey key, long offered,
                                  IActionSource source) {
        if (offered <= 0) throw new IllegalArgumentException("Nonpositive output offer");
        long inserted = checked(StorageHelper.poweredInsert(energy, storage, key, offered, source), offered);
        long remaining = offered - inserted;
        if (remaining == 0) return new Result(inserted, 0, 0, 0, State.COMPLETE);
        if (inserted > 0) return new Result(inserted, remaining, 0, 0, State.PROGRESS);

        long storable = checked(storage.insert(key, offered, Actionable.SIMULATE, source), offered);
        if (storable == 0) {
            // Some external handlers narrow a request to int. Distinguish their size
            // rejection from genuinely full/filtered storage; never execute this probe.
            long small = Math.min(offered, Integer.MAX_VALUE);
            long smallAccepted = small < offered ? checked(storage.insert(key, small, Actionable.SIMULATE, source), small) : 0;
            if (smallAccepted > 0) return new Result(0, remaining, smallAccepted, 0, State.REQUEST_LIMIT);
            return new Result(0, remaining, 0, 0, State.STORAGE);
        }
        double amountPerOperation = Math.max(1, key.getAmountPerOperation());
        double available = energy.extractAEPower(storable / amountPerOperation,
                Actionable.SIMULATE, PowerMultiplier.CONFIG);
        // Same unit conversion and rounding used by AE2 15.4.10 poweredInsert.
        long powered = Math.min(storable, (long)(available * amountPerOperation + 0.9));
        State state = !Double.isNaN(available) && powered <= 0 ? State.POWER : State.REJECTED;
        return new Result(0, remaining, storable, available, state);
    }

    private static long checked(long accepted, long offered) {
        if (accepted < 0 || accepted > offered) throw new IllegalStateException("Invalid AE insertion amount");
        return accepted;
    }
}
