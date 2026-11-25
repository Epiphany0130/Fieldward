package com.fieldward.mentor;

import com.fieldward.account.AccountPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class WorkspaceController {
    private final MentorConversationMapper conversations;

    WorkspaceController(MentorConversationMapper conversations) {
        this.conversations = conversations;
    }

    @GetMapping("/api/workspace")
    WorkspaceState workspace(@AuthenticationPrincipal AccountPrincipal student) {
        long accountId = student.accountId();
        return new WorkspaceState(Long.toString(accountId), conversations.findWorkspace(accountId));
    }

    record WorkspaceState(String accountId, MentorConversationMapper.Workspace workspace) {}
}
