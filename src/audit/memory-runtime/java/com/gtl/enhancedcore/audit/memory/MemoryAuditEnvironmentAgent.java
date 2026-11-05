package com.gtl.enhancedcore.audit.memory;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.LinkedHashSet;
import java.util.Set;
import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.Label;

/** Audit-process-only environment isolation and optional observations; does not edit a game library. */
public final class MemoryAuditEnvironmentAgent {
    private static boolean dispatchLoadMethod(String owner, String method) {
        return switch (owner) {
            case "com/gtl/enhancedcore/common/cloud/CloudCpuRefill" ->
                    method.equals("replenish") || method.equals("availableOperations");
            case "com/gtl/enhancedcore/common/cloud/CloudPatternBufferMachine" -> switch (method) {
                case "cloudDispatchCapacity", "graphOperationsPerPattern", "getAvailablePatterns",
                        "cloudMaintenance", "refreshPublished", "cloudLibraryChanged", "cloudPublishChanges",
                        "cloudReadyFailure", "pushPattern" -> true;
                default -> false;
            };
            case "com/gtl/enhancedcore/common/cloud/CloudPatternAllocator" -> method.equals("dispatchShare");
            case "appeng/me/service/CraftingService" ->
                    method.equals("refreshNodeCraftingProvider") || method.equals("getProviders");
            case "appeng/crafting/execution/CraftingCpuLogic" -> method.equals("executeCrafting");
            default -> false;
        };
    }

