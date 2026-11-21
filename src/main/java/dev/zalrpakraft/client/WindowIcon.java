package dev.zalrpakraft.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.zalrpakraft.Zalrpakraft;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.system.MemoryUtil;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/** Своя иконка окна (вместо стандартной). На macOS GLFW иконки окна не поддерживает — пропускаем. */
public final class WindowIcon {
    private static final int[] SIZES = {16, 32, 48, 128, 256};

    public static void apply() {
        if (Util.getPlatform() == Util.OS.OSX) return;
        List<ByteBuffer> buffers = new ArrayList<>();
        GLFWImage.Buffer images = GLFWImage.malloc(SIZES.length);
        try {
            for (int i = 0; i < SIZES.length; i++) {
                try (InputStream in = WindowIcon.class.getResourceAsStream("/assets/zalrpakraft/icons/icon_" + SIZES[i] + ".png")) {
                    if (in == null) throw new IllegalStateException("нет иконки " + SIZES[i]);
                    try (NativeImage img = NativeImage.read(in)) {
                        int w = img.getWidth(), h = img.getHeight();
                        ByteBuffer buf = MemoryUtil.memAlloc(w * h * 4);
                        buffers.add(buf);
                        for (int y = 0; y < h; y++)
                            for (int x = 0; x < w; x++) {
                                int abgr = img.getPixelRGBA(x, y);
                                buf.put((byte) (abgr & 255)).put((byte) ((abgr >> 8) & 255))
                                        .put((byte) ((abgr >> 16) & 255)).put((byte) ((abgr >>> 24) & 255));
                            }
                        buf.flip();
                        images.position(i);
                        images.width(w);
                        images.height(h);
                        images.pixels(buf);
                    }
                }
            }
            images.position(0);
            GLFW.glfwSetWindowIcon(Minecraft.getInstance().getWindow().getWindow(), images);
        } catch (Exception e) {
            Zalrpakraft.LOG.warn("Zalepacraft: не удалось поставить иконку окна", e);
        } finally {
            images.free();
            buffers.forEach(MemoryUtil::memFree);
        }
    }

    private WindowIcon() {}
}
