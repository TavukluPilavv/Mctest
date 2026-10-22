package dev.mctest;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
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

import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class MctestClient implements ClientModInitializer {
    private static boolean menuWasDown;
    private static boolean flyWasRun;

    private static final Map<String, Boolean> ENABLED = new LinkedHashMap<>();
    private static final Map<String, Integer> BINDS = new LinkedHashMap<>();
    private static final Map<String, Boolean> KEY_WAS_DOWN = new LinkedHashMap<>();

    private static double attackRange = 3.5;
    private static int attackIntervalTicks = 9;
    private static int attackDelayTicks;
    private static boolean targetPlayers = true;
    private static boolean targetMobs = true;
    private static boolean criticalsEnabled = true;
    private static boolean shieldBreakEnabled = true;

    private static double flySpeed = 0.45;
    private static double speedMultiplier = 1.25;

    private static LivingEntity currentTarget = null;
    private static float targetRotationAngle = 0.0f;

    public static final List<String> altAccounts = new ArrayList<>(List.of("Player_Vexion", "TavukluPilav"));
    public static String activeAccount = "Player_Vexion";

    public static final List<String> staffMembers = new ArrayList<>(List.of("Admin_Vexion", "Mod_Ahmet", "Helper_Zeynep"));

    public static int targetHudX = 100, targetHudY = 40;
    public static int keybindsHudX = 20, keybindsHudY = 100;
    public static int potionsHudX = 20, potionsHudY = 220;
    public static int staffHudX = 200, staffHudY = 100;
    
    private static String draggingHud = null;
    private static int dragOffsetX, dragOffsetY;

    static {
        String[] modules = {
                "Attack Aura", "Fly", "Speed", "X-Ray",
                "TargetHUD", "Keybinds HUD", "Potions HUD", "Staff HUD", "HUD Editor"
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

            if (ENABLED.get("Attack Aura")) runAttackAura(client);
            else currentTarget = null;

            if (ENABLED.get("Fly")) runLocalFly(client);
            else resetFly(client);

            if (ENABLED.get("Speed")) runSpeed(client);
        });

        WorldRenderEvents.AFTER_TRANSLUCENT.register(this::renderTargetIndicator);

        HudRenderCallback.EVENT.register((context, tickDelta) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.world == null) return;

            double mouseX = client.mouse.getX() * client.getWindow().getScaledWidth() / client.getWindow().getWidth();
            double mouseY = client.mouse.getY() * client.getWindow().getScaledHeight() / client.getWindow().getHeight();

            boolean hudEditorActive = ENABLED.get("HUD Editor");
            if (hudEditorActive) {
                if (draggingHud != null) {
                    if (GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_RELEASE) {
                        draggingHud = null;
                    } else {
                        if (draggingHud.equals("TargetHUD")) { targetHudX = (int)mouseX - dragOffsetX; targetHudY = (int)mouseY - dragOffsetY; }
                        if (draggingHud.equals("Keybinds HUD")) { keybindsHudX = (int)mouseX - dragOffsetX; keybindsHudY = (int)mouseY - dragOffsetY; }
                        if (draggingHud.equals("Potions HUD")) { potionsHudX = (int)mouseX - dragOffsetX; potionsHudY = (int)mouseY - dragOffsetY; }
                        if (draggingHud.equals("Staff HUD")) { staffHudX = (int)mouseX - dragOffsetX; staffHudY = (int)mouseY - dragOffsetY; }
                    }
                } else if (GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS) {
                    if (isInsideBox(mouseX, mouseY, targetHudX, targetHudY, 160, 45)) { draggingHud = "TargetHUD"; dragOffsetX = (int)mouseX - targetHudX; dragOffsetY = (int)mouseY - targetHudY; }
                    else if (isInsideBox(mouseX, mouseY, keybindsHudX, keybindsHudY, 120, 40)) { draggingHud = "Keybinds HUD"; dragOffsetX = (int)mouseX - keybindsHudX; dragOffsetY = (int)mouseY - keybindsHudY; }
                    else if (isInsideBox(mouseX, mouseY, potionsHudX, potionsHudY, 130, 40)) { draggingHud = "Potions HUD"; dragOffsetX = (int)mouseX - potionsHudX; dragOffsetY = (int)mouseY - potionsHudY; }
                    else if (isInsideBox(mouseX, mouseY, staffHudX, staffHudY, 130, 40)) { draggingHud = "Staff HUD"; dragOffsetX = (int)mouseX - staffHudX; dragOffsetY = (int)mouseY - staffHudY; }
                }
            }

            // Gerçekçi Cam Temalı HUD Çizimleri
            if (ENABLED.get("TargetHUD") && ENABLED.get("Attack Aura") && currentTarget != null && currentTarget.isAlive()) {
                context.fill(targetHudX, targetHudY, targetHudX + 150, targetHudY + 40, 0xC0000000);
                context.fill(targetHudX, targetHudY, targetHudX + 150, targetHudY + 1, 0xFF7C3AED);
                context.drawText(client.textRenderer, currentTarget.getName().getString(), targetHudX + 6, targetHudY + 6, 0xFFFFFFFF, false);
                context.drawText(client.textRenderer, String.format("HP: %.1f", currentTarget.getHealth()), targetHudX + 6, targetHudY + 20, 0xFF10B981, false);
            }

            if (ENABLED.get("Keybinds HUD")) {
                context.fill(keybindsHudX, keybindsHudY, keybindsHudX + 120, keybindsHudY + 18 + (countActiveModules() * 11), 0xC0000000);
                context.fill(keybindsHudX, keybindsHudY, keybindsHudX + 120, keybindsHudY + 1, 0xFF8B5CF6);
                context.drawText(client.textRenderer, "Keybinds", keybindsHudX + 6, keybindsHudY + 4, 0xFFC084FC, false);
                int kY = keybindsHudY + 16;
                for (Map.Entry<String, Boolean> entry : ENABLED.entrySet()) {
                    if (entry.getValue() && !entry.getKey().contains("HUD")) {
                        int key = BINDS.get(entry.getKey());
                        context.drawText(client.textRenderer, entry.getKey() + " [" + (key == GLFW.GLFW_KEY_UNKNOWN ? "-" : keyName(key)) + "]", keybindsHudX + 6, kY, 0xFFE2E8F0, false);
                        kY += 11;
                    }
                }
            }

            // Süreli Potions HUD
            if (ENABLED.get("Potions HUD")) {
                Collection<StatusEffectInstance> effects = client.player.getStatusEffects();
                if (!effects.isEmpty()) {
                    context.fill(potionsHudX, potionsHudY, potionsHudX + 130, potionsHudY + 18 + (effects.size() * 11), 0xC0000000);
                    context.fill(potionsHudX, potionsHudY, potionsHudX + 130, potionsHudY + 1, 0xFF06B6D4);
                    context.drawText(client.textRenderer, "Potions", potionsHudX + 6, potionsHudY + 4, 0xFF38BDF8, false);
                    int pY = potionsHudY + 16;
                    for (StatusEffectInstance effect : effects) {
                        String name = effect.getEffectType().value().getName().getString();
                        int durationSeconds = effect.getDuration() / 20;
                        String timeStr = String.format("%02d:%02d", durationSeconds / 60, durationSeconds % 60);
                        context.drawText(client.textRenderer, name + " (" + timeStr + ")", potionsHudX + 6, pY, 0xFFE2E8F0, false);
                        pY += 11;
                    }
                }
            }

            if (ENABLED.get("Staff HUD")) {
                context.fill(staffHudX, staffHudY, staffHudX + 120, staffHudY + 18 + (staffMembers.size() * 11), 0xC0000000);
                context.fill(staffHudX, staffHudY, staffHudX + 120, staffHudY + 1, 0xFFF43F5E);
                context.drawText(client.textRenderer, "Staff List", staffHudX + 6, staffHudY + 4, 0xFFF43F5E, false);
                int sY = staffHudY + 16;
                for (String staff : staffMembers) {
                    context.drawText(client.textRenderer, staff, staffHudX + 6, sY, 0xFFE2E8F0, false);
                    sY += 11;
                }
            }
        });
    }

    private static boolean isInsideBox(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private void renderTargetIndicator(WorldRenderContext context) {
        if (currentTarget == null || !currentTarget.isAlive() || !ENABLED.get("Attack Aura")) return;
        targetRotationAngle += 0.05f;
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

        // Göğse Kilitlenme (Kafa Yerine Gövde Yüksekliği 0.40)
        Vec3d chestPos = currentTarget.getPos().add(0, currentTarget.getHeight() * 0.40, 0);
        Vec3d delta = chestPos.subtract(player.getEyePos());
        float yaw = (float) (MathHelper.atan2(delta.z, delta.x) * (180.0 / Math.PI)) - 90.0F;

        // W Tuşu basılıyken hedefe yumuşak yönelme (Silent Rotation / Ekran Sarsılmaz)
        if (client.options.forwardKey.isPressed()) {
            player.setYaw(yaw);
        }
        player.setHeadYaw(yaw);

        if (attackDelayTicks > 0) {
            attackDelayTicks--;
            return;
        }

        if (player.getAttackCooldownProgress(0.5F) < 0.9F) return;

        // Manuel Kritik Kontrolü (Zıplayıp düşüş anında vurur)
        if (criticalsEnabled) {
            boolean isAirborne = !player.isOnGround() && !player.isClimbing() && !player.isTouchingWater();
            if (!isAirborne || player.fallDistance <= 0.0F) {
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

    private static void runSpeed(MinecraftClient client) {
        if (client.player == null) return;
        if (client.player.getVelocity().horizontalLength() > 0.01) {
            Vec3d vel = client.player.getVelocity();
            client.player.setVelocity(vel.x * speedMultiplier, vel.y, vel.z * speedMultiplier);
        }
    }

    public static final class VexionTitleScreen extends Screen {
        public VexionTitleScreen() {
            super(Text.literal("Vexion Main Menu"));
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            context.fill(0, 0, width, height, 0xFF090D16);

            ZonedDateTime trTime = ZonedDateTime.now(ZoneId.of("Europe/Istanbul"));
            String timeStr = trTime.format(DateTimeFormatter.ofPattern("HH:mm:ss"));

            context.drawCenteredTextWithShadow(textRenderer, "Vexion Client v1.0 | TSI: " + timeStr, width / 2, 12, 0xFFA855F7);

            int btnX = width / 2 - 90;
            int btnY = height / 2 - 15;
            context.fill(btnX, btnY, btnX + 180, btnY + 22, 0xFF1E293B);
            context.drawCenteredTextWithShadow(textRenderer, "Hesap: " + activeAccount, width / 2, btnY + 7, 0xFFFFFFFF);

            super.render(context, mouseX, mouseY, delta);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            int btnX = width / 2 - 90;
            int btnY = height / 2 - 15;
            if (mouseX >= btnX && mouseX <= btnX + 180 && mouseY >= btnY && mouseY <= btnY + 22) {
                int nextIndex = (altAccounts.indexOf(activeAccount) + 1) % altAccounts.size();
                activeAccount = altAccounts.get(nextIndex);
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
    }

    private static final class VexionClickGUI extends Screen {
        private String waitingForBind;
        private static final int W = 480;
        private static final int H = 300;

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

            context.fill(left, top, left + W, top + H, 0xF0090D16);
            context.fill(left, top, left + W, top + 28, 0xFF111827);
            context.drawText(textRenderer, "Vexion Client | Modern Menu", left + 12, top + 10, 0xFFC084FC, false);

            if (waitingForBind != null) {
                context.drawCenteredTextWithShadow(textRenderer, "Tuş seçin: " + waitingForBind, width / 2, top + 35, 0xFFF43F5E);
            }

            drawColumn(context, left + 12, top + 50, "COMBAT", List.of("Attack Aura"));
            drawColumn(context, left + 132, top + 50, "MOVEMENT", List.of("Fly", "Speed"));
            drawColumn(context, left + 252, top + 50, "RENDER", List.of("X-Ray"));
            drawColumn(context, left + 372, top + 50, "DISPLAY", List.of("TargetHUD", "Keybinds HUD", "Potions HUD", "Staff HUD", "HUD Editor"));

            super.render(context, mouseX, mouseY, delta);
        }

        private void drawColumn(DrawContext context, int x, int y, String title, List<String> modules) {
            context.drawText(textRenderer, title, x, y, 0xFF38BDF8, false);
            int mY = y + 14;
            for (String mod : modules) {
                boolean enabled = ENABLED.get(mod);
                context.fill(x, mY, x + 100, mY + 20, 0xFF1F2937);
                context.drawText(textRenderer, mod, x + 5, mY + 6, 0xFFFFFFFF, false);

                context.fill(x + 75, mY + 4, x + 95, mY + 16, enabled ? 0xFFA855F7 : 0xFF374151);
                context.fill(enabled ? x + 85 : x + 77, mY + 6, (enabled ? x + 85 : x + 77) + 8, mY + 14, 0xFFFFFFFF);
                mY += 25;
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            int left = (width - W) / 2;
            int top = (height - H) / 2;

            if (checkClick(mouseX, mouseY, left + 12, top + 64, List.of("Attack Aura"), button)) return true;
            if (checkClick(mouseX, mouseY, left + 132, top + 64, List.of("Fly", "Speed"), button)) return true;
            if (checkClick(mouseX, mouseY, left + 252, top + 64, List.of("X-Ray"), button)) return true;
            if (checkClick(mouseX, mouseY, left + 372, top + 64, List.of("TargetHUD", "Keybinds HUD", "Potions HUD", "Staff HUD", "HUD Editor"), button)) return true;

            return super.mouseClicked(mouseX, mouseY, button);
        }

        private boolean checkClick(double mx, double my, int x, int y, List<String> modules, int button) {
            int mY = y;
            for (String mod : modules) {
                if (mx >= x && mx <= x + 100 && my >= mY && my <= mY + 20) {
                    if (button == 0) ENABLED.put(mod, !ENABLED.get(mod));
                    else if (button == 1) client.setScreen(new ModuleSettingsScreen(mod));
                    else if (button == 2) waitingForBind = mod;
                    return true;
                }
                mY += 25;
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

    // Listeli Gerçekçi Ayarlar Ekranı
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
            int left = (width - 240) / 2;
            int top = (height - 180) / 2;
            context.fill(left, top, left + 240, top + 180, 0xF0090D16);
            context.drawText(textRenderer, module + " Ayarları", left + 10, top + 10, 0xFFC084FC, false);

            if ("Attack Aura".equals(module)) {
                drawListRow(context, left + 10, top + 35, "• Oyuncular", targetPlayers ? "AÇIK" : "KAPALI");
                drawListRow(context, left + 10, top + 60, "• Yaratıklar", targetMobs ? "AÇIK" : "KAPALI");
                drawListRow(context, left + 10, top + 85, "• Kritikler", criticalsEnabled ? "AÇIK" : "KAPALI");
                drawListRow(context, left + 10, top + 110, "• Kalkan Kırma", shieldBreakEnabled ? "AÇIK" : "KAPALI");
                drawListRow(context, left + 10, top + 135, "• Menzil", String.format("%.1f", attackRange));
            } else if ("Speed".equals(module)) {
                drawListRow(context, left + 10, top + 35, "• Hız Çarpanı", String.format("%.2f", speedMultiplier));
            }
            super.render(context, mouseX, mouseY, delta);
        }

        private void drawListRow(DrawContext context, int x, int y, String title, String val) {
            context.fill(x, y, x + 220, y + 20, 0xFF1F2937);
            context.drawText(textRenderer, title, x + 8, y + 6, 0xFFE2E8F0, false);
            context.drawText(textRenderer, val, x + 160, y + 6, 0xFFA855F7, false);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            int left = (width - 240) / 2;
            int top = (height - 180) / 2;
            if ("Attack Aura".equals(module)) {
                if (inside(mouseX, mouseY, left + 10, top + 35, 220, 20)) targetPlayers = !targetPlayers;
                if (inside(mouseX, mouseY, left + 10, top + 60, 220, 20)) targetMobs = !targetMobs;
                if (inside(mouseX, mouseY, left + 10, top + 85, 220, 20)) criticalsEnabled = !criticalsEnabled;
                if (inside(mouseX, mouseY, left + 10, top + 110, 220, 20)) shieldBreakEnabled = !shieldBreakEnabled;
                if (inside(mouseX, mouseY, left + 10, top + 135, 220, 20)) {
                    attackRange += 0.5;
                    if (attackRange > 6.0) attackRange = 3.0;
                }
            } else if ("Speed".equals(module)) {
                if (inside(mouseX, mouseY, left + 10, top + 35, 220, 20)) speedMultiplier = speedMultiplier >= 2.0 ? 1.1 : speedMultiplier + 0.15;
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
