package com.gtl.enhancedcore.integration.ae2;

import appeng.blockentity.AEBaseBlockEntity;
import appeng.hooks.ticking.TickHandler;
import appeng.util.ILevelRunnable;
import com.gtl.enhancedcore.GTLEnhancedcore;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

/** AE2 15.4.10 leaves deferred block-entity dirty callbacks queued after shutdown. */
@Mod.EventBusSubscriber(modid = GTLEnhancedcore.MOD_ID)
public final class AeStoppedServerTasks {
    private static final AtomicBoolean WARNED = new AtomicBoolean();
    private static final ClassValue<Capture> CAPTURES = new ClassValue<>() {
        @Override
        protected Capture computeValue(Class<?> type) {
            // Only the upstream AEBaseBlockEntity method-reference shape is understood.
            if (!type.isHidden() || !type.isSynthetic() || type.getNestHost() != AEBaseBlockEntity.class) {
                return new Capture(null);
            }
            Field[] fields = type.getDeclaredFields();
            if (fields.length != 1) return new Capture(null);
            Field capture = fields[0];
            if (!capture.getName().equals("arg$1") || capture.getType() != AEBaseBlockEntity.class
                    || Modifier.isStatic(capture.getModifiers()) || !Modifier.isFinal(capture.getModifiers())
                    || !capture.trySetAccessible()) return new Capture(null);
            return new Capture(capture);
        }
    };

    private AeStoppedServerTasks() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void serverStopped(ServerStoppedEvent event) {
        MinecraftServer departed = event.getServer();
        // Forge posts this event after stopServer/save on the departing server thread,
        // before unpublishing it. Never touch a queue owned by a subsequently started world.
        if (!ModList.get().isLoaded("ae2") || !departed.isStopped() || !departed.isSameThread()
                || ServerLifecycleHooks.getCurrentServer() != departed) return;
        try {
            Field field = QueueAccess.FIELD;
            if (field == null) return;
            Object value = field.get(TickHandler.instance());
            if (!(value instanceof Queue<?> queue) || queue.getClass() != ArrayDeque.class) return;
            int before = queue.size();
            // Bulk removal compacts mixed queues once, without running any callbacks.
            queue.removeIf(task -> belongsTo(task, departed));
            int removed = before - queue.size();
            if (removed > 0) GTLEnhancedcore.LOGGER.info(
                    "[AeStoppedServerTasks] Released {} deferred callbacks for the stopped server", removed);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            warnOnce(failure);
        }
    }

    private static boolean belongsTo(Object task, MinecraftServer departed) {
        if (!(task instanceof ILevelRunnable)) return false;
        Field capture = CAPTURES.get(task.getClass()).field();
        if (capture == null) return false;
        try {
            AEBaseBlockEntity entity = (AEBaseBlockEntity) capture.get(task);
            return entity != null && entity.getLevel() instanceof ServerLevel level && level.getServer() == departed;
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static Field resolveQueue() {
        try {
            Field field = TickHandler.class.getDeclaredField("serverQueue");
            if (field.getType() != Queue.class || Modifier.isStatic(field.getModifiers())
                    || !field.trySetAccessible()) throw new IllegalStateException("Unexpected AE2 serverQueue layout");
            return field;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            warnOnce(failure);
            return null;
        }
    }

    private static void warnOnce(Throwable failure) {
        if (WARNED.compareAndSet(false, true)) GTLEnhancedcore.LOGGER.warn(
                "[AeStoppedServerTasks] Could not release stopped-world callbacks", failure);
    }

    private record Capture(Field field) {}

    private static final class QueueAccess {
        private static final Field FIELD = resolveQueue();
    }
}
