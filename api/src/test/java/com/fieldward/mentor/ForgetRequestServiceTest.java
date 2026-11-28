package com.fieldward.mentor;

import com.fieldward.PostgresIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;

class ForgetRequestServiceTest extends PostgresIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ForgetRequestService service;
    @Autowired MentorLibraryController controller;

    @Test @Transactional
    void previewIsBoundToExplicitOwnedObjectsAndConfirmRemainsUnavailable() {
        long owner = account("forget-owner"), other = account("forget-other");
        long conversation = conversation(owner), otherConversation = conversation(other);
        long selected = note(conversation, "private fact"), retained = note(conversation, "unrelated");
        long otherNote = note(otherConversation, "private fact");
        var scope = new ForgetRequestService.Scope(List.of(selected), List.of(), List.of(), List.of());
        assertThatThrownBy(() -> service.propose(owner, "forget", new ForgetRequestService.Scope(List.of(otherNote), List.of(), List.of(), List.of())))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.propose(owner, "forget", new ForgetRequestService.Scope(List.of(selected, selected), List.of(), List.of(), List.of())))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.propose(owner, "forget", new ForgetRequestService.Scope(List.of(), List.of(), List.of(), List.of())))
                .isInstanceOf(ResponseStatusException.class);
        var proposal = service.propose(owner, "sensitive description", scope);
        assertThat(proposal.scope()).isEqualTo(scope);
        assertThat(proposal.objects()).extracting(ForgetRequestService.PreviewObject::description).contains("private fact");
        assertThat(jdbc.queryForObject("SELECT proposed_scope::text FROM mentor_forget_requests WHERE id=?::uuid", String.class, proposal.id())).contains(Long.toString(selected));
        assertThat(jdbc.queryForObject("SELECT row_to_json(r)::text FROM mentor_forget_requests r WHERE id=?::uuid", String.class, proposal.id())).doesNotContain("private fact", "sensitive description");
        assertThatThrownBy(() -> service.confirm(other, proposal.id(), scope)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.confirm(owner, proposal.id(), new ForgetRequestService.Scope(List.of(retained), List.of(), List.of(), List.of()))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.confirm(owner, proposal.id(), scope)).isInstanceOf(ResponseStatusException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mentor_memory_notes WHERE id IN (?,?)", Integer.class, selected, retained)).isEqualTo(2);
    }

    @Test @Transactional
    void endpointsRemainUnavailableAndDoNotStoreDescriptions() {
        long owner = account("forget-disabled");
        var principal = org.mockito.Mockito.mock(com.fieldward.account.AccountPrincipal.class);
        org.mockito.Mockito.when(principal.accountId()).thenReturn(owner);
        assertThatThrownBy(() -> controller.proposeForget(principal, new MentorLibraryController.ForgetInput("private fact")))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(503));
        var scope = new ForgetRequestService.Scope(List.of(1L), List.of(), List.of(), List.of());
        assertThatThrownBy(() -> controller.confirmForget(principal, java.util.UUID.randomUUID().toString(), new MentorLibraryController.ForgetConfirmation(true, scope)))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(503));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mentor_forget_requests WHERE account_id=?", Integer.class, owner)).isZero();
    }
    private long note(long conversation, String body) { return jdbc.queryForObject("INSERT INTO mentor_memory_notes(conversation_id,category,body) VALUES (?, 'NOTE', ?) RETURNING id", Long.class, conversation, body); }
    private long account(String name) { return jdbc.queryForObject("INSERT INTO accounts(email,display_name,password_hash,role) VALUES (?,?,'fixture','STUDENT') RETURNING id", Long.class, name+"@test.invalid", name); }
    private long conversation(long account) {
        jdbc.update("INSERT INTO resume_versions(account_id,version,draft_revision,content) VALUES (?,1,1,'{}'::jsonb)", account);
        return jdbc.queryForObject("INSERT INTO mentor_main_conversations(account_id,first_resume_version) VALUES (?,1) RETURNING id", Long.class, account);
    }
}
