package com.quant.market.datasource.tickflow;

public final class SymbolUtils {

    private SymbolUtils() {}

    /** 600519 -> 600519.SH, 000001 -> 000001.SZ */
    public static String toTickflowSymbol(String code, String market) {
        if (code == null || code.isBlank()) {
            return code;
        }
        if (code.contains(".")) {
            return code;
        }
        String exchange = resolveExchange(code, market);
        return code + "." + exchange;
    }

    /** 600519.SH -> 600519 */
    public static String toLocalCode(String symbol) {
        if (symbol == null) {
            return null;
        }
        int dot = symbol.indexOf('.');
        return dot > 0 ? symbol.substring(0, dot) : symbol;
    }

    /** 600519.SH -> SH */
    public static String extractMarket(String symbol) {
        if (symbol == null) {
            return null;
        }
        int dot = symbol.indexOf('.');
        return dot > 0 ? symbol.substring(dot + 1) : null;
    }

    private static String resolveExchange(String code, String market) {
        if (market != null && !market.isBlank()) {
            return market.toUpperCase();
        }
        if (code.startsWith("6") || code.startsWith("5")) {
            return "SH";
        }
        if (code.startsWith("0") || code.startsWith("3")) {
            return "SZ";
        }
        if (code.startsWith("4") || code.startsWith("8")) {
            return "BJ";
        }
        return "SH";
    }
}
