package net.cama.clickclose;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;

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
        
        // 1. Check ingredients (items)
        try {
            if (runtime.getIngredientListOverlay().getIngredientUnderMouse().isPresent()) {
                return true;
            }
        } catch (Exception e) {}
        
        try {
            if (runtime.getBookmarkOverlay().getIngredientUnderMouse().isPresent()) {
                return true;
            }
        } catch (Exception e) {}
        
        // 2. Check general overlay areas (search bar, buttons, etc.)
        // Since isMouseOver is unreliable or missing in some versions, we use a heuristic.
        // JEI Ingredient List is usually on the right side.
        // JEI Bookmarks are usually on the left side.
        
        // We can check if the screen has a child at the mouse position?
        // JEI overlays are not children of the screen usually.
        
        // However, we can try to access the config to know the width/bounds? No easy access.
        
        // Let's try the reflection check for isMouseOver again, but maybe we need to check specific implementation classes?
        // No, interface should be enough if it exists.
        
        // If the user says clicking the wrench (settings) closes it, it means our previous checks failed.
        // The wrench is part of the ingredient list overlay.
        
        // Let's try a different approach:
        // JEI renders its overlay.
        // If we can get the bounds of the overlay...
        
        // Actually, there is a simpler way.
        // If we are typing in the search bar, the search bar is focused.
        // Screen.getFocused() might return the search bar widget?
        // JEI adds the search bar as a widget to the screen?
        // If so, our `screen.getChildAt` in EventHandler should have caught it.
        // If it didn't, JEI manages it separately.
        
        // Let's try to be more aggressive with the reflection check.
        // Maybe the method is `hasMouseOver`?
        // Or maybe we can check `runtime.getScreenHelper().getGuiProperties(screen)`?
        
        // Let's try to use the `IIngredientListOverlay` methods to check if search bar is focused?
        // `hasKeyboardFocus()`?
        
        // If search bar is focused, we shouldn't close.
        // But clicking it to focus it...
        
        // Let's try to define the JEI area manually if all else fails.
        // Right side of screen.
        // Width is configurable.
        
        // Wait, let's try the reflection again but print errors if it fails (in my mind).
        // If `isMouseOver` exists, it should work.
        // Maybe the method name is obfuscated in the runtime?
        // But we are in dev env with deobf dependency.
        
        // Let's try to check if the mouse is over the search bar specifically.
        // We don't have access to it.
        
        // Let's add a heuristic for the right side of the screen.
        // If the mouse is to the right of the GUI, and JEI is loaded, assume it's JEI?
        // That might prevent closing when clicking empty space on the right.
        // But that's better than closing when clicking JEI.
        
        // Let's refine the heuristic.
        // JEI is usually displayed if there is space.
        // If `guiLeft + xSize` < `screenWidth`, there is space on the right.
        // If mouseX > `guiLeft + xSize`, we might be over JEI.
        
        // But we can do better.
        // Let's try to use `runtime.getIngredientListOverlay()` and check if we can cast it to something useful?
        // No.
        
        // Let's try to use the `isMouseOver` reflection again but with `int`?
        // `isMouseOver(double, double)` is standard.
        
        // Maybe we can check if the mouse is over the "config button" area?
        // Config button is usually bottom right of the overlay.
        
        // Let's add a broad check:
        // If JEI is loaded, and we are outside the GUI...
        // If we are to the right of the GUI (and JEI list is shown), don't close?
        // If we are to the left (and bookmarks are shown), don't close?
        
        // How do we know if they are shown?
        // `runtime.getIngredientListOverlay().isListDisplayed()`? (might not exist)
        
        // Let's try to use the reflection check again, but catch `NoSuchMethodException` specifically.
        // And maybe try `isMouseOver` with `int` arguments?
        
        try {
            Object overlay = runtime.getIngredientListOverlay();
            java.lang.reflect.Method m = overlay.getClass().getMethod("isMouseOver", double.class, double.class);
            if ((boolean) m.invoke(overlay, mouseX, mouseY)) return true;
        } catch (Exception e) {}
        
        // Try with int?
        try {
            Object overlay = runtime.getIngredientListOverlay();
            java.lang.reflect.Method m = overlay.getClass().getMethod("isMouseOver", int.class, int.class);
            if ((boolean) m.invoke(overlay, (int)mouseX, (int)mouseY)) return true;
        } catch (Exception e) {}

        // Try bookmark overlay
        try {
            Object overlay = runtime.getBookmarkOverlay();
            java.lang.reflect.Method m = overlay.getClass().getMethod("isMouseOver", double.class, double.class);
            if ((boolean) m.invoke(overlay, mouseX, mouseY)) return true;
        } catch (Exception e) {}
        
        // Heuristic: Search Bar is usually at the bottom center or bottom right of the overlay.
        // Wrench is near it.
        
        // Let's assume if we are far right or far left, it's JEI.
        // This is a bit loose but safe.
        // Screen width.
        Screen screen = Minecraft.getInstance().screen;
        if (screen != null) {
            int screenWidth = screen.width;
            // If mouse is within 20 pixels of the right edge, it's likely JEI scrollbar/buttons?
            // JEI is wider than that.
            
            // Let's just say: If we are NOT in the center area where the GUI is, 
            // and JEI is loaded, we should be careful.
            // But we want to close if clicking empty background.
            
            // What if we check if the click was consumed?
            // We are in Pre event.
            // If we let it pass, and check in Post?
            // We discussed this.
            
            // Let's try one more specific check for the search bar.
            // If the user is typing, the search bar has focus.
            // But clicking to *give* focus...
            
            // If we can't detect it, we can't detect it.
            // But `isMouseOver` *should* work if the method exists.
            // If it failed before, maybe it's because the overlay implementation class doesn't have it public?
            // `getMethod` only finds public methods.
            // The implementation might be package-private or something?
            // But the interface `IRecipeGuiOverlay` has it?
            // If the interface has it, `getMethod` on the class should find it.
            
            // Wait, `IRecipeGuiOverlay` in 1.20.1 might NOT have `isMouseOver`.
            // I checked online docs for 1.20.1 (mezz.jei.api.gui.handlers.IGuiProperties).
            
            // Let's try to use `runtime.getScreenHelper()`?
            
            // Okay, fallback plan:
            // If we are in `JeiHandler`, we can try to check if the mouse is over the "exclusion zones"?
            // No.
            
            // Let's go with the "Right/Left Side Heuristic" if reflection fails.
            // If mouseX > screen.width - 200 (approx JEI width) AND we are not inside the GUI?
            // That effectively disables click-close on the right side if JEI is installed.
            // That's probably acceptable behavior for modpack users who have JEI open.
            // If they hide JEI (Ctrl+O), we shouldn't block.
            // But we don't know if it's hidden easily.
            
            // Let's try to be a bit smarter.
            // If `getIngredientUnderMouse` returns empty, we might still be over the search bar.
            // The search bar is usually at the bottom of the screen.
            // If mouseY > screen.height - 30?
            
            if (mouseY > screen.height - 40) {
                 // Bottom area. Likely search bar if JEI is present.
                 // Check X range?
                 // Search bar is usually centered or wide.
                 return true;
            }
            
            // Wrench is usually next to search bar.
        }

        return false;
    }
}
