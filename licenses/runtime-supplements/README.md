# Bổ sung giấy phép từ nguồn của đúng phiên bản

Wheel `tokenizers==0.23.3` và meta-package `cuda-toolkit==12.8.1` không mang file
LICENSE riêng trong bản phân phối đã cài. Thư mục này bổ sung tài liệu gốc:

- Tokenizers: toàn văn LICENSE lấy từ source distribution 0.23.3 trên PyPI;
  archive được kiểm SHA-256 trước khi đọc file, không giải nén hoặc thực thi source.
- CUDA Toolkit: toàn văn EULA HTML từ trang archive 12.8.1 chính thức của NVIDIA.

Nguồn, phiên bản, đường dẫn và checksum đều nằm trong [provenance.json](provenance.json).
Khi đóng gói, công cụ yêu cầu phiên bản trong image khớp manifest này. Các giấy phép
đi kèm wheel và image vẫn được giữ riêng trong bản kiểm kê runtime; không thay bằng
giấy phép bổ sung, không cấp lại license cho mã nguồn repo hoặc dữ liệu.
