package dev.larrabyte.huff;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

@Mod(modid="huff", version="probably", useMetadata=true)
public class Main {
    @EventHandler
    public void init(FMLInitializationEvent event) {
        System.out.println("Registering event handlers!");
        MinecraftForge.EVENT_BUS.register(new AutoClicker());
    }
}
