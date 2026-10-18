package vn.haui.dx.hpdi.common.api;

/** Yêu cầu phân trang: {@code page} tính từ 0, {@code size} tối đa {@value #MAX_SIZE}. */
public record PageRequest(int page, int size) {
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public PageRequest {
        if (page < 0 || size < 1 || size > MAX_SIZE)
            throw CapabilityException.validation("page must be >= 0 and size between 1 and " + MAX_SIZE);
    }
    /** Tạo từ query param, thiếu thì dùng mặc định. */
    public static PageRequest of(Integer page, Integer size) {
        return new PageRequest(page == null ? 0 : page, size == null ? DEFAULT_SIZE : size);
    }
    public long offset() { return (long) page * size; }
}
