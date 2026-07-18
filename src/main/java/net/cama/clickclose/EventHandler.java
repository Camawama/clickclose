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
    // Width of the filter tab strip sticking out on the left side of the recipe book
    private static final int RECIPE_BOOK_TAB_STRIP_WIDTH = 35;
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
                if (currentScreen instanceof AbstractContainerScreen && client.player != null) {
                    // Close through the player so ServerboundContainerClosePacket is sent.
                    // setScreen(null) alone leaves the server-side container open (chest lids
                    // stay open, villager trades stay locked, input slots can desync).
                    client.player.closeContainer();
                } else {
                    // Non-container screens (e.g. JEI RecipesGui) have no server-side
                    // container to close.
                    client.setScreen(null);
                }
                return false;
            });

            ScreenEvents.afterRender(screen).register((currentScreen, guiGraphics, mouseX, mouseY, tickDelta) -> {
                renderOverlay(currentScreen, guiGraphics, mouseX, mouseY);
            });
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.screen == null) {
                restoreCursor();
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
        } catch (Exception e) {
            ClickClose.LOGGER.debug("Could not play close sound '{}'", soundName, e);
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
        guiGraphics.renderTooltip(Minecraft.getInstance().font, Component.translatable("clickclose.tooltip.close"), (int) mouseX, (int) mouseY);
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

    private static boolean isIgnored(Screen screen) {
        var ignored = Config.getIgnoredScreens();
        if (ignored.isEmpty()) {
            return false;
        }
        // Walk the class hierarchy so modded screens extending an ignored screen are ignored too
        for (Class<?> c = screen.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            if (ignored.contains(c.getName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean shouldClose(Screen screen, double mouseX, double mouseY) {
        if (isIgnored(screen)) {
            return false;
        }

        if (screen instanceof AbstractContainerScreen<?> containerScreen) {
            if (containerScreen.getMenu() != null) {
                ItemStack carried = containerScreen.getMenu().getCarried();
                if (!carried.isEmpty()) {
                    return false;
                }
            }

            // Covers Curios/Trinkets slots and other modded slots outside the main GUI.
            // hoveredSlot is made accessible by our access widener.
            if (containerScreen.hoveredSlot != null) {
                return false;
            }
        }

        ScreenBounds bounds = getScreenBounds(screen);
        if (bounds == null || bounds.contains(mouseX, mouseY)) {
            return false;
        }

        if (screen instanceof RecipeUpdateListener recipeListener) {
            RecipeBookComponent recipeBook = recipeListener.getRecipeBookComponent();
            if (recipeBook != null && recipeBook.isVisible() && isInsideRecipeBook(recipeBook, mouseX, mouseY)) {
                return false;
            }
        }

        if (screen.getChildAt(mouseX, mouseY).isPresent()) {
            return false;
        }

        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractWidget widget && widget.visible && widget.isMouseOver(mouseX, mouseY)) {
                return false;
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
            // Check for tabs above the Recipes GUI
            if (JeiCompat.isRecipesGui(screen)
                    && mouseY >= bounds.top - 30 && mouseY <= bounds.top
                    && mouseX >= bounds.left && mouseX <= bounds.left + bounds.width) {
                return false;
            }

            // JEI overlays (ingredient list, bookmarks) apply to every screen, RecipesGui included
            if (JeiCompat.isMouseOverJei(mouseX, mouseY)) {
                return false;
            }
        }

        return true;
    }

    private static ScreenBounds getScreenBounds(Screen screen) {
        if (screen instanceof AbstractContainerScreen<?> containerScreen) {
            // Fields are made accessible by our access widener; direct references are
            // remapped for production, unlike the old reflection by mapped name
            return new ScreenBounds(containerScreen.leftPos, containerScreen.topPos,
                    containerScreen.imageWidth, containerScreen.imageHeight);
        }

        if (isJeiLoaded()) {
            int[] jeiBounds = JeiCompat.getScreenBounds(screen);
            if (jeiBounds != null && jeiBounds.length == 4) {
                return new ScreenBounds(jeiBounds[0], jeiBounds[1], jeiBounds[2], jeiBounds[3]);
            }
        }

        return null;
    }

    private static boolean isInsideRecipeBook(RecipeBookComponent recipeBook, double mouseX, double mouseY) {
        // xOffset/width/height are made accessible by our access widener. width/height hold
        // the SCREEN size, not the book size — the book's position is derived here exactly
        // the way RecipeBookComponent.initVisuals computes it.
        int left = (recipeBook.width - RecipeBookComponent.IMAGE_WIDTH) / 2 - recipeBook.xOffset;
        int top = (recipeBook.height - RecipeBookComponent.IMAGE_HEIGHT) / 2;

        return mouseX >= left - RECIPE_BOOK_TAB_STRIP_WIDTH
                && mouseX < left + RecipeBookComponent.IMAGE_WIDTH
                && mouseY >= top
                && mouseY < top + RecipeBookComponent.IMAGE_HEIGHT;
    }

    private static boolean isJeiLoaded() {
        return FabricLoader.getInstance().isModLoaded("jei");
    }

    private record ScreenBounds(int left, int top, int width, int height) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= left && mouseX < left + width && mouseY >= top && mouseY < top + height;
        }
    }

    // Indirection so JeiHandler (which references JEI classes) is only loaded when JEI is present
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
