package com.zerotheabsolute.quantumflux.init;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.zerotheabsolute.quantumflux.QuantumFlux;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

import java.util.UUID;

public final class QFDataComponents {

    public static final int MAX_DIMENSION_ID_LENGTH = 128;
    private static final Codec<String> DIMENSION_ID_CODEC = Codec.STRING.validate(value -> {
        if (value.isEmpty()) return DataResult.success(value);
        if (value.length() > MAX_DIMENSION_ID_LENGTH) {
            return DataResult.error(() -> "Dimension identifier exceeds "
                    + MAX_DIMENSION_ID_LENGTH + " characters");
        }
        return ResourceLocation.tryParse(value) != null
                ? DataResult.success(value)
                : DataResult.error(() -> "Invalid dimension identifier: " + value);
    });

    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, QuantumFlux.MODID);

    // ── LinkingData record ──
    public record LinkingData(String dimension, BlockPos pylonPos, boolean active) {

        public static final Codec<LinkingData> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        DIMENSION_ID_CODEC.optionalFieldOf("dimension", "").forGetter(LinkingData::dimension),
                        BlockPos.CODEC.fieldOf("pylon_pos").forGetter(LinkingData::pylonPos),
                        Codec.BOOL.fieldOf("active").forGetter(LinkingData::active)
                ).apply(instance, LinkingData::new));

        public static final StreamCodec<FriendlyByteBuf, LinkingData> STREAM_CODEC = StreamCodec.of(
                (buf, data) -> {
                    buf.writeUtf(data.dimension(), MAX_DIMENSION_ID_LENGTH);
                    buf.writeBlockPos(data.pylonPos());
                    buf.writeBoolean(data.active());
                },
                buf -> new LinkingData(buf.readUtf(MAX_DIMENSION_ID_LENGTH), buf.readBlockPos(), buf.readBoolean())
        );
    }

    public static final RegistryObject<DataComponentType<LinkingData>> LINKING_DATA =
            DATA_COMPONENTS.register("linking_data", () ->
                    DataComponentType.<LinkingData>builder()
                            .persistent(LinkingData.CODEC)
                            .networkSynchronized(LinkingData.STREAM_CODEC)
                            .build());

    public static final RegistryObject<DataComponentType<Integer>> GADGET_COLOR =
            DATA_COMPONENTS.register("gadget_color", () ->
                    DataComponentType.<Integer>builder()
                            .persistent(Codec.INT)
                            .networkSynchronized(StreamCodec.of(
                                    FriendlyByteBuf::writeInt, FriendlyByteBuf::readInt))
                            .build());

    public static final RegistryObject<DataComponentType<Boolean>> GADGET_ACTIVE =
            DATA_COMPONENTS.register("gadget_active", () ->
                    DataComponentType.<Boolean>builder()
                            .persistent(Codec.BOOL)
                            .networkSynchronized(StreamCodec.of(
                                    FriendlyByteBuf::writeBoolean, FriendlyByteBuf::readBoolean))
                            .build());

    public static final RegistryObject<DataComponentType<UUID>> SELECTED_NETWORK =
            DATA_COMPONENTS.register("selected_network", () ->
                    DataComponentType.<UUID>builder()
                            .persistent(UUIDUtil.CODEC)
                            .networkSynchronized(StreamCodec.of(
                                    (FriendlyByteBuf buf, UUID uuid) -> buf.writeUUID(uuid),
                                    (FriendlyByteBuf buf) -> buf.readUUID()))
                            .build());

    /** Dimension in which SELECTED_NETWORK is valid. Kept separate for compatibility with existing UUID data. */
    public static final RegistryObject<DataComponentType<String>> SELECTED_NETWORK_DIMENSION =
            DATA_COMPONENTS.register("selected_network_dimension", () ->
                    DataComponentType.<String>builder()
                            .persistent(DIMENSION_ID_CODEC)
                            .networkSynchronized(StreamCodec.of(
                                    (buf, value) -> buf.writeUtf(value, MAX_DIMENSION_ID_LENGTH),
                                    buf -> buf.readUtf(MAX_DIMENSION_ID_LENGTH)))
                            .build());

    private QFDataComponents() {}
}
