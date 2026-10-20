package dev.obsidian.mixin;

import dev.obsidian.sound.MenuMusic;
import net.minecraft.client.sound.MusicTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MusicTracker.class)
public abstract class MusicTrackerMixin {
	/** Keep vanilla menu music from playing over ours. */
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void obsidian$silenceWhileOurs(CallbackInfo ci) {
		if (MenuMusic.isActive()) ci.cancel();
	}
}
