package dev.stym.tickradar.display;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;

final class MarkerTemplate {

    private final Node root;

    MarkerTemplate(Component shape) {
        this.root = Node.of(shape);
    }

    Component render(DisplayValues values) {
        return root.render(values);
    }

    private static final class Node {

        private final Component component;
        private final String[] literals;
        private final DisplayTag[] tags;
        private final Node[] children;
        private final boolean dynamic;

        private Node(Component component, String[] literals, DisplayTag[] tags, Node[] children, boolean dynamic) {
            this.component = component;
            this.literals = literals;
            this.tags = tags;
            this.children = children;
            this.dynamic = dynamic;
        }

        static Node of(Component component) {
            List<String> literals = new ArrayList<>();
            List<DisplayTag> tags = new ArrayList<>();
            if (component instanceof TextComponent text) {
                split(text.content(), literals, tags);
            }
            List<Component> children = component.children();
            Node[] childNodes = new Node[children.size()];
            boolean dynamicChildren = false;
            for (int i = 0; i < childNodes.length; i++) {
                childNodes[i] = of(children.get(i));
                dynamicChildren |= childNodes[i].dynamic;
            }
            boolean hasTags = !tags.isEmpty();
            return new Node(component,
                    hasTags ? literals.toArray(String[]::new) : null,
                    hasTags ? tags.toArray(DisplayTag[]::new) : null,
                    dynamicChildren ? childNodes : null,
                    hasTags || dynamicChildren);
        }

        private static void split(String content, List<String> literals, List<DisplayTag> tags) {
            int literalStart = 0;
            for (int i = 0; i < content.length(); i++) {
                DisplayTag tag = DisplayTag.ofMarker(content.charAt(i));
                if (tag != null) {
                    literals.add(content.substring(literalStart, i));
                    tags.add(tag);
                    literalStart = i + 1;
                }
            }
            literals.add(content.substring(literalStart));
        }

        Component render(DisplayValues values) {
            if (!dynamic) {
                return component;
            }
            Component rendered = component;
            if (tags != null) {
                rendered = ((TextComponent) component).content(content(values));
            }
            if (children != null) {
                List<Component> renderedChildren = new ArrayList<>(children.length);
                for (Node child : children) {
                    renderedChildren.add(child.render(values));
                }
                rendered = rendered.children(renderedChildren);
            }
            return rendered;
        }

        private String content(DisplayValues values) {
            StringBuilder out = new StringBuilder(64);
            for (int i = 0; i < tags.length; i++) {
                out.append(literals[i]).append(values.text(tags[i]));
            }
            return out.append(literals[tags.length]).toString();
        }
    }
}
