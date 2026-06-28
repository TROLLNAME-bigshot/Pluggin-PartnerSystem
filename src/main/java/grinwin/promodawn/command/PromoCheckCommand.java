package grinwin.promodawn.command;

import grinwin.promodawn.PromoDawn;
import grinwin.promodawn.model.PromoUsage;
import grinwin.promodawn.service.PromoService;
import grinwin.promodawn.util.MessageUtil;
import grinwin.promodawn.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class PromoCheckCommand implements CommandExecutor {

    private final PromoDawn plugin;
    private final PromoService service;

    public PromoCheckCommand(PromoDawn plugin, PromoService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("promodawn.check")) {
            sender.sendMessage(MessageUtil.colorize(plugin.getConfig().getString("messages.prefix") + plugin.getConfig().getString("messages.no-permission")));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(MessageUtil.colorize(plugin.getConfig().getString("messages.prefix") + "&eИспользование: &f/promocheck <ник>"));
            return true;
        }

        String targetName = args[0];

        // Асинхронная загрузка данных
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    // Сначала пытаемся найти онлайн игрока
                    Player target = Bukkit.getPlayerExact(targetName);
                    PromoUsage usage;

                    if (target != null) {
                        usage = service.getPlayerUsage(target.getUniqueId());
                    } else {
                        // Если не онлайн, ищем по имени
                        usage = service.getPlayerUsageByName(targetName);
                    }

                    // Отправляем ответ на главном потоке
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (usage == null) {
                                sender.sendMessage(MessageUtil.colorize(plugin.getConfig().getString("messages.prefix") + "&cИгрок &f" + targetName + " &cне использовал промокод."));
                                return;
                            }

                            sender.sendMessage(MessageUtil.colorize("&8&m                                                    "));
                            sender.sendMessage(MessageUtil.colorize("&6&lИнформация о промокоде игрока &f" + usage.getPlayerName()));
                            sender.sendMessage("");
                            sender.sendMessage(MessageUtil.colorize("  &7Промокод: &e" + usage.getCode()));
                            sender.sendMessage(MessageUtil.colorize("  &7Дата использования: &f" + TimeUtil.formatDate(usage.getUsedAt())));
                            sender.sendMessage(MessageUtil.colorize("  &7Первый заход: &f" + TimeUtil.formatDate(usage.getFirstJoinAt())));
                            sender.sendMessage(MessageUtil.colorize("&8&m                                                    "));
                        }
                    }.runTask(plugin);

                } catch (Exception e) {
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            sender.sendMessage(MessageUtil.colorize(plugin.getConfig().getString("messages.prefix") + "&cОшибка при проверке данных: " + e.getMessage()));
                        }
                    }.runTask(plugin);
                }
            }
        }.runTaskAsynchronously(plugin);

        return true;
    }
}

