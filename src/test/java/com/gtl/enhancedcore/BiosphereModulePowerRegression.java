package com.gtl.enhancedcore;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.jar.JarFile;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Executes the production voltage bridge and checks the actual ADD/Core power contracts. */
public final class BiosphereModulePowerRegression {
    private static final String BS = "com/gtladd/gtladditions/common/machine/multiblock/controller/bs/";
    private static final String MIXIN = "com/gtl/enhancedcore/mixin/gtladditions/BiosphereModulePowerMixin";
    private static int assertions;

    static int run() throws Exception {
        assertions = 0;
        ClassNode mixin = production();
        upstreamContracts();
        executableBridge(mixin);
        displayAndPaymentBoundary(mixin);
        return assertions;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Biosphere module power: " + run() + " assertions passed");
    }

    private static void upstreamContracts() throws Exception {
        ClassNode module = upstream("gtladditions-3.3.1Custom.jar", BS + "BiosphereIIIModule");
        check(module.methods.stream().noneMatch(m -> m.name.equals("getMaxVoltage")),
                "Current ADD omits the host maximum-voltage bridge");
        check(calls(method(module, "getTier"), BS + "BiosphereIIIController", "getTier"),
                "The existing recipe tier already delegates to the host");
        check(calls(method(module, "getOverclockVoltage"), BS + "BiosphereIIIController",
                        "getModuleRecipePower$gtladditions"),
                "The existing power budget already uses the host's module allocation");
        MethodNode ready = method(module, "isHostReady");
        check(calls(ready, BS + "BiosphereIIIController", "isFormed")
                        && calls(ready, BS + "BiosphereIIIController", "isWorkingEnabled")
                        && calls(ready, BS + "BiosphereIIIController", "getTier"),
                "ADD retains formed, enabled and host-tier safety gates");

        ClassNode base = upstream("gtladditions-3.3.1Custom.jar",
                "com/gtladd/gtladditions/api/machine/multiblock/GTLAddWorkableElectricMultipleRecipesMachine");
        MethodNode oldDisplay = method(base, "addEnergyDisplay");
        check(readsField(oldDisplay, "tier") && !calls(oldDisplay, null, "getTier"),
                "Reproduce the UI bug: ADD reads the empty garden's local tier field");
        ClassNode electric = upstream("gtceu-1.20.1-1.4.4.jar",
                "com/gregtechceu/gtceu/api/machine/multiblock/WorkableElectricMultiblockMachine");
        check(calls(method(electric, "getMaxVoltage"), null, "getHighestInputVoltage"),
                "The unpatched maximum voltage consults local hatches");
        ClassNode recipe = upstream("gtlcore-1.2.3.2-fix4.jar",
                "org/gtlcore/gtlcore/mixin/gtm/api/recipe/GTRecipeMixin");
        check(calls(method(recipe, "matchTickRecipe"), null, "getMaxVoltage"),
                "Core's general tick matching consumes maximum voltage, not overclock power");

        ClassNode host = upstream("gtladditions-3.3.1Custom.jar", BS + "BiosphereIIIController");
        check(hasOpcode(method(host, "getModuleRecipePower$gtladditions"), Opcodes.LDIV),
                "Wired module allocation divides the host power among active gardens");
        check(calls(method(host, "requestEnergy$gtladditions"), null, "canRequestEnergy")
                        && calls(method(host, "requestEnergy$gtladditions"), null, "consumeWiredEnergy"),
                "Actual payment stays in the upstream controller");
        check(calls(method(host, "consumeWiredEnergy"), null, "removeEnergy")
                        && calls(method(host, "consumeWiredEnergy"), null, "addEnergy"),
                "Upstream partial debit rollback remains available");
        ClassNode logic = upstream("gtladditions-3.3.1Custom.jar",
                BS + "BiosphereIIIModule$BiosphereModuleRecipeLogic");
        check(calls(method(logic, "handleRecipeWorking"), null, "requestEnergy$gtladditions")
                        && calls(method(logic, "handleWirelessTickInput"), null, "requestWirelessEnergy$gtladditions"),
                "Wired and wireless module work still pay exactly through their host");
    }

