package com.serverops.fabric.legacyfix;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * ONE-TIME CLEANUP after the removed 100 HP experiment.
 *
 * <p>The 100 HP build stored "max health = 100" inside every player's save file, so simply deleting that code
 * would leave those players at 100 HP forever. This class puts them back to vanilla 20 HP (10 hearts) the next
 * time they join, keeping the same PERCENTAGE of health. Players who never had 100 HP are not touched at all.
 *
 * <p>Nothing else about health, damage or healing is modified anywhere in the mod: it is 100 % vanilla.
 * Once every player has logged in once since the update you may delete this class and its line in
 * fabric.mod.json - harmless either way.
 */
public class LegacyHealthCleanup implements ModInitializer {
    private static final double LEGACY_MAX = 100.0;

    @Override
    public void onInitialize() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> fix(handler.player));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> fix(newPlayer));
    }

    private static void fix(ServerPlayer p) {
        AttributeInstance max = p.getAttribute(Attributes.MAX_HEALTH);
        if (max == null || max.getBaseValue() != LEGACY_MAX) return;
        float fraction = p.getHealth() / p.getMaxHealth();
        max.setBaseValue(20.0);
        p.setHealth(Math.max(1f, fraction * p.getMaxHealth()));
    }
}
