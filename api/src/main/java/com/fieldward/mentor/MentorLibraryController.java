package com.fieldward.mentor;

import com.fieldward.account.AccountPrincipal;
import com.fieldward.resume.ResumeUpload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/mentor")
class MentorLibraryController {
    private final MentorMemory memory;
    private final MentorTurns turns;
    private final ForgetRequestService forget;

    MentorLibraryController(MentorMemory memory, MentorTurns turns, ForgetRequestService forget) {
        this.memory = memory;
        this.forget = forget;
        this.turns = turns;
    }

    @GetMapping("/memory")
    MentorMemory.Library library(@AuthenticationPrincipal AccountPrincipal student,
                                @RequestParam(required = false) @Positive Long beforeNote,
                                @RequestParam(required = false) @Positive Long beforeResume,
                                @RequestParam(required = false) @Positive Long beforeFile) {
        return memory.library(student.accountId(), beforeNote, beforeResume, beforeFile);
    }

    @PostMapping("/memory/notes")
    ResponseEntity<Void> note(@AuthenticationPrincipal AccountPrincipal student, @RequestBody @Valid MemoryNote input) {
        requireAccount(student, input.accountId());
        memory.remember(student.accountId(), input.body());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/side-chats")
    SideList sides(@AuthenticationPrincipal AccountPrincipal student) {
        return new SideList(Long.toString(student.accountId()), turns.sides(student.accountId()));
    }

    @PostMapping("/side-chats")
    MentorTurnMapper.Side createSide(@AuthenticationPrincipal AccountPrincipal student, @RequestBody @Valid NewSide input) {
        requireAccount(student, input.accountId());
        return turns.createSide(student.accountId(), input.title());
    }

    @PostMapping(value = "/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    MentorMemoryMapper.FileInfo upload(@AuthenticationPrincipal AccountPrincipal student,
                                      @RequestParam String accountId, @RequestParam UUID requestId,
                                      @RequestPart MultipartFile file) {
        requireAccount(student, accountId);
        return memory.addFile(student.accountId(), requestId.toString(), ResumeUpload.read(file));
    }

    @GetMapping("/files/{id}")
    ResponseEntity<byte[]> download(@AuthenticationPrincipal AccountPrincipal student, @PathVariable @Positive long id) {
        var file = memory.original(student.accountId(), id);
        if (file == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到该资料文件");
        String disposition = ContentDisposition.attachment().filename(file.name(), StandardCharsets.UTF_8).build().toString();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.bytes());
    }

    @PostMapping("/forget-requests")
    ForgetRequestService.ForgetProposal proposeForget(@AuthenticationPrincipal AccountPrincipal student,
                                                      @RequestBody @Valid ForgetInput input) {
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "遗忘功能尚未开放，请稍后再试");
    }

    @PostMapping("/forget-requests/{id}/confirm")
    ForgetRequestService.ForgetResult confirmForget(@AuthenticationPrincipal AccountPrincipal student,
                                                     @PathVariable String id, @RequestBody @Valid ForgetConfirmation input) {
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "遗忘功能尚未开放，请稍后再试");
    }

    record ForgetInput(@NotBlank @Size(max = 1000) String description) {}
    record ForgetConfirmation(Boolean confirmed, @jakarta.validation.constraints.NotNull ForgetRequestService.Scope scope) {}

    private void requireAccount(AccountPrincipal student, String expected) {
        if (!Long.toString(student.accountId()).equals(expected)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前登录账号已变更，请重新登录后继续");
        }
    }

    record SideList(String accountId, List<MentorTurnMapper.Side> sides) {}
    record NewSide(@NotBlank @Pattern(regexp = "[0-9]{1,19}") String accountId, @NotBlank @Size(max = 80) String title) {}
    record MemoryNote(@NotBlank @Pattern(regexp = "[0-9]{1,19}") String accountId, @NotBlank @Size(max = 1000) String body) {}
}
