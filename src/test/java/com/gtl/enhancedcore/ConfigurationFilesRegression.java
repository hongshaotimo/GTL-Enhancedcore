package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.config.*;
import com.gtl.enhancedcore.network.ConfigFilePackets;
import io.netty.buffer.Unpooled;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import net.minecraft.network.FriendlyByteBuf;

/** Verifies real migration files, validation boundaries, conflict tokens and packet round trips. */
final class ConfigurationFilesRegression {
    private static int assertions;
    static int run() throws Exception {
        assertions = 0;
        Path temporary = Files.createTempDirectory("enhancedcore-config-files-");
        try {
            Path directory = temporary.resolve("GTL-Enhancedcore"); Files.createDirectories(directory);
            Files.writeString(directory.resolve("foreign_machines.properties"), "replaceOriginalStructures=false\nenhanceOriginalMachines=true\n");
            Files.writeString(directory.resolve("performance.properties"), "autoThreads=false\nbackgroundThreads=7\nbuilderThreads=3\n");
            Files.writeString(directory.resolve("client.toml"), "old preview values");
            Files.writeString(directory.resolve("chronal-altars-client.toml"), "old altar values");
            Files.writeString(directory.resolve("gtl_enhancedcore_recipe_type_sets.json"), "old groups");
            Files.writeString(directory.resolve("gtl_enhancedcore_ae_pins.json"), "[]");
            Files.writeString(directory.resolve("ae_pins.json"), "[{\"unique\":42}]");
            Files.writeString(directory.resolve("item_tooltips.json"), "preserve user tips exactly");
            var moved = ConfigConsolidation.consolidate(directory);
            check(moved.size() == 6, "Only the six known retired files are moved");
            var settings = StartupConfig.read(directory.resolve(StartupConfig.FILE));
            check(settings.equals(new StartupConfig.Settings(false, true, false, 7, 3, 0)), "Machine and worker settings survive consolidation independently");
            check(Files.readString(directory.resolve("item_tooltips.json")).equals("preserve user tips exactly"), "User Tips are byte-preserved");
            check(Files.readString(directory.resolve("ae_pins.json")).equals("[{\"unique\":42}]"), "Empty duplicate does not overwrite current pins");
            check(ConfigConsolidation.consolidate(directory).isEmpty(), "Consolidation is idempotent");
            try (var backups = Files.walk(temporary.resolve("GTL-Enhancedcore-backups"))) {
                check(backups.filter(Files::isRegularFile).count() == 6, "Every retired file has a recoverable copy");
            }
            Files.writeString(directory.resolve("gtl_enhancedcore_ae_pins.json"), "[{\"unique\":43}]");
            check(ConfigConsolidation.consolidate(directory).isEmpty(), "Conflicting nonempty duplicate pins are preserved");
            for (boolean structure : new boolean[]{false, true}) for (boolean enhance : new boolean[]{false, true}) {
                var sample = new StartupConfig.Settings(structure, enhance, true, 0, 255, 8);
                check(StartupConfig.parse(StartupConfig.serialize(sample)).equals(sample), "Every independent combination round-trips");
            }
            String broken = "backgroundThreads=256\n";
            invalid(() -> StartupConfig.parse(broken));
            invalid(() -> StartupConfig.parse("autoThreads=perhaps"));
            invalid(() -> StartupConfig.parse("mapRasterThreads=9"));
            String common = "[claim_replacement_terminal]\nenabled=false\nreplace_interval_ticks=1\n";
            check(ConfigCatalog.claimSettings(common).equals(new ConfigCatalog.ClaimSettings(false, 1)), "Common editor reads exact live values");
            invalid(() -> ConfigCatalog.COMMON.validate(common.replace("=1", "=0")));
            invalid(() -> ConfigCatalog.COMMON.validate(common.replace("=1", "=201")));
            invalid(() -> ConfigCatalog.COMMON.validate(common.replace("=1", "=1.5")));
            invalid(() -> ConfigCatalog.COPY_KEYS.validate("{\"copyItemId\":\"false\"}"));
            invalid(() -> ConfigCatalog.CHANGELOG.validate("{\"enabled\":false,\"version\":\"1\",\"title\":\"x\",\"sections\":[{\"heading\":\"h\",\"lines\":[42]}]}"));
            ConfigCatalog.CHANGELOG.validate("{\"enabled\":true,\"version\":\"3.5.3\",\"title\":\"配置公告\",\"sections\":[]}"); assertions++;
            ConfigCatalog.SEEN.validate(""); assertions++;
            invalid(() -> ConfigCatalog.SEEN.validate("1\n2"));
            ConfigCatalog.PINS.validate("[]"); assertions++;
            check(!ServerConfigEditor.authorized(false, false), "Ordinary remote players cannot edit server rules");
            check(ServerConfigEditor.authorized(true, false) && ServerConfigEditor.authorized(false, true), "Singleplayer owner or operator can edit");
            check(ServerConfigEditor.matches(common, ServerConfigEditor.digest(common)), "Matching snapshot authorizes a save");
            check(!ServerConfigEditor.matches(common + "# changed", ServerConfigEditor.digest(common)), "Concurrent edit blocks a stale save");
            var request = new ConfigFilePackets.Request(71, ConfigCatalog.COMMON, true, ServerConfigEditor.digest(common), common);
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try { ConfigFilePackets.Request.encode(request, buffer); check(ConfigFilePackets.Request.decode(buffer).equals(request), "Request codec preserves type, text and conflict token"); }
            finally { buffer.release(); }
            check(ConfigCatalog.PREVIEW.ordinal() == 8 && ConfigCatalog.DISMANTLE.ordinal() == 9,
                    "Appending dismantle rules preserves existing network config identifiers");
            check(ConfigCatalog.DISMANTLE.server, "Dismantle rules use the operator-only server path");
            String dismantle = TerminalDismantleConfig.defaultText();
            ConfigCatalog.DISMANTLE.validate(dismantle); assertions++;
            invalid(() -> ConfigCatalog.DISMANTLE.validate("maxRemovalsPerTick=0"));
            var dismantleRequest = new ConfigFilePackets.Request(72, ConfigCatalog.DISMANTLE, true,
                    ServerConfigEditor.digest(dismantle), dismantle);
            buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                ConfigFilePackets.Request.encode(dismantleRequest, buffer);
                check(ConfigFilePackets.Request.decode(buffer).equals(dismantleRequest),
                        "New dismantle configuration round-trips through the bounded server editor protocol");
            } finally { buffer.release(); }
            var response = new ConfigFilePackets.Response(71, common, "", true);
            buffer = new FriendlyByteBuf(Unpooled.buffer());
            try { ConfigFilePackets.Response.encode(response, buffer); check(ConfigFilePackets.Response.decode(buffer).equals(response), "Response codec confirms the matching request"); }
            finally { buffer.release(); }
        } finally {
            try (var files = Files.walk(temporary)) {
                for (Path path : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
        return assertions;
    }
    private static void invalid(Runnable operation) {
        try { operation.run(); throw new AssertionError("Expected invalid configuration to be rejected"); }
        catch (IllegalArgumentException expected) { assertions++; }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); assertions++; }
}
