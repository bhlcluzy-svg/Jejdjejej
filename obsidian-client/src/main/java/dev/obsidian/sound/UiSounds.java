package dev.obsidian.sound;

import dev.obsidian.ObsidianClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

/** The Obsidian menu's button sounds: the "zen" focus and hover sounds from uisfx (CC0). */
public final class UiSounds {
	private static final SoundEvent HOVER = SoundEvent.of(Identifier.of(ObsidianClient.MOD_ID, "ui.hover"));
	private static final SoundEvent CLICK = SoundEvent.of(Identifier.of(ObsidianClient.MOD_ID, "ui.click"));

	private static final long HOVER_COOLDOWN_MS = 70;
	private static long lastHover;

	private UiSounds() {
	}

	public static void hover() {
		if (ObsidianClient.CONFIG == null || !ObsidianClient.CONFIG.buttonHoverSound) return;
		// Sweeping across several buttons quickly plays one soft tick, not a burst.
		long now = Util.getMeasuringTimeMs();
		if (now - lastHover < HOVER_COOLDOWN_MS) return;
		lastHover = now;
		MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.ui(HOVER, 1.0f, 0.9f));
	}

	public static void click() {
		MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.ui(CLICK, 1.0f, 0.55f));
	}
}
