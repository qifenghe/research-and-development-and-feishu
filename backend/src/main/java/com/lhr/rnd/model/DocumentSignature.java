package com.lhr.rnd.model;

/** Names and dates belong to a saved document version, never to its downloader. */
public record DocumentSignature(String compiledBy, String compiledAt, String reviewedBy, String reviewedAt) {
    public static DocumentSignature pending(String name, String at) { return new DocumentSignature(name, at, null, null); }
    public boolean reviewed() { return present(reviewedBy) && present(reviewedAt); }
    public String compilerLabel() { return present(compiledBy) ? compiledBy : "历史记录未留存"; }
    public String compiledDate() { return date(compiledAt, "历史记录未留存"); }
    public String reviewerLabel() { return reviewed() ? "研发总监：" + reviewedBy : "研发总监（待审核）"; }
    public String reviewedDate() { return reviewed() ? date(reviewedAt, "待审核") : "待审核"; }
    private static boolean present(String value) { return value != null && !value.isBlank(); }
    private static String date(String value, String missing) { return !present(value) ? missing : value.length() >= 10 ? value.substring(0, 10) : value; }
}
