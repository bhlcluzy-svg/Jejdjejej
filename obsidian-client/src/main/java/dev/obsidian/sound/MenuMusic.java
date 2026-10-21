package dev.obsidian.sound;

import dev.obsidian.ObsidianClient;
import dev.obsidian.gui.LaunchScreen;
import dev.obsidian.mixin.SoundManagerAccessor;
import dev.obsidian.mixin.SoundSystemAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.Channel;
import net.minecraft.client.sound.Source;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.client.sound.AbstractSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.TickableSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;

/**
 * Loops the Obsidian menu track while you are in the menus (no world loaded)
 * and the Obsidian menu is turned on. Fades in and out instead of cutting.
 */
public final class MenuMusic {
	public static final Identifier TRACK = Identifier.of(ObsidianClient.MOD_ID, "menu_music");
	/** Volume change per tick while fading; 0.05 is a one second fade. */
	private static final float FADE_STEP = 0.05f;

	private static Track current;
	/** Paused from the menu: the song stops where it is and resumes from the same spot. */
	private static boolean paused;

	private MenuMusic() {
	}

	/** True while our track owns the menu, so vanilla menu music stays quiet. */
	public static boolean isActive() {
		return current != null;
	}

	public static boolean isPaused() {
		return paused;
	}

	public static void togglePause() {
		paused = !paused;
		if (current == null) return;
		MinecraftClient client = MinecraftClient.getInstance();
		SoundSystem system = ((SoundManagerAccessor) client.getSoundManager()).obsidian$getSoundSystem();
		Channel.SourceManager source = ((SoundSystemAccessor) system).obsidian$getSources().get(current);
		if (source != null) source.run(paused ? Source::pause : Source::resume);
	}

	public static void tick(MinecraftClient client) {
		boolean wanted = client.world == null
			&& (client.getOverlay() == null || LaunchScreen.isFading())
			&& client.currentScreen != null
			&& ObsidianClient.CONFIG != null
			&& !ObsidianClient.CONFIG.vanillaMenu;

		if (wanted && current == null && !paused) {
			client.getMusicTracker().stop();
			current = new Track();
			client.getSoundManager().play(current);
		} else if (current != null) {
			current.fadingOut = !wanted;
			if (current.isDone() || !wanted && current.fade <= 0f) {
				client.getSoundManager().stop(current);
				current = null;
			}
		}
	}

	private static final class Track extends AbstractSoundInstance implements TickableSoundInstance {
		private float fade = 0f;
		private boolean fadingOut = false;

		private Track() {
			super(TRACK, SoundCategory.MASTER, SoundInstance.createRandom());
			this.repeat = true;
			this.repeatDelay = 0;
			this.relative = true;
			this.attenuationType = SoundInstance.AttenuationType.NONE;
			this.volume = 0.0001f;
		}

		@Override
		public boolean isDone() {
			return false;
		}

		@Override
		public boolean shouldAlwaysPlay() {
			return true;
		}

		@Override
		public void tick() {
			fade = fadingOut ? Math.max(0f, fade - FADE_STEP) : Math.min(1f, fade + FADE_STEP);
			// The sound system drops sources whose volume is exactly 0, so keep a sliver.
			this.volume = Math.max(0.0001f, fade * ObsidianClient.CONFIG.effectiveVolume());
		}
	}
}
