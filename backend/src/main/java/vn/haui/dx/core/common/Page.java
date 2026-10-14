package vn.haui.dx.core.common;

import java.util.List;

/** Một trang kết quả: {@code {"items":[...],"page":0,"size":20,"total":123}}. */
public record Page<T>(List<T> items, int page, int size, long total) {
    public Page { items = List.copyOf(items); }
}
