package grinwin.promodawn.util;

import org.bukkit.ChatColor;

public class MessageUtil {
    public static String colorize(String input) {
        return ChatColor.translateAlternateColorCodes('&', input == null ? "" : input);
    }
}




