# Maintenance guide

## Build

From the repository root, run:

```bash
automation/build-navigator.sh
```

The script uses a digest-pinned Maven 3.9.9 / Eclipse Temurin 21 container and runs `mvn --batch-mode --no-transfer-progress clean verify`. It writes `target/awepeeps-navigator-1.4.0.jar`. The project compiles for Java 21 against the pinned Spigot API and provided BlueMap API 2.7.7 in `pom.xml`. BlueMap API 2.8.2 has Java 25 class files, so it cannot be consumed by this Java 21 build.

The seven unit tests cover finite coordinates, use counters, free browsing/failed creation, lodestone tracker and interaction policy, and marker-set identity ownership. POI coordinates and creator IDs remain under the legacy `teleports` config section. Existing entries retain their coordinates and creator IDs; missing immutable IDs are still assigned at startup for stable POI identity. Upgrade does not infer or place lodestones for existing entries; the lodestone target requirement applies to new entries only.

## Optional BlueMap markers

`bluemap-markers: true` is the default. With BlueMap installed and enabled, Navigator creates the fixed `awepeeps-navigator-pois` marker set (`Navigator POIs`) on every BlueMap map for each resolved Bukkit world and mirrors all configured POIs, including old records. It uses the immutable POI id for marker identity and retains stored decimal coordinates as-is. Only sets created by this bridge instance are reconciled or removed; an existing foreign set with the same key is left untouched. Names and legacy group values are HTML escaped in marker labels and details.

The optional API bridge is loaded only when the BlueMap plugin is present. Its API callbacks can run asynchronously and may run immediately during registration; the bridge reads no Bukkit world or configuration from those callbacks. A main-thread snapshot contains only marker data and world UUIDs. API lifecycle changes invalidate queued reconciliations, and API calls are serialized through the bridge lifecycle lock. On BlueMap disable the bridge removes only its exact owned sets before the API callback returns; plugin shutdown unregisters its exact listeners and removes its owned sets when the API remains usable. Startup and successful POI creation/removal rebuild from config. API failures log a warning without preventing POI creation or removal. Set `bluemap-markers: false` to disable the integration. Missing BlueMap or unresolved worlds simply produce no markers. No separate marker data or BlueMap configuration is written.

## Manual acceptance gates

- Install without BlueMap and verify the plugin loads, POI creation/removal works, and no BlueMap classes are loaded or required; repeat with BlueMap present and `bluemap-markers: false`.
- With BlueMap present, verify startup creates one POI marker per record (including legacy records) on every map belonging to a loaded world; marker position preserves stored decimals, and marker label/detail safely display HTML-like legacy group text.
- Create and remove POIs and verify map markers follow successful config saves only. Remove/replace a lodestone and verify its POI marker persists.
- Reload BlueMap repeatedly and verify markers return once, queued callbacks never restore stale snapshots, and plugin disable unregisters listeners and removes only sets this bridge created.
- Prepopulate an unrelated set using `awepeeps-navigator-pois`, including one labeled `Navigator POIs`; verify it remains unchanged and is not overwritten or removed.
- Confirm API update failures only log a warning and do not roll back or block core POI save/removal.

- Existing POI coordinates, groups, and creator IDs survive upgrade; missing POI IDs are saved.
- Names that differ only by case collide; invalid names and groups are rejected.
- Opening the menu and creating a POI each require exactly one ordinary compass; exercise main-hand and offhand use.
- Menu entry selection shows only name, group, world, and coordinates. Browsing and selection do not charge uses, change item metadata, or alter compass behavior.
- A successful POI creation spends one use; failed creation does not. The final use completes the save and then consumes the compass.
- `/navigator set` requires a directly targeted lodestone within 5 blocks. Confirm nearby non-lodestones, targets beyond five blocks, and lodestones blocked by another block are rejected without charging uses; a valid lodestone saves its block's integer coordinates even when the player stands elsewhere.
- Admin removal works for any POI; players can remove only POIs they created.
- A legacy 1.1 managed ordinary compass loses old destination/waypoint ID PDC and `Navigator bound:` lore when next used to open the menu or create a POI, while retaining uses and capacity. No use is refilled.
- A lodestone-bound compass with no lodestone available, or in an unavailable dimension, remains excluded from the menu and creation. Its lodestone target and tracking behavior remain vanilla.
- Bind an ordinary compass to an actual placed lodestone using vanilla gameplay; verify Navigator then excludes it and preserves that target when the player switches held items.
- Right-clicking either a lodestone-bound compass or a lodestone block is not cancelled and does not open Navigator or mutate the compass.
- Check both hands: offhand ordinary-compass fallback does not open a menu while a main-hand lodestone compass is being used. Verify ordinary main-hand use beside an offhand lodestone compass separately.
- Changing or removing the actual lodestone preserves the original target and vanilla compass behavior. Verify compass behavior while switching held items; no periodic plugin mutation occurs.
- Removing or replacing a lodestone does not delete its POI record. Confirm that the saved location remains visible; no automatic block validation, repair, or cleanup occurs.
- `/navigator clear` gives the compatibility explanation and does not change compass data. The plugin has no teleport behavior.

The lodestone gates are manual client/server acceptance checks; unit policy tests do not claim to verify real client behavior. `CompassMeta.hasLodestone()` can report false when a tracker has no target. Navigator also checks for the serialized `LodestoneTracked` component, which remains present for targetless trackers (including `tracked=false`), and never calls lodestone target or tracking setters. This check does not need the target world or block to be available. See the [Paper compass metadata implementation](https://github.com/PaperMC/Paper/blob/main/paper-server/src/main/java/org/bukkit/craftbukkit/inventory/CraftMetaCompass.java) for its target and tracker serialization behavior.

## Project scope

Awepeeps Navigator is an original replacement plugin. This repository contains plugin code, tests, generic documentation, and build tooling. It does not contain deployment automation or server data.
