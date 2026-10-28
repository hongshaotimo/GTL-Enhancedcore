package com.gtl.enhancedcore.common.structure;

import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;

/** Reviewed 2026-10-27 schematic, width x height x depth: 47 x 55 x 48. */
public final class HighFluxBreederReactorStructure {
    private HighFluxBreederReactorStructure() {}

    public static FactoryBlockPattern create() {
        return ReviewedStructurePalette.bind(StructurePatterns.start(
                "/data/gtl_enhancedcore/structures/high_flux_breeder_reactor.pattern.gz"),
                "high_flux_breeder_reactor");
    }
}
