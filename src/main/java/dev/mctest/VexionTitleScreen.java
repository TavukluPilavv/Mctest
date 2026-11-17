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
            nameField = new TextFieldWidget(textRenderer, center - 130, height / 2 - 76, 260, 26, Text.literal("Oyuncu adı"));
            nameField.setPlaceholder(Text.literal("Minecraft kullanıcı adını yaz"));
            nameField.setDrawsBackground(true);
            addDrawableChild(nameField);

            int actionY = height / 2 + 112;
            int actionWidth = 82;
            int actionGap = 5;
            int actionLeft = center - (actionWidth * 3 + actionGap * 2) / 2;

            addDrawableChild(VexionButton.customBuilder(Text.literal("Hesap Ekle"), b -> {
                String name = nameField.getText().trim();
                if (!validName(name)) {
                    notice = "İsim 1-16 karakter olmalı (A-Z, 0-9, _).";
                } else if (ACCOUNTS.contains(name)) {
                    notice = "Bu isim zaten ekli.";
                } else {
                    ACCOUNTS.add(name);
                    if (selectedAccount.isEmpty()) selectedAccount = name;
                    notice = name + " eklendi. Oturum otomatik değişmez.";
                    nameField.setText("");
                    saveAccounts();
                }
            }).dimensions(actionLeft, actionY, actionWidth, 25).build());

            addDrawableChild(VexionButton.customBuilder(Text.literal("Sil"), b -> {
                if (selectedAccount.isEmpty() || !ACCOUNTS.remove(selectedAccount)) {
                    notice = "Silmek için önce bir hesap seç.";
                } else {
                    notice = selectedAccount + " listeden silindi.";
                    selectedAccount = "";
                    saveAccounts();
                }
            }).dimensions(actionLeft + actionWidth + actionGap, actionY, actionWidth, 25).build());

            addDrawableChild(VexionButton.customBuilder(Text.literal("Geri"), b -> {
                accountScreen = false;
                notice = "";
                clearAndInit();
            }).dimensions(actionLeft + (actionWidth + actionGap) * 2, actionY, actionWidth, 25).build());
        } else {
            addDrawableChild(VexionButton.customBuilder(Text.literal("Tek Oyunculu"), b -> client.setScreen(new SelectWorldScreen(this)))
                    .dimensions(left, height / 2 - 38, buttonWidth, 25).build());
            addDrawableChild(VexionButton.customBuilder(Text.literal("Çok Oyunculu"), b -> client.setScreen(new MultiplayerScreen(this)))
                    .dimensions(left, height / 2 - 7, buttonWidth, 25).build());
            addDrawableChild(VexionButton.customBuilder(Text.literal("Ayarlar"), b -> client.setScreen(new OptionsScreen(this, client.options)))
                    .dimensions(left, height / 2 + 24, buttonWidth, 25).build());
            addDrawableChild(VexionButton.customBuilder(Text.literal("Hesap Değiştir"), b -> {
                accountScreen = true;
                notice = "";
                clearAndInit();
            }).dimensions(left, height / 2 + 55, buttonWidth, 25).build());
            addDrawableChild(VexionButton.customBuilder(Text.literal("Çıkış"), b -> client.scheduleStop())
                    .dimensions(left, height / 2 + 86, buttonWidth, 25).build());
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Keep Minecraft's own panorama/blur background and add a cinematic dark overlay.
        renderBackground(context, mouseX, mouseY, delta);
        context.fillGradient(0, 0, width, height, 0x22101B2B, 0x5505070C);
        context.fill(0, 0, width, 34, 0x66050912);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Vexion Client - 1.21.4"), width / 2, Math.max(18, height / 2 - 112), 0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer, "Türkiye saati  " + LocalTime.now(ZoneId.of("Europe/Istanbul")).format(CLOCK), 12, 12, 0xFFFFFFFF);

        if (!accountScreen) {
            // Large split-color V/C branding on the left side.
            context.getMatrices().push();
            context.getMatrices().translate(34.0, height / 2.0 - 52.0, 0.0);
            context.getMatrices().scale(3.8f, 3.8f, 1.0f);
            context.drawTextWithShadow(textRenderer, Text.literal("V"), 0, 0, 0xFF36A9FF);
            context.drawTextWithShadow(textRenderer, Text.literal("C"), 11, 0, 0xFFFF3D4F);
            context.getMatrices().pop();
            context.drawTextWithShadow(textRenderer, Text.literal("1.21.4"), 34, height / 2 + 12, 0xFFFFD84A);
        }

        int panelWidth = Math.min(300, width - 20);
        int panelLeft = accountScreen ? (width - panelWidth) / 2 : width - panelWidth - 34;
        int panelTop = accountScreen ? height / 2 - 112 : height / 2 - 48;
        int panelBottom = accountScreen ? height / 2 + 148 : height / 2 + 122;
        context.fill(panelLeft + 4, panelTop + 5, panelLeft + panelWidth + 4, panelBottom + 5, 0x55000000);
        context.fill(panelLeft, panelTop, panelLeft + panelWidth, panelBottom, 0xA8141A24);
        context.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + 1, 0x99D8E5F4);

        if (accountScreen) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("ACCOUNT MANAGER"), width / 2, height / 2 - 103, 0xFFFFFFFF);
            int y = height / 2 - 40;
            String loggedInName = MinecraftClient.getInstance().getSession().getUsername();
            for (String name : ACCOUNTS) {
                boolean activeSession = name.equalsIgnoreCase(loggedInName);
                int rowLeft = width / 2 - 130;
                int rowRight = width / 2 + 130;
                int border = activeSession ? 0xFF269BFF : 0xFFE9EDF5;
                context.fill(rowLeft - 2, y - 3, rowRight + 2, y + 19, border);
                context.fill(rowLeft, y - 1, rowRight, y + 17, 0xE51A2230);
                context.drawTextWithShadow(textRenderer, name, rowLeft + 9, y + 4, 0xFF58B8FF);
                context.drawTextWithShadow(textRenderer, activeSession ? "OYUNDA" : "SEÇ",
                        rowRight - 43, y + 4, activeSession ? 0xFF72C7FF : 0xFFE9EDF5);
                y += 23;
            }
            if (ACCOUNTS.isEmpty()) {
                context.drawCenteredTextWithShadow(textRenderer, Text.literal("Henüz hesap eklenmedi"), width / 2, height / 2 - 5, 0xFFCCD3DF);
            }
            if (!notice.isEmpty()) context.drawCenteredTextWithShadow(textRenderer, Text.literal(notice),
                    width / 2, height / 2 + 83, 0xFFFFD479);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("Mavi çerçeve: şu an oyunda olan hesap  |  Beyaz: diğer hesaplar"),
                    width / 2, height / 2 + 96, 0xFFD5DCE7);
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
            int y = height / 2 - 40;
            for (String name : new ArrayList<>(ACCOUNTS)) {
                if (mouseX >= width / 2.0 - 132 && mouseX <= width / 2.0 + 132 && mouseY >= y - 3 && mouseY <= y + 19) {
                    if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                        selectedAccount = name;
                        notice = name + " seçildi. Gerçek oturum değiştirme desteklenmiyor.";
                        saveAccounts();
                    }
                    return true;
                }
                y += 23;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static final class VexionButton extends ButtonWidget {
        private VexionButton(int x, int y, int width, int height, Text message, PressAction action) {
            super(x, y, width, height, message, action, DEFAULT_NARRATION_SUPPLIER);
        }
        static VBuilder customBuilder(Text message, PressAction action) {
            return new VBuilder(message, action);
        }
        private static final class VBuilder {
            private final Text message;
            private final PressAction action;
            private int x, y, width = 200, height = 20;
            private VBuilder(Text message, PressAction action) { this.message = message; this.action = action; }
            private VBuilder dimensions(int x, int y, int width, int height) {
                this.x = x; this.y = y; this.width = width; this.height = height; return this;
            }
            private VexionButton build() { return new VexionButton(x, y, width, height, message, action); }
        }
        @Override
        protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            boolean hovered = isHovered();
            context.fill(getX() + 2, getY() + 3, getX() + width + 2, getY() + height + 3, 0x66000000);
            context.fill(getX(), getY(), getX() + width, getY() + height, hovered ? 0xFFF0F4FA : 0xFF9CA8B8);
            context.fill(getX(), getY(), getX() + width, getY() + 2, hovered ? 0xFFFFFFFF : 0xFFDCE5F0);
            context.fill(getX(), getY() + height - 2, getX() + width, getY() + height, 0xFF515B69);
            context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, getMessage(),
                    getX() + width / 2, getY() + (height - 8) / 2, active ? 0xFFFFFFFF : 0xFF303846);
        }
    }

    @Override
    public boolean shouldPause() { return false; }
}
