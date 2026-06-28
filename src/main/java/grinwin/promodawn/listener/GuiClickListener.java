package grinwin.promodawn.listener;

import grinwin.promodawn.PromoDawn;
import grinwin.promodawn.gui.MyPromoRootHolder;
import grinwin.promodawn.gui.PartnerDonationsHolder;
import grinwin.promodawn.gui.YoutuberDetailHolder;
import grinwin.promodawn.gui.YoutubersListHolder;
import grinwin.promodawn.model.MyPromoMenuData;
import grinwin.promodawn.model.PartnerFinanceSummary;
import grinwin.promodawn.model.PartnerTransaction;
import grinwin.promodawn.model.StatsSnapshot;
import grinwin.promodawn.service.PartnerFinanceService;
import grinwin.promodawn.service.PromoService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;

public class GuiClickListener implements Listener {

    private final PromoDawn plugin;
    private final PromoService service;
    private final PartnerFinanceService partnerFinanceService;

    public GuiClickListener(PromoDawn plugin, PromoService service, PartnerFinanceService partnerFinanceService) {
        this.plugin = plugin;
        this.service = service;
        this.partnerFinanceService = partnerFinanceService;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory inv = event.getInventory();
        InventoryHolder holder = inv.getHolder();
        if (!(holder instanceof YoutubersListHolder) && !(holder instanceof YoutuberDetailHolder)
                && !(holder instanceof MyPromoRootHolder) && !(holder instanceof PartnerDonationsHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();

        if (holder instanceof YoutubersListHolder) {
            handleListClick(event, player);
            return;
        }
        if (holder instanceof MyPromoRootHolder) {
            handleMyPromoRootClick(event, player, (MyPromoRootHolder) holder);
            return;
        }
        if (holder instanceof YoutuberDetailHolder) {
            handleDetailClick(event, player, (YoutuberDetailHolder) holder);
            return;
        }
        if (holder instanceof PartnerDonationsHolder) {
            handlePartnerDonationsClick(event, player, (PartnerDonationsHolder) holder);
        }
    }

    private void handleListClick(InventoryClickEvent event, Player player) {
        if (event.getCurrentItem() == null || !event.getCurrentItem().hasItemMeta()) {
            return;
        }
        String display = event.getCurrentItem().getItemMeta().getDisplayName();
        String ownerName = org.bukkit.ChatColor.stripColor(display);
        service.findCodeByOwner(ownerName).ifPresent(code -> new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                try {
                    MyPromoMenuData data = partnerFinanceService.loadMyPromoMenuData(code, 0, 45, 0, 36);
                    new org.bukkit.scheduler.BukkitRunnable() {
                        @Override
                        public void run() {
                            player.openInventory(new grinwin.promodawn.gui.GuiBuilder(plugin, service, partnerFinanceService)
                                    .buildMyPromoRoot(data));
                        }
                    }.runTask(plugin);
                } catch (Exception ignored) {
                }
            }
        }.runTaskAsynchronously(plugin));
    }

    private void handleMyPromoRootClick(InventoryClickEvent event, Player player, MyPromoRootHolder holder) {
        if (event.getCurrentItem() == null || !event.getCurrentItem().hasItemMeta()) {
            return;
        }
        String name = org.bukkit.ChatColor.stripColor(event.getCurrentItem().getItemMeta().getDisplayName());
        service.findCode(holder.getCode()).ifPresent(code -> {
            if (name.equalsIgnoreCase("Игроки, прописавшие промокод")) {
                new org.bukkit.scheduler.BukkitRunnable() {
                    @Override
                    public void run() {
                        try {
                            StatsSnapshot snap = service.loadStatsPage(code.getCode(), 0, 45);
                            new org.bukkit.scheduler.BukkitRunnable() {
                                @Override
                                public void run() {
                                    player.openInventory(new grinwin.promodawn.gui.GuiBuilder(plugin, service,
                                            partnerFinanceService).buildYoutuberDetail(code, 0, snap));
                                }
                            }.runTask(plugin);
                        } catch (Exception ignored) {
                        }
                    }
                }.runTaskAsynchronously(plugin);
            }
            if (name.equalsIgnoreCase("Игроки, задонатившие на сервер")) {
                new org.bukkit.scheduler.BukkitRunnable() {
                    @Override
                    public void run() {
                        try {
                            PartnerFinanceSummary summary = partnerFinanceService.getFinanceSummary(code.getOwner());
                            List<PartnerTransaction> transactions = partnerFinanceService.listTransactions(code.getOwner(),
                                    36, 0);
                            new org.bukkit.scheduler.BukkitRunnable() {
                                @Override
                                public void run() {
                                    player.openInventory(new grinwin.promodawn.gui.GuiBuilder(plugin, service,
                                            partnerFinanceService).buildPartnerDonationsMenu(code, 0, summary,
                                                    transactions));
                                }
                            }.runTask(plugin);
                        } catch (Exception ignored) {
                        }
                    }
                }.runTaskAsynchronously(plugin);
            }
        });
    }

