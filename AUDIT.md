# Click Close! — Codebase Audit (Forge 1.20.1, `main` branch)

**Date:** 2026-07-18
**Scope:** All source files, build scripts, and resources on the `main` branch (Forge 1.20.1, mod v1.0.1).
Where a finding also applies to the `1.21.1-fabric` branch, it is marked **[also Fabric]**.

> **Verification & resolution pass — 2026-07-18 (this workspace).**
> Every finding was re-verified against the local workspace (`main` @ `4614839`, which is
> byte-identical to `Camawama/clickclose` `main` on GitHub, so the audit applied 1:1).
> All items are now **FIXED** on `main` except where noted. Two findings (M1/M2) were
> **factually wrong** about the 1.20.1 code and have been corrected below — the real bug
> was worse than described. See also the new "Repository state" section at the bottom:
> the workspace's git remote pointed at the wrong repository entirely.

---

## Summary

The mod is small, focused, and its core logic is solid: the `shouldClose` checks (carried
item, hovered slot, widget hover, creative tabs, recipe book, JEI overlays) show good
awareness of real-world edge cases. The most important problems found are **one real
gameplay bug** (closing screens without notifying the server) and **one compatibility bug**
(the mod crashes on Forge versions older than 47.3.10 despite claiming to support `[47,)`).
The rest is cleanup: Forge MDK template leftovers, a wrong LICENSE file, fragile
reflection that can be replaced by your own access transformer, and small state bugs.

| Severity | Count | Status |
|----------|-------|--------|
| High     | 2     | both fixed |
| Medium   | 6     | 5 fixed, M4 partially fixed (heuristic kept by design) |
| Low / cleanup | 9 | all fixed |

**Post-fix verification:** `gradlew build` (compile + jar + reobfJar) passes on the
fixed tree.

---

## High severity

### H1. Closing via `Minecraft.setScreen(null)` never tells the server the container closed
`EventHandler.java:45` **[also Fabric]**

When the player clicks outside, the mod calls `Minecraft.getInstance().setScreen(null)`
directly. Verified against the decompiled game code:

- `setScreen(null)` → `AbstractContainerScreen.removed()` → only `menu.removed(player)`
  (client-side cleanup). **No packet is sent.**
- The vanilla close path is `onClose()` → `LocalPlayer.closeContainer()` →
  **sends `ServerboundContainerClosePacket`** → `clientSideCloseContainer()`.

Because the packet is never sent, the server-side `player.containerMenu` stays open.
Observable consequences:

- Chest/shulker lids stay open (and stay "viewed") for other players; comparators and
  open-count logic see a phantom viewer.
- A villager you were trading with stays locked in the trade session, blocking other
  players on a server.
- Items left in server-side crafting/enchanting input slots are not returned the way the
  vanilla close path returns them, inviting desyncs and ghost items.

**Fix:** for container screens, close through the player, not the screen stack:

```java
if (screen instanceof AbstractContainerScreen) {
    Minecraft.getInstance().player.closeContainer(); // sends the packet + sets screen null
} else {
    Minecraft.getInstance().setScreen(null); // JEI RecipesGui etc. — no container to close
}
```

Note this still bypasses JEI's "go back" behavior (the reason the code comment gives for
using `setScreen(null)`), because `player.closeContainer()` does not call
`Screen.onClose()` — so the JEI back-navigation hook never runs.

> **FIXED:** container screens now close via `minecraft.player.closeContainer()` (with a
> null-check on the player); all other screens still use `setScreen(null)`.

### H2. Mod crashes on Forge 47.0.0–47.3.9 despite declaring support for `[47,)`
`ClickClose.java:25`, `gradle.properties:18-20`

