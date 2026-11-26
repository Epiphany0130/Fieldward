package com.fieldward.mentor;

import com.fieldward.PostgresIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

class ForgetRequestServiceTest extends PostgresIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ForgetRequestService service;
    @Autowired MentorLibraryController controller;

    @Test @Transactional
    void explicitConfirmationIsAccountScopedAndDeletesOnlySelectedMemory() {
        long owner = account("forget-owner"), other = account("forget-other");
        long conversation = conversation(owner), otherConversation = conversation(other);
        long selected = note(conversation, "secretbanana"), retained = note(conversation, "unrelated");
        long otherNote = note(otherConversation, "secretbanana");
        var proposal = service.propose(owner, "忘记 secretbanana");
        assertThat(count(selected)).isEqualTo(1);
        var scope = new ForgetRequestService.Scope(List.of(selected), List.of(), List.of(), List.of());
        assertThatThrownBy(() -> service.confirm(other, proposal.id(), scope)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> service.confirm(owner, proposal.id(), new ForgetRequestService.Scope(List.of(otherNote), List.of(), List.of(), List.of()))).isInstanceOf(RuntimeException.class);
        service.confirm(owner, proposal.id(), scope);
        service.confirm(owner, proposal.id(), scope); // retries do not repeat deletion
        assertThatThrownBy(() -> service.confirm(owner, proposal.id(), new ForgetRequestService.Scope(List.of(retained), List.of(), List.of(), List.of()))).isInstanceOf(RuntimeException.class);
        assertThat(count(selected)).isZero();
        assertThat(count(retained)).isEqualTo(1);
        assertThat(count(otherNote)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mentor_memory_notes WHERE conversation_id=? AND mentor_search_vector(body) @@ to_tsquery('simple','secretbanana')", Integer.class, conversation)).isZero();
    }
    @Test @Transactional
    void fileDeletionRemovesItsReferencesAndOnlyUnsharedOcr() {
        long owner = account("forget-files"), conversation = conversation(owner);
        String hash = "a".repeat(64), otherHash = "b".repeat(64);
        long file = jdbc.queryForObject("INSERT INTO mentor_files(conversation_id,request_id,file_name,content_type,sha256,extracted_text,file_bytes) VALUES (?,gen_random_uuid(),'selected.pdf','application/pdf',?,'',decode('01','hex')) RETURNING id", Long.class, conversation, hash);
        jdbc.update("INSERT INTO mentor_document_ocr(account_id,sha256,status,attempt,available_at,body) VALUES (?,?,'READY',1,now(),'secretocr'), (?,?,'READY',1,now(),'unrelated')", owner, hash, owner, otherHash);
        jdbc.update("INSERT INTO mentor_turns(conversation_id,request_id,student_message,mentor_reply,status,attempt,sources) VALUES (?,gen_random_uuid(),'hello','reply','COMPLETED',1,CAST(? AS jsonb))", conversation, "[{\"href\":\"/api/mentor/files/" + file + "\",\"excerpt\":\"secretocr\"},{\"href\":\"/other\"}]");
        var proposal = service.propose(owner, "删除资料");
        service.confirm(owner, proposal.id(), new ForgetRequestService.Scope(List.of(), List.of(), List.of(file), List.of()));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mentor_document_ocr WHERE account_id=? AND sha256=?", Integer.class, owner, hash)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mentor_document_ocr WHERE account_id=? AND sha256=?", Integer.class, owner, otherHash)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT sources::text FROM mentor_turns WHERE conversation_id=?", String.class, conversation)).contains("/other").doesNotContain("secretocr");
    }
    @Test @Transactional
    void destructiveConfirmationEndpointIsDisabled() {
        long owner = account("forget-disabled"), conversation = conversation(owner);
        long selected = note(conversation, "private fact");
        var proposal = service.propose(owner, "忘记 private fact");
        var principal = org.mockito.Mockito.mock(com.fieldward.account.AccountPrincipal.class);
        org.mockito.Mockito.when(principal.accountId()).thenReturn(owner);
        var confirmation = new MentorLibraryController.ForgetConfirmation(true,
                new ForgetRequestService.Scope(List.of(selected), List.of(), List.of(), List.of()));
        assertThatThrownBy(() -> controller.confirmForget(principal, proposal.id(), confirmation))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(count(selected)).isEqualTo(1);
    }
    @Test @Transactional
    void proposalEndpointDoesNotPersistDescriptionWhileUnavailable() {
        long owner = account("forget-proposal-disabled");
        var principal = org.mockito.Mockito.mock(com.fieldward.account.AccountPrincipal.class);
        org.mockito.Mockito.when(principal.accountId()).thenReturn(owner);
        assertThatThrownBy(() -> controller.proposeForget(principal, new MentorLibraryController.ForgetInput("private fact")))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mentor_forget_requests WHERE account_id=?", Integer.class, owner)).isZero();
    }
    private int count(long id) { return jdbc.queryForObject("SELECT count(*) FROM mentor_memory_notes WHERE id=?", Integer.class, id); }
    private long note(long conversation, String body) { return jdbc.queryForObject("INSERT INTO mentor_memory_notes(conversation_id,category,body) VALUES (?, 'NOTE', ?) RETURNING id", Long.class, conversation, body); }
    private long account(String name) { return jdbc.queryForObject("INSERT INTO accounts(email,display_name,password_hash,role) VALUES (?,?,'fixture','STUDENT') RETURNING id", Long.class, name+"@test.invalid", name); }
    private long conversation(long account) {
        jdbc.update("INSERT INTO resume_versions(account_id,version,draft_revision,content) VALUES (?,1,1,'{}'::jsonb)", account);
        return jdbc.queryForObject("INSERT INTO mentor_main_conversations(account_id,first_resume_version) VALUES (?,1) RETURNING id", Long.class, account);
    }
}
