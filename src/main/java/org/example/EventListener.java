package org.example;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class EventListener implements Listener {
    private final Main plugin;
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();

    
    private final Map<UUID, AISession> aiSessions = new HashMap<>();
    
    private final Map<UUID, String> pendingActions = new HashMap<>();
    private final AIManager aiManager = new AIManager();

    public EventListener(Main plugin) { this.plugin = plugin; }

    private static class AISession {
        String aiName;
        UUID displayUuid;
        public AISession(String aiName, UUID displayUuid) {
            this.aiName = aiName;
            this.displayUuid = displayUuid;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        String msg = e.getMessage();

        if (aiSessions.containsKey(p.getUniqueId())) {
            e.setCancelled(true);

            if (msg.equalsIgnoreCase("cancel")) {
                aiSessions.remove(p.getUniqueId());
                pendingActions.remove(p.getUniqueId());
                p.sendMessage("§c[AI] Đã thoát chế độ trò chuyện.");
                return;
            }

            handleAIChat(p, msg);
            return;
        }

        if (msg.startsWith("-")) {
            String key = msg.substring(1).trim();
            if (plugin.getConfigs().getContent().contains(key)) {
                ConfigurationSection section = plugin.getConfigs().getContent().getConfigurationSection(key);
                if (section == null || !canSpawn(p, key, section)) return;
                e.setCancelled(true);
                setCooldown(p, key);
                Bukkit.getScheduler().runTask(plugin, () -> plugin.getDisplayManager().spawn(p, key));
            }
        }
    }

    private void handleAIChat(Player p, String msg) {
        AISession session = aiSessions.get(p.getUniqueId());
        YamlConfiguration aiConfig = plugin.getConfigs().getAIConfig(session.aiName);

        if (aiConfig == null) {
            aiSessions.remove(p.getUniqueId());
            pendingActions.remove(p.getUniqueId());
            return;
        }

        
        Bukkit.getScheduler().runTask(plugin, () -> {

            
            if (pendingActions.containsKey(p.getUniqueId())) {
                String lowMsg = msg.toLowerCase();
                List<String> yesWords = plugin.getConfig().getStringList("AI-Settings.Keywords.Yes");
                if (yesWords.isEmpty()) yesWords = Arrays.asList("có", "yes", "đồng ý", "ok");
                List<String> noWords = plugin.getConfig().getStringList("AI-Settings.Keywords.No");
                if (noWords.isEmpty()) noWords = Arrays.asList("không", "no", "hủy");

                boolean isYes = yesWords.stream().anyMatch(lowMsg::contains);
                boolean isNo = noWords.stream().anyMatch(lowMsg::contains);

                if (isYes) {
                    String cmdWait = pendingActions.remove(p.getUniqueId());
                    org.bukkit.entity.Entity displayEnt = Bukkit.getEntity(session.displayUuid);
                    Interaction inter = null;
                    if (displayEnt != null) {
                        inter = displayEnt.getNearbyEntities(3.0, 3.0, 3.0).stream()
                                .filter(e -> e instanceof Interaction)
                                .map(e -> (Interaction) e)
                                .filter(i -> i.hasMetadata("BTD_DISPLAY"))
                                .findFirst().orElse(null);
                    }
                    execute(p, inter, cmdWait);
                    plugin.getDisplayManager().updateAIText(p, session.displayUuid, "§a✔ §fĐã thực hiện yêu cầu!");
                    return; 
                } else if (isNo) {
                    pendingActions.remove(p.getUniqueId());
                    plugin.getDisplayManager().updateAIText(p, session.displayUuid, "§c✘ §fĐã hủy yêu cầu.");
                    return;
                }
            }

            
            String response = aiManager.getResponse(aiConfig, msg);

            if (response != null) {
                String rawResponse = response.trim();
                String textToDisplay = rawResponse;
                String cmdToWait = null;

                
                if (rawResponse.contains("[WAIT:")) {
                    int start = rawResponse.indexOf("[WAIT:");
                    int end = rawResponse.indexOf("]", start);
                    if (end != -1) {
                        cmdToWait = rawResponse.substring(start + 6, end).trim();
                        textToDisplay = rawResponse.replace(rawResponse.substring(start, end + 1), "").trim();

                        
                        pendingActions.put(p.getUniqueId(), cmdToWait);

                        
                        plugin.getDisplayManager().updateAIText(p, session.displayUuid,
                                textToDisplay.replace("&", "§") + "\n§e§l↳ Gõ 'có' hoặc 'yes' để xác nhận!");
                    }
                } else {
                    
                    textToDisplay = rawResponse;
                }

                
                plugin.getDisplayManager().updateAIText(p, session.displayUuid, textToDisplay.replace("&", "§"));

                
                

            } else {
                p.sendMessage("§b[" + session.aiName + "] §fTôi không hiểu ý bạn...");
            }
        });
    }

    public void execute(Player p, Interaction inter, String cmd) {
        if (cmd == null || cmd.isEmpty()) return;

        String finalCmd = cmd.replace("%player_name%", p.getName()).trim();
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            finalCmd = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(p, finalCmd);
        }
        String lowerCmd = finalCmd.toLowerCase();

        if (lowerCmd.startsWith("[message]")) {
            p.sendMessage(finalCmd.substring(9).trim().replace("&", "§"));
            return;
        }

        if (lowerCmd.startsWith("[ai]")) {
            String aiName = finalCmd.substring(4).trim();
            if (inter != null && inter.hasMetadata("BTD_DISPLAY") && !inter.getMetadata("BTD_DISPLAY").isEmpty()) {
                UUID tdUuid = UUID.fromString(inter.getMetadata("BTD_DISPLAY").get(0).asString());
                aiSessions.put(p.getUniqueId(), new AISession(aiName, tdUuid));
                p.sendMessage("§b[AI] §fĐã kết nối với §e" + aiName + "§f. Hãy nhập nội dung chat!");
            }
            return;
        }

        if (lowerCmd.startsWith("[money]")) {
            try {
                double amt = Double.parseDouble(finalCmd.substring(7).trim());
                if (amt > 0) Main.getEconomy().depositPlayer(p, amt);
                else Main.getEconomy().withdrawPlayer(p, Math.abs(amt));
            } catch (Exception e) {}
            return;
        }

        if (lowerCmd.startsWith("[console]")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd.substring(9).trim());
            return;
        }

        if (lowerCmd.startsWith("[op]")) {
            boolean op = p.isOp();
            try {
                p.setOp(true);
                p.performCommand(finalCmd.substring(4).trim());
            } finally { if (!op) p.setOp(false); }
            return;
        }

        if (lowerCmd.startsWith("[call]")) {
            trySpawn(p, finalCmd.substring(6).trim());
            return;
        }

        
        if (inter != null) {
            if (lowerCmd.startsWith("[update-all]")) { handleUpdateAction(p, inter, finalCmd.substring(12).trim(), true); }
            else if (lowerCmd.startsWith("[update-bg]")) { handleUpdateAction(p, inter, finalCmd.substring(11).trim(), true); }
            else if (lowerCmd.startsWith("[update]")) { handleUpdateAction(p, inter, finalCmd.substring(8).trim(), false); }
            else if (lowerCmd.equalsIgnoreCase("[close]")) { closeDisplay(inter); }
            else if (lowerCmd.equalsIgnoreCase("[refresh]")) { refreshDisplay(inter); }
        }
    }

    private void closeDisplay(Interaction inter) {
        if (!inter.hasMetadata("BTD_DISPLAY") || inter.getMetadata("BTD_DISPLAY").isEmpty()) {
            inter.remove();
            return;
        }
        UUID tdUuid = UUID.fromString(inter.getMetadata("BTD_DISPLAY").get(0).asString());
        removeAISessionByDisplay(tdUuid);
        org.bukkit.entity.Entity d = Bukkit.getEntity(tdUuid);
        if (d instanceof TextDisplay td) {
            plugin.getDisplayManager().removeWithInterpolation(td, inter);
        } else {
            inter.remove();
        }
    }

    public void removeAISessionByDisplay(UUID displayUuid) {
        aiSessions.entrySet().removeIf(entry -> {
            if (entry.getValue().displayUuid.equals(displayUuid)) {
                pendingActions.remove(entry.getKey());
                return true;
            }
            return false;
        });
    }

    public void removeAISession(UUID playerUuid) {
        aiSessions.remove(playerUuid);
        pendingActions.remove(playerUuid);
    }

    private boolean canUpdate(Player p, ConfigurationSection section) {
        String perm = section.getString("permission");
        if (perm != null && !perm.isEmpty()) {
            if (perm.startsWith("!")) { if (p.hasPermission(perm.substring(1))) return false; }
            else { if (!p.hasPermission(perm)) return false; }
        }
        if (section.contains("conditions")) return checkConditions(p, section.getConfigurationSection("conditions"));
        return true;
    }

    private boolean canSpawn(Player p, String key, ConfigurationSection section) {
        if (!canUpdate(p, section)) return false;
        return !checkCooldown(p, key);
    }

    private void trySpawn(Player p, String key) {
        ConfigurationSection section = plugin.getConfigs().getContent().getConfigurationSection(key);
        if (section == null || !canSpawn(p, key, section)) return;
        setCooldown(p, key);
        Bukkit.getScheduler().runTask(plugin, () -> plugin.getDisplayManager().spawn(p, key));
    }

    private boolean checkConditions(Player p, ConfigurationSection cond) {
        if (cond == null) return true;
        String rawIn1 = cond.getString("input1", ""), rawIn2 = cond.getString("input2", "");
        String in1, in2;
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            in1 = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(p, rawIn1);
            in2 = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(p, rawIn2);
        } else {
            in1 = rawIn1.replace("%player_name%", p.getName());
            in2 = rawIn2.replace("%player_name%", p.getName());
        }
        String math = cond.contains("Math") ? cond.getString("Math") : cond.getString("math", "==");
        if (cond.getString("type", "Char").equalsIgnoreCase("Number")) {
            try {
                double n1 = Double.parseDouble(in1), n2 = Double.parseDouble(in2);
                return switch (math) {
                    case ">" -> n1 > n2; case "<" -> n1 < n2; case ">=" -> n1 >= n2;
                    case "<=" -> n1 <= n2; case "==" -> Math.abs(n1 - n2) < 0.01;
                    case "!=" -> Math.abs(n1 - n2) >= 0.01; default -> false;
                };
            } catch (Exception ex) { return false; }
        } else {
            return switch (math) {
                case "==" -> in1.equals(in2); case "!=" -> !in1.equals(in2);
                case "contains" -> in1.contains(in2); default -> false;
            };
        }
    }

    private void handleUpdateAction(Player p, Interaction inter, String newKey, boolean updateBG) {
        ConfigurationSection section = plugin.getConfigs().getContent().getConfigurationSection(newKey);
        if (section == null || !canUpdate(p, section)) return;
        if (inter == null || !inter.hasMetadata("BTD_DISPLAY") || inter.getMetadata("BTD_DISPLAY").isEmpty()) return;
        UUID tdUuid = UUID.fromString(inter.getMetadata("BTD_DISPLAY").get(0).asString());
        inter.setMetadata("BTD_KEY", new FixedMetadataValue(plugin, newKey));
        plugin.getDisplayManager().updateDisplay(p, tdUuid, newKey, updateBG);
    }

    @EventHandler public void onRC(PlayerInteractEntityEvent e) { handleInteraction(e.getPlayer(), e.getRightClicked(), e.getPlayer().isSneaking() ? "shift_right_click" : "right_click"); }
    @EventHandler public void onLC(EntityDamageByEntityEvent e) { if (e.getDamager() instanceof Player p && handleInteraction(p, e.getEntity(), p.isSneaking() ? "shift_left_click" : "left_click")) e.setCancelled(true); }

    private boolean handleInteraction(Player p, org.bukkit.entity.Entity target, String type) {
        if (!(target instanceof Interaction inter) || !inter.hasMetadata("BTD_KEY") || inter.getMetadata("BTD_KEY").isEmpty()) return false;
        if (plugin.getDisplayManager().isClosing(inter.getUniqueId())) return true;
        String key = inter.getMetadata("BTD_KEY").get(0).asString();
        ConfigurationSection sec = plugin.getConfigs().getContent().getConfigurationSection(key + ".interact." + type);
        if (sec != null) processCommandChain(p, inter, sec.getStringList("commands"), 0);
        return true;
    }

    private void processCommandChain(Player p, Interaction inter, List<String> commands, int index) {
        if (index >= commands.size() || plugin.getDisplayManager().isClosing(inter.getUniqueId())) return;
        String cmd = commands.get(index), lower = cmd.toLowerCase();
        if (lower.startsWith("[delay]")) {
            try {
                int t = Integer.parseInt(cmd.substring(7).trim());
                new BukkitRunnable() { @Override public void run() { processCommandChain(p, inter, commands, index + 1); } }.runTaskLater(plugin, t);
                return;
            } catch (Exception e) {}
        } else if (lower.equalsIgnoreCase("[close]")) {
            closeDisplay(inter);
            return;
        } else if (lower.equalsIgnoreCase("[refresh]")) {
            refreshDisplay(inter);
        } else {
            execute(p, inter, cmd);
        }
        processCommandChain(p, inter, commands, index + 1);
    }

    private void refreshDisplay(Interaction inter) {
        if (inter == null || !inter.hasMetadata("BTD_DISPLAY") || inter.getMetadata("BTD_DISPLAY").isEmpty()) return;
        plugin.getDisplayManager().refreshDuration(UUID.fromString(inter.getMetadata("BTD_DISPLAY").get(0).asString()));
    }
    
    @EventHandler
    public void onRightClick(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof Interaction inter)) return;

        
        if (inter.hasMetadata("BTD_HOLO_KEY")) {
            handleHoloClick(e.getPlayer(), inter, "right-click");
        }
    }

    
    @EventHandler
    public void onLeftClick(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (!(e.getEntity() instanceof Interaction inter)) return;

        if (inter.hasMetadata("BTD_HOLO_KEY")) {
            e.setCancelled(true); 
            handleHoloClick(p, inter, "left-click");
        }
    }

    private void handleHoloClick(Player p, Interaction inter, String type) {
        if (!inter.hasMetadata("BTD_HOLO_KEY")) return;

        
        String currentKey = inter.getMetadata("BTD_HOLO_KEY").get(0).asString();
        
        String parentKey = inter.getMetadata("BTD_HOLO_PARENT").get(0).asString();

        ConfigurationSection sec = plugin.getConfigs().getHologram().getConfigurationSection("Holograms." + currentKey + ".interact");
        if (sec == null || !sec.contains(type)) return;

        List<String> commands = sec.getStringList(type);
        for (String cmd : commands) {
            String finalCmd = cmd.replace("%player_name%", p.getName());

            if (finalCmd.startsWith("[update]")) {
                String[] parts = finalCmd.substring(8).trim().split(" ");
                String newContentKey = parts[0];
                int duration = (parts.length > 1) ? Integer.parseInt(parts[1]) : 0;

                
                plugin.getHologramManager().updateHologram(parentKey, newContentKey, duration);
            } else {
                execute(p, null, finalCmd);
            }
        }
    }

    private void runHoloCommands(Player p, String currentHoloKey, List<String> commands) {
        for (String cmd : commands) {
            String finalCmd = cmd.replace("%player_name%", p.getName());
            String lower = finalCmd.toLowerCase();

            if (lower.startsWith("[update]")) {
                String[] parts = finalCmd.substring(8).trim().split(" ");
                String newContentKey = parts[0];
                int duration = (parts.length > 1) ? Integer.parseInt(parts[1]) : 0;

                
                
                plugin.getHologramManager().updateHologram(currentHoloKey, newContentKey, duration);
                continue;
            }
            execute(p, null, finalCmd);
        }
    }
    private boolean checkCooldown(Player p, String k) { return cooldowns.getOrDefault(p.getUniqueId(), Collections.emptyMap()).getOrDefault(k, 0L) > System.currentTimeMillis(); }
    private void setCooldown(Player p, String k) { int t = plugin.getConfigs().getContent().getInt(k + ".cooldown", 0); if (t > 0) forceCooldown(p, k, t); }
    private void forceCooldown(Player p, String key, int seconds) { long expireTime = System.currentTimeMillis() + (seconds * 1000L); cooldowns.computeIfAbsent(p.getUniqueId(), u -> new HashMap<>()).put(key, expireTime); }
}