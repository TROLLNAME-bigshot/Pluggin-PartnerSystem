package grinwin.promodawn.command;

import grinwin.promodawn.PromoDawn;
import grinwin.promodawn.service.PromoService;
import grinwin.promodawn.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

public class PromoAddCommand implements CommandExecutor, TabCompleter {

    private final PromoDawn plugin;
    private final PromoService promoService;

    public PromoAddCommand(PromoDawn plugin, PromoService promoService) {
        this.plugin = plugin;
        this.promoService = promoService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("promodawn.admin")) {
            sender.sendMessage(MessageUtil.colorize(plugin.getConfig().getString("messages.no-permission")));
            return true;
        }

        // Usage: /promoadd <name> <reward> <uses> <message...>
        if (args.length < 4) {
            sender.sendMessage(MessageUtil
                    .colorize("&cИспользование: /promoadd <название> <награда> <использования> <сообщение...>"));
            return true;
        }

        String name = args[0];
        String reward = args[1];
        String usesStr = args[2];

        int uses;
        try {
            uses = Integer.parseInt(usesStr);
            if (uses <= 0)
                throw new NumberFormatException();
        } catch (NumberFormatException e) {
            sender.sendMessage(MessageUtil.colorize("&cКоличество использований должно быть положительным числом."));
            return true;
        }

        // Validate reward
        ConfigurationSection rewardsSec = plugin.getConfig().getConfigurationSection("rewards");
        if (rewardsSec == null || !rewardsSec.contains(reward)) {
            sender.sendMessage(MessageUtil.colorize("&cНаграда '" + reward + "' не найдена в конфиге."));
            return true;
        }

        StringBuilder messageBuilder = new StringBuilder();
        for (int i = 3; i < args.length; i++) {
            messageBuilder.append(args[i]).append(" ");
        }
        String message = messageBuilder.toString().trim();

        promoService.createPromoCode(plugin, name, reward, uses, message);
        sender.sendMessage(MessageUtil.colorize("&aПромокод &f" + name + " &aуспешно создан!"));

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("promodawn.admin"))
            return null;

        if (args.length == 1) {
            return null; // suggest nothing for name, or maybe <name>
        }
        if (args.length == 2) {
            ConfigurationSection rewards = plugin.getConfig().getConfigurationSection("rewards");
            if (rewards != null) {
                return new ArrayList<>(rewards.getKeys(false));
            }
        }
        if (args.length == 3) {
            List<String> uses = new ArrayList<>();
            uses.add("10"); // example
            uses.add("50");
            uses.add("100");
            return uses;
        }
        if (args.length == 4) {
            List<String> msg = new ArrayList<>();
            msg.add("&aВы успешно использовали промокод!");
            return msg;
        }
        return new ArrayList<>();
    }
}
