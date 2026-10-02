package dev.drimoz.oreveintweaker;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(OreVeinTweaker.MOD_ID)
public class OreVeinTweaker {
    public static final String MOD_ID = "oreveintweaker";

    public OreVeinTweaker(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, VeinConfig.SPEC);
        // Blocks are resolved once registries are filled (common setup), then on every config file edit.
        modBus.addListener(this::onSetup);
        modBus.addListener(this::onReload);

        if (DevTools.ENABLED) {
            NeoForge.EVENT_BUS.addListener(DevTools::register);
            NeoForge.EVENT_BUS.addListener(DevTools::measureToggle);
        }
    }

    private void onSetup(FMLCommonSetupEvent event) {
        VeinRules.rebuild();
    }

    private void onReload(ModConfigEvent.Reloading event) {
        VeinRules.rebuild();
    }
}
