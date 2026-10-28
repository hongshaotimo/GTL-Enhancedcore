package com.gtl.enhancedcore.common.config;

import com.gtl.enhancedcore.common.data.machine.InfinitySingularityRecipeLoader;
import com.gtl.enhancedcore.network.ConfigFilePackets;
import com.gtl.enhancedcore.network.GTLEnhancedcoreNetworkHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.PacketDistributor;

/** Operator-only, bounded file sessions. All reads, validation, writes and reloads start on the server thread. */
public final class ServerConfigEditor {
    private static final Set<MinecraftServer> RELOADING = java.util.Collections.synchronizedSet(
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>()));
    private ServerConfigEditor() {}
    public static String digest(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    public static boolean authorized(boolean owner, boolean operator) { return owner || operator; }
    public static boolean matches(String current, String expected) { return digest(current).equals(expected); }
    public static void handle(ServerPlayer player, ConfigFilePackets.Request request) {
        if (player == null) return;
        var server = player.getServer();
        if (server == null) return;
        try {
            if (!authorized(server.isSingleplayerOwner(player.getGameProfile()), player.hasPermissions(2)))
                throw new IllegalArgumentException("config.gtl_enhancedcore.operator_required");
            var kind = request.kind();
            if (!kind.server) throw new IllegalArgumentException("Invalid server configuration");
            if (RELOADING.contains(server)) throw new IllegalStateException("config.gtl_enhancedcore.reload_pending");
            Path path = FMLPaths.CONFIGDIR.get().resolve("GTL-Enhancedcore").resolve(kind.file);
            if (kind == ConfigCatalog.SINGULARITY) InfinitySingularityRecipeLoader.ensureDefaultConfig();
            if (kind == ConfigCatalog.DISMANTLE) TerminalDismantleConfig.ensureDefaultConfig(path);
            if (kind == ConfigCatalog.GALAXY) GalaxyConnectionConfig.ensureDefaultConfig(path);
            var file = new EditableConfigFile(path);
            String current = file.text();
            if (current.length() > 8_000) throw new IllegalArgumentException("Configuration too large");
            if (!request.save()) { reply(player, request, current, "", false); return; }
            if (!matches(current, request.expected())) throw new IllegalStateException("config.gtl_enhancedcore.file_conflict");
            kind.validate(request.text());
            if (kind == ConfigCatalog.SINGULARITY) InfinitySingularityRecipeLoader.validate(request.text());
            file.save(request.text());
            if (kind == ConfigCatalog.COMMON) applyCommon(request.text());
            if (kind != ConfigCatalog.SINGULARITY) { reply(player, request, request.text(), "", true); return; }
            RELOADING.add(server);
            try {
                server.reloadResources(server.getPackRepository().getSelectedIds()).whenComplete((ignored, error) ->
                        server.execute(() -> {
                            RELOADING.remove(server);
                            if (error == null) {
                                InfinityCellConfigInjector.inject();
                                reply(player, request, request.text(), "", true);
                            } else {
                                // Disk rollback is conditional: never overwrite a newer external edit.
                                try {
                                    if (Files.readString(path).equals(request.text())) ConfigFiles.writeAtomically(path, current);
                                } catch (Exception rollback) { error.addSuppressed(rollback); }
                                com.gtl.enhancedcore.GTLEnhancedcore.LOGGER.error("Configuration recipe reload failed; restored original file where unchanged", error);
                                reply(player, request, current, "config.gtl_enhancedcore.reload_failed", false);
                            }
                        }));
            } catch (Exception error) {
                RELOADING.remove(server);
                if (Files.readString(path).equals(request.text())) ConfigFiles.writeAtomically(path, current);
                throw error;
            }
        } catch (Exception error) {
            reply(player, request, "", error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage(), false);
        }
    }
    public static void applyCommon(String text) {
        var values = ConfigCatalog.claimSettings(text);
        GTLConfig.CLAIM_REPLACEMENT_ENABLED.set(values.enabled());
        GTLConfig.CLAIM_REPLACEMENT_INTERVAL_TICKS.set(values.interval());
    }
    private static void reply(ServerPlayer player, ConfigFilePackets.Request request, String text, String error, boolean saved) {
        if (player.hasDisconnected()) return;
        GTLEnhancedcoreNetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new ConfigFilePackets.Response(request.id(), text, error.substring(0, Math.min(2048, error.length())), saved));
    }
}
