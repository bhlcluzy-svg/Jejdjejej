# Obsidian Client

A Fabric mod for Minecraft 1.21.11 that replaces the main menu with the Obsidian one.

![Obsidian menu](screenshots/obsidian-menu.png)

## What it does

- **Intro.** The first time the menu opens each launch, the screen starts black, the
  OBSIDIAN logo fades and zooms in with a flash of light, then slides up as the rest of the
  menu rises in. Click or press any key to skip it.
- **Menu music.** `menu_music.ogg` starts with the intro and loops while you are in the
  menus. It fades out when you join a world and back in when you leave. Vanilla menu music
  is muted while it plays.
- **Volume** (top right). Drag the slider, or click the ♪ to mute. It only changes the
  menu music, not your Minecraft volume.
- **Buttons.** Singleplayer, Multiplayer, Quick Join (rejoins the last server you played on),
  Mods, Options and Quit.
- **Mods.** Opens Mod Menu if you have it, otherwise a built-in list with an
  "Open Mods Folder" button.
- **Info panel** (bottom left). Client version, Minecraft version, Fabric Loader version
  and how many mods are loaded.
- **Vanilla Menu** (bottom right). Switches to the normal Mojang title screen. A
  "◆ Obsidian" button in its top right corner switches back. The choice is remembered.
- **Movable panels.** Drag any panel to move it. Right-click a panel to put it back.
  Positions are saved.
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
- Logo, glow and icon: edit and run `python3 tools/make_logo.py` (needs Pillow). The logo
  uses Inter Display Black (SIL Open Font License).

## Build

```sh
gradle build    # Gradle 9; jar ends up in build/libs/
```
