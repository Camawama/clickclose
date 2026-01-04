package net.cama.clickclose;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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

@Mod.EventBusSubscriber(modid = ClickClose.MODID, value = Dist.CLIENT)
public class EventHandler {

    private static final ResourceLocation CLOSE_ICON = new ResourceLocation(ClickClose.MODID, "textures/gui/close_icon.png");
    private static boolean isCursorHidden = false;

    @SubscribeEvent
    public static void onMouseClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() == 0) { // Left click
            Screen screen = event.getScreen();
            if (screen == null) return;

            if (shouldClose(screen, event.getMouseX(), event.getMouseY())) {
                playCloseSound();
                screen.onClose();
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
                // Invalid sound name, ignore
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
        
        // Draw 16x16 texture centered
        // We need to offset by half size *after* scaling? No, translate first, then scale.
        // If we scale, the drawing coordinates are scaled.
        // We want the center of the scaled image to be at (0,0) (which is mouseX, mouseY).
        // So we draw at -8, -8.

        guiGraphics.blit(CLOSE_ICON, -8, -8, 0, 0, 16, 16, 16, 16);
        guiGraphics.pose().popPose();
        RenderSystem.disableBlend();
    }

    private static void renderTooltip(GuiGraphics guiGraphics, double mouseX, double mouseY) {
        guiGraphics.renderTooltip(Minecraft.getInstance().font, Component.literal("Close Menu"), (int)mouseX, (int)mouseY);
    }

    private static void renderDimGui(GuiGraphics guiGraphics, Screen screen) {
        if (screen instanceof AbstractContainerScreen) {
            AbstractContainerScreen<?> containerScreen = (AbstractContainerScreen<?>) screen;
            int x = containerScreen.leftPos;
            int y = containerScreen.topPos;
            int w = containerScreen.imageWidth;
            int h = containerScreen.imageHeight;
            
            double opacity = Config.DIMMING_OPACITY.get();
            int alpha = (int) (opacity * 255);
            int color = (alpha << 24); // Black with variable alpha
            
            guiGraphics.fill(x, y, x + w, y + h, color);
        }
    }

    private static boolean shouldClose(Screen screen, double mouseX, double mouseY) {
        String screenName = screen.getClass().getName();
        if (Config.IGNORED_SCREENS.get().contains(screenName)) {
            return false;
        }

        if (screen instanceof AbstractContainerScreen) {
            AbstractContainerScreen<?> containerScreen = (AbstractContainerScreen<?>) screen;
            
            // Check if player is holding an item
            if (containerScreen.getMenu() != null) {
                ItemStack carried = containerScreen.getMenu().getCarried();
                if (carried != null && !carried.isEmpty()) {
                    return false;
                }
            }
            
            int guiLeft = containerScreen.leftPos;
            int guiTop = containerScreen.topPos;
            int xSize = containerScreen.imageWidth;
            int ySize = containerScreen.imageHeight;

            boolean inside = mouseX >= guiLeft && mouseX < guiLeft + xSize &&
                             mouseY >= guiTop && mouseY < guiTop + ySize;

            if (!inside && screen instanceof RecipeUpdateListener) {
                RecipeUpdateListener recipeListener = (RecipeUpdateListener) screen;
                RecipeBookComponent recipeBook = recipeListener.getRecipeBookComponent();
                
                if (recipeBook.isVisible()) {
                     if (isInsideRecipeBook(recipeBook, mouseX, mouseY, guiLeft, guiTop, xSize, ySize)) {
                         return false;
                     }
                }
            }
            
            if (!inside) {
                 if (screen.getChildAt(mouseX, mouseY).isPresent()) {
                    return false;
                }
                
                if (screen instanceof CreativeModeInventoryScreen) {
                     boolean overTopTabs = mouseY >= guiTop - 28 && mouseY <= guiTop;
                     boolean overBottomTabs = mouseY >= guiTop + ySize - 4 && mouseY <= guiTop + ySize + 28;
                     
                     if (overTopTabs || overBottomTabs) {
                         if (mouseX >= guiLeft && mouseX <= guiLeft + xSize) {
                             return false;
                         }
                     }
                }

                return true;
            }
        }
        return false;
    }

    private static boolean isInsideRecipeBook(RecipeBookComponent recipeBook, double mouseX, double mouseY, int guiLeft, int guiTop, int guiWidth, int guiHeight) {
        try {
            int x = getIntField(recipeBook, "f_100276_", "x");
            int y = getIntField(recipeBook, "f_100277_", "y");
            int width = getIntField(recipeBook, "f_100278_", "width");
            int height = getIntField(recipeBook, "f_100279_", "height");
            
            if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height) {
                return true;
            }
            
            if (mouseX >= x - 35 && mouseX < x && mouseY >= y && mouseY < y + height) {
                return true;
            }
        } catch (Exception e) {
        }
        
        double bookWidth = 147;
        double tabsWidth = 35;
        double totalWidth = bookWidth + tabsWidth;
        
        if (mouseX >= guiLeft - totalWidth && mouseX < guiLeft) {
            if (mouseY >= guiTop && mouseY < guiTop + guiHeight) {
                return true;
            }
        }
        
        return false;
    }

    private static int getIntField(Object obj, String srgName, String mcpName) throws Exception {
        try {
            java.lang.reflect.Field field = obj.getClass().getDeclaredField(srgName);
            field.setAccessible(true);
            return field.getInt(obj);
        } catch (NoSuchFieldException e) {
            java.lang.reflect.Field field = obj.getClass().getDeclaredField(mcpName);
            field.setAccessible(true);
            return field.getInt(obj);
        }
    }
}
