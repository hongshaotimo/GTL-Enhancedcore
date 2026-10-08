package com.gtl.enhancedcore.common.structure;

import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Loads immutable geometry once and creates a fresh predicate builder for every definition. */
public final class StructurePatterns {
    private static final Map<String, StructureData> CACHE = new ConcurrentHashMap<>();

    private StructurePatterns() {}

    public static FactoryBlockPattern start(String resource) {
        FactoryBlockPattern builder = FactoryBlockPattern.start();
        data(resource).forEachAisle(builder::aisle);
        return builder;
    }

    static StructureData data(String resource) {
        return CACHE.computeIfAbsent(resource, path -> {
            try {
                return StructureData.read(StructurePatterns.class.getResourceAsStream(path));
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot load multiblock geometry " + path, e);
            }
        });
    }
}
