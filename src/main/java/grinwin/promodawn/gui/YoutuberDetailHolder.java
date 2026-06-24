package grinwin.promodawn.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class YoutuberDetailHolder implements InventoryHolder {
    private final String code;
    private final int page;

    public YoutuberDetailHolder(String code) { this(code, 0); }
    public YoutuberDetailHolder(String code, int page) { this.code = code; this.page = page; }

    public String getCode() {
        return code;
    }

    public int getPage() { return page; }

    @Override
    public Inventory getInventory() {
        return null;
    }
}


