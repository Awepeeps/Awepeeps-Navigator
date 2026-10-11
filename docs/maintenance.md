# Maintenance guide

## Build

From the repository root, run:

```bash
automation/build-navigator.sh
```

The script mounts the repository root into a digest-pinned Maven 3.9.9 / Eclipse Temurin 21 container and runs `mvn --batch-mode --no-transfer-progress clean verify`. It writes `target/awepeeps-navigator-1.1.0.jar`. The project compiles for Java 21 against the Spigot API version specified in `pom.xml`.

The automated suite contains eight unit tests for navigation math and plugin policy behavior. Review the source and configuration defaults when changing behavior; waypoint data is stored under `teleports` for compatibility with existing data. Missing immutable waypoint IDs are assigned during migration so a compass cannot silently follow a deleted waypoint recreated under the same name.

## Manual acceptance gates

Before treating a build as ready for a release, verify these behaviors on a compatible Minecraft server and client:

- Existing waypoint coordinates, groups, and creator data survive migration; missing IDs are saved.
- Names differing only by case collide, and invalid names or groups are rejected.
- Opening and selecting a waypoint requires exactly one ordinary compass, with main-hand and off-hand behavior checked.
- A link exactly at the radius boundary succeeds; beyond-radius and other-world attempts fail without charging a use.
- Re-selecting the current waypoint and browsing menus are free; changing targets and creating waypoints each spend one use.
- Switching between managed compasses changes guidance; clearing a compass binding does not restore uses.
- A deleted waypoint ID is unavailable, even if a waypoint with the same name is later created.
- Spending the final use completes the successful action and then consumes the held compass.
- Binding and remaining uses survive relog, restart, trade, and repair workflows; lore and item metadata are retained as the server API permits.
- Client behavior for main/off hand interactions, native damage bars, and compass metadata is checked directly.
- Confirm the plugin provides no teleport behavior.

The manual gates cover item and client/server behavior that unit tests cannot establish.

## Project scope

Awepeeps Navigator is an original replacement plugin. No upstream Navigator source is included. The repository contains plugin code and generic build and usage documentation; it does not contain deployment automation or server data.
