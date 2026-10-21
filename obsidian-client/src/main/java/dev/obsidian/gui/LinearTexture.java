package dev.obsidian.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;

/** A texture made in code that is smoothed (linear filtering) when scaled, instead of pixelated. */
final class LinearTexture extends NativeImageBackedTexture {
	LinearTexture(String name, NativeImage image) {
		super(() -> name, image);
		this.sampler = RenderSystem.getSamplerCache().get(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.LINEAR, false);
	}
}
