package grinwin.promodawn;

import org.bukkit.plugin.java.JavaPlugin;
import grinwin.promodawn.db.DatabaseManager;
import grinwin.promodawn.service.PartnerFinanceService;
import grinwin.promodawn.service.PromoService;
import org.bukkit.configuration.file.FileConfiguration;

public final class PromoDawn extends JavaPlugin {

    private DatabaseManager databaseManager;
    private PromoService promoService;
    private PartnerFinanceService partnerFinanceService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            reloadConfig();
        } catch (Exception e) {
            getLogger().severe("config.yml поврежден: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        FileConfiguration cfg = getConfig();
        try {
            org.bukkit.configuration.ConfigurationSection mysqlSec = cfg.getConfigurationSection("mysql");
            if (mysqlSec == null) {
                getLogger().severe("Config error: missing 'mysql' section. Check config.yml");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
            this.databaseManager = new DatabaseManager(mysqlSec);
            this.databaseManager.initSchema();
            this.promoService = new PromoService(databaseManager);
            this.promoService.loadFromConfig(cfg.getConfigurationSection("promo-codes"),
                    cfg.getConfigurationSection("rewards"));
            this.partnerFinanceService = new PartnerFinanceService(this, databaseManager, promoService);
            // register commands
            grinwin.promodawn.command.PromoCommand promoCmd = new grinwin.promodawn.command.PromoCommand(this,
                    promoService);
            getCommand("promo").setExecutor(promoCmd);
            getCommand("promo").setTabCompleter(promoCmd);
            getCommand("promoreload").setExecutor(new grinwin.promodawn.command.PromoReloadCommand(this));
            getCommand("mypromo").setExecutor(new grinwin.promodawn.command.MyPromoCommand(this, promoService, partnerFinanceService));
            getCommand("promos").setExecutor(new grinwin.promodawn.command.PromosCommand(this));
            getCommand("promocheck").setExecutor(new grinwin.promodawn.command.PromoCheckCommand(this, promoService));
            getCommand("promoadd").setExecutor(new grinwin.promodawn.command.PromoAddCommand(this, promoService));
            grinwin.promodawn.command.PartnerCommand partnerCommand = new grinwin.promodawn.command.PartnerCommand(this,
                    partnerFinanceService, promoService);
            getCommand("p").setExecutor(partnerCommand);
            getCommand("p").setTabCompleter(partnerCommand);
            // listeners
            getServer().getPluginManager()
                    .registerEvents(new grinwin.promodawn.listener.GuiClickListener(this, promoService, partnerFinanceService), this);
        } catch (Exception e) {
            getLogger().severe("Failed to initialize plugin: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.close();
        }
    }

    public PromoService getPromoService() {
        return promoService;
    }

    public PartnerFinanceService getPartnerFinanceService() {
        return partnerFinanceService;
    }
}
