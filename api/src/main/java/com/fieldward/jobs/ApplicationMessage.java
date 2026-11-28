package com.fieldward.jobs;

import java.util.List;
import java.util.Map;

final class ApplicationMessage {
    private static final Map<String,String> PREFIXES = Map.of("我已投递", "APPLIED", "我已经投递", "APPLIED", "我进入面试", "INTERVIEW", "我拿到录用", "OFFER", "我被拒绝", "REJECTED", "我已撤回", "WITHDRAWN");
    private static final Map<String,String> SUFFIXES = Map.of("已经进入面试", "INTERVIEW", "已进入面试", "INTERVIEW", "已经投递", "APPLIED", "已投递", "APPLIED");
    static Intent parse(String message, List<JobMapper.JobView> jobs) {
        String clean = message.strip();
        for (var prefix : PREFIXES.entrySet()) {
            if (!clean.startsWith(prefix.getKey())) continue;
            String title = clean.substring(prefix.getKey().length()).strip();
            var matches = jobs.stream().filter(job -> job.title().equals(title)).toList();
            if (matches.size() == 1) return new Intent(matches.getFirst().id(),prefix.getValue());
        }
        for (var suffix : SUFFIXES.entrySet()) {
            if (!clean.endsWith(suffix.getKey())) continue;
            String title = clean.substring(0, clean.length() - suffix.getKey().length()).strip();
            var matches = jobs.stream().filter(job -> job.title().equals(title)).toList();
            if (matches.size() == 1) return new Intent(matches.getFirst().id(),suffix.getValue());
        }
        return null;
    }
    static boolean isProgressStatement(String message) { return PREFIXES.keySet().stream().anyMatch(message.strip()::startsWith) || SUFFIXES.keySet().stream().anyMatch(message.strip()::endsWith); }
    record Intent(long jobId, String status) {}
}
