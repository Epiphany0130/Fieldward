package com.fieldward.resume;

import com.fieldward.account.AccountPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping("/api/resume")
class ResumeDraftController {
    private final ResumeDraftMapper drafts;
    private final ResumeVersionMapper versions;
    private final ResumeUploadMapper uploads;
    private final ResumeSubmissions submissions;
    private final JsonMapper json;
    private final ResumeUpdateService updates;

    ResumeDraftController(ResumeDraftMapper drafts, ResumeVersionMapper versions, ResumeUploadMapper uploads,
                          ResumeSubmissions submissions, JsonMapper json, ResumeUpdateService updates) {
        this.drafts = drafts;
        this.versions = versions;
        this.uploads = uploads;
        this.submissions = submissions;
        this.json = json;
        this.updates = updates;
    }

    @GetMapping("/draft")
    DraftView get(@AuthenticationPrincipal AccountPrincipal student) {
        long accountId = student.accountId();
        var stored = drafts.find(accountId);
        return stored == null
                ? new DraftView(Long.toString(accountId), 0, null, ResumeDraftContent.empty(), versions.latest(accountId), null)
                : toView(accountId, stored);
    }

    @PutMapping("/draft")
    @org.springframework.transaction.annotation.Transactional
    DraftView save(@AuthenticationPrincipal AccountPrincipal student, @RequestBody @Valid DraftSave input) {
        long accountId = requireAccount(student, input.accountId());
        if (versions.lockAccount(accountId) == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
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
    ResumeVersionMapper.Submission submit(@AuthenticationPrincipal AccountPrincipal student,
                                          @RequestBody @Valid SubmissionInput input) {
        return submissions.submitDraft(requireAccount(student, input.accountId()), input.revision());
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResumeVersionMapper.Submission upload(@AuthenticationPrincipal AccountPrincipal student,
                                          @RequestParam String accountId, @RequestParam UUID requestId,
                                          @RequestPart MultipartFile file) {
        long studentId = requireAccount(student, accountId);
        return submissions.submitUpload(studentId, requestId.toString(), ResumeUpload.read(file));
    }

    @PostMapping("/convert")
    ResumeUpdateService.ConversionResult convert(@AuthenticationPrincipal AccountPrincipal student,
                                                 @RequestBody @Valid ConversionInput input) {
        return updates.convertUpload(requireAccount(student, input.accountId()), input.version(), input.latestVersion(), input.draftRevision());
    }

    @PostMapping("/restore")
    ResumeVersionMapper.Submission restore(@AuthenticationPrincipal AccountPrincipal student,
                                           @RequestBody @Valid ConversionInput input) {
        return updates.restore(requireAccount(student, input.accountId()), input.version(), input.latestVersion(), input.draftRevision());
    }

    @GetMapping("/versions")
    java.util.List<ResumeVersionMapper.Submission> versions(@AuthenticationPrincipal AccountPrincipal student) {
        return versions.list(student.accountId());
    }

    @GetMapping("/final")
    FinalView finalDocument(@AuthenticationPrincipal AccountPrincipal student,
                            @RequestParam(required = false) @Positive Long version) {
        long accountId = student.accountId();
        var document = version == null ? versions.latestDocument(accountId) : versions.findDocument(accountId, version);
        if (document == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到该简历版本");
        }
        var content = document.content() == null ? null : json.readValue(document.content(), ResumeDraftContent.class);
        var upload = document.uploadRequestId() == null ? null : uploads.findMetadata(accountId, document.uploadRequestId());
        return new FinalView(Long.toString(accountId), document.version(), document.submittedAt(), content, upload);
    }

    @GetMapping("/final/original")
    ResponseEntity<byte[]> original(@AuthenticationPrincipal AccountPrincipal student, @RequestParam @Positive long version) {
        var file = uploads.findOriginal(student.accountId(), version);
        if (file == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到该版本的原文件");
        }
        String disposition = ContentDisposition.attachment().filename(file.name(), StandardCharsets.UTF_8).build().toString();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.bytes());
    }

    private long requireAccount(AccountPrincipal student, String expectedId) {
        long accountId = student.accountId();
        if (!Long.toString(accountId).equals(expectedId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前登录账号已变更，请重新登录原账号后继续");
        }
        return accountId;
    }

    private DraftView toView(long accountId, ResumeDraftMapper.StoredDraft stored) {
        var latest = versions.latest(accountId);
        var submittedDraft = versions.findByDraftRevision(accountId, stored.revision());
        return new DraftView(Long.toString(accountId), stored.revision(), stored.updatedAt(),
                json.readValue(stored.content(), ResumeDraftContent.class), latest,
                submittedDraft == null ? null : submittedDraft.version());
    }

    record DraftSave(@NotNull @Pattern(regexp = "[0-9]{1,19}") String accountId,
                     @NotNull @PositiveOrZero Long revision,
                     @NotNull @Valid ResumeDraftContent content) {}

    record SubmissionInput(@NotNull @Pattern(regexp = "[0-9]{1,19}") String accountId,
                           @NotNull @Positive Long revision) {}

    record ConversionInput(@NotNull @Pattern(regexp = "[0-9]{1,19}") String accountId,
                           @NotNull @Positive Long version, @NotNull @Positive Long latestVersion,
                           @NotNull @PositiveOrZero Long draftRevision) {}

    record FinalView(String accountId, long version, OffsetDateTime submittedAt, ResumeDraftContent content,
                     ResumeUploadMapper.Metadata upload) {}

    record DraftView(String accountId, long revision, OffsetDateTime updatedAt, ResumeDraftContent content,
                     ResumeVersionMapper.Submission submission, Long submittedDraftVersion) {}
}
