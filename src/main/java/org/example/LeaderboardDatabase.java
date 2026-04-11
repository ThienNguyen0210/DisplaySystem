package org.example;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class LeaderboardDatabase {
    private final Main plugin;
    private Connection connection;

    public LeaderboardDatabase(Main plugin) {
        this.plugin = plugin;
        setupDatabase();
        startAutoUpdateTask();
    }

    private void setupDatabase() {
        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) dataFolder.mkdirs();

            File dbFile = new File(dataFolder, "leaderboards.db");
            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();

            connection = DriverManager.getConnection(url);

            String sql = "CREATE TABLE IF NOT EXISTS player_money (" +
                    "uuid TEXT PRIMARY KEY," +
                    "name TEXT NOT NULL," +
                    "money DOUBLE NOT NULL," +
                    "last_updated LONG NOT NULL" +
                    ");";

            Statement stmt = connection.createStatement();
            stmt.execute(sql);
            stmt.close();

            plugin.getLogger().info("§a[BetterTextDisplay] SQLite database đã sẵn sàng cho Leaderboard!");
        } catch (SQLException e) {
            plugin.getLogger().severe("§c[BetterTextDisplay] Không thể khởi tạo SQLite database!");
            e.printStackTrace();
        }
    }

    
    public void updatePlayerMoney(Player player, double money) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "INSERT INTO player_money(uuid, name, money, last_updated) " +
                    "VALUES(?,?,?,?) ON CONFLICT(uuid) DO UPDATE SET " +
                    "name=excluded.name, money=excluded.money, last_updated=excluded.last_updated;";

            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, player.getUniqueId().toString());
                pstmt.setString(2, player.getName());
                pstmt.setDouble(3, money);
                pstmt.setLong(4, System.currentTimeMillis());
                pstmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    // Lấy top N player theo tiền (dùng cho leaderboard)
    public List<LeaderboardEntry> getTopMoney(int limit) {
        List<LeaderboardEntry> top = new ArrayList<>();
        String sql = "SELECT name, money FROM player_money ORDER BY money DESC LIMIT ?;";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, limit);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                top.add(new LeaderboardEntry(rs.getString("name"), rs.getDouble("money")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return top;
    }

    // Cập nhật dữ liệu khi player join (nếu dùng PlaceholderAPI + Vault)
    public void updateOnJoin(Player player) {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String placeholder = "%vault_eco_balance%";
            String valueStr = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, placeholder);

            // Làm sạch số tiền (loại bỏ dấu phẩy, chấm, ký tự tiền tệ...)
            String cleaned = valueStr.replaceAll("[^0-9.]", "");
            double money = 0.0;
            try {
                money = Double.parseDouble(cleaned);
            } catch (NumberFormatException e) {
                // Nếu parse lỗi, thử placeholder_fixed
                String fixed = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, "%vault_eco_balance_fixed%");
                cleaned = fixed.replaceAll("[^0-9.]", "");
                try {
                    money = Double.parseDouble(cleaned);
                } catch (NumberFormatException ignored) {
                    return; // Bỏ qua nếu vẫn lỗi
                }
            }

            // Cập nhật vào DB
            updatePlayerMoney(player, money);
        });
    }

    // Task tự động cập nhật toàn bộ online players mỗi 5 phút (đề phòng tiền thay đổi ngoài join)
    private void startAutoUpdateTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                updateOnJoin(p); // Tái sử dụng hàm
            }
        }, 20L * 60 * 5, 20L * 60 * 5); // Mỗi 5 phút
    }

    public void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}