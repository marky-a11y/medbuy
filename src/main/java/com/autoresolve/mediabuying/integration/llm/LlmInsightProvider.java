package com.autoresolve.mediabuying.integration.llm;

import com.autoresolve.mediabuying.model.dto.InsightDTO;

import java.util.List;
import java.util.Map;

/**
 * Strategy interface for generating AI-powered insights from client analytics data.
 * Implementations may use mock rules, a local model, or a remote LLM (e.g., NVIDIA NIM).
 */
public interface LlmInsightProvider {

    /**
     * Generates a list of actionable insights based on client analytics per sector.
     *
     * @param analytics aggregated analytics keyed by sector name
     * @return list of insights, sorted by descending confidence score
     */
    List<InsightDTO> generateInsights(ClientAnalytics analytics);

    /**
     * Aggregated analytics data for all sectors.
     */
    class ClientAnalytics {
        private Map<String, SectorStats> sectors;

        public ClientAnalytics() {
        }

        public ClientAnalytics(Map<String, SectorStats> sectors) {
            this.sectors = sectors;
        }

        public Map<String, SectorStats> getSectors() {
            return sectors;
        }

        public void setSectors(Map<String, SectorStats> sectors) {
            this.sectors = sectors;
        }
    }

    /**
     * Statistical summary for a single commerce sector.
     */
    class SectorStats {
        private int clientCount;
        private double avgOutlookScore;
        private int expiringContractCount;
        private Map<String, Integer> contractTypeCounts;

        public SectorStats() {
        }

        public SectorStats(int clientCount, double avgOutlookScore, int expiringContractCount,
                           Map<String, Integer> contractTypeCounts) {
            this.clientCount = clientCount;
            this.avgOutlookScore = avgOutlookScore;
            this.expiringContractCount = expiringContractCount;
            this.contractTypeCounts = contractTypeCounts;
        }

        public int getClientCount() {
            return clientCount;
        }

        public void setClientCount(int clientCount) {
            this.clientCount = clientCount;
        }

        public double getAvgOutlookScore() {
            return avgOutlookScore;
        }

        public void setAvgOutlookScore(double avgOutlookScore) {
            this.avgOutlookScore = avgOutlookScore;
        }

        public int getExpiringContractCount() {
            return expiringContractCount;
        }

        public void setExpiringContractCount(int expiringContractCount) {
            this.expiringContractCount = expiringContractCount;
        }

        public Map<String, Integer> getContractTypeCounts() {
            return contractTypeCounts;
        }

        public void setContractTypeCounts(Map<String, Integer> contractTypeCounts) {
            this.contractTypeCounts = contractTypeCounts;
        }
    }
}
