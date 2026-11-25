package com.fieldward.mentor;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

final class MentorSearch {
    private static final Pattern TERMS = Pattern.compile("[A-Za-z0-9]+|[㐀-鿿]+");
    private static final Set<String> COMMON = Set.of("the", "and", "you", "for", "what", "your",
            "可以", "请问", "关于", "一下", "什么", "哪个", "我的", "你能", "能不", "不能", "我想", "告诉",
            "诉我", "是不是", "是否", "记得", "还记", "之前", "以前", "昨天", "刚才", "提到", "说过", "简历", "版本");

    private MentorSearch() {}

    static String query(String text) {
        return String.join(" | ", keywords(text).stream().map(word -> word.charAt(0) >= '㐀'
                ? "zz" + HexFormat.of().formatHex(word.getBytes(StandardCharsets.UTF_8)) : word).toList());
    }

    private static Set<String> keywords(String text) {
        var terms = new LinkedHashSet<String>();
        var matches = TERMS.matcher(text.toLowerCase(Locale.ROOT));
        while (terms.size() < 48 && matches.find()) {
            String token = matches.group();
            if (token.charAt(0) >= '㐀') {
                for (int i = 0; i + 1 < token.length() && terms.size() < 48; i++) {
                    String pair = token.substring(i, i + 2);
                    if (!COMMON.contains(pair)) terms.add(pair);
                }
            } else if (token.length() >= 2 && token.length() <= 48 && !COMMON.contains(token)) {
                terms.add(token);
            }
        }
        return terms;
    }

    static String excerpt(String text, String question, int limit) {
        if (text.length() <= limit) return text;
        var words = keywords(question);
        String normalized = text.toLowerCase(Locale.ROOT);
        int bestStart = 0;
        int bestScore = -1;
        for (int start = 0; start < text.length(); start += Math.max(128, limit / 2)) {
            int score = 0;
            for (String word : words) {
                int hit = normalized.indexOf(word, start);
                if (hit >= 0 && hit + word.length() <= start + limit) score += word.length();
            }
            if (score > bestScore) {
                bestStart = start;
                bestScore = score;
            }
        }
        if (Character.isLowSurrogate(text.charAt(bestStart))) bestStart--;
        return (bestStart == 0 ? "" : "[相关片段]\n") + MentorResumes.clip(text.substring(bestStart), limit);
    }
}
