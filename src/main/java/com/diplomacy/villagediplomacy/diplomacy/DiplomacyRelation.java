package com.diplomacy.villagediplomacy.diplomacy;

import java.util.Locale;

public enum DiplomacyRelation {
    ALLY(100, 50),
    FRIENDLY(49, 20),
    NEUTRAL(19, -19),
    TENSION(-20, -49),
    HOSTILE(-50, -100);

    private final int maxScore;
    private final int minScore;

    DiplomacyRelation(int maxScore, int minScore) {
        this.maxScore = maxScore;
        this.minScore = minScore;
    }

    public int getMaxScore() {
        return maxScore;
    }

    public int getMinScore() {
        return minScore;
    }

    public boolean canTrade() {
        return this == ALLY || this == FRIENDLY || this == NEUTRAL;
    }

    public boolean isAggressive() {
        return this == HOSTILE;
    }

    public String getTranslationKey() {
        return "relation.villagediplomacy." + name().toLowerCase(Locale.ROOT);
    }

    public static DiplomacyRelation fromScore(int score) {
        if (score >= 50) return ALLY;
        if (score >= 20) return FRIENDLY;
        if (score >= -19) return NEUTRAL;
        if (score >= -49) return TENSION;
        return HOSTILE;
    }
}
