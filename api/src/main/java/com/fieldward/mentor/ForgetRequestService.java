package com.fieldward.mentor;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

@Service
class ForgetRequestService {
    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    ForgetRequestService(JdbcTemplate jdbc, JsonMapper json) { this.jdbc = jdbc; this.json = json; }

    @Transactional
    public ForgetProposal propose(long accountId, String description) {
        if (description == null || description.isBlank() || description.length() > 1000 || description.indexOf(0) >= 0)
            throw bad("请输入要忘记的信息");
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO mentor_forget_requests(id,account_id,description) VALUES (?,?,?)", id, accountId, description.strip());
        return new ForgetProposal(id.toString(), description.strip());
    }

    @Transactional
    public ForgetResult confirm(long accountId, String proposalId, Scope scope) {
        UUID id;
        try { id = UUID.fromString(proposalId); } catch (RuntimeException e) { throw bad("请求编号无效"); }
        if (scope == null || scope.notes() == null || scope.turns() == null || scope.files() == null || scope.resumes() == null)
            throw bad("请明确选择处理范围");
        var rows = jdbc.queryForList("SELECT confirmed_scope::text FROM mentor_forget_requests WHERE id=? AND account_id=? FOR UPDATE", String.class, id, accountId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到遗忘请求");
        String serialized = json.writeValueAsString(scope);
        if (rows.getFirst() != null) {
            if (!json.readTree(rows.getFirst()).equals(json.readTree(serialized))) throw new ResponseStatusException(HttpStatus.CONFLICT, "请求已按其他范围确认");
            return new ForgetResult(proposalId, scope);
        }
        Long conversation = jdbc.queryForObject("SELECT id FROM mentor_main_conversations WHERE account_id=?", Long.class, accountId);
        check(scope.notes(), "SELECT count(*) FROM mentor_memory_notes WHERE conversation_id=? AND id=?", conversation);
        check(scope.turns(), "SELECT count(*) FROM mentor_turns WHERE conversation_id=? AND id=?", conversation);
        check(scope.files(), "SELECT count(*) FROM mentor_files WHERE conversation_id=? AND id=?", conversation);
        check(scope.resumes(), "SELECT count(*) FROM resume_versions WHERE account_id=? AND version=?", accountId);
        for (long turn : scope.turns()) {
            if (jdbc.queryForObject("SELECT count(*) FROM mentor_turns WHERE conversation_id=? AND id=? AND status='STREAMING'", Integer.class, conversation, turn) != 0)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "对话正在进行，请结束后再删除");
        }
        for (long version : scope.resumes()) {
            int dependencies = jdbc.queryForObject("SELECT (SELECT count(*) FROM mentor_main_conversations WHERE account_id=? AND first_resume_version=?) + (SELECT count(*) FROM career_assessments WHERE account_id=? AND resume_version=?)", Integer.class, accountId, version, accountId, version);
            if (dependencies != 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "该简历版本有关联资料，暂不能安全删除");
        }
        for (long note : scope.notes()) jdbc.update("DELETE FROM mentor_memory_notes WHERE conversation_id=? AND id=?", conversation, note);
        for (long turn : scope.turns()) jdbc.update("DELETE FROM mentor_turns WHERE conversation_id=? AND id=?", conversation, turn);
        for (long file : scope.files()) {
            jdbc.update("UPDATE mentor_turns SET sources = COALESCE((SELECT jsonb_agg(s) FROM jsonb_array_elements(sources) s WHERE s->>'href' IS DISTINCT FROM ?), '[]'::jsonb) WHERE conversation_id=?", "/api/mentor/files/" + file, conversation);
            String hash = jdbc.queryForObject("SELECT sha256 FROM mentor_files WHERE conversation_id=? AND id=?", String.class, conversation, file);
            jdbc.update("DELETE FROM mentor_files WHERE conversation_id=? AND id=?", conversation, file);
            jdbc.update("DELETE FROM mentor_document_ocr o WHERE account_id=? AND sha256=? AND NOT EXISTS (SELECT 1 FROM mentor_pdf_scans s WHERE s.account_id=o.account_id AND s.sha256=o.sha256)", accountId, hash);
        }
        for (long version : scope.resumes()) {
            jdbc.update("UPDATE mentor_turns SET sources = COALESCE((SELECT jsonb_agg(s) FROM jsonb_array_elements(sources) s WHERE s->>'href' IS DISTINCT FROM ?), '[]'::jsonb) WHERE conversation_id=?", "/resume/final?version=" + version, conversation);
            jdbc.update("DELETE FROM resume_versions WHERE account_id=? AND version=?", accountId, version);
        }
        jdbc.update("UPDATE mentor_forget_requests SET description='', confirmed_scope=CAST(? AS jsonb), confirmed_at=CURRENT_TIMESTAMP WHERE id=?", serialized, id);
        return new ForgetResult(proposalId, scope);
    }
    private void check(List<Long> ids, String sql, long owner) {
        if (ids.size() != ids.stream().distinct().count()) throw bad("对象重复");
        for (Long id : ids) if (id == null || id <= 0 || jdbc.queryForObject(sql, Integer.class, owner, id) != 1)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到所选资料");
    }
    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    record Scope(List<Long> notes, List<Long> turns, List<Long> files, List<Long> resumes) {}
    record ForgetProposal(String id, String description) {}
    record ForgetResult(String id, Scope scope) {}
}
