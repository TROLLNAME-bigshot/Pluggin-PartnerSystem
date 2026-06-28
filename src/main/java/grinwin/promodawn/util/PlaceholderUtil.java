package grinwin.promodawn.util;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

public class PlaceholderUtil {

    public static boolean isPapiPresent() {
        return Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
    }

    public static String resolvePlaceholders(OfflinePlayer player, String text) {
        if (text == null) return "";
        if (!isPapiPresent()) return text;
        try {
            Class<?> clazz = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            java.lang.reflect.Method m = clazz.getMethod("setPlaceholders", OfflinePlayer.class, String.class);
            Object out = m.invoke(null, player, text);
            return out instanceof String ? (String) out : text;
        } catch (Throwable t) {
            return text;
        }
    }
}



