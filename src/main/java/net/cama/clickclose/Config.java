package net.cama.clickclose;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.List;

@Mod.EventBusSubscriber(modid = ClickClose.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config
{
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> IGNORED_SCREENS;
    public static final ForgeConfigSpec.EnumValue<VisualMode> VISUAL_MODE;
    public static final ForgeConfigSpec.DoubleValue DIMMING_OPACITY;
    public static final ForgeConfigSpec.ConfigValue<String> CLOSE_SOUND;
    public static final ForgeConfigSpec.BooleanValue HIDE_DEFAULT_CURSOR;
    public static final ForgeConfigSpec.DoubleValue CURSOR_SCALE;

    static {
        BUILDER.push("ClickClose Settings");
        IGNORED_SCREENS = BUILDER
                .comment("List of Screens that should not close when clicking outside.")
                .defineList("ignoredScreens", List.of(
                        "net.minecraft.client.gui.screens.TitleScreen",
                        "net.minecraft.client.gui.screens.OptionsScreen",
                        "net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen",
                        "net.minecraft.client.gui.screens.worldselection.SelectWorldScreen"
                ), o -> o instanceof String);

        VISUAL_MODE = BUILDER
                .comment("Visual indicator when hovering over the background area.",
                        "NONE: No indicator",
                        "CURSOR_X: Displays a custom icon at the cursor (changeable via resource pack)",
                        "TOOLTIP: Displays a 'Close Menu' tooltip",
                        "DIM_GUI: Dims the GUI slightly")
                .defineEnum("visualMode", VisualMode.CURSOR_X);

        HIDE_DEFAULT_CURSOR = BUILDER
                .comment("If true, hides the default Minecraft cursor when the custom close icon is displayed (only for CURSOR_X mode).")
                .define("hideDefaultCursor", true);

        CURSOR_SCALE = BUILDER
                .comment("Scale of the custom cursor icon when visualMode is CURSOR_X (0.1 to 5.0).")
                .defineInRange("cursorScale", 0.5, 0.1, 5.0);

        DIMMING_OPACITY = BUILDER
                .comment("Opacity of the dimming effect when visualMode is DIM_GUI (0.0 to 1.0).")
                .defineInRange("dimmingOpacity", 0.35, 0.0, 1.0);

        CLOSE_SOUND = BUILDER
                .comment("Registry name of the sound to play when closing the menu. Leave empty to disable.",
                        "Example: minecraft:ui.button.click")
                .define("closeSound", "minecraft:ui.button.click");

        BUILDER.pop();
    }

    static final ForgeConfigSpec SPEC = BUILDER.build();

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event)
    {
    }

    public enum VisualMode {
        NONE,
        CURSOR_X,
        TOOLTIP,
        DIM_GUI
    }
}
