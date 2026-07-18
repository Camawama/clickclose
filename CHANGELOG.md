# Changelog

## 1.0.2

### Fixed
- Closing a menu by clicking outside now properly notifies the server. This fixes chests appearing stuck open to other players, villagers staying locked in a trade, and occasional ghost items when closing crafting or enchanting menus.
- The mod no longer crashes on Forge versions older than 47.3.10. All Forge 47.x versions for 1.20.1 are now supported.
- Clicking on the recipe book (crafting table, inventory, furnace, smoker, blast furnace) is now detected accurately instead of using a rough estimate, so menus no longer close when clicking on or near the book.
- The cursor no longer stays hidden if the "hide default cursor" option is turned off while a menu is open.
- A JEI error no longer permanently blocks closing menus near the bottom of the screen.
- Screens added to the ignore list now also cover screens based on them from other mods.

### Changed
- The "Close Menu" tooltip can now be translated through resource packs.
- JEI is now listed as an optional dependency so load order is guaranteed.
- Minor performance improvements while a menu is open.
- Removed leftover template files and log spam from the mod's initial setup.
- Corrected the bundled license file to MIT, matching the mod's actual license.

## 1.0.1
- Improved JEI compatibility.

## 1.0.0
- Initial release with basic JEI and Curios compatibility.
