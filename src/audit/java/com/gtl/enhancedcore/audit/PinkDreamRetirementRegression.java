package com.gtl.enhancedcore.audit;

import com.gtl.enhancedcore.common.world.campus.CampusPlanAccess;
import com.gtl.enhancedcore.common.world.personalplot.PersonalPlotStore;
import com.gtl.enhancedcore.common.world.plot.CampusPresetRegistry;
import com.gtl.enhancedcore.common.world.plot.FileCampus;
import com.gtl.enhancedcore.common.world.plot.PlotPresetShare;
import com.gtl.enhancedcore.common.world.plot.PlotSettings;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.UUID;

/** Retirement removes creation eligibility without changing published world identities or archives. */
public final class PinkDreamRetirementRegression {
    private static int checks;
    public static void main(String[] args) throws Exception {
        var pink = PlotSettings.fileCampus("pink_dream_home_v1");
        check(CampusPresetRegistry.contains(pink.campusPresetId()), "Existing pink identity stays registered");
        check(!pink.isAvailableForCreation(), "Retired pink settings cannot be used for creation");
        check(CampusPresetRegistry.creationDefinitions().stream().noneMatch(d -> d.id().equals(pink.campusPresetId())),
                "The creation list excludes pink");
        check(CampusPresetRegistry.creationDefinitions().size() == CampusPresetRegistry.definitions().size() - 1,
                "Only the requested preset is retired");
        check(PlotSettings.fileCampus("quartz_works").isAvailableForCreation()
                        && PlotSettings.plannedCampus().isAvailableForCreation() && PlotSettings.defaults().isAvailableForCreation(),
                "Other creation choices remain available");
        check(PlotSettings.RoadStyle.CAMPUS_FILE.ordinal() == 13 && PlotSettings.RoadStyle.CAMPUS_FACTORY.ordinal() == 12,
                "Published generator enum identities stay fixed");
        var transported = PlotPresetShare.decode(PlotPresetShare.encode(pink));
        check(transported.equals(pink) && PlotPresetShare.decodeStored(PlotPresetShare.encode(pink)).equals(pink),
                "Old persisted settings and network snapshots retain the exact pink identity");
        var campus = FileCampus.of(transported.campusPresetId());
        check(!campus.plots().isEmpty() && CampusPlanAccess.of(transported) == CampusPlanAccess.of(pink),
                "Old worlds retain their resource geometry and cached map/teleport plan");
        Path root = Files.createTempDirectory("gtl-pink-retirement-");
        try {
            var owner = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");
            // Seed the released-format archive as an existing world, rather than invoking a new-create action.
            PersonalPlotStore.write(root, new PersonalPlotStore.Data(pink, java.util.Map.of()));
            var existing = new PersonalPlotStore.Data(pink, java.util.Map.of(owner, pink));
            PersonalPlotStore.write(root, existing);
            byte[] original = Files.readAllBytes(PersonalPlotStore.packPath(root, owner));
            var restarted = PersonalPlotStore.read(root);
            check(restarted.equals(existing), "Restart reads the old owner ZIP and saved template unchanged");
            PersonalPlotStore.write(root, restarted.withTemplate(PlotSettings.fileCampus("quartz_works")));
            check(PersonalPlotStore.read(root).worlds().get(owner).equals(pink)
                            && Arrays.equals(original, Files.readAllBytes(PersonalPlotStore.packPath(root, owner))),
                    "Reselecting the template never rewrites or migrates the existing pink archive");
        } finally {
            try (var files = Files.walk(root)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
            }
        }
        System.out.println("Pink retirement checks passed: " + checks);
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
}
