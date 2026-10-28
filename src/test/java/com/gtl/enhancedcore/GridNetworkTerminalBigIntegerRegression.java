package com.gtl.enhancedcore;

import com.google.gson.JsonParser;
import com.gtl.enhancedcore.common.recipe.GridNetworkTerminalPayment;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class GridNetworkTerminalBigIntegerRegression {
    private static int assertions;

    static int run() throws Exception {
        BigInteger overLong = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(42));
        var payment = GridNetworkTerminalPayment.plan(List.of(Long.MAX_VALUE, 42L), overLong, false);
        check(payment.valid(), "A positive BigInteger request is valid");
        check(payment.withdrawn().equals(overLong), "Long list amounts sum exactly beyond Long.MAX_VALUE");
        check(payment.remaining() == null, "A fully funded request returns null as the upstream success sentinel");

        payment = GridNetworkTerminalPayment.plan(List.of(Long.MAX_VALUE, 42L),
                BigInteger.valueOf(Long.MAX_VALUE), false);
        check(payment.withdrawn().equals(BigInteger.valueOf(Long.MAX_VALUE)), "Partial balance is charged exactly");
        check(payment.remaining().equals(List.of(42L)), "Partial payment returns only the unmet amount");

        payment = GridNetworkTerminalPayment.plan(List.of(Long.MAX_VALUE, 42L),
                BigInteger.valueOf(Long.MAX_VALUE), true);
        check(payment.withdrawn().signum() == 0, "Simulation never withdraws energy");
        check(payment.remaining().equals(List.of(42L)), "Simulation reports the same unmet amount as execution");

        payment = GridNetworkTerminalPayment.plan(List.of(Long.MAX_VALUE, 42L), BigInteger.ZERO, false);
        check(payment.withdrawn().signum() == 0, "An empty grid has no debit");
        check(payment.remaining().equals(List.of(Long.MAX_VALUE, 42L)),
                "Missing energy above long range remains exact for the next handler");

        payment = GridNetworkTerminalPayment.plan(List.of(Long.MAX_VALUE, Long.MAX_VALUE),
                BigInteger.ZERO, false);
        check(payment.remaining().equals(List.of(Long.MAX_VALUE, Long.MAX_VALUE)),
                "An empty terminal preserves both full-sized energy debts");
        payment = GridNetworkTerminalPayment.plan(payment.remaining(), BigInteger.valueOf(Long.MAX_VALUE), false);
        check(payment.remaining().equals(List.of(Long.MAX_VALUE)),
                "A second terminal funded for half the original demand cannot falsely report fully paid");
        payment = GridNetworkTerminalPayment.plan(payment.remaining(), BigInteger.valueOf(Long.MAX_VALUE), false);
        check(payment.remaining() == null, "A final handler must pay the entire preserved residual debt");

        payment = GridNetworkTerminalPayment.plan(List.of(5L, -1L), BigInteger.TEN, false);
        check(!payment.valid() && payment.withdrawn().signum() == 0,
                "Malformed negative inputs cannot credit the network or create a negative shortfall");
        var mixins = JsonParser.parseString(Files.readString(Path.of("src/main/resources/gtl_enhancedcore.mixins.json")))
                .getAsJsonObject().getAsJsonArray("mixins");
        check(mixins.asList().stream().anyMatch(value -> value.getAsString().equals("gtladditions.GridNetworkTerminalBigIntegerMixin")),
                "The wireless terminal BigInteger payment hook is registered");
        String mixinSource = Files.readString(Path.of(
                "src/main/java/com/gtl/enhancedcore/mixin/gtladditions/GridNetworkTerminalBigIntegerMixin.java"));
        check(!mixinSource.contains("static GridNetworkTerminalPayment.Payment plan("),
                "Payment planning stays outside the Mixin target");
        return assertions;
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
