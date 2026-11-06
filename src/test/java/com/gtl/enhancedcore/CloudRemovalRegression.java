package com.gtl.enhancedcore;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPInputStream;

/** The withdrawn system must be absent from source and the actual main build outputs. */
final class CloudRemovalRegression {
    private static final List<String> REMOVED = List.of(
            "common/cloud/", "common.cloud.", "client/cloud/", "client.cloud.",
            "textures/gui/cloud/", "block/machine/cloud/",
            "cloud_pattern_", "gtl_enhancedcore.cloud", "CloudPattern", "CloudMachineIdentity", "CloudProcessingTarget",
            "CloudOrderRecovery", "CloudNative", "CloudPort", "CloudBuffer",
            "中央样板库", "云端样板", "云端总成", "云仓", "云总成", "central library");

    static int run() throws Exception {
        int checks = 0;
        for (String directory : List.of("src/main/java", "src/main/resources",
                "build/classes/java/main", "build/resources/main")) {
            Path root = Path.of(directory);
            if (!Files.isDirectory(root)) throw new AssertionError("Missing main build input/output: " + directory);
            try (var files = Files.walk(root)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    String name = root.relativize(file).toString().replace('\\', '/');
                    // ASCII identifiers survive both UTF-8 resources and class-file constant pools.
                    byte[] data;
                    if (name.endsWith(".json.gz")) {
                        try (var stream = new GZIPInputStream(Files.newInputStream(file))) {
                            data = stream.readAllBytes();
                        }
                    } else data = Files.readAllBytes(file);
                    String contents = new String(data, StandardCharsets.ISO_8859_1);
                    for (String removed : REMOVED) {
                        String encoded = new String(removed.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
                        if (name.contains(removed) || contents.contains(encoded))
                            throw new AssertionError("Withdrawn system reference " + removed + " in " + file);
                        checks++;
                    }
                }
            }
        }
        return checks;
    }
}