    private void handleDetailClick(InventoryClickEvent event, Player player, YoutuberDetailHolder holder) {
        if (event.getCurrentItem() == null || !event.getCurrentItem().hasItemMeta()) {
            return;
        }
        String name = org.bukkit.ChatColor.stripColor(event.getCurrentItem().getItemMeta().getDisplayName());
        int page = holder.getPage();
        if (!"Далее".equalsIgnoreCase(name) && !"Назад".equalsIgnoreCase(name)) {
            return;
        }
        int nextPage = page + ("Далее".equalsIgnoreCase(name) ? 1 : -1);
        if (nextPage < 0) {
            return;
        }
        service.findCode(holder.getCode()).ifPresent(code -> new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                try {
                    StatsSnapshot snap = service.loadStatsPage(code.getCode(), nextPage, 45);
                    new org.bukkit.scheduler.BukkitRunnable() {
                        @Override
                        public void run() {
                            player.openInventory(new grinwin.promodawn.gui.GuiBuilder(plugin, service,
                                    partnerFinanceService).buildYoutuberDetail(code, nextPage, snap));
                        }
                    }.runTask(plugin);
                } catch (Exception ignored) {
                }
            }
        }.runTaskAsynchronously(plugin));
    }

    private void handlePartnerDonationsClick(InventoryClickEvent event, Player player, PartnerDonationsHolder holder) {
        if (event.getCurrentItem() == null || !event.getCurrentItem().hasItemMeta()) {
            return;
        }
        String name = org.bukkit.ChatColor.stripColor(event.getCurrentItem().getItemMeta().getDisplayName());
        int page = holder.getPage();
        service.findCode(holder.getCode()).ifPresent(code -> {
            if ("Назад в меню".equalsIgnoreCase(name)) {
                new org.bukkit.scheduler.BukkitRunnable() {
                    @Override
                    public void run() {
                        try {
                            MyPromoMenuData data = partnerFinanceService.loadMyPromoMenuData(code, 0, 45, 0, 36);
                            new org.bukkit.scheduler.BukkitRunnable() {
                                @Override
                                public void run() {
                                    player.openInventory(new grinwin.promodawn.gui.GuiBuilder(plugin, service,
                                            partnerFinanceService).buildMyPromoRoot(data));
                                }
                            }.runTask(plugin);
                        } catch (Exception ignored) {
                        }
                    }
                }.runTaskAsynchronously(plugin);
            }
            if ("Далее".equalsIgnoreCase(name) || "Назад".equalsIgnoreCase(name)) {
                int nextPage = page + ("Далее".equalsIgnoreCase(name) ? 1 : -1);
                if (nextPage < 0) {
                    return;
                }
                new org.bukkit.scheduler.BukkitRunnable() {
                    @Override
                    public void run() {
                        try {
                            PartnerFinanceSummary summary = partnerFinanceService.getFinanceSummary(code.getOwner());
                            List<PartnerTransaction> transactions = partnerFinanceService.listTransactions(code.getOwner(),
                                    36, nextPage * 36);
                            new org.bukkit.scheduler.BukkitRunnable() {
                                @Override
                                public void run() {
                                    player.openInventory(new grinwin.promodawn.gui.GuiBuilder(plugin, service,
                                            partnerFinanceService).buildPartnerDonationsMenu(code, nextPage, summary,
                                                    transactions));
                                }
                            }.runTask(plugin);
                        } catch (Exception ignored) {
                        }
                    }
                }.runTaskAsynchronously(plugin);
            }
        });
    }
}
