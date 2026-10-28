package com.gtl.enhancedcore;

import com.gtl.enhancedcore.client.performance.map.PackedMapImage;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Random;
import java.util.jar.JarFile;
import javax.imageio.ImageIO;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

/** Exact storage and PNG regression for the map-save image allocation redirect. */
final class MapImagePacingRegression {
    private static int assertions;

    static int run() throws Exception {
        assertions = 0;
        for (int type : new int[]{BufferedImage.TYPE_INT_RGB, BufferedImage.TYPE_INT_ARGB}) {
            for (int[] size : new int[][]{{1, 1}, {17, 3}, {3, 17}, {512, 512}}) {
                pixels(type, size[0], size[1]);
            }
        }
        constructorFailures();
        check(PackedMapImage.create(3, 4, BufferedImage.TYPE_3BYTE_BGR).getClass() == BufferedImage.class,
                "Unknown image layouts retain the JDK path");
        upstreamShape();
        return assertions;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Map image assertions: " + run());
    }

    private static void pixels(int type, int width, int height) throws Exception {
        BufferedImage original = new BufferedImage(width, height, type);
        BufferedImage packed = PackedMapImage.create(width, height, type);
        check(packed.getType() == type && packed.getWidth() == width && packed.getHeight() == height,
                "Image type and dimensions retained");
        check(original.isAlphaPremultiplied() == packed.isAlphaPremultiplied(), "No alpha premultiplication");
        Random random = new Random(0x186acbaL);
        int[] edgeColors = {0, -1, 0x00ffffff, 0x01020304, 0x80010203, 0xff000000, Integer.MIN_VALUE};
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int n = y * width + x;
                int color = n < edgeColors.length ? edgeColors[n] : random.nextInt();
                original.setRGB(x, y, color);
                packed.setRGB(x, y, color);
                check(original.getRGB(x, y) == packed.getRGB(x, y), "Every pixel including alpha is identical");
            }
        }
        check(Arrays.equals(((DataBufferInt) original.getRaster().getDataBuffer()).getData(),
                        ((DataBufferInt) packed.getRaster().getDataBuffer()).getData()),
                "Raw packed storage including RGB unused alpha bits is identical");
        int[] before = packed.getRGB(0, 0, width, height, null, 0, width);
        for (int[] xy : new int[][]{{-1, 0}, {0, -1}, {width, 0}, {0, height},
                {width, height - 1}, {Integer.MAX_VALUE, 0}, {0, Integer.MIN_VALUE}}) {
            check(failure(original, xy[0], xy[1]) == failure(packed, xy[0], xy[1]),
                    "Invalid coordinate exception class retained");
        }
        check(Arrays.equals(before, packed.getRGB(0, 0, width, height, null, 0, width)),
                "Invalid coordinates never change another row");
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        check(ImageIO.write(packed, "PNG", encoded), "Standard PNG writer accepts packed image");
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(encoded.toByteArray()));
        check(decoded.getWidth() == width && decoded.getHeight() == height, "PNG dimensions retained");
        check(Arrays.equals(before, decoded.getRGB(0, 0, width, height, null, 0, width)),
                "PNG round trip preserves every color and alpha bit");
        // Other normal BufferedImage operations must continue to see the same raster.
        packed.setRGB(0, 0, 1, 1, new int[]{0x31415926}, 0, 1);
        original.setRGB(0, 0, 1, 1, new int[]{0x31415926}, 0, 1);
        check(packed.getRGB(0, 0) == original.getRGB(0, 0), "Inherited bulk writes share the same storage");
    }

    private static Class<?> failure(BufferedImage image, int x, int y) {
        try { image.setRGB(x, y, 42); } catch (RuntimeException expected) { return expected.getClass(); }
        throw new AssertionError("Invalid coordinate must fail");
    }

    private static void constructorFailures() {
        for (int[] size : new int[][]{{0, 1}, {1, 0}, {-1, 1}, {1, -1}}) {
            Class<?> expected = constructorFailure(size[0], size[1], false);
            check(expected == constructorFailure(size[0], size[1], true), "Invalid dimensions fail identically");
        }
    }

    private static Class<?> constructorFailure(int width, int height, boolean packed) {
        try {
            if (packed) PackedMapImage.create(width, height, BufferedImage.TYPE_INT_ARGB);
            else new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        } catch (RuntimeException expected) { return expected.getClass(); }
        throw new AssertionError("Invalid dimensions must fail");
    }

    private static void upstreamShape() throws Exception {
        ClassNode node = new ClassNode();
        try (JarFile jar = new JarFile("libs/ftb-chunks-forge-2001.3.8.jar")) {
            try (var input = jar.getInputStream(jar.getJarEntry(
                    "dev/ftb/mods/ftbchunks/client/map/MapRegionData.class"))) {
                new ClassReader(input).accept(node, 0);
            }
        }
        var method = node.methods.stream().filter(m -> m.name.equals("write") && m.desc.equals("()V"))
                .findFirst().orElseThrow();
        int allocations = 0, constructors = 0, stores = 0, submit = 0, lock = 0;
        int argb = 0, rgb = 0;
        for (var insn : method.instructions) {
            if (insn instanceof TypeInsnNode type && type.getOpcode() == Opcodes.NEW
                    && type.desc.equals("java/awt/image/BufferedImage")) allocations++;
            if (insn instanceof MethodInsnNode call) {
                if (call.owner.equals("java/awt/image/BufferedImage")) {
                    if (call.name.equals("<init>") && call.desc.equals("(III)V")) {
                        constructors++;
                        if (insn.getPrevious().getOpcode() == Opcodes.ICONST_1) rgb++;
                        if (insn.getPrevious().getOpcode() == Opcodes.ICONST_2) argb++;
                    }
                    if (call.name.equals("setRGB") && call.desc.equals("(III)V")) stores++;
                }
                if (call.owner.equals("java/util/concurrent/ExecutorService")
                        && call.name.equals("execute") && call.desc.equals("(Ljava/lang/Runnable;)V")) submit++;
            }
            if (insn instanceof FieldInsnNode field && field.name.equals("MAP_EXECUTOR")) {
                check(field.owner.equals("dev/ftb/mods/ftbchunks/client/FTBChunksClient"), "Upstream executor owner");
            }
            if (insn.getOpcode() == Opcodes.MONITORENTER) lock++;
        }
        check(allocations == 5 && constructors == 5 && argb == 1 && rgb == 4,
                "Exactly five image allocations: one ARGB and four RGB");
        check(stores == 5, "Five upstream per-pixel stores retained");
        check(submit == 1 && lock == 1, "One original executor submission and original map lock");
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
