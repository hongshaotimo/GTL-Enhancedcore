package com.gtl.enhancedcore.common.structure;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;

/** Lossless, dimension-checked storage for generated multiblock aisles. */
public final class StructureData {
    private final String[][] aisles;

    private StructureData(String[][] aisles) {
        this.aisles = aisles;
    }

    public static StructureData read(InputStream input) throws IOException {
        if (input == null) throw new IOException("Missing structure resource");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new GZIPInputStream(input), StandardCharsets.US_ASCII))) {
            String header = reader.readLine();
            if (header == null) throw new IOException("Missing structure dimensions");
            String[] dimensions = header.split(" ");
            if (dimensions.length != 3) throw new IOException("Invalid structure dimensions: " + header);
            int width, height, depth;
            try {
                width = Integer.parseInt(dimensions[0]);
                height = Integer.parseInt(dimensions[1]);
                depth = Integer.parseInt(dimensions[2]);
            } catch (NumberFormatException e) {
                throw new IOException("Invalid structure dimensions: " + header, e);
            }
            if (width <= 0 || height <= 0 || depth <= 0 || width > 512 || height > 512 || depth > 512
                    || (long) width * height * depth > 45_000_000L) {
                throw new IOException("Structure dimensions exceed supported bounds: " + header);
            }
            String[][] aisles = new String[depth][height];
            for (int z = 0; z < depth; z++) {
                for (int y = 0; y < height; y++) {
                    String row = reader.readLine();
                    if (row == null || row.length() != width) {
                        throw new IOException("Invalid structure row at aisle " + z + ", row " + y);
                    }
                    aisles[z][y] = row;
                }
            }
            if (reader.readLine() != null) throw new IOException("Unexpected trailing structure data");
            return new StructureData(aisles);
        }
    }

    /** Each pattern builder owns its arrays; cached source data cannot be mutated by a caller. */
    public void forEachAisle(Consumer<String[]> consumer) {
        for (String[] aisle : aisles) consumer.accept(aisle.clone());
    }

}
