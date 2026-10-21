# Obsidian Client

A Fabric mod for Minecraft 1.21.11 that replaces the main menu with the Obsidian one.

![Obsidian menu](screenshots/obsidian-menu.png)

## What it does

- **Launch screen.** While Minecraft loads, an animated Obsidian screen replaces the
  Mojang one: drifting purple light and particles, a glowing gem, the name revealed letter
  by letter and a loading bar that follows the real progress. It stays at least long
  enough for its animation to play, then dissolves straight into the menu. Animations
  advance at most one frame's worth per frame, so loading hitches pause them instead of
  making them jump. (Drawn entirely from
  code-made textures, because the resource pack is not loaded yet at that point.)
- **Joining screens.** "Connecting to the server...", "Loading terrain...", "Saving world"
  and other progress screens show the scene (dimmed) with just the gem and a spinning
  ring of dots, no text. Nether
  and End portal loading keep Minecraft's own animation.
- **Intro.** The first time the menu opens each launch, the scene fades in from black,
  the "Obsidian" title rises in and the buttons follow. Click or press any key to skip it.
- **Title and text.** Poppins (SIL Open Font License), bundled in `assets/obsidian/fonts/`
  as `title.ttf` and `ui.ttf`. Minecraft's own font renderer samples TTF fonts without
  smoothing, which looks jagged when scaled, so `SmoothText` rasterises each string with
  Java's font renderer at the exact screen pixel size and draws it with linear filtering.
- **Animated background.** A calm sunset over a lake with an obsidian portal
  (`tools/art/sunset.jpg`, AI-upscaled to 2560 wide as `tools/art/sunset_hd.png` with
  Real-ESRGAN), split into sky, cloud and ground layers with soft cut-out edges. The clouds drift, a soft glow swirls inside the
  portal and smooth purple particles drift out of it, the
  lake sparkles, the sunset glow breathes and warm specks of light float about. Moving the
  mouse pans the layers gently side to side at different depths. See
  `screenshots/obsidian-menu.mp4`.
- **Same background everywhere.** Singleplayer, Multiplayer, Options and the other menus
  show the scene (blurred and darkened by Minecraft as usual) instead of the panorama.
- **Buttons.** A row of small pill buttons along the bottom: Singleplayer, Multiplayer,
  Mods, Options, Quit Game. Buttons play the "zen" hover and focus sounds from
  [uisfx](https://uisfx.com) (CC0 public domain): a soft tick when the mouse moves onto a
  button and a high, soft click when you press it. Set `"buttonHoverSound": false` in
  `config/obsidian.json` to turn the hover tick off.
- **Same size on any setting.** Menu sizes are set in screen pixels (designed at 1080p)
  rather than Minecraft's GUI units, so the menu looks the same whatever GUI Scale you use.
- **Menu music.** `menu_music.ogg` starts with the intro and loops for as long as you are
  in the menus. It fades out when you join a world and back in when you leave. Vanilla menu
  music never plays in the menus while the Obsidian menu is on, including at startup.
- **Mods.** Opens Mod Menu if you have it, otherwise a built-in list with an
  "Open Mods Folder" button.
- **Music controls** (top right). Pause stops the song where it is and resumes from the
  same spot; the speaker mutes it (it keeps playing silently); the slider sets the volume. It only changes the
  menu music, not your Minecraft volume.
- **Info** (bottom left). Client name, Minecraft version and how many mods are loaded.
- **Vanilla Menu** (bottom right). Switches to the normal Mojang title screen. A
  "◆ Obsidian" button in its top right corner switches back. The choice is remembered.
- **Movable panels.** You can drag the title, buttons, info or volume to move them, and
  right-click one to put it back. Positions are saved.
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
- Fonts: replace `title.ttf` / `ui.ttf` in `assets/obsidian/fonts/` with any TTF.
- Button sounds: replace `hover.ogg` / `click.ogg` in `assets/obsidian/sounds/` (Ogg
  Vorbis; `ffmpeg -i in.mp3 -c:a libvorbis out.ogg`).
- Mod icon: `python3 tools/make_sprites.py` (needs Pillow).
- Background: replace `tools/art/sunset.jpg`, upscale it with
  `python3 tools/ai_upscale.py MODEL.pth tools/art/sunset.jpg tools/art/sunset_hd.png 2560`
  (needs torch; model: RealESRGAN_x4plus_anime_6B.pth from
  huggingface.co/amd/realesrgan-x4plus-anime-6b), adjust the cloud, portal and lake
  positions at the top of `tools/make_scene.py` if they moved, then run `python3 tools/make_scene.py`
  (needs Pillow, numpy and opencv-python-headless). It cuts out the clouds and ground,
  fills the gaps, and regenerates `SceneLayout.java`.
- Fonts: replace `title.ttf` / `ui.ttf` in `assets/obsidian/fonts/` with any TTF.
- Button sounds: replace `hover.ogg` / `click.ogg` in `assets/obsidian/sounds/` (Ogg
  Vorbis; `ffmpeg -i in.mp3 -c:a libvorbis out.ogg`).
- Mod icon: `python3 tools/make_sprites.py` (needs Pillow).
- Frame rate: launch with `-Dobsidian.logFps=true` to print it to the log every 3 seconds.

## Build

```sh
gradle build    # Gradle 9; jar ends up in build/libs/
```
