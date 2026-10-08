package com.gtl.enhancedcore.common.util;

import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class BlockEntityRemovalSnapshot {
    private BlockEntityRemovalSnapshot() {}

    public static <Entity> Collection<Entity> forUnload(Map<?, Entity> entities,
            Consumer<List<Entity>> completed) {
        return new UnloadValues<>(entities.values(), completed);
    }

    public static <Entity> Collection<Entity> forRemoval(Map<?, Entity> entities,
            List<Entity> unloaded) {
        List<Entity> snapshot = new ArrayList<>(unloaded);
        Map<Entity, Boolean> included = new IdentityHashMap<>();
        for (Entity entity : unloaded) included.put(entity, Boolean.TRUE);
        for (Entity entity : new ArrayList<>(entities.values())) {
            if (included.put(entity, Boolean.TRUE) == null) snapshot.add(entity);
        }
        return Collections.unmodifiableList(snapshot);
    }

    private static final class UnloadValues<Entity> extends AbstractCollection<Entity> {
        private final List<Entity> snapshot;
        private final Consumer<List<Entity>> completed;

        private UnloadValues(Collection<Entity> values, Consumer<List<Entity>> completed) {
            this.snapshot = Collections.unmodifiableList(new ArrayList<>(values));
            this.completed = completed;
        }

        @Override
        public Iterator<Entity> iterator() {
            return snapshot.iterator();
        }

        @Override
        public int size() {
            return snapshot.size();
        }

        @Override
        public void forEach(Consumer<? super Entity> action) {
            snapshot.forEach(action);
            completed.accept(snapshot);
        }
    }
}
