package com.gtl.enhancedcore.audit;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableEnergyContainer;
import com.gregtechceu.gtceu.api.misc.EnergyContainerList;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gtl.enhancedcore.common.cloud.CloudPowerAccess;
import com.gtladd.gtladditions.api.machine.trait.IWirelessNetworkEnergyHandler;
import com.gtladd.gtladditions.common.machine.multiblock.controller.bs.BiosphereIIIController;
import com.gtladd.gtladditions.common.machine.multiblock.controller.bs.BiosphereIIIModule;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/** Real transformed host/module payment methods with controlled state, not a BS structure-formation test. */
public final class CloudModulePowerRuntimeChecks {
    private CloudModulePowerRuntimeChecks() {}
    public static void run(ServerLevel level, List<String> checks) throws Exception {
        var hostHolder = holder(level, "biosphere_iii", new BlockPos(128, 80, 112));
        var moduleHolder = holder(level, "garden_of_hermes", new BlockPos(130, 80, 112));
        try {
            var host = (BiosphereIIIController)hostHolder.getMetaMachine();
            var module = (BiosphereIIIModule)moduleHolder.getMetaMachine();
            require((Object)module instanceof CloudPowerAccess, "Actual registered BS module receives the external-host power adapter", checks);
            var power = (CloudPowerAccess)(Object)module;
            var energy = NotifiableEnergyContainer.receiverContainer(host, 100000, 33554432, 1);
            energy.setEnergyStored(10000);
            host.getCapabilitiesProxy().put(IO.IN, EURecipeCapability.CAP, List.of(energy));
            field(WorkableElectricMultiblockMachine.class, "energyContainer").set(host, new EnergyContainerList(List.of(energy)));
            field(WorkableElectricMultiblockMachine.class, "tier").setInt(host, 10);
            field(MultiblockControllerMachine.class, "isFormed").setBoolean(host, true);
            field(MultiblockControllerMachine.class, "isFormed").setBoolean(module, true);
            module.setHost(host); host.getModuleSet().add(module); host.setWorkingEnabled(true); module.setWorkingEnabled(true);
            source(host, "WIRED");
            require(host.getTier() >= 10 && power.cloudPowerLimit() > 0 && power.cloudPowerLimit() == module.getOverclockVoltage()
                            && host.getEnergyContainer().getEnergyStored() == 10000
                            && (module.getEnergyContainer() == null || module.getEnergyContainer().getEnergyStored() == 0),
                    "Controlled real module uses its host's native per-module budget while its local energy storage is empty", checks);
            var payment = BigInteger.valueOf(128); var before = sample(host);
            require(power.cloudPowerReady(payment) && energy.getEnergyStored() == 10000 && sample(host).equals(before),
                    "Host payment simulation neither debits physical EU nor changes native host statistics"
                            + " [ready=" + power.cloudPowerReady(payment) + ", limit=" + power.cloudPowerLimit()
                            + ", source=" + host.getEnergyContainer().getEnergyStored() + ", physical=" + energy.getEnergyStored()
                            + ", sample=" + sample(host) + ", before=" + before + "]", checks);
            var receipt = power.cloudPowerPay(payment);
            require(receipt != null && energy.getEnergyStored() == 9872 && sample(host).equals(before.add(payment)),
                    "Real wired host requestEnergy debits exactly once and records native host energy statistics", checks);
            source(host, "NONE"); module.setHost(null); receipt.refund(); receipt.refund();
            require(energy.getEnergyStored() == 10000 && sample(host).equals(before),
                    "A wired receipt refunds its captured host/source and statistics exactly once after binding/source changes", checks);
            module.setHost(host); source(host, "WIRED"); energy.setEnergyStored(64);
            require(!power.cloudPowerReady(payment) && power.cloudPowerPay(payment) == null && energy.getEnergyStored() == 64,
                    "Insufficient real host energy refuses payment without partial debit", checks);
            energy.setEnergyStored(10000); host.setWorkingEnabled(false);
            require(!power.cloudPowerReady(payment) && power.cloudPowerPay(payment) == null,
                    "Stopped host closes module payment admission", checks);
            host.setWorkingEnabled(true);

            var wireless = new Wireless(); source(host, "WIRELESS");
            field(BiosphereIIIController.class, "wirelessNetworkEnergyHandler").set(host, wireless);
            var huge = BigInteger.ONE.shiftLeft(70); var balance = wireless.balance; before = sample(host);
            require(power.cloudPowerReady(huge) && wireless.balance.equals(balance),
                    "Real wireless host simulation preserves arbitrary-precision payment amounts", checks);
            var wirelessReceipt = power.cloudPowerPay(huge);
            require(wirelessReceipt != null && wireless.balance.equals(balance.subtract(huge)) && sample(host).equals(before.add(huge)),
                    "Real requestWirelessEnergy consumes the exact BigInteger amount and records host statistics", checks);
            source(host, "WIRED"); module.setHost(null); wirelessReceipt.refund(); wirelessReceipt.refund();
            require(wireless.balance.equals(balance) && sample(host).equals(before),
                    "Wireless rollback credits the captured original handler and reverses host statistics once after source changes", checks);
            module.setHost(host); source(host, "WIRELESS"); wireless.online = false;
            require(!power.cloudPowerReady(payment) && power.cloudPowerPay(payment) == null && wireless.balance.equals(balance),
                    "Offline wireless host refuses payment without changing its balance", checks);
        } finally { hostHolder.setLevel(null); moduleHolder.setLevel(null); }
    }
    private static BigInteger sample(BiosphereIIIController host) throws Exception {
        return (BigInteger)field(BiosphereIIIController.class, "currentSampleEnergy").get(host);
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void source(BiosphereIIIController host, String name) throws Exception {
        var source = field(BiosphereIIIController.class, "energySource");
        source.set(host, Enum.valueOf((Class)source.getType(), name));
    }
    private static Field field(Class<?> type, String name) throws Exception {
        var field = type.getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static MetaMachineBlockEntity holder(ServerLevel level, String id, BlockPos pos) {
        var definition = GTRegistries.MACHINES.get(new ResourceLocation("gtladditions", id));
        if (definition == null) throw new AssertionError("Missing actual registered machine gtladditions:" + id);
        var holder = (MetaMachineBlockEntity)definition.getBlockEntityType().create(pos, definition.defaultBlockState());
        if (holder == null) throw new AssertionError("Missing actual module holder " + id); holder.setLevel(level); return holder;
    }
    private static void require(boolean condition, String label, List<String> checks) {
        if (!condition) throw new AssertionError(label); checks.add(label);
    }
    /** Only the external network balance is deterministic; real host methods perform all transfers/statistics. */
    private static final class Wireless implements IWirelessNetworkEnergyHandler {
        BigInteger balance = BigInteger.ONE.shiftLeft(90); boolean online = true;
        public boolean consumeEnergy(int change) { return consumeEnergy(BigInteger.valueOf(change)); }
        public boolean consumeEnergy(long change) { return consumeEnergy(BigInteger.valueOf(change)); }
        public boolean consumeEnergy(BigInteger change) {
            var next = balance.add(change); if (!online || next.signum() < 0) return false; balance = next; return true;
        }
        public BigInteger getMaxAvailableEnergy() { return balance; }
        public boolean isOnline() { return online; }
    }
}
