# VintageHunter 1.3 — Kurulum ve kullanım

## Telefona kurulum

1. `VintageHunter.apk` dosyasını telefona indirin ve açın.
   - Android "bilinmeyen kaynaklardan yükleme" izni isterse, dosyayı açtığınız uygulama (Chrome, Dosyalar, Claude) için izin verin.
   - Play Koruma "tanınmayan uygulama" uyarısı verirse **Yine de yükle**'ye basın. Uygulama Play Store'da olmadığı için bu normaldir.
2. 1.2 kuruluysa üzerine kurulur; aynı imza anahtarı kullanılıyor. 1.2'de yakalanan ilanlar yeni kurallarla yeniden değerlendirilip taşınır.
3. Uygulamayı açın. Ana sayfadaki **Kurulum** kartını tamamlayın:
   1. **Bildirim erişimi → Aç** ekranında *VintageHunter fırsat yakalayıcı*'yı açın.
      Anahtar gri ve basılmıyorsa (Android 13+, Play dışı kurulum): **Uygulama bilgisi** → sağ üstteki **⋮** → **Kısıtlanmış ayarlara izin ver**, sonra tekrar açın.
   2. **Bildirim izni** verin.
   3. **Pil kısıtlamasını kaldırın**. Samsung/Xiaomi gibi telefonlarda avcının arka planda durmaması için gereklidir.
4. Üst köşedeki **nabız simgesi** (Sistem durumu) her şeyin yeşil olduğunu göstermelidir.

## Platformlarda kayıtlı arama kurma (ilanların kaynağı)

VintageHunter siteleri kendisi taramaz. Dolap, Gardrops, Sahibinden ve Letgo uygulamalarının telefona gönderdiği **kayıtlı arama / yeni ilan bildirimlerini** yakalar.

1. **Aramalar** sekmesinde bir aramanın yanındaki **↗** düğmesine basın ve platformu seçin; platformun arama sayfası açılır.
2. O platformda aramayı kaydedin ve bildirimini açın. Seçeneğin adı platforma göre "Aramayı kaydet", "Favori arama", "Takip et" ya da zil simgesi olabilir.
3. Telefon ayarlarında o platform uygulamasının bildirimleri açık olmalı.

Sistem durumu ekranında her platform için son bildirim zamanı ve son 24 saatteki bildirim, eşleşen, uygun ve fiyatsız sayıları görünür. **Son platform bildirimleri** listesi, gelen her bildirimin nasıl değerlendirildiğini gösterir.

## Günlük kullanım

| Ne olur | Ne görürsünüz |
|---|---|
| Kategori sınırınıza uyan yeni ilan bildirimi geldi | Titreşimli **"Uygun ilan · 180 TL"** bildirimi. Dokununca ilan detayı açılır; **Platformda aç** ilanın kendisini açar. |
| Fırsat oranı yüksek eşiği (%45) geçti | Ayrı kanaldan **"Yüksek fırsat %55"** bildirimi |
| Takip ettiğiniz ilanın fiyatı düştü | **"Fiyat düştü · 180 → 150 TL"** |
| 30 dakikada yeni uygun ilan(lar) var | Özet bildirimi. Yeni ilan yoksa özet gönderilmez. |
| Fiyat yok, belirsiz, yabancı para veya toplu bildirim | Bildirim gönderilmez. İlan "Tümü" görünümünde etiketiyle durur. |

- **Platform uygulamasından ekleme:** İlanda **Paylaş → VintageHunter**. İlanın gerçek bağlantısı saklanır; fiyatı girip kaydedersiniz.
- **Fırsat analizi:** İlan detayında piyasa değerini girin veya benzer ürünlerin satılmış fiyatlarını `1500; 1800; 2000` gibi yazın; ortanca değer kullanılır. Değer girilmezse "Piyasa değeri doğrulanamadı" yazar, oran uydurulmaz.
- **Listeler:** Takip ettiklerim, Satın almayı düşündüklerim, Satın aldıklarım. Satın aldıklarım listesinde alış, satış, komisyon ve kargo girince brüt ve net kâr hesaplanır.

## Ayarlar

- **Kategori fiyat limitleri:** Ayarlar → Kategori fiyat limitleri → Düzenle. Sınır dahildir (1.500 sınırında 1.500 uygun, 1.501 değil). Kaydedince tüm ilanlar ve bildirimler yeni sınırla yeniden değerlendirilir.
- **Fırsat eşikleri:** Çok yüksek ≥ %60, Yüksek ≥ %45, Orta ≥ %25. "Yüksek" eşiği öncelikli bildirimin minimumudur.
- **Yedekle / Geri yükle:** Ayarlar, aramalar, favoriler ve ilan geçmişi tek JSON dosyasına yazılır (fotoğraflar hariç).
- **Eşleştirme denemesi:** Bir bildirim metnini yapıştırıp kaydetmeden nasıl değerlendirileceğini görebilirsiniz.

## Geliştirici notları

| | |
|---|---|
| Yapı | Capacitor 8 + vanilla JS arayüz (`www/`) + Java yerel katman (`android/app/src/main/java/.../`) |
| Kural motoru | `core/` (saf Java): VHPrice, VHText, VHEval, VHIdent, VHFilter, VHConfigJson |
| Veritabanı | SQLite `vh.db`: `listings`, `notif_log`, `raw_log` |
| Birim testleri | `cd android && ./gradlew :app:testDebugUnitTest` |
| Uçtan uca test | GitHub Actions'ta API 34 emülatörü; `scripts/e2e.py` |
| Yayın | Yalnızca `main` dalında ve tüm testler geçince Releases'e APK + AAB |
| İmza | Depodaki `vintagehunter.keystore` yalnızca dışarıdan kurulum içindir. Play Store'a yüklerken kendi yükleme anahtarınızı oluşturun ve Play App Signing kullanın. |
