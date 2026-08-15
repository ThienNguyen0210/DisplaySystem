

package org.example;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BTDCommand implements CommandExecutor, TabCompleter {
    private final Main plugin;

    public BTDCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        
        if (!sender.hasPermission("btd.admin")) {
            sender.sendMessage("§cBạn không có quyền thực hiện lệnh này!");
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cLệnh này chỉ dành cho người chơi!");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "reload":
                plugin.getConfigs().reloadConfigs();
                plugin.getHologramManager().loadAllHolograms();
                sender.sendMessage("§a[BetterTextDisplay] Đã tải lại toàn bộ cấu hình thành công!");
                return true;

            case "create":
                if (args.length < 2) {
                    sender.sendMessage("§cSử dụng: /btd create <tên>");
                    return true;
                }
                handleCreate(player, args[1]);
                return true;

            case "movehere":
                if (args.length < 2) {
                    sender.sendMessage("§cSử dụng: /btd movehere <tên>");
                    return true;
                }
                handleMoveHere(player, args[1]);
                return true;

            case "remove":
            case "delete":
                if (args.length < 2) {
                    sender.sendMessage("§cSử dụng: /btd remove <tên>");
                    return true;
                }
                handleRemove(player, args[1]);
                return true;

            case "give":
                if (args.length >= 2 && args[1].equalsIgnoreCase("tools")) {
                    player.getInventory().addItem(Tools.getTool());
                    player.sendMessage("§b§l[BTD] §aĐã nhận cây gậy điều khiển Hologram!");
                    return true;
                }
                sender.sendMessage("§cSử dụng: /btd give tools");
                return true;

            default:
                sendHelp(sender);
                return true;
        }
    }

    private void handleCreate(Player p, String name) {
        Location l = p.getLocation();
        String locStr = l.getWorld().getName() + "," + l.getX() + "," + l.getY() + "," + l.getZ();

        ConfigurationSection sec = plugin.getConfigs().getHologram().createSection("Holograms." + name);
        sec.set("location", locStr);
        sec.set("background", "#000000");
        sec.set("look-player", true);
        sec.set("up-down", true);
        sec.set("lines", Arrays.asList("&eHologram: " + name, "&fSửa nội dung trong hologram.yml"));
        sec.set("scale", 1.0);
        sec.set("interact.commands", Arrays.asList("[message] &bBạn đã click vào hologram %player_name%"));
        plugin.getConfigs().saveHologram();
        plugin.getHologramManager().loadAllHolograms(); 
        p.sendMessage("§a[BTD] Đã tạo hologram §f" + name + " §atại vị trí của bạn.");
    }

    private void handleMoveHere(Player p, String name) {
        ConfigurationSection hologramConfig = plugin.getConfigs().getHologram();

        String sectionPath = null;
        if (hologramConfig.contains("Holograms." + name)) {
            sectionPath = "Holograms." + name;
        } else if (hologramConfig.contains("LeaderBoards." + name)) {
            sectionPath = "LeaderBoards." + name;
        }

        if (sectionPath == null) {
            p.sendMessage("§cKhông tìm thấy hologram hoặc leaderboard tên: §f" + name);
            return;
        }

        Location l = p.getLocation();
        String locStr = l.getWorld().getName() + "," + l.getX() + "," + l.getY() + "," + l.getZ();

        hologramConfig.set(sectionPath + ".location", locStr);
        plugin.getConfigs().saveHologram();
        plugin.getHologramManager().loadAllHolograms();

        p.sendMessage("§a[BTD] Đã dời §f" + name + " §avề vị trí mới.");
    }

    private void handleRemove(Player p, String name) {
        ConfigurationSection hologramConfig = plugin.getConfigs().getHologram();

        String sectionPath = null;
        String type = "";
        if (hologramConfig.contains("Holograms." + name)) {
            sectionPath = "Holograms." + name;
            type = "hologram";
        } else if (hologramConfig.contains("LeaderBoards." + name)) {
            sectionPath = "LeaderBoards." + name;
            type = "leaderboard";
        }

        if (sectionPath == null) {
            p.sendMessage("§cKhông tìm thấy hologram hoặc leaderboard tên: §f" + name);
            return;
        }

        
        plugin.getHologramManager().removeAll(); 

        
        hologramConfig.set(sectionPath, null);
        plugin.getConfigs().saveHologram();
        plugin.getHologramManager().loadAllHolograms();

        p.sendMessage("§a[BTD] Đã xóa " + type + " §f" + name + " §athành công!");
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§e---- [BetterTextDisplay Help] ----");
        sender.sendMessage("§6/btd reload §f- Tải lại plugin");
        sender.sendMessage("§6/btd create <tên> §f- Tạo hologram mới");
        sender.sendMessage("§6/btd movehere <tên> §f- Dời hologram/leaderboard về vị trí đang đứng");
        sender.sendMessage("§6/btd remove <tên> §f- Xóa hologram hoặc leaderboard");
        sender.sendMessage("§6/btd give tools §f- Nhận gậy di chuyển hologram");
    }

    
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("btd.admin")) return null;

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            
            completions.addAll(Arrays.asList("reload", "create", "movehere", "remove", "give"));
            return filterCompletions(completions, args[0]);
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();

            if (sub.equals("give")) {
                completions.add("tools");
                return filterCompletions(completions, args[1]);
            }

            if (sub.equals("movehere") || sub.equals("remove") || sub.equals("create")) {
                
                ConfigurationSection holoSec = plugin.getConfigs().getHologram().getConfigurationSection("Holograms");
                if (holoSec != null) {
                    for (String key : holoSec.getKeys(false)) {
                        if (!holoSec.getBoolean(key + ".is-template", false)) {
                            completions.add(key);
                        }
                    }
                }

                ConfigurationSection lbSec = plugin.getConfigs().getHologram().getConfigurationSection("LeaderBoards");
                if (lbSec != null) {
                    completions.addAll(lbSec.getKeys(false));
                }

                return filterCompletions(completions, args[1]);
            }
        }

        return completions;
    }

    private List<String> filterCompletions(List<String> list, String input) {
        if (input.isEmpty()) return list;
        List<String> filtered = new ArrayList<>();
        for (String s : list) {
            if (s.toLowerCase().startsWith(input.toLowerCase())) {
                filtered.add(s);
            }
        }
        return filtered;
    }
}