package org.example;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin {
    private ConfigManager configManager;
    private HologramManager hologramManager;
    private DisplayManager displayManager;
    private AIManager aiManager;
    private EventListener eventListener;
    private LeaderboardDatabase leaderboardDatabase; // THÊM MỚI
    private static Economy econ = null;

    @Override
    public void onEnable() {
        // --- PHẦN 1: HIỂN THỊ BANNER ---
        String cyan = "\u001B[36m";
        String yellow = "\u001B[33m";
        String red = "\u001B[31m";
        String green = "\u001B[32m";
        String reset = "\u001B[0m";

        String[] banner = {
                " ",
                cyan + "  ■■■■■■■  ■■■■■■■  ■■■■■■■ ",
                cyan + "  ■     ■     ■     ■     ■ ",
                cyan + "  ■     ■     ■     ■     ■ ",
                cyan + "  ■■■■■■■     ■     ■     ■ ",
                cyan + "  ■     ■     ■     ■     ■ ",
                cyan + "  ■     ■     ■     ■     ■ ",
                cyan + "  ■■■■■■■     ■     ■■■■■■■ ",
                " ",
                yellow + "  >> Tác giả: " + reset + "ThienNguyen0210 (discord: Nhà Văn Viết Code)",
                yellow + "  >> Phiên bản: " + reset + "1.20.x->1.21.x",
                red    + "  >> Trạng thái: " + reset + "Thử Nghiệm",
                green  + "  >> Tuổi đời: " + reset + "Phiên bản đầu tiên",
                red  + "  >> Nguồn: " + reset + "Hef_SUS",
                " "
        };
        for (String line : banner) {
            Bukkit.getConsoleSender().sendMessage(line);
        }

        // --- PHẦN 2: THIẾT LẬP HỆ THỐNG ---

        // 1. Kiểm tra Vault + Economy
        if (!setupEconomy()) {
            getLogger().severe("§c[!] Không tìm thấy Vault hoặc plugin Kinh tế! Vô hiệu hóa plugin... (hãy cài plugins vault)");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 2. Khởi tạo ConfigManager
        this.configManager = new ConfigManager(this);

        // 3. Khởi tạo Leaderboard Database (SQLite) - TRƯỚC HologramManager để dùng được
        this.leaderboardDatabase = new LeaderboardDatabase(this);

        // 4. Khởi tạo các Manager khác
        this.aiManager = new AIManager();
        this.displayManager = new DisplayManager(this);
        this.hologramManager = new HologramManager(this);

        // 5. Load hologram và leaderboard từ config
        this.hologramManager.loadAllHolograms();

        // 6. Đăng ký lệnh
        if (getCommand("btd") != null) {
            getCommand("btd").setExecutor(new BTDCommand(this));
        }

        // 7. Đăng ký sự kiện
        this.eventListener = new EventListener(this);
        getServer().getPluginManager().registerEvents(this.eventListener, this);
        getServer().getPluginManager().registerEvents(new Tools(this), this);

        // 8. Đăng ký event PlayerJoin để cập nhật tiền vào SQLite
        getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onPlayerJoin(PlayerJoinEvent e) {
                // Delay 5 giây để PlaceholderAPI và economy load đầy đủ dữ liệu
                Bukkit.getScheduler().runTaskLater(Main.this, () -> {
                    leaderboardDatabase.updateOnJoin(e.getPlayer());
                }, 20L * 5);
            }
        }, this);

        getLogger().info("§a[BetterTextDisplay] Plugin đã khởi động thành công! Leaderboard SQLite sẵn sàng.");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    @Override
    public void onDisable() {
        // Xóa tất cả hologram trước
        if (hologramManager != null) {
            hologramManager.removeAll();
        }

        // Đóng kết nối SQLite an toàn
        if (leaderboardDatabase != null) {
            leaderboardDatabase.closeConnection();
        }

        getLogger().info("§c[BetterTextDisplay] Plugin đã tắt thành công!");
    }

    // --- GETTERS ---
    public static Economy getEconomy() {
        return econ;
    }

    public ConfigManager getConfigs() {
        return configManager;
    }

    public DisplayManager getDisplayManager() {
        return displayManager;
    }

    public AIManager getAiManager() {
        return aiManager;
    }

    public EventListener getEventListener() {
        return eventListener;
    }

    public HologramManager getHologramManager() {
        return hologramManager;
    }

    public LeaderboardDatabase getLeaderboardDatabase() {
        return leaderboardDatabase;
    }
}