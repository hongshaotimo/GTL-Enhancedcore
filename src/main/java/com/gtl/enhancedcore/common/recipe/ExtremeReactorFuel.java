package com.gtl.enhancedcore.common.recipe;

/**
 * The reactor's fuel cycle follows the advanced hyper reactor: 1 mB for 200 ticks.
 * Recipe registration and grid settlement use these same fixed values.
 */
public final class ExtremeReactorFuel {
    public static final String FLUID_ID = "gtceu:concentration_mixing_hyper_fuel_2";
    /** Fuel consumed per recipe (mB). */
    public static final long AMOUNT_MB = 1L;
    /** Recipe duration (ticks). */
    public static final int DURATION_TICKS = 200;
    /** Generated energy per tick (100x the original 5E12 EU/t baseline). */
    public static final long GENERATION_EUT = 500_000_000_000_000L;
    /** Grid settlement interval (ticks). */
    public static final int GRID_FLUSH_INTERVAL = 20;

    private ExtremeReactorFuel() {}
}
