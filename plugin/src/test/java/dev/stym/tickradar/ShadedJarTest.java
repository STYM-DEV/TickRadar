package dev.stym.tickradar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;

class ShadedJarTest {

    private static final int JAVA_25_MAJOR = 69;
    private static final int CLASS_MAGIC = 0xCAFEBABE;
    private static final String EMBEDDED_LIBRARIES = "dev/stym/tickradar/lib/";

    private static JarFile jar() throws IOException {
        String path = System.getProperty("tickradar.shadedJar");
        assertNotNull(path, "run through Gradle: the test task passes the jar path");
        return new JarFile(path);
    }

    @Test
    void ourClassesAreJava25BytecodeAndEmbeddedLibrariesNoNewer() throws IOException {
        List<String> wrong = new ArrayList<>();
        int classes = 0;
        try (JarFile jar = jar()) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) {
                    continue;
                }
                classes++;
                try (DataInputStream in = new DataInputStream(jar.getInputStream(entry))) {
                    assertEquals(CLASS_MAGIC, in.readInt());
                    in.readUnsignedShort();
                    int major = in.readUnsignedShort();
                    boolean library = entry.getName().startsWith(EMBEDDED_LIBRARIES);
                    if (library ? major > JAVA_25_MAJOR : major != JAVA_25_MAJOR) {
                        wrong.add(entry.getName() + " is " + major);
                    }
                }
            }
        }
        assertTrue(classes > 0);
        assertEquals(List.of(), wrong);
    }

    @Test
    void bStatsIsRelocatedWithoutItsUnusedCharts() throws IOException {
        try (JarFile jar = jar()) {
            assertNotNull(jar.getJarEntry(EMBEDDED_LIBRARIES + "bstats/bukkit/Metrics.class"));
            assertNotNull(jar.getJarEntry(EMBEDDED_LIBRARIES + "bstats/charts/SimplePie.class"));
            assertEquals(null, jar.getJarEntry(EMBEDDED_LIBRARIES + "bstats/charts/DrilldownPie.class"));
            assertEquals(null, jar.getJarEntry("org/bstats/bukkit/Metrics.class"));
        }
    }

    @Test
    void pluginDescriptionIsFoliaOnlyWithTheBuildVersion() throws IOException {
        String pluginYml = read("plugin.yml");
        assertFalse(pluginYml.contains("${"), pluginYml);
        assertTrue(pluginYml.contains("main: dev.stym.tickradar.TickRadarPlugin"));
        assertTrue(pluginYml.contains("api-version: '26.1'"));
        assertTrue(pluginYml.contains("folia-supported: true"));
        assertTrue(pluginYml.matches("(?s).*version: '\\d+\\.\\d+\\.\\d+.*'.*"));
    }

    @Test
    void shippedFilesAreInTheJar() throws IOException {
        for (String name : List.of("config.yml", "lang/en.yml", "lang/fr.yml")) {
            assertFalse(read(name).isBlank(), name);
        }
    }

    @Test
    void onlyOurOwnClassesAreShipped() throws IOException {
        List<String> foreign = new ArrayList<>();
        try (JarFile jar = jar()) {
            jar.stream()
                    .filter(entry -> entry.getName().endsWith(".class"))
                    .filter(entry -> !entry.getName().startsWith("dev/stym/tickradar/"))
                    .forEach(entry -> foreign.add(entry.getName()));
        }
        assertEquals(List.of(), foreign);
    }

    private static String read(String name) throws IOException {
        try (JarFile jar = jar()) {
            JarEntry entry = jar.getJarEntry(name);
            assertNotNull(entry, name + " is missing from the jar");
            try (InputStream in = jar.getInputStream(entry)) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
    }
}
