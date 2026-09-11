package com.zerotheabsolute.quantumflux.client.sound;

import com.zerotheabsolute.quantumflux.init.QFSounds;
import com.zerotheabsolute.quantumflux.QFConfig;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;

/** Positional pylon loop with click-free start and stop transitions. */
public final class PylonHumSoundInstance extends AbstractTickableSoundInstance {

    private static final int FADE_TICKS = 10;

    private int fadeProgress;
    private boolean fadingOut;
    private float activity = 0.35F;
    private float targetActivity = 0.35F;

    public PylonHumSoundInstance(BlockPos pos) {
        super(QFSounds.PYLON_HUM.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.x = pos.getX() + 0.5D;
        this.y = pos.getY() + 1.1D;
        this.z = pos.getZ() + 0.5D;
        this.looping = true;
        this.delay = 0;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
        this.relative = false;
        this.pitch = 1.0F;
        this.volume = 0.0F;
    }

    public void requestFadeOut() {
        fadingOut = true;
    }

    public void resume() {
        fadingOut = false;
    }

    public void setTransferring(boolean transferring) {
        targetActivity = transferring ? 1.0F : 0.35F;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        if (fadingOut) {
            fadeProgress = Math.max(0, fadeProgress - 1);
        } else {
            fadeProgress = Math.min(FADE_TICKS, fadeProgress + 1);
        }
        activity += (targetActivity - activity) * 0.2F;
        volume = QFConfig.PYLON_HUM_VOLUME.get().floatValue() * activity * fadeProgress / FADE_TICKS;
        if (fadingOut && fadeProgress == 0) stop();
    }
}
