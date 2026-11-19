package com.lakmal.keycraftsinhala;

import android.text.TextUtils;
import android.view.inputmethod.InputConnection;

public class SinhalaInputHandler {

    /**
     * Web Browsers / WebViews තුළ Sinhala Unicode Text නිවැරදිව commit කිරීම.
     */
    public static void commitSinhalaSuggestion(InputConnection ic, String suggestionText, int composingLength) {
        if (ic == null || TextUtils.isEmpty(suggestionText)) {
            return;
        }

        ic.beginBatchEdit();
        try {
            ic.finishComposingText();

            if (composingLength > 0) {
                // Allow room for multi-code-point Sinhala conjuncts and combining marks.
                int surroundingLength = (int) Math.min((long) composingLength * 16, 4096L);
                CharSequence beforeText = ic.getTextBeforeCursor(surroundingLength, 0);
                int deleteCodeUnits = calculateGraphemeCodeUnits(beforeText, composingLength);

                if (deleteCodeUnits > 0) {
                    ic.deleteSurroundingText(deleteCodeUnits, 0);
                }
            }

            ic.commitText(suggestionText, 1);
        } finally {
            ic.endBatchEdit();
        }
    }

    /**
     * Returns the UTF-16 code-unit length of the requested trailing grapheme clusters.
     * This avoids BreakIterator because Android's character iterator can split Sinhala
     * dependent vowels and virama/ZWJ conjunct sequences.
     */
    static int calculateGraphemeCodeUnits(CharSequence text, int graphemeCount) {
        if (TextUtils.isEmpty(text) || graphemeCount <= 0) {
            return 0;
        }

        int end = text.length();
        int start = end;
        int count = 0;

        while (count < graphemeCount && start > 0) {
            start = previousGraphemeStart(text, start);
            count++;
        }

        return end - start;
    }

    private static int previousGraphemeStart(CharSequence text, int end) {
        int cursor = end - Character.charCount(Character.codePointBefore(text, end));
        boolean attachPrevious = isGraphemeExtender(Character.codePointBefore(text, end));

        while (cursor > 0) {
            int codePoint = Character.codePointBefore(text, cursor);
            int previous = cursor - Character.charCount(codePoint);

            if (attachPrevious) {
                cursor = previous;
                attachPrevious = isGraphemeExtender(codePoint);
            } else if (isJoinerOrSinhalaVirama(codePoint)) {
                cursor = previous;
                attachPrevious = true;
            } else {
                break;
            }
        }

        return cursor;
    }

    private static boolean isJoinerOrSinhalaVirama(int codePoint) {
        return codePoint == 0x0DCA || codePoint == 0x200C || codePoint == 0x200D;
    }

    private static boolean isGraphemeExtender(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.NON_SPACING_MARK
                || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK
                || codePoint == 0x200C // Zero Width Non-Joiner
                || codePoint == 0x200D // Zero Width Joiner
                || (codePoint >= 0x1F3FB && codePoint <= 0x1F3FF) // Emoji modifiers
                || (codePoint >= 0xFE00 && codePoint <= 0xFE0F) // Variation selectors
                || (codePoint >= 0xE0100 && codePoint <= 0xE01EF)
                || (codePoint >= 0xE0020 && codePoint <= 0xE007F); // Emoji tags
    }
}
