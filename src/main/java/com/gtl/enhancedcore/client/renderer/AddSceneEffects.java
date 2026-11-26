package com.gtl.enhancedcore.client.renderer;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Binds ADD's scene API only when installed alongside the older compile-time API. */
public final class AddSceneEffects {
    private static final String API = "com.gtladd.gtladditions.api.client.scene.";
    private static final ClassLoader LOADER = AddSceneEffects.class.getClassLoader();
    private static final boolean AVAILABLE = LOADER.getResource(API.replace('.', '/') + "SceneRendering.class") != null;

    private AddSceneEffects() {}

    public static boolean available() { return AVAILABLE; }

    public static void submit(BlockEntity entity, int part, double radius, Runnable draw) {
        Scene.INSTANCE.submit(entity, part, radius, draw);
    }

    private static final class Scene {
        private static final Scene INSTANCE = create();
        private final Method frame, blockKey, builder, blend, bounds, sphere, build, submit, payload;
        private final Object provider, renderer, straight;

        private Scene() throws ReflectiveOperationException {
            Class<?> rendering = type("SceneRendering");
            Class<?> key = type("PartKey");
            Class<?> render = type("PartRenderer");
            Class<?> part = type("ScenePart");
            Class<?> partBuilder = type("ScenePart$Builder");
            Class<?> partBounds = type("PartBounds");
            Class<?> partBlend = type("PartBlend");
            frame = rendering.getMethod("frame");
            blockKey = key.getMethod("block", type("ProviderHandle"), BlockPos.class, int.class);
            builder = part.getMethod("builder", key, render, Object.class);
            blend = partBuilder.getMethod("blend", partBlend);
            bounds = partBuilder.getMethod("bounds", partBounds);
            sphere = partBounds.getMethod("sphere", double.class, double.class, double.class, double.class);
            build = partBuilder.getMethod("build");
            submit = type("SceneFrame").getMethod("submit", part);
            payload = part.getMethod("getPayload");
            straight = partBlend.getField("STRAIGHT").get(null);
            provider = rendering.getMethod("register", ResourceLocation.class)
                    .invoke(null, new ResourceLocation("gtl_enhancedcore", "machine_effects"));
            renderer = Proxy.newProxyInstance(render.getClassLoader(), new Class<?>[]{render}, this::render);
        }

        private static Scene create() {
            try {
                return new Scene();
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Unsupported GTL Additions scene API", exception);
            }
        }

        private static Class<?> type(String name) throws ClassNotFoundException {
            return Class.forName(API + name, false, LOADER);
        }

        private void submit(BlockEntity entity, int index, double radius, Runnable draw) {
            try {
                Object current = frame.invoke(null);
                if (current == null) return;
                BlockPos pos = entity.getBlockPos();
                Object key = blockKey.invoke(null, provider, pos, index);
                Object part = builder.invoke(null, key, renderer, new Draw(entity, draw));
                blend.invoke(part, straight);
                bounds.invoke(part, sphere.invoke(null, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, radius));
                submit.invoke(current, build.invoke(part));
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Could not submit Enhancedcore machine effect", exception);
            }
        }

        private Object render(Object proxy, Method method, Object[] args) throws Throwable {
            return switch (method.getName()) {
                case "draw" -> {
                    for (Object part : (List<?>) args[1]) {
                        Draw draw = (Draw) payload.invoke(part);
                        if (!draw.entity.isRemoved() && draw.entity.getLevel() == Minecraft.getInstance().level) draw.action.run();
                    }
                    yield null;
                }
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString" -> "GTL-Enhancedcore machine effects";
                default -> InvocationHandler.invokeDefault(proxy, method, args);
            };
        }
    }

    private record Draw(BlockEntity entity, Runnable action) {}
}
