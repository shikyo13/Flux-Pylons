package com.zerotheabsolute.quantumflux.network;

import net.minecraft.resources.ResourceLocation;

public interface QFPayload {
    Type<? extends QFPayload> type();
    record Type<T extends QFPayload>(ResourceLocation id) {}
}
