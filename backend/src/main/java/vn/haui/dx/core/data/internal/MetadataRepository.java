package vn.haui.dx.core.data.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import vn.haui.dx.core.common.CoreException;
import vn.haui.dx.core.common.Page;
import vn.haui.dx.core.common.PageRequest;
import vn.haui.dx.core.data.api.MetadataQuery;
import vn.haui.dx.core.data.api.MetadataRecord;
import vn.haui.dx.core.data.api.NewMetadata;

@Repository
public class MetadataRepository {
    private static final Map<String, String> SORTS = Map.of(
        "createdAt,desc", "created_at desc, id desc", "createdAt,asc", "created_at asc, id asc",
        "title,asc", "lower(title) asc, id asc", "title,desc", "lower(title) desc, id desc");
    private static final RowMapper<MetadataRecord> ROW = (row, index) -> new MetadataRecord(
        row.getObject("id", UUID.class), row.getString("workspace"), row.getString("title"),
        row.getString("source"), row.getString("version"), row.getString("usage_rights"),
        row.getString("checksum"), row.getObject("file_id", UUID.class), row.getString("owner"),
        row.getTimestamp("created_at").toInstant());
    private final JdbcTemplate jdbc;

    public MetadataRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public MetadataRecord insert(String workspace, String owner, NewMetadata input, String checksum) {
        UUID id = UUID.randomUUID();
        return jdbc.queryForObject("insert into metadata(id,workspace,title,source,version,usage_rights,checksum,file_id,owner) "
            + "values (?,?,?,?,?,?,?,?,?) returning *", ROW,
            id, workspace, input.title(), input.source(), input.version(), input.usageRights(), checksum, input.fileId(), owner);
    }

    public Optional<MetadataRecord> find(String workspace, UUID id) {
        return jdbc.query("select * from metadata where workspace=? and id=?", ROW, workspace, id).stream().findFirst();
    }

    public Page<MetadataRecord> search(String workspace, MetadataQuery query, PageRequest page) {
        String order = SORTS.get(query.sort() == null ? "createdAt,desc" : query.sort());
        if (order == null) throw CoreException.validation("Unsupported metadata sort");
        StringBuilder where = new StringBuilder(" where workspace=?");
        List<Object> args = new ArrayList<>(List.of(workspace));
        if (query.q() != null && !query.q().isEmpty()) {
            where.append(" and position(lower(?) in lower(title)) > 0");
            args.add(query.q());
        }
        if (query.source() != null) {
            where.append(" and source=?");
            args.add(query.source());
        }
        if (query.fileMediaTypes() != null) {
            // Rỗng = không loại nào hợp lệ → không trả bản ghi nào
            if (query.fileMediaTypes().isEmpty()) return new Page<>(List.of(), page.page(), page.size(), 0);
            where.append(" and file_id in (select id from file_object where workspace=metadata.workspace and media_type in (")
                .append(String.join(",", java.util.Collections.nCopies(query.fileMediaTypes().size(), "?")))
                .append("))");
            args.addAll(query.fileMediaTypes());
        }
        Long total = jdbc.queryForObject("select count(*) from metadata" + where, Long.class, args.toArray());
        args.add(page.size());
        args.add(page.offset());
        var items = jdbc.query("select * from metadata" + where + " order by " + order + " limit ? offset ?", ROW, args.toArray());
        return new Page<>(items, page.page(), page.size(), total);
    }
}
