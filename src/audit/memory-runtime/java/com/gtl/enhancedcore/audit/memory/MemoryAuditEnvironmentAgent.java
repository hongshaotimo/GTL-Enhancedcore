package com.gtl.enhancedcore.audit.memory;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;

/** Audit-process-only environment isolation and optional observations; does not edit a game library. */
public final class MemoryAuditEnvironmentAgent {
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
                if (!"org/lwjgl/glfw/GLFW".equals(name)
                        && !"oshi/util/platform/windows/WmiQueryHandler".equals(name)
                        && !recipeTick) return null;
                boolean glfw = "org/lwjgl/glfw/GLFW".equals(name);
                boolean[] changed = {false};
                var reader = new ClassReader(bytes);
                var writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
                reader.accept(new ClassVisitor(Opcodes.ASM8, writer) {
                    @Override
                    public MethodVisitor visitMethod(int access, String method, String descriptor,
                            String signature, String[] exceptions) {
                        MethodVisitor visitor = super.visitMethod(access, method, descriptor, signature, exceptions);
                        if (glfw && Boolean.getBoolean("gtl.enhancedcore.ftbClaimAudit")
                                && (method.equals("glfwGetMouseButton") || method.equals("glfwGetKey"))
                                && descriptor.equals("(JI)I")) {
                            visitor.visitCode();
                            visitor.visitLdcInsn("gtl.enhancedcore.ftbAudit."
                                    + (method.equals("glfwGetKey") ? "key." : "mouse."));
                            visitor.visitVarInsn(Opcodes.ILOAD, 2);
                            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "toString", "(I)Ljava/lang/String;", false);
                            visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "concat", "(Ljava/lang/String;)Ljava/lang/String;", false);
                            visitor.visitInsn(Opcodes.ICONST_0);
                            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "getInteger", "(Ljava/lang/String;I)Ljava/lang/Integer;", false);
                            visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
                            visitor.visitInsn(Opcodes.IRETURN);
                            visitor.visitMaxs(2, 3);
                            visitor.visitEnd();
                            changed[0] = true;
                            return null;
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
                        : "[MEMORY_ENV] WMI timeout bounded to 100ms");
                return writer.toByteArray();
            }
        });
    }
}
