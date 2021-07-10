package dev.larrabyte.huff;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

@Mod(modid="huff", version="probably", useMetadata=true)
public class Main {
    // Mod-wide objects (basically, we don't need more RNGs).
    public static final MersenneTwister rand = new MersenneTwister();
    public static final ReachExtender reachExtender = new ReachExtender();
    public static final AutoClicker autoClicker = new AutoClicker();

    @EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(reachExtender);
        MinecraftForge.EVENT_BUS.register(autoClicker);
    }
}
