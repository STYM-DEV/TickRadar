package dev.stym.tickradar.engine;

import java.util.List;

public record Attachment(Anchor anchor, boolean measureDue, List<Anchor> merged) {

    public Attachment {
        merged = List.copyOf(merged);
    }
}
