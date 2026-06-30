package grinwin.promodawn.service;

import grinwin.promodawn.PromoDawn;
import grinwin.promodawn.db.DatabaseManager;
import grinwin.promodawn.model.MyPromoMenuData;
import grinwin.promodawn.model.PartnerFinanceSummary;
import grinwin.promodawn.model.PartnerTransaction;
import grinwin.promodawn.model.PromoCode;
import grinwin.promodawn.model.PromoUsage;
import grinwin.promodawn.model.StatsSnapshot;
import grinwin.promodawn.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public class PartnerFinanceService {
    private final PromoDawn plugin;
    private final DatabaseManager databaseManager;
    private final PromoService promoService;

    public PartnerFinanceService(PromoDawn plugin, DatabaseManager databaseManager, PromoService promoService) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.promoService = promoService;
    }

    public boolean addTransaction(double amount, String playerName, CommandSender initiator) throws SQLException {
        PromoUsage usage = promoService.getPlayerUsageByName(playerName);
        announceAdminTransaction(playerName, amount);
        if (usage == null) {
            logToDatabase("DONATION_WITHOUT_PARTNER", null, playerName,
                    "Игрок задонатил без привязки к партнеру: " + formatMoney(amount));
            return false;
        }

        Optional<PromoCode> codeOpt = promoService.findCode(usage.getCode());
        if (!codeOpt.isPresent()) {
            logToDatabase("DONATION_WITHOUT_CODE", null, playerName,
                    "Не найден код для транзакции: " + usage.getCode());
            return false;
        }

        PromoCode code = codeOpt.get();
        String partnerName = code.getOwner();
        double percent = code.getPartnerPercent();
        double earned = roundMoney(amount * percent / 100.0D);
        long now = System.currentTimeMillis();

        try (Connection conn = databaseManager.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO partner_transactions (partner_name, player_uuid, player_name, promo_code, amount, partner_percent, partner_earned, created_at) VALUES (?,?,?,?,?,?,?,?)")) {
                ps.setString(1, partnerName);
                ps.setString(2, usage.getPlayerUuid().toString());
                ps.setString(3, usage.getPlayerName());
                ps.setString(4, usage.getCode());
                ps.setDouble(5, amount);
                ps.setDouble(6, percent);
                ps.setDouble(7, earned);
                ps.setLong(8, now);
                ps.executeUpdate();
            }
        }

        logToDatabase("DONATION", partnerName, usage.getPlayerName(),
                "Начислено " + formatMoney(earned) + " с доната " + formatMoney(amount));
        notifyPartner(partnerName, usage.getPlayerName(), amount, earned);
        return true;
    }

    public void markPaid(String partnerName, CommandSender initiator) throws SQLException {
        PartnerFinanceSummary summary = getFinanceSummary(partnerName);
        double unpaid = summary.getUnpaidAmount();
        if (unpaid <= 0.0D) {
            return;
        }
        long now = System.currentTimeMillis();
        String actor = initiator != null ? initiator.getName() : "CONSOLE";
        try (Connection conn = databaseManager.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO partner_payouts (partner_name, amount, created_at, created_by, comment) VALUES (?,?,?,?,?)")) {
            ps.setString(1, partnerName);
            ps.setDouble(2, unpaid);
            ps.setLong(3, now);
            ps.setString(4, actor);
            ps.setString(5, "Команда /p paid");
            ps.executeUpdate();
        }
        logToDatabase("PAYOUT", partnerName, actor,
                "Выплачено партнеру " + formatMoney(unpaid));
    }

    public void setCurrentPaidAmount(String partnerName, double amount, CommandSender initiator) throws SQLException {
        double currentUnpaid = getFinanceSummary(partnerName).getUnpaidAmount();
        long now = System.currentTimeMillis();
        String actor = initiator != null ? initiator.getName() : "CONSOLE";

        if (currentUnpaid > 0.0D) {
            try (Connection conn = databaseManager.getConnection();
                    PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO partner_payouts (partner_name, amount, created_at, created_by, comment) VALUES (?,?,?,?,?)")) {
                ps.setString(1, partnerName);
                ps.setDouble(2, -currentUnpaid);
                ps.setLong(3, now);
                ps.setString(4, actor);
                ps.setString(5, "Корректировка unpaid перед setpaid");
                ps.executeUpdate();
            }
        }

        double earned = getFinanceSummary(partnerName).getTotalEarned();
        double targetPaidTotal = roundMoney(earned - amount);
        double currentPaidTotal = getFinanceSummary(partnerName).getTotalPaid();
        double delta = roundMoney(targetPaidTotal - currentPaidTotal);
        if (delta != 0.0D) {
            try (Connection conn = databaseManager.getConnection();
                    PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO partner_payouts (partner_name, amount, created_at, created_by, comment) VALUES (?,?,?,?,?)")) {
                ps.setString(1, partnerName);
                ps.setDouble(2, delta);
                ps.setLong(3, now);
                ps.setString(4, actor);
                ps.setString(5, "Установка текущей суммы к выплате");
                ps.executeUpdate();
            }
        }
        logToDatabase("SET_UNPAID", partnerName, actor,
                "Установлена сумма к выплате: " + formatMoney(amount));
    }

    public void setPaidTotal(String partnerName, double amount, CommandSender initiator) throws SQLException {
        String actor = initiator != null ? initiator.getName() : "CONSOLE";
        long now = System.currentTimeMillis();
        try (Connection conn = databaseManager.getConnection()) {
            try (PreparedStatement delete = conn.prepareStatement("DELETE FROM partner_paid_totals WHERE partner_name=?")) {
                delete.setString(1, partnerName);
                delete.executeUpdate();
            }
            try (PreparedStatement insert = conn.prepareStatement(
                    "INSERT INTO partner_paid_totals (partner_name, total_paid, updated_at, updated_by) VALUES (?,?,?,?)")) {
                insert.setString(1, partnerName);
                insert.setDouble(2, amount);
                insert.setLong(3, now);
                insert.setString(4, actor);
                insert.executeUpdate();
            }
        }
        logToDatabase("SET_PAID_TOTAL", partnerName, actor,
                "Установлена общая выплаченная сумма: " + formatMoney(amount));
    }

    public PartnerFinanceSummary getFinanceSummary(String partnerName) throws SQLException {
        long dayStart = TimeUtil.startOfTodayMoscow();
        long weekStart = TimeUtil.startOfWeekMoscow();
        long monthStart = TimeUtil.startOfMonthMoscow();

        double day = getEarnedSince(partnerName, dayStart);
        double week = getEarnedSince(partnerName, weekStart);
        double month = getEarnedSince(partnerName, monthStart);
        double total = getTotalEarned(partnerName);
        double paid = getTotalPaid(partnerName);
        double override = getPaidTotalOverride(partnerName);
        if (override >= 0.0D) {
            paid = override;
        }
        double unpaid = roundMoney(total - paid);
        if (unpaid < 0.0D) {
            unpaid = 0.0D;
        }
        return new PartnerFinanceSummary(day, week, month, total, unpaid, paid);
    }

    public List<PartnerTransaction> listTransactions(String partnerName, int limit, int offset) throws SQLException {
        List<PartnerTransaction> list = new ArrayList<>();
        try (Connection conn = databaseManager.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT id, partner_name, player_uuid, player_name, promo_code, amount, partner_percent, partner_earned, created_at FROM partner_transactions WHERE partner_name=? ORDER BY created_at DESC LIMIT ? OFFSET ?")) {
            ps.setString(1, partnerName);
            ps.setInt(2, Math.max(1, limit));
            ps.setInt(3, Math.max(0, offset));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new PartnerTransaction(
                            rs.getLong("id"),
                            rs.getString("partner_name"),
                            UUID.fromString(rs.getString("player_uuid")),
                            rs.getString("player_name"),
                            rs.getString("promo_code"),
                            rs.getDouble("amount"),
                            rs.getDouble("partner_percent"),
                            rs.getDouble("partner_earned"),
                            rs.getLong("created_at")));
                }
            }
        }
        return list;
    }

    public MyPromoMenuData loadMyPromoMenuData(PromoCode code, int usagePage, int usagePageSize, int donationPage,
            int donationPageSize) throws SQLException {
        StatsSnapshot usage = promoService.loadStatsPage(code.getCode(), usagePage, usagePageSize);
        PartnerFinanceSummary finance = getFinanceSummary(code.getOwner());
        List<PartnerTransaction> transactions = listTransactions(code.getOwner(), donationPageSize,
                donationPage * donationPageSize);
        return new MyPromoMenuData(code, usage, finance, transactions);
    }

    private double getEarnedSince(String partnerName, long since) throws SQLException {
        try (Connection conn = databaseManager.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT COALESCE(SUM(partner_earned), 0) FROM partner_transactions WHERE partner_name=? AND created_at>=?")) {
            ps.setString(1, partnerName);
            ps.setLong(2, since);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? roundMoney(rs.getDouble(1)) : 0.0D;
            }
        }
    }

    private double getTotalEarned(String partnerName) throws SQLException {
        try (Connection conn = databaseManager.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT COALESCE(SUM(partner_earned), 0) FROM partner_transactions WHERE partner_name=?")) {
            ps.setString(1, partnerName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? roundMoney(rs.getDouble(1)) : 0.0D;
            }
        }
    }

    private double getTotalPaid(String partnerName) throws SQLException {
        try (Connection conn = databaseManager.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT COALESCE(SUM(amount), 0) FROM partner_payouts WHERE partner_name=?")) {
            ps.setString(1, partnerName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? roundMoney(rs.getDouble(1)) : 0.0D;
            }
        }
    }

    private double getPaidTotalOverride(String partnerName) throws SQLException {
        try (Connection conn = databaseManager.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT total_paid FROM partner_paid_totals WHERE partner_name=? LIMIT 1")) {
            ps.setString(1, partnerName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? roundMoney(rs.getDouble(1)) : -1.0D;
            }
        }
    }

    private void announceAdminTransaction(String playerName, double amount) {
        String message = plugin.getConfig().getString("partner-system.messages.admin-donation",
                "&fИгрок &a%player% &fзадонатил &a%amount% &fрублей")
                .replace("%player%", playerName)
                .replace("%amount%", formatMoney(amount));
        String colored = grinwin.promodawn.util.MessageUtil.colorize(message);
        Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.hasPermission("promodawn.notify"))
                .forEach(p -> p.sendMessage(colored));
        plugin.getLogger().info("[DONATION] " + playerName + " donated " + formatMoney(amount));
    }

    private void notifyPartner(String partnerName, String playerName, double amount, double earned) {
        Player partner = Bukkit.getPlayerExact(partnerName);
        if (partner == null) {
            return;
        }
        String message = plugin.getConfig().getString("partner-system.messages.partner-donation",
                "&fИгрок &a%player% &fзадонатил &a%amount% &fрублей по вашему промокоду. Вы получили с него &a%earned%&f.")
                .replace("%player%", playerName)
                .replace("%amount%", formatMoney(amount))
                .replace("%earned%", formatMoney(earned));
        partner.sendMessage(grinwin.promodawn.util.MessageUtil.colorize(message));
    }

    private void logToDatabase(String type, String partnerName, String actor, String details) throws SQLException {
        try (Connection conn = databaseManager.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO partner_logs (log_type, partner_name, actor_name, details, created_at) VALUES (?,?,?,?,?)")) {
            ps.setString(1, type);
            ps.setString(2, partnerName);
            ps.setString(3, actor);
            ps.setString(4, details);
            ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    public static String formatMoney(double value) {
        double rounded = roundMoney(value);
        if (Math.abs(rounded - Math.rint(rounded)) < 0.000001D) {
            return String.format(Locale.US, "%.0f", rounded);
        }
        return String.format(Locale.US, "%.2f", rounded);
    }

    private static double roundMoney(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }
}
