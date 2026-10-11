# Awepeeps Navigator

Awepeeps Navigator maintains a shared directory of named points of interest (POIs). It shows saved names, groups, worlds, and coordinates. It does not guide a needle, teleport, or manage external map configuration. Existing `teleports` records remain in place across upgrades.

## Player use

Run `/navigator` to browse (with `awepeepsnavigator.view`) and `/navigator set <name> [group]` to create (with `awepeepsnavigator.create`). Viewing can be configured to require the configured item. Creation always requires exactly one configured item, a permitted target block in direct view within 5 blocks, and create permission. The default item is an ordinary compass and the default target is a lodestone. With the default sneak setting, sneak-right-click opens the directory; this interaction never binds or changes compass targets. Normal non-sneak lodestone right-clicking remains vanilla. Lodestone-bound compasses are always passed through and are never modified.

When durability is enabled, successful creation spends one use, default 64, and the item breaks on its last use. Failed creation and browsing are free. With durability disabled, creation does not change any item charge, lore, damage, maximum damage, or metadata, including old worn managed items. See the [player guide](docs/player-guide.md) and [maintenance guide](docs/maintenance.md).

## Configuration

```yaml
item: COMPASS
menu:
  sneak-right-click: true
durability:
  enabled: true
  creation-uses: 64
creation:
  allowed-blocks: [LODESTONE]
visibility:
  require-item: true
permissions:
  admin-bypass: false
bluemap-markers: true
```

`item` is any Bukkit item material name. `menu.sneak-right-click` requires sneaking for every interaction-opened menu when true; ordinary non-sneak right-click passes through to vanilla. `durability.enabled` controls all Navigator item wear; `creation-uses` sets the capacity of newly managed items (1–1,000,000). `creation.allowed-blocks` is a list of Bukkit block material names accepted by `/navigator set`. `visibility.require-item: false` allows menu viewing without the item; creation still requires it. `permissions.admin-bypass` opts into `awepeepsnavigator.bypass`, which can bypass view/create permission nodes, but never item or target requirements. Both bypass settings default off.

On upgrade, if `durability.creation-uses` is absent, its value is initialized from legacy `compass-max-uses` (or 64 when absent). The obsolete key can then be removed. Existing managed items keep stored use counters and capacities while durability remains enabled.

## Permissions and administration

`awepeepsnavigator.view` and `awepeepsnavigator.create` default to true. `awepeepsnavigator.admin` defaults to operators and allows removing any POI and issuing a regular compass. `awepeepsnavigator.bypass` defaults to operators, but has no effect until `permissions.admin-bypass: true`.

## BlueMap

When installed, `bluemap-markers: true` (default) mirrors every POI, including legacy records, to a `Navigator POIs` marker set on maps for its resolved world. BlueMap API 2.7.7 is optional. Setting this option to false disables markers. BlueMap visibility is independent of the in-game menu item requirement. Removing a target block does not remove the POI or map marker.

## Build

Run `automation/build-navigator.sh` with Docker. It uses a digest-pinned Maven 3.9.9 / Eclipse Temurin 21 image and runs `mvn --batch-mode --no-transfer-progress clean verify`. The jar is `target/awepeeps-navigator-1.5.0.jar`.
