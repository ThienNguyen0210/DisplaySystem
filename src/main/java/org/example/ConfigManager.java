package org.example;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class ConfigManager {
    private final Main plugin;
    private FileConfiguration contentConfig;
    private FileConfiguration hologramConfig; // Thêm dòng này
    private final Map<String, YamlConfiguration> aiConfigs = new HashMap<>();

    public ConfigManager(Main plugin) {
        this.plugin = plugin;
        reloadConfigs();
    }

    public void reloadConfigs() {
        plugin.reloadConfig();

        // Load content.yml
        File contentFile = new File(plugin.getDataFolder(), "content.yml");
        if (!contentFile.exists()) plugin.saveResource("content.yml", false);
        contentConfig = YamlConfiguration.loadConfiguration(contentFile);

        // --- THÊM: Load hologram.yml ---
        File holoFile = new File(plugin.getDataFolder(), "hologram.yml");
        if (!holoFile.exists()) plugin.saveResource("hologram.yml", false);
        hologramConfig = YamlConfiguration.loadConfiguration(holoFile);
        // ------------------------------

        // Load folder AI
        aiConfigs.clear();
        File aiFolder = new File(plugin.getDataFolder(), "AI");
        for (String fileName : plugin.getConfig().getStringList("AI-Config")) {
            File f = new File(aiFolder, fileName);
            if (f.exists()) {
                String name = fileName.replace(".yml", "");
                aiConfigs.put(name, YamlConfiguration.loadConfiguration(f));
            }
        }
    }
    public void saveHologram() {
        try {
            File holoFile = new File(plugin.getDataFolder(), "hologram.yml");
            hologramConfig.save(holoFile);
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
    }
    public FileConfiguration getContent() { return contentConfig; }

    // Thêm Getter cho Hologram
    public FileConfiguration getHologram() { return hologramConfig; }

    public YamlConfiguration getAIConfig(String name) { return aiConfigs.get(name); }
}