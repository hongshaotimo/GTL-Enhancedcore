package com.gtl.enhancedcore.audit;

import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import com.gtl.enhancedcore.common.recipe.iv.IvBuffers;
import com.gtl.enhancedcore.common.recipe.iv.IvJob;
import com.gtl.enhancedcore.common.recipe.iv.IvRecipeLogic;
import com.gtl.enhancedcore.common.recipe.iv.IvTimeAcceleration;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;

/** One narrow production-cost check using real registered IV logic and its actual buffer queue. */
public final class IvTimeAccelerationChecks {
    private IvTimeAccelerationChecks() {}

    public static Map<String, Object> run() throws Exception {
        var fixture = StellarIvGuiChecks.fixture("gtl_enhancedcore:hydrothermal_leaching_complex", false, false, true);
        try {
            var machine = fixture.machine();
            var logic = machine.getRecipeLogic();
            if (!(logic instanceof IvRecipeLogic)) throw new AssertionError("Fixture did not create registered self-owned IV logic");
            var buffer = (MEPatternBufferPartMachine) fixture.holders().get(1).getMetaMachine();
            var jobs = IvBuffers.state(buffer).jobs;
            var recipe = GTRecipeBuilder.of(new ResourceLocation("gtl_enhancedcore", "iv_time_acceleration_cost_probe"),
                    machine.getRecipeType()).EUt(32).duration(20).buildRawRecipe();
            var stellar = new IvJob(0, recipe, new CompoundTag(), Map.of(), Map.of(), 1);
            stellar.parallel = 1; stellar.remaining = 0; stellar.duration = 20;
            stellar.ivGalaxy = true; stellar.eut = Long.MAX_VALUE;
            stellar.ivGalaxyEUt = BigInteger.TEN.pow(32).add(BigInteger.valueOf(17));
            var wireless = new IvJob(1, recipe, new CompoundTag(), Map.of(), Map.of(), 1);
            wireless.parallel = 1; wireless.remaining = 0; wireless.duration = 20;
            wireless.nativeWireless = true; wireless.eut = 0;
            wireless.nativeWirelessEUt = BigInteger.TEN.pow(31).add(BigInteger.valueOf(23));
            var ordinary = new IvJob(2, recipe, new CompoundTag(), Map.of(), Map.of(), 1);
            ordinary.parallel = 1; ordinary.remaining = 0; ordinary.duration = 20; ordinary.eut = 73;
            jobs.add(stellar); jobs.add(wireless); jobs.add(ordinary);
            logic.setStatus(RecipeLogic.Status.WORKING);
            var cost = IvTimeAcceleration.class.getDeclaredMethod("cost", RecipeLogic.class);
            cost.setAccessible(true);
            BigInteger expected = stellar.ivGalaxyEUt.add(wireless.nativeWirelessEUt)
                    .add(BigInteger.valueOf(ordinary.eut)).multiply(BigInteger.valueOf(8));
            BigInteger actual = (BigInteger) cost.invoke(null, logic);
            if (!actual.equals(expected)) throw new AssertionError("Wide IV bottle surcharge mismatch: " + actual + " != " + expected);
            var result = new LinkedHashMap<String, Object>();
            result.put("status", "PASS");
            result.put("machine", machine.getDefinition().getId().toString());
            result.put("logic", logic.getClass().getName());
            result.put("stellarEUt", stellar.ivGalaxyEUt.toString());
            result.put("nativeWirelessEUt", wireless.nativeWirelessEUt.toString());
            result.put("ordinaryEUt", ordinary.eut);
            result.put("expectedEightfoldCost", expected.toString());
            result.put("actualEightfoldCost", actual.toString());
            return result;
        } finally {
            for (var holder : fixture.holders()) holder.setLevel(null);
        }
    }
}
