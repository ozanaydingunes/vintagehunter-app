# VintageHunter 1.2 — Teknik hata analizi

Tarih: 2 Kasım 2026 · İncelenen sürüm: 1.2 (versionCode 3, commit 4904081)

## Mevcut mimari (1.2)

| Katman | Teknoloji | Görev |
|---|---|---|
| Arayüz | Capacitor 8 + tek dosya HTML/JS (`www/index.html`, 118 KB) | Panel, Tara, Analiz, Avcı, Ayarlar |
| Yerel servis | `VHListenerService` (Android NotificationListenerService) | Dolap/Gardrops/Sahibinden/Letgo bildirimlerini okur |
| Eşleştirme | `VHMatcher` (saf Java) | Marka + fiyat |
| Kayıt | `captures.json` (dosya) + SharedPreferences | Yakalanan bildirimler, ayarlar |
| Bildirim | `NotificationCompat` + `AlarmManager` (özet) | Alarm, 30 dk özet |
| Bulut | claude.ai zamanlanmış görev | Saat başı "tarama zamanı" hatırlatması |

Platformlardan doğrudan veri çeken bir entegrasyon **yoktur**. Tek ilan kaynağı, platform uygulamalarının telefona gönderdiği bildirimlerdir.

## Sorularınızın cevapları

**1. Bildirim oluşturulurken ilan neden listede yok?**
Gelen bildirimlerin çoğu ilan bildirimi değildi:
- Claude bulutundan saat başı gelen "🔎 SS:00 taraması" hatırlatması.
- Uygulamanın 30 dakikalık özeti, hiç ilan yakalanmasa bile "Son 30 dakikada eşleşen yeni ilan yok" diye geliyordu (`VHDigestReceiver.post`, `total == 0` dalı).

Bu iki bildirim türü, listede ilan yokken de geliyordu.

Gerçek bir yakalama olduğunda da üç kopukluk vardı:
- Alarm bildirimine dokunmak platformun kendi `PendingIntent`'ini çalıştırıyordu (`setContentIntent(original)`). Uygulama değil Dolap/Sahibinden açılıyordu.
- Uygulamadaki "İlanı aç" düğmesi bu bağlantıyı yalnızca bellekte tutuyordu (`INTENTS` statik haritası). Android uygulamayı kapatınca bağlantı kayboluyor, düğme sadece platformun ana sayfasını açıyordu.
- Liste varsayılan olarak "Eşleşenler" filtresiyle açılıyordu. Listenizdeki markaları içermeyen bildirimler "Diğer" sekmesinde kalıyordu.

**2. Bildirimdeki ilan kimliği veritabanı kaydıyla eşleşiyor mu?**
Hayır. Alarm bildiriminin kimliği `id.hashCode()` idi ve bildirime kayıt kimliği hiç eklenmiyordu. Bildirimden kayda geri dönmenin yolu yoktu.

**3. Fiyat filtreleri neden çalışmıyor?** Beş ayrı neden var; hepsi bu sürümün koduyla doğrulandı:

| Bildirim metni | 1.2 sonucu | Doğrusu |
|---|---|---|
| `Levi's 501 kot 1.200 TL · Kargo 39,99 TL` | **Fırsat, 39,99 TL** (en düşük tutar seçiliyor) | 1.200 TL, sınır üstü |
| `He-Man figür 1,500 TL` | **Fırsat, 1,5 TL** (virgül ondalık sayıldı) | 1.500 TL |
| `Levi's 501 için 4 yeni ilan, 90 TL'den başlayan` | **Fırsat, 90 TL** | Toplu arama bildirimi, tek ilan değil |
| `Express kargo ile gönderildi 120 TL` | **Fırsat** ("Express" markası sanıldı) | Eşleşme yok |
| `Levi's 501 kot` (fiyatsız) | **Alarm + "Eşleşenler" listesinde** | Fırsat sayılmamalı |

Ayrıca:
- **İki ayrı fiyat ayarı vardı.** Ayarlar ekranındaki "kategori tavanları" yalnızca analiz ekranında, Tara ekranındaki "fiyat aralıkları" yalnızca avcıda kullanılıyordu. Birini değiştirmek diğerini etkilemiyordu.
- **Tara ekranının platform bağlantıları** fiyat filtresini yalnızca Sahibinden'e ekliyordu. Dolap, Gardrops ve Letgo bağlantıları sınır üstü ürünleri de gösteriyordu.

