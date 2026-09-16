package com.cukkoo.soundculling;

import com.cukkoo.soundculling.config.SoundCullingConfig;

import java.util.List;
import java.util.Locale;

public final class SoundRuleEngine {
    private SoundRuleEngine() {}

    public static RuleResult resolve(String soundId, SoundCullingConfig config) {
        if (soundId == null || config == null) {
            return RuleResult.NORMAL;
        }

        if (config.whitelistedSounds != null && config.whitelistedSounds.contains(soundId)) {
            return RuleResult.NEVER_CULL;
        }

        List<SoundCullingConfig.SoundRule> rules = config.rules;
        if (rules == null || rules.isEmpty()) {
            return RuleResult.NORMAL;
        }

        for (int index = rules.size() - 1; index >= 0; index--) {
            SoundCullingConfig.SoundRule rule = rules.get(index);
            if (rule == null || rule.pattern == null || rule.pattern.isBlank()) {
                continue;
            }
            if (matches(rule.pattern, soundId)) {
                return fromAction(rule.action);
            }
        }
        return RuleResult.NORMAL;
    }

    public static RuleResult fromAction(String requestedAction) {
        String action = SoundCullingConfig.normalizeAction(requestedAction);
        return switch (action) {
            case "NEVER_CULL" -> RuleResult.NEVER_CULL;
            case "CRITICAL" -> RuleResult.CRITICAL;
            case "HIGH" -> RuleResult.HIGH;
            case "AGGRESSIVE" -> RuleResult.AGGRESSIVE;
            case "ALWAYS_CULL" -> RuleResult.ALWAYS_CULL;
            default -> RuleResult.NORMAL;
        };
    }

    // Match '*' in sound IDs without regex.
    public static boolean matches(String pattern, String value) {
        if (pattern == null || value == null) {
            return false;
        }

        String p = pattern.toLowerCase(Locale.ROOT);
        String v = value.toLowerCase(Locale.ROOT);

        int pIndex = 0;
        int vIndex = 0;
        int star = -1;
        int checkpoint = -1;

        while (vIndex < v.length()) {
            if (pIndex < p.length() && p.charAt(pIndex) == v.charAt(vIndex)) {
                pIndex++;
                vIndex++;
            } else if (pIndex < p.length() && p.charAt(pIndex) == '*') {
                star = pIndex++;
                checkpoint = vIndex;
            } else if (star != -1) {
                pIndex = star + 1;
                vIndex = ++checkpoint;
            } else {
                return false;
            }
        }

        while (pIndex < p.length() && p.charAt(pIndex) == '*') {
            pIndex++;
        }
        return pIndex == p.length();
    }

    public static String namespacePattern(String soundId) {
        if (soundId == null || soundId.isBlank()) {
            return "*";
        }
        int separator = soundId.indexOf(':');
        if (separator <= 0) {
            return soundId + ":*";
        }
        return soundId.substring(0, separator) + ":*";
    }

    public record RuleResult(
            String action, float priorityMultiplier, boolean neverCull, boolean alwaysCull) {
        public static final RuleResult NEVER_CULL = new RuleResult("NEVER_CULL", 4.0f, true, false);
        public static final RuleResult CRITICAL = new RuleResult("CRITICAL", 2.2f, false, false);
        public static final RuleResult HIGH = new RuleResult("HIGH", 1.55f, false, false);
        public static final RuleResult AGGRESSIVE =
                new RuleResult("AGGRESSIVE", 0.55f, false, false);
        public static final RuleResult ALWAYS_CULL =
                new RuleResult("ALWAYS_CULL", 0.0f, false, true);
        public static final RuleResult NORMAL = new RuleResult("NORMAL", 1.0f, false, false);
    }
}
