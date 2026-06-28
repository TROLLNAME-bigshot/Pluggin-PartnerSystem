package grinwin.promodawn.command;

import grinwin.promodawn.PromoDawn;
import grinwin.promodawn.gui.GuiBuilder;
import grinwin.promodawn.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PromosCommand implements CommandExecutor {

    private final PromoDawn plugin;

    public PromosCommand(PromoDawn plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command is only for players.");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("promodawn.promos")) {
            player.sendMessage(MessageUtil.colorize(plugin.getConfig().getString("messages.prefix") + plugin.getConfig().getString("messages.no-permission")));
            return true;
        }
        // Загрузить агрегированную статистику асинхронно, затем открыть меню на главном потоке
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                java.util.Map<String, Integer> totals;
                try {
                    totals = plugin.getPromoService().getTotalUsageCountsForAllCodes();
                } catch (Exception e) {
                    totals = java.util.Collections.emptyMap();
                }
                java.util.Map<String, Integer> finalTotals = totals;
                new org.bukkit.scheduler.BukkitRunnable() {
                    @Override
                    public void run() {
                        GuiBuilder builder = new GuiBuilder(plugin, plugin.getPromoService(), plugin.getPartnerFinanceService());
                        player.openInventory(builder.buildYoutubersList(finalTotals));
                    }
                }.runTask(plugin);
            }
        }.runTaskAsynchronously(plugin);
        return true;
    }
}




