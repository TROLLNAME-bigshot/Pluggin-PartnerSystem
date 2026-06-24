package grinwin.promodawn.listener;

import grinwin.promodawn.PromoDawn;
import grinwin.promodawn.gui.YoutuberDetailHolder;
import grinwin.promodawn.gui.YoutubersListHolder;
import grinwin.promodawn.service.PromoService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class GuiClickListener implements Listener {

    private final PromoDawn plugin;
    private final PromoService service;

    public GuiClickListener(PromoDawn plugin, PromoService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory inv = event.getInventory();
        InventoryHolder holder = inv.getHolder();
        if (!(holder instanceof YoutubersListHolder) && !(holder instanceof YoutuberDetailHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        if (holder instanceof YoutubersListHolder) {
            if (event.getCurrentItem() == null || !event.getCurrentItem().hasItemMeta()) return;
            String display = event.getCurrentItem().getItemMeta().getDisplayName();
            String ownerName = org.bukkit.ChatColor.stripColor(display);
            service.findCodeByOwner(ownerName).ifPresent(code -> {
                new org.bukkit.scheduler.BukkitRunnable() {
                    @Override
                    public void run() {
                        try {
                            grinwin.promodawn.model.StatsSnapshot snap = service.loadStatsPage(code.getCode(), 0, 45);
                            new org.bukkit.scheduler.BukkitRunnable() {
                                @Override
                                public void run() {
                                    player.openInventory(new grinwin.promodawn.gui.GuiBuilder(plugin, service).buildYoutuberDetail(code, 0, snap));
                                }
                            }.runTask(plugin);
                        } catch (Exception ignored) { }
                    }
                }.runTaskAsynchronously(plugin);
            });
        }

        if (holder instanceof YoutuberDetailHolder) {
            if (event.getCurrentItem() == null || !event.getCurrentItem().hasItemMeta()) return;
            YoutuberDetailHolder h = (YoutuberDetailHolder) holder;
            String name = org.bukkit.ChatColor.stripColor(event.getCurrentItem().getItemMeta().getDisplayName());
            int page = h.getPage();
            if ("Далее".equalsIgnoreCase(name) || "Назад".equalsIgnoreCase(name)) {
                int nextPage = page + ("Далее".equalsIgnoreCase(name) ? 1 : -1);
                if (nextPage < 0) return;
                service.findCode(h.getCode()).ifPresent(code -> {
                    new org.bukkit.scheduler.BukkitRunnable() {
                        @Override
                        public void run() {
                            try {
                                grinwin.promodawn.model.StatsSnapshot snap = service.loadStatsPage(code.getCode(), nextPage, 45);
                                new org.bukkit.scheduler.BukkitRunnable() {
                                    @Override
                                    public void run() {
                                        player.openInventory(new grinwin.promodawn.gui.GuiBuilder(plugin, service).buildYoutuberDetail(code, nextPage, snap));
                                    }
                                }.runTask(plugin);
                            } catch (Exception ignored) { }
                        }
                    }.runTaskAsynchronously(plugin);
                });
            }
        }
    }
}


