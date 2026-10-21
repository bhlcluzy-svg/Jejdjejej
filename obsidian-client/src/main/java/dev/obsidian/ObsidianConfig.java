package dev.obsidian;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Settings saved to config/obsidian.json. */
public class ObsidianConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("obsidian.json");

	/** Menu music volume, 0 to 1. */
	public float musicVolume = 0.6f;
	public boolean muted = false;
	/** Show the vanilla title screen instead of the Obsidian one. */
	public boolean vanillaMenu = false;
	/** Play the intro animation the first time the menu opens each launch. */
	public boolean playIntro = true;

	/** Panel id -> {x, y} as fractions of the screen size, so layouts survive window resizes. */
	public Map<String, float[]> panels = new HashMap<>();
	/** Bumped when the menu layout changes, so old saved positions are dropped. */
	public int layoutVersion = LAYOUT_VERSION;

	private static final int LAYOUT_VERSION = 5;

	public static ObsidianConfig load() {
		if (Files.exists(FILE)) {
			try (Reader reader = Files.newBufferedReader(FILE)) {
				ObsidianConfig config = GSON.fromJson(reader, ObsidianConfig.class);
				if (config != null) {
					if (config.panels == null || config.layoutVersion != LAYOUT_VERSION) {
						config.panels = new HashMap<>();
						config.layoutVersion = LAYOUT_VERSION;
					}
					return config;
				}
			} catch (Exception e) {
				ObsidianClient.LOGGER.warn("Could not read {}, using defaults", FILE, e);
			}
		}
		return new ObsidianConfig();
	}

	public void save() {
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer writer = Files.newBufferedWriter(FILE)) {
				GSON.toJson(this, writer);
			}
		} catch (Exception e) {
			ObsidianClient.LOGGER.warn("Could not save {}", FILE, e);
		}
	}

	public float effectiveVolume() {
		return muted ? 0f : musicVolume;
	}
}
