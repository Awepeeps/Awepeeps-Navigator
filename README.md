# Awepeeps Navigator

Awepeeps Navigator is a shared directory of named points of interest (POIs). Players can browse saved names, groups, worlds, and coordinates. The plugin does not guide a compass needle, track destinations, teleport, or bind/sync compasses.

## Player commands

- `/navigator` opens the POI menu while holding exactly one ordinary compass in either hand.
- `/navigator set <name> [group]` saves the player's current location as a shared POI. It requires exactly one ordinary compass.
- `/navigator remove <name>` removes a POI you created. Administrators with `awepeepsnavigator.admin` can remove any POI.
- `/navigator clear` remains as a legacy command and explains that compass binding is no longer supported.

Menu entries and selection show only the POI name, group, world, and coordinates. Browsing and viewing details are free. A successful POI creation spends one use from its required compass; failed creation does not. The default capacity is 64 uses per managed compass, and the compass is consumed after its final successful creation.

Lodestone-bound compasses are excluded from menu and creation use. Right-clicking with one, or right-clicking a lodestone block, is left entirely to vanilla Minecraft. Navigator detects a lodestone tracker from either its target or serialized tracking component, without requiring the target world or block to be available, and never changes its target. A lodestone compass with old Navigator metadata remains untouched.

See the [player guide](docs/player-guide.md) and [maintenance guide](docs/maintenance.md).

## Build and maintenance

Run `automation/build-navigator.sh` from a system with Docker. It uses a digest-pinned Maven 3.9.9 / Eclipse Temurin 21 image and runs `mvn --batch-mode --no-transfer-progress clean verify`. The resulting jar is `target/awepeeps-navigator-1.2.0.jar`.

The six unit tests cover finite POI coordinates, use counters, browsing/free failures, lodestone tracker detection, and pass-through policy. They do not replace manual client/server checks in the maintenance guide.

This is an original replacement plugin. POI coordinates remain in the legacy `teleports` config section to preserve existing data. Keep deployment configuration and server data outside this repository.
