package net.cama.clickclose;

import com.mojang.logging.LogUtils;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.api.runtime.IScreenHelper;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@JeiPlugin
public class JeiHandler implements IModPlugin {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static IJeiRuntime runtime;

    // isMouseOver runs every frame: cache reflection lookups and only log each failure once
    private static final Map<Class<?>, Optional<Method>> IS_MOUSE_OVER_CACHE = new ConcurrentHashMap<>();
    private static final Set<String> LOGGED_FAILURES = ConcurrentHashMap.newKeySet();

    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation(ClickClose.MODID, "jei_plugin");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
    }

    private static void logOnce(String what, Throwable t) {
        if (LOGGED_FAILURES.add(what)) {
            LOGGER.debug("ClickClose JEI compat: {} failed", what, t);
        }
    }

    public static boolean isMouseOver(double mouseX, double mouseY) {
        if (runtime == null) return false;

        // Default to false on error so an API hiccup doesn't permanently block
        // closing across the bottom strip of the screen
        boolean listDisplayed = false;
        try {
            listDisplayed = runtime.getIngredientListOverlay().isListDisplayed();
        } catch (Exception e) {
            logOnce("isListDisplayed", e);
        }

        try {
            if (runtime.getIngredientListOverlay().getIngredientUnderMouse().isPresent()) {
                return true;
            }
        } catch (Exception e) {
            logOnce("ingredient list getIngredientUnderMouse", e);
        }

        try {
            if (runtime.getBookmarkOverlay().getIngredientUnderMouse().isPresent()) {
                return true;
            }
        } catch (Exception e) {
            logOnce("bookmark getIngredientUnderMouse", e);
        }

        // Reflection fallback for isMouseOver (not part of the JEI API interfaces)
        try {
            if (invokeIsMouseOver(runtime.getIngredientListOverlay(), mouseX, mouseY)) {
                return true;
            }
        } catch (Exception e) {
            logOnce("ingredient list isMouseOver", e);
        }

        try {
            if (invokeIsMouseOver(runtime.getBookmarkOverlay(), mouseX, mouseY)) {
                return true;
            }
        } catch (Exception e) {
            logOnce("bookmark isMouseOver", e);
        }

        // Heuristic for the search bar area (bottom of screen).
        // We apply this even in RecipesGui because the search bar is an overlay on top.
        if (listDisplayed) {
            Screen currentScreen = net.minecraft.client.Minecraft.getInstance().screen;
            if (currentScreen != null) {
                if (mouseY > currentScreen.height - 30) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean invokeIsMouseOver(Object overlay, double mouseX, double mouseY) throws Exception {
        if (overlay == null) return false;
        Optional<Method> method = IS_MOUSE_OVER_CACHE.computeIfAbsent(overlay.getClass(), clazz -> {
            try {
                return Optional.of(clazz.getMethod("isMouseOver", double.class, double.class));
            } catch (NoSuchMethodException e) {
                return Optional.empty();
            }
        });
        return method.isPresent() && (boolean) method.get().invoke(overlay, mouseX, mouseY);
    }

    public static boolean isRecipesGui(Object screen) {
        return screen.getClass().getName().endsWith("RecipesGui");
    }

    public static int[] getScreenBounds(Screen screen) {
        if (runtime == null) return null;
        try {
            IScreenHelper screenHelper = runtime.getScreenHelper();
            if (screenHelper != null) {
                Optional<IGuiProperties> guiPropertiesOpt = screenHelper.getGuiProperties(screen);
                if (guiPropertiesOpt.isPresent()) {
                    IGuiProperties guiProperties = guiPropertiesOpt.get();
                    return new int[]{
                        guiProperties.getGuiLeft(),
                        guiProperties.getGuiTop(),
                        guiProperties.getGuiXSize(),
                        guiProperties.getGuiYSize()
                    };
                }
            }
        } catch (Exception e) {
            logOnce("getGuiProperties", e);
            if (isRecipesGui(screen)) {
                return getRecipesGuiBoundsReflection(screen);
            }
        }
        return null;
    }

    private static int[] getRecipesGuiBoundsReflection(Object screen) {
        try {
            Class<?> clazz = screen.getClass();
            int guiLeft = getIntField(clazz, screen, "guiLeft");
            int guiTop = getIntField(clazz, screen, "guiTop");
            int xSize = getIntField(clazz, screen, "xSize");
            int ySize = getIntField(clazz, screen, "ySize");
            return new int[]{guiLeft, guiTop, xSize, ySize};
        } catch (Exception e) {
             try {
                int guiLeft = (int) screen.getClass().getMethod("getGuiLeft").invoke(screen);
                int guiTop = (int) screen.getClass().getMethod("getGuiTop").invoke(screen);
                int xSize = (int) screen.getClass().getMethod("getXSize").invoke(screen);
                int ySize = (int) screen.getClass().getMethod("getYSize").invoke(screen);
                return new int[]{guiLeft, guiTop, xSize, ySize};
            } catch (Exception ex) {
                logOnce("RecipesGui bounds reflection", ex);
                return null;
            }
        }
    }

    private static int getIntField(Class<?> clazz, Object instance, String name) throws Exception {
        Class<?> current = clazz;
        while (current != null) {
            try {
                java.lang.reflect.Field f = current.getDeclaredField(name);
                f.setAccessible(true);
                return f.getInt(instance);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
