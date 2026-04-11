package org.example;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.World; // Thêm import này
import org.joml.Vector3f;

import java.util.*;

public class HologramManager {
    private final Main plugin;

    private final Map<String, UUID> activeHolograms = new HashMap<>();
    private final Map<String, UUID> activeInteractions = new HashMap<>();
    private final Map<String, Integer> resetTasks = new HashMap<>();
    private final Map<String, String> currentContent = new HashMap<>();

    public HologramManager(Main plugin) {
        this.plugin = plugin;
        startUpdateTask();
    }

    private void startUpdateTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<String, UUID> entry : activeHolograms.entrySet()) {
                    String key = entry.getKey();
                    Entity entity = Bukkit.getEntity(entry.getValue());

                    if (entity instanceof TextDisplay td && td.isValid()) {
                        ConfigurationSection lbSec = plugin.getConfigs().getHologram().getConfigurationSection("LeaderBoards." + key);

                        if (lbSec != null) {
                            td.setText(String.join("\n", buildLeaderboardLines(lbSec)));
                            continue;
                        }

                        String currentKey = currentContent.getOrDefault(key, key);
                        ConfigurationSection holoSec = plugin.getConfigs().getHologram().getConfigurationSection("Holograms." + currentKey);
                        if (holoSec != null) {
                            Player closest = td.getWorld().getPlayers().stream()
                                    .filter(p -> p.getLocation().distance(td.getLocation()) < 15)
                                    .min(Comparator.comparingDouble(p -> p.getLocation().distance(td.getLocation())))
                                    .orElse(null);

                            td.setText(formatText(closest, holoSec.getStringList("lines")));
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private String formatText(Player player, List<String> lines) {
        String raw = String.join("\n", lines);
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            raw = PlaceholderAPI.setPlaceholders(player, raw);
        }
        return raw.replace("&", "§");
    }

    public void loadAllHolograms() {
        removeAll();
        ConfigurationSection holoSection = plugin.getConfigs().getHologram().getConfigurationSection("Holograms");
        if (holoSection != null) {
            for (String key : holoSection.getKeys(false)) {
                if (holoSection.getBoolean(key + ".is-template", false)) continue;
                spawnHologram(key, "Holograms." + key);
            }
        }
        ConfigurationSection lbSection = plugin.getConfigs().getHologram().getConfigurationSection("LeaderBoards");
        if (lbSection != null) {
            for (String key : lbSection.getKeys(false)) {
                if (lbSection.getBoolean(key + ".is-template", false)) continue;
                spawnHologram(key, "LeaderBoards." + key);
            }
        }
    }

    /**
     * Spawn hologram bình thường từ config (dùng cho world chính)
     */
    public void spawnHologram(String key, String fullPath) {
        ConfigurationSection sec = plugin.getConfigs().getHologram().getConfigurationSection(fullPath);
        if (sec == null) {
            plugin.getLogger().warning("§c[BTD] Không tìm thấy section: " + fullPath);
            return;
        }

        String locStr = sec.getString("location");
        if (locStr == null || locStr.isEmpty()) {
            plugin.getLogger().warning("§c[BTD] Thiếu location cho hologram/leaderboard: " + key);
            return;
        }

        Location baseLoc = parseLocation(locStr);
        if (baseLoc == null || baseLoc.getWorld() == null) {
            String worldName = locStr.contains(",") ? locStr.split(",")[0].trim() : "unknown";
            plugin.getLogger().warning("§c[BTD] World '" + worldName + "' không tồn tại hoặc location sai cho: " + key + " (" + locStr + "). Bỏ qua!");
            return;
        }

        // Spawn với location gốc từ config
        spawnHologramEntity(key, fullPath, sec, baseLoc);
    }

    /**
     * Spawn hologram vào một world cụ thể (dùng cho instance dungeon)
     * @param key Tên hologram (ví dụ: "boss_timer")
     * @param targetWorld World instance cần spawn vào
     */
    public void spawnHologramInWorld(String key, World targetWorld) {
        if (targetWorld == null) {
            plugin.getLogger().warning("§c[BTD] Không thể spawn hologram '" + key + "' vào world null!");
            return;
        }

        String fullPath = "Holograms." + key; // Giả sử chỉ dùng Holograms, bạn có thể mở rộng cho LeaderBoards nếu cần
        ConfigurationSection sec = plugin.getConfigs().getHologram().getConfigurationSection(fullPath);
        if (sec == null) {
            plugin.getLogger().warning("§c[BTD] Không tìm thấy hologram config cho: " + key + " để spawn vào instance.");
            return;
        }

        String locStr = sec.getString("location");
        if (locStr == null || locStr.isEmpty()) {
            plugin.getLogger().warning("§c[BTD] Thiếu location cho hologram instance: " + key);
            return;
        }

        Location baseLoc = parseLocation(locStr);
        if (baseLoc == null) {
            plugin.getLogger().warning("§c[BTD] Location parse lỗi cho hologram instance: " + key);
            return;
        }

        // Đổi world thành instance, giữ nguyên tọa độ + yaw/pitch
        Location instanceLoc = new Location(targetWorld, baseLoc.getX(), baseLoc.getY(), baseLoc.getZ(), baseLoc.getYaw(), baseLoc.getPitch());

        spawnHologramEntity(key + "_instance_" + targetWorld.getName(), fullPath, sec, instanceLoc);
    }

    /**
     * Hàm chung spawn entity (TextDisplay + Interaction)
     */
    private void spawnHologramEntity(String storageKey, String fullPath, ConfigurationSection sec, Location loc) {
        float yaw = (float) sec.getDouble("yaw", loc.getYaw());
        float pitch = (float) sec.getDouble("pitch", loc.getPitch());
        loc.setYaw(yaw);
        loc.setPitch(pitch);

        TextDisplay td = loc.getWorld().spawn(loc, TextDisplay.class);

        if (fullPath.startsWith("LeaderBoards")) {
            td.setText(String.join("\n", buildLeaderboardLines(sec)));
        } else {
            td.setText(formatText(null, sec.getStringList("lines")));
        }

        applyStyles(td, sec);
        td.setRotation(yaw, pitch);
        td.addScoreboardTag("BTD_HOLO_" + storageKey);
        activeHolograms.put(storageKey, td.getUniqueId());

        // Spawn Interaction nếu có
        if (sec.contains("interact")) {
            float width = (float) sec.getDouble("interact.width", 1.0);
            float height = (float) sec.getDouble("interact.height", 1.0);
            float offsetY = (float) sec.getDouble("interact.offset-y", -0.5);
            float offsetForward = (float) sec.getDouble("interact.offset-forward", 0.1);

            org.bukkit.util.Vector direction = loc.getDirection();
            Location interLoc = loc.clone().add(0, offsetY, 0).add(direction.multiply(offsetForward));

            Interaction interaction = loc.getWorld().spawn(interLoc, Interaction.class);
            interaction.setInteractionWidth(width);
            interaction.setInteractionHeight(height);
            interaction.setResponsive(true);
            interaction.setRotation(yaw, pitch);

            interaction.setMetadata("BTD_HOLO_KEY", new FixedMetadataValue(plugin, storageKey));
            interaction.setMetadata("BTD_HOLO_PARENT", new FixedMetadataValue(plugin, storageKey));
            interaction.addScoreboardTag("BTD_INTERACT_" + storageKey);
            activeInteractions.put(storageKey, interaction.getUniqueId());
        }
    }

    // Các hàm còn lại giữ nguyên 100% (buildLeaderboardLines, updateHologram, applyStyles, removeAll, parseLocation, parseColor...)

    private List<String> buildLeaderboardLines(ConfigurationSection sec) {
        // ... (giữ nguyên code cũ của bạn)
        List<String> finalLines = new ArrayList<>();
        String header = sec.getString("header");
        if (header != null && !header.isEmpty()) finalLines.add(header.replace("&", "§"));

        String placeholder = sec.getString("placeholder", "%vault_eco_balance%");
        int maxRanks = sec.getInt("max-ranks", 10);
        String format = sec.getString("lines-format", "%rank% %player% %value%");
        String sortOrder = sec.getString("sort", "DESC");

        List<LeaderboardEntry> entries = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            String valStr = PlaceholderAPI.setPlaceholders(p, placeholder);
            try {
                double value = Double.parseDouble(valStr.replaceAll("[^0-9.]", ""));
                entries.add(new LeaderboardEntry(p.getName(), value));
            } catch (Exception ignored) {}
        }

        entries.sort((a, b) -> sortOrder.equalsIgnoreCase("DESC") ? Double.compare(b.getValue(), a.getValue()) : Double.compare(a.getValue(), b.getValue()));

        for (int i = 0; i < Math.min(entries.size(), maxRanks); i++) {
            LeaderboardEntry e = entries.get(i);
            Player p = Bukkit.getPlayer(e.getPlayerName());
            String line = format.replace("%rank%", String.valueOf(i + 1)).replace("%player%", e.getPlayerName()).replace("%value%", String.format("%,.0f", e.getValue()));
            if (p != null) line = PlaceholderAPI.setPlaceholders(p, line);
            finalLines.add(line.replace("&", "§"));
        }
        return finalLines;
    }

    public void updateHologram(String holoKey, String newContentKey, int durationSeconds) {
        // ... (giữ nguyên code cũ)
        UUID tdUuid = activeHolograms.get(holoKey);
        if (tdUuid == null) return;
        Entity tdEnt = Bukkit.getEntity(tdUuid);
        if (!(tdEnt instanceof TextDisplay td)) return;

        ConfigurationSection originalSec = plugin.getConfigs().getHologram().getConfigurationSection("Holograms." + holoKey);
        ConfigurationSection newSec = plugin.getConfigs().getHologram().getConfigurationSection("Holograms." + newContentKey);
        if (originalSec == null || newSec == null) return;

        td.setText(formatText(null, newSec.getStringList("lines")));
        applyStyles(td, newSec);

        currentContent.put(holoKey, newContentKey);
        UUID interUuid = activeInteractions.get(holoKey);
        if (interUuid != null) {
            Entity interEnt = Bukkit.getEntity(interUuid);
            if (interEnt instanceof Interaction inter) {
                inter.setMetadata("BTD_HOLO_KEY", new FixedMetadataValue(plugin, newContentKey));
            }
        }

        if (resetTasks.containsKey(holoKey)) Bukkit.getScheduler().cancelTask(resetTasks.get(holoKey));

        if (durationSeconds > 0) {
            int taskId = new BukkitRunnable() {
                @Override
                public void run() {
                    applyStyles(td, originalSec);
                    currentContent.remove(holoKey);
                    if (interUuid != null) {
                        Entity interEnt = Bukkit.getEntity(interUuid);
                        if (interEnt instanceof Interaction inter) {
                            inter.setMetadata("BTD_HOLO_KEY", new FixedMetadataValue(plugin, holoKey));
                        }
                    }
                    resetTasks.remove(holoKey);
                }
            }.runTaskLater(plugin, durationSeconds * 20L).getTaskId();
            resetTasks.put(holoKey, taskId);
        }
    }

    private void applyStyles(TextDisplay td, ConfigurationSection sec) {
        td.setBackgroundColor(parseColor(sec.getString("background", "#000000"), sec.getInt("opacity", 255)));
        float scale = (float) sec.getDouble("scale", 1.0);
        Transformation trans = td.getTransformation();
        trans.getScale().set(new Vector3f(scale, scale, scale));
        td.setTransformation(trans);

        boolean lookPlayer = sec.getBoolean("look-player", true);
        if (lookPlayer) {
            boolean upDown = sec.getBoolean("up-down", true);
            td.setBillboard(upDown ? TextDisplay.Billboard.CENTER : TextDisplay.Billboard.VERTICAL);
        } else {
            td.setBillboard(TextDisplay.Billboard.FIXED);
        }
    }

    public void removeAll() {
        resetTasks.values().forEach(Bukkit.getScheduler()::cancelTask);
        resetTasks.clear();
        currentContent.clear();
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getScoreboardTags().stream().anyMatch(tag -> tag.startsWith("BTD_HOLO_") || tag.startsWith("BTD_INTERACT_"))) {
                    entity.remove();
                }
            }
        }
        activeHolograms.clear();
        activeInteractions.clear();
    }

    private Location parseLocation(String str) {
        try {
            String[] p = str.split(",");
            if (p.length < 4) return null;
            World world = Bukkit.getWorld(p[0].trim());
            double x = Double.parseDouble(p[1].trim());
            double y = Double.parseDouble(p[2].trim());
            double z = Double.parseDouble(p[3].trim());
            return new Location(world, x, y, z);
        } catch (Exception e) {
            return null;
        }
    }

    private Color parseColor(String hex, int opacity) {
        try {
            java.awt.Color c = java.awt.Color.decode(hex);
            return Color.fromARGB(opacity, c.getRed(), c.getGreen(), c.getBlue());
        } catch (Exception e) {
            return Color.fromARGB(opacity, 0, 0, 0);
        }
    }

    public Map<String, UUID> getActiveHolograms() {
        return activeHolograms;
    }
}