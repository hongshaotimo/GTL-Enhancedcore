package com.gtl.enhancedcore.common.structure;

import com.google.gson.JsonParser;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gtl.enhancedcore.common.registration.MachineRegistrationSupport;
import com.gtl.enhancedcore.integration.terminal.LampPlacement;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.gtlcore.gtlcore.integration.terminal.StableBlockCandidates;

/** Literal schematic palette only; controllers and shared hatch predicates remain in registration. */
public final class ReviewedStructurePalette {
    private ReviewedStructurePalette(){}
    public static FactoryBlockPattern bind(FactoryBlockPattern builder,String name){
        try(var input=ReviewedStructurePalette.class.getResourceAsStream("/data/gtl_enhancedcore/structures/"+name+".json")){
            if(input==null)throw new IllegalStateException("Missing palette "+name);
            var bindings=JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("bindings");
            for(var entry:bindings.entrySet()){
                var binding=entry.getValue().getAsJsonObject();if(!binding.get("mode").getAsString().equals("literal"))continue;
                var block=MachineRegistrationSupport.requiredBlockBlock(binding.get("block").getAsString());
                BlockState state=block.defaultBlockState();
                for(var property:binding.getAsJsonObject("properties").entrySet()){
                    var key=block.getStateDefinition().getProperty(property.getKey());
                    if(key==null)throw new IllegalStateException("Unknown palette property "+property);
                    state=withProperty(state,key,property.getValue().getAsString());
                }
                BlockState preview=state;
                builder.where(entry.getKey().charAt(0),new TraceabilityPredicate(world->world.getBlockState().is(block),
                        StableBlockCandidates.mark(()->new BlockInfo[]{LampPlacement.info(preview)})));
            }
            return builder;
        }catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
    }
    private static <T extends Comparable<T>> BlockState withProperty(BlockState state,Property<T> property,String value){
        return state.setValue(property,property.getValue(value).orElseThrow());
    }
}
