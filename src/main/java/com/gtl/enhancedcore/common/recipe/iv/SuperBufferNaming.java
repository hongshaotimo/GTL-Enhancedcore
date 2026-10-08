package com.gtl.enhancedcore.common.recipe.iv;

import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Server-side naming shared by formation and the explicit restore button. */
public final class SuperBufferNaming {
    private SuperBufferNaming() {}

    public static void applyNames(WorkableMultiblockMachine machine) {
        if (machine.isRemote()) return;
        var buffers = collectBuffers(machine);
        for (int index = 0; index < buffers.size(); index++) {
            var buffer = buffers.get(index);
            String targetKey = targetKey(machine, buffers, index);
            var names = (SuperBufferNameAccess) (Object) buffer;
            String targetName = SuperBufferChineseNames.resolve(targetKey);
            if (SuperBufferAutoName.shouldWrite(buffer.getCustomName(), targetName,
                    names.enhanced$getAutomaticName(), names.enhanced$isManualName())) {
                names.enhanced$setAutomaticName(targetKey);
            }
        }
    }

    public static String automaticKey(MEPatternBufferPartMachine buffer) {
        var controllers = buffer.getControllers();
        if (!SuperBufferAutoName.exclusive(controllers.size()) || controllers.getFirst() == null
                || !(controllers.getFirst().self() instanceof WorkableMultiblockMachine machine)
                || !machine.isFormed()) return "";
        var buffers = collectBuffers(machine);
        int index = buffers.indexOf(buffer);
        return index < 0 ? "" : targetKey(machine, buffers, index);
    }

    private static String targetKey(WorkableMultiblockMachine machine,
                                    List<MEPatternBufferPartMachine> buffers, int index) {
        GTRecipeType[] types = machine.getRecipeTypes();
        if (types == null || types.length == 0) return "";
        if (IvBuffers.targetController(machine) && buffers.size() > 1) {
            List<String> keys = new ArrayList<>();
            for (GTRecipeType type : types) {
                if (type != null && type.registryName != null) keys.add(type.registryName.toLanguageKey());
            }
            return SuperBufferAutoName.keyForIndex(index, keys);
        }
        int active = machine.getActiveRecipeType();
        if (active < 0 || active >= types.length || types[active] == null || types[active].registryName == null) return "";
        return types[active].registryName.toLanguageKey();
    }

    private static List<MEPatternBufferPartMachine> collectBuffers(WorkableMultiblockMachine machine) {
        List<MEPatternBufferPartMachine> buffers = new ArrayList<>();
        for (var buffer : IvBuffers.collect(machine)) {
            var controllers = buffer.getControllers();
            if (SuperBufferAutoName.exclusive(controllers.size()) && controllers.getFirst() != null
                    && controllers.getFirst().self() == machine) {
                buffers.add(buffer);
            }
        }
        buffers.sort(Comparator.comparingLong(buffer -> SuperBufferAutoName.positionKey(
                buffer.getPos().getX(), buffer.getPos().getY(), buffer.getPos().getZ())));
        return buffers;
    }
}
