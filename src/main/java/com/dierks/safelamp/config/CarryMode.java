package com.dierks.safelamp.config;

/**
 * Where a player has to be carrying a lamp for it to protect them.
 *
 * <p>Chosen with the {@code carry-mode} key in config.yml. {@link #INVENTORY} is
 * the default because it is the friendliest for the plugin's main audience — a
 * young player picks the lamp up once and never has to think about it again.
 * The stricter modes exist for servers that want carrying the lamp to be a
 * visible, deliberate act rather than a passive buff.</p>
 */
public enum CarryMode {

    /** Anywhere in the 36 backpack/hotbar slots (offhand too, if enabled). */
    INVENTORY,

    /** Held in the main hand, or in the offhand if enabled. */
    HAND,

    /** Held in the main hand only. The offhand toggle does not apply. */
    MAIN_HAND;

    /**
     * Parse a config value, tolerating case and a few obvious spellings.
     *
     * @return the matching mode, or {@code null} if the value is not recognised
     *         (the caller logs it and falls back to {@link #INVENTORY})
     */
    public static CarryMode parse(String raw) {
        if (raw == null) {
            return null;
        }
        String key = raw.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (key) {
            case "INVENTORY", "ANY", "ANYWHERE", "INV", "BACKPACK" -> INVENTORY;
            case "HAND", "HANDS", "HELD", "EITHER_HAND" -> HAND;
            case "MAIN_HAND", "MAINHAND", "MAIN", "HOTBAR_SELECTED" -> MAIN_HAND;
            default -> null;
        };
    }
}
