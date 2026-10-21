package dev.obsidian.sound;

import dev.obsidian.ObsidianClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/** The Obsidian menu's own button sounds (made by tools/make_sounds.py). */
public final class UiSounds {
	private static final SoundEvent HOVER = SoundEvent.of(Identifier.of(ObsidianClient.MOD_ID, "ui.hover"));
	private static final SoundEvent CLICK = SoundEvent.of(Identifier.of(ObsidianClient.MOD_ID, "ui.click"));

	private UiSounds() {
	}

	public static void hover() {
		MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.ui(HOVER, 1.0f, 0.6f));
	}

	public static void click() {
		MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.ui(CLICK, 1.0f, 0.8f));
	}
}
