# Maintenance guide

## Build

Run `automation/build-navigator.sh` from the repository root. It uses the digest-pinned Maven 3.9.9 / Eclipse Temurin 21 container and runs `mvn --batch-mode --no-transfer-progress clean verify`. Output: `target/awepeeps-navigator-1.5.0.jar`. Spigot API and optional BlueMap API versions are pinned in `pom.xml`.

## Configuration reference

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

- `item`: Bukkit item material required for opening the menu (unless item visibility is disabled) and always required for creation. Exactly one must be held.
- `menu.sneak-right-click`: when true, interaction-opened menus require sneaking; ordinary non-sneak right-click passes through to vanilla. It does not create a POI or bind the item. Defaults true.
- `durability.enabled`: enables managed-item use metadata, lore, damage, maximum damage, and breaking. False means no such item changes for any item, including old worn managed items.
- `durability.creation-uses`: new managed-item capacity, default 64; valid values are 1 through 1,000,000. Existing items retain stored capacity and remaining charges when durability is enabled.
- `creation.allowed-blocks`: Bukkit block material whitelist used by direct ray-traced `/navigator set` targeting. Default `[LODESTONE]`.
- `visibility.require-item`: require the configured item for menu viewing. False permits viewing without it, but creation still requires the item and its cost/native safeguards.
- `permissions.admin-bypass`: default false. If true, `awepeepsnavigator.bypass` bypasses view and create permission checks. It does not waive item or target requirements.
- `bluemap-markers`: optional marker mirror; default true. It remains independent from in-game item visibility.

On upgrade, when `durability.creation-uses` is absent, the plugin copies legacy `compass-max-uses` (or 64 if absent) to the new setting and saves it. Remove the obsolete key after migration. Existing POIs under `teleports` retain coordinates and creator IDs; missing immutable IDs are assigned once. No legacy point is removed or assigned a block.

Permissions: `awepeepsnavigator.view` and `.create` default true; `.admin` and `.bypass` default op. `.admin` manages removal and compass issuance. `.bypass` only affects view/create checks when the config opt-in is enabled.

## Verification and manual acceptance

The policy tests cover use charging, visibility vs creation requirements, bypass gating, item wear off, interaction settings, lodestone protection, and marker ownership. Manual client/server checks are still required:

- Install with and without BlueMap; confirm plugin functionality and marker behavior. Turning off `visibility.require-item` must not change BlueMap markers.
- Upgrade a 1.4.0 config containing `compass-max-uses: 23` and no new setting; confirm `durability.creation-uses` becomes 23 even though the jar bundles a default value. Preserve every existing POI.
- Test default and custom configured items and allowed-block lists. Verify direct ray targeting respects the whitelist, range, and obstruction.
- Verify sneak-right-click opens menu without auto-binding or creating. Normal non-sneak lodestone right-click and bound compass behavior remain vanilla.
- Exercise main/offhand, permission denial and opt-in bypass, item-free viewing, and creation's always-required item.
- With durability enabled, verify one use per successful creation, no cost on failures, and final-use break. With it disabled, test fresh and previously worn managed items: charge, lore, damage, max damage, and break behavior must not change.
- Change held item or hotbar selection while menu is open. Confirm the stale menu closes; clicking/dragging must not move or steal GUI items.
- Remove/replace target blocks and confirm POIs persist; verify BlueMap continues to show the same records.

## BlueMap behavior

With BlueMap present and `bluemap-markers: true`, Navigator mirrors every POI, including legacy records, to `awepeeps-navigator-pois` on maps belonging to resolved worlds. Only marker sets created by this bridge instance are reconciled or removed. API callbacks do not read Bukkit world/config state, and lifecycle changes invalidate queued reconciliations. Missing BlueMap or unresolved worlds produce no markers; API failures log a warning without blocking POI operations. No marker file or BlueMap configuration is created.

## Project scope

This repository is the standalone POI directory plugin. It contains no deployment automation or server data.
