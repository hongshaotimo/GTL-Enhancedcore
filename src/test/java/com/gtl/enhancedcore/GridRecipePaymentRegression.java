package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.recipe.GridRecipePayment;
import com.gtl.enhancedcore.common.recipe.GridEnergyInputs;
import com.gtl.enhancedcore.common.recipe.GridEnergyRecipeInput;
import com.gtl.enhancedcore.common.recipe.EnergyContainerListView;
import java.math.BigInteger;
import java.util.List;

final class GridRecipePaymentRegression {
    private static int assertions;

    static int run() {
        multiTargetShadowContract();
        var grid = new Grid(new BigInteger("1000000000000000000000000000000"));
        long screenshotEUt = 4_323_455_640_262_410_240L;
        long localBuffer = 2_305_847_407_260_205_056L;
        check(screenshotEUt > localBuffer, "Reported super-parallel demand exceeds the hatch cache");
        BigInteger before = grid.balance;
        check(grid.pay(List.of(screenshotEUt), true), "A funded grid admits the reported recipe above cache and VA limits");
        check(grid.balance.equals(before) && grid.transfers == 0, "Admission does not withdraw EU");
        check(grid.pay(List.of(screenshotEUt), false), "The reported recipe really pays from the grid");
        check(grid.balance.equals(before.subtract(BigInteger.valueOf(screenshotEUt))) && grid.transfers == 1,
                "A super-parallel tick pays the exact full amount once");

        grid = new Grid(BigInteger.valueOf(9));
        check(!grid.pay(List.of(10L), true) && !grid.pay(List.of(10L), false), "Insufficient grid EU blocks simulation and execution");
        check(grid.balance.equals(BigInteger.valueOf(9)) && grid.transfers == 0, "A refused batch does not make a partial withdrawal");
        check(grid.pay(List.of(9L), false) && grid.balance.signum() == 0, "An exact balance pays successfully");
        check(!grid.pay(List.of(1L), false), "An exhausted grid cannot reuse a full local hatch buffer");

        grid = new Grid(BigInteger.valueOf(10));
        grid.rejectTransfer = true;
        check(grid.pay(List.of(10L), true), "Simulation sees sufficient energy before a debit race");
        check(!grid.pay(List.of(10L), false), "A failed actual debit is a failed recipe payment");
        check(grid.balance.equals(BigInteger.TEN) && grid.transfers == 1, "A failed debit cannot mutate the balance or retry against the cache");

        BigInteger large = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(2));
        check(GridRecipePayment.availableAsLong(large) == Long.MAX_VALUE, "A large grid balance saturates scheduler EU without wrapping");
        check(GridRecipePayment.availableAsLong(BigInteger.valueOf(-1)) == 0, "A negative ledger value cannot grant scheduler energy");
        grid = new Grid(large);
        check(grid.pay(List.of(Long.MAX_VALUE, Long.MAX_VALUE), true), "Multiple EU contents are summed beyond signed long without wrapping");
        check(grid.pay(List.of(Long.MAX_VALUE, Long.MAX_VALUE), false) && grid.balance.signum() == 0,
                "A large multi-content tick is charged in BigInteger exactly once");
        check(grid.pay(List.of(), false) && grid.pay(List.of(0L), false) && grid.transfers == 1,
                "Zero demand does not call the transfer API");

        grid = new Grid(before);
        var otherGrid = new Grid(BigInteger.TEN);
        check(GridEnergyInputs.remove(null, grid, screenshotEUt) == screenshotEUt,
                "An isolated order pays the reported amount without a local energy container");
        check(grid.balance.equals(before.subtract(BigInteger.valueOf(screenshotEUt))), "Isolated payment uses the same exact grid ledger");
        check(GridEnergyInputs.refund(null, grid, screenshotEUt) == screenshotEUt && grid.balance.equals(before),
                "An aborted isolated tick refunds its captured grid source");
        check(otherGrid.balance.equals(BigInteger.TEN), "The refund does not change another grid");
        grid.rejectTransfer = true;
        check(GridEnergyInputs.remove(null, grid, screenshotEUt) == 0 && grid.balance.equals(before),
                "Failed isolated payment grants no energy or progress");

