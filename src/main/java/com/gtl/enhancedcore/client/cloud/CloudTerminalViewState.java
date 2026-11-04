package com.gtl.enhancedcore.client.cloud;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.lang.ref.WeakReference;
import java.util.HashMap;

/** Small client view preferences only. No stacks, permissions, network or world objects. */
public final class CloudTerminalViewState {
    private static final Gson GSON = new Gson();
    private static final Map<Path, Store> STORES = new LinkedHashMap<>(8, 0.75f, true);
    private static final Map<Path, WeakReference<Store>> LIVE = new HashMap<>();
    private static final ScheduledExecutorService WRITER = Executors.newSingleThreadScheduledExecutor(task -> {
        var thread = new Thread(task, "EnhancedCore cloud view preferences");
        thread.setDaemon(true);
        return thread;
    });
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(CloudTerminalViewState::flushPending,
                "EnhancedCore cloud view preference shutdown"));
    }
    public record View(String library,String input,String output) {
        public View {
            library=library==null?"":library;
            input=bounded(input);output=bounded(output);
        }
        private static String bounded(String value) {
            if(value==null)return "";value=value.replace('\n',' ').replace('\r',' ');
            return value.substring(0,Math.min(96,value.length()));
        }
    }
    private final Store store;
    public CloudTerminalViewState(Path file) { store = acquire(file.toAbsolutePath().normalize()); }

    private static synchronized Store acquire(Path file) {
        Store result = STORES.get(file);
        if (result != null) return result;
        LIVE.values().removeIf(reference -> reference.get() == null);
        var live = LIVE.get(file);
        result = live == null ? null : live.get();
        if (result == null) result = new Store(file);
        LIVE.put(file, new WeakReference<>(result));
        STORES.put(file, result);
        if (STORES.size() > 8) {
            var iterator = STORES.entrySet().iterator();
            Store oldest = iterator.next().getValue();
            iterator.remove();
            // Only values/paths leave the cache, never a screen, player, menu or world.
            WRITER.execute(oldest::flush);
        }
        return result;
    }

    /** Called during client setup so the one cold disk read happens before a terminal opens. */
    public static void preload(Path file) { WRITER.execute(() -> new CloudTerminalViewState(file)); }
    public View load(String scope) { return store.read(key(scope)); }
    public View loadForLibrary(String scope,String library) { return store.read(key(scope+"\n"+library)); }

    /** Synchronous persistence for tools/tests; interactive screens use saveDeferred. */
    public void save(String scope, View view) {
        store.update(scope, view);
        store.flush();
    }

    /** Changes are immediately visible on reopen; one worker coalesces disk writes. */
    public void saveDeferred(String scope, View view) {
        if (store.update(scope, view)) store.schedule();
    }

    /** Orderly JVM shutdown and explicit verification boundary; never called from rendering. */
    public static void flushPending() {
        Store[] snapshot;
        synchronized (CloudTerminalViewState.class) {
            snapshot = LIVE.values().stream().map(WeakReference::get).filter(java.util.Objects::nonNull).toArray(Store[]::new);
        }
        for (Store store : snapshot) store.flush();
    }

    private static final class Store {
        final Path file;
        final ReentrantLock io = new ReentrantLock();
        JsonObject values = new JsonObject();
        long revision, written;
        boolean scheduled;
        Store(Path file) {
            this.file = file;
            try {
                if (Files.isRegularFile(file) && Files.size(file) <= 262144)
                    values = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            } catch (java.io.IOException | RuntimeException ignored) { values = new JsonObject(); }
        }
        synchronized View read(String key) {
            try {
                var row = values.getAsJsonObject(key);
                if (row != null) return new View(row.get("library").getAsString(),
                        row.get("input").getAsString(), row.get("output").getAsString());
            } catch (RuntimeException ignored) { }
            return new View("", "", "");
        }
        synchronized boolean update(String scope, View view) {
            String scopeKey = key(scope), libraryKey = key(scope+"\n"+view.library);
            var row = GSON.toJsonTree(view).getAsJsonObject();
            if (row.equals(values.get(scopeKey)) && (view.library.isEmpty() || row.equals(values.get(libraryKey)))) return false;
            values.add(scopeKey, row);
            if (!view.library.isEmpty()) values.add(libraryKey, row.deepCopy());
            while (values.size() > 128) values.remove(values.keySet().iterator().next());
            revision++;
            return true;
        }
        synchronized void schedule() {
            if (scheduled) return;
            scheduled = true;
            WRITER.schedule(() -> {
                long requested;
                synchronized (this) { requested = revision; }
                try { flush(); }
                finally {
                    synchronized (this) {
                        scheduled = false;
                        // A failed write is retried on a later edit or shutdown, not every 250ms.
                        if (revision > requested) schedule();
                    }
                }
            }, 250, TimeUnit.MILLISECONDS);
        }
        void flush() {
            io.lock();
            try {
                String text;
                long snapshot;
                synchronized (this) {
                    if (revision == written) return;
                    text = GSON.toJson(values);
                    snapshot = revision;
                }
                try {
                    Files.createDirectories(file.getParent());
                    Path temporary = file.resolveSibling(file.getFileName()+".tmp");
                    Files.writeString(temporary, text, StandardCharsets.UTF_8);
                    try { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
                    catch (java.nio.file.AtomicMoveNotSupportedException ignored) { Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING); }
                    synchronized (this) { written = snapshot; }
                } catch (java.io.IOException ignored) { }
            } finally { io.unlock(); }
        }
    }
    private static String key(String scope) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(scope.getBytes(StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
