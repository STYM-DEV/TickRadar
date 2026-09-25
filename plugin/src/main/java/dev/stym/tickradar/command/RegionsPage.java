package dev.stym.tickradar.command;

import dev.stym.tickradar.engine.RegionSnapshot;
import dev.stym.tickradar.engine.RegionView;
import java.util.List;

record RegionsPage(int page, int pages, List<RegionSnapshot> entries) {

    RegionsPage {
        entries = List.copyOf(entries);
    }

    static RegionsPage of(RegionView view, int requestedPage, int perPage) {
        List<RegionSnapshot> all = view.globalFirst();
        int size = Math.max(1, perPage);
        int pages = Math.max(1, (all.size() + size - 1) / size);
        int page = Math.clamp(requestedPage, 1, pages);
        int from = (page - 1) * size;
        int to = Math.min(all.size(), from + size);
        return new RegionsPage(page, pages, all.subList(from, to));
    }

    boolean hasNext() {
        return page < pages;
    }

    boolean isEmpty() {
        return entries.isEmpty();
    }
}
