# Test sonuçları — VintageHunter 1.3

Son çalıştırma: GitHub Actions `main` · 2026-11-02 10:40:03 UTC · Android 14 (API 34) emülatörü, Pixel 6 profili.
**Uçtan uca: 22/22 geçti · Birim: 126 doğrulama geçti.**

## Nasıl test edildi
- **Gerçek akış:** Test yardımcı uygulaması (`com.vhtest.dolap`, “Dolap Test”) platform uygulaması gibi Android bildirimi gönderir. VintageHunter bunu sistem bildirim dinleyicisiyle yakalar, SQLite'a yazar, kural motoruyla değerlendirir ve kendi bildirimini gönderir.
- Bildirime emülatörün bildirim çubuğunda **gerçekten dokunulur** (uiautomator). “Platformda aç” sonrası açılan pencere denetlenir.
- Arayüz, uygulamanın kendi JavaScript fonksiyonlarıyla (Chrome DevTools Protocol) okunur. Uygulama arka plandayken veritabanı doğrudan okunur.
- Gerçek Dolap/Gardrops/Sahibinden/Letgo bildirimleriyle test **yapılamadı**; test ortamından bu platformlara erişim yok. Bkz. DESTEKLENMEYENLER.md.

## Kabul testleri (belgedeki 13. bölüm)
| Sonuç | Test |
|---|---|
| ✅ | Kurulum: bildirim erişimi ve dinleyici bağlı |
| ✅ | Test 3a — İlk açılışta sahte/örnek ilan gösterilmiyor |
| ✅ | Bildirim yakalandı ve veritabanına yazıldı (180 TL, Dolap, uygun) |
| ✅ | Bildirimdeki ürün fotoğrafı kaydedildi |
| ✅ | Uygulama kendi 'Uygun ilan' bildirimini gönderdi |
| ✅ | Bildirim geçmişi kayıtla eşleşiyor (aynı ilan kimliği) |
| ✅ | Test 2 — Bildirime dokununca ilgili ilanın detayı açılıyor |
| ✅ | Test 3 — 'Platformda aç' platformun ilan sayfasını açıyor |
| ✅ | Test 1 — Figür sınırı 1.500: 1.500 ve 1.250,50 uygun; 1.501/2.000/3.000/5.000 uygun değil |
| ✅ | Test 1 — Bildirimler de aynı sınırı kullanıyor (yalnız 1.500 ve 1.250,50 bildirildi) |
| ✅ | Test 1 — Arayüzdeki 'Uygun' listede 1.500 TL üstü yok |
| ✅ | Test 5 — Konsol 3.000 / figür 1.500: SNES 2.800 uygun, He-Man 2.800 uygun değil |
| ✅ | Test 7 — Fiyatsız, $, toplu, belirsiz ve kargolu bildirimler fırsat sayılmadı (kargo yok sayıldı: 1.200 TL) |
| ✅ | Aynı ilan tekrar gelince ikinci kayıt ve ikinci bildirim yok |
| ✅ | Takip edilen ilanda fiyat düşüşü: kayıt güncellendi ve bildirim gitti |
| ✅ | Test 8 — İki kayıtlı arama kendi kuralıyla çalışıyor (A: 200 sınırı → uygun değil, B: uygun) |
| ✅ | Filtre: 'nike' · Dolap+Gardrops · 100–1.000 TL birlikte uygulanıyor |
| ✅ | Test 6 — Uygulama ekranda değilken yakalama ve bildirim |
| ✅ | Test 4 — Zorla kapatıp açınca ilanlar, aramalar, favoriler, sınırlar ve filtre korunuyor |
| ✅ | Zorla kapatma sonrası dinleyici yeniden bağlandı ve yakalamaya devam ediyor |
| ✅ | Paylaş → VintageHunter: ilanın gerçek bağlantısı kaydedildi ve kurala göre değerlendirildi |
| ✅ | Arayüzde yakalanmamış JavaScript hatası yok |

## Birim testleri (çekirdek kural motoru, `CoreTests`)
126 doğrulama, örnekler:
- **Fiyat biçimleri:** `1.250,50 TL`, `1,250.50 TL`, `₺900`, `TL 250`, `300 TRY`, `1,500 TL`→1.500, `250,90 TL`
- **Fiyat sayılmayan tutarlar:** kargo, indirim, kupon, teklif, “500 TL üzeri”
- **Fiyat düşüşü:** “2.000 TL yerine 900 TL”, “1.200 TL'den 950 TL'ye düştü”
- **Fırsat sayılmayanlar:** fiyatsız, `$`, toplu (“4 yeni ilan … başlayan”), belirsiz (“150 TL veya 200 TL”)
- **Sınırlar:** dahil (1.500/1.501); kategori bağımsızlığı; arama sınırı kategori sınırını aşamaz
- **Eşleştirme:** tam kelime (Vansan ≠ Vans), Türkçe İ/ı/ü, kesme işareti (Nike'ın), “Nike + ceket”
- **Fırsat analizi:** kademeler (%60 / %59,99 / %24,99), piyasa değeri yoksa oran uydurulmaz
- **Kimlik:** aynı ilan farklı fiyatla aynı kimliği alır (fiyat düşüşü), platform kimliğe dahil

Ekran görüntüleri ve ham rapor: `ci-results` dalı → `runs/11-1-main/e2e/`.
