---
name: navigator-retirement-guard
description: Handle the retired, archived Awepeeps Navigator repository. Use before touching Navigator to confirm the read-only status and avoid reactivating it.
---

# Navigator retirement guard

Awepeeps Navigator is **retired**. Both the owner fork and the Awepeeps organization repo are
archived, so pushes and pull requests are rejected. Read [AGENTS.md](../../../AGENTS.md).

## Default handling

1. Treat the repository as read-only; make no functional changes and open no pull requests.
2. If a task seems to require Navigator work, stop and get an explicit owner scope change
   (unarchive plus a stated goal) before proceeding.
3. Route current-server concerns to the settings/Setup owner; do not edit the
   Awepeeps-Minecraft-Setup cross-links from here.
4. Keep the public repo free of deployment coordinates, dates and timezones.

## If (and only if) a scope change authorizes work

1. Use an isolated worktree off `main`, the archived default branch.
2. Build with `automation/build-navigator.sh` (digest-pinned Maven 3.9.9 / Temurin 21 container):
   `mvn --batch-mode --no-transfer-progress clean verify`; jar `target/awepeeps-navigator-1.6.1.jar`.
3. Keep the POI directory's vanilla compass behaviour and all existing records intact.

## Do not

- Attempt to unarchive, force-push, or reopen the repository.
- Restore `test-supplies` or deploy anything from this repository.
