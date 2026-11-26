package com.fieldward.mentor;

import com.fieldward.account.AccountPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class WorkspaceController {
    private final MentorConversationMapper conversations;
    private final CareerPlans careers;
    private final MentorMemory memory;

    WorkspaceController(MentorConversationMapper conversations, CareerPlans careers, MentorMemory memory) {
        this.memory = memory;
        this.conversations = conversations;
        this.careers = careers;
    }

    @GetMapping("/api/workspace")
    WorkspaceState workspace(@AuthenticationPrincipal AccountPrincipal student) {
        long accountId = student.accountId();
        var workspace = conversations.findWorkspace(accountId);
        return new WorkspaceState(Long.toString(accountId), workspace, careers.current(accountId),
                workspace == null ? null : memory.nextStep(Long.parseLong(workspace.conversationId())));
    }

    record WorkspaceState(String accountId, MentorConversationMapper.Workspace workspace, CareerPlans.Summary career, String nextStep) {}
}
