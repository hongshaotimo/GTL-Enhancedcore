package com.gtl.enhancedcore.common.recipe.iv;

import appeng.api.stacks.AEKey;
import com.google.gson.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.*;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.appender.rolling.*;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachineBase;

/** Dedicated JSONL trace. Does not reconfigure root logging or emit events for ordinary machines. */
public final class IvTaskLog {
    private static final Gson JSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final String SESSION = UUID.randomUUID().toString();
    private static final String VERSION = net.minecraftforge.fml.ModList.get().getModContainerById("gtl_enhancedcore")
            .map(container -> container.getModInfo().getVersion().toString()).orElse("unknown");
    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static final Logger LOG = createLogger();
    public static final boolean TRACE_TICKS = Boolean.parseBoolean(System.getProperty("gtl.enhancedcore.iv.traceTicks", "false"));
    private IvTaskLog() {}
    private static Logger createLogger() {
        String name = "GTL-Enhancedcore-IV-Tasks";
        try {
            LoggerContext context = (LoggerContext)LogManager.getContext(false);
            var configuration = context.getConfiguration();
            String directory = FMLPaths.GAMEDIR.get().resolve("logs/gtl-enhancedcore").toString();
            var appender = RollingFileAppender.newBuilder().setName(name)
                    .withFileName(directory + "/iv-tasks.jsonl")
                    .withFilePattern(directory + "/iv-tasks-%i.jsonl.gz")
                    .withPolicy(SizeBasedTriggeringPolicy.createPolicy("32 MB"))
                    .withStrategy(DefaultRolloverStrategy.newBuilder().withMax("16").withConfig(configuration).build())
                    .setLayout(PatternLayout.newBuilder().withCharset(java.nio.charset.StandardCharsets.UTF_8).withPattern("%m%n").build())
                    .setConfiguration(configuration).build();
            appender.start(); configuration.addAppender(appender);
            LoggerConfig logger = new LoggerConfig(name, Level.INFO, false);
            logger.addAppender(appender, Level.INFO, null);
            configuration.addLogger(name, logger); context.updateLoggers();
        } catch (RuntimeException failure) {
            LogManager.getLogger(name).error("Cannot create IV task log; using the normal game log", failure);
        }
        return LogManager.getLogger(name);
    }
    public static void event(MEPatternBufferPartMachineBase buffer, IvJob job, String step, String result, Object... fields) {
        try { writeEvent(buffer, job, step, result, fields); }
        catch (RuntimeException failure) { LogManager.getLogger("GTL-Enhancedcore").error("IV diagnostic event could not be written: " + step, failure); }
    }
    private static void writeEvent(MEPatternBufferPartMachineBase buffer, IvJob job, String step, String result, Object... fields) {
        JsonObject record = new JsonObject();
        record.addProperty("time", Instant.now().toString()); record.addProperty("session", SESSION);
        record.addProperty("version", VERSION);
        record.addProperty("sequence", SEQUENCE.incrementAndGet()); record.addProperty("step", step); record.addProperty("result", result);
        if (buffer != null) {
            if (buffer.getLevel() != null) {
                record.addProperty("dimension", String.valueOf(buffer.getLevel().dimension().location()));
                record.addProperty("tick", buffer.getLevel().getGameTime());
            }
            record.addProperty("buffer", buffer.getPos().toShortString());
            IvBufferState state = IvBuffers.state(buffer);
            if (state != null) { record.addProperty("bufferId", state.identity.toString()); record.addProperty("owner", state.owner); }
        }
        if (job != null) {
            record.addProperty("task", job.id.toString()); record.addProperty("slot", job.slot);
            record.addProperty("recipe", job.recipe.id.toString()); record.addProperty("type", job.recipe.recipeType.registryName.toString());
        }
        for (int i = 0; i + 1 < fields.length; i += 2) record.add(String.valueOf(fields[i]), JSON.toJsonTree(fields[i + 1]));
        LOG.info(JSON.toJson(record));
    }
    public static List<Map<String, Object>> stock(Map<AEKey, Long> stock) {
        List<Map<String, Object>> result = new ArrayList<>();
        stock.forEach((key, amount) -> result.add(Map.of("key", key.toTagGeneric().toString(), "amount", amount)));
        return result;
    }
    public static void error(MEPatternBufferPartMachineBase buffer, IvJob job, String step, RuntimeException failure) {
        event(buffer, job, step, "ERROR", "error", failure.toString(), "stack", Arrays.stream(failure.getStackTrace()).map(Object::toString).toList());
    }
}