    /** Transplants only the compiled production method; no Minecraft or Forge is launched. */
    private static void executableBridge(ClassNode mixin) throws Exception {
        String probeName = "com/gtl/enhancedcore/BiosphereVoltageProbe";
        String hostName = Type.getInternalName(HostState.class);
        MethodNode source = method(mixin, "getMaxVoltage");
        MethodNode bridge = new MethodNode(Opcodes.ASM9, source.access, source.name, source.desc, source.signature, null);
        source.accept(bridge);
        bridge.visibleAnnotations = null;
        bridge.invisibleAnnotations = null;
        for (var instruction : bridge.instructions) {
            if (instruction instanceof FieldInsnNode field) {
                field.owner = probeName;
                field.desc = "L" + hostName + ";";
            } else if (instruction instanceof MethodInsnNode call) {
                check(call.owner.equals(BS + "BiosphereIIIController"), "Bridge consults only its actual host");
                call.owner = hostName;
            }
        }
        var writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, probeName, null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PUBLIC, "host", "L" + hostName + ";", null, null).visitEnd();
        var constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();
        bridge.accept(writer);
        writer.visitEnd();
        Class<?> probeType = new ProbeLoader().define(probeName.replace('/', '.'), writer.toByteArray());
        Constructor<?> ctor = probeType.getConstructor();
        Object probe = ctor.newInstance();
        Field hostField = probeType.getField("host");
        Method voltage = probeType.getMethod("getMaxVoltage");
        check((long) voltage.invoke(probe) == 0, "Disconnected gardens advertise no voltage");
        HostState state = new HostState();
        hostField.set(probe, state);
        state.voltage = Long.MAX_VALUE;
        check((long) voltage.invoke(probe) == 0, "An invalid host cannot expose stale maximum voltage");
        state.formed = true;
        for (long actual : new long[]{0, 8, 32, 512, 131072, 8388608, 33554432, 2147483647L, Long.MAX_VALUE}) {
            state.voltage = actual;
            check((long) voltage.invoke(probe) == actual,
                    "Admission keeps actual host voltage, including laser and wireless values: " + actual);
        }
        state.formed = false;
        check((long) voltage.invoke(probe) == 0, "Invalidation is visible immediately without a cached tier");
        hostField.set(probe, null);
        check((long) voltage.invoke(probe) == 0, "Host detach cannot retain an old power source");
    }

    private static void displayAndPaymentBoundary(ClassNode mixin) {
        check(mixin.methods.stream().noneMatch(m -> m.name.equals("getTier") || m.name.equals("getOverclockVoltage")
                        || m.name.startsWith("requestEnergy") || m.name.startsWith("handleRecipe")),
                "Patch preserves upstream tier, allocation and payment methods");
        MethodNode display = method(mixin, "addEnergyDisplay");
        check(!readsField(display, "tier") && !readsField(display, "energyContainer"),
                "Display never uses the garden's empty local energy state");
        check(calls(display, null, "getTier") && calls(display, null, "getOverclockVoltage"),
                "Wired display shares the same host tier and module allocation as recipe logic");
        check(calls(display, null, "isOnline") && calls(display, mixin.superName, "addEnergyDisplay"),
                "Online wireless display keeps ADD's full-precision balance path");
        check(mixin.methods.stream().filter(m -> Set.of("getMaxVoltage", "addEnergyDisplay", "cloud$hostReady",
                        "cloudPowerLimit", "cloudPowerReady").contains(m.name)).flatMap(m -> java.util.stream.StreamSupport.stream(
                        m.instructions.spliterator(), false)).noneMatch(i -> i instanceof MethodInsnNode call
                        && (call.name.equals("consumeEnergy") || call.name.equals("removeEnergy")
                        || call.name.startsWith("requestEnergy") || call.name.startsWith("requestWirelessEnergy")
                        || call.name.equals("cloudPowerPay"))),
                "Reading eligibility or display cannot charge the player");
    }

    public static final class HostState {
        public boolean formed;
        public long voltage;
        public boolean isFormed() { return formed; }
        public long getMaxVoltage() { return voltage; }
    }

    private static final class ProbeLoader extends ClassLoader {
        Class<?> define(String name, byte[] bytes) { return defineClass(name, bytes, 0, bytes.length); }
    }

    private static ClassNode production() throws Exception {
        try (var input = BiosphereModulePowerRegression.class.getClassLoader().getResourceAsStream(MIXIN + ".class")) {
            if (input == null) throw new AssertionError("Missing compiled biosphere Mixin");
            var node = new ClassNode();
            new ClassReader(input).accept(node, ClassReader.SKIP_FRAMES);
            return node;
        }
    }

    private static ClassNode upstream(String jarName, String className) throws Exception {
        try (var jar = new JarFile(Path.of("libs", jarName).toFile());
             var input = jar.getInputStream(jar.getJarEntry(className + ".class"))) {
            var node = new ClassNode();
            new ClassReader(input).accept(node, ClassReader.SKIP_FRAMES);
            return node;
        }
    }

    private static MethodNode method(ClassNode node, String name) {
        return node.methods.stream().filter(m -> m.name.equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("Missing method " + node.name + "." + name));
    }

    private static boolean calls(MethodNode method, String owner, String name) {
        for (var instruction : method.instructions)
            if (instruction instanceof MethodInsnNode call && call.name.equals(name)
                    && (owner == null || call.owner.equals(owner))) return true;
        return false;
    }

    private static boolean readsField(MethodNode method, String name) {
        for (var instruction : method.instructions)
            if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETFIELD
                    && field.name.equals(name)) return true;
        return false;
    }

    private static boolean hasOpcode(MethodNode method, int opcode) {
        for (var instruction : method.instructions) if (instruction.getOpcode() == opcode) return true;
        return false;
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
