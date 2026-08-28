package com.cukkoo.soundculling;

import com.cukkoo.soundculling.config.SoundCullingConfig;

import java.util.List;
import java.util.Locale;

/** Lightweight wildcard rule matcher used by the 2.0 priority engine. */
public final class SoundRuleEngine {

    private SoundRuleEngine() {
    }

    public static RuleResult resolve(String soundId, SoundCullingConfig config) {
        if (soundId == null || config == null) {
            return RuleResult.NORMAL;
        }

        if (config.whitelistedSounds != null && config.whitelistedSounds.contains(soundId)) {
            return new RuleResult("NEVER_CULL", 4.0f, true, false);
        }

        RuleResult result = RuleResult.NORMAL;
        List<SoundCullingConfig.SoundRule> rules = config.rules;
        if (rules == null || rules.isEmpty()) {
            return result;
        }

        for (SoundCullingConfig.SoundRule rule : rules) {
            if (rule == null || rule.pattern == null || rule.pattern.isBlank()) {
                continue;
            }
            if (matches(rule.pattern, soundId)) {
                result = fromAction(rule.action);
            }
        }
        return result;
    }

    public static RuleResult fromAction(String requestedAction) {
        String action = SoundCullingConfig.normalizeAction(requestedAction);
        return switch (action) {
            case "NEVER_CULL" -> new RuleResult(action, 4.0f, true, false);
            case "CRITICAL" -> new RuleResult(action, 2.2f, false, false);
            case "HIGH" -> new RuleResult(action, 1.55f, false, false);
            case "AGGRESSIVE" -> new RuleResult(action, 0.55f, false, false);
            case "ALWAYS_CULL" -> new RuleResult(action, 0.0f, false, true);
            default -> RuleResult.NORMAL;
        };
    }

    /**
     * Small allocation-free glob matcher supporting '*'. Matching is case
     * insensitive because resource namespaces and paths are conventionally lower-case.
     */
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

    public record RuleResult(String action, float priorityMultiplier, boolean neverCull, boolean alwaysCull) {
        public static final RuleResult NORMAL = new RuleResult("NORMAL", 1.0f, false, false);
    }
}
