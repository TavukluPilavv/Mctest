package dev.mctest;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
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
    
    // Hile Modülleri
    private static final Map<String, Boolean> ENABLED = new LinkedHashMap<>();
    private static final Map<String, Integer> BINDS = new LinkedHashMap<>();
    private static final Map<String, Boolean> KEY_WAS_DOWN = new LinkedHashMap<>();

    // Değişkenler (flySpeed buraya eklendi)
    private static double attackRange = 3.5;
    private static int attackIntervalTicks = 10;
    private static int attackDelayTicks;
    private static double flySpeed = 0.45;
    private static boolean targetPlayers = true;
    private static boolean targetMobs = true;
    private static boolean shieldBreakEnabled = true;

    // Hedef (Target HUD için)
    private static LivingEntity currentTarget = null;

    static {
        for (String module : new String[]{"Attack Aura", "Criticals", "Fly", "X-Ray", "TargetHUD", "Keybinds HUD", "Potions HUD"}) {
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
            
            // Right Shift menüsü
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

            // Tuş atamalarını dinleme
            for (String module : BINDS.keySet()) {
                int key = BINDS.get(module);
                boolean down = key != GLFW.GLFW_KEY_UNKNOWN && GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS;
                if (down && !KEY_WAS_DOWN.get(module)) {
                    ENABLED.put(module, !ENABLED.get(module));
                }
                KEY_WAS_DOWN.put(module, down);
            }

            if (client.player == null || client.world == null) {
                resetFly(client);
                currentTarget = null;
                return;
            }

            // Attack Aura Tetikleme
            if (ENABLED.get("Attack Aura")) {
                runAttackAura(client);
            } else {
                currentTarget = null;
            }

            // Fly Tetikleme
            if (ENABLED.get("Fly")) {
                runLocalFly(client);
            } else {
                resetFly(client);
            }
        });

        // Ekran HUD Çizimleri
        HudRenderCallback.EVENT.register((context, tickDelta) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.world == null) return;

            int screenWidth = client.getWindow().getScaledWidth();
            int screenHeight = client.getWindow().getScaledHeight();

            // 1. TARGET HUD
            if (ENABLED.get("TargetHUD") && ENABLED.get("Attack Aura") && currentTarget != null && currentTarget.isAlive()) {
                int thX = screenWidth / 2 - 75;
                int thY = 40;
                context.fill(thX, thY, thX + 150, thY + 40, 0xDD0F172A);
                context.fill(thX, thY, thX + 150, thY + 2, 0xFF8B5CF6);
                
                String targetName = currentTarget.getName().getString();
                context.drawText(client.textRenderer, targetName, thX + 8, thY + 8, 0xFFFFFFFF, false);

                float hp = currentTarget.getHealth();
                float maxHp = currentTarget.getMaxHealth();
                context.drawText(client.textRenderer, String.format("HP: %.1f / %.1f", hp, maxHp), thX + 8, thY + 22, 0xFF4ADE80, false);
            }

            // 2. KEYBINDS HUD
            if (ENABLED.get("Keybinds HUD")) {
                int kbY = 15;
                context.drawText(client.textRenderer, "Keybinds", screenWidth - 90, kbY, 0xFFC084FC, false);
                kbY += 12;
                for (Map.Entry<String, Boolean> entry : ENABLED.entrySet()) {
                    if (entry.getValue() && !entry.getKey().contains("HUD")) {
                        int key = BINDS.get(entry.getKey());
                        String kName = (key == GLFW.GLFW_KEY_UNKNOWN) ? "NONE" : keyName(key);
                        context.drawText(client.textRenderer, entry.getKey() + " [" + kName + "]", screenWidth - 110, kbY, 0xFFE2E8F0, false);
                        kbY += 10;
                    }
                }
            }

            // 3. POTIONS HUD
            if (ENABLED.get("Potions HUD")) {
                int ptY = screenHeight - 60;
                for (StatusEffectInstance effect : client.player.getStatusEffects()) {
                    String name = effect.getEffectType().value().getName().getString();
                    context.drawText(client.textRenderer, name + " " + (effect.getAmplifier() + 1), 10, ptY, 0xFF38BDF8, false);
                    ptY -= 12;
                }
            }
        });
    }

    public static boolean isXrayEnabled() {
        return ENABLED.get("X-Ray");
    }

    private static void runAttackAura(MinecraftClient client) {
        PlayerEntity player = client.player;
        if (player == null || client.world == null || client.interactionManager == null) return;

        currentTarget = client.world.getEntitiesByClass(
                LivingEntity.class,
                player.getBoundingBox().expand(attackRange),
                entity -> entity.isAlive()
                        && !entity.isRemoved()
                        && entity != player
                        && !(entity instanceof ArmorStandEntity)
                        && ((targetPlayers && entity instanceof PlayerEntity) || (targetMobs && entity instanceof MobEntity))
                        && player.canSee(entity)
                        && player.squaredDistanceTo(entity) <= attackRange * attackRange
        ).stream()
         .min(Comparator.comparingDouble(player::squaredDistanceTo))
         .orElse(null);

        if (currentTarget == null) return;

        // F5 Modunda kafa kilitlenmesi (Silent Aim)
        Vec3d delta = currentTarget.getEyePos().subtract(player.getEyePos());
        float yaw = (float) (MathHelper.atan2(delta.z, delta.x) * (180.0 / Math.PI)) - 90.0F;
        player.setHeadYaw(yaw);
        player.setBodyYaw(yaw);

        if (attackDelayTicks > 0) {
            attackDelayTicks--;
            return;
        }

        if (player.getAttackCooldownProgress(0.5F) < 0.9F) {
            return;
        }

        if (ENABLED.get("Criticals")) {
            boolean isAirborne = !player.isOnGround() && !player.isClimbing() && !player.isTouchingWater();
            if (!isAirborne) {
                return;
            }
        }

        if (shieldBreakEnabled && currentTarget.isBlocking()) {
            client.interactionManager.attackEntity(player, currentTarget);
            player.swingHand(Hand.MAIN_HAND);
        }

        client.interactionManager.attackEntity(player, currentTarget);
        player.swingHand(Hand.MAIN_HAND);
        attackDelayTicks = attackIntervalTicks;
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
        private static final int PANEL_W = 380;
        private static final int PANEL_H = 240;

        private HackMenuScreen() {
            super(Text.literal("Vexion Client"));
        }

        @Override
        public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            context.fill(0, 0, width, height, 0x88000000);
            int left = (width - PANEL_W) / 2;
            int top = (height - PANEL_H) / 2;

            context.fill(left, top, left + PANEL_W, top + PANEL_H, 0xFF0F172A);
            context.fill(left, top, left + PANEL_W, top + 32, 0xFF1E293B);
            context.drawText(textRenderer, "Vexion Client | Menu", left + 12, top + 11, 0xFFFFFFFF, false);

            int y = top + 42;
            for (String module : BINDS.keySet()) {
                boolean enabled = ENABLED.get(module);
                context.fill(left + 12, y - 2, left + PANEL_W - 12, y + 20, 0xFF1E293B);
                context.drawText(textRenderer, module, left + 20, y + 4, 0xFFFFFFFF, false);

                int toggleX = left + 320;
                int toggleY = y + 2;
                context.fill(toggleX, toggleY, toggleX + 32, toggleY + 14, enabled ? 0xFF8B5CF6 : 0xFF334155);
                context.fill(enabled ? toggleX + 18 : toggleX + 2, toggleY + 2, (enabled ? toggleX + 18 : toggleX + 2) + 10, toggleY + 12, 0xFFFFFFFF);

                y += 26;
            }
            super.render(context, mouseX, mouseY, delta);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            int left = (width - PANEL_W) / 2;
            int top = (height - PANEL_H) / 2;
            int y = top + 42;
            for (String module : BINDS.keySet()) {
                if (mouseX >= left + 12 && mouseX <= left + PANEL_W - 12 && mouseY >= y - 2 && mouseY <= y + 20) {
                    if (button == 0) {
                        ENABLED.put(module, !ENABLED.get(module));
                    } else if (button == 1) {
                        client.setScreen(new ModuleSettingsScreen(module));
                    } else if (button == 2) {
                        waitingForBind = module;
                    }
                    return true;
                }
                y += 26;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (waitingForBind != null) {
                BINDS.put(waitingForBind, keyCode == GLFW.GLFW_KEY_ESCAPE ? GLFW.GLFW_KEY_UNKNOWN : keyCode);
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
        public boolean shouldPause() { return false; }
    }

    private static final class ModuleSettingsScreen extends Screen {
        private final String module;
        private ModuleSettingsScreen(String module) {
            super(Text.literal(module + " Settings"));
            this.module = module;
        }

        @Override
        public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            context.fill(0, 0, width, height, 0x88000000);
            int left = (width - 320) / 2;
            int top = (height - 180) / 2;
            context.fill(left, top, left + 320, top + 180, 0xFF0F172A);
            context.drawText(textRenderer, module + " Ayarları", left + 12, top + 12, 0xFFFFFFFF, false);

            if ("Attack Aura".equals(module)) {
                drawButton(context, left + 16, top + 45, "Oyuncular: " + (targetPlayers ? "AÇIK" : "KAPALI"));
                drawButton(context, left + 166, top + 45, "Yaratıklar: " + (targetMobs ? "AÇIK" : "KAPALI"));
                drawButton(context, left + 16, top + 80, "Kalkan Kırma: " + (shieldBreakEnabled ? "AÇIK" : "KAPALI"));
                drawButton(context, left + 166, top + 80, "Menzil: " + String.format("%.1f", attackRange));
                drawButton(context, left + 16, top + 115, " - Menzil ");
                drawButton(context, left + 166, top + 115, " + Menzil ");
            }
            super.render(context, mouseX, mouseY, delta);
        }

        private void drawButton(DrawContext context, int x, int y, String label) {
            context.fill(x, y, x + 138, y + 22, 0xFF1E293B);
            context.drawCenteredTextWithShadow(textRenderer, label, x + 69, y + 7, 0xFFFFFFFF);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            int left = (width - 320) / 2;
            int top = (height - 180) / 2;
            if ("Attack Aura".equals(module)) {
                if (inside(mouseX, mouseY, left + 16, top + 45, 138, 22)) targetPlayers = !targetPlayers;
                if (inside(mouseX, mouseY, left + 166, top + 45, 138, 22)) targetMobs = !targetMobs;
                if (inside(mouseX, mouseY, left + 16, top + 80, 138, 22)) shieldBreakEnabled = !shieldBreakEnabled;
                if (inside(mouseX, mouseY, left + 16, top + 115, 138, 22)) attackRange = Math.max(1.0, attackRange - 0.5);
                if (inside(mouseX, mouseY, left + 166, top + 115, 138, 22)) attackRange = Math.min(8.0, attackRange + 0.5);
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
        public boolean shouldPause() { return false; }
    }

    private static String keyName(int key) {
        if (key == GLFW.GLFW_KEY_UNKNOWN) return "Yok";
        String name = GLFW.glfwGetKeyName(key, 0);
        return name != null ? name.toUpperCase() : "KEY_" + key;
    }
}

