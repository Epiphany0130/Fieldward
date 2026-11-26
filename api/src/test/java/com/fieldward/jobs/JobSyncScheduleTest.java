package com.fieldward.jobs;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class JobSyncScheduleTest {
    @Test void oneFailureDoesNotPreventOtherSourcesFromSyncing() {
        var service = mock(JobSyncService.class);
        JobSource first = mock(JobSource.class), second = mock(JobSource.class);
        when(first.authorized()).thenReturn(true); when(second.authorized()).thenReturn(true);
        when(first.id()).thenReturn("first"); when(second.id()).thenReturn("second");
        when(service.sync("first")).thenThrow(new IllegalStateException("unavailable"));
        new JobSyncSchedule(service, List.of(first, second), mock(JobSourceRegistry.class)).syncAll();
        verify(service).sync("first"); verify(service).sync("second");
    }
}
