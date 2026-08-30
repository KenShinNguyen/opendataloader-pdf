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

/**
 * Repairs a URL that a line break split in the source PDF.
 *
 * <p>Lines are joined with a space, which is right for prose and wrong for a URL: a
 * reference broken as "https://finbold.&#47;&#47;newline&#47;&#47;com/canadian-pension-fund" arrives as
 * "https://finbold. com/canadian-pension-fund", which no longer resolves and no longer
 * matches the link it came from. On a book of ordinary length this reaches one URL in
 * five.
 *
 * <p>A separator is only dropped where the text on both sides says the URL continues,
 * because the alternative mistake - gluing the next sentence onto a URL that really did
 * end the line - is worse than leaving a space in. Either the next line opens with a
 * character that cannot begin a sentence but is ordinary inside a URL ('/', '?', '&amp;',
 * '=', '#'), or the URL breaks on a character it cannot end on and the next line
 * continues in lower case or a digit. A sentence that ends on a URL is followed by a
 * capital letter, so it keeps its space.
 */
public final class LineJoinRepair {

    /**
     * Characters a URL can break on mid-address. A URL ending in one of these has not
     * finished, though '.' is also an ordinary full stop, which is why the character
     * after the break has to agree.
     */
    private static final String URL_BREAK_CHARS = "./?&=-_~+#%:";

    /**
     * Characters that cannot start a sentence but are ordinary inside a URL, so they
     * settle a continuation on their own.
     */
    private static final String URL_ONLY_STARTS = "/?&=#";

    private static final String SCHEME_SEPARATOR = "://";
    private static final String HOST_PREFIX = "www.";

    private LineJoinRepair() {
    }

    /**
     * @param text text whose lines have already been joined
     * @return the text with separators inside a split URL removed
     */
    public static String repairSplitUrls(String text) {
        if (text == null || text.indexOf(' ') < 0) {
            return text;
        }
        StringBuilder repaired = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == ' ' && index + 1 < text.length()
                && continuesUrl(repaired, text.charAt(index + 1))) {
                continue;
            }
            repaired.append(character);
        }
        return repaired.toString();
    }

    private static boolean continuesUrl(CharSequence before, char next) {
        String token = lastToken(before);
        if (token.indexOf(SCHEME_SEPARATOR) < 0 && !startsHost(token)) {
            return false;
        }
        if (URL_ONLY_STARTS.indexOf(next) >= 0) {
            return true;
        }
        char last = token.charAt(token.length() - 1);
        return URL_BREAK_CHARS.indexOf(last) >= 0 && (Character.isLowerCase(next) || Character.isDigit(next));
    }

    private static String lastToken(CharSequence text) {
        int end = text.length();
        int start = end;
        while (start > 0 && !Character.isWhitespace(text.charAt(start - 1))) {
            start--;
        }
        return text.subSequence(start, end).toString();
    }

    private static boolean startsHost(String token) {
        return token.length() > HOST_PREFIX.length()
            && token.regionMatches(true, 0, HOST_PREFIX, 0, HOST_PREFIX.length());
    }
}
