# Proje durumu

Her gün sonunda güncellenir (bkz. [WORKFLOW.md](WORKFLOW.md)).

**Son güncelleme:** 2026-10-16

## Şu anki durum
- `main` temiz, CI yeşil, 27 birim testi geçiyor, debug APK ~41 MB.
- Son yayınlanan sürüm: `v0.1.0` (2026-10-15). PR #8 ile gelen özellikler henüz hiçbir sürümde yok.
- Açık issue: #5 (çevrimdışı harita ve rota).

## Sıradaki işler

### v0.2.0: birikmiş özellikleri yayınla
1. README'yi güncelle: yeni özellikler (gündüz teması, İngilizce, hız limiti, yolculuk kaydı, mesaj okuma, yatış açısı, Now Playing), "Fikirler" satırı, yeni ekran görüntüleri.
2. Harita arama kutusunu hareket halindeyken kilitle (sürüş kuralı: sürerken yazı girişi yok; şu an `MapScreen` içindeki `BasicTextField` hızdan bağımsız açık).
3. Nominatim/Overpass isteklerindeki `User-Agent` satırını güncelle (sürüm ve iletişim bilgisi).
4. `versionCode`/`versionName` artır, release yayınla.

### v0.3.0: OBD, kayıtlı yerler, hava durumu, benzinlikler (bu sırayla)
5. **OBD motor verileri** (isteğe bağlı; ELM327 uyumlu klasik Bluetooth adaptör, SPP/RFCOMM)
   - Ayarlarda eşli cihazlardan adaptör bir kez seçilir; uygulama açılınca otomatik bağlanır, kopunca tekrar dener. Adaptör aynı anda tek uygulamaya bağlanır (MotoDash bağlıyken Car Scanner bağlanamaz).
   - ELM327 başlatma (`ATZ`, `ATE0`, `ATL0`, `ATS0`, `ATH0`, `ATSP0`), ardından desteklenen PID'ler sorulur (`0100`/`0120`/`0140`); yalnızca motorun verdiği veriler gösterilir.
   - Hız göstergesinin içinde devir yayı ve altında vites. Vites devir/hız oranından öğrenilerek tahmin edilir (ECU vermese de çalışır).
   - Alt satırda motor sıcaklığı (aşırı ısınmada kırmızı ve sesli uyarı) ve akü voltajı (`ATRV`; 12 V altı / 15 V üstü uyarı).
   - Arıza kodları (`03`): arıza lambası yanınca kod ve Türkçe açıklama. Silme (`04`) yalnızca motor dururken ve onay sorularak.
   - Geliştirme için sahte OBD modu (emülatörde Bluetooth yok); gerçek bağlantı motorda denenir, ham iletişim kaydı dosya olarak paylaşılabilir.
   - Test motoru: TVS Raider 125 (Car Scanner ile bağlanıyor). Hava-yağ soğutmalı olduğu için sıcaklık PID'i olmayabilir veya yağ/silindir sıcaklığı olarak gelebilir; PID listesinden doğrulanacak.
   - İzinler: `BLUETOOTH_CONNECT` (API 31+), eski sürümlerde `BLUETOOTH`.
6. **Kayıtlı yerler**
   - Arama sonuçlarında ☆; basınca "Ev / İş / Favori" seçilir. Ev ve İş tek yuva (yenisi eskisinin yerine geçer), favoriler sınırsız.
   - Harita açılınca arama kutusunun altında büyük karolar (🏠 Ev, 🏢 İş, ⭐ favoriler; ≥56dp, yatay kaydırma). Uzun basınca sil; yeniden adlandırma yalnızca dururken.
   - Telefonda JSON olarak saklanır (`TripStore` gibi), sunucu yok.
7. **Hava durumu** (Open-Meteo, anahtarsız, 15 dakikada bir)
   - Durum çubuğunda simge ve sıcaklık.
   - 1 saat içinde yağmur bekleniyorsa dashboard'da uyarı şeridi.
   - Sesli yağmur uyarısı, her yağmur için bir kez; ayardan kapatılabilir.
   - Navigasyon kartında varış saatindeki hava.
8. **Benzinlikler**
   - Haritada ⛽ düğmesi; Overpass `amenity=fuel` ile işaretler, isim ve marka gösterilir.
   - Navigasyon varken rota üzerindekiler, yoksa 5 km çevredekiler.
   - Dokununca "Buraya git"; navigasyon sırasında benzinlik **ara durak** olarak eklenir (Valhalla çoklu nokta; `NavigationController` ara durak desteği gerekir).

### v0.4.0: ısınma koruması, SOS (bu sırayla)
9. **Isınma koruması**
   - Pil 42°C üstünde parlaklık kademeli kısılır ve harita kare hızı düşürülür.
   - Isınmışken motor 1 dakika durursa ekran kısılır, hareket edince geri açılır (normal sıcaklıkta karartma yok).
   - 45°C üstünde "Telefon ısınıyor, şarjı kesmeyi düşün" uyarısı.
10. **SOS** (varsayılan kapalı, ayarlardan açılır)
   - Rehberden en fazla 3 acil durum kişisi (`READ_CONTACTS`).
   - İki tetik, ayrı ayrı açılıp kapanır: otomatik kaza algılama (sert darbe + hareketsizlik) ve dashboard'da uzun basmalı SOS düğmesi.
   - Otomatikte 30 sn yüksek sesli geri sayım ve büyük İYİYİM düğmesi; süre dolunca konum linkli **tek** SMS (`SEND_SMS`), arama yok.
   - Ayarlarda SMS göndermeyen "Test et" düğmesi.

### Diğer
11. `NavigationController` için birim testleri (konumu rotaya eşleme, yeniden rota, 600 m / 150 m anonsları).
12. Release imzalama ayarı ve `isMinifyEnabled` (şu an sadece debug APK yayınlanabiliyor).
13. #5 çevrimdışı harita ve rota (büyük iş, ayrı planlanmalı).

### Kararlaştırıldı, yapılmayacak
- Gidon kumandası desteği, yakıt menzili hesabı, TPMS, grup sürüşü/canlı konum paylaşımı (donanım veya sunucu gerektiriyor, anahtarsız/ücretsiz ilkesine uymuyor).

## Yarım kalan
- Yok.

## Bilinen sorunlar
- Harita arama kutusu sürüş sırasında da yazı girişine izin veriyor (bkz. madde 2).

## Günlük
- **2026-10-16**: PR #8 birleştirildi (6 issue kapandı). Durum incelendi, çalışma rutini (`WORKFLOW.md`) ve bu dosya eklendi.
- **2026-10-16**: Benzer projelerle (DMD2, Calimoto, Sygic, MOTO-HUB vb.) karşılaştırma yapıldı; v0.3 ve v0.4 özellik planı kararlaştırıldı.
