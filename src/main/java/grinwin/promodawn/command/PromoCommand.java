package grinwin.promodawn.command;

import grinwin.promodawn.PromoDawn;
import grinwin.promodawn.model.PromoCode;
import grinwin.promodawn.service.PromoService;
import grinwin.promodawn.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class PromoCommand implements CommandExecutor, TabCompleter {

    private final PromoDawn plugin;
    private final PromoService promoService;

    public PromoCommand(PromoDawn plugin, PromoService promoService) {
        this.plugin = plugin;
        this.promoService = promoService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command is only for players.");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("promodawn.promo")) {
            player.sendMessage(MessageUtil.colorize(plugin.getConfig().getString("messages.prefix")
                    + plugin.getConfig().getString("messages.no-permission")));
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(MessageUtil.colorize(plugin.getConfig().getString("messages.prefix")
                    + plugin.getConfig().getString("messages.usage-promo")));
            return true;
        }

        String codeArg = args[0].toLowerCase(Locale.ROOT);
        Optional<PromoCode> promoOpt = promoService.findCode(codeArg);
        if (!promoOpt.isPresent()) {
            player.sendMessage(MessageUtil.colorize(plugin.getConfig().getString("messages.prefix")
                    + plugin.getConfig().getString("messages.code-not-exists")));
            playError(player);
            return true;
        }
        PromoCode code = promoOpt.get();

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    // Check global limits (Max Uses for the code itself)
                    if (code.getMaxUses() > 0) {
                        int currentUses = promoService.getTotalUsageCount(code.getCode());
                        if (currentUses >= code.getMaxUses()) {
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    List<String> failMsgs = code.getFailureMessages();
                                    if (failMsgs != null && !failMsgs.isEmpty()) {
                                        for (String msg : failMsgs) {
                                            player.sendMessage(MessageUtil.colorize(msg));
                                        }
                                    } else {
                                        // Default fallback if no custom message
                                        player.sendMessage(
                                                MessageUtil.colorize(plugin.getConfig().getString("messages.prefix")
                                                        + "&cДанный промокод закончился."));
                                    }
                                    playError(player);
                                }
                            }.runTask(plugin);
                            return;
                        }
                    }

                    // Check player limits
                    boolean alreadyUsed = false;
                    if (code.isIgnoreGlobalLimit()) {
                        // For limited codes, we only check if the player used THIS specific code.
                        if (promoService.hasUsedSpecificPromo(player.getUniqueId(), code.getCode())) {
                            alreadyUsed = true;
                        }
                    } else {
                        // For standard codes, we check if player used ANY standard code.
                        if (promoService.hasUsedStandardPromo(player.getUniqueId())) {
                            alreadyUsed = true;
                        }
                    }

                    if (alreadyUsed) {
                        // back to main thread to message/sound
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                // For limited codes, maybe a specific message is better?
                                // "You already used THIS code" vs "You already used A code"
                                // Current config only has "code-already-used".
                                // We can use custom failure message if it exists (usually for max uses, but
                                // maybe for double usage too?)
                                // Or stick to standard.

                                player.sendMessage(MessageUtil.colorize(plugin.getConfig().getString("messages.prefix")
                                        + plugin.getConfig().getString("messages.code-already-used")));
                                playError(player);
                            }
                        }.runTask(plugin);
                        return;
                    }

                    long firstJoin = player.getFirstPlayed();
                    long now = System.currentTimeMillis();
                    promoService.recordUsage(player.getUniqueId(), player.getName(), code, now, firstJoin);

                    // execute reward and notify on main thread
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            executeRewardCommands(player, code.getRewardKey(), code);

                            // Send success message
                            List<String> successMsgs = code.getSuccessMessages();
                            if (successMsgs != null && !successMsgs.isEmpty()) {
                                for (String msg : successMsgs) {
                                    player.sendMessage(MessageUtil.colorize(msg));
                                }
                            } else {
                                player.sendMessage(MessageUtil
                                        .colorize(plugin.getConfig().getString("messages.prefix") + plugin.getConfig()
                                                .getString("messages.code-success").replace("%code%", code.getCode())));
                            }

                            playSuccess(player, code.getCode());

                            // Notify owner and admins
                            notifyOwnerAndAdmins(player, code);
                        }
                    }.runTask(plugin);
                } catch (SQLException ex) {
                    plugin.getLogger().severe("SQL error: " + ex.getMessage());
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            player.sendMessage(MessageUtil.colorize("&cПроизошла ошибка. Попробуйте позже."));
                        }
                    }.runTask(plugin);
                }
            }
        }.runTaskAsynchronously(plugin);

        return true;
    }

    private void executeRewardCommands(Player player, String rewardKey, PromoCode code) {
        FileConfiguration cfg = plugin.getConfig();
        List<String> commands = cfg.getStringList("rewards." + rewardKey + ".commands");
        if (commands == null || commands.isEmpty()) {
            // backward compatibility: allow list directly under reward key
            commands = cfg.getStringList("rewards." + rewardKey);
        }
        if (commands == null || commands.isEmpty()) {
            commands = cfg.getStringList("rewards.default");
            if (commands == null || commands.isEmpty()) {
                commands = cfg.getStringList("rewards.default.commands");
            }
        }
        for (String raw : commands) {
            String cmd = raw.replace("%player%", player.getName())
                    .replace("%code%", code.getCode())
                    .replace("%owner%", code.getOwner());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }

        // Optional plain messages to player (without prefix) - ONLY if custom success
        // message is NOT set?
        // Logic: if code has custom success messages, we might skip reward default
        // messages?
        // User request didn't specify. Usually reward messages are part of reward.
        // But limited codes have "message-use" which acts as success message.
        // Let's keep reward messages as they describe items, unless specific
        // instructions.
        List<String> messages = cfg.getStringList("rewards." + rewardKey + ".messages");
        if ((messages == null || messages.isEmpty())) {
            messages = cfg.getStringList("rewards.default.messages");
        }
        if (messages != null) {
            for (String line : messages) {
                if (line == null)
                    continue;
                String text = line.replace("%player%", player.getName())
                        .replace("%code%", code.getCode())
                        .replace("%owner%", code.getOwner());
                player.sendMessage(MessageUtil.colorize(text));
            }
        }
    }

    private void playSuccess(Player player, String code) {
        FileConfiguration cfg = plugin.getConfig();
        if (cfg.getBoolean("settings.useSounds", true)) {
            try {
                Sound sound = Sound.valueOf(cfg.getString("sounds.success.name", "ENTITY_PLAYER_LEVELUP"));
                float volume = (float) cfg.getDouble("sounds.success.volume", 1.0);
                float pitch = (float) cfg.getDouble("sounds.success.pitch", 1.0);
                player.playSound(player.getLocation(), sound, volume, pitch);
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (cfg.getBoolean("settings.useTitles", true)) {
            String title = MessageUtil.colorize(cfg.getString("titles.success.title", "&aУспех!"));
            String sub = MessageUtil.colorize(
                    cfg.getString("titles.success.subtitle", "Вы активировали %code%").replace("%code%", code));
            int fadeIn = cfg.getInt("titles.success.fadeIn", 5);
            int stay = cfg.getInt("titles.success.stay", 40);
            int fadeOut = cfg.getInt("titles.success.fadeOut", 10);
            player.sendTitle(title, sub, fadeIn, stay, fadeOut);
        }
    }

    private void playError(Player player) {
        FileConfiguration cfg = plugin.getConfig();
        if (cfg.getBoolean("settings.useSounds", true)) {
            try {
                Sound sound = Sound.valueOf(cfg.getString("sounds.error.name", "ENTITY_VILLAGER_NO"));
                float volume = (float) cfg.getDouble("sounds.error.volume", 1.0);
                float pitch = (float) cfg.getDouble("sounds.error.pitch", 1.0);
                player.playSound(player.getLocation(), sound, volume, pitch);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void notifyOwnerAndAdmins(Player activator, PromoCode code) {
        // Updated logic: if limited (or all?), use special format
        // User asked: "А для администрации при использовании таких промокодов должно
        // выводиться: &a(ник) &7активировал промокод &a(название промокода)"
        // Assuming "таких" means limited codes or created via new system.
        // Let's use simple check: if maxUses > 0, it's definitely limited.

        boolean isLimited = code.getMaxUses() > 0;

        if (isLimited) {
            String adminMsg = MessageUtil
                    .colorize("&a" + activator.getName() + " &7активировал промокод &a" + code.getCode());
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.isOp() || p.hasPermission("promodawn.notify")) {
                    p.sendMessage(adminMsg);
                }
            }
        } else {
            // Old logic for media/youtubers
            String ownerName = code.getOwner();
            // owner (if online)
            Player owner = Bukkit.getPlayerExact(ownerName);
            if (owner != null && owner.isOnline()) {
                owner.sendMessage(MessageUtil.colorize("&a" + activator.getName() + " &7активировал ваш промокод!"));
            }
            // admins (ops)
            String adminMsg = MessageUtil
                    .colorize("&a" + activator.getName() + " &7активировал промокод медиа &a" + ownerName);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.isOp() || p.hasPermission("promodawn.notify")) {
                    p.sendMessage(adminMsg);
                }
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> list = new ArrayList<>();
        if (args.length == 1) {
            String start = args[0];
            if (start.isEmpty()) {
                list.add("от кого вы пришли?");
            }
        }
        return list;
    }
}
