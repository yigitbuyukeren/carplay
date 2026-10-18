# Proje durumu

Her gün sonunda güncellenir (bkz. [WORKFLOW.md](WORKFLOW.md)).

**Son güncelleme:** 2026-10-18

## Şu anki durum
- `feature/obd` dalında v0.2 ve v0.3 işleri ile v0.4 işlerinin tamamı bitti; `main`'e henüz birleşmedi (PR açık).
- 155 birim testi geçiyor, lint hatasız, debug APK ~41 MB, R8 ile küçültülmüş release APK ~24 MB. Sürüm numarası `0.3.0` (`versionCode` 3); **etiketlenmedi, yayınlanmadı.**
- Emülatörde elle doğrulandı: gösterge + OBD (sahte), hava çipi, harita, kayıtlı yerler, adres adlandırma, benzinlik listesi, ara durak ve "yola devam", ısınma koruması (parlaklık ve kararma), SOS test geri sayımı, sekmeli ayarlar, küçültülmüş release, GPX açma (dosya seçici), GPX rotasından çıkma ve rotaya yaklaşma, bakım uyarısı ve kartı.
- **Gerçek cihazda denenmedi:** gerçek ELM327 adaptör, gerçek SMS gönderimi, kaza algılama eşikleri, gidonda ısınma.
- Açık issue: #5 (çevrimdışı harita ve rota).

