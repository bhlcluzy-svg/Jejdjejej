package dev.obsidian.mixin;

import dev.obsidian.ObsidianClient;
import dev.obsidian.gui.SceneBackground;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenMixin {
	@Shadow
	@Final
	protected MinecraftClient client;
	@Shadow
	public int width;
	@Shadow
	public int height;

	/**
	 * Menus outside a world (Singleplayer, Multiplayer, Options...) normally draw
	 * the rotating panorama; draw the Obsidian scene instead. Vanilla still blurs
	 * and darkens it on top, so the lists stay readable.
	 */
	@Inject(method = "renderPanoramaBackground", at = @At("HEAD"), cancellable = true)
	private void obsidian$sceneBackground(DrawContext context, float deltaTicks, CallbackInfo ci) {
		if (client == null || client.world != null || ObsidianClient.CONFIG == null || ObsidianClient.CONFIG.vanillaMenu) return;
		int mx = (int) client.mouse.getScaledX(client.getWindow());
		int my = (int) client.mouse.getScaledY(client.getWindow());
		SceneBackground.INSTANCE.render(context, width, height, mx, my);
		ci.cancel();
	}
}
