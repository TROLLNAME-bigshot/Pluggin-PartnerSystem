package grinwin.promodawn.command;

import grinwin.promodawn.PromoDawn;
import grinwin.promodawn.service.PartnerFinanceService;
import grinwin.promodawn.service.PromoService;
import grinwin.promodawn.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class PartnerCommand implements CommandExecutor, TabCompleter {
    private final PromoDawn plugin;
    private final PartnerFinanceService financeService;
    private final PromoService promoService;

    public PartnerCommand(PromoDawn plugin, PartnerFinanceService financeService, PromoService promoService) {
        this.plugin = plugin;
        this.financeService = financeService;
        this.promoService = promoService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("promodawn.partner.admin")) {
            sender.sendMessage(MessageUtil.colorize(plugin.getConfig().getString("messages.prefix")
                    + plugin.getConfig().getString("messages.no-permission")));
            return true;
        }
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if ("transaction".equals(sub)) {
            return handleTransaction(sender, args);
        }
        if ("paid".equals(sub)) {
            return handlePaid(sender, args);
        }
        if ("setpaid".equals(sub)) {
            return handleSetPaid(sender, args);
        }
        if ("setpaidtotal".equals(sub)) {
            return handleSetPaidTotal(sender, args);
        }

        sendUsage(sender);
        return true;
    }

    private boolean handleTransaction(CommandSender sender, String[] args) {
        if (args.length != 4 || !"add".equalsIgnoreCase(args[1])) {
            sender.sendMessage(MessageUtil.colorize("&cИспользование: /p transaction add <сумма> <ник>"));
            return true;
        }
        Double amount = parseAmount(args[2], sender);
        if (amount == null) {
            return true;
        }
        String playerName = args[3];
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                try {
                    boolean linked = financeService.addTransaction(amount, playerName, sender);
                    new org.bukkit.scheduler.BukkitRunnable() {
                        @Override
                        public void run() {
                            if (linked) {
                                sender.sendMessage(MessageUtil.colorize("&aТранзакция добавлена для игрока &f" + playerName));
                            } else {
                                sender.sendMessage(MessageUtil.colorize(
                                        "&eТранзакция добавлена в лог, но у игрока нет активированного партнерского промокода."));
                            }
                        }
                    }.runTask(plugin);
                } catch (Exception e) {
                    new org.bukkit.scheduler.BukkitRunnable() {
                        @Override
                        public void run() {
                            sender.sendMessage(MessageUtil.colorize("&cОшибка добавления транзакции: " + e.getMessage()));
                        }
                    }.runTask(plugin);
                }
            }
        }.runTaskAsynchronously(plugin);
        return true;
    }

    private boolean handlePaid(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage(MessageUtil.colorize("&cИспользование: /p paid <ник партнера>"));
            return true;
        }
        String partnerName = args[1];
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                try {
                    financeService.markPaid(partnerName, sender);
                    new org.bukkit.scheduler.BukkitRunnable() {
                        @Override
                        public void run() {
                            sender.sendMessage(MessageUtil.colorize("&aВыплата для партнера &f" + partnerName + " &aзафиксирована."));
                        }
                    }.runTask(plugin);
                } catch (Exception e) {
                    new org.bukkit.scheduler.BukkitRunnable() {
                        @Override
                        public void run() {
                            sender.sendMessage(MessageUtil.colorize("&cОшибка выплаты: " + e.getMessage()));
                        }
                    }.runTask(plugin);
                }
            }
        }.runTaskAsynchronously(plugin);
        return true;
    }

    private boolean handleSetPaid(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage(MessageUtil.colorize("&cИспользование: /p setpaid <ник партнера> <сумма>"));
            return true;
        }
        String partnerName = args[1];
        Double amount = parseAmount(args[2], sender);
        if (amount == null) {
            return true;
        }
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                try {
                    financeService.setCurrentPaidAmount(partnerName, amount, sender);
                    new org.bukkit.scheduler.BukkitRunnable() {
                        @Override
                        public void run() {
                            sender.sendMessage(MessageUtil.colorize("&aСумма к выплате для &f" + partnerName + " &aизменена на &f"
                                    + PartnerFinanceService.formatMoney(amount)));
                        }
                    }.runTask(plugin);
                } catch (Exception e) {
                    new org.bukkit.scheduler.BukkitRunnable() {
                        @Override
                        public void run() {
                            sender.sendMessage(MessageUtil.colorize("&cОшибка изменения суммы: " + e.getMessage()));
                        }
                    }.runTask(plugin);
                }
            }
        }.runTaskAsynchronously(plugin);
        return true;
    }

    private boolean handleSetPaidTotal(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage(MessageUtil.colorize("&cИспользование: /p setpaidtotal <ник партнера> <сумма>"));
            return true;
        }
        String partnerName = args[1];
        Double amount = parseAmount(args[2], sender);
        if (amount == null) {
            return true;
        }
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                try {
                    financeService.setPaidTotal(partnerName, amount, sender);
                    new org.bukkit.scheduler.BukkitRunnable() {
                        @Override
                        public void run() {
                            sender.sendMessage(MessageUtil.colorize("&aОбщая выплаченная сумма для &f" + partnerName
                                    + " &aизменена на &f" + PartnerFinanceService.formatMoney(amount)));
                        }
                    }.runTask(plugin);
                } catch (Exception e) {
                    new org.bukkit.scheduler.BukkitRunnable() {
                        @Override
                        public void run() {
                            sender.sendMessage(MessageUtil.colorize("&cОшибка изменения общей суммы: " + e.getMessage()));
                        }
                    }.runTask(plugin);
                }
            }
        }.runTaskAsynchronously(plugin);
        return true;
    }

    private Double parseAmount(String raw, CommandSender sender) {
        try {
            double value = Double.parseDouble(raw.replace(',', '.'));
            if (value < 0.0D) {
                sender.sendMessage(MessageUtil.colorize("&cСумма не может быть отрицательной."));
                return null;
            }
            return value;
        } catch (NumberFormatException e) {
            sender.sendMessage(MessageUtil.colorize("&cНекорректная сумма: " + raw));
            return null;
        }
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(MessageUtil.colorize("&e/p transaction add <сумма> <ник>"));
        sender.sendMessage(MessageUtil.colorize("&e/p paid <ник партнера>"));
        sender.sendMessage(MessageUtil.colorize("&e/p setpaid <ник партнера> <сумма>"));
        sender.sendMessage(MessageUtil.colorize("&e/p setpaidtotal <ник партнера> <сумма>"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("promodawn.partner.admin")) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return filter(Arrays.asList("transaction", "paid", "setpaid", "setpaidtotal"), args[0]);
        }
        if (args.length == 2 && "transaction".equalsIgnoreCase(args[0])) {
            return filter(Collections.singletonList("add"), args[1]);
        }
        if (args.length == 2 && ("paid".equalsIgnoreCase(args[0]) || "setpaid".equalsIgnoreCase(args[0])
                || "setpaidtotal".equalsIgnoreCase(args[0]))) {
            List<String> owners = new ArrayList<>();
            for (grinwin.promodawn.model.PromoCode code : promoService.getAllCodes().values()) {
                if (!owners.contains(code.getOwner())) {
                    owners.add(code.getOwner());
                }
            }
            return filter(owners, args[1]);
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> values, String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(value);
            }
        }
        return result;
    }
}
