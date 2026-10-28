package com.gtl.enhancedcore;

import com.google.gson.JsonParser;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Static contract for the optional TravelAnchors 5.0.1 range hook. */
final class TravelAnchorsRegression {
    private static int assertions;

    static int run() throws Exception {
        assertions = 0;
        String mixinPath = "src/main/java/com/gtl/enhancedcore/mixin/travelanchors/TravelAnchorRangeMixin.java";
        String mixin = Files.readString(Path.of(mixinPath));
        var config = JsonParser.parseString(Files.readString(
                Path.of("src/main/resources/gtl_enhancedcore.mixins.json"))).getAsJsonObject();
        var common = config.getAsJsonArray("mixins");
        boolean registered = common.asList().stream()
                .anyMatch(value -> value.getAsString().equals("travelanchors.TravelAnchorRangeMixin"));
        check(registered, "TravelAnchors range mixin is registered on both sides");
        check(mixin.contains("@Mixin(targets = \"de.castcrafter.travelanchors.TeleportHandler\", remap = false)"),
                "range hook targets the installed TravelAnchors handler without remapping");
        check(mixin.contains("@Inject(method = \"getMaxDistance(Lnet/minecraft/world/entity/player/Player;)D\""),
                "range hook intercepts the shared anchor search radius");
        check(mixin.contains("cir.setReturnValue(Double.POSITIVE_INFINITY)"),
                "range hook admits anchors at any finite distance");
        check(!mixin.contains("shortTeleport"), "short staff blink range remains separate");

        String plugin = Files.readString(Path.of(
                "src/main/java/com/gtl/enhancedcore/common/performance/PerformanceMixinPlugin.java"));
        check(plugin.contains("mixinClassName.equals(\"com.gtl.enhancedcore.mixin.travelanchors.TravelAnchorRangeMixin\")")
                        && plugin.contains("return hasClass(targetClassName);"),
                "optional TravelAnchors target is skipped when its class is absent");
        bytecodeAudit();
        return assertions;
    }

    private static void bytecodeAudit() throws Exception {
        String gameDirectory = System.getenv("SHCORE_GAME_DIR");
        Path jar = gameDirectory == null || gameDirectory.isBlank()
                ? Path.of("C:/Minecraft/.minecraft/versions/GregTech Leisure-Enhanced/mods/TravelAnchors-1.20.1-5.0.1.jar")
                : Path.of(gameDirectory, "mods", "TravelAnchors-1.20.1-5.0.1.jar");
        if (!Files.isRegularFile(jar)) return;
        try (ZipFile archive = new ZipFile(jar.toFile())) {
            ClassNode handler = read(archive, "de/castcrafter/travelanchors/TeleportHandler.class");
            MethodNode range = method(handler, "getMaxDistance", "(Lnet/minecraft/world/entity/player/Player;)D");
            check(range != null, "installed TravelAnchors handler exposes the hooked range signature");
            MethodNode lookup = method(handler, "getAnchorToTeleport", "(Lnet/minecraft/world/level/Level;"
                    + "Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;)"
                    + "Lorg/apache/commons/lang3/tuple/Pair;");
            check(lookup != null && calls(lookup, "de/castcrafter/travelanchors/TeleportHandler", "getMaxDistance") == 1,
                    "anchor lookup uses the hooked range method");
            MethodNode teleport = method(handler, "anchorTeleport", "(Lnet/minecraft/world/level/Level;"
                    + "Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/InteractionHand;)Z");
            check(teleport != null && calls(teleport,
                    "de/castcrafter/travelanchors/TeleportHandler", "getAnchorToTeleport") == 1,
                    "anchor and staff teleport entry reaches the same unlimited lookup");
            ClassNode events = read(archive, "de/castcrafter/travelanchors/EventListener.class");
            MethodNode click = method(events, "onRightClick",
                    "(Lnet/minecraftforge/event/entity/player/PlayerInteractEvent$RightClickItem;)V");
            check(click != null && calls(click, "de/castcrafter/travelanchors/TeleportHandler", "anchorTeleport") == 1,
                    "travel staff right-click anchor route uses the shared lookup");
        }
    }

    private static ClassNode read(ZipFile archive, String name) throws Exception {
        try (InputStream input = archive.getInputStream(archive.getEntry(name))) {
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }

    private static MethodNode method(ClassNode owner, String name, String descriptor) {
        return owner.methods.stream().filter(candidate -> candidate.name.equals(name)
                && candidate.desc.equals(descriptor)).findFirst().orElse(null);
    }

    private static int calls(MethodNode method, String owner, String name) {
        return (int) java.util.stream.StreamSupport.stream(method.instructions.spliterator(), false)
                .filter(instruction -> instruction instanceof MethodInsnNode call
                        && call.owner.equals(owner) && call.name.equals(name)).count();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }
}
