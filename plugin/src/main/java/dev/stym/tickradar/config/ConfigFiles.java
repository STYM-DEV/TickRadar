package dev.stym.tickradar.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ConfigFiles {

    public static final List<String> BUILT_IN_LANGUAGES = List.of("en", "fr");
    private static final Pattern LANG_FILE = Pattern.compile("([a-z]{2,3})\\.yml");

    private final Path dataFolder;
    private final Function<String, InputStream> resources;

    public ConfigFiles(Path dataFolder, Function<String, InputStream> resources) {
        this.dataFolder = dataFolder;
        this.resources = resources;
    }

    public List<String> installDefaults() throws IOException {
        List<String> installed = new ArrayList<>();
        List<String> files = new ArrayList<>();
        files.add(ConfigLoader.FILE_NAME);
        BUILT_IN_LANGUAGES.forEach(language -> files.add(LangBundle.fileName(language)));
        for (String file : files) {
            Path target = dataFolder.resolve(file);
            if (Files.exists(target)) {
                continue;
            }
            Files.createDirectories(target.getParent());
            try (InputStream in = open(file)) {
                Files.copy(in, target);
            }
            installed.add(file);
        }
        return installed;
    }

    public LoadResult<Settings> read(long generation) {
        List<String> warnings = new ArrayList<>();
        String configText;
        Map<String, String> adminLanguages;
        try {
            configText = readConfig(warnings);
            adminLanguages = readAdminLanguages(warnings);
        } catch (UnreadableFileException e) {
            return new LoadResult.Unreadable<>(e.getMessage());
        }
        LoadResult<ConfigSnapshot> config = ConfigLoader.load(ConfigLoader.FILE_NAME, configText);
        if (config instanceof LoadResult.Unreadable<ConfigSnapshot>(String error)) {
            return new LoadResult.Unreadable<>(error);
        }
        LoadResult<LangBundle> lang = LangBundle.load(builtInLanguages(), adminLanguages);
        if (lang instanceof LoadResult.Unreadable<LangBundle>(String error)) {
            return new LoadResult.Unreadable<>(error);
        }
        LoadResult.Loaded<ConfigSnapshot> loadedConfig = (LoadResult.Loaded<ConfigSnapshot>) config;
        LoadResult.Loaded<LangBundle> loadedLang = (LoadResult.Loaded<LangBundle>) lang;
        warnings.addAll(loadedConfig.warnings());
        warnings.addAll(loadedLang.warnings());
        return new LoadResult.Loaded<>(new Settings(loadedConfig.value(), loadedLang.value(), generation), warnings);
    }

    public Settings builtInDefaults(long generation) {
        LoadResult<LangBundle> lang = LangBundle.load(builtInLanguages(), Map.of());
        if (lang instanceof LoadResult.Loaded<LangBundle> loaded) {
            return new Settings(ConfigLoader.defaults(), loaded.value(), generation);
        }
        throw new IllegalStateException("The built-in messages cannot be read: " + ((LoadResult.Unreadable<LangBundle>) lang).error());
    }

    private String readConfig(List<String> warnings) {
        Path file = dataFolder.resolve(ConfigLoader.FILE_NAME);
        if (!Files.exists(file)) {
            warnings.add(ConfigLoader.FILE_NAME + ": missing; using the default settings");
            return "";
        }
        return readText(file, ConfigLoader.FILE_NAME);
    }

    private Map<String, String> readAdminLanguages(List<String> warnings) {
        Path folder = dataFolder.resolve(LangBundle.FOLDER);
        Map<String, String> languages = new LinkedHashMap<>();
        if (!Files.isDirectory(folder)) {
            return languages;
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(folder, "*.yml")) {
            for (Path file : files) {
                String name = file.getFileName().toString();
                Matcher matcher = LANG_FILE.matcher(name);
                if (matcher.matches()) {
                    languages.put(matcher.group(1), readText(file, LangBundle.FOLDER + "/" + name));
                } else {
                    warnings.add(LangBundle.FOLDER + "/" + name + ": not a language file name such as en.yml; ignored");
                }
            }
        } catch (IOException e) {
            throw new UnreadableFileException(LangBundle.FOLDER + ": " + e.getMessage());
        }
        return languages;
    }

    private Map<String, String> builtInLanguages() {
        Map<String, String> languages = new LinkedHashMap<>();
        for (String language : BUILT_IN_LANGUAGES) {
            try (InputStream in = open(LangBundle.fileName(language))) {
                languages.put(language, new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return languages;
    }

    private InputStream open(String resource) throws IOException {
        InputStream in = resources.apply(resource);
        if (in == null) {
            throw new NoSuchFileException(resource, null, "missing from the TickRadar jar");
        }
        return in;
    }

    private static String readText(Path file, String name) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (CharacterCodingException e) {
            throw new UnreadableFileException(name + ": not a UTF-8 text file");
        } catch (IOException e) {
            throw new UnreadableFileException(name + ": " + e.getMessage());
        }
    }

    private static final class UnreadableFileException extends RuntimeException {

        UnreadableFileException(String message) {
            super(message, null, false, false);
        }
    }
}
