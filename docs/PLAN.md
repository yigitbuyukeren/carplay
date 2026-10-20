# Plan listesi

Bekleyen maddeler. Uygulama, kullanıcı "planı yap" diyene kadar başlamaz; yapılanlar buradan silinir (kayıt için `docs/STATUS.md` ve git geçmişi). Kaynak: [EKSIKLER.md](EKSIKLER.md).

## 6. Telefon listesinde 4 satır (eklendi 2026-10-20, bekliyor)

Şu an Son aramalar ve Rehber listesinde yatay telefonda 3 tam satır sığıyor (satır 80 dp). Liste alanı yaklaşık 310 dp, yani 4 satır için satır yüksekliği ~68-70 dp olmalı (dokunma hedefi 64 dp altına inmez).

**Plan**: `PagedList` satır yüksekliğini sabit 80 dp yerine ekran yüksekliğine göre seçer: yatay düzende önce 4 satır sığıyor mu bakar (`(alan - 3 aralık) / 4`, 64-80 dp arası kısıtlı), sığmazsa 3 satıra döner; dik düzende bugünkü 80 dp kalır. Satırın içindeki ArA düğmesi 60 → 56 dp, avatar 44 dp kalır, iki satır yazı (ad 24 sp + numara 14 sp) sığar. Sayfa düğmeleri gene tam satır sayısı kadar kaydırır. Telefonda ve emülatörde (360 dp ve 411 dp yükseklik) görülür.

## 7. Tuş takımı: yapıştır kalksın, silme tuşu büyüsün (eklendi 2026-10-20, bekliyor)

- "Yapıştır" düğmesi ve panoyu okuyan kod (`ClipboardManager`, `ContentPaste` ikonu, `phone_paste` metni) kaldırılır.
- Silme tuşu (şimdi 72 dp daire, ikon 32 dp) büyütülür: 84 dp daire, ikon 40 dp. Numara boşken de aynı yerde durur (soluk, tıklanmaz) ki "Ara" düğmesi yer değiştirmesin; dokunuşta bir hane siler, uzun basınca hepsini siler (bugünkü davranış).
- Dar (dik) ekranda sığması için 76 dp ile sınırlanır.
- README'deki "yapıştır" ibaresi çıkarılır.

## 8. Ana ekranda rakım kalksın (eklendi 2026-10-20, bekliyor)

Hız kartındaki "▲ 40 m" rakım yazısı kaldırılır (`SpeedCard`, `altitude_fmt`); GPS durumu ("GPS iyi / orta / zayıf / bekleniyor") kalır, ortalanır. Kullanılmayan `altitudeM` alanı ve dize kaynağı varsa temizlenir, README ve STATUS'taki "rakım" geçen satırlar düzeltilir.

## 9. Widget özelliği silinsin (eklendi 2026-10-20, bekliyor)

Madde 3'ün sistem widget'ı kısmı geri alınır; düzenlenebilir kartlar (harita, müzik, uygulama, boş) ve uygulama kartı kalır.
- Silinecek: `WidgetHost.kt`, `WidgetUi.kt` (kart, seçici, güncelleme), `DashCard.WIDGET`, ayardaki "Widget" seçeneği, `Prefs.widgetId`, `DashboardViewModel.widgetId/setWidget`, `MainActivity.onActivityResult` widget kolu, ilgili dize kaynakları; `ChoiceRow`'un 3'lü satıra bölme düzeni geri alınabilir ama dursa da zararsız.
- Eski kayıt: telefonda üst kart "widget" olarak kayıtlı olabilir. `DashLayout.fromString` bilinmeyen adı artık varsayılana düşürmek yerine o yuvayı `NONE` yapar, böylece ayar bozulmaz. Eşleştirilmiş widget kimliği için tek seferlik `AppWidgetHost.deleteHost()` temizliği yapılır (sonra kod silinir ya da bir sürüm tutulur).
- Test: `DashLayoutTest`'e "WIDGET kaydı boş yuva olur" testi.
- Belgeler: README, proje rehberi (mimari satırı), STATUS, `docs/PLAN.md` durum satırı, EKSIKLER.md (madde 9'daki widget ibaresi) güncellenir.
