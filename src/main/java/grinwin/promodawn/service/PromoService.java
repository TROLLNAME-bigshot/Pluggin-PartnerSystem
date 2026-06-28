package grinwin.promodawn.service;

import grinwin.promodawn.db.DatabaseManager;
import grinwin.promodawn.model.PromoCode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class PromoService {
    private final DatabaseManager databaseManager;
    private final Map<String, PromoCode> codeById = new HashMap<>();

    public PromoService(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public void loadFromConfig(ConfigurationSection codesSection, ConfigurationSection rewardsSection)
            throws SQLException {
        Map<String, PromoCode> loaded = new HashMap<>();

        // Load standard codes
        if (codesSection != null) {
            for (String key : codesSection.getKeys(false)) {
                ConfigurationSection sec = codesSection.getConfigurationSection(key);
                if (sec == null)
                    continue;
                loaded.put(key.toLowerCase(Locale.ROOT), loadCodeFromSection(key, sec, false));
            }
        }

        // Load limited codes
        if (codesSection != null && codesSection.getParent() != null) {
            ConfigurationSection limitSection = codesSection.getParent().getConfigurationSection("promo-codes-limit");
            if (limitSection != null) {
                for (String key : limitSection.getKeys(false)) {
                    ConfigurationSection sec = limitSection.getConfigurationSection(key);
                    if (sec == null)
                        continue;
                    loaded.put(key.toLowerCase(Locale.ROOT), loadCodeFromSection(key, sec, true));
                }
            }
        }

        // upsert to DB and refresh cache. Preserve existing created_at if row exists.
        try (Connection conn = databaseManager.getConnection()) {
            // insert new ones
            try (PreparedStatement insert = conn.prepareStatement(
                    "INSERT IGNORE INTO promo_codes (code, owner, reward_key, created_at, partner_percent, max_uses, custom_success_msg, custom_failure_msg, ignore_global_limit) VALUES (?,?,?,?,?,?,?,?,?)")) {
                for (PromoCode code : loaded.values()) {
                    insert.setString(1, code.getCode());
                    insert.setString(2, code.getOwner());
                    insert.setString(3, code.getRewardKey());
                    insert.setLong(4, code.getCreatedAt());
                    insert.setDouble(5, code.getPartnerPercent());
                    insert.setInt(6, code.getMaxUses());
                    insert.setString(7, serializeList(code.getSuccessMessages()));
                    insert.setString(8, serializeList(code.getFailureMessages()));
                    insert.setBoolean(9, code.isIgnoreGlobalLimit());
                    insert.addBatch();
                }
                insert.executeBatch();
            }
            // update mutable fields without touching created_at
            try (PreparedStatement update = conn.prepareStatement(
                    "UPDATE promo_codes SET owner=?, reward_key=?, partner_percent=?, max_uses=?, custom_success_msg=?, custom_failure_msg=?, ignore_global_limit=? WHERE code=?")) {
                for (PromoCode code : loaded.values()) {
                    update.setString(1, code.getOwner());
                    update.setString(2, code.getRewardKey());
                    update.setDouble(3, code.getPartnerPercent());
                    update.setInt(4, code.getMaxUses());
                    update.setString(5, serializeList(code.getSuccessMessages()));
                    update.setString(6, serializeList(code.getFailureMessages()));
                    update.setBoolean(7, code.isIgnoreGlobalLimit());
                    update.setString(8, code.getCode());
                    update.addBatch();
                }
                update.executeBatch();
            }

            // reload from DB to get actual created_at from storage
            // ВАЖНО: загружаем только те коды, которые присутствуют в конфиге!
            Map<String, PromoCode> refreshed = new HashMap<>();
            if (!loaded.isEmpty()) {
                // Создаём список кодов из конфига для SQL IN запроса
                String placeholders = String.join(",", Collections.nCopies(loaded.size(), "?"));
                String query = "SELECT code, owner, reward_key, created_at, partner_percent, max_uses, custom_success_msg, custom_failure_msg, ignore_global_limit FROM promo_codes WHERE code IN ("
                        + placeholders + ")";

                try (PreparedStatement sel = conn.prepareStatement(query)) {
                    int idx = 1;
                    for (String code : loaded.keySet()) {
                        sel.setString(idx++, code);
                    }

                    try (ResultSet rs = sel.executeQuery()) {
                        while (rs.next()) {
                            String code = rs.getString("code");
                            String owner = rs.getString("owner");
                            String reward = rs.getString("reward_key");
                            long createdAt = rs.getLong("created_at");
                            int maxUses = rs.getInt("max_uses");
                            double partnerPercent = rs.getDouble("partner_percent");
                            List<String> success = deserializeList(rs.getString("custom_success_msg"));
                            List<String> failure = deserializeList(rs.getString("custom_failure_msg"));
                            boolean ignoreGlobal = rs.getBoolean("ignore_global_limit");
                            refreshed.put(code.toLowerCase(java.util.Locale.ROOT),
                                    new PromoCode(code.toLowerCase(java.util.Locale.ROOT), owner, reward, createdAt,
                                            partnerPercent, maxUses, ignoreGlobal, success, failure));
                        }
                    }
                }
            }
            this.codeById.clear();
            this.codeById.putAll(refreshed);
        }
    }

    private PromoCode loadCodeFromSection(String key, ConfigurationSection sec, boolean isLimited) {
        String owner = sec.getString("owner", key); // default specific owner for limited codes might be null or system
        if (isLimited && !sec.contains("owner")) {
            owner = "SERVER";
        }
        String reward = sec.getString("reward", "default");
        long createdAt;
        if (sec.isString("create-data")) {
            try {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm",
                        new java.util.Locale("ru"));
                sdf.setTimeZone(java.util.TimeZone.getTimeZone("Europe/Moscow"));
                java.util.Date d = sdf.parse(sec.getString("create-data"));
                createdAt = d.getTime() / 1000L;
            } catch (Exception e) {
                java.util.logging.Logger.getLogger("PromoDawn")
                        .warning("Не удалось разобрать create-data для '" + key + "': " + e.getMessage());
                createdAt = sec.getLong("created-at", System.currentTimeMillis() / 1000L);
            }
        } else {
            createdAt = sec.getLong("created-at", System.currentTimeMillis() / 1000L);
        }

        double partnerPercent = sec.getDouble("percentage-promo", 0.0D);

        int maxUses = -1;
        List<String> successMsg = null;
        List<String> failureMsg = null;
        boolean ignoreGlobalLimit = false;

        if (isLimited) {
            maxUses = sec.getInt("use", -1);
            if (sec.contains("message-use")) {
                successMsg = sec.getStringList("message-use");
            }
            if (sec.contains("message-use-false")) {
                failureMsg = sec.getStringList("message-use-false");
            }
            ignoreGlobalLimit = true; // Limited codes by default ignore the "one-per-player-globally" rule
        }

        return new PromoCode(key.toLowerCase(Locale.ROOT), owner, reward, createdAt, partnerPercent, maxUses,
                ignoreGlobalLimit,
                successMsg, failureMsg);
    }

    private String serializeList(List<String> list) {
        if (list == null || list.isEmpty())
            return null;
        return String.join(";;;", list);
    }

    private List<String> deserializeList(String data) {
        if (data == null || data.isEmpty())
            return null;
        return Arrays.asList(data.split(";;;"));
    }

    public void createPromoCode(grinwin.promodawn.PromoDawn plugin, String name, String reward, int maxUses,
            String successMessage) {
        FileConfiguration config = plugin.getConfig();
        String path = "promo-codes-limit." + name;

        config.set(path + ".reward", reward);
        config.set(path + ".use", maxUses);

        // Success message as list
        List<String> success = new ArrayList<>();
        success.add("");
        success.add(successMessage);
        success.add("");
        config.set(path + ".message-use", success);

        // Default failure message if not provided
        List<String> failure = new ArrayList<>();
        failure.add("");
        failure.add("&cДанный промокод уже использовали! Вы не успели :(");
        failure.add("");
        config.set(path + ".message-use-false", failure);

        config.set(path + ".create-data", new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", new java.util.Locale("ru"))
                .format(new java.util.Date()));

        plugin.saveConfig();
        plugin.reloadConfig();
        try {
            // Reload service
            loadFromConfig(plugin.getConfig().getConfigurationSection("promo-codes"),
                    plugin.getConfig().getConfigurationSection("rewards"));
        } catch (SQLException e) {
            plugin.getLogger().severe("SQL error reloading promo codes: " + e.getMessage());
        }
    }

    public Optional<PromoCode> findCode(String code) {
        if (code == null)
            return Optional.empty();
        return Optional.ofNullable(codeById.get(code.toLowerCase(Locale.ROOT)));
    }

    public Optional<PromoCode> findCodeByOwner(String ownerName) {
        if (ownerName == null)
            return Optional.empty();
        String lower = ownerName.toLowerCase(Locale.ROOT);
        for (PromoCode c : codeById.values()) {
            if (c.getOwner().equalsIgnoreCase(lower) || c.getOwner().equalsIgnoreCase(ownerName)) {
                return Optional.of(c);
            }
        }
        return Optional.empty();
    }

    /**
     * Checks if player has used ANY standard promo code (where ignore_global_limit
     * = false).
     */
    public boolean hasUsedStandardPromo(UUID playerUuid) throws SQLException {
        try (Connection conn = databaseManager.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT 1 FROM promo_usages u JOIN promo_codes c ON u.code = c.code WHERE u.player_uuid=? AND c.ignore_global_limit = FALSE LIMIT 1")) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Checks if player has used a SPECIFIC promo code.
     */
    public boolean hasUsedSpecificPromo(UUID playerUuid, String code) throws SQLException {
        try (Connection conn = databaseManager.getConnection();
                PreparedStatement ps = conn
                        .prepareStatement("SELECT 1 FROM promo_usages WHERE player_uuid=? AND code=? LIMIT 1")) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, code);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public void recordUsage(UUID playerUuid, String playerName, PromoCode code, long usedAt, long firstJoinAt)
            throws SQLException {
        try (Connection conn = databaseManager.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO promo_usages (player_uuid, player_name, code, used_at, first_join_at) VALUES (?,?,?,?,?)")) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, playerName);
            ps.setString(3, code.getCode());
            ps.setLong(4, usedAt);
            ps.setLong(5, firstJoinAt);
            ps.executeUpdate();
        }
    }

    public Map<String, PromoCode> getAllCodes() {
        return Collections.unmodifiableMap(codeById);
    }

    public int getTotalUsageCount(String code) throws SQLException {
        try (Connection conn = databaseManager.getConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM promo_usages WHERE code=?")) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public int getUsageCountSince(String code, long sinceMillis) throws SQLException {
        try (Connection conn = databaseManager.getConnection();
                PreparedStatement ps = conn
                        .prepareStatement("SELECT COUNT(*) FROM promo_usages WHERE code=? AND used_at>=?")) {
            ps.setString(1, code);
            ps.setLong(2, sinceMillis);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public List<grinwin.promodawn.model.PromoUsage> listUsages(String code, int limit, int offset) throws SQLException {
        List<grinwin.promodawn.model.PromoUsage> list = new ArrayList<>();
        try (Connection conn = databaseManager.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT id, player_uuid, player_name, code, used_at, first_join_at FROM promo_usages WHERE code=? ORDER BY used_at DESC LIMIT ? OFFSET ?")) {
            ps.setString(1, code);
            ps.setInt(2, Math.max(1, limit));
            ps.setInt(3, Math.max(0, offset));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong("id");
                    java.util.UUID uuid = java.util.UUID.fromString(rs.getString("player_uuid"));
                    String name = rs.getString("player_name");
                    String codeFromDb = rs.getString("code"); // Renamed to avoid conflict with method parameter
                    long usedAt = rs.getLong("used_at");
                    long firstJoinAt = rs.getLong("first_join_at");
                    list.add(new grinwin.promodawn.model.PromoUsage(id, uuid, name, codeFromDb, usedAt, firstJoinAt));
                }
            }
        }
        return list;
    }

    public Map<String, Integer> getTotalUsageCountsForAllCodes() throws SQLException {
        Map<String, Integer> counts = new HashMap<>();
        try (Connection conn = databaseManager.getConnection();
                PreparedStatement ps = conn
                        .prepareStatement("SELECT code, COUNT(*) AS c FROM promo_usages GROUP BY code")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String code = rs.getString("code");
                    int c = rs.getInt("c");
                    if (code != null)
                        counts.put(code.toLowerCase(java.util.Locale.ROOT), c);
                }
            }
        }
        return counts;
    }

    public grinwin.promodawn.model.StatsSnapshot loadStatsPage(String code, int page, int pageSize)
            throws SQLException {
        long dayStart = grinwin.promodawn.util.TimeUtil.startOfTodayMoscow();
        long weekStart = grinwin.promodawn.util.TimeUtil.startOfWeekMoscow();
        int day = getUsageCountSince(code, dayStart);
        int week = getUsageCountSince(code, weekStart);
        long monthStart = grinwin.promodawn.util.TimeUtil.startOfMonthMoscow();
        int month = getUsageCountSince(code, monthStart);
        int total = getTotalUsageCount(code);
        int offset = page * pageSize;
        java.util.List<grinwin.promodawn.model.PromoUsage> pageList = listUsages(code, pageSize, offset);
        return new grinwin.promodawn.model.StatsSnapshot(day, week, total, month, pageList);
    }

    public grinwin.promodawn.model.PromoUsage getPlayerUsage(java.util.UUID playerUuid) throws SQLException {
        try (Connection conn = databaseManager.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT u.id, u.player_name, u.code, u.used_at, u.first_join_at " +
                                "FROM promo_usages u " +
                                "JOIN promo_codes c ON u.code = c.code " +
                                "WHERE u.player_uuid=? AND c.ignore_global_limit = FALSE " +
                                "ORDER BY u.used_at DESC LIMIT 1")) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long id = rs.getLong("id");
                    String name = rs.getString("player_name");
                    String code = rs.getString("code");
                    long usedAt = rs.getLong("used_at");
                    long firstJoinAt = rs.getLong("first_join_at");
                    return new grinwin.promodawn.model.PromoUsage(id, playerUuid, name, code, usedAt, firstJoinAt);
                }
            }
        }
        return null;
    }

    public grinwin.promodawn.model.PromoUsage getPlayerUsageByName(String playerName) throws SQLException {
        try (Connection conn = databaseManager.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT u.id, u.player_uuid, u.player_name, u.code, u.used_at, u.first_join_at " +
                                "FROM promo_usages u " +
                                "JOIN promo_codes c ON u.code = c.code " +
                                "WHERE u.player_name=? AND c.ignore_global_limit = FALSE " +
                                "ORDER BY u.used_at DESC LIMIT 1")) {
            ps.setString(1, playerName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long id = rs.getLong("id");
                    java.util.UUID uuid = java.util.UUID.fromString(rs.getString("player_uuid"));
                    String name = rs.getString("player_name");
                    String code = rs.getString("code");
                    long usedAt = rs.getLong("used_at");
                    long firstJoinAt = rs.getLong("first_join_at");
                    return new grinwin.promodawn.model.PromoUsage(id, uuid, name, code, usedAt, firstJoinAt);
                }
            }
        }
        return null;
    }
}
