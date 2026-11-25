package com.fieldward.resume;

import com.fieldward.account.AccountPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.OffsetDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping("/api/resume/draft")
class ResumeDraftController {
    private final ResumeDraftMapper drafts;
    private final JsonMapper json;

    ResumeDraftController(ResumeDraftMapper drafts, JsonMapper json) {
        this.drafts = drafts;
        this.json = json;
    }

    @GetMapping
    DraftView get(@AuthenticationPrincipal AccountPrincipal student) {
        long accountId = student.accountId();
        var stored = drafts.find(accountId);
        return stored == null
                ? new DraftView(Long.toString(accountId), 0, null, ResumeDraftContent.empty())
                : toView(accountId, stored);
    }

    @PutMapping
    DraftView save(@AuthenticationPrincipal AccountPrincipal student, @RequestBody @Valid DraftSave input) {
        long accountId = student.accountId();
        if (!Long.toString(accountId).equals(input.accountId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前登录账号已变更，请重新登录原账号后再保存");
        }

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

    private DraftView toView(long accountId, ResumeDraftMapper.StoredDraft stored) {
        return new DraftView(Long.toString(accountId), stored.revision(), stored.updatedAt(),
                json.readValue(stored.content(), ResumeDraftContent.class));
    }

    record DraftSave(@NotNull @Pattern(regexp = "[0-9]{1,19}") String accountId,
                     @NotNull @PositiveOrZero Long revision,
                     @NotNull @Valid ResumeDraftContent content) {}

    record DraftView(String accountId, long revision, OffsetDateTime updatedAt, ResumeDraftContent content) {}
}
