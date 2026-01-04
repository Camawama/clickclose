package net.cama.clickclose;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.api.runtime.IScreenHelper;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

@JeiPlugin
public class JeiHandler implements IModPlugin {
    private static IJeiRuntime runtime;

    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation(ClickClose.MODID, "jei_plugin");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
    }

    public static boolean isMouseOver(double mouseX, double mouseY) {
        if (runtime == null) return false;
        
        boolean listDisplayed = false;
        try {
            listDisplayed = runtime.getIngredientListOverlay().isListDisplayed();
        } catch (Exception e) {
            listDisplayed = true;
        }

        try {
            if (runtime.getIngredientListOverlay().getIngredientUnderMouse().isPresent()) {
                return true;
            }
        } catch (Exception e) {
        }
        
        try {
            if (runtime.getBookmarkOverlay().getIngredientUnderMouse().isPresent()) {
                return true;
            }
        } catch (Exception e) {
        }
        
        // Reflection fallback for isMouseOver
        try {
            Object ingredientOverlay = runtime.getIngredientListOverlay();
            if ((boolean) ingredientOverlay.getClass().getMethod("isMouseOver", double.class, double.class).invoke(ingredientOverlay, mouseX, mouseY)) {
                return true;
            }
        } catch (Exception e) {
        }
        
        try {
            Object bookmarkOverlay = runtime.getBookmarkOverlay();
            if ((boolean) bookmarkOverlay.getClass().getMethod("isMouseOver", double.class, double.class).invoke(bookmarkOverlay, mouseX, mouseY)) {
                return true;
            }
        } catch (Exception e) {
        }
        
        // Heuristic for search bar area (bottom of screen)
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
