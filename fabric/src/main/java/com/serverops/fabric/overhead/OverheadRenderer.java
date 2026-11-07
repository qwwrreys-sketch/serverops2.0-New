package com.serverops.fabric.overhead;

import java.util.UUID;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.serverops.fabric.client.ClientState;
import com.serverops.fabric.net.Net;
import com.serverops.fabric.sidemenu.MenuConfig;
import com.serverops.fabric.sidemenu.client.MenuClientState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * The name tag above other players. It replaces the vanilla name (and the panel's old bracketed role tag).
 *
 * <pre>
 *       ExE                      line 1  role, raw text in the role colour, no brackets (only if the player has one)
 *   Exe Schulz-Devereux          line 2  RP name + surname, slightly larger than the others
 *      murdermed                 line 3  Minecraft username, small
 *    [=====-----] 50%            line 4  slim rounded health bar + percentage  - ONLY while the admin HP toggle is on
 * </pre>
 *
 * Lines 1-3 are ALWAYS shown; the admin HP toggle only adds or removes line 4. The percentage is health / max health
 * (so 10 of 20 HP = 50 %): green above 50 %, yellow/orange 21-50 %, red 20 % and below.
 * Players without a character yet show their Minecraft name on line 2 and have no line 3.
 *
 * <p>Sizes are relative to the vanilla name (1.0 = vanilla size): everything here is the same size or SMALLER than
 * vanilla except the name line, which is 10 % larger. All values are in the constants below.
 */
public final class OverheadRenderer {
    private OverheadRenderer() {}

    // ---- sizes (1.0 = size of the vanilla name) ----
    private static final float ROLE_SCALE = 0.70f;
    private static final float NAME_SCALE = 1.10f;    // the largest line, but only slightly
    private static final float USER_SCALE = 0.75f;
    private static final float PERCENT_SCALE = 0.70f;
    private static final float LINE_GAP = 1.0f;       // space between lines (in text pixels)
    private static final float BAR_W = 26f;           // slim and short
    private static final float BAR_H = 3.2f;
    private static final float BAR_GAP = 3f;          // space between bar and percentage

    private static final int NAME_COLOR = 0xF4EEDC;   // warm white (same family as the panel's beige)
    private static final int USER_COLOR = 0xC4C4CC;   // soft grey
    private static final int GREEN = 0x4CC864;
    private static final int AMBER = 0xF0A020;
    private static final int RED = 0xE5484D;
    private static final int TRACK = 0xB0141418;      // the empty part of the bar (semi-transparent dark)

    /**
     * Draws the tag. Called by the mixin only for players the game itself would show a name for (so invisible
     * players, far-away players and your own character keep behaving like vanilla).
     *
     * @param showBar true while the admin panel's HP visibility is on
     */
    public static void draw(AbstractClientPlayer p, PoseStack ps, MultiBufferSource buffer, boolean showBar) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        // ---------------------------------------------------------------- what to show
        UUID id = p.getUUID();
        Net.Tag tag = ClientState.tag(id);
        String rp = MenuClientState.rpName(id);
        String user = p.getGameProfile().getName();

        Component role = tag == null ? null : text(tag.name());
        Component main = text(rp.isEmpty() ? user : rp);
        Component sub = rp.isEmpty() ? null : text(user);

        // ---------------------------------------------------------------- layout, top to bottom
        float hRole = role == null ? 0 : 9 * ROLE_SCALE + LINE_GAP;
        float hMain = 9 * NAME_SCALE + LINE_GAP;
        float hSub = sub == null ? 0 : 9 * USER_SCALE + LINE_GAP;
        float hBar = showBar ? 6f : 0f;
        // the block ENDS where the vanilla name would end, so it grows upward when the bar is added
        float y = 9f - (hRole + hMain + hSub + hBar) + (showBar ? 0f : LINE_GAP);

        ps.pushPose();
        ps.translate(0.0F, p.getBbHeight() + 0.5F, 0.0F);
        ps.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        ps.scale(-0.025F, -0.025F, 0.025F);

        if (role != null) {
            centered(mc, font, role, y, ROLE_SCALE, tag.color() & 0xFFFFFF, ps, buffer);
            y += hRole;
        }
        centered(mc, font, main, y, NAME_SCALE, NAME_COLOR, ps, buffer);
        y += hMain;
        if (sub != null) {
            centered(mc, font, sub, y, USER_SCALE, USER_COLOR, ps, buffer);
            y += hSub;
        }

        // ---------------------------------------------------------------- line 4: bar + percentage (HP toggle only)
        if (showBar) {
            float max = Math.max(1f, p.getMaxHealth());
            float frac = Mth.clamp(p.getHealth() / max, 0f, 1f);
            int pct = Math.round(100f * frac);
            int color = pct > 50 ? GREEN : (pct > 20 ? AMBER : RED);
            Component pctText = text(pct + "%");

            float pctW = font.width(pctText) * PERCENT_SCALE;
            float bx = -(BAR_W + BAR_GAP + pctW) / 2f;
            float by = y + (hBar - BAR_H) / 2f;

            VertexConsumer vc = buffer.getBuffer(RenderType.debugQuads());
            bar(vc, ps.last().pose(), bx, by, BAR_W, BAR_H, frac, 0xFF000000 | color);
            at(mc, font, pctText, bx + BAR_W + BAR_GAP, y + (hBar - 9 * PERCENT_SCALE) / 2f + 0.5f,
                    PERCENT_SCALE, color, ps, buffer, false);
        }

        ps.popPose();
    }

    // ================================================================== text

    private static MutableComponent text(String s) {
        MutableComponent c = Component.literal(s);
        if (MenuConfig.SERIF_FONT != null) c = c.withStyle(st -> st.withFont(MenuConfig.SERIF_FONT));
        return c;
    }

    private static void centered(Minecraft mc, Font font, Component c, float y, float scale, int rgb, PoseStack ps, MultiBufferSource buf) {
        at(mc, font, c, -font.width(c) * scale / 2f, y, scale, rgb, ps, buf, true);
    }

    /**
     * One text line at pixel position (x, y). Drawn like the vanilla name: a faint see-through pass with the usual
     * translucent dark backing, then the normal pass on top.
     */
    private static void at(Minecraft mc, Font font, Component c, float x, float y, float scale, int rgb,
                           PoseStack ps, MultiBufferSource buf, boolean backing) {
        ps.pushPose();
        ps.translate(x, y, 0);
        ps.scale(scale, scale, 1f);
        Matrix4f m = ps.last().pose();
        int background = backing ? (int) (mc.options.getBackgroundOpacity(0.25F) * 255.0F) << 24 : 0;
        font.drawInBatch(c, 0, 0, 0x20000000 | rgb, false, m, buf, Font.DisplayMode.SEE_THROUGH, background, LightTexture.FULL_BRIGHT);
        font.drawInBatch(c, 0, 0, 0xFF000000 | rgb, false, m, buf, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        ps.popPose();
    }

    // ================================================================== the slim rounded bar

    /**
     * Rounded bar made of thin horizontal strips (half a pixel high), shortened at both ends by the curve of a circle
     * (radius = half the height) = smooth round caps without any texture.
     *
     * <p>Each strip is split at the fill line: the left piece is the coloured fill, the right piece is the dark empty
     * track. The two pieces NEVER overlap, so there is no depth-fighting (this is what made the fill invisible and
     * the bar look solid black in the first version).
     */
    private static void bar(VertexConsumer vc, Matrix4f m, float x, float y, float w, float h, float frac, int fillArgb) {
        float r = h / 2f;
        float fillX = x + w * frac;
        final float step = 0.4f;
        for (float top = 0; top < h; top += step) {
            float bottom = Math.min(h, top + step);
            float cy = (top + bottom) / 2f;
            float edge = Math.min(cy, h - cy);                     // distance of this strip to the top / bottom edge
            float inset = edge >= r ? 0f : r - (float) Math.sqrt(r * r - (r - edge) * (r - edge));
            float x0 = x + inset;
            float x1 = x + w - inset;
            if (x1 <= x0) continue;
            float split = Mth.clamp(fillX, x0, x1);
            if (split > x0) quad(vc, m, x0, y + top, split, y + bottom, fillArgb);   // fill
            if (x1 > split) quad(vc, m, split, y + top, x1, y + bottom, TRACK);       // empty track
        }
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, int argb) {
        int a = argb >>> 24 & 255, r = argb >> 16 & 255, g = argb >> 8 & 255, b = argb & 255;
        vc.vertex(m, x0, y0, 0).color(r, g, b, a).endVertex();
        vc.vertex(m, x0, y1, 0).color(r, g, b, a).endVertex();
        vc.vertex(m, x1, y1, 0).color(r, g, b, a).endVertex();
        vc.vertex(m, x1, y0, 0).color(r, g, b, a).endVertex();
    }
}
