package com.gtl.enhancedcore;

import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pure JDK collection-mechanism checks, not a mock of AbilityHandler or a runtime AT/install test.
 * No Minecraft/Forge classes are loaded. All worker waits are bounded.
 */
final class FlightStateConcurrencyRegression {
    private static final AtomicInteger ASSERTIONS = new AtomicInteger();
    private static final int CYCLES = 128;

    private FlightStateConcurrencyRegression() {}

    static int run() throws Exception {
        ASSERTIONS.set(0);
        ExecutorService workers = Executors.newFixedThreadPool(2, task -> {
            Thread worker = new Thread(task, "flight-state-regression");
            worker.setDaemon(true);
            return worker;
        });
        try {
            originalMapRace(workers);
            preservesOriginalEntries();
            separateLogicalSides(workers);
            sameKeyInitializedOnce(workers);
            callbackSemantics();
            return ASSERTIONS.get();
        } finally {
            workers.shutdownNow();
            if (!workers.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Flight-state test workers did not terminate");
            }
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("FlightStateConcurrencyRegression: " + run()
                + " assertions passed (JDK mechanism only; runtime AT/install not verified)");
    }

    private static void originalMapRace(ExecutorService workers) throws Exception {
        Map<String, Object> original = new HashMap<>();
        Object seed = new Object();
        Object clientValue = new Object();
        Object serverValue = new Object();
        original.put("seed", seed);
        CountDownLatch clientComputing = new CountDownLatch(1);
        CountDownLatch serverFinished = new CountDownLatch(1);

        // Force the second key's insertion between the first compute's modCount capture and check.
        var client = workers.submit(() -> {
            try {
                original.computeIfAbsent("player:true", key -> {
                    clientComputing.countDown();
                    await(serverFinished);
                    return clientValue;
                });
                return false;
            } catch (ConcurrentModificationException expected) {
                return true;
            }
        });
        var server = workers.submit(() -> {
            try {
                await(clientComputing);
                return original.computeIfAbsent("player:false", key -> serverValue);
            } finally {
                serverFinished.countDown();
            }
        });
        check(client.get(5, TimeUnit.SECONDS), "original HashMap deterministically throws CME");
        check(server.get(5, TimeUnit.SECONDS) == serverValue, "other side's insertion completed");
        check(original.get("seed") == seed, "original seed identity survives the race");
        check(original.get("player:false") == serverValue, "original writer value is retained");
        check(!original.containsKey("player:true"), "failed compute did not insert its value");
    }

    private static void preservesOriginalEntries() {
        Map<String, Object> original = new HashMap<>();
        Object clientValue = new Object();
        Object serverValue = new Object();
        Object nullKeyValue = new Object();
        original.put("player:true", clientValue);
        original.put("player:false", serverValue);
        original.put(null, nullKeyValue);
        original.put("null-value", null);
        Map<String, Object> wrapped = Collections.synchronizedMap(original);
        check(wrapped != original && wrapped.size() == 4, "wrapper retains all original entries");
        check(wrapped.get("player:true") == clientValue, "client value identity retained");
        check(wrapped.get("player:false") == serverValue, "server value identity retained");
        check(wrapped.get(null) == nullKeyValue, "HashMap null-key semantics retained");
        check(wrapped.containsKey("null-value") && wrapped.get("null-value") == null,
                "HashMap null-value semantics retained");
        check(wrapped.computeIfAbsent("player:true", key -> {
            throw new AssertionError("existing value must not be recreated");
        }) == clientValue, "existing value bypasses initialization");
        check(wrapped.remove("player:false") == serverValue, "remove returns original identity");
        check(!original.containsKey("player:false"), "wrapper uses original backing map");
        check(wrapped.put("player:true", serverValue) == clientValue, "put returns previous identity");
        check(original.get("player:true") == serverValue, "wrapper did not copy the backing map");
    }

    private static void separateLogicalSides(ExecutorService workers) throws Exception {
        Map<String, Object> original = new HashMap<>();
        Object seed = new Object();
        original.put("seed", seed);
        Map<String, Object> wrapped = Collections.synchronizedMap(original);
        CountDownLatch start = new CountDownLatch(1);
        var client = workers.submit(() -> cycle(wrapped, "player:true", start));
        var server = workers.submit(() -> cycle(wrapped, "player:false", start));
        start.countDown();
        Object clientValue = client.get(5, TimeUnit.SECONDS);
        Object serverValue = server.get(5, TimeUnit.SECONDS);
        check(clientValue != serverValue, "logical sides retain different state objects");
        check(wrapped.get("player:true") == clientValue, "client final identity retained");
        check(wrapped.get("player:false") == serverValue, "server final identity retained");
        check(wrapped.get("seed") == seed && wrapped.size() == 3, "parallel operations preserve old entries");
    }

    private static Object cycle(Map<String, Object> map, String key, CountDownLatch start) {
        await(start);
        Object last = null;
        for (int index = 0; index < CYCLES; index++) {
            Object next = new Object();
            check(map.remove(key) == last, "side removes only its own previous state");
            last = map.computeIfAbsent(key, ignored -> {
                check(Thread.holdsLock(map), "synchronized compute holds the wrapper monitor");
                return next;
            });
            check(last == next && map.get(key) == next, "side reads its own initialized state");
        }
        return last;
    }

    private static void sameKeyInitializedOnce(ExecutorService workers) throws Exception {
        Map<String, Object> wrapped = Collections.synchronizedMap(new HashMap<>());
        AtomicInteger initializations = new AtomicInteger();
        CountDownLatch firstComputing = new CountDownLatch(1);
        CountDownLatch secondCalling = new CountDownLatch(1);
        Object value = new Object();
        var first = workers.submit(() -> wrapped.computeIfAbsent("shared", key -> {
            initializations.incrementAndGet();
            firstComputing.countDown();
            await(secondCalling);
            check(Thread.holdsLock(wrapped), "first initializer owns the wrapper monitor");
            return value;
        }));
        var second = workers.submit(() -> {
            await(firstComputing);
            // Signal before acquiring the monitor; never wait for a competing callback while holding it.
            secondCalling.countDown();
            return wrapped.computeIfAbsent("shared", key -> {
                initializations.incrementAndGet();
                return new Object();
            });
        });
        check(first.get(5, TimeUnit.SECONDS) == value, "first compute returns its initialized value");
        check(second.get(5, TimeUnit.SECONDS) == value, "same-key contender receives the same identity");
        check(initializations.get() == 1, "same key is initialized exactly once");
        check(wrapped.size() == 1 && wrapped.get("shared") == value, "single same-key mapping retained");
    }

    private static void callbackSemantics() {
        Map<String, Object> wrapped = Collections.synchronizedMap(new HashMap<>());
        Object value = new Object();
        wrapped.put("seed", value);
        check(wrapped.computeIfAbsent("read", key -> wrapped.get("seed")) == value,
                "same-thread read reentry does not deadlock");
        RuntimeException sentinel = new RuntimeException("mapping callback sentinel");
        try {
            wrapped.computeIfAbsent("throws", key -> { throw sentinel; });
            throw new AssertionError("callback exception was swallowed");
        } catch (RuntimeException caught) {
            check(caught == sentinel, "original callback exception propagates unchanged");
        }
        check(!wrapped.containsKey("throws"), "failed callback did not insert a value");
        try {
            wrapped.computeIfAbsent("outer", key -> {
                wrapped.put("inner", value);
                return value;
            });
            throw new AssertionError("same-thread structural reentry must still throw CME");
        } catch (ConcurrentModificationException expected) {
            check(wrapped.get("inner") == value && !wrapped.containsKey("outer"),
                    "wrapper does not hide same-thread structural reentry");
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("Flight-state latch timed out");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Flight-state worker interrupted", interrupted);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        ASSERTIONS.incrementAndGet();
    }
}
