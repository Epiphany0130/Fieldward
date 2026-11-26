package com.fieldward.realtime;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RealtimeDisabledTest {
    @Test void finishIsDisabledOverHttpAndDoesNotMutate() throws Exception {
        var sessions = mock(RealtimeSessionService.class);
        var authorization = mock(RealtimeAuthorization.class);
        var mvc = MockMvcBuilders.standaloneSetup(new RealtimeController(sessions, authorization))
                .setCustomArgumentResolvers(new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver()).build();
        mvc.perform(post("/api/realtime/00000000-0000-0000-0000-000000000001/finish")
                .contentType(MediaType.APPLICATION_JSON).content("{\"requestId\":\"finish\"}"))
                .andExpect(status().isServiceUnavailable());
        verifyNoInteractions(sessions);
    }
    @Test void internalEndpointsAreDisabledOverHttp() throws Exception {
        var sessions = mock(RealtimeSessionService.class);
        var authorization = mock(RealtimeAuthorization.class);
        var mvc = MockMvcBuilders.standaloneSetup(new RealtimeInternalController(sessions, authorization)).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/internal/realtime/00000000-0000-0000-0000-000000000001/context?accountId=1"))
                .andExpect(status().isServiceUnavailable());
        mvc.perform(post("/api/internal/realtime/segment").contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\":1,\"sessionId\":\"00000000-0000-0000-0000-000000000001\",\"eventId\":\"event\",\"segment\":0}"))
                .andExpect(status().isServiceUnavailable());
        verifyNoInteractions(sessions);
    }
    @Test void cleanupIsNotScheduled() throws Exception {
        assertThat(RealtimeSessionService.class.getMethod("expire").getAnnotation(Scheduled.class)).isNull();
    }
}
