package dev.mctest;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
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
    private static boolean shieldBreakEnabled = true;

    // Anlık hedef (Target HUD için)
    private static LivingEntity currentTarget = null;

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
                currentTarget = null;
                return;
            }

            if (ENABLED.get("Attack Aura")) {
                runMobAttackTest(client);
            } else {
                currentTarget = null;
            }

            if (ENABLED.get("Fly")) {
                runLocalFly(client);
            } else {
                resetFly(client);
            }
        });

        // HUD Render Kaydı (Target HUD, Potions HUD, Keybinds HUD)
        HudRenderCallback.EVENT.register((context, tickDelta) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.world == null) return;

            int screenWidth = client.getWindow().getScaledWidth();
            int screenHeight = client.getWindow().getScaledHeight();

            // 1. Target HUD (Ekranın orta üst kısmı)
            if (ENABLED.get("Attack Aura") && currentTarget != null && currentTarget.isAlive()) {
                int thX = screenWidth / 2 - 80;
                int thY = 40;
                context.fill(thX, thY, thX + 160, thY + 45, 0xCC111827);
                context.fill(thX, thY, thX + 160, thY + 3, 0xFF8B5CF6); // Mor Vexion vurgusu
                
                String targetName = currentTarget.getName().getString();
                if (targetName.length() > 18) targetName = targetName.substring(0, 15) + "...";
                context.drawText(client.textRenderer, targetName, thX + 8, thY + 8, 0xFFFFFFFF, false);

                float hp = currentTarget.getHealth();
                float maxHp = currentTarget.getMaxHealth();
                String hpText = String.format("HP: %.1f / %.1f", hp, maxHp);
                context.drawText(client.textRenderer, hpText, thX + 8, thY + 24, 0xFF71F0A0, false);
            }

            // 2. Keybinds HUD (Sağ üst köşe)
            int kbY = 20;
            context.drawText(client.textRenderer, "§5[Vexion Keybinds]", screenWidth - 120, kbY, 0xFFFFFFFF, false);
            kbY += 12;
            for (Map.Entry<String, Boolean> entry : ENABLED.entrySet()) {
                if (entry.getValue()) {
                    String modName = entry.getKey();
                    int key = BINDS.get(modName);
                    String kName = (key == GLFW.GLFW_KEY_UNKNOWN) ? "NONE" : GLFW.glfwGetKeyName(key, 0);
                    if (kName == null) kName = "KEY_" + key;
                    else kName = kName.toUpperCase();

                    context.drawText(client.textRenderer, modName + " [" + kName + "]", screenWidth - 120, kbY, 0xFFD8B4FE, false);
                    kbY += 12;
                }
            }

            // 3. Potions HUD (Sol alt köşe)
            int ptY = screenHeight - 80;
            context.drawText(client.textRenderer, "§d[Active Effects]", 15, ptY, 0xFFFFFFFF, false);
            ptY += 12;
            for (StatusEffectInstance effect : client.player.getStatusEffects()) {
                String name = effect.getEffectType().value().getName().getString();
                int amp = effect.getAmplifier() + 1;
                context.drawText(client.textRenderer, name + " " + amp, 15, ptY, 0xFFE2E8F0, false);
                ptY += 12;
            }
        });
    }

    public static boolean isXrayEnabled() {
        return ENABLED.get("X-Ray");
    }

    private static void runMobAttackTest(MinecraftClient client) {
        PlayerEntity player = client.player;
        if (player == null || client.world == null || client.interactionManager == null) return;

        currentTarget = client.world.getEntitiesByClass(
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

        if (currentTarget == null) return;

        // Silent Aim (Kafa ve gövde yönünü hedefe çevir, ekranı fırıl fırıl döndürmez)
        Vec3d delta = currentTarget.getEyePos().subtract(player.getEyePos());
        float yaw = (float) (MathHelper.atan2(delta.z, delta.x) * (180.0 / Math.PI)) - 90.0F;
        player.setHeadYaw(yaw);
        player.setBodyYaw(yaw);

        if (attackDelayTicks > 0) {
            attackDelayTicks--;
            return;
        }

        if (player.getAttackCooldownProgress(0.5F) < 1.0F) {
            return;
        }

        // Kritik Düzeltmesi: Açıkken havada düşüş anında vurur, kapalıyken normal vurur.
        if (ENABLED.get("Criticals")) {
            boolean isFalling = player.fallDistance > 0.0F
                    && !player.isOnGround()
                    && !player.isClimbing()
                    && !player.isTouchingWater();

            if (!isFalling) {
                return;
            }
        }

        if (shieldBreakEnabled && currentTarget.isBlocking()) {
            switchToAxe(player);
        }

        client.interactionManager.attackEntity(player, currentTarget);
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
        private static final int PANEL_W = 380;
        private static final int PANEL_H = 220;

        private HackMenuScreen() {
            super(Text.literal("Vexion Client"));
        }

        @Override
        public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            context.fill(0, 0, width, height, 0x88000000);
            int left = (width - PANEL_W) / 2;
            int top = (height - PANEL_H) / 2;

            // Modern Koyu Arka Plan ve Üst Başlık
            context.fill(left, top, left + PANEL_W, top + PANEL_H, 0xFF0F172A);
            context.fill(left, top, left + PANEL_W, top + 32, 0xFF1E293B);
            context.drawText(textRenderer, "Vexion Client  |  Modern Menu", left + 12, top + 11, 0xFFFFFFFF, false);
            context.drawText(textRenderer, "[RIGHT SHIFT]: Kapat", left + 245, top + 11, 0xFF94A3B8, false);

            int y = top + 46;
            for (String module : BINDS.keySet()) {
                boolean enabled = ENABLED.get(module);
                
                // Modül satır arkaplanı
                context.fill(left + 12, y - 4, left + PANEL_W - 12, y + 22, 0xFF1E293B);
                context.drawText(textRenderer, module, left + 20, y + 3, 0xFFFFFFFF, false);
                
                String bindText = waitingForBind != null && waitingForBind.equals(module)
                        ? "Bas..." : keyName(BINDS.get(module));
                context.drawText(textRenderer, "[" + bindText + "]", left + 160, y + 3, 0xFFC084FC, false);

                // Modern Şık Toggle Anahtar Kutusu (Mor / Gri)
                int toggleX = left + 300;
                int toggleY = y + 2;
                int toggleColor = enabled ? 0xFF8B5CF6 : 0xFF475569;
                context.fill(toggleX, toggleY, toggleX + 36, toggleY + 14, toggleColor);
                // Topuz
                int knobX = enabled ? (toggleX + 22) : (toggleX + 2);
                context.fill(knobX, toggleY + 2, knobX + 12, toggleY + 12, 0xFFFFFFFF);

                y += 32;
            }

            context.drawText(textRenderer, "Sol tık: Aç/Kapat   Orta tık: Tuş Ata   Sağ tık: Ayarlar",
                    left + 12, top + PANEL_H - 22, 0xFF94A3B8, false);
            super.render(context, mouseX, mouseY, delta);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            int left = (width - PANEL_W) / 2;
            int top = (height - PANEL_H) / 2;
            int y = top + 42;
            for (String module : BINDS.keySet()) {
                if (mouseX >= left + 12 && mouseX <= left + PANEL_W - 12
                        && mouseY >= y - 4 && mouseY <= y + 22) {
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
            super(Text.literal(module + " Ayarları"));
            this.module = module;
        }

        @Override
        public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            context.fill(0, 0, width, height, 0x88000000);
            int left = (width - 330) / 2;
            int top = (height - 200) / 2;
            context.fill(left, top, left + 330, top + 200, 0xFF0F172A);
            context.fill(left, top, left + 330, top + 32, 0xFF1E293B);
            context.drawText(textRenderer, module + " Ayarları", left + 12, top + 11, 0xFFFFFFFF, false);

            if ("Attack Aura".equals(module)) {
                context.drawText(textRenderer, "Menzil: " + String.format("%.1f", attackRange), left + 16, top + 45, 0xFFE2E8F0, false);
                drawButton(context, left + 16, top + 60, " - Menzil ");
                drawButton(context, left + 176, top + 60, " + Menzil ");

                drawButton(context, left + 16, top + 92, "Criticals: " + (ENABLED.get("Criticals") ? "AÇIK" : "KAPALI"));
                drawButton(context, left + 176, top + 92, "Kalkan Kırıcı: " + (shieldBreakEnabled ? "AÇIK" : "KAPALI"));

                context.drawText(textRenderer, "Vuruş Aralığı: " + attackIntervalTicks + " tick", left + 16, top + 130, 0xFFE2E8F0, false);
                drawButton(context, left + 16, top + 145, " - Hız ");
                drawButton(context, left + 176, top + 145, " + Hız ");
            } else if ("Fly".equals(module)) {
                context.drawText(textRenderer, "Uçuş Hızı: " + String.format("%.2f", flySpeed), left + 16, top + 55, 0xFFE2E8F0, false);
                drawButton(context, left + 16, top + 75, " - Hız ");
                drawButton(context, left + 176, top + 75, " + Hız ");
            } else {
                context.drawText(textRenderer, "Bu modül için ek ayar bulunmuyor.", left + 16, top + 70, 0xFF94A3B8, false);
            }
            super.render(context, mouseX, mouseY, delta);
        }

        private void drawButton(DrawContext context, int x, int y, String label) {
            context.fill(x, y, x + 138, y + 22, 0xFF334155);
            context.drawCenteredTextWithShadow(textRenderer, label, x + 69, y + 7, 0xFFFFFFFF);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
            int left = (width - 330) / 2;
            int top = (height - 200) / 2;
            if ("Attack Aura".equals(module)) {
                if (inside(mouseX, mouseY, left + 16, top + 60, 138, 22)) {
                    attackRange = Math.max(1.0, attackRange - 0.5);
                    return true;
                }
                if (inside(mouseX, mouseY, left + 176, top + 60, 138, 22)) {
                    attackRange = Math.min(6.0, attackRange + 0.5);
                    return true;
                }
                if (inside(mouseX, mouseY, left + 16, top + 92, 138, 22)) {
                    ENABLED.put("Criticals", !ENABLED.get("Criticals"));
                    return true;
                }
                if (inside(mouseX, mouseY, left + 176, top + 92, 138, 22)) {
                    shieldBreakEnabled = !shieldBreakEnabled;
                    return true;
                }
                if (inside(mouseX, mouseY, left + 16, top + 145, 138, 22)) {
                    attackIntervalTicks = Math.max(1, attackIntervalTicks - 1);
                    return true;
                }
                if (inside(mouseX, mouseY, left + 176, top + 145, 138, 22)) {
                    attackIntervalTicks = Math.min(40, attackIntervalTicks + 1);
                    return true;
                }
            } else if ("Fly".equals(module)) {
                if (inside(mouseX, mouseY, left + 16, top + 75, 138, 22)) {
                    flySpeed = Math.max(0.10, flySpeed - 0.05);
                    return true;
                }
                if (inside(mouseX, mouseY, left + 176, top + 75, 138, 22)) {
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
        if (key == GLFW.GLFW_KEY_UNKNOWN) return "ATANMADI";
        String name = GLFW.glfwGetKeyName(key, 0);
        if (name != null) return name.toUpperCase();
        return "KEY_" + key;
    }
}
