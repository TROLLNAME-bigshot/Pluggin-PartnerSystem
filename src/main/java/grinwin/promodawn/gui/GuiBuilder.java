package grinwin.promodawn.gui;

import grinwin.promodawn.PromoDawn;
import grinwin.promodawn.model.MyPromoMenuData;
import grinwin.promodawn.model.PartnerFinanceSummary;
import grinwin.promodawn.model.PartnerTransaction;
import grinwin.promodawn.model.PromoCode;
import grinwin.promodawn.model.PromoUsage;
import grinwin.promodawn.model.StatsSnapshot;
import grinwin.promodawn.service.PartnerFinanceService;
import grinwin.promodawn.service.PromoService;
import grinwin.promodawn.util.MessageUtil;
import grinwin.promodawn.util.SkinCache;
import grinwin.promodawn.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class GuiBuilder {
    private final PromoDawn plugin;
    private final PromoService service;
    private final PartnerFinanceService partnerFinanceService;

    public GuiBuilder(PromoDawn plugin, PromoService service, PartnerFinanceService partnerFinanceService) {
        this.plugin = plugin;
        this.service = service;
        this.partnerFinanceService = partnerFinanceService;
    }

    public Inventory buildMyPromoRoot(MyPromoMenuData data) {
        String title = MessageUtil.colorize(plugin.getConfig().getString("gui.mypromo.title", "&8Мой промокод"));
        Inventory inv = Bukkit.createInventory(new MyPromoRootHolder(data.getCode().getCode()), 9, title);

        ItemStack usagesButton = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta usagesMeta = usagesButton.getItemMeta();
        usagesMeta.setDisplayName(MessageUtil.colorize("&eИгроки, прописавшие промокод"));
        List<String> usagesLore = new ArrayList<>();
        usagesLore.add(MessageUtil.colorize("&7Открыть привычное меню статистики"));
        usagesMeta.setLore(usagesLore);
        usagesButton.setItemMeta(usagesMeta);
        inv.setItem(2, usagesButton);

        ItemStack donationsButton = new ItemStack(Material.EMERALD);
        ItemMeta donationsMeta = donationsButton.getItemMeta();
        donationsMeta.setDisplayName(MessageUtil.colorize("&aИгроки, задонатившие на сервер"));
        List<String> donationsLore = new ArrayList<>();
        donationsLore.add(MessageUtil.colorize("&7Открыть статистику заработка партнера"));
        donationsMeta.setLore(donationsLore);
        donationsButton.setItemMeta(donationsMeta);
        inv.setItem(6, donationsButton);
        return inv;
    }

    public Inventory buildYoutubersList(Map<String, Integer> totals) {
        String title = MessageUtil.colorize(plugin.getConfig().getString("gui.promos.title", "&8Промокоды ютуберов"));
        Inventory inv = Bukkit.createInventory(new YoutubersListHolder(), 54, title);

        boolean usePlayerHeads = plugin.getConfig().getBoolean("settings.usePlayerHeads", false);

        int index = 0;
        for (Map.Entry<String, PromoCode> entry : service.getAllCodes().entrySet()) {
            if (index >= inv.getSize()) {
                break;
            }
            PromoCode pc = entry.getValue();

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
            lore.add(MessageUtil.colorize("&7Добавлен: &f" + TimeUtil.formatDate(pc.getCreatedAt() * 1000)));
            meta.setLore(lore);
            skull.setItemMeta(meta);
            inv.setItem(index++, skull);
        }
        return inv;
    }

    public Inventory buildYoutuberDetail(PromoCode code, int page, StatsSnapshot snapshot) {
        String title = MessageUtil.colorize("&8Промо: &e" + code.getOwner() + " &7(стр. " + (page + 1) + ")");
        Inventory inv = Bukkit.createInventory(new YoutuberDetailHolder(code.getCode(), page), 54, title);

        boolean usePlayerHeads = plugin.getConfig().getBoolean("settings.usePlayerHeads", false);

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

        List<PromoUsage> usages = snapshot.getUsagesPage();
        int slot = 9;
        for (PromoUsage u : usages) {
            if (slot >= inv.getSize()) {
                break;
            }

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
            OfflinePlayer ofp = Bukkit.getOfflinePlayer(u.getPlayerUuid());
            String pt = grinwin.promodawn.util.PlaceholderUtil.resolvePlaceholders(ofp, "%statistic_hours_played%");
            if (pt != null && !pt.equals("%statistic_hours_played%")) {
                lore2.add(MessageUtil.colorize("&7Наиграл в этом вайпе: &f" + pt));
            }
            sm.setLore(lore2);
            head.setItemMeta(sm);
            inv.setItem(slot++, head);
        }

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

    public Inventory buildPartnerDonationsMenu(PromoCode code, int page, PartnerFinanceSummary summary,
            List<PartnerTransaction> transactions) {
        String title = MessageUtil.colorize("&8Донаты: &e" + code.getOwner() + " &7(стр. " + (page + 1) + ")");
        Inventory inv = Bukkit.createInventory(new PartnerDonationsHolder(code.getCode(), page), 54, title);

        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta pm = paper.getItemMeta();
        pm.setDisplayName(MessageUtil.colorize("&eСтатистика заработка"));
        List<String> lore = new ArrayList<>();
        lore.add(MessageUtil.colorize("&7За день: &f" + PartnerFinanceService.formatMoney(summary.getDayEarned())));
        lore.add(MessageUtil.colorize("&7За неделю: &f" + PartnerFinanceService.formatMoney(summary.getWeekEarned())));
        lore.add(MessageUtil.colorize("&7За месяц: &f" + PartnerFinanceService.formatMoney(summary.getMonthEarned())));
        lore.add(MessageUtil.colorize("&7За всё время: &f" + PartnerFinanceService.formatMoney(summary.getTotalEarned())));
        pm.setLore(lore);
        paper.setItemMeta(pm);
        inv.setItem(4, paper);

        ItemStack salary = new ItemStack(Material.GOLD_INGOT);
        ItemMeta salaryMeta = salary.getItemMeta();
        salaryMeta.setDisplayName(MessageUtil.colorize("&eЗарплата"));
        List<String> salaryLore = new ArrayList<>();
        salaryLore.add(MessageUtil.colorize("&7Вам должны выплатить: &f"
                + PartnerFinanceService.formatMoney(summary.getUnpaidAmount())));
        salaryLore.add(MessageUtil.colorize("&7Всего было выплачено: &f"
                + PartnerFinanceService.formatMoney(summary.getTotalPaid())));
        salaryMeta.setLore(salaryLore);
        salary.setItemMeta(salaryMeta);
        inv.setItem(8, salary);

        boolean usePlayerHeads = plugin.getConfig().getBoolean("settings.usePlayerHeads", false);
        int slot = 9;
        for (PartnerTransaction transaction : transactions) {
            if (slot >= inv.getSize()) {
                break;
            }

            ItemStack head;
            if (usePlayerHeads) {
                head = SkinCache.getPlayerHead(transaction.getPlayerName());
            } else {
                head = new ItemStack(Material.CREEPER_HEAD);
            }

            SkullMeta sm = (SkullMeta) head.getItemMeta();
            sm.setDisplayName(MessageUtil.colorize("&e" + transaction.getPlayerName()));
            List<String> headLore = new ArrayList<>();
            headLore.add(MessageUtil.colorize("&7Задонатил: &f" + TimeUtil.formatDate(transaction.getCreatedAt())));
            headLore.add(MessageUtil.colorize("&7Сумма доната: &f"
                    + PartnerFinanceService.formatMoney(transaction.getAmount())));
            headLore.add(MessageUtil.colorize("&7Вы с этого получили: &f"
                    + PartnerFinanceService.formatMoney(transaction.getPartnerEarned())));
            sm.setLore(headLore);
            head.setItemMeta(sm);
            inv.setItem(slot++, head);
        }

        ItemStack next = new ItemStack(Material.ARROW);
        ItemMeta nextMeta = next.getItemMeta();
        nextMeta.setDisplayName(MessageUtil.colorize("&aДалее"));
        next.setItemMeta(nextMeta);
        inv.setItem(53, next);

        if (page > 0) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prev.getItemMeta();
            prevMeta.setDisplayName(MessageUtil.colorize("&cНазад"));
            prev.setItemMeta(prevMeta);
            inv.setItem(45, prev);
        }

        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName(MessageUtil.colorize("&cНазад в меню"));
        back.setItemMeta(backMeta);
        inv.setItem(49, back);
        return inv;
    }
}
