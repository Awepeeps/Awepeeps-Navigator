# Repository guidance: Awepeeps Navigator (retired)

This file applies to **every file in this repository**. **This repository is retired and archived**
on both the owner fork and the Awepeeps organization; treat it as read-only. Do not reactivate,
unarchive, push, or open pull requests without an explicit owner scope change. See
[navigator-retirement-guard](.agents/skills/navigator-retirement-guard/SKILL.md). It is a public
repo, so keep all content source/generic.

## Purpose and layout (historical)

A standalone Paper plugin that stored a shared directory of named points of interest; vanilla
controls compass binding. Code is `src/main/java/com/awepeeps/navigator`; config is
`src/main/resources/config.yml`; user and maintenance guides are under `docs/`; the build script is
`automation/build-navigator.sh`.

## Rules while retired

- Make no functional changes. Documentation-only cleanup is still limited by the archive.
- Do not add features, markers, commands, or config keys.
- Deployment-specific dates, timezones and coordinates never belong here.
- Active-server cross-links are owned by the Awepeeps-Minecraft-Setup repository; do not edit it.

## Build (only if a scope change authorizes it)

`automation/build-navigator.sh` runs a digest-pinned Maven 3.9.9 / Eclipse Temurin 21 container:
`mvn --batch-mode --no-transfer-progress clean verify`; jar `target/awepeeps-navigator-1.6.1.jar`.
