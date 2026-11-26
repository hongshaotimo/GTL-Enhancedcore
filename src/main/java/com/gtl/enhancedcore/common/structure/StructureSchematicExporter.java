package com.gtl.enhancedcore.common.structure;

import com.gregtechceu.gtceu.api.block.IMachineBlock;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gtl.enhancedcore.common.registration.RetiredMachines;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.registries.ForgeRegistries;

/** Exports the active preview geometry of every registered multiblock as Sponge schematics. */
public final class StructureSchematicExporter {
    private static final long MAX_CELLS = 13_000_000L;

    private StructureSchematicExporter() {}

    public static Result exportToDesktop() throws IOException {
        return exportToDesktop(null);
    }

    public static Result exportMissingToDesktop() throws IOException {
        Path root = Path.of(System.getProperty("user.home"), "Desktop", "GTL结构导出");
        Path previous;
        try (var folders = Files.list(root)) {
            previous = folders.filter(Files::isDirectory)
                    .max(Comparator.comparing(path -> path.getFileName().toString()))
                    .orElseThrow(() -> new IOException("No previous structure export was found"));
        }
        Path report = previous.resolve("导出清单.txt");
        if (!Files.isRegularFile(report)) throw new IOException("Previous export manifest is missing");
        Set<ResourceLocation> targets = new HashSet<>();
        boolean readingSkipped = false;
        for (String line : Files.readAllLines(report)) {
            if (line.equals("未导出的结构：")) {
                readingSkipped = true;
                continue;
            }
            if (readingSkipped && !line.isBlank()) {
                var matcher = java.util.regex.Pattern.compile("^\\s*([a-z0-9_.-]+:[a-z0-9_./-]+)")
                        .matcher(line);
                if (matcher.find()) {
                    ResourceLocation id = ResourceLocation.tryParse(matcher.group(1));
                    if (id != null) targets.add(id);
                }
            }
        }
        if (targets.isEmpty()) throw new IOException("The previous export has no skipped structures");
        return exportToDesktop(targets);
    }

    private static Result exportToDesktop(Set<ResourceLocation> targets) throws IOException {
        Path desktop = Path.of(System.getProperty("user.home"), "Desktop");
        if (!Files.isDirectory(desktop)) throw new IOException("Desktop directory was not found: " + desktop);
        Path root = desktop.resolve("GTL结构导出");
        Files.createDirectories(root);
        Path output = createRunDirectory(root);
        List<Exported> exported = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        Set<ResourceLocation> seen = new HashSet<>();

        for (var block : ForgeRegistries.BLOCKS.getValues()) {
            if (!(block instanceof IMachineBlock machineBlock)
                    || !(machineBlock.getDefinition() instanceof MultiblockMachineDefinition definition)
                    || !seen.add(definition.getId())
                    || AddStructureOwnership.isLegacy(definition.getId())
                    || RetiredMachines.contains(definition.getId().toString())) continue;
            ResourceLocation id = definition.getId();
            if (targets != null && !targets.contains(id)) continue;
            try {
                var patternFactory = definition.getPatternFactory();
                if (patternFactory == null) {
                    skipped.add(id + "\t结构图案工厂为空");
                    continue;
                }
                BlockPattern pattern = patternFactory.get();
                if (pattern == null) {
                    skipped.add(id + "\t结构图案为空");
                    continue;
                }
                int[] repetitions = NativeStructureHollowing.minimumRepetitions(pattern.aisleRepetitions);
                var matches = ((com.gtl.enhancedcore.mixin.gtceu.BlockPatternAccessor) pattern)
                        .gtlEnhancedcore$getBlockMatches();
                long repeatedDepth = 0;
                for (int repetition : repetitions) repeatedDepth += repetition;
                long estimatedCells = (long) matches[0].length * matches[0][0].length * repeatedDepth;
                if (estimatedCells > MAX_CELLS) {
                    skipped.add(id + "\t超出单图上限: " + estimatedCells + " 格");
                    continue;
                }
                BlockInfo[][][] preview = pattern.getPreview(repetitions);
                Dimensions dimensions = dimensions(preview);
                if (dimensions.volume() > MAX_CELLS) {
                    skipped.add(id + "\t超出单图上限: " + dimensions.volume() + " 格");
                    continue;
                }
                String displayName = definition.getBlock().getName().getString();
                if (displayName.isBlank() || displayName.startsWith("block."))
                    displayName = fallbackName(id);
                String safeName = displayName.replaceAll("[\\\\/:*?\"<>|]", "_");
                String fileName = safeName + "_" + id.getNamespace() + "__"
                        + id.getPath().replace('/', '_') + ".schem";
                writeSchematic(output.resolve(fileName), id, preview, dimensions);
                exported.add(new Exported(id, displayName, fileName, dimensions));
            } catch (RuntimeException | LinkageError failure) {
                skipped.add(id + "\t" + failure.getClass().getSimpleName() + ": " + safeMessage(failure));
            }
        }

        exported.sort(Comparator.comparingLong((Exported item) -> item.dimensions().volume())
                .thenComparing(item -> item.id().toString()));
        List<String> report = new ArrayList<>();
        report.add("GTL Enhanced 当前注册多方块结构导出");
        report.add("来源：当前运行实例中 GTCEu、GTLCore、GTLAdditions 及 GTL-Enhancedcore 的注册结构");
        report.add("格式：WorldEdit Sponge schematic v2；按当前结构预览的首选方块候选导出；重复结构取最小重复数。");
        report.add("已导出：" + exported.size() + "；未导出：" + skipped.size());
        report.add("");
        report.add("从小到大：");
        for (int i = 0; i < exported.size(); i++) {
            Exported item = exported.get(i);
            String rankedName = String.format(Locale.ROOT, "%04d_%s", i + 1, item.fileName());
            Files.move(output.resolve(item.fileName()), output.resolve(rankedName));
            report.add(String.format(Locale.ROOT, "%04d\t%s\t%s\t%dx%dx%d\t%d 格\t%s",
                    i + 1, item.displayName(), item.id(), item.dimensions().width(),
                    item.dimensions().height(), item.dimensions().length(),
                    item.dimensions().volume(), rankedName));
        }
        if (!skipped.isEmpty()) {
            report.add("");
            report.add("未导出的结构：");
            report.addAll(skipped);
        }
        Files.write(output.resolve("导出清单.txt"), report);
        return new Result(output, exported.size(), skipped.size());
    }

