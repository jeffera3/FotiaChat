package gg.fotia.chat.condition;

import org.bukkit.NamespacedKey;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Locale;

final class BukkitConditionSubject implements ConditionSubject {

    private final Player player;

    BukkitConditionSubject(Player player) {
        this.player = player;
    }

    @Override
    public boolean hasPermission(String permission) {
        return player.hasPermission(permission);
    }

    @Override
    public String world() {
        return player.getWorld().getName();
    }

    @Override
    public String gamemode() {
        return player.getGameMode().name();
    }

    @Override
    public String biome() {
        Biome biome = player.getLocation().getBlock().getBiome();
        NamespacedKey key = biome.getKey();
        return key == null ? biome.toString() : key.toString();
    }

    @Override
    public String environment() {
        return player.getWorld().getEnvironment().name();
    }

    @Override
    public int level() {
        return player.getLevel();
    }

    @Override
    public double y() {
        return player.getLocation().getY();
    }

    @Override
    public String weather() {
        if (player.getWorld().isThundering()) {
            return "THUNDER";
        }
        return player.getWorld().hasStorm() ? "RAIN" : "CLEAR";
    }

    @Override
    public long time() {
        return player.getWorld().getTime();
    }

    @Override
    public int potionLevel(String effect) {
        PotionEffectType type = PotionEffectType.getByName(effect.toUpperCase(Locale.ROOT));
        if (type == null) {
            return -1;
        }
        PotionEffect active = player.getPotionEffect(type);
        return active == null ? -1 : active.getAmplifier() + 1;
    }
}
