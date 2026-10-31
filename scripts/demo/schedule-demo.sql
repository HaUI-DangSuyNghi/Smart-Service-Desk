-- Lich truc mau cho tai khoan demo (tuan nay + tuan sau, T2-T6), da duyet san.
-- Nguoi lam hanh chinh (tu truc moi ca T2-T6, tru ngay le) va nguoi dang ky theo ca (ca tuan / vai buoi).
-- Chay lai khong trung. Gio ca lay tu cau hinh hpdi_work_settings (ca 1 = Sang, ca 2 = Chieu).

-- Hanh chinh: user2 Tran Thi Binh, user9 Dang Thu Dung (lab-a), user5 Hoang Thu Ha (lab-b)
INSERT INTO hpdi_agent_work_mode (workspace, subject, mode, updated_by) VALUES
    ('lab-a', 'b5a318c4-d38c-4acd-ab78-cc3bd7f1efc1', 'OFFICE', 'system:demo'),
    ('lab-a', '427e1cc6-51c7-4f73-a56c-14fd40b82bb4', 'OFFICE', 'system:demo'),
    ('lab-b', '568cf3bf-2524-4108-8653-66669472d13b', 'OFFICE', 'system:demo')
ON CONFLICT (workspace, subject) DO UPDATE SET mode = 'OFFICE', updated_by = 'system:demo', updated_at = clock_timestamp();
-- Ban demo cu da dang ky ca cho ho: bo di vi hanh chinh khong can o
DELETE FROM hpdi_agent_slot s
USING hpdi_agent_work_mode m
WHERE s.registered_by = 'system:demo' AND m.mode = 'OFFICE'
  AND m.workspace = s.workspace AND m.subject = s.subject;

-- Theo ca
WITH plan(workspace, subject, pattern) AS (VALUES
    -- lab-a
    ('lab-a', 'de5017eb-1a0d-4667-aa63-f7d09f1ace16', 'full'),        -- superadmin
    ('lab-a', '7f523c46-f90f-4fc7-927e-443d627d1ae5', 'mwf-morning'), -- user8  Pham Van Cuong: sang T2, T4, T6
    ('lab-a', 'ce77856b-d2dc-4ae3-a4ec-4f25d06f435b', 'tue-thu'),     -- user10 Ngo Minh Duc: ca ngay T3, T5
    ('lab-a', 'f1f09793-a768-499b-a8ca-60a11e67d13f', 'afternoons'),  -- user11 Bui Lan Huong: chieu T2-T5
    -- lab-b
    ('lab-b', 'de5017eb-1a0d-4667-aa63-f7d09f1ace16', 'full'),        -- superadmin
    ('lab-b', 'a4954a1a-7d9c-4e77-8806-669b060860fe', 'mwf-morning'), -- user12 Ly Quang Hai: sang T2, T4, T6
    ('lab-b', '4150f73c-7ef4-47fc-b010-5d133d83b959', 'tue-thu')      -- user13 Vo Ngoc Lan: ca ngay T3, T5
),
days AS (
    SELECT d::date AS day
    FROM generate_series(
        date_trunc('week', (now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date),
        date_trunc('week', (now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date) + interval '13 days',
        interval '1 day') AS d
    WHERE extract(isodow FROM d) <= 5
      -- Bo ngay le trong cau hinh
      AND NOT EXISTS (
          SELECT 1 FROM hpdi_work_settings w, jsonb_array_elements(w.payload->'schedule'->'holidays') h
          WHERE w.id = 1 AND (h->>'date')::date = d::date)
),
shifts AS (
    SELECT s.value->>'name' AS name, (s.value->>'start')::time AS start_time,
           (s.value->>'end')::time AS end_time, s.idx
    FROM hpdi_work_settings, jsonb_array_elements(payload->'shifts') WITH ORDINALITY AS s(value, idx)
    WHERE id = 1
)
INSERT INTO hpdi_agent_slot (id, workspace, subject, slot_date, shift_name, starts_at, ends_at,
                             status, registered_by, decided_by, decided_at)
SELECT gen_random_uuid(), p.workspace, p.subject, d.day, s.name,
       (d.day + s.start_time) AT TIME ZONE 'Asia/Ho_Chi_Minh',
       (d.day + s.end_time) AT TIME ZONE 'Asia/Ho_Chi_Minh',
       'APPROVED', 'system:demo', 'system:demo', now()
FROM plan p CROSS JOIN days d CROSS JOIN shifts s
WHERE CASE p.pattern
    WHEN 'full' THEN true
    WHEN 'mwf-morning' THEN extract(isodow FROM d.day) IN (1, 3, 5) AND s.idx = 1
    WHEN 'tue-thu' THEN extract(isodow FROM d.day) IN (2, 4)
    WHEN 'afternoons' THEN extract(isodow FROM d.day) BETWEEN 1 AND 4 AND s.idx = 2
END
ON CONFLICT DO NOTHING;

-- Tong ket: hanh chinh, va so o theo ca moi nguoi trong 2 tuan
SELECT workspace, subject, 'OFFICE' AS mode, NULL::bigint AS slots
FROM hpdi_agent_work_mode WHERE mode = 'OFFICE'
UNION ALL
SELECT workspace, subject, 'SHIFT', count(*)
FROM hpdi_agent_slot
WHERE slot_date >= date_trunc('week', (now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date)
GROUP BY workspace, subject
ORDER BY 1, 3, 4 DESC;
