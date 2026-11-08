package com.gtl.enhancedcore;

import java.util.List;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Pin the actual legacy and transfinite dispatch ABIs used by the material transaction. */
final class ProcessingDispatchRegression {
    static int run() throws Exception {
        int checks = 0;
        try (var jar = new ZipFile("libs/gtlcore-1.2.3.3-fix3.jar")) {
            for (String name : List.of("org/gtlcore/gtlcore/mixin/ae2/logic/CraftingCpuLogicMixin",
                    "org/gtlcore/gtlcore/integration/ae2/crafting/transfinite/TransfiniteCraftingLogic")) {
                var node = new ClassNode();
                new ClassReader(jar.getInputStream(jar.getEntry(name + ".class"))).accept(node, 0);
                var method = node.methods.stream().filter(m -> m.name.equals("executeCrafting")).findFirst().orElseThrow();
                int sizing = 0, batched = 0, single = 0, pushes = 0;
                for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call) {
                    if (call.owner.endsWith("/CraftingPatternAutoExpand") && call.name.equals("getOperations")) sizing++;
                    if (call.owner.endsWith("/AEUtils") && call.name.equals("extractForProcessingPattern")) {
                        if (call.desc.contains(";J)")) batched++; else single++;
                    }
                    if (call.owner.equals("appeng/api/networking/crafting/ICraftingProvider") && call.name.equals("pushPattern")) pushes++;
                }
                if (sizing != 1 || batched != 1 || single != 1 || pushes != 1)
                    throw new AssertionError("Changed CPU material-transaction ABI: " + name);
                checks += 4;
            }
        }
        for (String name : List.of("IvSmartDispatchMixin", "IvTransfiniteCraftingMixin")) {
            var node = new ClassNode();
            try (var input = ProcessingDispatchRegression.class.getClassLoader().getResourceAsStream(
                    "com/gtl/enhancedcore/mixin/gtlcore/" + name + ".class")) {
                new ClassReader(input).accept(node, 0);
            }
            for (String method : List.of("iv$extractBatch", "iv$extractSingle")) {
                var hook = node.methods.stream().filter(m -> m.name.equals(method)).findFirst().orElseThrow();
                boolean guarded = false;
                for (var instruction : hook.instructions) if (instruction instanceof MethodInsnNode call
                        && call.owner.endsWith("/ProcessingDispatchBatch") && call.name.equals("extract")) guarded = true;
                if (!guarded) throw new AssertionError("Unguarded CPU material extraction: " + name + "." + method);
                checks++;
            }
        }
        return checks;
    }
}
