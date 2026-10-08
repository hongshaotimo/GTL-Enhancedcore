package com.gtl.enhancedcore;

import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.misc.EnergyContainerList;
import com.gtl.enhancedcore.common.recipe.WeatherAnchorPower;
import java.lang.reflect.Proxy;
import java.util.List;

/** Exercises the real GT energy aggregation, including its voltage/amperage folding. */
final class WeatherAnchorPowerRegression {
    private static final long ZPM = 131072L;
    private static final long COST = ZPM * 4;

    static int run() {
        Cell low = new Cell(ZPM / 4, 16, COST * 2, Long.MAX_VALUE);
        EnergyContainerList folded = new EnergyContainerList(List.of(low.proxy()));
        check(folded.getInputVoltage() >= ZPM, "reproduce folded low-voltage high-amperage input");
        check(!WeatherAnchorPower.tryConsume(folded, ZPM, COST) && low.stored == COST * 2,
                "LuV high amperage must not bypass ZPM voltage");
        Cell first = new Cell(ZPM, 2, COST / 2, Long.MAX_VALUE);
        Cell second = new Cell(ZPM, 2, COST / 2, Long.MAX_VALUE);
        EnergyContainerList pair = new EnergyContainerList(List.of(first.proxy(),second.proxy()));
        check(WeatherAnchorPower.tryConsume(pair,ZPM,COST) && first.stored == 0 && second.stored == 0,
                "two ZPM 2A hatches pay exactly one tick together");
        check(!WeatherAnchorPower.tryConsume(pair,ZPM,COST),"empty buffers stop the field");
        Cell shortBuffer = new Cell(ZPM,4,COST-1,Long.MAX_VALUE);
        check(!WeatherAnchorPower.tryConsume(new EnergyContainerList(List.of(shortBuffer.proxy())),ZPM,COST)
                && shortBuffer.stored == COST-1,"underfunded tick never debits");
        Cell partial = new Cell(ZPM,4,COST,COST/2);
        check(!WeatherAnchorPower.tryConsume(new EnergyContainerList(List.of(partial.proxy())),ZPM,COST)
                && partial.stored == COST,"partial third-party debit is refunded without activating");
        check(!WeatherAnchorPower.tryConsume(null,ZPM,COST),"unformed energy container");
        return 7;
    }

    private static void check(boolean ok,String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static final class Cell {
        final long voltage,amps,maxDebit;
        long stored;
        Cell(long voltage,long amps,long stored,long maxDebit) {
            this.voltage=voltage;this.amps=amps;this.stored=stored;this.maxDebit=maxDebit;
        }
        IEnergyContainer proxy() {
            return (IEnergyContainer) Proxy.newProxyInstance(IEnergyContainer.class.getClassLoader(),
                    new Class<?>[]{IEnergyContainer.class},(proxy,method,args) -> {
                        return switch (method.getName()) {
                            case "getInputVoltage" -> voltage;
                            case "getInputAmperage" -> amps;
                            case "getOutputVoltage", "getOutputAmperage" -> 0L;
                            case "getEnergyStored" -> stored;
                            case "changeEnergy" -> {
                                long delta=(Long) args[0];
                                if (delta<0) delta=-Math.min(-delta,Math.min(stored,maxDebit));
                                stored+=delta;
                                yield delta;
                            }
                            default -> throw new UnsupportedOperationException(method.getName());
                        };
                    });
        }
    }
}
