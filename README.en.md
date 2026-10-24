# Simple Trade Reroll — Minecraft Java 26.3 / Fabric

Source code: [GitHub](https://github.com/ahmetkoyuturkder-cmd/simple-trade-reroll). Licensed under MIT.

Adds Reroll, Target and Auto controls next to the villager trading screen. The interface and chat messages follow the Minecraft language setting in English or Turkish.

- **Reroll:** Generate new trades once.
- **Target:** Search for a tradeable enchanted book, an item the villager sells, or an item the villager buys.
- **Level:** Require an exact enchantment level. `0` accepts any level.
- **Maximum emerald cost:** Accept trades at or below the chosen cost. `0` accepts any price. This filter does not apply when the villager pays emeralds for your item.
- **Search / Auto:** Search up to 2,000 rolls. Progress appears beside the trading screen. Press Auto again to stop.

Select a target, set the filters and press Search. When the desired offer appears, trade once to lock it in. Text fields keep inventory shortcuts such as `E` from closing the trading screen while typing.

This mod works in **singleplayer only**. It rerolls only unused novice villagers whose two trade input slots are empty. It does not break the villager's job site.

## Installation

Put `simple-trade-reroll-26.3-1.2.1.jar` in the `mods` folder of a Fabric 26.3 profile with a compatible Fabric API. Remove or disable earlier versions of this mod and other villager reroll mods, then restart Minecraft.

On narrow screens, open the controls with the `TR` button. It shows the attempt count while searching.

## Build from source

Use Java 25 and run `./gradlew build`. The JAR is created in `build/libs/`.

## Limits

Only trades available to an unused novice villager can match. Targets unavailable to that profession will not be found. Servers with other players are unsupported.
