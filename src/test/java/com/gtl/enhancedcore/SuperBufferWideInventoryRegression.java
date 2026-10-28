package com.gtl.enhancedcore;

import com.google.gson.JsonParser;
import com.gtl.enhancedcore.common.recipe.iv.SuperBufferLongBatch;
import java.math.BigInteger;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gtlcore.gtlcore.utils.datastructure.Int128;

final class SuperBufferWideInventoryRegression {
    private static int assertions;

    static int run() throws Exception {
        assertions = 0;
        check(SuperBufferLongBatch.available(Int128.ZERO()) == 0, "Empty wide stock exposes no input batch");
        check(SuperBufferLongBatch.available(new Int128(-1, -1)) == 0, "Negative/corrupt wide stock grants no inputs");
        check(SuperBufferLongBatch.available(new Int128(Long.MAX_VALUE)) == Long.MAX_VALUE,
                "A long-sized amount remains exact");
        check(SuperBufferLongBatch.available(new Int128(0, Long.MIN_VALUE)) == Long.MAX_VALUE,
                "Positive stock just above signed long exposes one full batch");
        check(SuperBufferLongBatch.available(new Int128(1, 17)) == Long.MAX_VALUE,
                "Larger positive Int128 stock saturates the batch instead of wrapping negative");

        BigInteger initial = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO).add(BigInteger.valueOf(17));
        Int128 stock = Int128.fromBigInteger(initial);
        long[] batches = new long[3];
        for (int index = 0; index < batches.length; index++) {
            batches[index] = SuperBufferLongBatch.available(stock);
            check(SuperBufferLongBatch.consume(stock, batches[index]), "A valid long batch is consumed from wide stock");
        }
        check(List.of(batches[0], batches[1], batches[2]).equals(
                        List.of(Long.MAX_VALUE, Long.MAX_VALUE, 17L)) && stock.isZero(),
                "Consumed batches reveal the retained Int128 remainder until all stock is processed");
        var mixins = JsonParser.parseString(Files.readString(Path.of("src/main/resources/gtl_enhancedcore.mixins.json")))
                .getAsJsonObject().getAsJsonArray("mixins");
        check(mixins.asList().stream().anyMatch(value -> value.getAsString().equals("gtladditions.SuperBufferWideInventoryMixin")),
                "The wide stock batch projection is registered");
        check(mixins.asList().stream().anyMatch(value -> value.getAsString().equals("gtladditions.SuperBufferWideLimitInventoryMixin")),
                "Wide limit views avoid narrowing Int128 values");
        return assertions;
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
