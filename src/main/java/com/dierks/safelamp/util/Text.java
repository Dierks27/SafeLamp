package com.dierks.safelamp.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Small Adventure helper so the rest of the plugin can keep working with simple
 * legacy '&amp;'-coded strings straight out of config.yml while still producing
 * proper {@link Component}s (Paper's native text type).
 */
public final class Text {

    private static final LegacyComponentSerializer AMPERSAND = LegacyComponentSerializer.legacyAmpersand();

    private Text() {
    }

    /**
     * Deserialize an '&amp;'-coded string, explicitly turning OFF italics.
     *
     * <p>Item display names and lore render italic by default in vanilla; without
     * this every lamp would look like it had been renamed in an anvil.</p>
     */
    public static Component of(String legacy) {
        return AMPERSAND.deserialize(legacy == null ? "" : legacy)
                .decoration(TextDecoration.ITALIC, false);
    }
}
