package dev.mctest;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MctestClient implements ClientModInitializer {
    private static boolean menuWasDown;
    private static boolean flyWasRun;
    private static final Map<String, Boolean> ENABLED = new LinkedHashMap<>();
    private static final Map<String, Integer> BINDS = new LinkedHashMap<>();
    private static final Map<String, Boolean> KEY_WAS_DOWN = new LinkedHashMap<>();
    private static double attackRange = 3.0;
    private static int attackIntervalTicks = 10;
    private static double flySpeed = 0.45;
    private static int attackDelayTicks;

    // Özel modül ayarları
    private static boolean shieldBreakEnabled = true;

    static {
        for (String module : new String[]{"Attack Aura", "Criticals", "Mobs", "Fly", "X-Ray"}) {
            ENABLED.put(module, false);
            BINDS.put(module, GLFW.GLFW_KEY_UNKNOWN);
            KEY_WAS_DOWN.put(module, false);
        }
    }

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.getWindow() == null) return;
            long handle = client.getWindow().getHandle();
            boolean menuDown = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
            if (menuDown && !menuWasDown) {
                if (client.currentScreen == null) {
                    client.setScreen(new HackMenuScreen());
                } else if (client.currentScreen instanceof HackMenuScreen) {
                    client.setScreen(null);
                }
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

            if (client.player == null || client.world == null) {
                resetFly(client);
                return;
            }

            // Multiplayer kısıtlaması kaldırıldı; her yerde çalışır.
            if (ENABLED.get("Attack Aura")) runMobAttackTest(client);
            if (ENABLED.get("Fly")) runLocalFly(client);
            else resetFly(client);
        });
    }

    public static boolean isXrayEnabled() {
        return ENABLED.get("X-Ray");
    }

    private static void runMobAttackTest(MinecraftClient client) {
        PlayerEntity player = client.player;
        if (player == null || client.world == null || client.interactionManager == null) return;

        // Hem oyuncular hem yaratıklar hedef alınabilir.
        LivingEntity target = client.world.getEntitiesByClass(
                LivingEntity.class,
                player.getBoundingBox().expand(attackRange),
                entity -> entity.isAlive()
                        && !entity.isRemoved()
                        && entity != player
                        && !(entity instanceof ArmorStandEntity)
                        && player.canSee(entity)
                        && player.squaredDistanceTo(entity) <= attackRange * attackRange
        ).stream()
         .min(Comparator.comparingDouble(player::squaredDistanceTo))
         .orElse(null);

        if (target == null) return;

        // SILENT AIM: Ekran kamerasını dönmesini engelleyip sadece kafa/gövde açısını hedefe kilitliyoruz (F5'te gözükür).
        Vec3d delta = target.getEyePos().subtract(player.getEyePos());
        float yaw = (float) (MathHelper.atan2(delta.z, delta.x) * (180.0 / Math.PI)) - 90.0F;
        player.setHeadYaw(yaw);
        player.setBodyYaw(yaw);

        if (attackDelayTicks > 0) {
            attackDelayTicks--;
            return;
        }

        // Saldırı bekleme süresi dolmadıysa bekle (Spam atmaz)
        if (player.getAttackCooldownProgress(0.5F) < 1.0F) {
            return;
        }

        // Criticals Modu: Açık ise sadece oyuncu havada düşüş aşamasındayken tam kritik vurur.
        if (ENABLED.get("Criticals")) {
            boolean isFalling = player.fallDistance > 0.0F
                    && !player.isOnGround()
                    && !player.isClimbing()
                    && !player.isTouchingWater();

            if (!isFalling) {
                return;
            }
        }

        // Kalkan Kırma (Shield Break): Hedef kalkan kullanıyorsa envanterdeki baltaya geçer
        if (shieldBreakEnabled && target.isBlocking()) {
            switchToAxe(player);
        }

        client.interactionManager.attackEntity(player, target);
        player.swingHand(Hand.MAIN_HAND);
        attackDelayTicks = attackIntervalTicks;
    }

    private static void switchToAxe(PlayerEntity player) {
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getStack(i).getItem() instanceof AxeItem) {
                player.getInventory().selectedSlot = i;
                break;
            }
        }
    }

    private static void runLocalFly(MinecraftClient client) {
        if (client.player == null) return;
        flyWasRun = true;
        double vertical = 0.0;
        if (client.options.jumpKey.isPressed()) vertical += flySpeed;
        if (client.options.sneakKey.isPressed()) vertical -= flySpeed;
        Vec3d velocity = client.player.getVelocity();
        client.player.setVelocity(velocity.x, vertical, velocity.z);
        client.player.fallDistance = 0.0F;
    }

    private static void resetFly(MinecraftClient client) {
        if (flyWasRun && client.player != null) {
            client.player.setVelocity(client.player.getVelocity().x, 0.0, client.player.getVelocity().z);
            client.player.fallDistance = 0.0F;
        }
        flyWasRun = false;
    }

    private static final class HackMenuScreen extends Screen {
        private String waitingForBind;
        private static final int PANEL_W = 360;
        private static final int PANEL_H = 190;

        private HackMenuScreen() {
            super(Text.literal("Vexion Client"));
        }

        @Override
        public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            context.fill(0, 0, width, height, 0x99000000);
            int left = (width - PANEL_W) / 2;
            int top = (height - PANEL_H) / 2;
            context.fill(left, top, left + PANEL_W, top + PANEL_H, 0xEE111827);
            context.fill(left, top, left + PANEL_W, top + 27, 0xFF263449);
            context.drawText(textRenderer, "Vexion Client  |  Hack Menu", left + 10, top + 9, 0xFFFFFFFF, false);
            context.drawText(textRenderer, "Right Shift: close", left + 246, top + 9, 0xFFB9C6D8, false);

            int y = top + 42;
            for (String module : BINDS.keySet()) {
                boolean enabled = ENABLED.get(module);
                context.fill(left + 10, y - 4, left + PANEL_W - 10, y + 20,
                        enabled ? 0xFF244D3B : 0xFF263142);
                context.drawText(textRenderer, module, left + 18, y + 3, 0xFFFFFFFF, false);
                String bindText = waitingForBind != null && waitingForBind.equals(module)
                        ? "Press a key..." : keyName(BINDS.get(module));
                context.drawText(textRenderer, "[" + bindText + "]", left + 145, y + 3, 0xFFFFD479, false);
                context.drawText(textRenderer, enabled ? "ON" : "OFF", left + 290, y + 3,
                        enabled ? 0xFF71F0A0 : 0xFFB9C6D8, false);
                y += 32;
            }
            context.drawText(textRenderer, "Left-click: toggle   Middle-click: bind   Right-click: settings",
                    left + 12, top + 146, 0xFFFFFFFF, false);
            super.render(context, mouseX, mouseY, delta);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            int left = (width - PANEL_W) / 2;
            int top = (height - PANEL_H) / 2;
            int y = top + 38;
            for (String module : BINDS.keySet()) {
                if (mouseX >= left + 10 && mouseX <= left + PANEL_W - 10
                        && mouseY >= y - 4 && mouseY <= y + 20) {
                    if (button == 2) {
                        waitingForBind = module;
                    } else if (button == 0) {
                        ENABLED.put(module, !ENABLED.get(module));
                    } else if (button == 1) {
                        client.setScreen(new ModuleSettingsScreen(module));
                    }
                    return true;
                }
                y += 32;
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
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
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

    private static final class ModuleSettingsScreen extends Screen {
        private final String module;
        private ModuleSettingsScreen(String module) {
            super(Text.literal(module + " Settings"));
            this.module = module;
        }

        @Override
        public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            context.fill(0, 0, width, height, 0x66000000);
            int left = (width - 330) / 2;
            int top = (height - 200) / 2;
            context.fill(left, top, left + 330, top + 200, 0xEE111827);
            context.fill(left, top, left + 330, top + 27, 0xFF263449);
            context.drawText(textRenderer, module + " Settings", left + 10, top + 9, 0xFFFFFFFF, false);

            if ("Attack Aura".equals(module)) {
                context.drawText(textRenderer, "Range: " + String.format("%.1f", attackRange) + " blocks",
                        left + 16, top + 38, 0xFFFFFFFF, false);
                drawButton(context, left + 16, top + 52, " - Range ");
                drawButton(context, left + 176, top + 52, " + Range ");

                drawButton(context, left + 16, top + 82, "Criticals: " + (ENABLED.get("Criticals") ? "ON" : "OFF"));
                drawButton(context, left + 176, top + 82, "ShieldBreak: " + (shieldBreakEnabled ? "ON" : "OFF"));

                context.drawText(textRenderer, "Interval: " + attackIntervalTicks + " ticks",
                        left + 16, top + 115, 0xFFFFFFFF, false);
                drawButton(context, left + 16, top + 130, " - Interval ");
                drawButton(context, left + 176, top + 130, " + Interval ");
            } else if ("Fly".equals(module)) {
                context.drawText(textRenderer, "Vertical speed: " + String.format("%.2f", flySpeed),
                        left + 16, top + 58, 0xFFFFFFFF, false);
                drawButton(context, left + 16, top + 82, " - Speed ");
                drawButton(context, left + 176, top + 82, " + Speed ");
            } else {
                context.drawText(textRenderer, "X-Ray render toggle", left + 16, top + 62, 0xFFFFFFFF, false);
                context.drawText(textRenderer, "Toggle it in the main module list.",
                        left + 16, top + 88, 0xFFB9C6D8, false);
            }
            super.render(context, mouseX, mouseY, delta);
        }

        private void drawButton(DrawContext context, int x, int y, String label) {
            context.fill(x, y, x + 138, y + 20, 0xFF334155);
            context.drawCenteredTextWithShadow(textRenderer, label, x + 69, y + 6, 0xFFFFFFFF);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
            int left = (width - 330) / 2;
            int top = (height - 200) / 2;
            if ("Attack Aura".equals(module)) {
                if (inside(mouseX, mouseY, left + 16, top + 52, 138, 20)) {
                    attackRange = Math.max(1.0, attackRange - 0.5);
                    return true;
                }
                if (inside(mouseX, mouseY, left + 176, top + 52, 138, 20)) {
                    attackRange = Math.min(6.0, attackRange + 0.5);
                    return true;
                }
                if (inside(mouseX, mouseY, left + 16, top + 82, 138, 20)) {
                    ENABLED.put("Criticals", !ENABLED.get("Criticals"));
                    return true;
                }
                if (inside(mouseX, mouseY, left + 176, top + 82, 138, 20)) {
                    shieldBreakEnabled = !shieldBreakEnabled;
                    return true;
                }
                if (inside(mouseX, mouseY, left + 16, top + 130, 138, 20)) {
                    attackIntervalTicks = Math.max(1, attackIntervalTicks - 1);
                    return true;
                }
                if (inside(mouseX, mouseY, left + 176, top + 130, 138, 20)) {
                    attackIntervalTicks = Math.min(40, attackIntervalTicks + 1);
                    return true;
                }
            } else if ("Fly".equals(module)) {
                if (inside(mouseX, mouseY, left + 16, top + 82, 138, 20)) {
                    flySpeed = Math.max(0.10, flySpeed - 0.05);
                    return true;
                }
                if (inside(mouseX, mouseY, left + 176, top + 82, 138, 20)) {
                    flySpeed = Math.min(1.50, flySpeed + 0.05);
                    return true;
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        private boolean inside(double x, double y, int bx, int by, int w, int h) {
            return x >= bx && x <= bx + w && y >= by && y <= by + h;
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                client.setScreen(new HackMenuScreen());
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
