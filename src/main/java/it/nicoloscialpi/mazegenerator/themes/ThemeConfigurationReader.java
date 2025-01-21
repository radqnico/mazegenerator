package it.nicoloscialpi.mazegenerator.themes;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Set;

public class ThemeConfigurationReader {

    private YamlConfiguration yamlConfiguration;
    private final JavaPlugin plugin;

    public ThemeConfigurationReader(JavaPlugin plugin, String configName) {
        this.plugin = plugin;
        File file = new File(plugin.getDataFolder(), configName);
        if (!file.exists()) {
            boolean mkdirs = file.getParentFile().mkdirs();
            plugin.saveResource(configName, false);
        }
        yamlConfiguration = new YamlConfiguration();
        try {
            yamlConfiguration.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().severe(e.getMessage());
        }
    }

    public HashMap<String, Theme> parseThemes() {
        HashMap<String, Theme> result = new HashMap<>();

        Set<String> keysL0 = yamlConfiguration.getKeys(false);
        for (String keyL0 : keysL0) {
            ConfigurationSection yamlL0 = yamlConfiguration.getConfigurationSection(keyL0);
            if (yamlL0 == null) {
                plugin.getLogger().warning("Configuration section '" + keyL0 + "' is null");
                continue;
            }
            Theme theme = new Theme();
            result.put(keyL0, theme);
            Set<String> keysL1 = yamlL0.getKeys(false);
            for (String keyL1 : keysL1) {
                if (!(keyL1.equals("floor") || keyL1.equals("wall") || keyL1.equals("top"))) {
                    plugin.getLogger().warning("Configuration section '" + keyL1 + "' is not one of [floor,wall,top], skipping");
                    continue;
                }

                ConfigurationSection yamlL1 = yamlL0.getConfigurationSection(keyL1);
                if (yamlL1 == null) {
                    plugin.getLogger().warning("Configuration section '" + keyL1 + "' is null, skipping");
                    continue;
                }

                Set<String> keysL2 = yamlL1.getKeys(false);
                for (String keyL2 : keysL2) {
                    int materialWeight = yamlL1.getInt(keyL2, -1);
                    Material matchMaterial = Material.matchMaterial(keyL2);
                    if (matchMaterial == null) {
                        plugin.getLogger().warning("Material '" + keyL2 + "' does not exists, skipping");
                        continue;
                    }
                    if (materialWeight == -1) {
                        plugin.getLogger().warning("Material '\" + keyL2 + \"' is ok but weight'" + materialWeight + "' is NOT valid, skipping");
                        continue;
                    }
                    theme.insertBySectionName(keyL1.toLowerCase(), matchMaterial, materialWeight);
                }
            }
        }

        return result;
    }
}
