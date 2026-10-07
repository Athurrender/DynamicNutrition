# Dynamic Nutrition

Five nutrients, tracked from everything you eat, with values modelled on real nutritional data.
For Minecraft **1.20** through **26.3** on **Fabric**, **NeoForge** and **Forge**: see
[Versions](#versions) for exactly which.

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

Values are resolved in stages. Explicit data first, then item tags, then the recipe graph, so
bread is a carbohydrate because wheat is, even through ingredients you cannot eat on their own.
Anything still unresolved is worked out from the food and drink convention tags, including the
subtags mods add for themselves, so dragon meat from a mod nobody has written a compat patch for
still reads as meat. Then a food served differently, such as a stew in a cup or a slice of a feast,
is worth what that food is worth, and last a food's own name is read: spider meat is meat. Foods
that no stage can place are listed by `/dynamicnutrition unassigned`.

## Configuration

Cloth Config gives the mod a settings screen in four tabs: the HUD strip, the inventory button and
tooltips, the variety mechanic, and the nutrient effects, where each of the ten effects has its own
strength and you can hide Well Nourished and Malnourished from the top of the screen, from beside
the inventory, or both. Hidden, they still work. Where Cloth is optional, the mod runs on its
defaults without it and there is no settings screen.

## Commands

| Command | Who | What |
|---|---|---|
| `/dynamicnutrition get` | anyone | your current levels |
| `/dynamicnutrition variety` | anyone | your recent meals, and what your held food is worth |
| `/dynamicnutrition unassigned` | anyone | foods that resolved to no nutrients |
| `/dynamicnutrition export` | operators | the whole resolved table as a CSV, for pack authors |

## Versions

| Minecraft | Loaders | Cloth Config | Source folder |
|---|---|---|---|
| 26.1, 26.2, 26.3 | Fabric, NeoForge | optional | `26.x` |
| 1.21.11 | Fabric, NeoForge, Forge | required | `1.21` |
| 1.21, 1.21.1 | Fabric, NeoForge, Forge | required | `1.21` |
| 1.20, 1.20.1 | Fabric, Forge (also runs on NeoForge 47.1) | optional | `1.20` |

On Forge 1.21.11 the Cloth dependency is the unofficial **Cloth Config API Forge** port, because the
official build stops shipping a Forge module after 1.21.3. Same mod id, same version number. On
Forge 1.21.1 and 1.20.1 it is the official Cloth Config Forge build.

## Building

Each folder is its own Gradle build, because each needs a different Gradle, plugin set or JDK.
Code and art that are identical in every version live once, in `shared/`.

| Folder | JDK to run Gradle | Command |
|---|---|---|
| `26.x` | 25 | `./gradlew build` |
| `1.21` | 24 | `./gradlew build -Pmc=1.21.1` and `./gradlew build -Pmc=1.21.11` |
| `1.20` | 22 | `./gradlew build` |

`1.21` builds both 1.21.x versions from one tree: what differs between them lives in
`src/1.21.1` and `src/1.21.11` inside each module, and `versions/<version>.properties` holds every
version number. Jars land in each loader's `build/libs`.

## Licence

PolyForm Shield 1.0.0. See `LICENSE`, and `NOTICE.txt` for third party material.
