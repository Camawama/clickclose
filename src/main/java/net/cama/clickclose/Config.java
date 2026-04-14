package net.cama.clickclose;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public class Config {
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve(ClickClose.MODID + "-client.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Data data = Data.defaults();

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                Data parsed = GSON.fromJson(reader, Data.class);
                data = sanitize(parsed);
            } catch (IOException | JsonParseException e) {
                ClickClose.LOGGER.warn("Failed reading config, using defaults: {}", CONFIG_PATH, e);
                data = Data.defaults();
            }
        } else {
            data = Data.defaults();
        }

        save();
    }

    public static String getMinecraftTarget() {
        return "1.21.1";
    }

    public enum VisualMode {
        NONE,
        CURSOR_X,
        TOOLTIP,
        DIM_GUI
    }

    public static List<String> getIgnoredScreens() {
        return data.ignoredScreens;
    }

    public static VisualMode getVisualMode() {
        return data.visualMode;
    }

    public static double getDimmingOpacity() {
        return data.dimmingOpacity;
    }

    public static String getCloseSound() {
        return data.closeSound;
    }

    public static boolean isHideDefaultCursor() {
        return data.hideDefaultCursor;
    }

    public static double getCursorScale() {
        return data.cursorScale;
    }

    private static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            ClickClose.LOGGER.warn("Failed writing config: {}", CONFIG_PATH, e);
        }
    }

    private static Data sanitize(Data raw) {
        Data defaults = Data.defaults();
        Data safe = new Data();

        if (raw == null) {
            return defaults;
        }

        List<String> ignoredScreens = raw.ignoredScreens == null
                ? defaults.ignoredScreens
                : raw.ignoredScreens.stream().filter(Objects::nonNull).distinct().toList();

        safe.ignoredScreens = ignoredScreens;
        safe.visualMode = raw.visualMode == null ? defaults.visualMode : raw.visualMode;
        safe.dimmingOpacity = clamp(raw.dimmingOpacity, 0.0, 1.0, defaults.dimmingOpacity);
        safe.closeSound = raw.closeSound == null ? defaults.closeSound : raw.closeSound;
        safe.hideDefaultCursor = raw.hideDefaultCursor == null ? defaults.hideDefaultCursor : raw.hideDefaultCursor;
        safe.cursorScale = clamp(raw.cursorScale, 0.1, 5.0, defaults.cursorScale);

        return safe;
    }

    private static double clamp(Double value, double min, double max, double fallback) {
        if (value == null || Double.isNaN(value)) {
            return fallback;
        }
        return Math.min(max, Math.max(min, value));
    }

    private static final class Data {
        List<String> ignoredScreens;
        VisualMode visualMode;
        Double dimmingOpacity;
        String closeSound;
        Boolean hideDefaultCursor;
        Double cursorScale;

        static Data defaults() {
            Data data = new Data();
            data.ignoredScreens = List.of(
                    "net.minecraft.client.gui.screens.TitleScreen",
                    "net.minecraft.client.gui.screens.OptionsScreen",
                    "net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen",
                    "net.minecraft.client.gui.screens.worldselection.SelectWorldScreen"
            );
            data.visualMode = VisualMode.CURSOR_X;
            data.dimmingOpacity = 0.35;
            data.closeSound = "minecraft:ui.button.click";
            data.hideDefaultCursor = true;
            data.cursorScale = 0.5;
            return data;
        }
    }
}
