package com.fieldward.mentor;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
class MentorResumes {
    private static final Pattern VERSION_NUMBER = Pattern.compile(
            "第\\s*([0-9]{1,18}|[一二两三四五六七八九十百零]{1,7})\\s*(?:版|个版本)"
            + "|版本\\s*[vV]?\\s*([0-9]{1,18})|(?<![a-zA-Z0-9])[vV]([0-9]{1,18})(?![a-zA-Z0-9])");
    private static final Pattern EMAIL = Pattern.compile("(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(?:\\+?86[ -]?)?1[3-9]\\d(?:[ -]?\\d){8}(?!\\d)");
    private final MentorResumeMapper resumes;

    MentorResumes(MentorResumeMapper resumes) {
        this.resumes = resumes;
    }

    List<MentorResumeMapper.Item> catalog(long conversation, Long before, int limit) {
        return resumes.catalog(conversation, before, limit);
    }

    List<MentorResumeMapper.Document> select(long conversation, List<MentorResumeMapper.Item> catalog,
                                           String query, String search) {
        var versions = new LinkedHashSet<Long>();
        var matches = VERSION_NUMBER.matcher(query);
        while (versions.size() < 5 && matches.find()) {
            for (int index = 1; index <= matches.groupCount(); index++) {
                if (matches.group(index) != null) versions.add(parseVersion(matches.group(index)));
            }
        }
        if (query.contains("最早") || query.contains("最初")) versions.add(1L);
        catalog.stream().filter(item -> item.fileName() != null && query.contains(item.fileName()))
                .limit(5).forEach(item -> versions.add(item.version()));
        if (!catalog.isEmpty()) versions.add(catalog.getFirst().version());
        if (catalog.size() > 1 && (query.contains("上一版") || query.contains("对比") || query.contains("差异"))) {
            versions.add(catalog.get(1).version());
        }
        var selected = new LinkedHashMap<Long, MentorResumeMapper.Document>();
        for (long version : versions) {
            var document = resumes.find(conversation, version);
            if (document != null) selected.put(version, document);
        }
        if (!search.isBlank()) {
            for (var document : resumes.search(conversation, search)) {
                selected.putIfAbsent(document.version(), document);
            }
        }
        return List.copyOf(selected.values());
    }

    private long parseVersion(String text) {
        if (Character.isDigit(text.charAt(0))) return Long.parseLong(text);
        int total = 0;
        int digit = 0;
        for (char value : text.toCharArray()) {
            if (value == '十' || value == '百') {
                total += Math.max(1, digit) * (value == '十' ? 10 : 100);
                digit = 0;
            } else {
                digit = value == '两' ? 2 : "零一二三四五六七八九".indexOf(value);
            }
        }
        return total + digit;
    }

    static String redactContacts(String text) {
        return PHONE.matcher(EMAIL.matcher(text).replaceAll("[邮箱已隐藏]")).replaceAll("[手机号已隐藏]");
    }

    static String clip(String text, int limit) {
        if (text.length() <= limit) return text;
        int end = Character.isHighSurrogate(text.charAt(limit - 1)) ? limit - 1 : limit;
        return text.substring(0, end) + "\n[其余内容保存在原始来源中]";
    }
}
