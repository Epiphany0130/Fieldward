package com.fieldward.mentor;

import java.util.ArrayList;
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

    // Internal groundwork only. The public endpoints remain unavailable until derived content
    // and in-flight generation can be invalidated reliably.
    @Transactional
    public ForgetProposal propose(long accountId, String description, Scope scope) {
        if (description == null || description.isBlank() || description.length() > 1000 || description.indexOf(0) >= 0)
            throw bad("请输入要忘记的信息");
        validate(scope);
        List<PreviewObject> objects = new ArrayList<>();
        preview(objects, scope.notes(), "note", "SELECT n.body FROM mentor_memory_notes n JOIN mentor_main_conversations c ON c.id=n.conversation_id WHERE c.account_id=? AND n.id=? FOR SHARE OF n", accountId);
        preview(objects, scope.turns(), "turn", "SELECT concat_ws(E'\\n',t.student_message,t.mentor_reply) FROM mentor_turns t JOIN mentor_main_conversations c ON c.id=t.conversation_id WHERE c.account_id=? AND t.id=? FOR SHARE OF t", accountId);
        preview(objects, scope.files(), "file", "SELECT f.file_name FROM mentor_files f JOIN mentor_main_conversations c ON c.id=f.conversation_id WHERE c.account_id=? AND f.id=? FOR SHARE OF f", accountId);
        preview(objects, scope.resumes(), "resume", "SELECT content::text FROM resume_versions WHERE account_id=? AND version=? FOR SHARE", accountId);
        UUID id = UUID.randomUUID();
        Scope fixed = new Scope(List.copyOf(scope.notes()), List.copyOf(scope.turns()), List.copyOf(scope.files()), List.copyOf(scope.resumes()));
        List<PreviewObject> fixedObjects = List.copyOf(objects);
        jdbc.update("INSERT INTO mentor_forget_requests(id,account_id,description,proposed_scope) VALUES (?,?,?,CAST(? AS jsonb))",
                id, accountId, "", json.writeValueAsString(fixed));
        return new ForgetProposal(id.toString(), description.strip(), fixed, fixedObjects);
    }

    @Transactional
    public ForgetResult confirm(long accountId, String proposalId, Scope scope) {
        UUID id;
        try { id = UUID.fromString(proposalId); } catch (RuntimeException e) { throw bad("请求编号无效"); }
        validate(scope);
        var rows = jdbc.queryForList("SELECT proposed_scope::text FROM mentor_forget_requests WHERE id=? AND account_id=? FOR UPDATE", String.class, id, accountId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到遗忘请求");
        if (rows.getFirst() == null || !json.readTree(rows.getFirst()).equals(json.readTree(json.writeValueAsString(scope))))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "确认范围与预览不一致");
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "遗忘功能尚未开放，请稍后再试");
    }

    private void preview(List<PreviewObject> objects, List<Long> ids, String type, String sql, long owner) {
        for (Long id : ids) {
            List<String> rows = jdbc.queryForList(sql, String.class, owner, id);
            if (rows.size() != 1) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到所选资料");
            objects.add(new PreviewObject(type, id, rows.getFirst()));
        }
    }
    private void validate(Scope scope) {
        if (scope == null || scope.notes() == null || scope.turns() == null || scope.files() == null || scope.resumes() == null)
            throw bad("请明确选择处理范围");
        int total = 0;
        for (List<Long> ids : List.of(scope.notes(), scope.turns(), scope.files(), scope.resumes())) {
            if (ids.stream().anyMatch(id -> id == null || id <= 0) || ids.size() != ids.stream().distinct().count()) throw bad("对象无效或重复");
            total += ids.size();
        }
        if (total == 0 || total > 100) throw bad("请选择一至一百项资料");
    }
    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    record Scope(List<Long> notes, List<Long> turns, List<Long> files, List<Long> resumes) {}
    record PreviewObject(String type, long id, String description) {}
    record ForgetProposal(String id, String description, Scope scope, List<PreviewObject> objects) {}
    record ForgetResult(String id, Scope scope) {}
}
