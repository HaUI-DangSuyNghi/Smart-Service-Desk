package vn.haui.dx.hpdi.process.api;

import java.util.List;
import java.util.Map;

/** Danh mục quy trình cho giao diện chung của Tầng 2. */
public interface ProcessCatalog {
    /** Mọi quy trình đã đăng ký kèm nhãn; người gọi phải là thành viên workspace. */
    List<ProcessDescriptor> describe(String workspace);

    /**
     * Tạo yêu cầu từ form chung. Chỉ nhận quy trình tự phục vụ; {@code businessKey} do server sinh
     * vì loại quy trình này không gắn bản ghi nghiệp vụ nào.
     */
    ProcessHandle startSelfService(String workspace, String processKey, Map<String, String> variables);
}
