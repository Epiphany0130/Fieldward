package com.fieldward.resume;

import com.fieldward.mentor.MentorConversationMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

@Service
class ResumeSubmissions {
    private final ResumeDraftMapper drafts;
    private final ResumeVersionMapper versions;
    private final ResumeUploadMapper uploads;
    private final MentorConversationMapper conversations;
    private final JsonMapper json;

    ResumeSubmissions(ResumeDraftMapper drafts, ResumeVersionMapper versions, ResumeUploadMapper uploads,
                      MentorConversationMapper conversations, JsonMapper json) {
        this.drafts = drafts;
        this.versions = versions;
        this.uploads = uploads;
        this.conversations = conversations;
        this.json = json;
    }

    @Transactional
    public ResumeVersionMapper.Submission submitDraft(long accountId, long revision) {
        lockAccount(accountId);
        var draft = drafts.lock(accountId);
        var existing = versions.findByDraftRevision(accountId, revision);
        if (existing != null) {
            return existing;
        }
        if (draft == null || draft.revision() != revision) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "草稿已在其他页面更新，请加载最新版本后提交");
        }
        var content = json.readValue(draft.content(), ResumeDraftContent.class);
        if (content.profile().name().isBlank() || !content.hasSchool()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写姓名和学校");
        }
        return createVersion(accountId, revision, draft.content(), null);
    }

    @Transactional
    public ResumeVersionMapper.Submission submitUpload(long accountId, String requestId, ResumeUpload file) {
        lockAccount(accountId);
        var existing = versions.findByUploadRequest(accountId, requestId);
        if (existing != null) {
            if (!file.sha256().equals(uploads.findHash(accountId, requestId))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "文件已变更，请重新选择文件后提交");
            }
            return existing;
        }
        uploads.insert(accountId, requestId, file);
        return createVersion(accountId, null, null, requestId);
    }

    private ResumeVersionMapper.Submission createVersion(long accountId, Long revision, String content, String requestId) {
        var submission = versions.insert(accountId, revision, content, requestId);
        if (submission.version() == 1) {
            conversations.createMain(accountId, submission.version());
        }
        return submission;
    }

    private void lockAccount(long accountId) {
        // Both entry points allocate versions and open the first conversation under this lock.
        if (versions.lockAccount(accountId) == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录已失效，请重新登录");
        }
    }
}
