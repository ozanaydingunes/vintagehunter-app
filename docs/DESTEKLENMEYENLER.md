# Çalışmayan veya platform kısıtı nedeniyle desteklenmeyen özellikler

Burada yazan hiçbir şey uygulamada çalışıyormuş gibi gösterilmez.

| İstek | Durum | Neden | Bunun yerine |
|---|---|---|---|
| Dolap, Gardrops, Sahibinden, Letgo'yu uygulamanın/sunucunun kendisinin 20–30 dakikada bir taraması | **Yok** | Resmî API yok. Dolap kullanıcı sözleşmesi otomatik robot/tarama ile veri toplamayı yazılı izne bağlıyor; diğer platformların kuralları doğrulanamadı. Tarama hesabı ve uygulamayı riske atar. | Platformların kendi kayıtlı arama bildirimleri anında yakalanır. Tarama aralığı yerine "bildirim gelir gelmez". |
| Sunucu tarafı tarama + push bildirimi | **Yok** | Yukarıdaki nedenle sunucunun platformlara istek atması gerekirdi. | Telefondaki bildirim dinleyici, uygulama kapalıyken de sistem tarafından çalıştırılır. |
| Onay olmadan otomatik satın alma | **Yok** | Para harcayan işlem kullanıcı onayı olmadan yapılmaz; platformlar da izin vermiyor. | Bildirime dokun → **Platformda aç** → satın alma platformda kullanıcı tarafından. |
| Bildirimden gelen ilanın kalıcı web adresi | **Kısmi** | Platform bildirimi ilan adresini vermez, yalnızca kendi "aç" bağlantısını verir. Bu bağlantı bildirim duruyorken çalışır. | Uygulamanın kendi bildirimi durdukça "Platformda aç" ilanı açar. Kalıcı adres için **Paylaş → VintageHunter**. Bağlantı kalmayınca ilan "Bağlantı süresi doldu" diye işaretlenir. |
| Silinen/yayından kaldırılan ilanı tespit | **Kısmi** | Platform sayfası sorgulanmadığı için ilanın satılıp satılmadığı bilinemez. | Yalnızca bağlantının açılıp açılamadığı işaretlenir. |
| Ürün fotoğrafı | **Kısmi** | Platform bildiriminde görsel varsa kaydedilir. Görselsiz bildirimde fotoğraf yoktur. | Platform ve kategori harfiyle sade bir yer tutucu gösterilir; sahte görsel kullanılmaz. |
| Fiyat | **Kısmi** | Bildirimde TL tutarı yoksa fiyat bilinemez. | "Fiyat okunamadı" etiketi; fırsat bildirimi gönderilmez. Paylaşılan/elle eklenen ilanda fiyat girilir. |
| Konum, yeni/ikinci el durumu | **Kısmi** | Yalnızca bildirim metninde yazıyorsa bilinir. | Filtrelerde bu not açıkça yazar. |
| Tahmini piyasa değeri (otomatik) | **Yok** | Doğrulanabilir satılmış fiyat verisine izinli erişim yok. Aktif ilan fiyatları satış fiyatı değildir. | Kullanıcının girdiği değer veya satılmış fiyatların ortancası. Yoksa "Piyasa değeri doğrulanamadı". |
| Gerçek platform bildirimleriyle test | **Yapılamadı** | Test ortamından Dolap/Gardrops/Sahibinden/Letgo hesaplarına ve sitelerine erişim yok. | Emülatörde platform uygulaması gibi bildirim gönderen test uygulamasıyla uçtan uca test. Gerçek bildirim metinleri telefonda **Sistem durumu → Son platform bildirimleri** ekranında görülebilir; uyumsuzluk olursa "Tanı raporu" ile düzeltilir. |
| Platform paket adları | **Doğrulanmadı** | Paket adı veya uygulama adında dolap/gardrops/sahibinden/letgo geçen uygulamalar tanınır. | Sistem durumu ekranı tanınan paketi gösterir; "bulunamadı" yazarsa bildirilmesi yeterli. |
| Kayıtlı arama sayısı | **Sınırsız** | 200'ün üzerinde arama ve çok sayıda kelime eşleştirmeyi yavaşlatabilir. | — |
| İlan kayıt sayısı | **3.000** | Favori ve listede olmayan en eski kayıtlar 3.000'i aşınca silinir. | Önemli ilanları favoriye veya listeye alın. |
| Play Store yayını | **Hazır değil** | AAB üretiliyor ama depodaki imza anahtarı yalnızca dışarıdan kurulum içindir. | Play Console'da kendi yükleme anahtarınızla imzalayın. |
