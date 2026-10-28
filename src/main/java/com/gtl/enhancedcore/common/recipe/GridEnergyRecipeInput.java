package com.gtl.enhancedcore.common.recipe;

import java.math.BigInteger;

/** Optional grid-hatch bridge; querying it must not fill or consume the hatch's local buffer. */
public interface GridEnergyRecipeInput {
    boolean gridEnergyConnected();
    BigInteger gridEnergyAvailable();
    boolean gridEnergyTransfer(BigInteger delta);

    /** Direct network terminals have no shared physical EU/t limit across isolated orders. */
    default boolean gridEnergyUnboundedPower() { return false; }

    default boolean gridEnergyPay(long amount) {
        return gridEnergyConnected() && GridRecipePayment.pay(BigInteger.valueOf(amount), false,
                this::gridEnergyAvailable, this::gridEnergyTransfer);
    }
}
