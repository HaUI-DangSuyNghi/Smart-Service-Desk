# Dependency truy xuất AI-03

Ngày đối chiếu: 17/10/2026. Các pin phục vụ baseline Python 3.12, không phải
tuyên bố phiên bản mới nhất hoặc đã audit CVE. AI-02 giữ nguyên các pin cũ.

| Dependency | Pin | Nguồn license chính thức |
| --- | --- | --- |
| scikit-learn | 1.6.1 | [BSD-3-Clause](https://github.com/scikit-learn/scikit-learn/blob/1.6.1/COPYING) |
| NumPy | 2.2.6 | [BSD-3-Clause và notices](https://github.com/numpy/numpy/blob/v2.2.6/LICENSE.txt) |
| SciPy | 1.15.3 | [BSD-3-Clause và notices](https://github.com/scipy/scipy/blob/v1.15.3/LICENSE.txt) |
| joblib | 1.4.2 | [BSD-3-Clause](https://github.com/joblib/joblib/blob/1.4.2/LICENSE.txt) |
| threadpoolctl | 3.6.0 | [BSD-3-Clause](https://github.com/joblib/threadpoolctl/blob/3.6.0/LICENSE) |

scikit-learn kéo NumPy, SciPy, joblib, threadpoolctl; SciPy phụ thuộc NumPy.
Khóa cả closure trong `requirements.lock` và runtime metadata để tránh cài wheel
tự chọn bộ số học khác. Pin 1.6.1 hỗ trợ Python 3.12 và đủ TF-IDF sparse; không
thêm framework orchestration. [API TF-IDF chính thức](https://scikit-learn.org/1.6/modules/generated/sklearn.feature_extraction.text.TfidfVectorizer.html).

Wheel số học có thể kèm OpenBLAS, LAPACK và runtime compiler với notices riêng.
Nguồn license cấp project không thay thế notices của binary. Báo cáo resolution
ghi URL/hash wheel và metadata; phải giữ các LICENSE/NOTICE đi kèm khi phân phối.
Không dùng `joblib.load`/pickle để đọc index, dù joblib là dependency bắc cầu.

Lựa chọn dependency không thay đổi license repository, không cấp quyền cho
fixture và không đóng gate phát hành. Windows được kiểm tra local; Linux do
matrix CI kiểm tra, không ghi PASS khi chưa có lần chạy thực tế.
