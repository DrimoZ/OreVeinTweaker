package dev.drimoz.oreveintweaker;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(OreVeinTweaker.MOD_ID)
public class OreVeinTweaker {
    public static final String MOD_ID = "oreveintweaker";

    public OreVeinTweaker() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, VeinConfig.SPEC);
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        // Blocks are resolved once registries are filled (common setup), then on every config file edit.
        bus.addListener(this::onSetup);
        bus.addListener(this::onReload);

        if (DevTools.ENABLED) {
            MinecraftForge.EVENT_BUS.addListener(DevTools::register);
            MinecraftForge.EVENT_BUS.addListener(DevTools::measureToggle);
        }
    }

    private void onSetup(FMLCommonSetupEvent event) {
        VeinRules.rebuild();
    }

    private void onReload(ModConfigEvent.Reloading event) {
        VeinRules.rebuild();
    }
}