## Bu dalda yapılanlar
- **v0.2**: harita arama kutusu 5 km/sa üstünde kilitli, `User-Agent` sürümlü ve iletişim adresli (`Http.kt`, tek HTTP yardımcısı), README ve ekran görüntüleri yenilendi.
- **OBD**: ELM327 Bluetooth, devir yayı, tahmini vites (devir/hız oranından öğrenilir), motor sıcaklığı, akü voltajı, arıza kodları, sahte OBD modu.
- **Kayıtlı yerler**: Ev / İş / favoriler, arama sonucunda ve rota kartında ☆, harita panelinde büyük karolar, dashboard'da "Eve git / İşe git". Haritaya uzun basılan nokta adresiyle adlandırılır (Nominatim reverse).
- **Hava durumu** (Open-Meteo): navigasyon kartında sıcaklık çipi, 1 saat içinde yağmur ve ≤3°C buzlanma uyarısı (şerit + isteğe bağlı sesli).
- **Benzinlikler**: ⛽ düğmesi; yakınında (Nominatim, yedek: Overpass) veya rota üzerinde (Overpass, yedek: Nominatim koridoru). Navigasyon sırasında **ara durak** eklenir, varınca "Yola devam".
- **Isınma koruması**: 42°C üstünde parlaklık kısılır ve harita 20 FPS'e iner; sıcakken motor 1 dk durursa ekran kararır; 45°C'de uyarı.
- **SOS** (varsayılan kapalı): kişi seçici ile en fazla 3 kişi, otomatik kaza algılama ve uzun basmalı düğme, geri sayım ekranı, geri sayım sonunda kişi başına tek SMS (OSM konum linki), "Test et" SMS göndermez.
- `NavigationController` bağımlılıkları enjekte edilebilir hale getirildi, 26 testle kapsandı (eşleme, 600/150 m anonsları, yeniden rota, varış, benzinlik, ara durak, rota seçenekleri, GPX).
- Ayarlar dört sekmeye bölündü (Kurulum / Sürüş / Güvenlik / Motor).
- Release yapılandırması: R8 + kaynak küçültme, `keystore.properties` ile imzalama (dosya git'e girmez), CI artık `assembleRelease` de derliyor.

## İkinci tur: rakip ve benzer projelerden eklenenler
Bakılanlar: DMD2, Calimoto, Kurviger, Scenic, REVER, RISER, Motolog, AndrOBD, OpenDash, Headunit Revived / Open Launcher. Çıkan ortak beklentiler ve kararlar:
- **Eklendi, rota seçenekleri**: otoyolsuz ve ücretsiz yol (Valhalla `use_highways` / `use_tolls`), rota kartında iki düğme, seçim kalıcı.
- **Eklendi, GPX içe aktarma ve takip** (Kurviger, Scenic, DMD2'nin ortak özelliği): dosya seçici, paylaş menüsü ve dosya yöneticisinden açılır; Valhalla `trace_route` (harita eşleme) ile yollara oturtulur ve normal rota gibi dönüş talimatı verir. Yakınsa oradan başlar, uzaksa önce rotaya götürür; rotadan çıkınca yeniden rota çizmez (izi çöpe atmaz), rotaya dönünce devam eder. Benzinlik ara durağı izi bozmaz.
- **Eklendi, bakım hatırlatıcıları** (Motolog, OpenDash): beş kalem, uygulamanın saydığı kilometreye göre, dashboard'da yalnızca dururken tek seferlik uyarı.
- **Eklendi, park yeri**: yolculuk bitince son hareketli nokta otomatik "Park" karosu olur.
- **Düzeltildi (lint)**: Android 8–9'da mesaj okuma çökebilirdi (API 28/30 çağrıları, artık androidx uyumluluk sınıfı), hız şeridi İngilizcede de "km/sa" yazıyordu, manifest GPX filtresinde host eksikti, kullanılmayan 4 string silindi. CI artık `lintDebug` de çalıştırıyor.
- **Eklenmedi**: viraj ağırlıklı rota (Calimoto/Kurviger'in asıl farkı; Valhalla'da anahtarsız karşılığı yok), radar uyarısı (veri ve yasal durum belirsiz), grup takibi ve canlı konum (sunucu gerekir), mph/mil birimi (hız limiti tabelaları ve her yerde sabit km varsayımı; ayrı iş olarak değerlendirilebilir), yakıt günlüğü (sayı girişi gerekir, sürerken olmaz), TPMS ve gidon kumandası (donanım).
- **Sınır**: GPX eşleme genel Valhalla sunucusunun sınırlarına tabi (yaklaşık 200 km üstü reddedilebilir); hata mesajı bunu söyler.

## Sıradaki işler
1. **Gerçek motorda dene** (TVS Raider 125 + ELM327): PID listesi, vites tablosu öğrenmesi, sıcaklık PID'i var mı (hava-yağ soğutmalı). Ham kayıt "Kaydı paylaş" ile alınır.
2. **SOS eşiklerini sahada ayarla**: `CrashDetector.IMPACT_G` (4 g) ve 8 sn hareketsizlik yalnızca sentetik verilerle test edildi. Gidonda yanlış alarm olursa önce eşiği artır. Gerçek SIM ile bir test SMS'i (kendi numarana) gönder.
3. **Küçük ekran kontrolü**: dock (4 karo + durum) 360 dp yükseklikte sığıyor mu? Sığmıyorsa dock'u kaydırılabilir yap.
4. **v0.3.0 yayını**: release anahtarını oluştur (`keytool`), `keystore.properties` yaz, `assembleRelease`, etiketle, `gh release create`. Ekran görüntülerini Türkçe arayüzle yenile.
5. GPX için: rota planlayıcıdan gerçek bir dosya (Kurviger, Calimoto dışa aktarımı) ile dene; çok uzun izleri parçalara bölüp eşlemek gerekebilir.
6. #5 çevrimdışı harita ve rota (büyük iş, ayrı planlanmalı: MapLibre çevrimdışı bölgeler + yerel Valhalla/GraphHopper karşılığı düşünülmeli).

### Kararlaştırıldı, yapılmayacak
- Gidon kumandası desteği, yakıt menzili hesabı, TPMS, grup sürüşü/canlı konum paylaşımı (donanım veya sunucu gerektiriyor, anahtarsız/ücretsiz ilkesine uymuyor).
- Navigasyon kartında varış saatindeki hava: ikinci bir tahmin isteği gerekir, kazancı az.
- Rehber izni (`READ_CONTACTS`): kişi seçici izin gerektirmeden aynı işi görüyor.
- Uygulama arka plandayken kaza algılama: Android arka plan sensörlerini kısıtlar, sürekli foreground servis ve bildirim gerekir. Dashboard zaten ekranda kalan launcher olduğu için ertelendi.

## Yarım kalan
- Yok.

## Bilinen sorunlar ve riskler
- Genel Overpass sunucuları sık 500/502 veriyor veya 30 sn sürüyor; benzinlik araması bu yüzden birkaçına birden soruyor ve Nominatim'e düşüyor. Yine de rota boyunca arama yedek yolda daha az isabetli (ilk ~20 km, 700 m koridor).
- Sistem çubukları ilk açılışta kısa süre görünüp düzeni kaydırabiliyor (emülatörde gözlendi, eskiden de vardı).
- SOS ve kaza algılama bir güvenlik garantisi değildir; README'de belirtildi.

## Günlük
- **2026-10-16**: PR #8 birleştirildi (6 issue kapandı). Durum incelendi, çalışma rutini (`WORKFLOW.md`) ve bu dosya eklendi.
- **2026-10-16**: Benzer projelerle (DMD2, Calimoto, Sygic, MOTO-HUB vb.) karşılaştırma yapıldı; v0.3 ve v0.4 özellik planı kararlaştırıldı.
- **2026-10-18 (ikinci tur)**: benzer projeler incelendi; rota seçenekleri, GPX takip, bakım hatırlatıcıları ve park yeri eklendi; lint hataları (Android 8–9 mesaj okuma çökmesi dahil) giderildi.
- **2026-10-18**: v0.2, v0.3 ve v0.4 planındaki işlerin hepsi tek dalda tamamlandı; emülatörde doğrulandı (yukarıya bak). Overpass güvenilmez çıktığı için benzinlikte Nominatim yedeği eklendi.
