package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.recipe.GalaxyBatchAccounting;
import com.gtl.enhancedcore.common.structure.StructureData;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.Random;

final class GalaxyBatchAccountingRegression {
    static int run() throws Exception {
        int assertions = 0;
        var random = new Random(0x67616c617879L);
        for (int i = 0; i < 10000; i++) {
            long power = 1 + random.nextLong(Long.MAX_VALUE - 1);
            int minimum = 1 + random.nextInt(100000);
            BigInteger energy = BigInteger.valueOf(power).multiply(BigInteger.valueOf(1 + random.nextInt(100000)))
                    .add(BigInteger.valueOf(random.nextInt(100000)));
            var plan = GalaxyBatchAccounting.plan(energy, minimum, power);
            if (plan == null || plan.duration() < minimum || plan.eut() < 0 || plan.eut() > power
                    || BigInteger.valueOf(plan.eut()).multiply(BigInteger.valueOf(plan.duration())).compareTo(energy) < 0)
                throw new AssertionError("Galaxy batching must conserve energy within physical input power");
            assertions++;
        }
        if (GalaxyBatchAccounting.plan(BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(Integer.MAX_VALUE)), 1, Long.MAX_VALUE).duration() != Integer.MAX_VALUE)
            throw new AssertionError("Exact maximum batch must fit");
        if (GalaxyBatchAccounting.plan(BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(Integer.MAX_VALUE)).add(BigInteger.ONE), 1, Long.MAX_VALUE) != null)
            throw new AssertionError("Overflow must reject before withdrawing materials");
        if (GalaxyBatchAccounting.plan(BigInteger.ZERO, 20, 1).eut() != 0) throw new AssertionError("Zero energy must remain zero");
        BigInteger maxPlus16 = BigInteger.ONE.shiftLeft(63);
        var huge = GalaxyBatchAccounting.plan(maxPlus16.multiply(BigInteger.valueOf(20)).subtract(BigInteger.ONE), 20, maxPlus16);
        if (huge == null || huge.duration() != 20 || !huge.eut().equals(maxPlus16))
            throw new AssertionError("MAX+16 must admit BigInteger power beyond signed long");
        var stretched = GalaxyBatchAccounting.plan(maxPlus16.multiply(BigInteger.valueOf(21)), 20, maxPlus16);
        if (stretched == null || stretched.duration() != 21 || stretched.eut().compareTo(maxPlus16) > 0)
            throw new AssertionError("Power ceiling extends duration instead of supplying free energy");
        BigInteger energyLeft = maxPlus16.multiply(BigInteger.valueOf(20)).subtract(BigInteger.valueOf(7));
        BigInteger initialEnergy = energyLeft, paid = BigInteger.ZERO;
        for (int tick = 0; tick < huge.duration(); tick++) {
            BigInteger payment = huge.eut().min(energyLeft);
            paid = paid.add(payment); energyLeft = energyLeft.subtract(payment);
        }
        if (energyLeft.signum() != 0 || !paid.equals(initialEnergy))
            throw new AssertionError("Final BigInteger tick pays only remaining energy exactly");
        try (var input = Files.newInputStream(Path.of("src/main/resources/data/gtl_enhancedcore/structures/retired_light_hunter.pattern.gz"))) {
            var rows = new java.util.ArrayList<String[]>();
            StructureData.read(input).forEachAisle(rows::add);
            if (rows.size() != 1 || rows.getFirst().length != 1 || !rows.getFirst()[0].equals("C!"))
                throw new AssertionError("Retirement must retain only its controller and impossible cell");
        }
        return assertions + 7 + RetiredMachinesRegression.run() + ChamberPositionsRegression.run();
    }
}
