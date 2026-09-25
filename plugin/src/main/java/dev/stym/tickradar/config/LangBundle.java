package dev.stym.tickradar.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class LangBundle {

    public static final String DEFAULT_LANGUAGE = "en";
    public static final String FOLDER = "lang";

    private final Map<String, Map<String, String>> languages;

    private LangBundle(Map<String, Map<String, String>> languages) {
        this.languages = Map.copyOf(languages);
    }

    public static LoadResult<LangBundle> load(Map<String, String> builtIn, Map<String, String> admin) {
        List<String> warnings = new ArrayList<>();
        Map<String, Map<String, String>> parsedBuiltIn = new LinkedHashMap<>();
        for (Map.Entry<String, String> file : builtIn.entrySet()) {
            LoadResult<Map<String, String>> parsed = parse(file.getKey(), file.getValue(), warnings);
            if (parsed instanceof LoadResult.Unreadable<Map<String, String>>(String error)) {
                return new LoadResult.Unreadable<>(error);
            }
            parsedBuiltIn.put(file.getKey(), ((LoadResult.Loaded<Map<String, String>>) parsed).value());
        }
        Map<String, String> reference = parsedBuiltIn.get(DEFAULT_LANGUAGE);
        if (reference == null) {
            return new LoadResult.Unreadable<>(fileName(DEFAULT_LANGUAGE) + ": the built-in English messages are missing");
        }
        Set<String> codes = new TreeSet<>(parsedBuiltIn.keySet());
        codes.addAll(admin.keySet());
        Map<String, Map<String, String>> merged = new LinkedHashMap<>();
        for (String code : codes) {
            Map<String, String> texts = new LinkedHashMap<>(reference);
            texts.putAll(parsedBuiltIn.getOrDefault(code, Map.of()));
            if (admin.containsKey(code)) {
                LoadResult<Map<String, String>> parsed = parse(code, admin.get(code), warnings);
                if (parsed instanceof LoadResult.Unreadable<Map<String, String>>(String error)) {
                    return new LoadResult.Unreadable<>(error);
                }
                overlay(code, texts, ((LoadResult.Loaded<Map<String, String>>) parsed).value(), warnings);
            }
            merged.put(code, Map.copyOf(texts));
        }
        return new LoadResult.Loaded<>(new LangBundle(merged), warnings);
    }

    public static String fileName(String language) {
        return FOLDER + "/" + language + ".yml";
    }

    public String resolve(String language) {
        return language != null && languages.containsKey(language) ? language : DEFAULT_LANGUAGE;
    }

    public String text(String language, String key) {
        String text = languages.get(resolve(language)).get(key);
        return text != null ? text : key;
    }

    public Set<String> languages() {
        return languages.keySet();
    }

    public Set<String> keys(String language) {
        return languages.get(resolve(language)).keySet();
    }

    private static void overlay(String code, Map<String, String> texts, Map<String, String> custom, List<String> warnings) {
        for (Map.Entry<String, String> entry : custom.entrySet()) {
            if (texts.containsKey(entry.getKey())) {
                texts.put(entry.getKey(), entry.getValue());
            } else {
                warnings.add(fileName(code) + ": unknown key '" + entry.getKey() + "' ignored");
            }
        }
    }

    private static LoadResult<Map<String, String>> parse(String code, String content, List<String> warnings) {
        return switch (YamlSection.parse(fileName(code), content)) {
            case LoadResult.Unreadable<YamlSection>(String error) -> new LoadResult.Unreadable<>(error);
            case LoadResult.Loaded<YamlSection>(YamlSection root, List<String> ignored) -> {
                Map<String, String> texts = new LinkedHashMap<>();
                root.flatten().forEach((key, value) -> texts.put(key, value instanceof List<?> lines
                        ? String.join("\n", lines.stream().map(String::valueOf).toList())
                        : String.valueOf(value)));
                warnings.addAll(root.finish());
                yield new LoadResult.Loaded<>(texts, List.of());
            }
        };
    }
}
