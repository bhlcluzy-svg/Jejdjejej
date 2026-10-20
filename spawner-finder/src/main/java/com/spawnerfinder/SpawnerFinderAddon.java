package com.spawnerfinder;

import com.mojang.logging.LogUtils;
import com.spawnerfinder.modules.ActivityFinder;
import com.spawnerfinder.modules.BaseFinder;
import com.spawnerfinder.modules.PacketLogger;
import com.spawnerfinder.modules.SpawnerFinder;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class SpawnerFinderAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();

    // The module goes into Max Addon's category when that addon is installed,
    // so it shows up under its other modules. Otherwise it goes into World.
    private static final String PREFERRED_CATEGORY = "Max Addon";

    @Override
    public void onInitialize() {
        Category category = findCategory(PREFERRED_CATEGORY);
        LOG.info("[Spawner Finder] Adding module to category {}", category.name);
        Modules.get().add(new SpawnerFinder(category));
        Modules.get().add(new PacketLogger(category));
        Modules.get().add(new ActivityFinder(category));
        Modules.get().add(new BaseFinder(category));
    }

    private static Category findCategory(String name) {
        for (Category category : Modules.loopCategories()) {
            if (category.name.equals(name)) return category;
        }
        return Categories.World;
    }

    @Override
    public String getPackage() {
        return "com.spawnerfinder";
    }
}
