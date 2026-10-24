package dev.dov.tin.metal.command;

import dev.dov.metalj.resources.MTLResource;

public record Span(long id, long start, long end) {
    public Span(MTLResource resource, long start, long length) {
        this(resource.getId(), start, start + length);
    }

    public static Span whole(MTLResource resource) {
        return new Span(resource.getId(), 0, Long.MAX_VALUE);
    }

    public boolean overlaps(Span other) {
        return id == other.id && start < other.end && other.start < end;
    }
}
