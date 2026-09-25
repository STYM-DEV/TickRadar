package dev.stym.tickradar.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SettingsServiceTest {

    @TempDir
    Path folder;

    private final List<LogRecord> logs = new ArrayList<>();
    private Logger logger;

    @BeforeEach
    void captureLogs() {
        logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                logs.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
    }

    private SettingsService service() {
        return new SettingsService(new ConfigFiles(folder, SettingsServiceTest.class.getClassLoader()::getResourceAsStream), logger);
    }

    @Test
    void firstStartInstallsTheDefaultFilesAndLogsNothing() {
        Settings settings = service().loadAtStartup();
        assertTrue(Files.isRegularFile(folder.resolve("config.yml")));
        assertTrue(Files.isRegularFile(folder.resolve("lang/en.yml")));
        assertTrue(Files.isRegularFile(folder.resolve("lang/fr.yml")));
        assertEquals(ConfigLoader.defaults(), settings.config());
        assertEquals(List.of(), logs.stream().map(LogRecord::getMessage).toList());
    }

    @Test
    void existingFilesAreNeverOverwritten() throws IOException {
        write("config.yml", "sampling:\n  interval-ticks: 40\n");
        Settings settings = service().loadAtStartup();
        assertEquals(40, settings.config().sampling().intervalTicks());
        assertEquals("sampling:\n  interval-ticks: 40\n", Files.readString(folder.resolve("config.yml")));
    }

    @Test
    void anUnreadableFileAtStartupGivesTheDefaultsAndAnError() throws IOException {
        write("config.yml", "sampling: [unclosed\n");
        Settings settings = service().loadAtStartup();
        assertEquals(ConfigLoader.defaults(), settings.config());
        assertTrue(logs.stream().anyMatch(log -> log.getLevel() == Level.SEVERE && log.getMessage().startsWith("config.yml:")));
    }

    @Test
    void reloadAppliesAValidFileWithANewGeneration() throws IOException {
        SettingsService service = service();
        Settings first = service.loadAtStartup();
        write("config.yml", "thresholds: {warning: 5, critical: 10}\n");
        assertInstanceOf(LoadResult.Loaded.class, service.reload());
        assertEquals(5, service.current().config().thresholds().warning());
        assertTrue(service.current().generation() > first.generation());
    }

    @Test
    void reloadNeverAppliesAnUnreadableFile() throws IOException {
        SettingsService service = service();
        write("config.yml", "thresholds: {warning: 5, critical: 10}\n");
        Settings before = service.loadAtStartup();
        write("config.yml", "thresholds: {warning: 5, critical: [\n");
        LoadResult<Settings> result = service.reload();
        assertTrue(assertInstanceOf(LoadResult.Unreadable.class, result).error().startsWith("config.yml:"));
        assertSame(before, service.current());
    }

    @Test
    void reloadNeverAppliesAnUnreadableLanguageFile() throws IOException {
        SettingsService service = service();
        Settings before = service.loadAtStartup();
        write("lang/fr.yml", "status: {ok: [\n");
        assertInstanceOf(LoadResult.Unreadable.class, service.reload());
        assertSame(before, service.current());
    }

    @Test
    void aFileThatIsNotUtf8IsUnreadable() throws IOException {
        SettingsService service = service();
        Settings before = service.loadAtStartup();
        Files.write(folder.resolve("config.yml"), new byte[] {'a', ':', ' ', (byte) 0xC3, (byte) 0x28, '\n'});
        String error = assertInstanceOf(LoadResult.Unreadable.class, service.reload()).error();
        assertEquals("config.yml: not a UTF-8 text file", error);
        assertSame(before, service.current());
    }

    @Test
    void customLanguageFilesAreLoaded() throws IOException {
        SettingsService service = service();
        service.loadAtStartup();
        write("lang/de.yml", "status:\n  ok: \"gut\"\n");
        write("lang/notes.yml", "a: b\n");
        LoadResult.Loaded<?> result = assertInstanceOf(LoadResult.Loaded.class, service.reload());
        assertEquals("gut", service.current().lang().text("de", "status.ok"));
        assertEquals(List.of("lang/notes.yml: not a language file name such as en.yml; ignored"), result.warnings());
    }

    private void write(String name, String content) throws IOException {
        Path file = folder.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
