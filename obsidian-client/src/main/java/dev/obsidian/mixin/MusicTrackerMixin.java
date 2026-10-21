package dev.obsidian.mixin;

import dev.obsidian.ObsidianClient;
import dev.obsidian.sound.MenuMusic;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MusicTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MusicTracker.class)
public abstract class MusicTrackerMixin {
	/**
	 * Keep vanilla menu music out of the menus entirely while the Obsidian menu is
	 * on, including the moment the game first loads (before our track starts).
	 */
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void obsidian$silenceWhileOurs(CallbackInfo ci) {
		MinecraftClient client = MinecraftClient.getInstance();
		boolean obsidianMenus = client.world == null && ObsidianClient.CONFIG != null && !ObsidianClient.CONFIG.vanillaMenu;
		if (MenuMusic.isActive() || obsidianMenus) ci.cancel();
	}
}
