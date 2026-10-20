# Obsidian Client

A Fabric mod for Minecraft 1.21.11 that replaces the main menu with the Obsidian one.

![Obsidian menu](screenshots/obsidian-menu.png)

## What it does

- **Intro.** The first time the menu opens each launch, the screen starts black, the
  obsidian eclipse glows in, the OBSIDIAN logo zooms in with a flash of light, and the
  buttons rise into place. Click or press any key to skip it.
- **Background.** A night sky with a violet nebula, an obsidian eclipse behind the logo,
  distant crystal spires and big foreground shards with glowing cracks. Each layer moves a
  different amount as you move the mouse (parallax), and purple particles rise and drift
  away from your cursor.
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
- Logo, glow and icon: edit and run `python3 tools/make_logo.py` (needs Pillow). The logo
  uses Inter Display Black (SIL Open Font License).
- Background layers: edit and run `python3 tools/make_background.py` (needs Pillow and
  numpy), or replace the PNGs in `src/main/resources/assets/obsidian/textures/gui/bg/`
  with your own 1920x1080 art.
- Frame rate: launch with `-Dobsidian.logFps=true` to print it to the log every 3 seconds.

## Build

```sh
gradle build    # Gradle 9; jar ends up in build/libs/
```
