package com.ozzystore35.vintagehunter.core;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * İlan kimliği: aynı ilanın tekrar bildirilmesini engellemek ve fiyat düşüşünü tanımak için.
 * Bildirimler platformun ilan numarasını vermez; kimlik = platform + ürün metni (fiyatlar ve
 * "yeni ilan / fiyatı düştü" gibi kalıp sözcükler çıkarılmış). Böylece aynı ürün 180 TL'den 150 TL'ye
 * düştüğünde aynı kimliği alır.
 */
public final class VHIdent {
    private VHIdent() {}

    private static final Set<String> BOILER = new HashSet<>(Arrays.asList(
            "yeni", "ilan", "ilani", "ilanin", "ilanlar", "kayitli", "arama", "aramanda", "aramaniz", "aramaniza", "aramana",
            "aramandaki", "favori", "favorindeki", "favorilerindeki", "favorine", "favorilerine", "takip", "ettigin",
            "ettiginiz", "ettigini", "urun", "urunun", "urunu", "urunde", "fiyati", "fiyat", "fiyatini", "dustu", "dusus",
            "indirim", "indirimde", "indirime", "girdi", "yerine", "tl", "try", "den", "dan", "ten", "tan", "ye", "ya", "e",
            "a", "icin", "bu", "su", "simdi", "artik", "firsat", "firsati", "kacirma", "kacirmayin", "hemen", "goz", "at",
            "bak", "var", "uygun", "eklendi", "yayinda", "guncellendi", "hey", "merhaba", "ve", "ile", "sana", "senin",
            "icin", "begendigin", "begendigin", "sepetindeki", "son", "dakika", "eski", "fiyatla", "yeniden", "satista"));

    /** Para tutarlarını metinden çıkar (kimlikte fiyat olmamalı). */
    private static String stripAmounts(String s) {
        return s.replaceAll("(?:₺|\\bTL|\\bTRY)\\s?\\d[\\d.,]*", " ")
                .replaceAll("\\d[\\d.,]*\\s?(?:TL|Tl|tl|TRY|₺)", " ");
    }

    public static String key(String platform, String title, String text) {
        String f = VHText.fold(stripAmounts((title == null ? "" : title) + " " + (text == null ? "" : text)));
        StringBuilder b = new StringBuilder();
        int n = 0;
        for (String t : f.trim().split(" ")) {
            if (t.isEmpty() || BOILER.contains(t)) continue;
            b.append(t).append(' ');
            n++;
        }
        String core = b.toString().trim();
        String p = platform == null ? "" : platform.toLowerCase();
        if (n < 2) return p + "|h:" + Integer.toHexString(f.hashCode());
        return p + "|" + core;
    }
}
