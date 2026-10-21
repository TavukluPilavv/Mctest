package dev.mctest;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client-only configuration prototype for a private test world.
 * AttackAura is deliberately a settings/visual prototype: it does not automate
 * attacks, rotate toward real players, bypass shields, or send combat packets.
 */
public final class MctestClient implements ClientModInitializer {
    private static boolean menuWasDown;
    private static final Map<String, Boolean> ENABLED = new LinkedHashMap<>();
    private static final Map<String, Integer> BINDS = new LinkedHashMap<>();
    private static final Map<String, Boolean> KEY_WAS_DOWN = new LinkedHashMap<>();
    private static double attackRange = 3.0;

    static {
        ENABLED.put("AttackAura", false);
        ENABLED.put("X-Ray", false);
        ENABLED.put("Fly", false);
        BINDS.put("AttackAura", GLFW.GLFW_KEY_UNKNOWN);
        BINDS.put("X-Ray", GLFW.GLFW_KEY_UNKNOWN);
        BINDS.put("Fly", GLFW.GLFW_KEY_UNKNOWN);
        KEY_WAS_DOWN.put("AttackAura", false);
        KEY_WAS_DOWN.put("X-Ray", false);
        KEY_WAS_DOWN.put("Fly", false);
    }

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.getWindow() == null) return;
            long handle = client.getWindow().getHandle();
            boolean menuDown = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
            if (menuDown && !menuWasDown && client.currentScreen == null) {
                client.setScreen(new HackMenuScreen());
            }
            menuWasDown = menuDown;

            if (client.currentScreen != null) return;
            for (String module : BINDS.keySet()) {
                int key = BINDS.get(module);
                boolean down = key != GLFW.GLFW_KEY_UNKNOWN
                        && GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS;
                if (down && !KEY_WAS_DOWN.get(module)) {
                    ENABLED.put(module, !ENABLED.get(module));
                }
                KEY_WAS_DOWN.put(module, down);
            }
        });
    }

    private static final class HackMenuScreen extends Screen {
        private String waitingForBind;
        private static final int PANEL_W = 310;
        private static final int PANEL_H = 220;

        private HackMenuScreen() {
            super(Text.literal("MCTest Hack Menu"));
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            renderBackground(context, mouseX, mouseY, delta);
            int left = (width - PANEL_W) / 2;
            int top = (height - PANEL_H) / 2;
            context.fill(left, top, left + PANEL_W, top + PANEL_H, 0xEE111827);
            context.fill(left, top, left + PANEL_W, top + 24, 0xFF263449);
            context.drawText(textRenderer, "MCTest  |  Hack Menu", left + 10, top + 8, 0xFFFFFFFF, false);
            context.drawText(textRenderer, "Right Shift: close", left + 190, top + 8, 0xFFB9C6D8, false);

            int y = top + 38;
            for (String module : BINDS.keySet()) {
                boolean enabled = ENABLED.get(module);
                int color = enabled ? 0xFF244D3B : 0xFF263142;
                context.fill(left + 10, y - 4, left + PANEL_W - 10, y + 20, color);
                context.drawText(textRenderer, module, left + 18, y + 3, 0xFFFFFFFF, false);
                String bindText = waitingForBind != null && waitingForBind.equals(module)
                        ? "Press a key..." : keyName(BINDS.get(module));
                context.drawText(textRenderer, "[" + bindText + "]", left + 154, y + 3, 0xFFFFD479, false);
                context.drawText(textRenderer, enabled ? "ON" : "OFF", left + 266, y + 3,
                        enabled ? 0xFF71F0A0 : 0xFFB9C6D8, false);
                y += 31;
            }

            context.drawText(textRenderer, "AttackAura test range: " + String.format("%.1f", attackRange),
                    left + 12, top + 145, 0xFFFFFFFF, false);
            context.fill(left + 12, top + 162, left + 142, top + 184, 0xFF334155);
            context.fill(left + 154, top + 162, left + 284, top + 184, 0xFF334155);
            context.drawCenteredTextWithShadow(textRenderer, "- Range", left + 77, top + 169, 0xFFFFFFFF);
            context.drawCenteredTextWithShadow(textRenderer, "+ Range", left + 219, top + 169, 0xFFFFFFFF);
            context.drawText(textRenderer, "Prototype only: no automated attacks or shield bypass.",
                    left + 12, top + 198, 0xFFFFC7A8, false);
            super.render(context, mouseX, mouseY, delta);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            int left = (width - PANEL_W) / 2;
            int top = (height - PANEL_H) / 2;
            int y = top + 34;
            for (String module : BINDS.keySet()) {
                if (mouseX >= left + 10 && mouseX <= left + PANEL_W - 10
                        && mouseY >= y - 4 && mouseY <= y + 20) {
                    if (button == 2) {
                        waitingForBind = module;
                    } else if (button == 0) {
                        ENABLED.put(module, !ENABLED.get(module));
                    }
                    return true;
                }
                y += 31;
            }
            if (button == 0 && mouseY >= top + 162 && mouseY <= top + 184) {
                if (mouseX >= left + 12 && mouseX <= left + 142) {
                    attackRange = Math.max(1.0, attackRange - 0.5);
                    return true;
                }
                if (mouseX >= left + 154 && mouseX <= left + 284) {
                    attackRange = Math.min(6.0, attackRange + 0.5);
                    return true;
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (waitingForBind != null) {
                if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                    waitingForBind = null;
                    return true;
                }
                BINDS.put(waitingForBind, keyCode);
                KEY_WAS_DOWN.put(waitingForBind, true);
                waitingForBind = null;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                close();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean shouldPause() {
            return false;
        }
    }

    private static String keyName(int key) {
        if (key == GLFW.GLFW_KEY_UNKNOWN) return "middle-click to bind";
        String name = GLFW.glfwGetKeyName(key, 0);
        if (name != null) return name.toUpperCase();
        return "KEY " + key;
    }
}