    public static void premain(String arguments, Instrumentation instrumentation) {
        if (!Boolean.getBoolean("gtl.enhancedcore.memoryAudit")) {
            throw new IllegalStateException("The memory environment agent requires an explicit audit flag");
        }
        instrumentation.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String name, Class<?> type,
                    ProtectionDomain domain, byte[] bytes) {
                boolean recipeTick = !Boolean.getBoolean("gtl.enhancedcore.memoryAuditProductionTicks")
                        && (name.startsWith("com/gtl/enhancedcore/common/recipe/")
                        || name.equals("com/gregtechceu/gtceu/api/machine/trait/RecipeLogic")
                        || name.startsWith("org/gtlcore/gtlcore/common/machine/trait/")
                        || name.startsWith("com/gtladd/gtladditions/api/machine/logic/"));
                boolean ticketTrace = Boolean.getBoolean("gtl.enhancedcore.cloudTicketAudit")
                        && "net/minecraft/server/level/DistanceManager".equals(name);
                boolean dispatchLoad = Boolean.getBoolean("gtl.enhancedcore.cloudDispatchLoadAudit")
                        && (name.equals("com/gtl/enhancedcore/common/cloud/CloudCpuRefill")
                        || name.equals("com/gtl/enhancedcore/common/cloud/CloudPatternBufferMachine")
                        || name.equals("com/gtl/enhancedcore/common/cloud/CloudPatternAllocator")
                        || name.equals("appeng/me/service/CraftingService")
                        || name.equals("appeng/crafting/execution/CraftingCpuLogic"));
                if (!"org/lwjgl/glfw/GLFW".equals(name)
                        && !"oshi/util/platform/windows/WmiQueryHandler".equals(name)
                        && !recipeTick && !ticketTrace && !dispatchLoad) return null;
                boolean glfw = "org/lwjgl/glfw/GLFW".equals(name);
                boolean[] changed = {false};
                Set<String> loadCounters = new LinkedHashSet<>();
                var reader = new ClassReader(bytes);
                var writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
                reader.accept(new ClassVisitor(Opcodes.ASM8, writer) {
                    @Override public void visitEnd() {
                        if (ticketTrace) super.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                                "ec$auditTicketSamples", "I", null, null).visitEnd();
                        for (String counter : loadCounters) super.visitField(
                                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                                counter, "J", null, null).visitEnd();
                        super.visitEnd();
                    }
                    @Override
                    public MethodVisitor visitMethod(int access, String method, String descriptor,
                            String signature, String[] exceptions) {
                        MethodVisitor visitor = super.visitMethod(access, method, descriptor, signature, exceptions);
                        if (dispatchLoad && dispatchLoadMethod(name, method)
                                && (access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0) {
                            changed[0] = true;
                            String counter = "ec$audit$" + method;
                            loadCounters.add(counter);
                            boolean zeroBudget = name.equals("appeng/crafting/execution/CraftingCpuLogic")
                                    && method.equals("executeCrafting") && descriptor.startsWith("(I")
                                    && (access & Opcodes.ACC_STATIC) == 0;
                            if (zeroBudget) loadCounters.add("ec$audit$zeroBudget");
                            return new MethodVisitor(Opcodes.ASM8, visitor) {
                                @Override public void visitCode() {
                                    super.visitCode();
                                    increment(counter);
                                    if (zeroBudget) {
                                        var positiveBudget = new Label();
                                        super.visitVarInsn(Opcodes.ILOAD, 1);
                                        super.visitJumpInsn(Opcodes.IFGT, positiveBudget);
                                        increment("ec$audit$zeroBudget");
                                        super.visitLabel(positiveBudget);
                                        super.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                                    }
                                }
                                private void increment(String field) {
                                    super.visitFieldInsn(Opcodes.GETSTATIC, name, field, "J");
                                    super.visitInsn(Opcodes.LCONST_1);
                                    super.visitInsn(Opcodes.LADD);
                                    super.visitFieldInsn(Opcodes.PUTSTATIC, name, field, "J");
                                }
                            };
                        }
                        if (ticketTrace && (method.equals("addTicket") || method.equals("m_140784_"))
                                && descriptor.equals("(JLnet/minecraft/server/level/Ticket;)V")) {
                            changed[0] = true;
                            return new MethodVisitor(Opcodes.ASM8, visitor) {
                                @Override public void visitCode() {
                                    super.visitCode(); var skip = new Label();
                                    super.visitLdcInsn("gtl.enhancedcore.cloudTicketTraceActive");
                                    super.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Boolean", "getBoolean", "(Ljava/lang/String;)Z", false);
                                    super.visitJumpInsn(Opcodes.IFEQ, skip);
                                    super.visitVarInsn(Opcodes.LLOAD, 1); super.visitLdcInsn(137438953504L);
                                    super.visitInsn(Opcodes.LCMP); super.visitJumpInsn(Opcodes.IFNE, skip);
                                    super.visitFieldInsn(Opcodes.GETSTATIC, name, "ec$auditTicketSamples", "I");
                                    super.visitIntInsn(Opcodes.BIPUSH, 12); super.visitJumpInsn(Opcodes.IF_ICMPGE, skip);
                                    super.visitFieldInsn(Opcodes.GETSTATIC, name, "ec$auditTicketSamples", "I");
                                    super.visitInsn(Opcodes.ICONST_1); super.visitInsn(Opcodes.IADD);
                                    super.visitFieldInsn(Opcodes.PUTSTATIC, name, "ec$auditTicketSamples", "I");
                                    super.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "err", "Ljava/io/PrintStream;");
                                    super.visitVarInsn(Opcodes.ALOAD, 3);
                                    super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/Object;)V", false);
                                    super.visitTypeInsn(Opcodes.NEW, "java/lang/Exception"); super.visitInsn(Opcodes.DUP);
                                    super.visitLdcInsn("CLOUD_TICKET_AUDIT core chunk ticket add (observation only)");
                                    super.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Exception", "<init>", "(Ljava/lang/String;)V", false);
                                    super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Exception", "printStackTrace", "()V", false);
                                    super.visitLabel(skip); super.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                                }
                            };
                        }
                        if (glfw && (method.equals("glfwShowWindow") || method.equals("glfwFocusWindow"))
                                && descriptor.equals("(J)V")
                                || recipeTick && method.equals("serverTick") && descriptor.equals("()V")) {
                            visitor.visitCode();
                            visitor.visitInsn(Opcodes.RETURN);
                            visitor.visitMaxs(0, (access & Opcodes.ACC_STATIC) == 0 ? 1 : 2);
                            visitor.visitEnd();
                            changed[0] = true;
                            return null;
                        }
                        if (glfw && method.equals("glfwCreateWindow")) {
                            changed[0] = true;
                            return new MethodVisitor(Opcodes.ASM8, visitor) {
                                @Override public void visitCode() {
                                    super.visitCode();
                                    hint(0x00020004); // GLFW_VISIBLE
                                    hint(0x00020001); // GLFW_FOCUSED
                                    hint(0x0002000C); // GLFW_FOCUS_ON_SHOW
                                }
                                private void hint(int constant) {
                                    super.visitLdcInsn(constant);
                                    super.visitInsn(Opcodes.ICONST_0);
                                    super.visitMethodInsn(Opcodes.INVOKESTATIC, name, "glfwWindowHint", "(II)V", false);
                                }
                            };
                        }
                        if ("oshi/util/platform/windows/WmiQueryHandler".equals(name) && method.equals("<clinit>")) {
                            changed[0] = true;
                            return new MethodVisitor(Opcodes.ASM8, visitor) {
                                @Override public void visitInsn(int opcode) {
                                    if (opcode == Opcodes.RETURN) {
                                        super.visitIntInsn(Opcodes.SIPUSH, 100);
                                        super.visitFieldInsn(Opcodes.PUTSTATIC, name, "globalTimeout", "I");
                                    }
                                    super.visitInsn(opcode);
                                }
                            };
                        }
                        return visitor;
                    }
                }, 0);
                if (!changed[0]) return null;
                System.out.println(glfw ? "[MEMORY_ENV] GLFW windows hidden before creation; show/focus blocked"
                        : recipeTick ? "[MEMORY_ENV] Audit recipe ticking paused in " + name
                        : ticketTrace ? "[MEMORY_ENV] Core chunk ticket stack observation enabled (first 12, gated by fixture phase)"
                        : dispatchLoad ? "[MEMORY_ENV] Cloud dispatch load call counters enabled in " + name
                        : "[MEMORY_ENV] WMI timeout bounded to 100ms");
                return writer.toByteArray();
            }
        });
    }
}
