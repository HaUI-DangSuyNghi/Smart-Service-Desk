package vn.haui.dx.hpdi.human.api;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Đăng ký ca theo ô (ngày × ca trong Cấu hình), R3. Kỹ thuật viên tự đăng ký / huỷ ô của mình
 * trước giờ bắt đầu ca đầu tiên của ngày đó; điều phối viên đăng ký hộ, huỷ hộ, duyệt.
 */
public interface AgentSlots {
    record ShiftWindow(String name, LocalTime start, LocalTime end) {}
    record HolidayNote(LocalDate date, String name) {}
    /** Quy tắc cho màn đăng ký: các ca, hạn chót trong ngày, T7/CN và ngày lễ, cần duyệt. */
    record Policy(String zone, List<ShiftWindow> shifts, LocalTime cutoff,
                  boolean weekendRegistration, boolean approvalRequired, LocalDate today, List<HolidayNote> holidays) {}
    /** SHIFT: đăng ký từng ca; OFFICE: trực giờ hành chính (mọi ca, T2-T6, trừ ngày lễ), không cần đăng ký. */
    record WorkMode(String subject, String mode) {}
    record WorkModeChange(String mode) {}
    record Slot(UUID id, String subject, LocalDate date, String shift, Instant startsAt, Instant endsAt,
                String status, String registeredBy) {}
    record Registration(LocalDate date, String shift) {}

    Policy policy(String workspace);
    /** Kỹ thuật viên chỉ nhận ô của mình; điều phối viên nhận ô của mọi người. */
    List<Slot> list(String workspace, LocalDate from, LocalDate to);
    Slot register(String workspace, String subject, Registration registration);
    void cancel(String workspace, String subject, UUID id);
    Slot approve(String workspace, UUID id);
    void reject(String workspace, UUID id);
    /** Kỹ thuật viên nhận của mình; điều phối viên nhận mọi kỹ thuật viên hiện tại (chưa đặt = SHIFT). */
    List<WorkMode> workModes(String workspace);
    /** Kỹ thuật viên đổi của mình ("me"); điều phối viên đổi hộ. */
    WorkMode setWorkMode(String workspace, String subject, WorkModeChange change);
}
