package dev.mctest;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class MctestClient implements ClientModInitializer {
    private static KeyBinding testMessageKey;

    @Override
    public void onInitializeClient() {
        testMessageKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.mctest.test_message",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                "category.misc"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (testMessageKey.wasPressed()) {
                if (client.player != null) {
                    client.player.sendMessage(
                            Text.literal("Mctest loaded! Client test key works."),
                            false
                    );
                }
            }
        });
    }
}
