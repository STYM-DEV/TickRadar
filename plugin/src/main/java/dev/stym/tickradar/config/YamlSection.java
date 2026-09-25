package dev.stym.tickradar.config;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.error.MarkedYAMLException;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;

final class YamlSection {

    private final Context context;
    private final String path;
    private final int line;
    private final Map<String, NodeTuple> entries = new LinkedHashMap<>();
    private final Set<String> read = new HashSet<>();

    static final class Context {

        private record Warning(int line, String text) {
        }

        private final String fileName;
        private final List<Warning> warnings = new ArrayList<>();
        private final List<YamlSection> sections = new ArrayList<>();

        Context(String fileName) {
            this.fileName = fileName;
        }

        void warn(int line, String text) {
            warnings.add(new Warning(line, text));
        }

        List<String> finish() {
            for (YamlSection section : sections) {
                section.warnUnknownKeys();
            }
            return warnings.stream()
                    .sorted(Comparator.comparingInt(Warning::line))
                    .map(warning -> fileName + ":" + warning.line() + ": " + warning.text())
                    .toList();
        }
    }

    private YamlSection(Context context, String path, int line, MappingNode node) {
        this.context = context;
        this.path = path;
        this.line = line;
        context.sections.add(this);
        if (node == null) {
            return;
        }
        for (NodeTuple tuple : node.getValue()) {
            if (!(tuple.getKeyNode() instanceof ScalarNode key)) {
                context.warn(lineOf(tuple.getKeyNode()), "keys must be plain names; entry ignored");
                continue;
            }
            if (entries.put(key.getValue(), tuple) != null) {
                context.warn(lineOf(key), "'" + fullKey(key.getValue()) + "' is set twice; the last value is used");
            }
        }
    }

    static LoadResult<YamlSection> parse(String fileName, String content) {
        Node document;
        try {
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(true);
            document = new Yaml(new SafeConstructor(options)).compose(new StringReader(content));
        } catch (MarkedYAMLException e) {
            Mark mark = e.getProblemMark() != null ? e.getProblemMark() : e.getContextMark();
            String where = mark == null ? fileName : fileName + ":" + (mark.getLine() + 1);
            return new LoadResult.Unreadable<>(where + ": " + firstLine(e.getProblem() != null ? e.getProblem() : e.getMessage()));
        } catch (YAMLException e) {
            return new LoadResult.Unreadable<>(fileName + ": " + firstLine(e.getMessage()));
        }
        Context context = new Context(fileName);
        if (document == null) {
            return new LoadResult.Loaded<>(new YamlSection(context, "", 1, null), List.of());
        }
        if (!(document instanceof MappingNode mapping)) {
            return new LoadResult.Unreadable<>(fileName + ":" + lineOf(document)
                    + ": the file must be a list of 'key: value' settings");
        }
        return new LoadResult.Loaded<>(new YamlSection(context, "", lineOf(mapping), mapping), List.of());
    }

    List<String> finish() {
        return context.finish();
    }

    YamlSection section(String key) {
        Node value = value(key);
        if (value == null || isNull(value)) {
            return new YamlSection(context, fullKey(key), line, null);
        }
        if (value instanceof MappingNode mapping) {
            return new YamlSection(context, fullKey(key), lineOf(value), mapping);
        }
        context.warn(lineOf(value), "'" + fullKey(key) + "' must be a section of settings; using the defaults");
        return new YamlSection(context, fullKey(key), lineOf(value), null);
    }

    int integer(String key, int defaultValue, int min, int max) {
        return wholeNumber(key, defaultValue, min, max, false);
    }

    int integerOrDefault(String key, int defaultValue, int min, int max) {
        return wholeNumber(key, defaultValue, min, max, true);
    }

    private int wholeNumber(String key, int defaultValue, int min, int max, boolean defaultWhenOutOfRange) {
        Optional<String> text = scalar(key);
        if (text.isEmpty()) {
            return defaultValue;
        }
        int valueLine = lineOf(value(key));
        int parsed;
        try {
            parsed = Integer.parseInt(text.get().trim());
        } catch (NumberFormatException e) {
            context.warn(valueLine, "'" + fullKey(key) + "' must be a whole number; using " + defaultValue);
            return defaultValue;
        }
        if (parsed < min || parsed > max) {
            int used = defaultWhenOutOfRange ? defaultValue : Math.clamp(parsed, min, max);
            context.warn(valueLine, "'" + fullKey(key) + "' must be between " + min + " and " + max + "; using " + used);
            return used;
        }
        return parsed;
    }

