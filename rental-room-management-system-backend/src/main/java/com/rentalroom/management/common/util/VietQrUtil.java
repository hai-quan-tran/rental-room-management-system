package com.rentalroom.management.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Server-side mirror of the frontend's {@code shared/utils/vietqr.util.ts} (Room Detail, session 14)
 * — email HTML is built here in Java, so the TypeScript util can't be reused directly, but the URL
 * format and {@code addInfo} convention must stay identical for bank reconciliation to line up
 * regardless of whether the customer pays via the in-app QR or the emailed one.
 */
public final class VietQrUtil {

    private static final Pattern COMBINING_DIACRITICS = Pattern.compile("\\p{Mn}+");

    private VietQrUtil() {
    }

    /** Strips Vietnamese diacritics — VietQR's {@code addInfo} (transfer note) must stay plain ASCII. */
    public static String stripDiacritics(String text) {
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
        String noMarks = COMBINING_DIACRITICS.matcher(normalized).replaceAll("");
        return noMarks.replace('đ', 'd').replace('Đ', 'D');
    }

    /**
     * Builds a VietQR Quick Link image URL (https://www.vietqr.io/en/danh-sach-api/link-tao-ma-nhanh/)
     * — no API key needed. Returns {@code null} when the branch has no bank info configured yet.
     */
    public static String buildImageUrl(String bankBin, String accountNumber, BigDecimal amount,
                                        String addInfo, String accountName) {
        if (bankBin == null || bankBin.isBlank() || accountNumber == null || accountNumber.isBlank()) {
            return null;
        }
        BigDecimal wholeAmount = amount.max(BigDecimal.ZERO).setScale(0, RoundingMode.HALF_UP);
        StringBuilder url = new StringBuilder("https://img.vietqr.io/image/")
                .append(bankBin).append('-').append(accountNumber).append("-compact2.png")
                .append("?amount=").append(wholeAmount.toPlainString())
                .append("&addInfo=").append(encode(stripDiacritics(addInfo)));
        if (accountName != null && !accountName.isBlank()) {
            url.append("&accountName=").append(encode(stripDiacritics(accountName)));
        }
        return url.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
