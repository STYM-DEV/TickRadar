package dev.stym.tickradar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ArchitectureTest {

    private static final Path MAIN = Path.of("src/main/java/dev/stym/tickradar");
    private static final Path TEST = Path.of("src/test/java/dev/stym/tickradar");
    private static final List<String> GAME_FREE_PACKAGES = List.of("engine", "config", "alert/discord");
    private static final List<String> GAME_FREE_FILES = List.of("alert/AlertEvent.java", "alert/AlertSink.java");
    private static final List<String> GAME_API = List.of("org.bukkit", "io.papermc", "net.kyori", "com.destroystokyo",
            "net.minecraft", "com.mojang", "io.papermc.paper.threadedregions");
    private static final List<String> WARM_UP_FILES = List.of("display/DisplayWarmUp.java", "sample/SamplingWarmUp.java");
    private static final Pattern WARM_UP_FORBIDDEN = Pattern.compile(
            "org\\.bukkit|io\\.papermc|com\\.destroystokyo|net\\.minecraft|\\b(Bukkit|Server|Player|World|Entity|Location)\\b");

    @Test
    void enginePackagesNeverTouchTheGameApi() throws IOException {
        List<String> offenders = new ArrayList<>();
        int checked = 0;
        for (String name : GAME_FREE_PACKAGES) {
            Path folder = MAIN.resolve(name);
            if (!Files.isDirectory(folder)) {
                continue;
            }
            for (Path file : javaFiles(folder)) {
                checked++;
                String source = Files.readString(file);
                for (String api : GAME_API) {
                    if (source.contains(api + ".")) {
                        offenders.add(MAIN.relativize(file) + " uses " + api);
                    }
                }
            }
        }
        assertFalse(checked == 0, "no source found: the test must run from the plugin folder");
        assertEquals(List.of(), offenders);
    }

    @Test
    void theAlertTypesSeenByDiscordNeverTouchTheGameApi() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (String name : GAME_FREE_FILES) {
            String source = Files.readString(MAIN.resolve(name));
            for (String api : GAME_API) {
                if (source.contains(api + ".")) {
                    offenders.add(name + " uses " + api);
                }
            }
        }
        assertEquals(List.of(), offenders);
    }

    @Test
    void theWarmUpNeverTouchesAGameObject() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (String name : WARM_UP_FILES) {
            Matcher game = WARM_UP_FORBIDDEN.matcher(Files.readString(MAIN.resolve(name)));
            while (game.find()) {
                offenders.add(name + " uses " + game.group());
            }
        }
        assertEquals(List.of(), offenders);
    }

    @Test
    void costlyTpsCallsStayInTheirDedicatedClasses() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles(MAIN)) {
            String source = Files.readString(file);
            String name = file.getFileName().toString();
            if (source.contains("getRegionTPS(") && !name.equals("FoliaRegionTps.java")) {
                offenders.add(name + " calls getRegionTPS");
            }
            if (source.contains("getTPS()") && !name.equals("RegionMeter.java")) {
                offenders.add(name + " calls getTPS");
            }
        }
        assertEquals(List.of(), offenders);
    }

    @Test
    void sourcesHaveNoComments() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path root : List.of(MAIN, TEST)) {
            for (Path file : javaFiles(root)) {
                List<String> lines = Files.readAllLines(file);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i).trim();
                    if (line.startsWith("//") || line.startsWith("/*") || line.startsWith("*/") || line.startsWith("* ")) {
                        offenders.add(file + ":" + (i + 1));
                    }
                }
            }
        }
        assertEquals(List.of(), offenders);
    }

    @Test
    void sourcesHaveNoLiteralInvisibleCharacters() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path root : List.of(MAIN, TEST)) {
            for (Path file : javaFiles(root)) {
                List<String> lines = Files.readAllLines(file);
                for (int i = 0; i < lines.size(); i++) {
                    int line = i + 1;
                    lines.get(i).codePoints()
                            .filter(ArchitectureTest::isInvisible)
                            .forEach(codePoint -> offenders.add(file + ":" + line + " U+" + Integer.toHexString(codePoint).toUpperCase()));
                }
            }
        }
        assertEquals(List.of(), offenders);
    }

    private static boolean isInvisible(int codePoint) {
        return codePoint == 0x00A0 || codePoint == 0x202F || codePoint == 0x2028 || codePoint == 0x2029 || codePoint == 0xFEFF
                || (codePoint >= 0x200B && codePoint <= 0x200F)
                || (codePoint >= 0xFDD0 && codePoint <= 0xFDEF)
                || (codePoint >= 0xE000 && codePoint <= 0xF8FF);
    }

    private static List<Path> javaFiles(Path folder) throws IOException {
        try (Stream<Path> files = Files.walk(folder)) {
            return files.filter(file -> file.toString().endsWith(".java")).toList();
        }
    }
}
