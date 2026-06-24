package grinwin.promodawn.command;

import grinwin.promodawn.PromoDawn;
import grinwin.promodawn.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class PromoReloadCommand implements CommandExecutor {

    private final PromoDawn plugin;

    public PromoReloadCommand(PromoDawn plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("promodawn.reload")) {
            sender.sendMessage(MessageUtil.colorize(plugin.getConfig().getString("messages.prefix")
                    + plugin.getConfig().getString("messages.no-permission")));
            return true;
        }
        try {
            plugin.reloadConfig();
            plugin.getPromoService().loadFromConfig(plugin.getConfig().getConfigurationSection("promo-codes"),
                    plugin.getConfig().getConfigurationSection("rewards"));
            // Очищаем кеш скинов при перезагрузке
            grinwin.promodawn.util.SkinCache.clearCache();
            sender.sendMessage(MessageUtil.colorize(plugin.getConfig().getString("messages.prefix")
                    + plugin.getConfig().getString("messages.reload-success")));
        } catch (Exception e) {
            sender.sendMessage(MessageUtil.colorize("&cОшибка перезагрузки config.yml: " + e.getMessage()));
            sender.sendMessage(
                    MessageUtil.colorize("&7Проверьте отступы (2 пробела), отсутствие табов и лишних символов."));
        }
        return true;
    }
}