The constructor takes `FMLJavaModLoadingContext` as a parameter. Context injection into
mod constructors was only backported to 1.20.1 in **Forge 47.3.10** ("Optionally supply
FMLJavaModLoadingContext as a param to mod constructors"). On any earlier Forge 47.x the
mod fails to construct and the game shows a mod-loading error.

**Fix (either):**
- Bump `forge_version_range` / `loader_version_range` to `[47.3.10,)`, or
- Use a no-arg constructor with `FMLJavaModLoadingContext.get()` (deprecated but works
  across all of 47.x).

> **FIXED:** switched to a no-arg constructor. It uses `ModLoadingContext.get()`
> (not deprecated, works on all of 47.x) since registering the config spec is the only
> thing the constructor needs — no mod-bus access required. Version ranges stay `[47,)`.

---

## Medium severity

### M1. Recipe-book reflection never works — on *any* screen, in dev *or* production
`EventHandler.java:318-328` — **CORRECTED FINDING** (the original text below was wrong)

> *Original claim:* the reflection only fails for furnace-type screens because
> `getDeclaredField` doesn't see inherited fields, and works elsewhere.
>
> *What is actually true in 1.20.1* (verified against the parchment-mapped dev jar and
> `createSrgToMcp/output.srg`): `RecipeBookComponent` has **no `x` or `y` fields at all**.
> The real fields are `xOffset` (`f_100276_`), `width` (`f_100277_`) and `height`
> (`f_100278_`) — and `width`/`height` hold the **screen** size, not the book bounds.
> `f_100279_` is `tabButtons` (a `List`) and `f_100280_` is `selectedTab`, so the AT
> file's `# x / # y / # width / # height / # isVisible` comments were all mislabeled.
>
> Consequences for the old code:
> - **Dev:** `getDeclaredField("f_100276_")` fails (fields are mapped), then
>   `getDeclaredField("x")` fails (no such field) → heuristic fallback, always.
> - **Production:** `f_100276_`–`f_100278_` resolve, but then `getInt` on `f_100279_`
>   (`tabButtons`, not an int) throws `IllegalArgumentException` → heuristic fallback,
>   always. Had `f_100279_` been an int, the code would have silently computed a
>   nonsense rectangle (`y` = screen width!) and mis-closed clicks on the recipe book.
> - So in practice *every* recipe-book screen everywhere used the rough 147+35px
>   heuristic; the reflection was 100% dead weight.

**FIXED:** `isInsideRecipeBook` now computes the book position exactly the way vanilla
`RecipeBookComponent.initVisuals` does — `left = (width - IMAGE_WIDTH) / 2 - xOffset`,
`top = (height - IMAGE_HEIGHT) / 2` — reading `xOffset`/`width`/`height` directly (made
public by the corrected access transformer), plus the 35px tab strip on the left. No
reflection, no fallback needed, works for furnace screens since the fields are inherited.

### M2. Access transformer contained wrong entries; AbstractContainerScreen entries redundant
`EventHandler.java:288-328`, `META-INF/accesstransformer.cfg` — **CORRECTED FINDING**

> *Original claim:* "the AT already makes `RecipeBookComponent.x/y/width/height` public,
> so read `recipeBook.x` directly." Impossible — those fields don't exist (see M1). The
> AT's five `RecipeBookComponent` entries actually exposed `xOffset`, `width`, `height`,
> `tabButtons`, and `selectedTab`.

**FIXED:** the AT is trimmed to the three fields actually needed (`f_100276_ xOffset`,
`f_100277_ width`, `f_100278_ height`) with correct comments. The redundant
`AbstractContainerScreen` entries were removed — the code now uses Forge's public
`getGuiLeft()` / `getGuiTop()` / `getXSize()` / `getYSize()` getters (verified present
on the 47.4.13 dev jar). `getIntField` in `EventHandler` was deleted.

### M3. Cursor can stay hidden after toggling `hideDefaultCursor` off
`EventHandler.java:84-90`

In the `CURSOR_X` case there is no `else restoreCursor()` — if the cursor was hidden and
the user turns `hideDefaultCursor` off (config reload mid-screen), the hardware cursor
stays hidden until the mouse leaves the background area. The Fabric branch already has the
`else restoreCursor()`; port it.

> **FIXED:** `else restoreCursor()` added to the `CURSOR_X` case.

### M4. JEI bottom-strip heuristic blocks closing across the whole bottom of the screen
`JeiHandler.java:68-77` **[also Fabric]**

`if (mouseY > screen.height - 30) return true;` treats the entire bottom 30px of the
screen as "over JEI" whenever the ingredient list is displayed — including the area left
of the search bar where a close-click would be perfectly safe. Worse, the fallback
`catch (Exception e) { listDisplayed = true; }` means any JEI API hiccup permanently
enables the block. Consider using `IScreenHelper`/`IGuiProperties` or JEI's exclusion
areas to get the real search-bar bounds, and defaulting `listDisplayed` to `false` on error.

> **PARTIALLY FIXED:** `listDisplayed` now defaults to `false` on error (and the failure
> is logged once), so an API hiccup no longer permanently blocks the bottom strip. The
> 30px bottom-strip heuristic itself is deliberately kept: JEI's search bar can sit
> left-aligned, centered, or right-aligned depending on JEI's own config, and the JEI API
> exposes no stable public bounds for it. A conservative strip that only applies while
> the ingredient list is actually displayed is the safer trade-off (worst case: a click
> at the very bottom doesn't close; never: an unwanted close).

### M5. Repeated reflection & registry lookups every frame
`EventHandler.java` (render path), `JeiHandler.java:52-66` **[also Fabric]**

`shouldClose` runs every frame via `ScreenEvent.Render.Post`, and in the worst case does
uncached `getDeclaredField`/`getMethod` reflection (recipe book, JEI `isMouseOver`
fallback) each time. Cache the `Field`/`Method` objects in static finals (or eliminate
them per M2). Minor in practice, but it is per-frame work that doesn't need to repeat.

> **FIXED:** the recipe-book reflection is gone entirely (M1/M2). JEI's `isMouseOver`
> reflection fallback now caches the resolved `Method` per overlay class in a
> `ConcurrentHashMap`, and failures are logged once instead of silently swallowed.

### M6. Ignore-list matches exact class names only
`EventHandler.java:174-177` **[also Fabric]**

`Config.IGNORED_SCREENS.get().contains(screenName)` won't match subclasses, so a modded
screen extending an ignored screen is not ignored. Also, the shipped defaults
(`TitleScreen`, `OptionsScreen`, etc.) are all non-container screens that `shouldClose`
can never close anyway (they have no bounds), so the defaults are effectively dead — they
make the config *look* load-bearing when it isn't. Consider documenting that the list is
for container/JEI screens, or matching by `isAssignableFrom`.

> **FIXED:** `isIgnored` now walks the screen's class hierarchy, so subclasses of an
> ignored screen are ignored too. The shipped defaults were left as-is (harmless, and
> removing them would churn existing user configs).

---

## Low severity / cleanup

### L1. LICENSE.txt is Forge's LGPL text, but the mod is MIT — **FIXED**
`LICENSE.txt`, `gradle.properties:49`, `mods.toml:12` **[also Fabric]**
The repo shipped the Minecraft Forge LGPL 2.1 license file while `mods.toml` declares MIT.
> Replaced with the MIT license text (Copyright (c) 2026 Camawama).

### L2. Forge MDK leftovers: `changelog.txt` (74 KB of Forge history), `CREDITS.txt` — **FIXED**
Both files were Minecraft Forge's own, not the mod's.
> Both deleted (`git rm`).

### L3. Template code in `ClickClose.java` — **FIXED**
`commonSetup` ("HELLO FROM COMMON SETUP"), `onServerStarting` ("HELLO from server
starting"), and the `ClientModEvents` inner class were unmodified MDK examples.
> The class is now just the `MODID` constant and a constructor registering the config
> (via `ModLoadingContext`, see H2). The dead `ServerStartingEvent` handler and
> `MinecraftForge.EVENT_BUS.register(this)` are gone.

### L4. Empty `Config.onLoad` handler — **FIXED**
`Config.java:63-66`
> Deleted along with the `@Mod.EventBusSubscriber` annotation and unused imports.

### L5. Hardcoded "Close Menu" tooltip, no lang file — **FIXED** **[also Fabric]**
`EventHandler.java:140` used `Component.literal("Close Menu")`.
> Added `assets/clickclose/lang/en_us.json` with `clickclose.tooltip.close` and switched
> to `Component.translatable`.

### L6. `mods.toml` is still mostly template — **FIXED**
> Rewritten without the MDK example comments; `issueTrackerURL` and `displayURL` now
> point at https://github.com/Camawama/clickclose; JEI declared as an optional
> dependency (`mandatory=false`, `versionRange="[15,)"`, `ordering="AFTER"`,
> `side="CLIENT"`).

### L7. Duplicated bounds logic — **FIXED**
`renderDimGui` (EventHandler.java:143-171) re-implemented the same bounds resolution as
`shouldClose`.
> Extracted a private `ScreenBounds` record with a `contains()` check and a
> `getScreenBounds(Screen)` helper (container getters first, JEI fallback) used by both
> paths — mirroring the Fabric branch's structure.

### L8. Silent empty catches — **FIXED** **[also Fabric]**
> `playCloseSound` logs at debug level; all JEI compat failures go through a
> `logOnce` helper (debug level, each failure site logged once per session so the
> per-frame path can't spam `debug.log`).

### L9. Build script nits — **FIXED**
- `org.gradle.daemon=false` removed from `gradle.properties`.
- JEI CurseMaven pin now has a comment (project 238222, file 7391695 = JEI 15.x for
  1.20.1) noting both lines must stay on the same file id.
- The unused `gameTestServer` / `data` run configs and the datagen
  `src/generated/resources` source-set entry were deleted.

---

## Things that are done well

- **Carried-item guard** (`getCarried().isEmpty()`) prevents item-throw misclicks — the
  single most common complaint against mods in this genre.
- **`getSlotUnderMouse` check** cleanly covers Curios and other mods that place slots
  outside the GUI rectangle.
- **Widget hover checks** via both `getChildAt` and the `AbstractWidget.isMouseOver`
  sweep cover screens that position buttons outside their bounds.
- **Creative tab strip handling** correctly accounts for tabs above and below the GUI.
- **Config** uses `ForgeConfigSpec` range validation properly (`defineInRange`,
  element validators on the list).
- **Client-only marking** (`clientSideOnly=true` in mods.toml) is correct.

---

## Branch notes

The `1.21.1-fabric` branch is generally *ahead* of `main` in code quality: it already has
the superclass-walking reflection (M1), the `ScreenBounds` record (L7), the
`else restoreCursor()` (M3), config sanitization, and a guard against double event
registration. After fixing `main`, consider porting H1 (the `player.closeContainer()`
fix) to Fabric too, since that bug exists identically there
(`EventHandler.java` → `client.setScreen(null)`).

**Still open for Fabric** (not touched in this pass — the branch isn't checked out in
this workspace): H1 (`player.closeContainer()`), M4 (default `listDisplayed=false`),
L1 (LICENSE), L5 (lang file), L8 (log-once). Note that 1.21.1 rewrote
`RecipeBookComponent`, so the M1/M2 correction above is specific to 1.20.1 — re-verify
field names there before porting anything recipe-book related.

---

## Repository state (found during this pass — outside the original audit scope)

The workspace's `origin` remote pointed at **`https://github.com/Camawama/FullStop.git`**
— a completely different mod. That is why git reported "diverged: 4 and 25 different
commits": the 25 "incoming" commits were FullStop's history (no common ancestor at all),
and pulling them would have merged an unrelated project into this one.

The real repo `Camawama/clickclose` exists and its `main` (`4614839`) is **identical**
to the local pre-audit HEAD — nothing was actually out of sync. To repair (blocked by
tool permissions in this session, run manually):

```
git remote set-url origin https://github.com/Camawama/clickclose.git
git fetch --all --prune
```
