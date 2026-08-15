package org.example;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

import java.util.*;

public class DisplayManager {
    private final Main plugin;
    private final Set<UUID> closingEntities = new HashSet<>();
    private final Map<UUID, Integer> removeTasks = new HashMap<>();
    private final Map<UUID, Interaction> linkedInteractions = new HashMap<>();

    public DisplayManager(Main plugin) { this.plugin = plugin; }

    public boolean isClosing(UUID uuid) { return closingEntities.contains(uuid); }

    public void spawn(Player player, String key) {
        ConfigurationSection section = plugin.getConfigs().getContent().getConfigurationSection(key);
        if (section == null) return;

        Location loc = player.getEyeLocation().add(player.getLocation().getDirection().multiply(2));
        float targetScale = (float) section.getDouble("scale", 2.0);

        TextDisplay td = player.getWorld().spawn(loc, TextDisplay.class);

        
        td.setText(setupTextContent(player, section));
        td.setSeeThrough(true);
        td.setBillboard(TextDisplay.Billboard.CENTER);
        td.setBackgroundColor(parseColor(section.getString("background-color", "#03fcf4"), section.getInt("opacity", 120)));

        
        boolean useAnimation = plugin.getConfig().getBoolean("settings.enable-animation", true);
        int animTicks = useAnimation ? plugin.getConfig().getInt("settings.animation-ticks", 5) : 0;
        String pivot = plugin.getConfig().getString("settings.animation-pivot", "MID").toUpperCase();

        if (useAnimation) {
            applyTransformationWithPivot(td, 0.01f, pivot);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (td.isDead()) return;
                td.setInterpolationDuration(animTicks);
                td.setInterpolationDelay(0);
                applyTransformationWithPivot(td, targetScale, pivot);
            }, 1L);
        } else {
            applyTransformationWithPivot(td, targetScale, pivot);
        }

        Interaction inter = player.getWorld().spawn(loc, Interaction.class);
        inter.setInteractionWidth(targetScale * 2);
        inter.setInteractionHeight(targetScale);
        inter.setMetadata("BTD_KEY", new FixedMetadataValue(plugin, key));
        inter.setMetadata("BTD_DISPLAY", new FixedMetadataValue(plugin, td.getUniqueId().toString()));

        linkedInteractions.put(td.getUniqueId(), inter);
        
        List<String> onSpawnCommands = section.getStringList("message-on-spawn");
        if (onSpawnCommands.isEmpty()) {
            onSpawnCommands = section.getStringList("mess-on-spawn"); 
        }

        if (!onSpawnCommands.isEmpty()) {
            for (String cmd : onSpawnCommands) {
                if (cmd == null || cmd.trim().isEmpty()) continue;

                String processedCmd = cmd
                        .replace("%player_name%", player.getName())
                        .replace("%player_health%", String.valueOf((int) player.getHealth()))
                        .replace("%player_max_health%", String.valueOf((int) player.getMaxHealth()));

                if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                    processedCmd = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, processedCmd);
                }

                String lowerCmd = processedCmd.toLowerCase().trim();

                
                if (lowerCmd.startsWith("[message]")) {
                    player.sendMessage(processedCmd.substring(9).trim().replace("&", "§"));
                }
                else if (lowerCmd.startsWith("[money]") ||
                        lowerCmd.startsWith("[console]") ||
                        lowerCmd.startsWith("[op]") ||
                        lowerCmd.startsWith("[call]") ||
                        lowerCmd.startsWith("[update") ||
                        lowerCmd.startsWith("[close]") ||
                        lowerCmd.startsWith("[refresh]")) {
                    
                    if (plugin.getEventListener() != null) {
                        plugin.getEventListener().execute(player, inter, processedCmd);
                    }
                }
                else {
                    
                    player.sendMessage(processedCmd.replace("&", "§"));
                }
            }
        }
        
        scheduleRemoval(td, section.getInt("duration", 10));
    }

    private String setupTextContent(Player p, ConfigurationSection sec) {
        List<String> lines = sec.getStringList("content");
        if (lines.isEmpty()) lines = sec.getStringList("text");

        StringBuilder sb = new StringBuilder();
        for (String l : lines) {
            String line = l.replace("&", "§");
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                line = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(p, line);
            }
            line = line.replace("%player_name%", p.getName())
                    .replace("%player_health%", String.valueOf((int)p.getHealth()))
                    .replace("%player_max_health%", String.valueOf((int)p.getMaxHealth()));
            sb.append(line).append("\n");
        }
        return sb.toString().trim();
    }

    public void updateDisplay(Player p, UUID tdUuid, String newKey, boolean updateBG) {
        org.bukkit.entity.Entity entity = Bukkit.getEntity(tdUuid);
        if (!(entity instanceof TextDisplay td) || td.isDead()) return;

        ConfigurationSection sec = plugin.getConfigs().getContent().getConfigurationSection(newKey);
        if (sec == null) return;

        td.setText(setupTextContent(p, sec));
        float targetScale = (float) sec.getDouble("scale", 2.0);
        String pivot = plugin.getConfig().getString("settings.animation-pivot", "MID").toUpperCase();
        applyTransformationWithPivot(td, targetScale, pivot);

        if (updateBG) {
            td.setBackgroundColor(parseColor(sec.getString("background-color", "#03fcf4"), sec.getInt("opacity", 120)));
        }

        Interaction inter = linkedInteractions.get(tdUuid);
        if (inter != null) {
            inter.setInteractionWidth(targetScale * 2);
            inter.setInteractionHeight(targetScale);
        }
        refreshDuration(tdUuid);
    }

    private void scheduleRemoval(TextDisplay td, int durationSeconds) {
        UUID uuid = td.getUniqueId();
        if (removeTasks.containsKey(uuid)) Bukkit.getScheduler().cancelTask(removeTasks.get(uuid));
        int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            removeWithInterpolation(td, linkedInteractions.get(uuid));
            removeTasks.remove(uuid);
        }, (long) durationSeconds * 20).getTaskId();
        removeTasks.put(uuid, taskId);
    }

    public void refreshDuration(UUID tdUuid) {
        org.bukkit.entity.Entity entity = Bukkit.getEntity(tdUuid);
        if (!(entity instanceof TextDisplay td) || td.isDead()) return;
        Interaction inter = linkedInteractions.get(tdUuid);
        if (inter == null || !inter.hasMetadata("BTD_KEY") || inter.getMetadata("BTD_KEY").isEmpty()) return;
        String key = inter.getMetadata("BTD_KEY").get(0).asString();
        int duration = plugin.getConfigs().getContent().getInt(key + ".duration", 10);
        scheduleRemoval(td, duration);
    }

    public void removeWithInterpolation(TextDisplay td, Interaction inter) {
        if (td == null) return;
        UUID tdUuid = td.getUniqueId();

        
        
        if (plugin.getEventListener() != null) {
            plugin.getEventListener().removeAISessionByDisplay(tdUuid);
        }

        if (inter != null) {
            if (closingEntities.contains(inter.getUniqueId())) return;
            closingEntities.add(inter.getUniqueId());
        }

        removeTasks.remove(tdUuid);
        linkedInteractions.remove(tdUuid);

        boolean useAnimation = plugin.getConfig().getBoolean("settings.enable-animation", true);
        int animTicks = useAnimation ? plugin.getConfig().getInt("settings.animation-ticks", 5) : 0;
        String pivot = plugin.getConfig().getString("settings.animation-pivot", "MID").toUpperCase();

        if (useAnimation && !td.isDead()) {
            td.setInterpolationDuration(animTicks);
            td.setInterpolationDelay(0);
            applyTransformationWithPivot(td, 0.01f, pivot);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                td.remove();
                if (inter != null) { inter.remove(); closingEntities.remove(inter.getUniqueId()); }
            }, animTicks);
        } else {
            td.remove();
            if (inter != null) { inter.remove(); closingEntities.remove(inter.getUniqueId()); }
        }
    }

    public void updateAIText(Player p, UUID tdUuid, String aiResponse) {
        org.bukkit.entity.Entity entity = Bukkit.getEntity(tdUuid);
        if (!(entity instanceof TextDisplay td) || td.isDead()) return;
        String text = aiResponse.replace("&", "§").replace("%player_name%", p.getName());
        td.setText(text);
        refreshDuration(tdUuid);
    }

    private void applyTransformationWithPivot(TextDisplay td, float scale, String pivot) {
        Transformation trans = td.getTransformation();
        trans.getScale().set(new Vector3f(scale, scale, scale));
        float yOffset = 0;
        if (pivot.equals("TOP")) yOffset = scale * 0.6f;
        else if (pivot.equals("BOT")) yOffset = -scale * 0.6f;
        trans.getTranslation().set(new Vector3f(0, yOffset, 0));
        td.setTransformation(trans);
    }

    private Color parseColor(String hex, int opacity) {
        try {
            java.awt.Color c = java.awt.Color.decode(hex);
            return Color.fromARGB(opacity, c.getRed(), c.getGreen(), c.getBlue());
        } catch (Exception e) {
            return Color.fromARGB(opacity, 3, 252, 244);
        }
    }
}