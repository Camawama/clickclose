# Click Close!

Click Close is a client-side Fabric mod for Minecraft 1.21.1.

It lets you close container and inventory screens by clicking the background area outside the GUI.

## Features

- Click outside the container to close it.
- Avoids closing when you are carrying an item.
- Respects clickable widgets and tabs.
- Optional visual feedback:
- `CURSOR_X`
- `TOOLTIP`
- `DIM_GUI`
- `NONE`
- Optional close sound (`minecraft:ui.button.click` by default).
- JEI-aware behavior for overlay hover checks when JEI is installed.

## Config

Client config file:

- `config/clickclose-client.json`

Main options:

- `ignoredScreens`
- `visualMode`
- `hideDefaultCursor`
- `cursorScale`
- `dimmingOpacity`
- `closeSound`
