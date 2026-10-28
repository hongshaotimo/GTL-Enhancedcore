package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.structure.NativeStructureHollowing;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Verifies conservative 26-neighbor visibility and the all-machine replacement hook. */
final class NativeStructureHollowingRegression {
    static int run() throws Exception {
        int assertions = 0;
        boolean[] solid = new boolean[27];
        Arrays.fill(solid, true);
        boolean[] hidden = NativeStructureHollowing.hiddenOpaque(solid, 3, 3, 3);
        check(count(hidden) == 1 && hidden[13], "Only the interior of a sealed solid 3-cube is hidden");
        assertions++;

        solid[12] = false;
        hidden = NativeStructureHollowing.hiddenOpaque(solid, 3, 3, 3);
        check(count(hidden) == 0, "A transparent opening exposes the interior");
        assertions++;

        boolean[] shell = new boolean[125];
        for (int z = 0; z < 5; z++) for (int y = 0; y < 5; y++) for (int x = 0; x < 5; x++)
            shell[(z * 5 + y) * 5 + x] = x == 0 || x == 4 || y == 0 || y == 4 || z == 0 || z == 4;
        hidden = NativeStructureHollowing.hiddenOpaque(shell, 5, 5, 5);
        check(count(hidden) == 0, "A one-block shell remains visible from outside");
        assertions++;

        boolean[] diagonal = new boolean[125];
        Arrays.fill(diagonal, true);
        diagonal[0] = false;
        diagonal[(1 * 5 + 1) * 5 + 1] = true;
        hidden = NativeStructureHollowing.hiddenOpaque(diagonal, 5, 5, 5);
        check(!hidden[(1 * 5 + 1) * 5 + 1], "Corner-connected sight protects a candidate");
        assertions++;

        boolean[] repeatedSolid = new boolean[27];
        Arrays.fill(repeatedSolid, true);
        hidden = NativeStructureHollowing.hiddenOpaqueAcrossRepetitions(repeatedSolid, 3, 3, 3,
                new int[][] {{1, 1}, {1, 2}, {1, 1}});
        check(hidden[13], "The repeated aisle cell stays hidden at every legal repeat count");
        assertions++;

        hidden = NativeStructureHollowing.hiddenOpaqueAcrossRepetitions(repeatedSolid, 3, 3, 3,
                new int[][] {{1, 2}, {1, 1}, {1, 1}});
        check(!hidden[4], "A repeated boundary aisle remains required when any legal copy is exposed");
        assertions++;

        check(Arrays.equals(NativeStructureHollowing.minimumRepetitions(new int[][] {{1, 1}, {3, 15}, {2, 2}}),
                new int[] {1, 3, 2}), "Structure previews use the minimum valid aisle counts");
        assertions++;

        String mixin = Files.readString(Path.of(
                "src/main/java/com/gtl/enhancedcore/mixin/gtceu/GtlMegastructurePatternMixin.java"));
        check(mixin.contains("NativeStructureHollowing.optimize(definition.getId(), original.get())"),
                "Third-party factory results pass through the configured global optimizer");
        assertions++;
        return assertions;
    }

    private static int count(boolean[] values) {
        int count = 0;
        for (boolean value : values) if (value) count++;
        return count;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
