# Spawner Finder

A small Meteor Client addon for Minecraft 1.21.11 with three modules: `spawner-finder`, `packet-logger` and `activity-finder`.

## What it does

The server tells your game about every mob near you, even underground, so it can draw them.
A spawner spawns its mobs within a few blocks of itself. When several new mobs (skeletons by
default) appear in the same small spot below `max-y` within `time-window` seconds, the module
flags that spot: a chat alert, plus a box and a beam where the spawner probably is.

Limits:

- A spawner only runs while a player is within 16 blocks of it, so this finds spawners that
  someone is using right now, not abandoned ones.
- Mobs are only sent within entity tracking range (a few dozen blocks), so you have to be fairly close.
- Groups of natural cave spawns can cause false alerts. Raise `min-spawns` if that happens.

## Where it shows up

If Max Addon is installed, the module is added to the "Max Addon" category, under its other
modules. Otherwise it goes in Meteor's "World" category.

## Getting the jar

GitHub Actions builds it on every push (workflow "Build Spawner Finder"). Open the latest run,
download the `spawner-finder` artifact, unzip it, and put `spawner-finder-<version>.jar` in your
mods folder next to Meteor Client.

## Settings

| Setting | Default | What it does |
|---|---|---|
| mobs | skeleton | Which mobs to watch |
| max-y | 40 | Ignore mobs that appear above this height |
| cluster-radius | 5 | How close spawns must be to count as one spot |
| min-spawns | 4 | New mobs needed before a spot is flagged |
| time-window | 90 | Only count mobs from the last this-many seconds |

## packet-logger

Records what the server sends into `.minecraft/spawner-finder-logs/<label>_<time>.jsonl`, one event
per line, so a recording made near a known base can be compared with one made over untouched land.
Pick the `label` (Base, Wild or Test) before turning it on; turning it off saves the file and prints
a summary in chat.

It records: per-section sky and block light for each chunk, block changes, block entities, block
events (pistons, chests), sounds, entities appearing, particles, world events, and your position once
a second. It does not record chat or anything about your account, and the file never leaves your PC.

Light is one character per 16-block section, bottom to top, starting one section below the world:
`.` not sent, `e` sent as empty, `0` all dark, `F` all full, `m` mixed.

## activity-finder

Flags spots underground where something is happening right now, using the events the server sends
so the game can play them, and guesses what each spot is:

| Guess | From |
|---|---|
| Spawner | Spawner spawn world events, sent at the spawner's position whenever it spawns a mob |
| Possible skeleton (etc.) spawner | `min-mob-sounds` sounds from the same hostile mob coming from one spot |
| Player mining | Another player's block cracking animation, block-broken events, break sounds |
| Player walking | Footstep sounds |
| Storage / machine | Block events: chests, barrels and shulkers opening, pistons, note blocks |
| Building | Block place sounds |

Mob sounds on their own never flag a spot unless they cluster like a spawner. Events within
`ignore-near-me` blocks of you, above `max-y`, or less than `min-depth` blocks under the ground at that
column are ignored. A spawner event flags a spot at once; otherwise a spot needs `min-events` events
within `time-window` seconds. Flagged spots get a chat message, a 16x16 outline of their chunk at the
spot's height, a small box and a beam (red for spawners), and a line in the top-right corner of the
screen with the guess, coordinates and distance.

These events only reach you from a few dozen blocks away, so this catches activity you pass close
to, not bases from far away. To check it works without other players, build a piston clock below
`max-y` in singleplayer and stand more than `ignore-near-me` blocks from it.
