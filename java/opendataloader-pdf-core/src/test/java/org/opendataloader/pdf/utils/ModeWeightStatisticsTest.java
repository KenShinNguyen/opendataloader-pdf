package org.opendataloader.pdf.utils;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModeWeightStatisticsTest {

    @Test
    void getModeReturnsMostFrequentScoreWithinRange() {
        ModeWeightStatistics statistics = new ModeWeightStatistics(10.0, 32.0, 9.0, 14.0);
        statistics.addScore(10.0);
        statistics.addScore(12.0);
        statistics.addScore(12.0);
        statistics.addScore(12.0);
        statistics.addScore(14.0);
        statistics.sortByFrequency();

        double mode = statistics.getMode();

        assertThat(mode).isCloseTo(12.0, Offset.offset(0.001));
    }

    @Test
    void getModeReturnsNaNWhenNoScoresWithinRange() {
        ModeWeightStatistics statistics = new ModeWeightStatistics(10.0, 32.0, 9.0, 13.0);
        statistics.addScore(5.0);
        statistics.addScore(7.0);
        statistics.sortByFrequency();

        double mode = statistics.getMode();

        assertThat(mode).isCloseTo(0.0, Offset.offset(0.001));
    }

    @Test
    void getBoostGivesFractionalRankForScoresAboveMode() {
        ModeWeightStatistics statistics = new ModeWeightStatistics(10.0, 32.0, 9.0, 13.0);
        statistics.addScore(12.0);
        statistics.addScore(12.0);
        statistics.addScore(12.0);
        statistics.addScore(10.0);
        statistics.addScore(14.0);
        statistics.addScore(16.0);

        double boostForFourteen = statistics.getBoost(14.0);
        double boostForSixteen = statistics.getBoost(16.0);
        double boostForMode = statistics.getBoost(12.0);

        assertThat(boostForFourteen).isCloseTo(0.5, Offset.offset(0.001));
        assertThat(boostForSixteen).isCloseTo(1.0, Offset.offset(0.001));
        assertThat(boostForMode).isCloseTo(0.0, Offset.offset(0.001));
    }

    @Test
    void getDominantScoreAgreesWithGetModeForBodyTextInsideTheWindow() {
        ModeWeightStatistics statistics = new ModeWeightStatistics(10.0, 32.0, 10.0, 13.0);
        for (int i = 0; i < 100; i++) {
            statistics.addScore(12.0);
        }
        for (int i = 0; i < 5; i++) {
            statistics.addScore(18.0);
        }
        statistics.sortByFrequency();

        assertThat(statistics.getDominantScore()).isCloseTo(12.0, Offset.offset(0.001));
        assertThat(statistics.getBoost(18.0)).isCloseTo(1.0, Offset.offset(0.001));
        assertThat(statistics.getBoost(12.0)).isCloseTo(0.0, Offset.offset(0.001));
    }

    /**
     * A large-print book: body text at 15pt sits above the [10, 13] window and only the
     * footnotes fall inside it, so the windowed mode reports the footnote size on a
     * handful of samples against hundreds. Reading it as the body size gave every
     * paragraph the rarity boost meant for headings.
     */
    @Test
    void getDominantScoreIgnoresARareScoreInsideTheWindow() {
        ModeWeightStatistics statistics = new ModeWeightStatistics(10.0, 32.0, 10.0, 13.0);
        for (int i = 0; i < 791; i++) {
            statistics.addScore(15.007);
        }
        for (int i = 0; i < 31; i++) {
            statistics.addScore(21.257);
        }
        for (int i = 0; i < 19; i++) {
            statistics.addScore(11.255);
        }
        statistics.sortByFrequency();

        assertThat(statistics.getMode()).isCloseTo(11.255, Offset.offset(0.001));
        assertThat(statistics.getDominantScore()).isCloseTo(15.007, Offset.offset(0.001));
        assertThat(statistics.getBoost(15.007)).isCloseTo(0.0, Offset.offset(0.001));
        assertThat(statistics.getBoost(21.257)).isGreaterThan(0.0);
    }

    @Test
    void getDominantScoreFallsBackToTheModeWhenNothingIsInsideTheWindow() {
        ModeWeightStatistics statistics = new ModeWeightStatistics(10.0, 32.0, 10.0, 13.0);
        statistics.addScore(24.0);
        statistics.addScore(24.0);
        statistics.addScore(30.0);
        statistics.sortByFrequency();

        assertThat(statistics.getMode()).isCloseTo(0.0, Offset.offset(0.001));
        assertThat(statistics.getDominantScore()).isCloseTo(24.0, Offset.offset(0.001));
    }

    @Test
    void getDominantScoreIsZeroWithoutScores() {
        ModeWeightStatistics statistics = new ModeWeightStatistics(10.0, 32.0, 10.0, 13.0);

        assertThat(statistics.getDominantScore()).isCloseTo(0.0, Offset.offset(0.001));
    }

    @Test
    void getDominantScorePrefersTheWindowedScoreOnATie() {
        ModeWeightStatistics statistics = new ModeWeightStatistics(10.0, 32.0, 10.0, 13.0);
        for (int i = 0; i < 10; i++) {
            statistics.addScore(12.0);
            statistics.addScore(20.0);
        }
        statistics.sortByFrequency();

        assertThat(statistics.getDominantScore()).isCloseTo(12.0, Offset.offset(0.001));
    }
}
