package com.autoresolve.mediabuying.integration.llm;

import com.autoresolve.mediabuying.model.dto.InsightDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Stub implementation of {@link LlmInsightProvider} for the NVIDIA NIM backend.
 * Activated when the Spring profile {@code nvidia-nim} is active.
 *
 * <h3>Planned Integration Details</h3>
 * <ul>
 *   <li><b>Endpoint:</b> {@code https://integrate.api.nvidia.com/v1/chat/completions}</li>
 *   <li><b>Model:</b> {@code meta/llama3-70b-instruct}</li>
 *   <li><b>Prompt format:</b> System prompt instructing the model to analyze
 *       sector-level client analytics and return JSON array of insight objects.</li>
 *   <li><b>Authentication:</b> Bearer token via {@code nvidia.nim.api-key} property.</li>
 * </ul>
 *
 * <p>This implementation is not yet wired and will throw
 * {@link UnsupportedOperationException} if invoked.
 */
@Component
@Profile("nvidia-nim")
public class NvidiaNimInsightProvider implements LlmInsightProvider {

    private static final Logger log = LoggerFactory.getLogger(NvidiaNimInsightProvider.class);

    @Override
    public List<InsightDTO> generateInsights(ClientAnalytics analytics) {
        log.warn("NVIDIA NIM insight provider invoked but is not yet configured");
        throw new UnsupportedOperationException(
                "NVIDIA NIM integration not yet configured. " +
                "Use the 'default' profile or implement the REST client in NvidiaNimInsightProvider.");
    }
}
