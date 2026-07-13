# NekoBox UI reference

A verbatim copy of the **NekoBox for Android** UI, kept here purely as a
reference for porting screens into MikuBox. **Nothing in this folder is part of
the Gradle build** — it lives outside `app/src`, so it is never compiled,
linked, or packaged. It will not affect the app or the build.

## Contents

- `res/` — the complete NekoBox `app/src/main/res` (all 49 layouts, ~141
  drawables, menus, `values` (themes/attrs/styles/colors/strings/dimens),
  fonts, raw animations, etc.).
- `java/ui/` — activities, fragments and bottom sheets
  (`io.nekohasekai.sagernet.ui`).
- `java/widget/` — custom views used by the layouts
  (`io.nekohasekai.sagernet.widget`).
- `java/neko/` — MikuRay/`com.neko` UI helpers (particles, shape image views,
  marquee text, blur, etc.).

## Why it can't just be dropped into the app

NekoBox's UI is welded to its **sing-box** stack:

- ~97 of its source files use the sing-box data model (`Room SagerDatabase`,
  `DataStore`, `ProxyEntity`, `ProfileManager`); MikuBox has none of these — it
  uses the Mihomo core with a simple `SharedPreferences`-backed profile store.
- Its `res/values` (theme, `attrs`, `colors`, `strings`) collide by name with
  MikuBox's own resources.
- ~28 files call the sing-box/libbox native core directly.

So screens are ported **one at a time**, re-skinned onto MikuBox's Mihomo-backed
data layer and Material3 theme, rather than pasted wholesale. Use these files to
copy layouts/drawables and match the visual design.

Source: <https://github.com/MatsuriDayo/NekoBoxForAndroid> (GPL-3.0).