        var hiddenGrid = new Grid(BigInteger.valueOf(8_000_000_000L));
        EnergyContainerListView aggregate = () -> List.of(hiddenGrid);
        check(GridEnergyInputs.findIn(List.of(aggregate)) == hiddenGrid,
                "An adaptive grid hatch remains discoverable inside GTCEu's aggregate energy container");
        check(GridRecipePayment.availableAsLong(GridEnergyInputs.findIn(List.of(aggregate)).gridEnergyAvailable())
                        == 8_000_000_000L,
                "Grid preflight reads the account balance instead of the aggregate's local hatch cache");
        return assertions;
    }

    /** Mixin 0.8.5 rejects remappable shadows in a mixin with multiple targets before applying it. */
    private static void multiTargetShadowContract() {
        String resource = "/com/gtl/enhancedcore/mixin/gtmadvancedhatch/GridEnergyRecipeInputMixin.class";
        try (var input = GridRecipePaymentRegression.class.getResourceAsStream(resource)) {
            var node = new org.objectweb.asm.tree.ClassNode();
            new org.objectweb.asm.ClassReader(input).accept(node,
                    org.objectweb.asm.ClassReader.SKIP_CODE | org.objectweb.asm.ClassReader.SKIP_DEBUG);
            var mixin = node.invisibleAnnotations.stream()
                    .filter(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")).findFirst().orElseThrow();
            check(((List<?>) annotationValue(mixin, "targets")).size() == 2,
                    "The payment bridge still targets both energy and laser grid containers");
            var owner = node.fields.stream().filter(f -> f.name.equals("owner_uuid")).findFirst().orElseThrow();
            var shadow = owner.visibleAnnotations.stream()
                    .filter(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Shadow;")).findFirst().orElseThrow();
            check(Boolean.FALSE.equals(annotationValue(shadow, "remap")),
                    "Both third-party targets pass Mixin's multi-target shadow validation (default remap=true is invalid)");
        } catch (java.io.IOException e) {
            throw new AssertionError("Cannot inspect the compiled grid bridge", e);
        }
    }

    private static Object annotationValue(org.objectweb.asm.tree.AnnotationNode annotation, String key) {
        for (int i = 0; i < annotation.values.size(); i += 2) {
            if (annotation.values.get(i).equals(key)) return annotation.values.get(i + 1);
        }
        return null;
    }

    private static final class Grid implements GridEnergyRecipeInput, com.gregtechceu.gtceu.api.capability.IEnergyContainer {
        private BigInteger balance;
        private int transfers;
        private boolean rejectTransfer;

        private Grid(BigInteger balance) { this.balance = balance; }

        @Override public boolean gridEnergyConnected() { return true; }
        @Override public BigInteger gridEnergyAvailable() { return balance; }
        @Override public boolean gridEnergyTransfer(BigInteger delta) {
            transfers++;
            if (rejectTransfer || balance.add(delta).signum() < 0) return false;
            balance = balance.add(delta);
            return true;
        }

        @Override public long acceptEnergyFromNetwork(net.minecraft.core.Direction side, long voltage, long amperage) { return 0; }
        @Override public boolean inputsEnergy(net.minecraft.core.Direction side) { return true; }
        @Override public long changeEnergy(long difference) { return 0; }
        @Override public long getEnergyStored() { return 0; }
        @Override public long getEnergyCapacity() { return 268_435_456L; }
        @Override public long getInputAmperage() { return 1; }
        @Override public long getInputVoltage() { return 16_777_216L; }

        private boolean pay(List<Long> amounts, boolean simulate) {
            return GridRecipePayment.pay(amounts, simulate, this::gridEnergyAvailable, this::gridEnergyTransfer);
        }
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
