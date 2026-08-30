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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class LineJoinRepairTest {

    /**
     * References from one book, each broken across a line in the source PDF. A fifth of
     * its URLs arrived with a space in them.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "https://www. chainalysis.com/ko/blog/2023-report.|https://www.chainalysis.com/ko/blog/2023-report.",
        "https://www.youtube.com/watch? v=WKibLhAGP1A|https://www.youtube.com/watch?v=WKibLhAGP1A",
        "https:// www.cnbc.com/2018/05/07/munger.html|https://www.cnbc.com/2018/05/07/munger.html",
        "https://finbold. com/canadian-pension-fund|https://finbold.com/canadian-pension-fund"
    })
    void closesAUrlSplitAcrossALine(String joined, String expected) {
        assertThat(LineJoinRepair.repairSplitUrls(joined)).isEqualTo(expected);
    }

    /**
     * A character that cannot begin a sentence but is ordinary inside a URL settles the
     * continuation on its own, whatever the URL broke on.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "https://example.com /path/to/page|https://example.com/path/to/page",
        "https://example.com/a ?q=1|https://example.com/a?q=1",
        "https://example.com/a?q=1 &r=2|https://example.com/a?q=1&r=2"
    })
    void closesAUrlWhoseNextLineCannotStartASentence(String joined, String expected) {
        assertThat(LineJoinRepair.repairSplitUrls(joined)).isEqualTo(expected);
    }

    /**
     * Gluing the following sentence onto a URL is the worse mistake, so a capital letter
     * after the break keeps its space.
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "Read more at https://example.com. The next sentence follows.",
        "See https://example.com/ Then we continue.",
        "Visit www.example.com. Another point entirely."
    })
    void leavesASentenceThatEndsOnAUrl(String text) {
        assertThat(LineJoinRepair.repairSplitUrls(text)).isEqualTo(text);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "The cost fell from $76.67 in 1977 to $0.25 in 2023.",
        "i.e. we should think for ourselves.",
        "Section 3. see the appendix",
        "https://example.com",
        ""
    })
    void leavesTextWithNoSplitUrl(String text) {
        assertThat(LineJoinRepair.repairSplitUrls(text)).isEqualTo(text);
    }

    /**
     * '.' and '?' end sentences as well as breaking URLs, so for those the word after
     * the break has to look like part of a URL too. A sentence resuming in lower case
     * used to be glued onto the URL in front of it.
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "Read https://example.com. see the appendix",
        "Read https://example.com. the appendix follows",
        "Try https://example.com/a? or maybe not",
        "Visit www.example.com. more on this later"
    })
    void leavesASentenceResumingInLowerCaseAfterAUrl(String text) {
        assertThat(LineJoinRepair.repairSplitUrls(text)).isEqualTo(text);
    }

    @Test
    void passesNullThrough() {
        assertThat(LineJoinRepair.repairSplitUrls(null)).isNull();
    }
}