    double decimal(String key, double defaultValue) {
        Optional<String> text = scalar(key);
        if (text.isEmpty()) {
            return defaultValue;
        }
        try {
            double parsed = Double.parseDouble(text.get().trim());
            if (Double.isFinite(parsed)) {
                return parsed;
            }
        } catch (NumberFormatException e) {
            context.warn(lineOf(value(key)), "'" + fullKey(key) + "' must be a number; using " + defaultValue);
            return defaultValue;
        }
        context.warn(lineOf(value(key)), "'" + fullKey(key) + "' must be a finite number; using " + defaultValue);
        return defaultValue;
    }

    boolean bool(String key, boolean defaultValue, boolean valueIfInvalid) {
        Optional<String> text = scalar(key);
        if (text.isEmpty()) {
            return defaultValue;
        }
        switch (text.get().trim().toLowerCase(Locale.ROOT)) {
            case "true", "yes", "on" -> {
                return true;
            }
            case "false", "no", "off" -> {
                return false;
            }
            default -> {
                context.warn(lineOf(value(key)), "'" + fullKey(key) + "' must be true or false; using " + valueIfInvalid);
                return valueIfInvalid;
            }
        }
    }

    String string(String key, String defaultValue) {
        return scalar(key).orElse(defaultValue);
    }

    List<String> strings(String key, List<String> defaultValue) {
        Node value = value(key);
        if (value == null || isNull(value)) {
            return defaultValue;
        }
        if (!(value instanceof SequenceNode sequence)) {
            context.warn(lineOf(value), "'" + fullKey(key) + "' must be a list such as [a, b]; using " + defaultValue);
            return defaultValue;
        }
        List<String> items = new ArrayList<>();
        for (Node item : sequence.getValue()) {
            if (item instanceof ScalarNode scalar && !isNull(scalar)) {
                items.add(scalar.getValue());
            } else {
                context.warn(lineOf(item), "'" + fullKey(key) + "' must only contain plain values; entry ignored");
            }
        }
        return items;
    }

    List<YamlSection> sections(String key) {
        Node value = value(key);
        if (value == null || isNull(value)) {
            return List.of();
        }
        if (!(value instanceof SequenceNode sequence)) {
            context.warn(lineOf(value), "'" + fullKey(key) + "' must be a list; ignored");
            return List.of();
        }
        List<YamlSection> items = new ArrayList<>();
        for (Node item : sequence.getValue()) {
            if (item instanceof MappingNode mapping) {
                items.add(new YamlSection(context, fullKey(key), lineOf(item), mapping));
            } else {
                context.warn(lineOf(item), "'" + fullKey(key) + "' entries must be sections such as {a: 1}; entry ignored");
            }
        }
        return items;
    }

    Map<String, Object> flatten() {
        Map<String, Object> flat = new LinkedHashMap<>();
        for (Map.Entry<String, NodeTuple> entry : entries.entrySet()) {
            Node value = entry.getValue().getValueNode();
            String key = fullKey(entry.getKey());
            read.add(entry.getKey());
            switch (value) {
                case MappingNode mapping -> flat.putAll(new YamlSection(context, key, lineOf(mapping), mapping).flatten());
                case ScalarNode scalar when !isNull(scalar) -> flat.put(key, scalar.getValue());
                case SequenceNode sequence -> flat.put(key, strings(entry.getKey(), List.of()));
                default -> context.warn(lineOf(value), "'" + key + "' has no value; ignored");
            }
        }
        return flat;
    }

    void warn(String key, String text) {
        Node value = value(key);
        context.warn(value == null ? line : lineOf(value), "'" + fullKey(key) + "' " + text);
    }

    private static String firstLine(String message) {
        if (message == null) {
            return "unreadable YAML";
        }
        int end = message.indexOf('\n');
        return (end < 0 ? message : message.substring(0, end)).trim();
    }

    private Optional<String> scalar(String key) {
        Node value = value(key);
        if (value == null || isNull(value)) {
            return Optional.empty();
        }
        if (value instanceof ScalarNode scalar) {
            return Optional.of(scalar.getValue());
        }
        context.warn(lineOf(value), "'" + fullKey(key) + "' must be a single value; using the default");
        return Optional.empty();
    }

    private Node value(String key) {
        read.add(key);
        NodeTuple tuple = entries.get(key);
        return tuple == null ? null : tuple.getValueNode();
    }

    private void warnUnknownKeys() {
        for (Map.Entry<String, NodeTuple> entry : entries.entrySet()) {
            if (!read.contains(entry.getKey())) {
                context.warn(lineOf(entry.getValue().getKeyNode()), "unknown key '" + fullKey(entry.getKey()) + "' ignored");
            }
        }
    }

    private String fullKey(String key) {
        return path.isEmpty() ? key : path + "." + key;
    }

    private static boolean isNull(Node node) {
        return Tag.NULL.equals(node.getTag());
    }

    private static int lineOf(Node node) {
        return node == null || node.getStartMark() == null ? 0 : node.getStartMark().getLine() + 1;
    }
}
