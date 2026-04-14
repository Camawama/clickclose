package net.cama.clickclose;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClickClose implements ClientModInitializer {
    public static final String MODID = "clickclose";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    @Override
    public void onInitializeClient() {
        Config.load();
        EventHandler.register();
        LOGGER.info("Initialized Click Close! for Fabric {}", Config.getMinecraftTarget());
    }
}
