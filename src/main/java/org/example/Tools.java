// Fixed Tools.java - Ensured it can move both holograms and leaderboards by using consistent tags/metadata.

package org.example;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Tools implements Listener {
    private final Main plugin;
    private final Map<UUID, String> dragging = new HashMap<>();
    private final Map<UUID, Location> originalLocs = new HashMap<>();

    public Tools(Main plugin) {
        this.plugin = plugin;
        startTask();
    }

    public static ItemStack getTool() {
        ItemStack item = new ItemStack(Material.STICK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§b§lHologram Mover §7(Right-Click)");
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent e) {
        Player p = e.getPlayer();
        ItemStack item = p.getInventory().getItemInMainHand();

        // Kiểm tra gậy
        if (item.getType() != Material.STICK || !item.hasItemMeta() ||
                !item.getItemMeta().getDisplayName().contains("Hologram Mover")) return;

        e.setCancelled(true);

        if (dragging.containsKey(p.getUniqueId())) {
            stopDragging(p, true);
            return;
        }

        org.bukkit.entity.Entity target = e.getRightClicked();
        String key = null;

        // TRƯỜNG HỢP 1: Click vào TextDisplay
        if (target instanceof TextDisplay td) {
            key = td.getScoreboardTags().stream()
                    .filter(tag -> tag.startsWith("BTD_HOLO_"))
                    .map(tag -> tag.replace("BTD_HOLO_", ""))
                    .findFirst().orElse(null);
        }
        // TRƯỜNG HỢP 2: Click vào Interaction (Hitbox)
        else if (target instanceof org.bukkit.entity.Interaction) {
            if (target.hasMetadata("BTD_HOLO_KEY")) {
                key = target.getMetadata("BTD_HOLO_KEY").get(0).asString();
            }
        }

        if (key != null) {
            dragging.put(p.getUniqueId(), key);
            // Quan trọng: Lưu vị trí của TextDisplay thực tế thay vì vị trí Interaction
            UUID tdUuid = plugin.getHologramManager().getActiveHolograms().get(key);
            org.bukkit.entity.Entity tdEnt = Bukkit.getEntity(tdUuid);
            originalLocs.put(p.getUniqueId(), (tdEnt != null ? tdEnt.getLocation() : target.getLocation()).clone());

            p.sendMessage("§e§l[BTD] §aĐang di chuyển: §f" + key);
        }
    }
    @EventHandler
    public void onPlayerInteract(org.bukkit.event.player.PlayerInteractEvent e) {
        Player p = e.getPlayer();
        // Nếu đang dắt Hologram và click chuột phải vào không khí hoặc khối
        if (dragging.containsKey(p.getUniqueId()) &&
                (e.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_AIR ||
                        e.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK)) {

            ItemStack item = p.getInventory().getItemInMainHand();
            if (item.getType() == Material.STICK && item.hasItemMeta() &&
                    item.getItemMeta().getDisplayName().contains("Hologram Mover")) {

                e.setCancelled(true);
                stopDragging(p, true); // Đặt xuống và lưu
            }
        }
    }
    @EventHandler
    public void onSlotChange(PlayerItemHeldEvent e) {
        if (dragging.containsKey(e.getPlayer().getUniqueId())) {
            stopDragging(e.getPlayer(), false);
            e.getPlayer().sendMessage("§e§l[BTD] §cDa huy di chuyen do doi vat pham.");
        }
    }

    private void stopDragging(Player p, boolean save) {
        String key = dragging.remove(p.getUniqueId());
        Location oldLoc = originalLocs.remove(p.getUniqueId());

        if (key == null) return;

        UUID tdUuid = plugin.getHologramManager().getActiveHolograms().get(key);
        if (tdUuid == null) return;
        org.bukkit.entity.Entity ent = Bukkit.getEntity(tdUuid);

        if (save && ent != null) {
            Location n = ent.getLocation();
            // Kiểm tra xem key thuộc Holograms hay LeaderBoards
            String sectionPath = plugin.getConfigs().getHologram().contains("Holograms." + key) ? "Holograms." + key : "LeaderBoards." + key;
            ConfigurationSection holoSec = plugin.getConfigs().getHologram().getConfigurationSection(sectionPath);

            // 1. Luôn luôn lưu tọa độ XYZ
            String locStr = String.format("%s,%f,%f,%f", n.getWorld().getName(), n.getX(), n.getY(), n.getZ());
            holoSec.set("location", locStr);

            // 2. Đọc cài đặt từ config.yml (Dùng plugin.getConfig())
            boolean canUpdateYaw = plugin.getConfig().getBoolean("Settings.Tools.paw", false); // Note: Có lẽ là lỗi đánh máy, nên là "yaw"
            boolean canUpdatePitch = plugin.getConfig().getBoolean("Settings.Tools.pitch", false);

            // 3. Xử lý lưu Yaw (Xoay ngang)
            if (canUpdateYaw) {
                holoSec.set("yaw", (double) n.getYaw());
            }

            // 4. Xử lý lưu Pitch (Xoay dọc)
            if (canUpdatePitch) {
                holoSec.set("pitch", (double) n.getPitch());
            }

            // Lưu file và thông báo
            plugin.getConfigs().saveHologram();
            p.sendMessage("§e§l[BTD] §aĐã đặt Hologram!");
            if (canUpdateYaw || canUpdatePitch) {
                p.sendMessage("§7(Đã cập nhật: " + (canUpdateYaw ? "Yaw " : "") + (canUpdatePitch ? "Pitch" : "") + ")");
            }

            plugin.getHologramManager().loadAllHolograms();
        } else if (ent != null) {
            ent.teleport(oldLoc);
        }
    }
    @EventHandler
    public void onPlace(org.bukkit.event.player.PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!dragging.containsKey(p.getUniqueId())) return;

        // Nếu đang cầm gậy và click chuột phải (vào không khí hoặc block)
        if (e.getAction().name().contains("RIGHT_CLICK")) {
            ItemStack item = p.getInventory().getItemInMainHand();
            if (item.getType() == Material.STICK && item.hasItemMeta() &&
                    item.getItemMeta().getDisplayName().contains("Hologram Mover")) {

                e.setCancelled(true);
                stopDragging(p, true); // Xác nhận đặt và lưu Yaw/Pitch
            }
        }
    }
    private void startTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<UUID, String> entry : dragging.entrySet()) {
                    Player p = Bukkit.getPlayer(entry.getKey());
                    if (p == null) continue;

                    UUID tdUuid = plugin.getHologramManager().getActiveHolograms().get(entry.getValue());
                    if (tdUuid == null) continue;

                    Entity ent = Bukkit.getEntity(tdUuid);
                    if (ent != null) {
                        // Lấy vị trí cách người chơi 3 block theo hướng nhìn
                        Location targetLoc = p.getTargetBlock(null, 3).getLocation().add(0.5, 1.2, 0.5);

                        // Cập nhật Yaw/Pitch để đối diện người chơi
                        targetLoc.setYaw(p.getLocation().getYaw() + 180);
                        targetLoc.setPitch(p.getLocation().getPitch() * -1); // Đảo ngược pitch để nhìn thẳng mặt

                        ent.teleport(targetLoc);
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }
}