package net.cama.clickclose;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

public class EventHandler {

    private static final ResourceLocation CLOSE_ICON = ResourceLocation.parse(ClickClose.MODID + ":textures/gui/close_icon.png");
    private static final int INVENTORY_EDGE_CLOSE_WIDTH = 10;
    private static final int WINDOW_EDGE_CLOSE_WIDTH = 10;
    private static boolean isRegistered = false;
    private static boolean isCursorHidden = false;

    private EventHandler() {
    }

    public static void register() {
        if (isRegistered) {
            return;
        }
        isRegistered = true;

        ScreenEvents.BEFORE_INIT.register((client, screen, width, height) -> {
            ScreenMouseEvents.allowMouseClick(screen).register((currentScreen, mouseX, mouseY, button) -> {
                if (button != 0) {
                    return true;
                }
                if (!shouldClose(currentScreen, mouseX, mouseY)) {
                    return true;
                }

                playCloseSound();
                client.setScreen(null);
                client.mouseHandler.grabMouse();
                clearManagedCursorStateForGameplay();
                return false;
            });

            ScreenEvents.afterRender(screen).register((currentScreen, guiGraphics, mouseX, mouseY, tickDelta) -> {
                renderOverlay(currentScreen, guiGraphics, mouseX, mouseY);
            });
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.screen == null) {
                clearManagedCursorStateForGameplay();
            }
        });
    }

    private static void renderOverlay(Screen screen, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        if (screen == null) {
            restoreCursor();
            return;
        }

        Config.VisualMode mode = Config.getVisualMode();
        if (mode == Config.VisualMode.NONE) {
            restoreCursor();
            return;
        }

        if (shouldClose(screen, mouseX, mouseY)) {
            switch (mode) {
                case CURSOR_X:
                    if (Config.isHideDefaultCursor()) {
                        hideCursor();
                    } else {
                        restoreCursor();
                    }
                    renderCursorIcon(guiGraphics, mouseX, mouseY);
                    break;
                case TOOLTIP:
                    restoreCursor();
                    renderTooltip(guiGraphics, mouseX, mouseY);
                    break;
                case DIM_GUI:
                    restoreCursor();
                    renderDimGui(guiGraphics, screen);
                    break;
            }
        } else {
            restoreCursor();
        }
    }

    private static void playCloseSound() {
        String soundName = Config.getCloseSound();
        if (soundName == null || soundName.isBlank()) {
            return;
        }

        try {
            ResourceLocation soundLoc = ResourceLocation.parse(soundName);
            SoundEvent soundEvent = BuiltInRegistries.SOUND_EVENT.get(soundLoc);
            if (soundEvent != null) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(soundEvent, 1.0F));
            }
        } catch (Exception ignored) {
            // Invalid sound id, ignore
        }
    }

    private static void hideCursor() {
        if (!isCursorHidden) {
            GLFW.glfwSetInputMode(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN);
            isCursorHidden = true;
        }
    }

    private static void restoreCursor() {
        if (isCursorHidden) {
            GLFW.glfwSetInputMode(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
            isCursorHidden = false;
        }
    }

    private static void clearManagedCursorStateForGameplay() {
        if (isCursorHidden) {
            isCursorHidden = false;
        }
    }

    private static void renderCursorIcon(GuiGraphics guiGraphics, double mouseX, double mouseY) {
        RenderSystem.enableBlend();
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(mouseX, mouseY, 500);

        float scale = (float) Config.getCursorScale();
        guiGraphics.pose().scale(scale, scale, 1.0f);

        guiGraphics.blit(CLOSE_ICON, -8, -8, 0, 0, 16, 16, 16, 16);
        guiGraphics.pose().popPose();
        RenderSystem.disableBlend();
    }

    private static void renderTooltip(GuiGraphics guiGraphics, double mouseX, double mouseY) {
        guiGraphics.renderTooltip(Minecraft.getInstance().font, Component.literal("Close Menu"), (int)mouseX, (int)mouseY);
    }

    private static void renderDimGui(GuiGraphics guiGraphics, Screen screen) {
        ScreenBounds bounds = getScreenBounds(screen);
        if (bounds != null) {
            double opacity = Config.getDimmingOpacity();
            int alpha = (int) (opacity * 255);
            int color = alpha << 24;
            guiGraphics.fill(bounds.left, bounds.top, bounds.left + bounds.width, bounds.top + bounds.height, color);
        }
    }

    private static boolean shouldClose(Screen screen, double mouseX, double mouseY) {
        String screenName = screen.getClass().getName();
        if (Config.getIgnoredScreens().contains(screenName)) {
            return false;
        }

        ScreenBounds bounds = getScreenBounds(screen);
        if (bounds == null) {
            return false;
        }

        if (screen instanceof AbstractContainerScreen<?> containerScreen) {
            if (containerScreen.getMenu() != null) {
                ItemStack carried = containerScreen.getMenu().getCarried();
                if (!carried.isEmpty()) {
                    return false;
                }
            }

            if (getSlotUnderMouse(containerScreen) != null) {
                return false;
            }
        }

        boolean inside = bounds.contains(mouseX, mouseY);

        if (!inside && screen instanceof RecipeUpdateListener recipeListener) {
            RecipeBookComponent recipeBook = recipeListener.getRecipeBookComponent();
            if (recipeBook != null && recipeBook.isVisible()) {
                if (isInsideRecipeBook(recipeBook, mouseX, mouseY, bounds)) {
                    return false;
                }
            }
        }

        if (!inside) {
            if (screen.getChildAt(mouseX, mouseY).isPresent()) {
                return false;
            }

            for (GuiEventListener child : screen.children()) {
                if (child instanceof AbstractWidget widget) {
                    if (widget.visible && widget.isMouseOver(mouseX, mouseY)) {
                        return false;
                    }
                }
            }

            if (screen instanceof CreativeModeInventoryScreen) {
                boolean overTopTabs = mouseY >= bounds.top - 28 && mouseY <= bounds.top;
                boolean overBottomTabs = mouseY >= bounds.top + bounds.height - 4 && mouseY <= bounds.top + bounds.height + 28;

                if ((overTopTabs || overBottomTabs) && mouseX >= bounds.left && mouseX <= bounds.left + bounds.width) {
                    return false;
                }
            }

            if (isJeiLoaded()) {
                if (JeiCompat.isRecipesGui(screen)) {
                    if (mouseY >= bounds.top - 30 && mouseY <= bounds.top) {
                        if (mouseX >= bounds.left && mouseX <= bounds.left + bounds.width) {
                            return false;
                        }
                    }
                    if (JeiCompat.isMouseOverJei(mouseX, mouseY)) {
                        return false;
                    }
                } else {
                    if (JeiCompat.isMouseOverJei(mouseX, mouseY)) {
                        return false;
                    }
                }
            }

            return isOnInventoryEdge(bounds, mouseX, mouseY) || isOnWindowEdge(screen, mouseX, mouseY);
        }
        return false;
    }

    private static boolean isOnInventoryEdge(ScreenBounds bounds, double mouseX, double mouseY) {
        int leftOuter = bounds.left - INVENTORY_EDGE_CLOSE_WIDTH;
        int rightOuter = bounds.left + bounds.width + INVENTORY_EDGE_CLOSE_WIDTH;
        int topOuter = bounds.top - INVENTORY_EDGE_CLOSE_WIDTH;
        int bottomOuter = bounds.top + bounds.height + INVENTORY_EDGE_CLOSE_WIDTH;

        boolean inExpandedX = mouseX >= leftOuter && mouseX < rightOuter;
        boolean inExpandedY = mouseY >= topOuter && mouseY < bottomOuter;

        boolean nearLeft = mouseX >= leftOuter && mouseX < bounds.left;
        boolean nearRight = mouseX >= bounds.left + bounds.width && mouseX < rightOuter;
        boolean nearTop = mouseY >= topOuter && mouseY < bounds.top;
        boolean nearBottom = mouseY >= bounds.top + bounds.height && mouseY < bottomOuter;

        return (inExpandedY && (nearLeft || nearRight)) || (inExpandedX && (nearTop || nearBottom));
    }

    private static boolean isOnWindowEdge(Screen screen, double mouseX, double mouseY) {
        return mouseX < WINDOW_EDGE_CLOSE_WIDTH
                || mouseY < WINDOW_EDGE_CLOSE_WIDTH
                || mouseX >= screen.width - WINDOW_EDGE_CLOSE_WIDTH
                || mouseY >= screen.height - WINDOW_EDGE_CLOSE_WIDTH;
    }

    private static Object getSlotUnderMouse(AbstractContainerScreen<?> screen) {
        for (String methodName : new String[]{"getSlotUnderMouse", "getHoveredSlot"}) {
            Class<?> current = screen.getClass();
            while (current != null) {
                try {
                    java.lang.reflect.Method method = current.getDeclaredMethod(methodName);
                    method.setAccessible(true);
                    return method.invoke(screen);
                } catch (NoSuchMethodException ignored) {
                    current = current.getSuperclass();
                } catch (ReflectiveOperationException ignored) {
                    current = null;
                }
            }
        }
        return null;
    }

    private static ScreenBounds getScreenBounds(Screen screen) {
        if (screen instanceof AbstractContainerScreen<?> containerScreen) {
            Integer left = getIntField(containerScreen, "leftPos", "x", "guiLeft");
            Integer top = getIntField(containerScreen, "topPos", "y", "guiTop");
            Integer width = getIntField(containerScreen, "imageWidth", "backgroundWidth", "xSize");
            Integer height = getIntField(containerScreen, "imageHeight", "backgroundHeight", "ySize");

            if (left != null && top != null && width != null && height != null && width > 0 && height > 0) {
                return new ScreenBounds(left, top, width, height);
            }
        }

        if (isJeiLoaded()) {
            int[] bounds = JeiCompat.getScreenBounds(screen);
            if (bounds != null && bounds.length == 4) {
                return new ScreenBounds(bounds[0], bounds[1], bounds[2], bounds[3]);
            }
        }

        return null;
    }

    private static boolean isInsideRecipeBook(RecipeBookComponent recipeBook, double mouseX, double mouseY, ScreenBounds screenBounds) {
        Integer x = getIntField(recipeBook, "x", "f_100276_");
        Integer y = getIntField(recipeBook, "y", "f_100277_");
        Integer width = getIntField(recipeBook, "width", "f_100278_");
        Integer height = getIntField(recipeBook, "height", "f_100279_");

        if (x != null && y != null && width != null && height != null) {
            if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height) {
                return true;
            }
            if (mouseX >= x - 35 && mouseX < x && mouseY >= y && mouseY < y + height) {
                return true;
            }
        }

        double bookWidth = 147;
        double tabsWidth = 35;
        double totalWidth = bookWidth + tabsWidth;

        if (mouseX >= screenBounds.left - totalWidth && mouseX < screenBounds.left) {
            return mouseY >= screenBounds.top && mouseY < screenBounds.top + screenBounds.height;
        }

        return false;
    }

    private static Integer getIntField(Object target, String... fieldNames) {
        for (String fieldName : fieldNames) {
            Class<?> current = target.getClass();
            while (current != null) {
                try {
                    java.lang.reflect.Field field = current.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    return field.getInt(target);
                } catch (NoSuchFieldException ignored) {
                    current = current.getSuperclass();
                } catch (IllegalAccessException ignored) {
                    break;
                }
            }
        }
        return null;
    }

    private static boolean isJeiLoaded() {
        return FabricLoader.getInstance().isModLoaded("jei");
    }

    private record ScreenBounds(int left, int top, int width, int height) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= left && mouseX < left + width && mouseY >= top && mouseY < top + height;
        }
    }

    private static class JeiCompat {
        static boolean isMouseOverJei(double mouseX, double mouseY) {
            return JeiHandler.isMouseOver(mouseX, mouseY);
        }
        
        static boolean isRecipesGui(Screen screen) {
            return JeiHandler.isRecipesGui(screen);
        }
        
        static int[] getScreenBounds(Screen screen) {
            return JeiHandler.getScreenBounds(screen);
        }
    }
}
