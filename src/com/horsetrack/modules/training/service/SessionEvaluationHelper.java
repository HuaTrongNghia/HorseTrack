package com.horsetrack.equinecare.modules.training.service;

import lombok.Builder;
import lombok.Getter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SessionEvaluationHelper {

    private static final Pattern EVAL_PATTERN = Pattern.compile("\\[EVAL:score=(\\d+)(?:;by=([^;]*))?(?:;at=([^;]*))?(?:;comment=([^\\]]*))?\\]");

    @Getter
    @Builder
    public static class EvaluationData {
        private Integer score;
        private String by;
        private String at;
        private String comment;
    }

    public static String attachEvaluation(String existingNotes, Integer score, String comment, String by, String at) {
        String cleanNotes = removeEvaluation(existingNotes);
        String safeComment = comment != null ? comment.replace(";", ",").replace("]", ")") : "";
        String safeBy = by != null ? by.replace(";", ",").replace("]", ")") : "";
        String safeAt = at != null ? at.replace(";", ",").replace("]", ")") : "";
        String evalTag = String.format("[EVAL:score=%d;by=%s;at=%s;comment=%s]", score, safeBy, safeAt, safeComment);

        if (cleanNotes == null || cleanNotes.trim().isEmpty()) {
            return evalTag;
        }
        return cleanNotes.trim() + " " + evalTag;
    }

    public static EvaluationData extractEvaluation(String notes) {
        if (notes == null || notes.isEmpty()) {
            return null;
        }
        Matcher matcher = EVAL_PATTERN.matcher(notes);
        if (matcher.find()) {
            Integer score = null;
            try {
                score = Integer.parseInt(matcher.group(1));
            } catch (Exception ignored) {}

            return EvaluationData.builder()
                    .score(score)
                    .by(matcher.group(2))
                    .at(matcher.group(3))
                    .comment(matcher.group(4))
                    .build();
        }
        return null;
    }

    public static String removeEvaluation(String notes) {
        if (notes == null) return null;
        return notes.replaceAll("\\[EVAL:[^\\]]+\\]", "").trim();
    }
}
