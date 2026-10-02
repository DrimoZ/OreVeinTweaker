package com.oreveinstripper;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(OreVeinStripper.MOD_ID)
public class OreVeinStripper {
    public static final String MOD_ID = "oreveinstripper";

    public OreVeinStripper(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, VeinConfig.SPEC);
        // Blocks are resolved once registries are filled (common setup), then on every config file edit.
        modBus.addListener(this::onSetup);
        modBus.addListener(this::onReload);

        if (DevTools.ENABLED) {
            NeoForge.EVENT_BUS.addListener(DevTools::register);
        }
    }

    private void onSetup(FMLCommonSetupEvent event) {
        VeinRules.rebuild();
    }

    private void onReload(ModConfigEvent.Reloading event) {
        VeinRules.rebuild();
    }
}
