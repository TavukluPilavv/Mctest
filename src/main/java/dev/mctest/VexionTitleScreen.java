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

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class VexionTitleScreen extends Screen {
    private static final List<String> ACCOUNTS = new ArrayList<>();
    private static String selectedAccount = "";
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private TextFieldWidget nameField;
    private boolean accountScreen;

    public VexionTitleScreen() { super(Text.literal("Vexion Client")); }

    @Override
    protected void init() {
        int center = width / 2;
        if (accountScreen) {
            nameField = new TextFieldWidget(textRenderer, center - 120, height / 2 - 45, 240, 24, Text.literal("Oyuncu adı"));
            nameField.setPlaceholder(Text.literal("İsmini buraya yaz"));
            addDrawableChild(nameField);
            addDrawableChild(ButtonWidget.builder(Text.literal("Add'e Ekle"), b -> {
                String name = nameField.getText().trim();
                if (!name.isEmpty() && name.length() <= 16 && name.matches("[A-Za-z0-9_]+") && !ACCOUNTS.contains(name)) {
                    ACCOUNTS.add(name);
                    if (selectedAccount.isEmpty()) selectedAccount = name;
                    nameField.setText("");
                }
            }).dimensions(center - 120, height / 2 - 12, 240, 22).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Geri"), b -> {
                accountScreen = false;
                clearAndInit();
            }).dimensions(center - 120, height - 42, 240, 22).build());
        } else {
            addDrawableChild(ButtonWidget.builder(Text.literal("Tek Oyunculu"), b -> client.setScreen(new SelectWorldScreen(this)))
                    .dimensions(center - 120, height / 2 - 35, 240, 24).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Çok Oyunculu"), b -> client.setScreen(new MultiplayerScreen(this)))
                    .dimensions(center - 120, height / 2 - 5, 240, 24).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Ayarlar"), b -> client.setScreen(new OptionsScreen(this, client.options)))
                    .dimensions(center - 120, height / 2 + 25, 240, 24).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Hesap Değiştir"), b -> {
                accountScreen = true;
                clearAndInit();
            }).dimensions(center - 120, height / 2 + 55, 240, 24).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Çıkış"), b -> client.scheduleStop())
                    .dimensions(center - 120, height / 2 + 85, 240, 24).build());
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Vexion Client - 1.21.4"), width / 2, height / 2 - 100, 0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer, "Türkiye saati: " + LocalTime.now(ZoneId.of("Europe/Istanbul")).format(CLOCK), 10, 10, 0xFFFFFFFF);
        if (accountScreen) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("Hesaplar (seçmek için sol tık, silmek için sağ tık)"), width / 2, height / 2 - 75, 0xFFFFFFFF);
            int y = height / 2 + 20;
            for (String name : ACCOUNTS) {
                int color = name.equals(selectedAccount) ? 0xFF55FF55 : 0xFFFFFFFF;
                context.drawCenteredTextWithShadow(textRenderer, Text.literal((name.equals(selectedAccount) ? "● " : "○ ") + name), width / 2, y, color);
                y += 18;
            }
        } else if (!selectedAccount.isEmpty()) {
            context.drawTextWithShadow(textRenderer, "Seçili isim: " + selectedAccount, 10, 26, 0xFF55FF55);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (accountScreen && nameField != null) {
            int y = height / 2 + 20;
            for (String name : ACCOUNTS) {
                if (mouseY >= y - 3 && mouseY <= y + 14 && Math.abs(mouseX - width / 2.0) < 130) {
                    if (button == 1) {
                        ACCOUNTS.remove(name);
                        if (name.equals(selectedAccount)) selectedAccount = ACCOUNTS.isEmpty() ? "" : ACCOUNTS.get(0);
                    } else if (button == 0) {
                        selectedAccount = name;
                    }
                    return true;
                }
                y += 18;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean shouldPause() { return false; }
}
