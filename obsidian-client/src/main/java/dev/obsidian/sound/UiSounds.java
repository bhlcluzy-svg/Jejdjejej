package dev.obsidian.sound;

import dev.obsidian.ObsidianClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/** The Obsidian menu's button sounds ("minimal" set from uisfx, CC0). Hover sounds are off unless enabled in the config. */
public final class UiSounds {
	private static final SoundEvent HOVER = SoundEvent.of(Identifier.of(ObsidianClient.MOD_ID, "ui.hover"));
	private static final SoundEvent CLICK = SoundEvent.of(Identifier.of(ObsidianClient.MOD_ID, "ui.click"));

	private UiSounds() {
	}

	public static void hover() {
		if (ObsidianClient.CONFIG == null || !ObsidianClient.CONFIG.hoverSounds) return;
		MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.ui(HOVER, 1.0f, 0.6f));
	}

	public static void click() {
		MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.ui(CLICK, 1.0f, 0.7f));
	}
}
