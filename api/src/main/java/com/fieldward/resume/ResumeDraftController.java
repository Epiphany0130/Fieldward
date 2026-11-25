package com.fieldward.resume;

import com.fieldward.account.AccountPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.OffsetDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping("/api/resume")
class ResumeDraftController {
    private final ResumeDraftMapper drafts;
    private final ResumeVersionMapper versions;
    private final JsonMapper json;

    ResumeDraftController(ResumeDraftMapper drafts, ResumeVersionMapper versions, JsonMapper json) {
        this.drafts = drafts;
        this.versions = versions;
        this.json = json;
    }

    @GetMapping("/draft")
    DraftView get(@AuthenticationPrincipal AccountPrincipal student) {
        long accountId = student.accountId();
        var stored = drafts.find(accountId);
        return stored == null
                ? new DraftView(Long.toString(accountId), 0, null, ResumeDraftContent.empty(), null)
                : toView(accountId, stored);
    }

    @PutMapping("/draft")
    DraftView save(@AuthenticationPrincipal AccountPrincipal student, @RequestBody @Valid DraftSave input) {
        long accountId = requireAccount(student, input.accountId());
        String content = json.writeValueAsString(input.content());
        var saved = input.revision() == 0
                ? drafts.insert(accountId, content)
                : drafts.update(accountId, input.revision(), content);
        if (saved != null) {
            return toView(accountId, saved);
        }

        var current = drafts.find(accountId);
        if (current != null) {
            var view = toView(accountId, current);
            if (view.content().equals(input.content())) {
                return view;
            }
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT, "草稿已在其他页面更新，本次修改仍留在此页");
    }

    @PostMapping("/submit")
    @Transactional
    public ResumeVersionMapper.Submission submit(@AuthenticationPrincipal AccountPrincipal student,
                                                 @RequestBody @Valid SubmissionInput input) {
        long accountId = requireAccount(student, input.accountId());
        // Serialize submissions with each other and with saves of this student's draft.
        var draft = drafts.lock(accountId);
        var existing = versions.findByDraftRevision(accountId, input.revision());
        if (existing != null) {
            return existing;
        }
        if (draft == null || draft.revision() != input.revision()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "草稿已在其他页面更新，请加载最新版本后提交");
        }
        var content = json.readValue(draft.content(), ResumeDraftContent.class);
        if (content.profile().name().isBlank() || !content.hasSchool()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写姓名和学校");
        }
        return versions.insert(accountId, draft.revision(), draft.content());
    }

    @GetMapping("/final")
    FinalView finalDocument(@AuthenticationPrincipal AccountPrincipal student,
                            @RequestParam(required = false) @Positive Long version) {
        long accountId = student.accountId();
        var document = version == null ? versions.latestDocument(accountId) : versions.findDocument(accountId, version);
        if (document == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到该简历版本");
        }
        return new FinalView(Long.toString(accountId), document.version(), document.submittedAt(),
                json.readValue(document.content(), ResumeDraftContent.class));
    }

    private long requireAccount(AccountPrincipal student, String expectedId) {
        long accountId = student.accountId();
        if (!Long.toString(accountId).equals(expectedId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前登录账号已变更，请重新登录原账号后继续");
        }
        return accountId;
    }

    private DraftView toView(long accountId, ResumeDraftMapper.StoredDraft stored) {
        return new DraftView(Long.toString(accountId), stored.revision(), stored.updatedAt(),
                json.readValue(stored.content(), ResumeDraftContent.class), versions.latest(accountId));
    }

    record DraftSave(@NotNull @Pattern(regexp = "[0-9]{1,19}") String accountId,
                     @NotNull @PositiveOrZero Long revision,
                     @NotNull @Valid ResumeDraftContent content) {}

    record SubmissionInput(@NotNull @Pattern(regexp = "[0-9]{1,19}") String accountId,
                           @NotNull @Positive Long revision) {}

    record FinalView(String accountId, long version, OffsetDateTime submittedAt, ResumeDraftContent content) {}

    record DraftView(String accountId, long revision, OffsetDateTime updatedAt, ResumeDraftContent content,
                     ResumeVersionMapper.Submission submission) {}
}
