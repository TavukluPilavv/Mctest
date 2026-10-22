package dev.mctest;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
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

import java.util.*;

public final class MctestClient implements ClientModInitializer {
    private static boolean menuWasDown;
    private static boolean flyWasRun;

    // Modüller ve Kategoriler
    private static final Map<String, Boolean> ENABLED = new LinkedHashMap<>();
    private static final Map<String, Integer> BINDS = new LinkedHashMap<>();
    private static final Map<String, Boolean> KEY_WAS_DOWN = new LinkedHashMap<>();

    // Combat / Attack Aura Ayarları
    private static double attackRange = 3.5;
    private static int attackIntervalTicks = 10;
    private static int attackDelayTicks;
    private static boolean targetPlayers = true;
    private static boolean targetMobs = true;
    private static boolean criticalsEnabled = true;
    private static boolean shieldBreakEnabled = true;

    // Movement Ayarları
    private static double flySpeed = 0.45;
    private static double speedMultiplier = 1.25;

    // Hedef (Target HUD için)
    private static LivingEntity currentTarget = null;

    // Alt Hesap Sistemi (Alt Manager)
    public static final List<String> altAccounts = new ArrayList<>(List.of("Player_Vexion", "TavukluPilav"));
    public static String activeAccount = "Player_Vexion";

    // HUD Sürükleme Konumları
    public static int targetHudX = 100, targetHudY = 40;
    public static int keybindsHudX = 20, keybindsHudY = 100;
    public static int potionsHudX = 20, potionsHudY = 220;
    private static String draggingHud = null;
    private static int dragOffsetX, dragOffsetY;

    static {
        // Modül listesi (Combat, Movement, Render, Display)
        String[] modules = {
                "Attack Aura", "Fly", "Speed", "X-Ray",
                "TargetHUD", "Keybinds HUD", "Potions HUD"
        };
        for (String m : modules) {
            ENABLED.put(m, false);
            BINDS.put(m, GLFW.GLFW_KEY_UNKNOWN);
            KEY_WAS_DOWN.put(m, false);
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
                    client.setScreen(new VexionClickGUI());
                } else if (client.currentScreen instanceof VexionClickGUI) {
                    client.setScreen(null);
                }
            }
            menuWasDown = menuDown;

            if (client.currentScreen != null) return;

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

            // Hilelerin Çalıştırılması
            if (ENABLED.get("Attack Aura")) runAttackAura(client);
            else currentTarget = null;

            if (ENABLED.get("Fly")) runLocalFly(client);
            else resetFly(client);

