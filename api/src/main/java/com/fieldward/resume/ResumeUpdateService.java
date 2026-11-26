package com.fieldward.resume;

import java.util.ArrayList;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

/** Applies only independently verified evidence, never synthesizing project experience. */
@Service
public class ResumeUpdateService {
    private final ResumeVersionMapper versions;
    private final ResumeDraftMapper drafts;
    private final ResumeUploadMapper uploads;
    private final JdbcTemplate db;
    private final JsonMapper json;

    ResumeUpdateService(ResumeVersionMapper versions, ResumeDraftMapper drafts, ResumeUploadMapper uploads, JdbcTemplate db, JsonMapper json) {
        this.versions = versions; this.drafts = drafts; this.uploads = uploads; this.db = db; this.json = json;
    }

    public record ConversionResult(long version, boolean needsSupplement) {}

    @Transactional
    public ConversionResult convertUpload(long accountId, long sourceVersion, long expectedLatest, long expectedRevision) {
        if (versions.lockAccount(accountId) == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        var latest = versions.latestDocument(accountId);
        if (latest == null || latest.version() != expectedLatest)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "简历已有新版本，请刷新后重试");
        var source = versions.findDocument(accountId, sourceVersion);
        if (source == null || source.uploadRequestId() == null)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到上传原件");
        var upload = uploads.findMetadata(accountId, source.uploadRequestId());
        if (upload == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到上传原件");
        var draft = drafts.lock(accountId);
        if ((draft == null ? 0 : draft.revision()) != expectedRevision)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "草稿已在其他页面更新，请刷新后重试");
        String text = upload.text();
        if (text.codePointCount(0, text.length()) > 30000)
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "识别正文过长，请下载原件后手动补充");
        var entries = new ArrayList<ResumeDraftContent.Experience>();
        for (int start = 0; start < text.length();) {
            int end = text.offsetByCodePoints(start, Math.min(1000, text.codePointCount(start, text.length())));
            entries.add(new ResumeDraftContent.Experience(ResumeDraftContent.ExperienceType.OTHER,
                    "原件正文（请核对并整理）", "", "", "", text.substring(start, end), null));
            start = end;
        }
        var empty = ResumeDraftContent.empty();
        var layout = new ResumeDraftContent.Layout(ResumeDraftContent.Theme.RULED, ResumeDraftContent.Density.STANDARD,
                java.util.List.of(ResumeDraftContent.Section.OTHER, ResumeDraftContent.Section.SKILLS), null, null, null);
        var content = new ResumeDraftContent(empty.profile(), empty.education(), empty.additionalEducation(), entries, empty.skills(), layout);
        String serialized = json.writeValueAsString(content);
        Long maxRevision = db.queryForObject("SELECT COALESCE(MAX(GREATEST(draft_revision, generated_draft_revision)),0) FROM resume_versions WHERE account_id=?", Long.class, accountId);
        var saved = draft == null ? drafts.insertAtRevision(accountId, maxRevision + 1, serialized) : drafts.updateAfterVersion(accountId, expectedRevision, maxRevision, serialized);
        if (saved == null && (draft == null || !draft.content().equals(serialized)))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "草稿已在其他页面更新，请刷新后重试");
        var result = versions.insert(accountId, null, serialized, null);
        long savedRevision = saved == null ? draft.revision() : saved.revision();
        db.update("UPDATE resume_versions SET generated_draft_revision=? WHERE account_id=? AND version=?", savedRevision, accountId, result.version());
        return new ConversionResult(result.version(), text.isBlank());
    }

    @Transactional
    public ResumeVersionMapper.Submission restore(long accountId, long sourceVersion, long expectedLatest, long expectedRevision) {
        if (versions.lockAccount(accountId) == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        var latest = versions.latestDocument(accountId);
        if (latest == null || latest.version() != expectedLatest)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "简历已有新版本，请刷新后重试");
        var source = versions.findDocument(accountId, sourceVersion);
        if (source == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到该简历版本");
        var draft = drafts.lock(accountId);
        if ((draft == null ? 0 : draft.revision()) != expectedRevision)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "草稿已在其他页面更新，请刷新后重试");
        if (source.version() == latest.version()) return versions.latest(accountId);
        if (source.content() == null) {
            var original = uploads.findOriginal(accountId, sourceVersion);
            if (original == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到上传原件");
            String originalText = db.queryForObject("SELECT extracted_text FROM resume_uploads WHERE account_id=? AND request_id=CAST(? AS uuid)", String.class, accountId, source.uploadRequestId());
            String request = java.util.UUID.randomUUID().toString();
            uploads.insert(accountId, request, new ResumeUpload(original.name(), original.contentType(),
                    uploads.findHash(accountId, source.uploadRequestId()), originalText, original.bytes()));
            return versions.insert(accountId, null, null, request);
        }
        Long maxRevision = db.queryForObject("SELECT COALESCE(MAX(GREATEST(draft_revision, generated_draft_revision)),0) FROM resume_versions WHERE account_id=?", Long.class, accountId);
        var saved = draft == null ? drafts.insertAtRevision(accountId, maxRevision + 1, source.content())
                : drafts.updateAfterVersion(accountId, expectedRevision, maxRevision, source.content());
        if (saved == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "草稿已在其他页面更新，请刷新后重试");
        var result = versions.insert(accountId, null, source.content(), null);
        db.update("UPDATE resume_versions SET generated_draft_revision=? WHERE account_id=? AND version=?", saved.revision(), accountId, result.version());
        return result;
    }

    @Transactional
    public ResumeVersionMapper.Submission applyEvidence(long accountId, long baseVersion, long evidenceId, String requestId) {
        if (versions.lockAccount(accountId) == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        var prior = db.query("SELECT version, evidence_id FROM resume_evidence_updates WHERE account_id=? AND request_id=CAST(? AS uuid)",
                (rs, n) -> new long[]{rs.getLong(1), rs.getLong(2)}, accountId, requestId);
        if (!prior.isEmpty()) {
            if (prior.getFirst()[1] != evidenceId) throw new ResponseStatusException(HttpStatus.CONFLICT, "请求已用于其他证据");
            return new ResumeVersionMapper.Submission(prior.getFirst()[0], null, versions.findDocument(accountId, prior.getFirst()[0]).submittedAt());
        }
        var existing = db.query("SELECT version FROM resume_evidence_updates WHERE account_id=? AND evidence_id=?", (rs,n) -> rs.getLong(1), accountId, evidenceId);
        if (!existing.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT, "该证据已更新简历");
        var titles = db.query("SELECT c.title FROM capability_evidence e JOIN career_capabilities c ON c.id=e.capability_id WHERE e.id=? AND e.account_id=? AND e.outcome='PASSED' AND e.source_type IN ('LEARNING','INTERVIEW','DIAGNOSIS')", (rs,n) -> rs.getString(1), evidenceId, accountId);
        if (titles.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到可用于简历的有效证据");
        var latest = versions.latestDocument(accountId);
        if (latest == null || versions.findDocument(accountId, baseVersion) == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到该简历版本");
        if (latest.content() == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "请先将上传的简历转为可编辑版本");
        var draft = drafts.lock(accountId);
        Long latestRevision = db.queryForObject("SELECT COALESCE(draft_revision, generated_draft_revision) FROM resume_versions WHERE account_id=? AND version=?", Long.class, accountId, latest.version());
        if (draft != null && latestRevision == null && !draft.content().equals(latest.content()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "无法确定草稿与最新版本的关系，请先确认并提交草稿");
        String source = draft != null && (latestRevision == null || draft.revision() >= latestRevision)
                ? draft.content() : latest.content();
        if (source == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "请先将上传的简历转为可编辑版本");
        var content = json.readValue(source, ResumeDraftContent.class);
        var skills = new ArrayList<>(content.skills());
        if (skills.stream().anyMatch(s -> s.name().equalsIgnoreCase(titles.getFirst()))) {
            var latestContent = json.readValue(latest.content(), ResumeDraftContent.class);
            if (latestContent.skills().stream().noneMatch(s -> s.name().equalsIgnoreCase(titles.getFirst())))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "该技能只存在于未提交的草稿中，请先提交后重试");
            db.update("INSERT INTO resume_evidence_updates(account_id,request_id,evidence_id,version) VALUES (?,CAST(? AS uuid),?,?)", accountId, requestId, evidenceId, latest.version());
            return versions.latest(accountId);
        }
        {
            if (skills.size() >= 30) throw new ResponseStatusException(HttpStatus.CONFLICT, "技能栏已满，请先编辑简历");
            skills.add(new ResumeDraftContent.Skill(titles.getFirst(), ResumeDraftContent.Proficiency.GUIDED, "已通过能力验证"));
        }
        var updated = new ResumeDraftContent(content.profile(), content.education(), content.additionalEducation(), content.experiences(), skills, content.layout());
        String serialized = json.writeValueAsString(updated);
        Long maxRevision = db.queryForObject("SELECT COALESCE(MAX(GREATEST(draft_revision, generated_draft_revision)),0) FROM resume_versions WHERE account_id=?", Long.class, accountId);
        var saved = draft == null ? drafts.insertAtRevision(accountId, maxRevision + 1, serialized)
                : drafts.updateAfterVersion(accountId, draft.revision(), maxRevision, serialized);
        if (saved == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "草稿已在其他页面更新，请刷新后重试");
        var result = versions.insert(accountId, null, serialized, null);
        db.update("UPDATE resume_versions SET generated_draft_revision=? WHERE account_id=? AND version=?", saved.revision(), accountId, result.version());
        db.update("INSERT INTO resume_evidence_updates(account_id,request_id,evidence_id,version) VALUES (?,CAST(? AS uuid),?,?)", accountId, requestId, evidenceId, result.version());
        db.update("INSERT INTO mentor_turns(conversation_id,request_id,student_message,mentor_reply,status,attempt) SELECT id,gen_random_uuid(),NULL,?,'COMPLETED',1 FROM mentor_main_conversations WHERE account_id=?", "已根据验证证据更新简历技能「" + titles.getFirst() + "」，保存为第 " + result.version() + " 版；你可以查看历史版本。", accountId);
        return result;
    }
}
