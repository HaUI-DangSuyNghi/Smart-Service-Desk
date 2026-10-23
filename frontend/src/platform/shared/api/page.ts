/** Trang kết quả có phân trang, dùng chung cho mọi API danh sách. */
export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
}
