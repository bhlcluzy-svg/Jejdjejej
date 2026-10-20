package dev.obsidian.gui;

import dev.obsidian.ObsidianClient;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.gui.screen.Screen;

import java.util.Comparator;
import java.util.List;

/** Opens Mod Menu's screen when it is installed, otherwise our own simple list. */
public final class ModsScreens {
	private static List<ModContainer> mods;

	private ModsScreens() {
	}

	/** Mods you installed: no built-ins (Minecraft, Java) and no libraries bundled inside other mods. */
	public static List<ModContainer> userMods() {
		if (mods == null) {
			mods = FabricLoader.getInstance().getAllMods().stream()
				.filter(m -> m.getContainingMod().isEmpty())
				.filter(m -> !"builtin".equals(m.getMetadata().getType()))
				.sorted(Comparator.comparing(m -> m.getMetadata().getName().toLowerCase()))
				.toList();
		}
		return mods;
	}

	public static int count() {
		return userMods().size();
	}

	public static Screen create(Screen parent) {
		if (FabricLoader.getInstance().isModLoaded("modmenu")) {
			try {
				return (Screen) Class.forName("com.terraformersmc.modmenu.gui.ModsScreen")
					.getConstructor(Screen.class)
					.newInstance(parent);
			} catch (ReflectiveOperationException | ClassCastException e) {
				ObsidianClient.LOGGER.warn("Could not open Mod Menu, using the built-in list", e);
			}
		}
		return new ObsidianModsScreen(parent);
	}
}
