package gg.fotia.chat.condition;

public interface ConditionSubject {

    boolean hasPermission(String permission);

    default String world() {
        return "";
    }

    default String gamemode() {
        return "";
    }

    default String biome() {
        return "";
    }

    default String environment() {
        return "";
    }

    default int level() {
        return 0;
    }

    default double y() {
        return 0;
    }

    default String weather() {
        return "";
    }

    default long time() {
        return 0;
    }

    default int potionLevel(String effect) {
        return -1;
    }
}
