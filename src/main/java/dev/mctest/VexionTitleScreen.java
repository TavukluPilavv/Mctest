package dev.mctest;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class VexionTitleScreen extends Screen {
    private static final List<String> ACCOUNTS = new ArrayList<>();
    private static String selectedAccount = "";
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static boolean accountsLoaded;
    private TextFieldWidget nameField;
    private boolean accountScreen;
    private String notice = "";

    public VexionTitleScreen() {
        super(Text.literal("Vexion Client"));
        loadAccounts();
    }

    private static Path accountFile() {
        return MinecraftClient.getInstance().runDirectory.toPath().resolve("vexion-accounts.txt");
    }

    private static void loadAccounts() {
        if (accountsLoaded) return;
        accountsLoaded = true;
        try {
            Path path = accountFile();
            if (Files.exists(path)) {
                List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
                if (!lines.isEmpty()) selectedAccount = lines.get(0);
                for (int i = 1; i < lines.size(); i++) {
                    String name = lines.get(i).trim();
                    if (validName(name) && !ACCOUNTS.contains(name)) ACCOUNTS.add(name);
                }
                if (!ACCOUNTS.contains(selectedAccount)) selectedAccount = ACCOUNTS.isEmpty() ? "" : ACCOUNTS.get(0);
            }
        } catch (IOException ignored) { }
    }

    private static void saveAccounts() {
        try {
            List<String> lines = new ArrayList<>();
            lines.add(selectedAccount);
            lines.addAll(ACCOUNTS);
            Files.write(accountFile(), lines, StandardCharsets.UTF_8);
        } catch (IOException ignored) { }
    }

    private static boolean validName(String name) {
        return name != null && !name.isEmpty() && name.length() <= 16 && name.matches("[A-Za-z0-9_]+");
    }

    @Override
    protected void init() {
        int center = width / 2;
        int buttonWidth = Math.min(260, width - 36);
        int left = center - buttonWidth / 2;
        if (accountScreen) {
            nameField = new TextFieldWidget(textRenderer, center - 130, height / 2 - 65, 260, 25, Text.literal("Oyuncu adı"));
            nameField.setPlaceholder(Text.literal("Minecraft kullanıcı adını yaz"));
            addDrawableChild(nameField);
            addDrawableChild(ButtonWidget.builder(Text.literal("Add'e Ekle"), b -> {
                String name = nameField.getText().trim();
                if (!validName(name)) {
                    notice = "İsim 1-16 karakter olmalı (A-Z, 0-9, _).";
                } else if (ACCOUNTS.contains(name)) {
                    notice = "Bu isim zaten ekli.";
                } else {
                    ACCOUNTS.add(name);
                    if (selectedAccount.isEmpty()) selectedAccount = name;
                    notice = name + " eklendi.";
                    nameField.setText("");
                    saveAccounts();
                }
            }).dimensions(center - 130, height / 2 - 32, 125, 23).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Geri"), b -> {
                accountScreen = false;
                notice = "";
                clearAndInit();
            }).dimensions(center + 5, height / 2 - 32, 125, 23).build());
        } else {
            addDrawableChild(ButtonWidget.builder(Text.literal("Tek Oyunculu"), b -> client.setScreen(new SelectWorldScreen(this)))
                    .dimensions(left, height / 2 - 38, buttonWidth, 25).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Çok Oyunculu"), b -> client.setScreen(new MultiplayerScreen(this)))
                    .dimensions(left, height / 2 - 7, buttonWidth, 25).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Ayarlar"), b -> client.setScreen(new OptionsScreen(this, client.options)))
                    .dimensions(left, height / 2 + 24, buttonWidth, 25).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Hesap Değiştir"), b -> {
                accountScreen = true;
                notice = "";
                clearAndInit();
            }).dimensions(left, height / 2 + 55, buttonWidth, 25).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Çıkış"), b -> client.scheduleStop())
                    .dimensions(left, height / 2 + 86, buttonWidth, 25).build());
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Keep Minecraft's own panorama/blur background and add a cinematic dark overlay.
        renderBackground(context, mouseX, mouseY, delta);
        context.fillGradient(0, 0, width, height, 0x66101B2B, 0xAA05070C);
        context.fill(0, 0, width, 34, 0x66050912);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Vexion Client - 1.21.4"), width / 2, Math.max(18, height / 2 - 112), 0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer, "Türkiye saati  " + LocalTime.now(ZoneId.of("Europe/Istanbul")).format(CLOCK), 12, 12, 0xFFFFFFFF);

        int panelWidth = Math.min(300, width - 20);
        int panelLeft = (width - panelWidth) / 2;
        int panelTop = accountScreen ? height / 2 - 92 : height / 2 - 48;
        int panelBottom = accountScreen ? height / 2 + Math.min(190, ACCOUNTS.size() * 19 + 92) : height / 2 + 122;
        context.fill(panelLeft + 4, panelTop + 5, panelLeft + panelWidth + 4, panelBottom + 5, 0x55000000);
        context.fill(panelLeft, panelTop, panelLeft + panelWidth, panelBottom, 0xA8141A24);
        context.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + 1, 0x99D8E5F4);

        if (accountScreen) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("HESAP DEĞİŞTİR"), width / 2, height / 2 - 86, 0xFFFFFFFF);
            int y = height / 2 + 1;
            for (String name : ACCOUNTS) {
                boolean selected = name.equals(selectedAccount);
                int rowLeft = width / 2 - 130;
                int rowRight = width / 2 + 130;
                context.fill(rowLeft, y - 3, rowRight, y + 15, selected ? 0x5533DD77 : 0x332B3545);
                context.drawTextWithShadow(textRenderer, selected ? "● " + name : "○ " + name,
                        rowLeft + 9, y + 1, selected ? 0xFF55FF77 : 0xFFE3E8F0);
                context.drawTextWithShadow(textRenderer, selected ? "AKTİF" : "SEÇ",
                        rowRight - 40, y + 1, selected ? 0xFF55FF77 : 0xFFB8C2D0);
                y += 19;
            }
            if (ACCOUNTS.isEmpty()) {
                context.drawCenteredTextWithShadow(textRenderer, Text.literal("Henüz hesap eklenmedi"), width / 2, height / 2 + 10, 0xFFCCD3DF);
            }
            if (!notice.isEmpty()) context.drawCenteredTextWithShadow(textRenderer, Text.literal(notice), width / 2, height / 2 + Math.min(190, ACCOUNTS.size() * 19 + 63), 0xFFFFD479);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("Sol tık: seç  •  Sağ tık: sil"), width / 2, height - 14, 0xFFD5DCE7);
        } else {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("VEXION CLIENT"), width / 2, height / 2 - 82, 0xFFFFFFFF);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("Seçili isim: " + (selectedAccount.isEmpty() ? "Yok" : selectedAccount)),
                    width / 2, height / 2 + 117, selectedAccount.isEmpty() ? 0xFFDFDFDF : 0xFF55FF77);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (accountScreen && nameField != null) {
            int y = height / 2 + 1;
            for (String name : new ArrayList<>(ACCOUNTS)) {
                if (mouseX >= width / 2.0 - 130 && mouseX <= width / 2.0 + 130 && mouseY >= y - 3 && mouseY <= y + 15) {
                    if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                        ACCOUNTS.remove(name);
                        if (name.equals(selectedAccount)) selectedAccount = ACCOUNTS.isEmpty() ? "" : ACCOUNTS.get(0);
                        notice = name + " silindi.";
                        saveAccounts();
                    } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                        selectedAccount = name;
                        notice = name + " seçildi. Bu yalnızca menüdeki seçimi değiştirir.";
                        saveAccounts();
                    }
                    return true;
                }
                y += 19;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean shouldPause() { return false; }
}
