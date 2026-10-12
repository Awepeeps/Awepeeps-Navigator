# Maintenance guide

## Build

Run `automation/build-navigator.sh` from the repository root. It uses the digest-pinned Maven 3.9.9 / Eclipse Temurin 21 container and runs `mvn --batch-mode --no-transfer-progress clean verify`. Output: `target/awepeeps-navigator-1.6.0.jar`. Spigot API and optional BlueMap API versions are pinned in `pom.xml`.

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
bluemap-spawn-marker: false
test-supplies:
  enabled: false
  expires-on: ""
  timezone: ""
```

- `item`: Bukkit item material required for opening the menu (unless item visibility is disabled) and always required for creation. Exactly one must be held.
- `menu.sneak-right-click`: when true, interaction-opened menus require sneaking; ordinary non-sneak right-click passes through to vanilla. It does not create a POI or bind the item. Defaults true.
- `durability.enabled`: enables managed-item use metadata, lore, damage, maximum damage, and breaking. False means no such item changes for any item, including old worn managed items.
- `durability.creation-uses`: new managed-item capacity, default 64; valid values are 1 through 1,000,000. Existing items retain stored capacity and remaining charges when durability is enabled.
- `creation.allowed-blocks`: Bukkit block material whitelist used by direct ray-traced `/navigator set` targeting. Default `[LODESTONE]`.
- `visibility.require-item`: require the configured item for menu viewing. False permits viewing without it, but creation still requires the item and its cost/native safeguards.
- `permissions.admin-bypass`: default false. If true, `awepeepsnavigator.bypass` bypasses view and create permission checks. It does not waive item or target requirements.
- `bluemap-markers`: optional marker mirror; default true. It remains independent from in-game item visibility.
- `bluemap-spawn-marker`: optional live spawn marker; default false. Administrators can enable/disable it with `/navigator spawn on|off` in game, console, or RCON. It follows the first normal world in Bukkit's world order (the primary Overworld), reading its live `World#getSpawnLocation()` and refreshing every five seconds. Coordinates are never configured. It uses its own `World Spawn` layer, separate from the Navigator POI set.
- `test-supplies.enabled`: default false. When enabled, both `test-supplies.expires-on` (`YYYY-MM-DD`) and `test-supplies.timezone` (IANA zone ID) must be valid. The feature is active until the configured date ends in that timezone; invalid or missing values fail closed.

On upgrade, when `durability.creation-uses` is absent, the plugin copies legacy `compass-max-uses` (or 64 if absent) to the new setting and saves it. Remove the obsolete key after migration. Existing POIs under `teleports` retain coordinates and creator IDs; missing immutable IDs are assigned once. No legacy point is removed or assigned a block.

Permissions: `awepeepsnavigator.view` and `.create` default true; `.admin` and `.bypass` default op. `.admin` manages removal and compass issuance. `.bypass` only affects view/create checks when the config opt-in is enabled.

## Verification and manual acceptance

The policy tests cover use charging, visibility vs creation requirements, bypass gating, item wear off, interaction settings, lodestone protection, marker ownership/retry state, safe chest placement gates, expiry parsing, and starter-kit contents. Manual client/server checks are still required:

- Install with and without BlueMap; confirm plugin functionality and marker behavior. Turning off `visibility.require-item` must not change BlueMap markers.
- Enable the spawn marker with `/navigator spawn on` in game and from console/RCON; confirm it appears under its own BlueMap layer at the primary Overworld spawn, never at Nether or End coordinates. Change the Overworld spawn with `/setworldspawn` and verify the marker follows within five seconds. Turn it off and confirm the spawn layer is removed while Navigator POIs and unrelated layers remain.
- In a disposable test server, configure an intentionally temporary expiry and enable `test-supplies`. Verify one chest appears near the primary normal world's current spawn, outside the configured spawn-protection square, only on a solid dry floor with air at and above the chest position; existing blocks/chests remain untouched. Restart and confirm the same chest is retained without restocking. Break/remove the chest and confirm the issuance tombstone prevents a replacement kit. Disable the feature or let it expire and confirm it creates no new chest and leaves existing items/world blocks untouched. Never put deployment expiry details in the public repository.
- Upgrade a 1.4.0 config containing `compass-max-uses: 23` and no new setting; confirm `durability.creation-uses` becomes 23 even though the jar bundles a default value. Preserve every existing POI.
- Test default and custom configured items and allowed-block lists. Verify direct ray targeting respects the whitelist, range, and obstruction.
- Verify sneak-right-click opens menu without auto-binding or creating. Normal non-sneak lodestone right-click and bound compass behavior remain vanilla.
- Exercise main/offhand, permission denial and opt-in bypass, item-free viewing, and creation's always-required item.
- With durability enabled, verify one use per successful creation, no cost on failures, and final-use break. With it disabled, test fresh and previously worn managed items: charge, lore, damage, max damage, and break behavior must not change.
- Change held item or hotbar selection while menu is open. Confirm the stale menu closes; clicking/dragging must not move or steal GUI items.
- Remove/replace target blocks and confirm POIs persist; verify BlueMap continues to show the same records.

## BlueMap behavior

With BlueMap present and `bluemap-markers: true`, Navigator mirrors every POI, including legacy records, to `awepeeps-navigator-pois` on maps belonging to resolved worlds. Only marker sets created by this bridge instance are reconciled or removed. API callbacks do not read Bukkit world/config state, and lifecycle changes invalidate queued reconciliations. Missing BlueMap or unresolved worlds produce no markers; API failures log a warning without blocking POI operations. No marker file or BlueMap configuration is created.

When `bluemap-spawn-marker` is enabled, the primary Overworld spawn is independently mirrored to `awepeeps-navigator-spawn`. The selected world is the first `Environment.NORMAL` world in Bukkit's world order, so Nether/End spawn values are excluded. The live spawn is polled every five seconds to account for `/setworldspawn` and world replacement/reset; repeated polling also retries writes that previously failed. Turning the option off removes only this plugin-owned spawn marker set. Disabling Navigator cleans up its own POI and spawn layers without removing sets that merely share their IDs or labels but belong to another plugin.

Test supplies are a separate, default-off test facility and are not part of Navigator's compass behavior. The configured local-date expiry is inclusive and timezone-aware. The manager runs on the server thread after startup and retries at intervals while active. It targets only the first normal world selected by the same stable Bukkit world order as the spawn marker, uses loaded chunks, checks for a solid dry floor, air at chest height and above, and avoids adjacent chest blocks so it does not merge with existing inventories. Placement is outside Bukkit's square spawn protection using `Server#getSpawnRadius()`; search is bounded to 64 blocks and defers if protection is 64 blocks or larger. Before placing anything, it saves a durable per-world issuance tombstone; if that write fails, it leaves the world unchanged. A plugin PDC marker and private location store identify owned chests. The tombstone prevents replacement kits if the chest is broken, removed, or loses its marker; the PDC marker can recover ownership if saving its location fails. Existing marked chests are never restocked, including after restart; there is no cleanup on disable or expiry, so player-held items and world contents remain intact. The kit includes lodestone, bed, compass, brush, logs, planks, cobblestone, crafting table, sticks, torches, diamonds, and iron ingots.

## Project scope

This repository is the standalone POI directory plugin. It contains no deployment automation or server data.
