package dev.alphasucks.aea.compat.tacz;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.index.CommonGunIndex;
import dev.alphasucks.aea.compat.Codecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * Which guns something applies to: {@code "gun"} (a TaCZ gun id or a list of them) and {@code "gun_type"} (a TaCZ gun
 * type such as {@code pistol}, {@code rifle}, {@code sniper}, {@code shotgun}, {@code smg}, {@code mg} or {@code rpg},
 * or a list). Both empty means every gun; with both, a gun has to match both.
 */
public record GunFilter(List<ResourceLocation> guns, List<String> types) {
    public static final GunFilter ANY = new GunFilter(List.of(), List.of());

    public static final MapCodec<GunFilter> FIELDS = RecordCodecBuilder.mapCodec(i -> i.group(
        Codecs.singleOrList(ResourceLocation.CODEC).optionalFieldOf("gun", List.of()).forGetter(GunFilter::guns),
        Codecs.singleOrList(Codec.STRING).optionalFieldOf("gun_type", List.of()).forGetter(GunFilter::types)
    ).apply(i, GunFilter::new));

    public boolean any() {
        return guns.isEmpty() && types.isEmpty();
    }

    /** {@code stack} is a gun this filter accepts. */
    public boolean test(ItemStack stack) {
        IGun gun = IGun.getIGunOrNull(stack);
        return gun != null && test(gun.getGunId(stack));
    }

    public boolean test(ResourceLocation gunId) {
        if (gunId == null) return false;
        if (!guns.isEmpty() && !guns.contains(gunId)) return false;
        if (types.isEmpty()) return true;
        Optional<CommonGunIndex> index = TimelessAPI.getCommonGunIndex(gunId);
        return index.isPresent() && types.contains(index.get().getType());
    }
}
