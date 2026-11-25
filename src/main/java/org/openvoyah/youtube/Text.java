package org.openvoyah.youtube;

/** String helpers for page texts, addresses and version names. */
final class Text {
    private Text() {}

    /** Drops leading and trailing whitespace, counting Unicode spaces such as the no-break space as whitespace too. */
    static String trim(String text) {
        int start = 0;
        int end = text.length();
        while (start < end && blank(text.charAt(start))) start++;
        while (end > start && blank(text.charAt(end - 1))) end--;
        return text.substring(start, end);
    }

    /** At most the first {@code count} characters of {@code text}. */
    static String take(String text, int count) {
        return text.length() <= count ? text : text.substring(0, count);
    }

    /** The decimal number {@code text} holds, or null when it holds anything else. */
    static Integer toIntOrNull(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    private static boolean blank(char c) {
        return Character.isWhitespace(c) || Character.isSpaceChar(c);
    }
}
