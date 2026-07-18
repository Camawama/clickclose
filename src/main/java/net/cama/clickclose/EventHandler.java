package net.cama.clickclose;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

@Mod.EventBusSubscriber(modid = ClickClose.MODID, value = Dist.CLIENT)
public class EventHandler {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation CLOSE_ICON = new ResourceLocation(ClickClose.MODID, "textures/gui/close_icon.png");
    // Width of the filter tab strip sticking out on the left side of the recipe book
    private static final int RECIPE_BOOK_TAB_STRIP_WIDTH = 35;
    private static boolean isCursorHidden = false;

    private record ScreenBounds(int left, int top, int width, int height) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= left && mouseX < left + width && mouseY >= top && mouseY < top + height;
        }
    }

    @SubscribeEvent
    public static void onMouseClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() == 0) { // Left click
            Screen screen = event.getScreen();
            if (screen == null) return;

            if (shouldClose(screen, event.getMouseX(), event.getMouseY())) {
                playCloseSound();

                Minecraft minecraft = Minecraft.getInstance();
                if (screen instanceof AbstractContainerScreen && minecraft.player != null) {
                    // Close through the player so ServerboundContainerClosePacket is sent.
                    // setScreen(null) alone leaves the server-side container open (chest lids
                    // stay open, villager trades stay locked, input slots can desync).
                    // This also bypasses JEI's "back" navigation, which is intentional:
                    // we want to close the entire GUI stack.
                    minecraft.player.closeContainer();
                } else {
                    // Non-container screens (e.g. JEI RecipesGui) have no server-side
                    // container to close.
                    minecraft.setScreen(null);
                }

                event.setCanceled(true);
            }
        }
    }

    private static void playCloseSound() {
        String soundName = Config.CLOSE_SOUND.get();
        if (soundName != null && !soundName.isEmpty()) {
            try {
                ResourceLocation soundLoc = new ResourceLocation(soundName);
                SoundEvent soundEvent = ForgeRegistries.SOUND_EVENTS.getValue(soundLoc);
                if (soundEvent != null) {
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(soundEvent, 1.0F));
                }
            } catch (Exception e) {
                LOGGER.debug("Could not play close sound '{}'", soundName, e);
            }
        }
    }

    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Post event) {
        Screen screen = event.getScreen();
        if (screen == null) return;

        Config.VisualMode mode = Config.VISUAL_MODE.get();
        if (mode == Config.VisualMode.NONE) {
            restoreCursor();
            return;
        }

        double mouseX = event.getMouseX();
        double mouseY = event.getMouseY();

        if (shouldClose(screen, mouseX, mouseY)) {
            GuiGraphics guiGraphics = event.getGuiGraphics();

            switch (mode) {
                case CURSOR_X:
                    if (Config.HIDE_DEFAULT_CURSOR.get()) {
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

    // Ensure cursor is restored when screen closes or changes
    @SubscribeEvent
    public static void onScreenClose(ScreenEvent.Closing event) {
        restoreCursor();
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
        // Render the custom texture
        RenderSystem.enableBlend();
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(mouseX, mouseY, 500); // On top

        float scale = Config.CURSOR_SCALE.get().floatValue();
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
            double opacity = Config.DIMMING_OPACITY.get();
            int alpha = (int) (opacity * 255);
            int color = (alpha << 24); // Black with variable alpha
            guiGraphics.fill(bounds.left(), bounds.top(), bounds.left() + bounds.width(), bounds.top() + bounds.height(), color);
        }
    }

    private static ScreenBounds getScreenBounds(Screen screen) {
        if (screen instanceof AbstractContainerScreen<?> containerScreen) {
            return new ScreenBounds(containerScreen.getGuiLeft(), containerScreen.getGuiTop(),
                    containerScreen.getXSize(), containerScreen.getYSize());
        }
        if (isJeiLoaded()) {
            // Use JEI API to get bounds for any screen JEI supports (including RecipesGui)
            int[] jeiBounds = JeiCompat.getScreenBounds(screen);
            if (jeiBounds != null) {
                return new ScreenBounds(jeiBounds[0], jeiBounds[1], jeiBounds[2], jeiBounds[3]);
            }
        }
        return null;
    }

    private static boolean isIgnored(Screen screen) {
        var ignored = Config.IGNORED_SCREENS.get();
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
            // Check if player is holding an item
            if (containerScreen.getMenu() != null) {
                ItemStack carried = containerScreen.getMenu().getCarried();
                if (carried != null && !carried.isEmpty()) {
                    return false;
                }
            }

            // Check if mouse is over any slot (Handles Curios slots and other modded slots outside main GUI)
            if (containerScreen.getSlotUnderMouse() != null) {
                return false;
            }
        }

        ScreenBounds bounds = getScreenBounds(screen);
        if (bounds == null || bounds.contains(mouseX, mouseY)) {
            return false;
        }

        if (screen instanceof RecipeUpdateListener recipeListener) {
            RecipeBookComponent recipeBook = recipeListener.getRecipeBookComponent();
            if (recipeBook.isVisible() && isInsideRecipeBook(recipeBook, mouseX, mouseY)) {
                return false;
            }
        }

        // Check if any child widget is under the mouse
        if (screen.getChildAt(mouseX, mouseY).isPresent()) {
            return false;
        }

        // Iterate over all children to check if any AbstractWidget is hovered
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractWidget widget && widget.visible && widget.isMouseOver(mouseX, mouseY)) {
                return false;
            }
        }

        if (screen instanceof CreativeModeInventoryScreen) {
            boolean overTopTabs = mouseY >= bounds.top() - 28 && mouseY <= bounds.top();
            boolean overBottomTabs = mouseY >= bounds.top() + bounds.height() - 4 && mouseY <= bounds.top() + bounds.height() + 28;

            if ((overTopTabs || overBottomTabs) && mouseX >= bounds.left() && mouseX <= bounds.left() + bounds.width()) {
                return false;
            }
        }

        // JEI Compatibility
        if (isJeiLoaded()) {
            // Check for tabs above the Recipes GUI
            if (JeiCompat.isRecipesGui(screen)
                    && mouseY >= bounds.top() - 30 && mouseY <= bounds.top()
                    && mouseX >= bounds.left() && mouseX <= bounds.left() + bounds.width()) {
                return false;
            }

            // JEI overlays (ingredient list, bookmarks) apply to every screen, RecipesGui included
            if (JeiCompat.isMouseOverJei(mouseX, mouseY)) {
                return false;
            }
        }

        return true;
    }

    private static boolean isInsideRecipeBook(RecipeBookComponent recipeBook, double mouseX, double mouseY) {
        // xOffset/width/height are made public by our access transformer. width/height hold
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
        return net.minecraftforge.fml.ModList.get().isLoaded("jei");
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
