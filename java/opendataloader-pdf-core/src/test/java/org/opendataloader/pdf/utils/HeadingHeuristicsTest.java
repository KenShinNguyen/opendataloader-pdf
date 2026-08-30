/*
 * Copyright 2025-2026 Hancom Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opendataloader.pdf.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class HeadingHeuristicsTest {

    /**
     * Text that reached the Markdown output as a heading from an untagged book whose
     * body font is the same size as these runs, so nothing but the wording sets them
     * apart from the paragraphs around them.
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "Am I absolutely sure that John didn't say \"Hi\" because he is angry with me?",
        "Look at what you've watched on YouTube or other algorithm-dependent platforms.",
        "What trends am I basing this prediction on?",
        "The bottom line is, the Matrix narrative is often used by gurus and conspiracy theorists "
            + "who seek to push an agenda and manipulate us. It allows them to cast doubt on our "
            + "vision of the world and instill fear."
    })
    void vetoesRunningProse(String text) {
        assertThat(HeadingHeuristics.looksLikeSentence(text)).isTrue();
    }

    @Test
    void vetoesTheTailOfAParagraphBrokenAcrossAPage() {
        assertThat(HeadingHeuristics.looksLikeSentence("personally could be.")).isTrue();
        assertThat(HeadingHeuristics.looksLikeSentence(
            "and hidden agendas, finding the \"truth\" becomes an almost impossible task.")).isTrue();
    }

    @Test
    void vetoesASentenceClosedByAQuotationMark() {
        assertThat(HeadingHeuristics.looksLikeSentence(
            "According to the European Society of Cardiology, “There is no threshold below "
                + "which LDL is not atherogenic. Lower is better.”")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "CONTENTS",
        "PART I",
        "DESIRE TO BELONG",
        "OTHER BOOKS BY THE AUTHORS",
        "C. Observations/personal experiences",
        "Introduction",
        "3.1 Methods",
        "Why bother"
    })
    void keepsHeadings(String text) {
        assertThat(HeadingHeuristics.looksLikeSentence(text)).isFalse();
    }

    /**
     * Terminal punctuation is only a sentence ending once there are words in front of
     * it, so a numeric or near-numeric label survives.
     */
    @ParameterizedTest
    @ValueSource(strings = {"1", "1.", "Step 2.", "IV."})
    void keepsShortLabelsEndingInAPeriod(String text) {
        assertThat(HeadingHeuristics.looksLikeSentence(text)).isFalse();
    }

    @Test
    void vetoesAnythingLongerThanAHeading() {
        String longLabel = "WORD ".repeat(HeadingHeuristics.MAX_HEADING_LENGTH / 5 + 1);

        assertThat(longLabel.trim().length()).isGreaterThan(HeadingHeuristics.MAX_HEADING_LENGTH);
        assertThat(HeadingHeuristics.looksLikeSentence(longLabel)).isTrue();
    }

    @Test
    void ignoresEmptyAndNullText() {
        assertThat(HeadingHeuristics.looksLikeSentence(null)).isFalse();
        assertThat(HeadingHeuristics.looksLikeSentence("")).isFalse();
        assertThat(HeadingHeuristics.looksLikeSentence("   ")).isFalse();
    }
}
