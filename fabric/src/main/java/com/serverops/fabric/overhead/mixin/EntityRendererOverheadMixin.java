package com.serverops.fabric.overhead.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.serverops.fabric.client.ClientState;
import com.serverops.fabric.overhead.OverheadRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the name above OTHER players with the new overhead tag - always, for everybody; the admin HP toggle only
 * decides whether the health bar is part of it.
 *
 * <p>It only acts when the game itself would show a name ({@code shouldShowName}: not for invisible players, not
 * too far away, not for your own character...). In every other case nothing happens and vanilla / the old code
 * behave as before. When it acts, the base render step ends here: that step only draws the vanilla name, so the
 * panel's old bracketed role tag and old text health bar (injected at the END of that step) are skipped. Model,
 * armor and the rest of the player are rendered by subclasses BEFORE this step and are not affected.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererOverheadMixin {

    @Shadow
    protected abstract boolean shouldShowName(Entity entity);

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void serverops$overhead(Entity entity, float yaw, float partialTick, PoseStack poseStack,
                                    MultiBufferSource buffer, int light, CallbackInfo ci) {
        if (!(entity instanceof AbstractClientPlayer p) || p == Minecraft.getInstance().player) return;
        if (!this.shouldShowName(entity)) return;
        OverheadRenderer.draw(p, poseStack, buffer, ClientState.healthBars);
        ci.cancel();
    }
}
