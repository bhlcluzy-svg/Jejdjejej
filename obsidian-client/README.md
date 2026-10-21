# Obsidian Client

A Fabric mod for Minecraft 1.21.11 that replaces the main menu with the Obsidian one.

![Obsidian menu](screenshots/obsidian-menu.png)

## What it does

- **Intro.** The first time the menu opens each launch, the space scene fades in from
  black, the OBSIDIAN blocks drop into place column by column, and the buttons rise into
  place. Click or press any key to skip it.
- **Title.** "OBSIDIAN" built from Minecraft's own obsidian (and a few crying obsidian)
  block textures, Minecraft-logo style: black outline, bevelled edges and a 3D side. A
  sheen sweeps across it every few seconds.
- **Animated background.** The space picture in `tools/art/space.webp`, with its ships,
  Saturn and asteroids cut out into separate sprites so they cruise, bob and tumble. Stars
  twinkle, purple portal dust spirals into the obsidian ring, a ship flies across the
  screen every 10 to 20 seconds, and real Minecraft blocks float past in the foreground.
  Everything shifts with the mouse at its own depth (parallax), and the purple dust drifts
  away from your cursor. See `screenshots/obsidian-menu.mp4`.
- **Menu music.** `menu_music.ogg` starts with the intro and loops for as long as you are
  in the menus. It fades out when you join a world and back in when you leave. Vanilla menu
  music is muted while it plays.
- **Button bar** (bottom). Singleplayer, Multiplayer, Quick Join (rejoins the last server
  you played on; hover it to see which), Mods, Options and Quit.
- **Mods.** Opens Mod Menu if you have it, otherwise a built-in list with an
  "Open Mods Folder" button.
- **Volume** (top right). Drag the slider, or click the ♪ to mute. It only changes the
  menu music, not your Minecraft volume.
- **Info** (bottom left). Client version, Minecraft version and how many mods are loaded.
- **Vanilla Menu** (bottom right). Switches to the normal Mojang title screen. A
  "◆ Obsidian" button in its top right corner switches back. The choice is remembered.
- **Movable panels.** Drag any panel (including the logo) to move it. Right-click a panel
  to put it back. Positions are saved.
- **Smooth.** Vanilla caps menus at 60 FPS; the Obsidian menu runs at your normal Max
  Framerate setting, and everything moves with sub-pixel precision.
- **Loading screen.** Dark background and a violet progress bar instead of Mojang red.
  The Mojang logo stays because nothing from a mod can be drawn that early.

Settings are saved in `config/obsidian.json`.

## Install

1. Get the jar: GitHub Actions builds it on every push (workflow "Build Obsidian Client").
   It is also committed to `builds/`.
2. Put it in `.minecraft/mods` with Fabric Loader 0.17.3 or newer for 1.21.11. Fabric API is
   not required (the bits it needs are bundled), but it is fine if you have it.
   It works alongside Meteor and the Spawner Finder addon.

## Changing the music or logo

- Music: replace `src/main/resources/assets/obsidian/sounds/menu_music.ogg` with any
  `.ogg` file (`ffmpeg -i song.mp3 -c:a libvorbis -q:a 4 menu_music.ogg`).
- Title letters: edit the block patterns in `BlockLogo.java`.
- Glow sprite and mod icon: `python3 tools/make_sprites.py` (needs Pillow).
- Background: replace `tools/art/space.webp` and update the object boxes in
  `tools/make_space.py`, then run `python3 tools/make_space.py` (needs Pillow, numpy and
  opencv-python-headless). It cuts the listed objects into sprites, fills the holes, and
  regenerates `SpaceLayout.java`.
- Frame rate: launch with `-Dobsidian.logFps=true` to print it to the log every 3 seconds.

## Build

```sh
gradle build    # Gradle 9; jar ends up in build/libs/
```
