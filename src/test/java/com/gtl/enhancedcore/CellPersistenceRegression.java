package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.machine.hatch.DeferredCellSave;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Transfer bursts must not serialize a whole disk per item; inventory boundaries still flush. */
final class CellPersistenceRegression {
    private static int assertions;
    private static final String DRIVE = "com/gtl/enhancedcore/common/machine/hatch/MEDrivePartMachine";

    static int run() throws Exception {
        assertions = 0;
        int[] live = {100_000}, saved = {100_000}, writes = {0};
        DeferredCellSave save = new DeferredCellSave(() -> { saved[0] = live[0]; writes[0]++; });
        save.flush();
        check(writes[0] == 0, "clean cells never serialize");
        for (int i = 0; i < 50_000; i++) { live[0]--; save.markDirty(); }
        check(writes[0] == 0 && saved[0] == 100_000, "a synchronous auto-build burst only marks dirty");
        save.flush();
        check(writes[0] == 1 && saved[0] == 50_000, "one boundary writes the final quantity exactly once");
        save.flush();
        check(writes[0] == 1, "remount/save of a clean cell does not repeat the copy");
        live[0] = 0; save.markDirty(); save.flush();
        check(saved[0] == 0 && writes[0] == 2, "emptying a disk is also persisted");

        int[] attempts = {0};
        DeferredCellSave retry = new DeferredCellSave(() -> {
            if (++attempts[0] == 1) throw new IllegalStateException("disk write failure");
        });
        retry.markDirty();
        try { retry.flush(); throw new AssertionError("write failure must propagate"); }
        catch (IllegalStateException expected) { check(attempts[0] == 1, "write failure is visible"); }
        retry.flush(); retry.flush();
        check(attempts[0] == 2, "failed flush retains pending data for retry");

        DeferredCellSave[] reentrant = new DeferredCellSave[1];
        int[] nestedWrites = {0};
        reentrant[0] = new DeferredCellSave(() -> {
            if (++nestedWrites[0] == 1) { reentrant[0].markDirty(); reentrant[0].flush(); }
        });
        reentrant[0].markDirty(); reentrant[0].flush();
        check(nestedWrites[0] == 1, "nested persistence does not recurse");
        reentrant[0].flush();
        check(nestedWrites[0] == 2, "a change during write is retained for the next boundary");

        ClassNode drive = compiled(DRIVE);
        MethodNode factory = method(drive, "createCellInventory");
        check(calls(factory, "appeng/api/storage/StorageCells", "getCellInventory") == 1,
                "actual drive supplies the AE save provider");
        int callbackCount = 0;
        for (MethodNode callback : drive.methods) {
            if (!callback.name.startsWith("lambda$createCellInventory$")) continue;
            if (calls(callback, "com/gtl/enhancedcore/common/machine/hatch/DeferredCellSave", "markDirty") != 0) {
                callbackCount++;
                check(calls(callback, "appeng/api/storage/cells/StorageCell", "persist") == 0,
                        "actual transfer callback never persists the disk");
            }
        }
        check(callbackCount == 1, "one dirty-only transfer callback is installed");
        ClassNode transfer = compiled(DRIVE + "$2");
        for (String boundary : new String[]{"getStackInSlot", "setStackInSlot", "extractItem", "serializeNBT"}) {
            MethodNode hook = method(transfer, boundary);
            String flush = boundary.equals("serializeNBT") ? "flushCells" : "flushSlot";
            check(calls(hook, DRIVE, flush) == 1, boundary + " flushes the actual cached cells");
            boolean flushed = false;
            for (var instruction : hook.instructions) if (instruction instanceof MethodInsnNode call) {
                if (call.name.equals(flush)) flushed = true;
                if (call.owner.equals("com/lowdragmc/lowdraglib/misc/ItemStackTransfer") && call.name.equals(boundary))
                    check(flushed, "flush precedes the stack copy/removal/serialization in " + boundary);
            }
        }
        check(calls(method(drive, "mountInventories"), DRIVE, "cellsInSlot") == 1,
                "remount reuses cached packed cells instead of opening competing handlers");
        upstreamNotificationContract();
        return assertions;
    }

    private static void upstreamNotificationContract() throws Exception {
        try (JarFile jar = new JarFile(Path.of("libs/gtlcore-1.2.3.3-fix3.jar").toFile())) {
            ClassNode node = new ClassNode();
            new ClassReader(jar.getInputStream(jar.getJarEntry(
                    "org/gtlcore/gtlcore/integration/ae2/storage/FastInfinityCellInventory.class")))
                    .accept(node, 0);
            check(calls(method(node, "saveChanges"), "appeng/api/storage/cells/ISaveProvider", "saveChanges") == 1,
                    "GTLCore fix4 notifies the owning drive after a real transfer");
        }
    }

    static ClassNode compiled(String name) throws Exception {
        ClassNode node = new ClassNode();
        try (InputStream input = CellPersistenceRegression.class.getClassLoader().getResourceAsStream(name + ".class")) {
            if (input == null) throw new AssertionError("Missing compiled " + name);
            new ClassReader(input).accept(node, 0);
        }
        return node;
    }
    static MethodNode method(ClassNode node, String name) {
        return node.methods.stream().filter(m -> m.name.equals(name) && (m.access & 0x0040) == 0)
                .findFirst().orElseThrow();
    }
    static long calls(MethodNode method, String owner, String name) {
        long result = 0;
        for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call
                && call.owner.equals(owner) && call.name.equals(name)) result++;
        return result;
    }
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
