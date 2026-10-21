# XrayAudit

A Paper/Spigot 1.21.x plugin that checks what your server **actually sends** to players against the real world.
It reports which anti-xray and anti-ESP rules are failing.

It reads outgoing packets after Paper's anti-xray has run, using a bundled and relocated copy of PacketEvents.
It never modifies or cancels a packet.

## What it checks

| Check | How |
|---|---|
| Chunk ore leaks | Decodes sampled chunk packets and compares every buried target block (all 6 neighbours occluding) with a snapshot of the real chunk. It counts how many were sent as themselves, against the fake-ore (decoy) baseline. Blocks above Paper's `max-block-height` are counted separately. |
| Block-update leaks | For `block_change` / `multi_block_change` packets that send a target block, it measures how far the real block is from any exposed face. A reveal farther away than `update-radius` points to a path (plugin, command, physics) that skips anti-xray. |
| Block-entity leaks | Block entities in chunk packets and `block_entity_data` packets for fully buried positions (spawners, chests, ...). |
| Entity ESP | Entity spawns sent to players who have no line of sight to the entity. |
| Sound ESP | Positional sounds sent from places the player cannot see. |
| Early chunk sends | Chunks sent far outside the player's send-view-distance. |
| Global events | World events broadcast to everyone with coordinates. |

The report reads your `config/paper-world-defaults.yml`, the per-world `paper-world.yml` and `spigot.yml`.
That lets each finding name the setting to change, e.g. "coal_ore leaks above max-block-height → set `max-block-height: 128`".

## Install

1. `mvn package`, then copy `target/XrayAudit-<version>.jar` to `plugins/`.
2. Restart the server. Play normally, or log in with a test account.
3. `/xrayaudit report` writes `plugins/XrayAudit/reports/latest.md`. A report is also written every 10 minutes and at shutdown.
   Per-finding coordinates are in `reports/findings-*.ndjson`.

Commands (permission `xrayaudit.admin`, op by default): `status`, `report`, `reset`, `reload`, `on`, `off`.

If Paper's `use-permission` is true, ops bypass anti-xray. Audit with a non-op account by setting
`audit.mode: permission` and granting `xrayaudit.audited`.

## Cost

- Chunk packets are sampled (`chunks.sample-rate`, default 25%).
- Each sample takes up to 5 chunk snapshots on the main thread, capped by `chunks.max-snapshots-per-tick`.
- The per-block comparison runs on a background thread.
- Other checks are filtered on the network thread before anything is queued.
- The "Pipeline health" section of the report shows dropped observations if the sample rates are too high.

## Limits

- Light data, heightmaps and biomes are not analysed. Light data reveals cave shapes even with every ore hidden.
- Truth is read a tick or so after the packet is sent, so a block mined in between can be miscounted. This is rare, and the statistics are rate-based.
- Line of sight uses a block ray trace from the player's eye, so transparent blocks count as obstructions.