    private static Path createRunDirectory(Path root) throws IOException {
        String base = "导出-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        for (int suffix = 0; ; suffix++) {
            Path candidate = root.resolve(suffix == 0 ? base : base + "-" + suffix);
            try {
                return Files.createDirectory(candidate);
            } catch (java.nio.file.FileAlreadyExistsException ignored) {
                // Keep previous exports and create a new output folder.
            }
        }
    }

    private static Dimensions dimensions(BlockInfo[][][] blocks) throws IOException {
        if (blocks.length == 0 || blocks[0].length == 0 || blocks[0][0].length == 0)
            throw new IOException("Empty preview");
        int width = blocks.length;
        int height = blocks[0].length;
        int length = blocks[0][0].length;
        long volume = (long) width * height * length;
        if (width > Short.MAX_VALUE || height > Short.MAX_VALUE || length > Short.MAX_VALUE
                || volume > Integer.MAX_VALUE) throw new IOException("Unsupported dimensions");
        for (BlockInfo[][] plane : blocks) {
            if (plane.length != height) throw new IOException("Ragged preview height");
            for (BlockInfo[] row : plane) if (row.length != length) throw new IOException("Ragged preview length");
        }
        return new Dimensions(width, height, length, volume);
    }

    private static void writeSchematic(Path path, ResourceLocation id, BlockInfo[][][] blocks,
                                       Dimensions dimensions) throws IOException {
        LinkedHashMap<String, Integer> palette = new LinkedHashMap<>();
        palette.put("minecraft:air", 0);
        int[] indexes = new int[(int) dimensions.volume()];
        int cursor = 0;
        for (int y = 0; y < dimensions.height(); y++) {
            for (int z = 0; z < dimensions.length(); z++) {
                for (int x = 0; x < dimensions.width(); x++) {
                    BlockInfo info = blocks[x][y][z];
                    BlockState state = info == null ? null : info.getBlockState();
                    String stateName = state == null ? "minecraft:air" : stateName(state);
                    indexes[cursor++] = palette.computeIfAbsent(stateName, ignored -> palette.size());
                }
            }
        }
        CompoundTag root = new CompoundTag();
        root.putInt("Version", 2);
        root.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        root.putShort("Width", (short) dimensions.width());
        root.putShort("Height", (short) dimensions.height());
        root.putShort("Length", (short) dimensions.length());
        root.putIntArray("Offset", new int[]{0, 0, 0});
        CompoundTag metadata = new CompoundTag();
        metadata.putString("Name", id.toString());
        root.put("Metadata", metadata);
        CompoundTag encodedPalette = new CompoundTag();
        palette.forEach(encodedPalette::putInt);
        root.put("Palette", encodedPalette);
        root.putInt("PaletteMax", palette.size());
        root.putByteArray("BlockData", encodeVarints(indexes));
        ListTag blockEntities = new ListTag();
        for (int y = 0; y < dimensions.height(); y++) {
            for (int z = 0; z < dimensions.length(); z++) {
                for (int x = 0; x < dimensions.width(); x++) {
                    BlockInfo info = blocks[x][y][z];
                    if (info == null || !info.hasBlockEntity()) continue;
                    var entity = info.getBlockEntity(new BlockPos(x, y, z));
                    if (entity == null) continue;
                    CompoundTag tag = entity.saveWithFullMetadata();
                    tag.putInt("x", x);
                    tag.putInt("y", y);
                    tag.putInt("z", z);
                    blockEntities.add(tag);
                }
            }
        }
        root.put("BlockEntities", blockEntities);
        NbtIo.writeCompressed(root, path.toFile());
    }

    private static String stateName(BlockState state) {
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (state.getValues().isEmpty()) return blockId.toString();
        return blockId + "[" + state.getValues().entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().getName()))
                .map(entry -> propertyText(entry.getKey(), entry.getValue()))
                .reduce((left, right) -> left + "," + right).orElse("") + "]";
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String propertyText(Property property, Comparable value) {
        return property.getName() + "=" + property.getName(value);
    }

    private static byte[] encodeVarints(int[] values) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (int value : values) {
            int remaining = value;
            while ((remaining & ~0x7f) != 0) {
                output.write((remaining & 0x7f) | 0x80);
                remaining >>>= 7;
            }
            output.write(remaining);
        }
        return output.toByteArray();
    }

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null ? "无详细信息" : message.replace('\n', ' ').replace('\r', ' ');
    }

    private static String fallbackName(ResourceLocation id) {
        if (id.equals(new ResourceLocation("gtceu", "charcoal_pile_igniter"))) return "木炭堆点火器";
        if (id.equals(new ResourceLocation("gtladditions", "arcanic_astrograph"))) return "奥术星图";
        if (id.equals(new ResourceLocation("gtladditions", "heart_of_the_universe"))) return "宇宙之心";
        return id.getPath();
    }

    private record Dimensions(int width, int height, int length, long volume) {}
    private record Exported(ResourceLocation id, String displayName, String fileName, Dimensions dimensions) {}
    public record Result(Path directory, int exported, int skipped) {}
}
