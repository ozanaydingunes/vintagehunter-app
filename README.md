# VintageHunter — Android uygulaması (1.3)

Dolap, Gardrops, Sahibinden ve Letgo uygulamalarının kayıtlı arama bildirimlerini yakalar; kayıtlı aramalarınız ve kategori fiyat sınırlarınızla karşılaştırır; uygun ilanı anında bildirir ve listeler.

- **Kurulum ve kullanım:** [docs/KURULUM.md](docs/KURULUM.md)
- **Teknik hata analizi (1.2 → 1.3):** [docs/ANALIZ.md](docs/ANALIZ.md)
- **Desteklenmeyen / kısıtlı özellikler:** [docs/DESTEKLENMEYENLER.md](docs/DESTEKLENMEYENLER.md)

## Derleme ve testler
Her gönderimde GitHub Actions:
1. birim testleri (`android/app/src/test`, çekirdek kural motoru),
2. APK + AAB derlemesi,
3. API 34 emülatöründe uçtan uca test (`scripts/e2e.py`).

Yalnızca `main` dalında ve tüm testler geçtiyse **Releases**'e `VintageHunter.apk` ve `VintageHunter.aab` yayınlanır. Test raporu ve ekran görüntüleri `ci-results` dalındadır.
