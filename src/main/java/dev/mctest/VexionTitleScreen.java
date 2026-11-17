package dev.mctest;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
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
    private static final Identifier BACKGROUND = Identifier.of("attackaura", "textures/gui/vexion_background.jpg");
    private static final List<String> ACCOUNTS = new ArrayList<>();
    private static String selectedAccount = "";
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static boolean accountsLoaded;
    private static boolean welcomePlayed;
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
        if (!welcomePlayed && client != null) {
            client.getSoundManager().play(PositionedSoundInstance.master(VexionSounds.WELCOME, 1.0f));
            welcomePlayed = true;
        }

        int center = width / 2;
        int buttonWidth = Math.min(280, width - 36);
        int left = accountScreen ? center - 140 : Math.max(18, width - buttonWidth - 34);

        if (accountScreen) {
            nameField = new TextFieldWidget(textRenderer, center - 130, height / 2 - 76, 260, 28, Text.literal("Oyuncu adı"));
            nameField.setPlaceholder(Text.literal("Minecraft kullanıcı adını yaz"));
            nameField.setDrawsBackground(true);
            nameField.setMaxLength(16);
            addDrawableChild(nameField);

            int actionY = Math.min(height - 48, height / 2 + 112);
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
                    notice = name + " listeye eklendi.";
                    nameField.setText("");
                    saveAccounts();
                }
            }).dimensions(actionLeft, actionY, actionWidth, 27).build());

            addDrawableChild(VexionButton.customBuilder(Text.literal("Sil"), b -> {
                if (selectedAccount.isEmpty() || !ACCOUNTS.remove(selectedAccount)) {
                    notice = "Silmek için önce bir hesap seç.";
                } else {
                    notice = selectedAccount + " listeden silindi.";
                    selectedAccount = ACCOUNTS.isEmpty() ? "" : ACCOUNTS.get(0);
                    saveAccounts();
                }
            }).dimensions(actionLeft + actionWidth + actionGap, actionY, actionWidth, 27).build());

            addDrawableChild(VexionButton.customBuilder(Text.literal("Geri"), b -> {
                accountScreen = false;
                notice = "";
                clearAndInit();
            }).dimensions(actionLeft + (actionWidth + actionGap) * 2, actionY, actionWidth, 27).build());
        } else {
            addDrawableChild(VexionButton.customBuilder(Text.literal("Tek Oyunculu"), b -> client.setScreen(new SelectWorldScreen(this)))
                    .dimensions(left, height / 2 - 52, buttonWidth, 27).build());
            addDrawableChild(VexionButton.customBuilder(Text.literal("Çok Oyunculu"), b -> client.setScreen(new MultiplayerScreen(this)))
                    .dimensions(left, height / 2 - 18, buttonWidth, 27).build());
            addDrawableChild(VexionButton.customBuilder(Text.literal("Ayarlar"), b -> client.setScreen(new OptionsScreen(this, client.options)))
                    .dimensions(left, height / 2 + 16, buttonWidth, 27).build());
            addDrawableChild(VexionButton.customBuilder(Text.literal("Account Manager"), b -> {
                accountScreen = true;
                notice = "";
                clearAndInit();
            }).dimensions(left, height / 2 + 50, buttonWidth, 27).build());
            addDrawableChild(VexionButton.customBuilder(Text.literal("Oyundan Çık"), b -> client.scheduleStop())
                    .dimensions(left, height / 2 + 84, buttonWidth, 27).build());
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.drawTexture(RenderLayer::getGuiTextured, BACKGROUND,
                0, 0, 0.0f, 0.0f, width, height, 800, 450);
        context.fillGradient(0, 0, width, height, 0x12040A16, 0x44020710);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.fill(0, 0, width, 34, 0x5510182A);
        context.drawTextWithShadow(textRenderer, "Türkiye saati  " + LocalTime.now(ZoneId.of("Europe/Istanbul")).format(CLOCK), 12, 12, 0xFFFFFFFF);

        if (!accountScreen) {
            context.getMatrices().push();
            context.getMatrices().translate(38.0, height / 2.0 - 65.0, 0.0);
            context.getMatrices().scale(5.0f, 5.0f, 1.0f);
            context.drawTextWithShadow(textRenderer, Text.literal("V"), 0, 0, 0xFF28A8FF);
            context.drawTextWithShadow(textRenderer, Text.literal("C"), 12, 0, 0xFFFF354D);
            context.getMatrices().pop();
            context.drawTextWithShadow(textRenderer, Text.literal("1.21.4"), 40, height / 2 + 8, 0xFFFFD52E);
            context.drawTextWithShadow(textRenderer, Text.literal("VEXION CLIENT"), 40, height / 2 + 28, 0xFFFFFFFF);
        }

        int panelWidth = Math.min(320, width - 20);
        int panelLeft = accountScreen ? (width - panelWidth) / 2 : width - panelWidth - 24;
        int panelTop = accountScreen ? height / 2 - 112 : height / 2 - 72;
        int panelBottom = accountScreen ? height / 2 + 148 : height / 2 + 122;
        context.fill(panelLeft + 4, panelTop + 5, panelLeft + panelWidth + 4, panelBottom + 5, 0x66000000);
        context.fill(panelLeft, panelTop, panelLeft + panelWidth, panelBottom, 0xA8121B2A);
        context.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + 2, 0xCC64B5FF);

        if (accountScreen) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("ACCOUNT MANAGER"), width / 2, height / 2 - 103, 0xFFFFFFFF);
            int y = height / 2 - 40;
            String loggedInName = MinecraftClient.getInstance().getSession().getUsername();
            for (String name : ACCOUNTS) {
                boolean activeSession = name.equalsIgnoreCase(loggedInName);
                boolean selected = name.equalsIgnoreCase(selectedAccount);
                int rowLeft = width / 2 - 130;
                int rowRight = width / 2 + 130;
                int border = activeSession ? 0xFF269BFF : 0xFFE9EDF5;
                context.fill(rowLeft - 2, y - 3, rowRight + 2, y + 19, border);
                context.fill(rowLeft, y - 1, rowRight, y + 17, selected ? 0xFF20364F : 0xE51A2230);
                context.drawTextWithShadow(textRenderer, name, rowLeft + 9, y + 4, 0xFF58B8FF);
                context.drawTextWithShadow(textRenderer, activeSession ? "OYUNDA" : (selected ? "SEÇİLİ" : "SEÇ"),
                        rowRight - 48, y + 4, activeSession ? 0xFF72C7FF : 0xFFE9EDF5);
                y += 23;
            }
            if (ACCOUNTS.isEmpty()) {
                context.drawCenteredTextWithShadow(textRenderer, Text.literal("Henüz hesap eklenmedi"), width / 2, height / 2 - 5, 0xFFCCD3DF);
            }
            if (!notice.isEmpty()) context.drawCenteredTextWithShadow(textRenderer, Text.literal(notice),
                    width / 2, height / 2 + 83, 0xFFFFD479);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("Mavi çerçeve: oyunda oturum açmış hesap  |  Beyaz: diğer hesaplar"),
                    width / 2, height / 2 + 96, 0xFFD5DCE7);
        } else {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("Vexion Client"), width / 2, height / 2 - 91, 0xFFFFFFFF);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("Seçili isim: " + (selectedAccount.isEmpty() ? "Yok" : selectedAccount)),
                    width / 2, height / 2 + 119, selectedAccount.isEmpty() ? 0xFFDFDFDF : 0xFF55C7FF);
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
                        notice = name.equalsIgnoreCase(MinecraftClient.getInstance().getSession().getUsername())
                                ? name + " şu anki oyun hesabı."
                                : name + " seçildi. Bu liste Microsoft oturumunu değiştirmez.";
                        saveAccounts();
                    }
                    return true;
                }
                y += 23;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static final class VexionButton extends net.minecraft.client.gui.widget.ButtonWidget {
        private VexionButton(int x, int y, int width, int height, Text message, PressAction action) {
            super(x, y, width, height, message, action, DEFAULT_NARRATION_SUPPLIER);
        }
        static VBuilder customBuilder(Text message, PressAction action) { return new VBuilder(message, action); }
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
            context.fill(getX(), getY(), getX() + width, getY() + height, hovered ? 0xFFF0F7FF : 0xFF9CAFC4);
            context.fill(getX(), getY(), getX() + width, getY() + 2, hovered ? 0xFFFFFFFF : 0xFFE4F0FF);
            context.fill(getX(), getY() + height - 2, getX() + width, getY() + height, 0xFF46576B);
            context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, getMessage(),
                    getX() + width / 2, getY() + (height - 8) / 2, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean shouldPause() { return false; }
}
