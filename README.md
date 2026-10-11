# Awepeeps Navigator

Awepeeps Navigator is a compass-based Minecraft waypoint guidance plugin. It helps players find shared waypoints in the same world and never teleports players.

## Player commands

- `/navigator` opens the waypoint menu while holding exactly one ordinary compass.
- `/navigator set <name> [group]` creates a waypoint at your current location and binds the held compass to it.
- `/navigator clear` unbinds the held compass. It does not restore uses.
- `/navigator remove <name>` removes a waypoint you created. Administrators with `awepeepsnavigator.admin` can remove any waypoint.

To use a waypoint, open the menu with the same single compass and select an entry. Guidance follows the managed compass you hold. Browsing and selecting its current waypoint are free; creating a waypoint or changing its binding costs one use. The default maximum is 64 uses per managed compass.

Links must be within a configurable 16-block three-dimensional radius and in the same world. Failed attempts, including attempts outside that radius or in another world, do not charge a use. The plugin has no player or waypoint teleport feature.

See the [player guide](docs/player-guide.md) for usage details.

## Build and maintenance

Run `automation/build-navigator.sh` from a system with Docker. The script uses a digest-pinned Maven 3.9.9 / Eclipse Temurin 21 image and runs `mvn --batch-mode --no-transfer-progress clean verify` from the repository root. The resulting jar is `target/awepeeps-navigator-1.1.0.jar`.

The build runs eight unit tests for navigation math and policy behavior. These tests do not replace the manual client/server acceptance checks in the [maintenance guide](docs/maintenance.md).

This is an original replacement plugin. No upstream Navigator source is included.

## Repository workflow

The canonical repository is [Awepeeps/Awepeeps-Navigator](https://github.com/Awepeeps/Awepeeps-Navigator).
Work in the personal fork, push to origin, and open a PR targeting the org's main branch.
The plugin repository owns source, tests, generic documentation and build tooling.
Keep live waypoint configs, server data, addresses and authentication files out of this repository.
