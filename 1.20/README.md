# Dynamic Nutrition

Five nutrients, tracked from everything you eat, with values modelled on real nutritional data.
For Minecraft **1.20** and **1.20.1** on **Fabric** and **Forge**. The Forge jar also runs on NeoForge 47.1.

Hunger tells you how long since you last ate. It says nothing about what you ate. Dynamic Nutrition
adds the other half: carbohydrates, protein, fat, vitamins and minerals, each filled by different
foods and each drained at its own rate.

## How it works

- **Every food is worth real amounts of five nutrients.** A carrot is worth far more vitamins than
  cooked beef, and cooked beef is worth far more protein than a carrot, because the numbers come
  from actual per-100g composition rather than from a category label.
- **Nutrients drain at different speeds.** Carbohydrates burn fastest, fat is storage and barely
  moves. Five bars that emptied together would be one bar with five labels.
- **Eating the same thing over and over is worth less.** Of your last several meals, each repeat of
  a food costs part of its value, down to a floor it never drops below. Variety is the mechanic, and
  it affects nutrition only, never hunger.
- **Each nutrient has its own effect.** Above its green line it gives one and below its red line
  another: carbs give Speed or Slowness, protein Strength or Weakness, fat Resistance or Hunger,
  vitamins Regeneration or Blindness, and minerals Haste or Mining Fatigue. Each works at half the
  strength of the vanilla level I effect by default, and each can be set anywhere from 0 to 100% in
  the config. Milk does not clear them: the cure for a deficiency is food.
- **Two icons, not ten.** Every buff your diet earns shows as **Well Nourished** and every debuff as
  **Malnourished**, both at once if you are doing well on one nutrient and badly on another. Hover
  either one beside your inventory to see exactly which effects it stands for, each coloured like
  its nutrient. A potion or beacon that gives the same vanilla effect keeps its own icon, and you
  never see an effect twice. Hovering a bar on the nutrition screen names the two effects that bar
  controls.

## Where to look

- **Press N** to open the nutrition screen, or use the small button in your inventory. The keybind
  is the primary route, so if the button ever collides with another mod you can simply turn it off.
- **A strip beside the hunger bar** shows all five at a glance. Each section is ten pixels, so one
  pixel is worth exactly ten points.
- **Food tooltips** show what that food is worth to you right now. Hold F3 and H for where the value
  came from.
- **The bars are marked** with the two thresholds for that nutrient. The marks turn white once you
  are past them.

## Any food mod works, with no configuration

Values are resolved in four stages. Explicit data first, then item tags, then the recipe graph, so
bread is a carbohydrate because wheat is. Anything still unresolved is worked out from the food
convention tags that both loaders ship, which is why a food from a mod nobody has written a compat
patch for still feeds you something sensible.

## Configuration

Cloth Config is **optional** on this version. With it installed there is a settings screen in four tabs: the HUD strip, the
inventory button and tooltips, the variety mechanic, and the nutrient effects, where each of the
ten effects has its own strength and you can hide Well Nourished and Malnourished from the top of
the screen, from beside the inventory, or both. Hidden, they still work.

Without it the mod runs on its defaults and there is no settings screen. On Forge and NeoForge it
is the official Cloth Config Forge build.

## Commands

| Command | Who | What |
|---|---|---|
| `/dynamicnutrition get` | anyone | your current levels |
| `/dynamicnutrition variety` | anyone | your recent meals, and what your held food is worth |
| `/dynamicnutrition unassigned` | anyone | foods that resolved to no nutrients |
| `/dynamicnutrition export` | operators | the whole resolved table as a CSV, for pack authors |

## Building

JDK 22. The toolchain compiles against Java 17, which is what this Minecraft version requires.

```
./gradlew build
```

Jars land in `fabric/build/libs` and `forge/build/libs`.

## Licence

PolyForm Shield 1.0.0. See `LICENSE`, and `NOTICE.txt` for third party material.
