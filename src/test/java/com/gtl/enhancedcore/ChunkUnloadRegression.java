package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.util.BlockEntityRemovalSnapshot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class ChunkUnloadRegression {
    private static int assertions;

    private ChunkUnloadRegression() {}

    static int run() {
        assertions = 0;
        originalRemovalReentrancy();
        for (int size = 1; size <= 64; size++) {
            selfAndNeighborRemoval(size);
            firstPhaseRemovalAndReplacement(size);
        }
        nestedUnload(false);
        nestedUnload(true);
        callbackFailures(false);
        callbackFailures(true);
        emptyAndRepeatedUnload();
        immutableSnapshots();
        return assertions;
    }

    public static void main(String[] args) {
        System.out.println("ChunkUnloadRegression: " + run()
                + " assertions passed (JDK mechanism; game Mixin installation is tested separately)");
    }

    private static void originalRemovalReentrancy() {
        Fixture fixture = new Fixture(1);
        Entity original = fixture.originals.get(0);
        original.removal = () -> fixture.lookup(original.position);
        try {
            fixture.entities.values().forEach(Entity::setRemoved);
            throw new AssertionError("Original HashMap.values.forEach must throw CME");
        } catch (ConcurrentModificationException expected) {
            check(original.removed, "setRemoved runs before the reentrant lookup");
            check(fixture.entities.isEmpty(), "lookup of the removed entity deletes its map entry");
            check(expected.getStackTrace()[0].getClassName().equals("java.util.HashMap$Values"),
                    "original failure is the actual HashMap values traversal");
        }
    }

    private static void selfAndNeighborRemoval(int size) {
        Fixture fixture = new Fixture(size);
        Map<Integer, Entity> originalMap = fixture.entities;
        for (Entity entity : fixture.originals) {
            entity.removal = () -> {
                fixture.lookup(entity.position);
                fixture.entities.remove((entity.position + 1) % size);
            };
        }
        fixture.clear();
        check(fixture.entities == originalMap, "the original map object is not replaced");
        verifyCompleted(fixture, 1, 1);
        fixture.clear();
        verifyCompleted(fixture, 1, 1);
        check(fixture.pending == null, "repeat unload retains no entity snapshot");
    }

    private static void firstPhaseRemovalAndReplacement(int size) {
        Fixture fixture = new Fixture(size);
        Entity replacement = new Entity(0);
        Entity first = fixture.originals.get(0);
        first.unload = () -> {
            fixture.entities.clear();
            fixture.entities.put(replacement.position, replacement);
        };
        fixture.clear();
        verifyCompleted(fixture, 1, 1);
        check(replacement.unloadCalls == 0, "new first-phase entries keep original notification semantics");
        check(replacement.removalCalls == 1 && replacement.removed,
                "a first-phase replacement also receives setRemoved");
        check(first.equals(replacement) && first != replacement,
                "replacement is equal but has a distinct entity identity");
    }

    private static void nestedUnload(boolean duringRemoval) {
        Fixture fixture = new Fixture(5);
        boolean[] entered = {false};
        Runnable nested = () -> {
            if (entered[0]) return;
            entered[0] = true;
            fixture.clear();
        };
        if (duringRemoval) fixture.originals.get(0).removal = nested;
        else fixture.originals.get(0).unload = nested;
        fixture.clear();
        check(entered[0], "nested unload really executes");
        verifyCompleted(fixture, 2, 2);
        check(fixture.pending == null, "nested phases do not lose or retain the outer snapshot");
    }

    private static void callbackFailures(boolean duringRemoval) {
        Fixture fixture = new Fixture(5);
        RuntimeException sentinel = new IllegalStateException("callback sentinel");
        Runnable failure = () -> { throw sentinel; };
        if (duringRemoval) fixture.originals.get(0).removal = failure;
        else fixture.originals.get(0).unload = failure;
        try {
            fixture.clear();
            throw new AssertionError("Callback exception must propagate");
        } catch (RuntimeException caught) {
            check(caught == sentinel, "the original callback failure propagates unchanged");
        }
        check(fixture.entities.size() == 5, "failed callbacks do not trigger final map clear");
        check(fixture.tickers.size() == 5, "failed callbacks do not silently clear tickers");
        check(fixture.pending == null, "failed callback retains no pending entity snapshot");
        for (Entity entity : fixture.originals) {
            entity.unload = () -> {};
            entity.removal = () -> {};
        }
        fixture.clear();
        check(fixture.entities.isEmpty() && fixture.tickers.isEmpty(), "a later explicit unload can complete");
        for (Entity entity : fixture.originals) check(entity.removed, "recovery removes every entity");
        for (Ticker ticker : fixture.originalTickers) check(ticker.rebinds == 1, "recovery cleans each ticker once");
    }

    private static void emptyAndRepeatedUnload() {
        Fixture fixture = new Fixture(0);
        for (int cycle = 0; cycle < 128; cycle++) {
            fixture.clear();
            check(fixture.entities.isEmpty() && fixture.tickers.isEmpty(), "empty unload stays empty");
            check(fixture.pending == null, "empty unload retains no snapshot");
            Entity entity = new Entity(cycle);
            Ticker ticker = new Ticker();
            fixture.entities.put(cycle, entity);
            fixture.tickers.put(cycle, ticker);
            fixture.clear();
            check(entity.unloadCalls == 1 && entity.removalCalls == 1 && entity.removed,
                    "reloaded entity receives both callbacks once");
            check(ticker.rebinds == 1, "reloaded ticker is rebound once");
        }
    }

    private static void immutableSnapshots() {
        Fixture fixture = new Fixture(2);
        Collection<Entity> values = BlockEntityRemovalSnapshot.forUnload(fixture.entities,
                unloaded -> fixture.pending = unloaded);
        var iterator = values.iterator();
        iterator.next();
        expectUnsupported(iterator::remove);
        values.forEach(Entity::onChunkUnloaded);
        expectUnsupported(fixture.pending::clear);
        Collection<Entity> removal = BlockEntityRemovalSnapshot.forRemoval(fixture.entities, fixture.pending);
        expectUnsupported(removal::clear);
        check(fixture.entities.size() == 2, "snapshot operations cannot mutate the live entity map");
    }

    private static void verifyCompleted(Fixture fixture, int unloadCalls, int removalCalls) {
        check(fixture.entities.isEmpty(), "original block entity map is cleared");
        check(fixture.tickers.isEmpty(), "original ticker map is cleared");
        for (Entity entity : fixture.originals) {
            check(entity.unloadCalls == unloadCalls, "every original entity receives onChunkUnloaded");
            check(entity.removalCalls == removalCalls, "every original entity receives setRemoved");
            check(entity.removed, "every original entity is marked removed");
        }
        for (Ticker ticker : fixture.originalTickers) check(ticker.rebinds == 1, "each ticker is rebound once");
    }

    private static void expectUnsupported(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Snapshot must be immutable");
        } catch (UnsupportedOperationException expected) {
            check(true, "snapshot rejects structural mutation");
        }
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static final class Fixture {
        final Map<Integer, Entity> entities = new HashMap<>();
        final Map<Integer, Ticker> tickers = new HashMap<>();
        final List<Entity> originals = new ArrayList<>();
        final List<Ticker> originalTickers = new ArrayList<>();
        List<Entity> pending;

        Fixture(int size) {
            for (int index = 0; index < size; index++) {
                Entity entity = new Entity(index);
                Ticker ticker = new Ticker();
                entities.put(index, entity);
                tickers.put(index, ticker);
                originals.add(entity);
                originalTickers.add(ticker);
            }
        }

        Entity lookup(int position) {
            Entity entity = entities.get(position);
            if (entity != null && entity.removed) return entities.remove(position);
            return entity;
        }

        void clear() {
            BlockEntityRemovalSnapshot.forUnload(entities, unloaded -> pending = unloaded)
                    .forEach(Entity::onChunkUnloaded);
            List<Entity> unloaded = pending;
            pending = null;
            BlockEntityRemovalSnapshot.forRemoval(entities, unloaded).forEach(Entity::setRemoved);
            entities.clear();
            tickers.values().forEach(Ticker::rebind);
            tickers.clear();
        }
    }

    private static final class Entity {
        final int position;
        int unloadCalls;
        int removalCalls;
        boolean removed;
        Runnable unload = () -> {};
        Runnable removal = () -> {};

        Entity(int position) {
            this.position = position;
        }

        void onChunkUnloaded() {
            unloadCalls++;
            unload.run();
        }

        void setRemoved() {
            removalCalls++;
            removed = true;
            removal.run();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Entity;
        }

        @Override
        public int hashCode() {
            return 1;
        }
    }

    private static final class Ticker {
        int rebinds;

        void rebind() {
            rebinds++;
        }
    }
}
