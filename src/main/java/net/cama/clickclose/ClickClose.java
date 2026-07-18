package net.cama.clickclose;

import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod(ClickClose.MODID)
public class ClickClose
{
    public static final String MODID = "clickclose";

    // No-arg constructor: FMLJavaModLoadingContext constructor injection only exists on
    // Forge 47.3.10+, but this mod declares support for all of [47,).
    public ClickClose()
    {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
    }
}
