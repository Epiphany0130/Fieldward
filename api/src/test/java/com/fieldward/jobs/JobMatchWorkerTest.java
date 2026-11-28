package com.fieldward.jobs;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class JobMatchWorkerTest {
    @Test void disabledWorkerNeverClaimsOrCallsModel() {
        var store = mock(JobMatchStore.class);
        var extractor = mock(JobRequirementExtractor.class);
        new JobMatchWorker(store, extractor, List.of(), false).process();
        verifyNoInteractions(store, extractor);
    }
}