            if (ENABLED.get("Speed")) runSpeed(client);
        });

        // HUD Çizimleri ve Sürükleme Mantığı
        HudRenderCallback.EVENT.register((context, tickDelta) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.world == null) return;

            double mouseX = client.mouse.getX() * client.getWindow().getScaledWidth() / client.getWindow().getWidth();
            double mouseY = client.mouse.getY() * client.getWindow().getScaledHeight() / client.getWindow().getHeight();

            if (draggingHud != null) {
                if (GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_RELEASE) {
                    draggingHud = null;
                } else {
                    if (draggingHud.equals("TargetHUD")) { targetHudX = (int)mouseX - dragOffsetX; targetHudY = (int)mouseY - dragOffsetY; }
                    if (draggingHud.equals("Keybinds HUD")) { keybindsHudX = (int)mouseX - dragOffsetX; keybindsHudY = (int)mouseY - dragOffsetY; }
                    if (draggingHud.equals("Potions HUD")) { potionsHudX = (int)mouseX - dragOffsetX; potionsHudY = (int)mouseY - dragOffsetY; }
                }
            }

            // 1. Target HUD
            if (ENABLED.get("TargetHUD") && ENABLED.get("Attack Aura") && currentTarget != null && currentTarget.isAlive()) {
                context.fill(targetHudX, targetHudY, targetHudX + 160, targetHudY + 45, 0xDD0F172A);
                context.fill(targetHudX, targetHudY, targetHudX + 160, targetHudY + 3, 0xFF8B5CF6);
                context.drawText(client.textRenderer, currentTarget.getName().getString(), targetHudX + 8, targetHudY + 8, 0xFFFFFFFF, false);
                context.drawText(client.textRenderer, String.format("Can: %.1f / %.1f", currentTarget.getHealth(), currentTarget.getMaxHealth()), targetHudX + 8, targetHudY + 24, 0xFF4ADE80, false);
            }

            // 2. Keybinds HUD (Klavye Simgeli)
            if (ENABLED.get("Keybinds HUD")) {
                context.fill(keybindsHudX, keybindsHudY, keybindsHudX + 120, keybindsHudY + 20 + (countActiveModules() * 12), 0xDD0F172A);
                context.fill(keybindsHudX, keybindsHudY, keybindsHudX + 120, keybindsHudY + 2, 0xFFC084FC);
                context.drawText(client.textRenderer, "⌨ Keybinds", keybindsHudX + 8, keybindsHudY + 6, 0xFFC084FC, false);
                int kY = keybindsHudY + 20;
                for (Map.Entry<String, Boolean> entry : ENABLED.entrySet()) {
                    if (entry.getValue() && !entry.getKey().contains("HUD")) {
                        int key = BINDS.get(entry.getKey());
                        context.drawText(client.textRenderer, entry.getKey() + " [" + (key == GLFW.GLFW_KEY_UNKNOWN ? "-" : keyName(key)) + "]", keybindsHudX + 8, kY, 0xFFE2E8F0, false);
                        kY += 12;
                    }
                }
            }

            // 3. Potions HUD (İksir Simgeli)
            if (ENABLED.get("Potions HUD")) {
                int pCount = client.player.getStatusEffects().size();
                if (pCount > 0) {
                    context.fill(potionsHudX, potionsHudY, potionsHudX + 130, potionsHudY + 20 + (pCount * 12), 0xDD0F172A);
                    context.fill(potionsHudX, potionsHudY, potionsHudX + 130, potionsHudY + 2, 0xFF38BDF8);
                    context.drawText(client.textRenderer, "🧪 Potions", potionsHudX + 8, potionsHudY + 6, 0xFF38BDF8, false);
                    int pY = potionsHudY + 20;
                    for (StatusEffectInstance effect : client.player.getStatusEffects()) {
                        String name = effect.getEffectType().value().getName().getString();
                        context.drawText(client.textRenderer, name + " " + (effect.getAmplifier() + 1), potionsHudX + 8, pY, 0xFFE2E8F0, false);
                        pY += 12;
                    }
                }
            }
        });
    }

    private static int countActiveModules() {
        int c = 0;
        for (Map.Entry<String, Boolean> e : ENABLED.entrySet()) {
            if (e.getValue() && !e.getKey().contains("HUD")) c++;
        }
        return c;
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
        ).stream().min(Comparator.comparingDouble(player::squaredDistanceTo)).orElse(null);

        if (currentTarget == null) return;

        // F5 Modu için Silent Aim (Kafa ve gövde yönünü hedefe çevirir)
        Vec3d delta = currentTarget.getEyePos().subtract(player.getEyePos());
        float yaw = (float) (MathHelper.atan2(delta.z, delta.x) * (180.0 / Math.PI)) - 90.0F;
        player.setHeadYaw(yaw);
        player.setBodyYaw(yaw);

        if (attackDelayTicks > 0) {
            attackDelayTicks--;
            return;
        }

        if (player.getAttackCooldownProgress(0.5F) < 0.9F) return;

        // Kritik Vuruş Mantığı (Açıkken havada düşüş/zıplama anında kesin vurur)
        if (criticalsEnabled) {
            boolean isAirborne = !player.isOnGround() && !player.isClimbing() && !player.isTouchingWater();
            if (!isAirborne) {
                player.jump(); // Otomatik zıplayarak kritik şartını tetikler
                return;
            }
        }

        // Kalkan Kırma (Herhangi bir itemle kalkanı kırar)
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

    private static void runSpeed(MinecraftClient client) {
        if (client.player == null) return;
        if (client.player.horizontalSpeed > 0.01F) {
            Vec3d vel = client.player.getVelocity();
            client.player.setVelocity(vel.x * speedMultiplier, vel.y, vel.z * speedMultiplier);
        }
    }

    // --- DİKEY KATEGORİKLİ MODERN CLICKGUI ---
    private static final class VexionClickGUI extends Screen {
        private String waitingForBind;
        private static final int W = 520;
        private static final int H = 320;

        private VexionClickGUI() {
            super(Text.literal("Vexion Client ClickGUI"));
        }

        @Override
        public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            context.fill(0, 0, width, height, 0x88000000);
            int left = (width - W) / 2;
            int top = (height - H) / 2;

            // Ana Arka Plan
            context.fill(left, top, left + W, top + H, 0xFF0F172A);
            context.fill(left, top, left + W, top + 36, 0xFF1E293B);
            context.drawText(textRenderer, "Vexion Client | Modern Menu", left + 15, top + 12, 0xFFFFFFFF, false);

            // Dikey Sütunlar (Combat, Movement, Render, Display)
            drawColumn(context, left + 15, top + 50, "COMBAT", List.of("Attack Aura"));
            drawColumn(context, left + 145, top + 50, "MOVEMENT", List.of("Fly", "Speed"));
            drawColumn(context, left + 275, top + 50, "RENDER", List.of("X-Ray"));
            drawColumn(context, left + 405, top + 50, "DISPLAY", List.of("TargetHUD", "Keybinds HUD", "Potions HUD"));

            super.render(context, mouseX, mouseY, delta);
        }

        private void drawColumn(DrawContext context, int x, int y, String title, List<String> modules) {
            context.drawText(textRenderer, title, x, y, 0xFFC084FC, false);
            int mY = y + 16;
            for (String mod : modules) {
                boolean enabled = ENABLED.get(mod);
                context.fill(x, mY, x + 110, mY + 24, 0xFF1E293B);
                context.drawText(textRenderer, mod, x + 6, mY + 8, 0xFFFFFFFF, false);

                // Toggle Kutusu
                context.fill(x + 80, mY + 6, x + 104, mY + 18, enabled ? 0xFF8B5CF6 : 0xFF334155);
                context.fill(enabled ? x + 92 : x + 82, mY + 8, (enabled ? x + 92 : x + 82) + 10, mY + 16, 0xFFFFFFFF);
                mY += 30;
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            int left = (width - W) / 2;
            int top = (height - H) / 2;

            // Tıklama Kontrolleri (Dikey Sütunlara Göre)
            if (checkClick(mouseX, mouseY, left + 15, top + 66, List.of("Attack Aura"), button)) return true;
            if (checkClick(mouseX, mouseY, left + 145, top + 66, List.of("Fly", "Speed"), button)) return true;
            if (checkClick(mouseX, mouseY, left + 275, top + 66, List.of("X-Ray"), button)) return true;
            if (checkClick(mouseX, mouseY, left + 405, top + 66, List.of("TargetHUD", "Keybinds HUD", "Potions HUD"), button)) return true;

            return super.mouseClicked(mouseX, mouseY, button);
        }

        private boolean checkClick(double mx, double my, int x, int y, List<String> modules, int button) {
            int mY = y;
            for (String mod : modules) {
                if (mx >= x && mx <= x + 110 && my >= mY && my <= mY + 24) {
                    if (button == 0) ENABLED.put(mod, !ENABLED.get(mod));
                    else if (button == 1) client.setScreen(new ModuleSettingsScreen(mod));
                    else if (button == 2) waitingForBind = mod;
                    return true;
                }
                mY += 30;
            }
            return false;
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

    // --- MODÜL AYARLARI EKRANI (Sağ Tık) ---
    private static final class ModuleSettingsScreen extends Screen {
        private final String module;
        private ModuleSettingsScreen(String module) {
            super(Text.literal(module + " Ayarları"));
            this.module = module;
        }

        @Override
        public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            context.fill(0, 0, width, height, 0x88000000);
            int left = (width - 320) / 2;
            int top = (height - 200) / 2;
            context.fill(left, top, left + 320, top + 200, 0xFF0F172A);
            context.drawText(textRenderer, module + " Ayarları", left + 12, top + 12, 0xFFFFFFFF, false);

            if ("Attack Aura".equals(module)) {
                drawBtn(context, left + 16, top + 45, "Oyuncular: " + (targetPlayers ? "AÇIK" : "KAPALI"));
                drawBtn(context, left + 166, top + 45, "Yaratıklar: " + (targetMobs ? "AÇIK" : "KAPALI"));
                drawBtn(context, left + 16, top + 80, "Kritikler: " + (criticalsEnabled ? "AÇIK" : "KAPALI"));
                drawBtn(context, left + 166, top + 80, "Kalkan Kırma: " + (shieldBreakEnabled ? "AÇIK" : "KAPALI"));
                drawBtn(context, left + 16, top + 115, "Menzil: " + String.format("%.1f", attackRange));
            } else if ("Speed".equals(module)) {
                drawBtn(context, left + 16, top + 45, "Hız Çarpanı: " + String.format("%.2f", speedMultiplier));
            }
            super.render(context, mouseX, mouseY, delta);
        }

        private void drawBtn(DrawContext context, int x, int y, String label) {
            context.fill(x, y, x + 138, y + 24, 0xFF1E293B);
            context.drawCenteredTextWithShadow(textRenderer, label, x + 69, y + 8, 0xFFFFFFFF);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            int left = (width - 320) / 2;
            int top = (height - 200) / 2;
            if ("Attack Aura".equals(module)) {
                if (inside(mouseX, mouseY, left + 16, top + 45, 138, 24)) targetPlayers = !targetPlayers;
                if (inside(mouseX, mouseY, left + 166, top + 45, 138, 24)) targetMobs = !targetMobs;
                if (inside(mouseX, mouseY, left + 16, top + 80, 138, 24)) criticalsEnabled = !criticalsEnabled;
                if (inside(mouseX, mouseY, left + 166, top + 80, 138, 24)) shieldBreakEnabled = !shieldBreakEnabled;
                if (inside(mouseX, mouseY, left + 16, top + 115, 138, 24)) attackRange = attackRange >= 6.0 ? 3.0 : attackRange + 0.5;
            } else if ("Speed".equals(module)) {
                if (inside(mouseX, mouseY, left + 16, top + 45, 138, 24)) speedMultiplier = speedMultiplier >= 2.0 ? 1.1 : speedMultiplier + 0.15;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        private boolean inside(double x, double y, int bx, int by, int w, int h) {
            return x >= bx && x <= bx + w && y >= by && y <= by + h;
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                client.setScreen(new VexionClickGUI());
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean shouldPause() { return false; }
    }

    private static String keyName(int key) {
        if (key == GLFW.GLFW_KEY_UNKNOWN) return "-";
        String name = GLFW.glfwGetKeyName(key, 0);
        return name != null ? name.toUpperCase() : "KEY_" + key;
    }
}
