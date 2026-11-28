package com.fieldward.jobs;

import com.fieldward.account.AccountPrincipal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class JobMatchControllerTest {
    @Test void filtersUnsafeSourcesAndUsesAuthenticatedAccount() {
        var mapper=mock(JobMapper.class); var store=mock(JobMatchStore.class); var principal=mock(AccountPrincipal.class);
        when(principal.accountId()).thenReturn(17L);
        var good=new JobMapper.JobView(1,"ok","a","ACTIVE","A","SQL","https://example.com/a",null);
        var bad=new JobMapper.JobView(2,"ok","b","ACTIVE","B","SQL","https://evil.test/b",null);
        when(mapper.listActiveVersioned()).thenReturn(List.of(new JobMapper.VersionedJob(good,3L),new JobMapper.VersionedJob(bad,4L)));
        when(store.match(17,3)).thenReturn(new JobMatchStore.View("PENDING",3,0,null,new JobMatchService.Match(null,List.of(),List.of(),List.of())));
        JobSource source=new JobSource() {
            public String id(){return "ok";} public boolean authorized(){return true;}
            public java.util.Set<String> allowedHosts(){return java.util.Set.of("example.com");}
            public SourceBatch fetchChanges(String cursor){throw new UnsupportedOperationException();}
        };
        var controller=new JobMatchController(mapper,store,List.of(source));
        assertThat(controller.list(principal)).hasSize(1);
        verify(store).match(17,3); verifyNoMoreInteractions(store);
    }
}