**4. Filtre sorguda mı, arayüzde mi?**
Fiyat kontrolü servis tarafında bir kez yapılıp sonuç (`status`) kayda yazılıyordu. Sınır değiştiğinde eski kayıtlar yeniden değerlendirilmiyordu. Arayüz ise yalnızca bu eski etikete göre süzüyordu.

**5. Platform entegrasyonları gerçek ilan verisi döndürüyor mu?**
Hayır, döndüren bir entegrasyon yok. Panel ekranındaki 5 ilan sabit kodlu **DEMO** verisiydi. "Platform bağlantıları" yalnızca tarayıcıda arama sayfası açan adreslerdi. Gerçek veri yalnızca bildirimlerden geliyordu ve bildirimlerden fotoğraf da alınmıyordu.

**6. Android arka plan işlemleri durduruyor mu?**
- Bildirim dinleyici sistem tarafından bağlanan bir servistir ve uygulama kapalıyken de çalışır.
- Ancak uygulama "Zorla durdur" ile ya da bazı üreticilerin (Samsung, Xiaomi) pil yöneticisiyle durdurulursa dinleyici yeniden bağlanmıyordu. Kodda `requestRebind` çağrısı yoktu.
- Özet alarmı `setInexactRepeating` ile kuruluydu; Android bunu dakikalarca kaydırabilir.

**7. Kategoriye özel fiyatlar doğru saklanıyor mu?**
Saklanıyordu ama kategori bazında değil, yalnızca iki tür bazındaydı ("giyim", "figür"). Konsol, antika ve elektronik için avcıda ayrı sınır yoktu.

**8. İnternet yokken davranış?**
- Yazı tipleri Google Fonts'tan yükleniyordu; internetsiz açılışta yedek fonta düşüyordu.
- Bildirim yakalama internetsiz de çalışıyordu.

**9. Bildirim ile liste arasındaki tutarsızlık nerede?**
Üç farklı veri kaynağı vardı:
- Claude bulutu: saatlik hatırlatma.
- `AlarmManager`: özet.
- `captures.json`: liste.

Alarm bildirimleri kayıtla bağlantısızdı (2. madde), özet de sıfır kayıtta gönderiliyordu (1. madde).

## 1.3'teki kalıcı düzeltmeler

| Sorun | Düzeltme |
|---|---|
| Bildirim ↔ kayıt kopukluğu | Tek veri kaynağı SQLite (`vh.db`). Önce kayıt yazılır, sonra o kaydın kimliğiyle bildirim gönderilir. Bildirime dokununca uygulama o ilanın detayını açar. Platform bağlantısı bildirimdeki "Platformda aç" düğmesinde saklanır. |
| Yanlış fiyat | Yeni ayrıştırıcı: binlik/ondalık kuralları, kargo/indirim/kupon tutarlarını yok sayma, "X yerine Y" fiyat düşüşü, yabancı para, toplu arama bildirimi. Belirsiz fiyat hiçbir zaman fırsat sayılmaz. |
| Çift fiyat ayarı | Tek ayar: kategori başına üst sınır. Her kayıtlı arama kendi aralığını kategori sınırı içinde daraltabilir. |
| Eski sınırla kalan kayıtlar | Sınır veya arama değişince tüm kayıtlar aynı Java kuralıyla yeniden değerlendirilir. Liste ve bildirimler aynı kodu kullanır. |
| Fiyatsız ilan "eşleşen" görünüyordu | Fiyatsız ilan ayrı etiketlenir ("Fiyat okunamadı"); uygun ilanlarda ve fırsat bildirimlerinde yer almaz. |
| Boş özet bildirimi | Özet yalnızca yeni uygun ilan varsa gönderilir. Claude'daki saatlik hatırlatma durduruldu. |
| Demo ilanlar | Kaldırıldı. Gerçek ilan yoksa boş durum ve kurulum adımları gösterilir. |
| Fotoğraf yok | Bildirimdeki ürün görseli varsa kaydedilir. |
| Dinleyici yeniden bağlanmıyordu | Uygulama açılışında `requestRebind`; pil optimizasyonundan muaf tutma düğmesi. |
| Gerçek bağlantı yok | Platform uygulamasında **Paylaş → VintageHunter** ile ilanın gerçek adresi kaydedilir. |
