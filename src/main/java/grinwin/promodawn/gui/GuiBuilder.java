package grinwin.promodawn.gui;

import grinwin.promodawn.PromoDawn;
import grinwin.promodawn.model.PromoCode;
import grinwin.promodawn.model.PromoUsage;
import grinwin.promodawn.service.PromoService;
import grinwin.promodawn.util.MessageUtil;
import grinwin.promodawn.util.SkinCache;
import grinwin.promodawn.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;

public class GuiBuilder {
    private final PromoDawn plugin;
    private final PromoService service;

    public GuiBuilder(PromoDawn plugin, PromoService service) {
        this.plugin = plugin;
        this.service = service;
    }

    public Inventory buildYoutubersList(Map<String, Integer> totals) {
        String title = MessageUtil.colorize(plugin.getConfig().getString("gui.promos.title", "&8Промокоды ютуберов"));
        Inventory inv = Bukkit.createInventory(new YoutubersListHolder(), 54, title);

        boolean usePlayerHeads = plugin.getConfig().getBoolean("settings.usePlayerHeads", false);

        int index = 0;
        for (Map.Entry<String, PromoCode> entry : service.getAllCodes().entrySet()) {
            if (index >= inv.getSize())
                break;
            PromoCode pc = entry.getValue();

            // Only show standard (non-limited) codes in the list
            if (pc.isIgnoreGlobalLimit()) {
                continue;
            }

            ItemStack skull;
            if (usePlayerHeads) {
                skull = SkinCache.getPlayerHead(pc.getOwner());
            } else {
                skull = new ItemStack(Material.SKELETON_SKULL);
            }

            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            meta.setDisplayName(MessageUtil.colorize("&e" + pc.getOwner()));
            List<String> lore = new ArrayList<>();
            int total = totals.getOrDefault(pc.getCode().toLowerCase(Locale.ROOT), 0);
            lore.add(MessageUtil.colorize("&7Всего использований: &f" + total));
            lore.add(MessageUtil.colorize("&7Добавлен: &f" + TimeUtil.formatDate(pc.getCreatedAt() * 1000))); // created-at
                                                                                                              // в
                                                                                                              // секундах
            meta.setLore(lore);
            skull.setItemMeta(meta);
            inv.setItem(index++, skull);
        }
        return inv;
    }

    public Inventory buildYoutuberDetail(PromoCode code, int page, grinwin.promodawn.model.StatsSnapshot snapshot) {
        String title = MessageUtil.colorize("&8Промо: &e" + code.getOwner() + " &7(стр. " + (page + 1) + ")");
        Inventory inv = Bukkit.createInventory(new YoutuberDetailHolder(code.getCode(), page), 54, title);

        boolean usePlayerHeads = plugin.getConfig().getBoolean("settings.usePlayerHeads", false);

        // Центральная бумага с метриками
        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta pm = paper.getItemMeta();
        pm.setDisplayName(MessageUtil.colorize("&eСтатистика"));
        List<String> lore = new ArrayList<>();
        lore.add(MessageUtil.colorize("&7За день: &f" + snapshot.getDayCount()));
        lore.add(MessageUtil.colorize("&7За неделю: &f" + snapshot.getWeekCount()));
        lore.add(MessageUtil.colorize("&7За всё время: &f" + snapshot.getTotalCount()));
        Integer month = snapshot.getMonthCount();
        if (month != null) {
            lore.add(MessageUtil.colorize("&7За месяц: &f" + month));
        }
        pm.setLore(lore);
        pm.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        paper.setItemMeta(pm);
        inv.setItem(4, paper);

        // Участники
        List<PromoUsage> usages = snapshot.getUsagesPage();
        int slot = 9;
        for (PromoUsage u : usages) {
            if (slot >= inv.getSize())
                break;

            ItemStack head;
            if (usePlayerHeads) {
                head = SkinCache.getPlayerHead(u.getPlayerName());
            } else {
                head = new ItemStack(Material.CREEPER_HEAD);
            }

            SkullMeta sm = (SkullMeta) head.getItemMeta();
            sm.setDisplayName(MessageUtil.colorize("&e" + u.getPlayerName()));
            List<String> lore2 = new ArrayList<>();
            lore2.add(MessageUtil.colorize("&7Использовал: &f" + TimeUtil.formatDate(u.getUsedAt())));
            lore2.add(MessageUtil.colorize("&7Первый вход: &f" + TimeUtil.formatDate(u.getFirstJoinAt())));
            // PlaceholderAPI playtime (hours) if present: %statistic_hours_played%
            OfflinePlayer ofp = Bukkit.getOfflinePlayer(u.getPlayerUuid());
            String pt = grinwin.promodawn.util.PlaceholderUtil.resolvePlaceholders(ofp, "%statistic_hours_played%");
            if (pt != null && !pt.equals("%statistic_hours_played%")) {
                lore2.add(MessageUtil.colorize("&7Наиграл в этом вайпе: &f" + pt));
            }
            sm.setLore(lore2);
            head.setItemMeta(sm);
            inv.setItem(slot++, head);
        }

        // Навигация
        ItemStack next = new ItemStack(Material.ARROW);
        ItemMeta nm = next.getItemMeta();
        nm.setDisplayName(MessageUtil.colorize("&aДалее"));
        next.setItemMeta(nm);
        inv.setItem(53, next);

        if (page > 0) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta pm2 = prev.getItemMeta();
            pm2.setDisplayName(MessageUtil.colorize("&cНазад"));
            prev.setItemMeta(pm2);
            inv.setItem(45, prev);
        }

        return inv;
    }
}
