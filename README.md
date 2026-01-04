# Click Close! 🖱️❌

**Click Close!** is a simple but powerful mod for Minecraft Forge 1.20.1. No more reaching for the `Esc` or `E` key every time you want to close a chest or crafting table!

## ✨ Features

*   **Click to Close**: Simply click anywhere on the background (outside the GUI) to close containers, chests, and menus.
*   **Smart Detection**: The mod intelligently detects the size and position of your GUI. It won't close if you click on:
    *   The main container window.
    *   Recipe book tabs or the recipe book itself.
    *   Creative inventory tabs.
    *   Any other buttons or widgets added by other mods.
*   **Conditional**: Prevents accidental closing if you are holding an item, so you can still drop items by clicking outside the inventory!

## ⚙️ Customization

**Click Close!** is fully configurable to suit your playstyle. Check the config file (`clickclose-client.toml`) for these options:

### - Ignore List
Define which screens should **NOT** be closed by this mod. By default, screens like the Main Menu, Options, and Multiplayer screens are ignored.

### - Visual Indicators
Choose how you want to be notified that clicking will close the menu:
*   **`CURSOR_X`** (Default): Displays a custom icon (red 'X') at your cursor position.
    *   *Option to hide the default Minecraft cursor.*
    *   *Adjustable scale for the icon.*
    *   *Texture can be changed via Resource Packs!* (`assets/clickclose/textures/gui/close_icon.png`)
*   **`TOOLTIP`**: Shows a "Close Menu" tooltip.
*   **`DIM_GUI`**: Dims the container GUI slightly to indicate it's out of focus.
    *   *Adjustable dimming opacity.*
*   **`NONE`**: No visual indicator.

### - Sound
*   Play a sound when closing the menu (Default: standard button click).
*   Disable the sound or change it to any other registered sound event.
