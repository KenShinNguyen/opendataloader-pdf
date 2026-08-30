package org.opendataloader.pdf.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ModeWeightStatistics {
    private final double scoreMax;
    private final double scoreMin;
    private final double modeMin;
    private final double modeMax;
    private final Map<Double, Long> countMap = new HashMap<>();
    private List<Map.Entry<Double, Long>> sorted = new ArrayList<>();
    private List<Double> higherScores = new ArrayList<>();
    private boolean isInitHigherScores = false;

    public ModeWeightStatistics(double scoreMin, double scoreMax, double modeMin, double modeMax) {
        this.scoreMin = scoreMin;
        this.scoreMax = scoreMax;
        this.modeMin = modeMin;
        this.modeMax = modeMax;
    }

    public void addScore(double score) {
        countMap.merge(score, 1L, Long::sum);
    }

    /**
     * @return the number of scores recorded, counting repeats
     */
    public long getSampleCount() {
        long total = 0;
        for (long count : countMap.values()) {
            total += count;
        }
        return total;
    }

    public double getBoost(double score) {
        initHigherScores();
        int n = higherScores.size();
        if (n == 0) {
            return 0.0;
        }
        for (int i = 0; i < n; i++) {
            if (Double.compare(higherScores.get(i), score) == 0) {
                return (double) (i + 1) / n;
            }
        }
        return 0.0;
    }

    public void sortByFrequency() {
        sorted = new ArrayList<>(countMap.entrySet());
        sorted.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
    }

    public double getMode() {
        for (Map.Entry<Double, Long> entry : sorted) {
            double value = entry.getKey();
            if (value >= modeMin && value <= modeMax) {
                return value;
            }
        }
        return 0.0;
    }

    /**
     * The score that represents ordinary body text: the mode of the distribution.
     *
     * <p>{@link #getMode()} instead returns the most frequent score inside the
     * [modeMin, modeMax] window, an absolute range tuned for 10-13pt body text. That
     * window misreads two kinds of document. One typeset outside it - a large-print book
     * at 15pt, a slide deck at 24pt - has no score in the window at all, so getMode()
     * returns 0.0 and every score counts as "larger than the body", handing ordinary
     * paragraphs the same rarity boost as real headings. Worse, a document whose body
     * sits above the window but whose footnotes fall inside it gets its footnote size
     * back as the body size, on a handful of samples against thousands.
     *
     * <p>The most frequent size in a run of text is the body size, so that is what this
     * returns. The window survives only as a tie-break: when two scores are equally
     * common, the one inside it wins, which also makes the result deterministic. For the
     * documents the window was tuned for the answer is unchanged, because there the body
     * size is both the overall mode and the windowed one.
     *
     * @return the dominant score, or 0.0 when no score has been recorded
     */
    public double getDominantScore() {
        if (sorted.isEmpty()) {
            sortByFrequency();
        }
        double dominant = 0.0;
        long dominantCount = 0L;
        boolean dominantInWindow = false;
        for (Map.Entry<Double, Long> entry : sorted) {
            double value = entry.getKey();
            long count = entry.getValue();
            boolean inWindow = value >= modeMin && value <= modeMax;
            if (count > dominantCount || (count == dominantCount && inWindow && !dominantInWindow)) {
                dominant = value;
                dominantCount = count;
                dominantInWindow = inWindow;
            }
        }
        return dominant;
    }

    private void initHigherScores() {
        if (isInitHigherScores) {
            return;
        }

        double mode = getDominantScore();

        higherScores = sorted.stream()
            .map(Map.Entry::getKey)
            .filter(s -> s > mode && s >= scoreMin && s <= scoreMax)
            .sorted()
            .collect(Collectors.toList());

        isInitHigherScores = true;
    }
}
