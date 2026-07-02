package com.autoresolve.mediabuying.scheduler;

import com.autoresolve.mediabuying.service.SourceAttributionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SourceVerificationSchedulerTest {

    @Mock
    private SourceAttributionService sourceAttributionService;

    private SourceVerificationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new SourceVerificationScheduler(sourceAttributionService);
    }

    @Test
    void testVerifyStaleSourcesDelegatesToService() {
        scheduler.verifyStaleSources();

        verify(sourceAttributionService, times(1)).verifySourceUrls();
    }
}
