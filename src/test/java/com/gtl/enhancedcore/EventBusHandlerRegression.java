package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.performance.structure.StructureSearchBackoff;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/** Detects names that alias in Forge eventbus 6.2.33's generated subscriber class cache. */
public final class EventBusHandlerRegression {
    private EventBusHandlerRegression() {}

    public static int run() throws Exception {
        Path output = Path.of(StructureSearchBackoff.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        var generatedNames = new HashMap<String, String>();
        int[] assertions = {0};
        try (var classes = Files.walk(output)) {
            for (Path file : classes.filter(path -> path.toString().endsWith(".class")).toList()) {
                ClassReader reader = new ClassReader(Files.readAllBytes(file));
                String owner = reader.getClassName();
                reader.accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                            String signature, String[] exceptions) {
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override public org.objectweb.asm.AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                                if (annotation.equals("Lnet/minecraftforge/eventbus/api/SubscribeEvent;")) {
                                    Type[] parameters = Type.getArgumentTypes(descriptor);
                                    if (parameters.length != 1) throw new AssertionError("Invalid subscriber " + owner + '.' + name);
                                    // This deliberately mirrors the actual library's package/class/method/argument-simple-name key.
                                    String ownerName = owner.replace('/', '.');
                                    String packageName = ownerName.substring(0, ownerName.lastIndexOf('.'));
                                    String simpleOwner = ownerName.substring(Math.max(ownerName.lastIndexOf('.'), ownerName.lastIndexOf('$')) + 1);
                                    String argument = parameters[0].getClassName();
                                    String simpleArgument = argument.substring(Math.max(argument.lastIndexOf('.'), argument.lastIndexOf('$')) + 1);
                                    String key = packageName + ".__" + simpleOwner + '_' + name + '_' + simpleArgument;
                                    String method = ownerName + '.' + name + descriptor;
                                    String previous = generatedNames.putIfAbsent(key, method);
                                    if (previous != null) throw new AssertionError("Forge subscriber wrapper name collision: "
                                            + key + " aliases " + previous + " and " + method);
                                    assertions[0]++;
                                }
                                return null;
                            }
                        };
                    }
                }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
        }
        if (assertions[0] == 0) throw new AssertionError("No production event subscribers examined");
        return assertions[0];
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Event subscriber wrapper checks: " + run());
    }
}
