package com.gtl.enhancedcore;

import com.google.gson.JsonParser;
import com.gtl.enhancedcore.client.cloud.CloudTerminalViewState;
import java.nio.file.Files;

public final class CloudViewCacheRegression {
    public static void main(String[] args) throws Exception { System.out.println("Cloud view cache assertions: " + run()); }
    public static int run() {
        int checks = 0;
        try {
            var directory = Files.createTempDirectory("enhancedcore-view-cache-");
            var file = directory.resolve("views.json");
            try {
                var initial = new CloudTerminalViewState(file);
                for (int i = 0; i < 100; i++) initial.saveDeferred("server/player/terminal",
                        new CloudTerminalViewState.View("library", "iron " + i, "gold"));
                var reopened = new CloudTerminalViewState(file);
                if (!reopened.load("server/player/terminal").input().equals("iron 99"))
                    throw new AssertionError("Immediate reopen lost deferred query"); checks++;
                if (!reopened.load("other-server/player/terminal").input().isEmpty())
                    throw new AssertionError("Shared file cache leaked endpoint scope"); checks++;
                CloudTerminalViewState.flushPending();
                var saved = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                if (saved.size() != 2 || !saved.toString().contains("iron 99"))
                    throw new AssertionError("Merged writes lost latest/library preference"); checks++;
                Object firstStore = store(initial), secondStore = store(reopened);
                if (firstStore != secondStore) throw new AssertionError("Reopen reparses a second Store"); checks++;
                for (int i = 0; i < 140; i++) reopened.saveDeferred("scope-" + i,
                        new CloudTerminalViewState.View("", "query", ""));
                CloudTerminalViewState.flushPending();
                if (JsonParser.parseString(Files.readString(file)).getAsJsonObject().size() != 128)
                    throw new AssertionError("Preference rows not bounded"); checks++;
                // Force hot-cache eviction while the first screen/store remains live.
                for (int i = 0; i < 10; i++) new CloudTerminalViewState(directory.resolve("other-" + i + ".json"));
                var afterEviction = new CloudTerminalViewState(file);
                if (store(afterEviction) != firstStore) throw new AssertionError("Live evicted path acquired competing Store"); checks++;
                afterEviction.saveDeferred("after-eviction", new CloudTerminalViewState.View("", "latest", ""));
                if (!initial.load("after-eviction").input().equals("latest"))
                    throw new AssertionError("Old and reopened live screens disagree after eviction"); checks++;
            } finally {
                CloudTerminalViewState.flushPending();
                Files.deleteIfExists(file); Files.deleteIfExists(directory.resolve("views.json.tmp"));
                Files.deleteIfExists(directory);
            }
        } catch (Exception failure) { throw new AssertionError("Cloud view cache persistence", failure); }
        return checks;
    }
    private static Object store(CloudTerminalViewState value) throws Exception {
        var field = CloudTerminalViewState.class.getDeclaredField("store"); field.setAccessible(true);
        return field.get(value);
    }
}
