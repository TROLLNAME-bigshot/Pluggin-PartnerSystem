package grinwin.promodawn.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class SkinCache {

    private static final Map<String, CachedHead> cachedHeads = new ConcurrentHashMap<>();
    private static final long CACHE_DURATION = TimeUnit.HOURS.toMillis(1); // Кеш на 1 час
    private static Object skinsRestorerAPI = null;
    private static boolean skinsRestorerChecked = false;

    private static class CachedHead {
        final ItemStack head;
        final long timestamp;

        CachedHead(ItemStack head) {
            this.head = head;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_DURATION;
        }
    }

    /**
     * Создаёт голову игрока используя SkinsRestorer API
     * БЕЗ HTTP-запросов к Mojang! Все скины локальные.
     */
    public static ItemStack getPlayerHead(String playerName) {
        String key = playerName.toLowerCase();
        
        // Проверяем кеш
        CachedHead cached = cachedHeads.get(key);
        if (cached != null && !cached.isExpired()) {
            return cached.head.clone();
        }

        // Создаём голову через SkinsRestorer
        ItemStack head = createHeadWithSkinsRestorer(playerName);
        
        // Сохраняем в кеш
        cachedHeads.put(key, new CachedHead(head));
        
        // Автоочистка при превышении лимита
        if (cachedHeads.size() > 1000) {
            cleanExpiredCache();
        }

        return head.clone();
    }

    /**
     * Создание головы через SkinsRestorer API (локально, без сети)
     */
    private static ItemStack createHeadWithSkinsRestorer(String playerName) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();

        try {
            // Инициализируем SkinsRestorer API при первом вызове
            if (!skinsRestorerChecked) {
                initializeSkinsRestorer();
                skinsRestorerChecked = true;
            }

            if (skinsRestorerAPI != null) {
                // Получаем скин через SkinsRestorer (ЛОКАЛЬНО, БЕЗ СЕТИ!)
                Object skinData = getSkinFromSkinsRestorer(playerName);
                
                if (skinData != null) {
                    // Применяем текстуру напрямую к голове
                    applyTextureToHead(meta, skinData);
                    head.setItemMeta(meta);
                    return head;
                } else {
                    // Попытка через прямой запрос к таблице (последний fallback)
                    Bukkit.getLogger().info("[PromoDawn] API не вернуло скин для " + playerName + ", игнорируем (используем пустую голову)");
                }
            }
            
        } catch (Exception e) {
            Bukkit.getLogger().warning("[PromoDawn] Ошибка загрузки скина через SkinsRestorer для " + playerName + ": " + e.getMessage());
            e.printStackTrace();
        }

        // Fallback: стандартная голова без текстуры
        head.setItemMeta(meta);
        return head;
    }

    /**
     * Инициализация SkinsRestorer API
     */
    private static void initializeSkinsRestorer() {
        try {
            if (Bukkit.getPluginManager().getPlugin("SkinsRestorer") == null) {
                Bukkit.getLogger().warning("[PromoDawn] SkinsRestorer не найден! Головы игроков будут пустыми.");
                return;
            }

            Bukkit.getLogger().info("[PromoDawn] Обнаружен SkinsRestorer, инициализируем API...");

            // SkinsRestorer v14+ API
            Class<?> apiClass = Class.forName("net.skinsrestorer.api.SkinsRestorerAPI");
            skinsRestorerAPI = apiClass.getMethod("getApi").invoke(null);
            
            if (skinsRestorerAPI != null) {
                Bukkit.getLogger().info("[PromoDawn] SkinsRestorer API успешно инициализирован!");
                Bukkit.getLogger().info("[PromoDawn] API класс: " + skinsRestorerAPI.getClass().getName());
                
                // Логируем доступные методы для отладки
                java.lang.reflect.Method[] methods = skinsRestorerAPI.getClass().getMethods();
                StringBuilder methodList = new StringBuilder("[PromoDawn] Доступные методы SR API: ");
                for (java.lang.reflect.Method m : methods) {
                    if (m.getName().toLowerCase().contains("skin") || m.getName().toLowerCase().contains("player")) {
                        methodList.append(m.getName()).append("(").append(m.getParameterCount()).append(" params), ");
                    }
                }
                Bukkit.getLogger().info(methodList.toString());
            } else {
                Bukkit.getLogger().warning("[PromoDawn] SkinsRestorer API вернул null!");
            }
            
        } catch (Exception e) {
            Bukkit.getLogger().severe("[PromoDawn] Не удалось инициализировать SkinsRestorer API: " + e.getMessage());
            e.printStackTrace();
            skinsRestorerAPI = null;
        }
    }

    /**
     * Получение скина из SkinsRestorer (локально)
     */
    private static Object getSkinFromSkinsRestorer(String playerName) {
        try {
            if (skinsRestorerAPI == null) return null;

            // Попытка 1: getSkinData(playerName) - для старых версий SR
            try {
                Object skinData = skinsRestorerAPI.getClass()
                    .getMethod("getSkinData", String.class)
                    .invoke(skinsRestorerAPI, playerName);
                
                if (skinData != null) {
                    return skinData;
                }
            } catch (NoSuchMethodException ignored) {
                // Метод не найден, пробуем другой
            }

            // Попытка 2: getPlayerSkin(playerName) - для новых версий SR (v14+)
            try {
                Object optionalSkin = skinsRestorerAPI.getClass()
                    .getMethod("getPlayerSkin", String.class)
                    .invoke(skinsRestorerAPI, playerName);

                // Проверяем Optional.isPresent()
                boolean isPresent = (boolean) optionalSkin.getClass()
                    .getMethod("isPresent")
                    .invoke(optionalSkin);

                if (isPresent) {
                    // Optional.get()
                    return optionalSkin.getClass()
                        .getMethod("get")
                        .invoke(optionalSkin);
                }
            } catch (NoSuchMethodException ignored) {
                // Метод не найден
            }

            // Попытка 3: getSkinForPlayer(UUID) - через UUID игрока
            try {
                @SuppressWarnings("deprecation")
                org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
                if (offlinePlayer != null && offlinePlayer.getUniqueId() != null) {
                    Object optionalSkin = skinsRestorerAPI.getClass()
                        .getMethod("getSkinForPlayer", UUID.class)
                        .invoke(skinsRestorerAPI, offlinePlayer.getUniqueId());

                    boolean isPresent = (boolean) optionalSkin.getClass()
                        .getMethod("isPresent")
                        .invoke(optionalSkin);

                    if (isPresent) {
                        return optionalSkin.getClass()
                            .getMethod("get")
                            .invoke(optionalSkin);
                    }
                }
            } catch (NoSuchMethodException ignored) {
                // Метод не найден
            }

            return null;

        } catch (Exception e) {
            Bukkit.getLogger().warning("[PromoDawn] Ошибка получения скина из SkinsRestorer для " + playerName + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Применение текстуры к голове через рефлексию
     */
    private static void applyTextureToHead(SkullMeta meta, Object skinData) throws Exception {
        // Получаем texture и signature из PlayerSkin
        String textureValue = (String) skinData.getClass()
            .getMethod("getTextureValue")
            .invoke(skinData);

        String textureSignature = null;
        try {
            textureSignature = (String) skinData.getClass()
                .getMethod("getSignatureValue")
                .invoke(skinData);
        } catch (Exception ignored) {
            // Signature опциональна
        }

        // Создаём GameProfile с текстурой через рефлексию
        Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
        Object profile = gameProfileClass.getConstructor(UUID.class, String.class)
            .newInstance(UUID.randomUUID(), "");

        // Получаем PropertyMap
        Object properties = gameProfileClass.getMethod("getProperties").invoke(profile);

        // Создаём Property
        Class<?> propertyClass = Class.forName("com.mojang.authlib.properties.Property");
        Object property = propertyClass.getConstructor(String.class, String.class, String.class)
            .newInstance("textures", textureValue, textureSignature);

        // Добавляем Property в PropertyMap
        properties.getClass().getMethod("put", Object.class, Object.class)
            .invoke(properties, "textures", property);

        // Применяем GameProfile к SkullMeta через рефлексию
        Field profileField = meta.getClass().getDeclaredField("profile");
        profileField.setAccessible(true);
        profileField.set(meta, profile);
    }

    /**
     * Очищает устаревшие записи из кеша
     */
    private static void cleanExpiredCache() {
        cachedHeads.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    /**
     * Очищает весь кеш (вызывать при перезагрузке конфига)
     */
    public static void clearCache() {
        cachedHeads.clear();
    }

    /**
     * Возвращает количество закешированных голов
     */
    public static int getCacheSize() {
        return cachedHeads.size();
    }

    /**
     * Проверка доступности SkinsRestorer
     */
    public static boolean isSkinsRestorerAvailable() {
        return skinsRestorerAPI != null;
    }
}
