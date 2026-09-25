package dev.stym.tickradar;

import io.papermc.paper.ServerBuildInfo;
import java.util.List;
import net.kyori.adventure.key.Key;

final class ServerSupport {

    private static final Key FOLIA_BRAND = Key.key("papermc", "folia");
    private static final String FOLIA_CLASS = "io.papermc.paper.threadedregions.RegionizedServer";

    private ServerSupport() {
    }

    static boolean isFolia() {
        try {
            return ServerBuildInfo.buildInfo().isBrandCompatible(FOLIA_BRAND) || hasFoliaClass();
        } catch (RuntimeException | LinkageError e) {
            return hasFoliaClass();
        }
    }

    static List<String> notFoliaLines(String serverName) {
        return List.of(
                "TickRadar only runs on Folia (https://papermc.io/software/folia).",
                "This server runs " + serverName + ", which has a single main thread: use /tps or spark instead.",
                "TickRadar is now disabled. Nothing was changed on your server.");
    }

    private static boolean hasFoliaClass() {
        try {
            Class.forName(FOLIA_CLASS, false, ServerSupport.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }
}
