package dev.zalrpakraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Кнопка главного меню: тёмное стекло, фиолетовая рамка, при наведении — золотое свечение и звёздочка. */
public class StarButton extends AbstractButton {
    private final Runnable action;
    private float hover; // 0..1, плавно

    public StarButton(int x, int y, int w, int h, Component text, Runnable action) {
        super(x, y, w, h, text);
        this.action = action;
    }

    @Override
    public void onPress() {
        action.run();
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
        boolean hot = isHoveredOrFocused() && active;
        hover = Mth.lerp(0.25f, hover, hot ? 1f : 0f);
        int x0 = getX(), y0 = getY(), x1 = x0 + width, y1 = y0 + height;

        for (int i = 3; i >= 1; i--) {
            int a = (int) (hover * 30 / i);
            g.fill(x0 - i, y0 - i, x1 + i, y1 + i, (a << 24) | 0xFFC94A);
        }
        g.fillGradient(x0, y0, x1, y1, active ? 0xC0201436 : 0x80201436, active ? 0xC00E0A1C : 0x800E0A1C);
        int border = lerp(0xFF6A54A8, 0xFFFFD27A, hover);
        if (!active) border = 0xFF3A3250;
        g.fill(x0, y0, x1, y0 + 1, border);
        g.fill(x0, y1 - 1, x1, y1, border);
        g.fill(x0, y0, x0 + 1, y1, border);
        g.fill(x1 - 1, y0, x1, y1, border);
        // уголки
        int c = lerp(0xFFB79CFF, 0xFFFFF0B0, hover);
        g.fill(x0, y0, x0 + 3, y0 + 1, c); g.fill(x0, y0, x0 + 1, y0 + 3, c);
        g.fill(x1 - 3, y1 - 1, x1, y1, c); g.fill(x1 - 1, y1 - 3, x1, y1, c);

        Minecraft mc = Minecraft.getInstance();
        int textColor = !active ? 0xFF6E6680 : lerp(0xFFE6DDF8, 0xFFFFF2C8, hover);
        int shift = (int) (hover * 4);
        int tw = mc.font.width(getMessage());
        int tx = x0 + (width - tw) / 2 + shift;
        int ty = y0 + (height - 8) / 2;
        if (hover > 0.05f) {
            int a = (int) (hover * 255);
            g.drawString(mc.font, "✦", tx - 11, ty, (Mth.clamp(a, 0, 255) << 24) | 0xFFD27A, false);
        }
        g.drawString(mc.font, getMessage(), tx, ty, textColor, true);
    }

    private static int lerp(int a, int b, float t) {
        int r = 0;
        for (int s = 0; s <= 24; s += 8) {
            int ca = (a >>> s) & 255, cb = (b >>> s) & 255;
            r |= ((int) Mth.lerp(t, ca, cb) & 255) << s;
        }
        return r;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}
