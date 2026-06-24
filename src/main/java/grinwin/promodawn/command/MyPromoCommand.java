package grinwin.promodawn.command;

import grinwin.promodawn.PromoDawn;
import grinwin.promodawn.gui.GuiBuilder;
import grinwin.promodawn.model.PromoCode;
import grinwin.promodawn.service.PromoService;
import grinwin.promodawn.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Optional;

public class MyPromoCommand implements CommandExecutor {

    private final PromoDawn plugin;
    private final PromoService service;

    public MyPromoCommand(PromoDawn plugin, PromoService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command is only for players.");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("promodawn.mypromo")) {
            player.sendMessage(MessageUtil.colorize(plugin.getConfig().getString("messages.prefix") + plugin.getConfig().getString("messages.no-permission")));
            return true;
        }
        Optional<PromoCode> code = service.findCodeByOwner(player.getName());
        if (!code.isPresent()) {
            player.sendMessage(MessageUtil.colorize("&cЗа вами не закреплен промокод."));
            return true;
        }
        PromoCode pc = code.get();
        // Загрузим статистику асинхронно, а открыть меню и отрисовать — на главном потоке
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                try {
                    grinwin.promodawn.model.StatsSnapshot snap = service.loadStatsPage(pc.getCode(), 0, 45);
                    new org.bukkit.scheduler.BukkitRunnable() {
                        @Override
                        public void run() {
                            GuiBuilder builder = new GuiBuilder(plugin, service);
                            player.openInventory(builder.buildYoutuberDetail(pc, 0, snap));
                        }
                    }.runTask(plugin);
                } catch (Exception e) {
                    new org.bukkit.scheduler.BukkitRunnable() {
                        @Override
                        public void run() {
                            player.sendMessage(MessageUtil.colorize("&cОшибка открытия меню: " + e.getMessage()));
                        }
                    }.runTask(plugin);
                }
            }
        }.runTaskAsynchronously(plugin);
        return true;
    }
}




