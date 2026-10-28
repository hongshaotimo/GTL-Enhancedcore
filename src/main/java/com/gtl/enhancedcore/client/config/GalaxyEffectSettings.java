package com.gtl.enhancedcore.client.config;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraftforge.fml.loading.FMLPaths;
/** 0=off, 1=low, 2=balanced, 3=high. Reloads locally without a server round trip. */
public final class GalaxyEffectSettings {
    private static long nextRead;
    private static int quality=2;
    private GalaxyEffectSettings() {}
    public static int quality() {
        long now=System.nanoTime();if(now<nextRead)return quality;nextRead=now+2_000_000_000L;
        Path file=FMLPaths.CONFIGDIR.get().resolve("GTL-Enhancedcore/galaxy_effects.properties");
        try {
            if(!Files.exists(file)){Files.createDirectories(file.getParent());Files.writeString(file,"# 0=off, 1=low, 2=balanced, 3=high\nquality=2\n");}
            var p=new java.util.Properties();try(var in=Files.newInputStream(file)){p.load(in);}
            quality=Math.max(0,Math.min(3,Integer.parseInt(p.getProperty("quality","2").trim())));
        }catch(java.io.IOException|NumberFormatException e){quality=2;}
        return quality;
    }
}
