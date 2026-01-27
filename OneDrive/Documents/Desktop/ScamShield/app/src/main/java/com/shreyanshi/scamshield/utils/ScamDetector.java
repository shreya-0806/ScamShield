package com.shreyanshi.scamshield.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ScamDetector {

    private static final List<String> KEYWORDS =
            Arrays.asList("otp", "bank", "urgent", "account", "blocked", "kyc");

    // New rule map for user-friendly messages
    private static final Map<String, String> RULES = new HashMap<>();
    static {
        RULES.put("otp", "Caller asked for an OTP (one-time password). Never share OTP with anyone.");
        RULES.put("one-time password", "Caller asked for an OTP (one-time password). Never share OTP with anyone.");
        RULES.put("password", "Caller requested your password/PIN. Do not disclose passwords.");
        RULES.put("pin", "Caller requested your password/PIN. Do not disclose passwords.");
        RULES.put("account number", "Caller asked for your bank account details. Banks do not ask for these by phone.");
        RULES.put("bank account", "Caller asked for your bank account details. Banks do not ask for these by phone.");
        RULES.put("social security", "Caller asked for Social Security / national ID. This is suspicious.");
        RULES.put("ssn", "Caller asked for Social Security / national ID. This is suspicious.");
        RULES.put("prize", "Prize/lottery scam language detected. Do not pay or share personal data.");
        RULES.put("winner", "Prize/lottery scam language detected. Do not pay or share personal data.");
        RULES.put("warrant", "Threatening legal consequences detected. Verify with official channels.");
        RULES.put("police", "Threatening legal consequences detected. Verify with official channels.");
        RULES.put("tax", "Tax-related scam language detected. Authorities do not demand immediate payment by phone.");
        RULES.put("remote access", "Caller asked to install remote access software. Do not allow remote access.");
        RULES.put("teamviewer", "Caller asked to install TeamViewer/AnyDesk. Do not allow remote access.");
        RULES.put("western union", "Request to send money via wire transfer detected. This is a common scam.");
        RULES.put("send money", "Request to send money via wire transfer detected. This is a common scam.");
    }

    public static class ScamResult {
        public boolean isScam;
        public List<String> matchedKeywords;

        ScamResult(boolean s, List<String> k) {
            isScam = s;
            matchedKeywords = k;
        }
    }

    public static ScamResult detect(String text) {
        List<String> found = new ArrayList<>();
        if (text == null) return new ScamResult(false, found);
        String lower = text.toLowerCase(Locale.ROOT);

        for (String k : KEYWORDS) {
            if (lower.contains(k)) found.add(k);
        }
        return new ScamResult(found.size() >= 2, found);
    }

    // New: return list of matched keywords (for backward compatibility with service code)
    public static List<String> detectKeywords(String transcript) {
        List<String> matches = new ArrayList<>();
        if (transcript == null) return matches;
        String t = transcript.toLowerCase(Locale.ROOT);
        for (String k : RULES.keySet()) {
            if (t.contains(k)) {
                matches.add(k);
            }
        }
        return matches;
    }

    // New: build an aggregated alert message from matched rules
    public static String buildAlertMessage(List<String> matches, String transcript) {
        if (matches == null || matches.isEmpty()) return "Potential scam detected";
        StringBuilder sb = new StringBuilder();
        for (String k : matches) {
            String msg = RULES.get(k);
            if (msg != null) {
                sb.append(msg).append(" ");
            }
        }
        String out = sb.toString().trim();
        return out.isEmpty() ? "Potential scam detected" : out;
    }
}
