package dev.stym.tickradar.alert.discord;

import java.util.Map;

final class Templates {

    private Templates() {
    }

    static String fill(String template, Map<String, String> values) {
        StringBuilder out = new StringBuilder(template.length() + 32);
        int index = 0;
        while (index < template.length()) {
            int open = template.indexOf('{', index);
            int close = open < 0 ? -1 : template.indexOf('}', open + 1);
            if (close < 0) {
                out.append(template, index, template.length());
                break;
            }
            String value = values.get(template.substring(open + 1, close));
            out.append(template, index, open);
            out.append(value != null ? value : template.substring(open, close + 1));
            index = close + 1;
        }
        return out.toString();
    }
}
