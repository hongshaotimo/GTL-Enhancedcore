package com.gtl.enhancedcore.common.world.plot;

/** Display-sized raster of the actual terrain, without synthetic marker overlays. */
public final class PlotPreviewRaster {
    public static final int MAX_PIXELS = 1024;
    public record CampusViewport(double minX, double minZ, double width, double depth) {}
    private PlotPreviewRaster() {}

    /**
     * Palette order follows {@link PlotLayout.Cell}; output is opaque ARGB.
     * When shrinking, exact block coverage is averaged instead of dropping narrow roads or dashes.
     * Enlarged previews use nearest blocks so their square edges remain sharp.
     */
    public static int[] render(PlotSettings settings, int pixels, int plotsPerSide, int[] palette) {
        if (pixels < 1 || pixels > MAX_PIXELS || plotsPerSide < 1 || plotsPerSide > 2
                || palette.length != PlotLayout.Cell.values().length)
            throw new IllegalArgumentException("Invalid plot preview raster dimensions or palette");
        if (settings.isCampus()) return campus(settings, pixels, plotsPerSide == 2);
        int cells = settings.plotBlocks() * plotsPerSide;
        int[] result = new int[pixels * pixels];
        if (pixels >= cells) {
            for (int z = 0; z < pixels; z++) for (int x = 0; x < pixels; x++) {
                int wx = (int) ((2L * x + 1) * cells / (2L * pixels));
                int wz = (int) ((2L * z + 1) * cells / (2L * pixels));
                result[z * pixels + x] = 0xff000000 | palette[PlotLayout.surfaceAt(settings, wx, wz).ordinal()];
            }
            return result;
        }
        // Common integer coordinates: one block is pixels units, one output pixel is cells units.
        // This makes partial coverage exact, even for odd target sizes and one-block markers.
        for (int z = 0; z < pixels; z++) for (int x = 0; x < pixels; x++) {
            int left = x * cells, right = left + cells, top = z * cells, bottom = top + cells;
            long red = 0, green = 0, blue = 0;
            for (int wz = top / pixels; wz < (bottom + pixels - 1) / pixels; wz++) {
                int weightZ = Math.min(bottom, (wz + 1) * pixels) - Math.max(top, wz * pixels);
                for (int wx = left / pixels; wx < (right + pixels - 1) / pixels; wx++) {
                    int weightX = Math.min(right, (wx + 1) * pixels) - Math.max(left, wx * pixels);
                    int rgb = palette[PlotLayout.surfaceAt(settings, wx, wz).ordinal()];
                    long weight = (long) weightX * weightZ;
                    red += ((rgb >>> 16) & 255) * weight;
                    green += ((rgb >>> 8) & 255) * weight;
                    blue += (rgb & 255) * weight;
                }
            }
            long total = (long) cells * cells;
            result[z * pixels + x] = 0xff000000 | ((int) ((red + total / 2) / total) << 16)
                    | ((int) ((green + total / 2) / total) << 8) | (int) ((blue + total / 2) / total);
        }
        return result;
    }

    private static int[] campus(PlotSettings settings, int pixels, boolean overview) {
        CampusViewport viewport = switch (settings.roadStyle()) {
            case CAMPUS -> {
                var b = LegacyPlannedCampus.previewBounds(overview);
                yield new CampusViewport(b.minX(), b.minZ(), b.width(), b.depth());
            }
            case CAMPUS_PARK -> {
                var b = ParkPlannedCampus.previewBounds(overview);
                yield new CampusViewport(b.minX(), b.minZ(), b.width(), b.depth());
            }
            case CAMPUS_COMPACT -> {
                var b = CompactPlannedCampus.previewBounds(overview);
                yield new CampusViewport(b.minX(), b.minZ(), b.width(), b.depth());
            }
            case CAMPUS_DENSE -> {
                var b = DensePlannedCampus.previewBounds(overview);
                yield new CampusViewport(b.minX(), b.minZ(), b.width(), b.depth());
            }
            case CAMPUS_WOODLAND -> {
                var b = PlannedCampus.previewBounds(overview);
                yield new CampusViewport(b.minX(), b.minZ(), b.width(), b.depth());
            }
            default -> throw new IllegalArgumentException("Not a campus preset");
        };
        return renderCampus(settings, pixels, pixels, viewport, settings.roadStyle() == PlotSettings.RoadStyle.CAMPUS ? 1 : 4);
    }

    public static int[] renderCampus(int width, int height, PlannedCampus.Bounds bounds) {
        return renderCampus(width, height, bounds, 4);
    }

    public static int[] renderCampus(int width, int height, PlannedCampus.Bounds bounds, int samples) {
        return renderCampus(width, height, new CampusViewport(bounds.minX(), bounds.minZ(), bounds.width(), bounds.depth()), samples);
    }

    public static int[] renderCampus(int width, int height, CampusViewport bounds, int samples) {
        return renderCampus(PlotSettings.plannedCampus(), width, height, bounds, samples);
    }

    public static int[] renderCampus(PlotSettings settings, int width, int height, CampusViewport bounds, int samples) {
        if (width < 1 || height < 1 || width > MAX_PIXELS || height > MAX_PIXELS) throw new IllegalArgumentException("Invalid atlas size");
        if (samples != 1 && samples != 4) throw new IllegalArgumentException("Invalid atlas quality");
        int[] result = new int[width * height];
        for (int z = 0; z < height; z++) for (int x = 0; x < width; x++) {
            int red = 0, green = 0, blue = 0;
            for (int dz = 0; dz < samples; dz++) for (int dx = 0; dx < samples; dx++) {
                int wx = (int) Math.floor(bounds.minX() + (x + (dx + .5) / samples) * bounds.width() / width);
                int wz = (int) Math.floor(bounds.minZ() + (z + (dz + .5) / samples) * bounds.depth() / height);
                int rgb = switch (settings.roadStyle()) {
                    case CAMPUS -> LegacyPlannedCampus.previewColorAt(wx, wz);
                    case CAMPUS_PARK -> ParkPlannedCampus.previewColorAt(wx, wz);
                    case CAMPUS_COMPACT -> CompactPlannedCampus.previewColorAt(wx, wz);
                    case CAMPUS_DENSE -> DensePlannedCampus.previewColorAt(wx, wz);
                    case CAMPUS_WOODLAND -> PlannedCampus.previewColorAt(wx, wz);
                    default -> throw new IllegalArgumentException("Not a campus preset");
                };
                red += (rgb >>> 16) & 255; green += (rgb >>> 8) & 255; blue += rgb & 255;
            }
            int count = samples * samples;
            result[z * width + x] = 0xff000000 | (red / count << 16) | (green / count << 8) | blue / count;
        }
        return result;
    }
}
