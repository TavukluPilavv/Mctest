package dev.mctest;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public final class VexionSounds {
    public static final Identifier WELCOME_ID = Identifier.of("attackaura", "welcome");
    public static final SoundEvent WELCOME = SoundEvent.of(WELCOME_ID);

    private VexionSounds() {}

    public static void register() {
        Registry.register(Registries.SOUND_EVENT, WELCOME_ID, WELCOME);
    }
}
