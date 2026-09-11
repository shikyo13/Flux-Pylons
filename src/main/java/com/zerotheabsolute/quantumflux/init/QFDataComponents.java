package com.zerotheabsolute.quantumflux.init;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.UUID;

/** Gadget state stored in vanilla item NBT on Minecraft 1.20.1. */
public final class QFDataComponents {
    public static final int MAX_DIMENSION_ID_LENGTH = 128;
    private static final String ROOT = "quantumflux";
    private static final Codec<String> DIMENSION_ID_CODEC = Codec.STRING.comapFlatMap(value -> {
        if (value.isEmpty()) return DataResult.success(value);
        return value.length() <= MAX_DIMENSION_ID_LENGTH && ResourceLocation.tryParse(value) != null
                ? DataResult.success(value) : DataResult.error(() -> "Invalid dimension identifier");
    }, value -> value);

    // DFU in 1.20.1 silently ignores invalid values in optionalFieldOf. Keep the
    // legacy missing-dimension default while rejecting an invalid supplied value.
    private static final com.mojang.serialization.MapCodec<String> OPTIONAL_DIMENSION = new com.mojang.serialization.MapCodec<>() {
        @Override public <T> DataResult<String> decode(com.mojang.serialization.DynamicOps<T> ops,
                com.mojang.serialization.MapLike<T> input) {
            T value = input.get("dimension");
            return value == null ? DataResult.success("") : DIMENSION_ID_CODEC.parse(ops, value);
        }
        @Override public <T> com.mojang.serialization.RecordBuilder<T> encode(String value,
                com.mojang.serialization.DynamicOps<T> ops, com.mojang.serialization.RecordBuilder<T> prefix) {
            return value.isEmpty() ? prefix : prefix.add("dimension", DIMENSION_ID_CODEC.encodeStart(ops, value));
        }
        @Override public <T> java.util.stream.Stream<T> keys(com.mojang.serialization.DynamicOps<T> ops) {
            return java.util.stream.Stream.of(ops.createString("dimension"));
        }
    };

    public record LinkingData(String dimension, BlockPos pylonPos, boolean active) {
        public static final Codec<LinkingData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                OPTIONAL_DIMENSION.forGetter(LinkingData::dimension),
                BlockPos.CODEC.fieldOf("pylon_pos").forGetter(LinkingData::pylonPos),
                Codec.BOOL.fieldOf("active").forGetter(LinkingData::active)
        ).apply(instance, LinkingData::new));
    }

    public static final DataKey<LinkingData> LINKING_DATA = new DataKey<>("linking_data", LinkingData.CODEC);
    public static final DataKey<Integer> GADGET_COLOR = new DataKey<>("gadget_color", Codec.INT);
    public static final DataKey<Boolean> GADGET_ACTIVE = new DataKey<>("gadget_active", Codec.BOOL);
    public static final DataKey<UUID> SELECTED_NETWORK = new DataKey<>("selected_network", UUIDUtil.CODEC);
    public static final DataKey<String> SELECTED_NETWORK_DIMENSION = new DataKey<>("selected_network_dimension", DIMENSION_ID_CODEC);

    public record DataKey<T>(String name, Codec<T> codec) {
        public T get(ItemStack stack) {
            CompoundTag tag = stack.getTagElement(ROOT);
            if (tag == null || !tag.contains(name)) return null;
            return codec.parse(NbtOps.INSTANCE, tag.get(name)).result().orElse(null);
        }
        public void set(ItemStack stack, T value) {
            if (value == null) { remove(stack); return; }
            var encoded = codec.encodeStart(NbtOps.INSTANCE, value).result()
                    .orElseThrow(() -> new IllegalArgumentException("Invalid gadget state: " + name));
            stack.getOrCreateTagElement(ROOT).put(name, encoded);
        }
        public void remove(ItemStack stack) {
            CompoundTag tag = stack.getTagElement(ROOT);
            if (tag == null) return;
            tag.remove(name);
            if (tag.isEmpty()) stack.removeTagKey(ROOT);
        }
    }
    private QFDataComponents() {}
}
