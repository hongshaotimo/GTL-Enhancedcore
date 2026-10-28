package com.gtl.enhancedcore.common.structure;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gtl.enhancedcore.common.recipe.iv.IvBufferRegistry;
import com.gtl.enhancedcore.integration.terminal.LampPlacement;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.gtlcore.gtlcore.integration.terminal.StableBlockCandidates;

import static com.gtl.enhancedcore.common.registration.MachineRegistrationSupport.*;

/** Exact 199 x 216 x 199 imported blueprint; only the 24 marked cells accept ports. */
public final class StellarVortexStructure {
    private static final String ROOT = "/data/gtl_enhancedcore/structures/stellar_vortex_space_station";
    private static final JsonObject BINDINGS = readBindings();

    private StellarVortexStructure() {}

    private static JsonObject readBindings() {
        try (var input = StellarVortexStructure.class.getResourceAsStream(ROOT + ".json")) {
            if (input == null) throw new IOException("Missing stellar vortex structure manifest");
            return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("bindings");
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    public static BlockPattern create(MultiblockMachineDefinition definition) {
        var builder = StructurePatterns.start(ROOT + ".pattern.gz")
                .where('C', Predicates.controller(Predicates.blocks(definition.get())))
                .where('X', requiredBlock("gtlcore:dimensionally_transcendent_casing")
                        .or(Predicates.blocks(com.gtl.enhancedcore.common.machine.GTLEnhancedcoreMachines.getGalaxySourceHatch().get())
                                .setMinGlobalLimited(1).setMaxGlobalLimited(1).setPreviewCount(1))
                        .or(new TraceabilityPredicate(world -> {
                            var block = world.getBlockState().getBlock();
                            return block != com.gtl.enhancedcore.common.machine.GTLEnhancedcoreMachines.getGalaxySourceHatch().getBlock()
                                    && block != com.gtl.enhancedcore.common.machine.GTLEnhancedcoreMachines.getGalaxyTargetHatch().getBlock()
                                    && PartAbility.IMPORT_ITEMS.isApplicable(block);
                        }, () -> PartAbility.IMPORT_ITEMS.getAllBlocks().stream()
                                .filter(block -> block != com.gtl.enhancedcore.common.machine.GTLEnhancedcoreMachines.getGalaxySourceHatch().getBlock())
                                .filter(block -> block != com.gtl.enhancedcore.common.machine.GTLEnhancedcoreMachines.getGalaxyTargetHatch().getBlock())
                                .map(block -> new BlockInfo(block.defaultBlockState())).toArray(BlockInfo[]::new)).setMinGlobalLimited(1)))
                .where(' ', Predicates.any());
        for (var entry : BINDINGS.entrySet()) {
            var binding = entry.getValue().getAsJsonObject();
            if (!binding.get("mode").getAsString().equals("literal")) continue;
            builder.where(entry.getKey().charAt(0), literal(binding));
        }
        return builder.build();
    }

    /** Keep the enormous unconstrained volume absent from the preview position map. */
    public static BlockInfo[][][] preview(MultiblockMachineDefinition definition) {
        var matches=((com.gtl.enhancedcore.mixin.gtceu.BlockPatternAccessor)definition.getPatternFactory().get()).gtlEnhancedcore$getBlockMatches();
        BlockInfo[][][] result=new BlockInfo[199][216][199];
        var choices=new java.util.HashMap<Character,BlockInfo>();
        BlockInfo casing=BlockInfo.fromBlockState(requiredBlockBlock("gtlcore:dimensionally_transcendent_casing").defaultBlockState());
        BlockInfo input=PartAbility.IMPORT_ITEMS.getAllBlocks().stream()
                .filter(block -> block != com.gtl.enhancedcore.common.machine.GTLEnhancedcoreMachines.getGalaxySourceHatch().getBlock()
                        && block != com.gtl.enhancedcore.common.machine.GTLEnhancedcoreMachines.getGalaxyTargetHatch().getBlock())
                .findFirst().map(block -> BlockInfo.fromBlockState(block.defaultBlockState()))
                .orElseThrow(() -> new IllegalStateException("No ordinary item input hatch registered for stellar vortex preview"));
        BlockInfo source=BlockInfo.fromBlockState(com.gtl.enhancedcore.common.machine.GTLEnhancedcoreMachines.getGalaxySourceHatch().defaultBlockState());
        boolean[] sourcePlaced={false};boolean[] inputPlaced={false};int[] aisle={0};
        StructurePatterns.data(ROOT+".pattern.gz").forEachAisle(rows->{
            int z=aisle[0]++;
            for(int y=0;y<216;y++)for(int x=0;x<199;x++) {
                char symbol=rows[y].charAt(x);
                if(symbol==' ')continue;
                BlockInfo info;
                if(symbol=='X') {
                    if(!sourcePlaced[0]) { info=source;sourcePlaced[0]=true; }
                    else if(!inputPlaced[0]) { info=input;inputPlaced[0]=true; }
                    else info=casing;
                }
                else {
                    info=choices.get(symbol);
                    if(info==null) { info=previewCandidate(matches[z][y][x],symbol);choices.put(symbol,info); }
                }
                // Preview is baked at NORTH with LEFT/UP/FRONT pattern axes.
                result[198-x][y][198-z]=info;
            }
        });
        return result;
    }
    private static BlockInfo previewCandidate(TraceabilityPredicate predicate,char symbol) {
        for(var simple:java.util.stream.Stream.concat(predicate.limited.stream(),predicate.common.stream()).toList()) {
            if(simple.candidates==null)continue;
            var candidates=simple.candidates.get();
            if(candidates!=null && candidates.length>0 && candidates[0]!=null)return candidates[0];
        }
        throw new IllegalStateException("Missing stellar vortex preview candidate: "+symbol);
    }

    private static TraceabilityPredicate literal(JsonObject binding) {
        var block = requiredBlockBlock(binding.get("block").getAsString());
        BlockState state = block.defaultBlockState();
        for (var entry : binding.getAsJsonObject("properties").entrySet()) {
            var property = block.getStateDefinition().getProperty(entry.getKey());
            if (property == null) throw new IllegalStateException("Unknown structure property: " + entry.getKey());
            state = withProperty(state, property, entry.getValue().getAsString());
        }
        BlockState preview = state;
        // Runtime active/powered may change. Lamp placement retains the reviewed item NBT.
        return new TraceabilityPredicate(world -> world.getBlockState().is(block),
                StableBlockCandidates.mark(() -> new BlockInfo[]{LampPlacement.info(preview)}));
    }

    private static <T extends Comparable<T>> BlockState withProperty(BlockState state, Property<T> property, String value) {
        return state.setValue(property, property.getValue(value).orElseThrow(
                () -> new IllegalStateException("Invalid structure property " + property.getName() + "=" + value)));
    }
}
