package com.example.gsb.licensecheck.rules;

import com.example.gsb.licensecheck.model.Verdict;

public final class RuleEvaluation {
    private final Verdict verdict;
    private final String matchedRule;

    public RuleEvaluation(Verdict verdict, String matchedRule) {
        this.verdict = verdict;
        this.matchedRule = matchedRule;
    }

    public Verdict getVerdict() {
        return verdict;
    }

    public String getMatchedRule() {
        return matchedRule;
    }
}
