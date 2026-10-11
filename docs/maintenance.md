# Maintenance guide

## Build

From the repository root, run:

```bash
automation/build-navigator.sh
```

The script uses a digest-pinned Maven 3.9.9 / Eclipse Temurin 21 container and runs `mvn --batch-mode --no-transfer-progress clean verify`. It writes `target/awepeeps-navigator-1.2.0.jar`. The project compiles for Java 21 against the pinned Spigot API version in `pom.xml`.

The six unit tests cover finite coordinates, use counters, free browsing/failed creation, and lodestone tracker and interaction policy. POI coordinates and creator IDs remain under the legacy `teleports` config section. Existing entries retain their coordinates and creator IDs; missing immutable IDs are still assigned at startup for stable POI identity.

## Manual acceptance gates

- Existing POI coordinates, groups, and creator IDs survive upgrade; missing POI IDs are saved.
- Names that differ only by case collide; invalid names and groups are rejected.
- Opening the menu and creating a POI each require exactly one ordinary compass; exercise main-hand and offhand use.
- Menu entry selection shows only name, group, world, and coordinates. Browsing and selection do not charge uses, change item metadata, or alter compass behavior.
- A successful POI creation spends one use; failed creation does not. The final use completes the save and then consumes the compass.
- Admin removal works for any POI; players can remove only POIs they created.
- A legacy 1.1 managed ordinary compass loses old destination/waypoint ID PDC and `Navigator bound:` lore when next used to open the menu or create a POI, while retaining uses and capacity. No use is refilled.
- A lodestone-bound compass with no lodestone available, or in an unavailable dimension, remains excluded from the menu and creation. Its lodestone target and tracking behavior remain vanilla.
- Bind an ordinary compass to an actual placed lodestone using vanilla gameplay; verify Navigator then excludes it and preserves that target when the player switches held items.
- Right-clicking either a lodestone-bound compass or a lodestone block is not cancelled and does not open Navigator or mutate the compass.
- Check both hands: offhand ordinary-compass fallback does not open a menu while a main-hand lodestone compass is being used. Verify ordinary main-hand use beside an offhand lodestone compass separately.
- Changing or removing the actual lodestone preserves the original target and vanilla compass behavior. Verify compass behavior while switching held items; no periodic plugin mutation occurs.
- `/navigator clear` gives the compatibility explanation and does not change compass data. The plugin has no teleport behavior.

The lodestone gates are manual client/server acceptance checks; unit policy tests do not claim to verify real client behavior. `CompassMeta.hasLodestone()` can report false when a tracker has no target. Navigator also checks for the serialized `LodestoneTracked` component, which remains present for targetless trackers (including `tracked=false`), and never calls lodestone target or tracking setters. This check does not need the target world or block to be available. See the [Paper compass metadata implementation](https://github.com/PaperMC/Paper/blob/main/paper-server/src/main/java/org/bukkit/craftbukkit/inventory/CraftMetaCompass.java) for its target and tracker serialization behavior.

## Project scope

Awepeeps Navigator is an original replacement plugin. This repository contains plugin code, tests, generic documentation, and build tooling. It does not contain deployment automation or server data.
