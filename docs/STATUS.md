# Proje durumu

Her gün sonunda güncellenir (bkz. [WORKFLOW.md](WORKFLOW.md)).

**Son güncelleme:** 2026-10-19

## Şu anki durum
- Her şey `main`'de, açık dal ve açık issue yok. 156 birim testi geçiyor, lint hatasız, release (R8) derleniyor, debug APK ~42 MB. Sürüm numarası `0.3.0` (`versionCode` 3); **etiketlenmedi, yayınlanmadı.**
- Son turda: **araç profilleri** (motosiklet, araba, bisiklet, dik ekran dahil) ve tarama listesindeki **33 hatanın düzeltmesi** (aşağıdaki iki bölüm). Debug APK telefona yüklendi, emülatörde dik ekranlar, SOS, 360 dp ve büyük yazı görüldü.
- Bu turda eklenen: uygulama içi telefon (Son aramalar, Rehber, tuş takımı), MotoDash'in kendi arama ekranı (varsayılan telefon uygulaması seçilirse), eldivene göre büyütülmüş dokunma hedefleri, müzik ekranı yeniden (sesli, ilerleme çubuğu, kapak kaydırma, yön animasyonları, "Oynat" ile son müziği başlatma), navigasyon kartında canlı mini harita, uygulamaya gömülü Türkçe arama klavyesi, hız kartında rakım ve GPS durumu.
- Bu turda çıkarılan: OBD, bakım takibi, eğim göstergesi, GPX.
- **Gerçek cihazda denenmedi / eksik doğrulama:** arama sırasında MotoDash varsayılan telefon uygulamasıyken ses yolu, sessiz, tuş takımı ve ses tuşları; müzik kapalıyken Oynat'ın uygulamayı uyandırması; gerçek SMS gönderimi; kaza algılama eşikleri; gidonda ısınma; canlı mini haritanın pil ve ısı etkisi. Son yapılan kapak animasyonu düzeltmesi telefona yüklendi ama yeniden izlenmedi.
- Issue'lar: yedisi de kapalı. #5 (çevrimdışı harita ve rota) 2026-10-19'da "planlanmıyor" diye kapatıldı; fikir olarak aşağıda duruyor.

## Yapılanlar (kronolojik)
- **v0.2**: harita arama kutusu 5 km/sa üstünde kilitli, `User-Agent` sürümlü ve iletişim adresli (`Http.kt`, tek HTTP yardımcısı), README ve ekran görüntüleri yenilendi.
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
- **Eklendi, park yeri**: yolculuk bitince son hareketli nokta otomatik "Park" karosu olur.
- **Düzeltildi (lint)**: Android 8–9'da mesaj okuma çökebilirdi (API 28/30 çağrıları, artık androidx uyumluluk sınıfı), hız şeridi İngilizcede de "km/sa" yazıyordu, manifest GPX filtresinde host eksikti, kullanılmayan 4 string silindi. CI artık `lintDebug` de çalıştırıyor.
- **Eklenmedi**: viraj ağırlıklı rota (Calimoto/Kurviger'in asıl farkı; Valhalla'da anahtarsız karşılığı yok), radar uyarısı (veri ve yasal durum belirsiz), grup takibi ve canlı konum (sunucu gerekir), mph/mil birimi (hız limiti tabelaları ve her yerde sabit km varsayımı; ayrı iş olarak değerlendirilebilir), yakıt günlüğü (sayı girişi gerekir, sürerken olmaz), TPMS ve gidon kumandası (donanım).

## Uygulama içi telefon ekranı (2026-10-19)
- Dock'taki telefon tuşu artık sistem çeviricisini açmaz; `PhoneScreen` (CarPlay tarzı) açılır: Son aramalar (aynı numaranın art arda kayıtları birleşir, cevapsızlar kırmızı), Favoriler (rehberde yıldızlı kişiler), Tuş takımı. Satıra dokununca doğrudan aranır (`TelecomManager.placeCall`, izin yoksa çeviriciye numarayla düşer).
- Tuş takımı 5 km/sa üstünde kilitli (haritadaki arama kutusuyla aynı kural). İzinler (`READ_CALL_LOG`, `READ_CONTACTS`, `CALL_PHONE`) ilk açılışta değil, ekrandaki "İzin ver" düğmesiyle istenir.
- Mantık `PhoneMath` içinde, `PhoneTest` ile 8 test. Emülatörde Son aramalar listesi doğrulandı; Favoriler ve Tuş takımı emülatör takıldığı için ekranda görülmedi, gerçek aramanın başlaması da denenmedi.
- **Arama ekranı da uygulamada**: MotoDash varsayılan telefon uygulaması seçilirse (Ayarlar > Kurulum > "Varsayılan telefon uygulaması") `MotoInCallService` aramayı taşır; sistemin kendi arama ekranı açılmaz. `CallScreen` arıyor / çalıyor / görüşmede durumlarını, sessiz ve hoparlör düğmelerini gösterir. Seçilmezse eski davranış (telefonun kendi ekranı + `CallScreen` üstte) sürer. Başka uygulamanın `tel:` bağlantıları tuş takımına numarayla düşer. **Gerçek telefonda denenmedi**; Android 11 altında arayan adı yerine numara görünür.
- **Eldivenle kullanım (2026-10-19)**: dokunma hedefleri büyütüldü (dock karoları ekran yüksekliğine göre 62–76 dp, `PillButton` 72 dp, harita düğmeleri 68 dp, ayar satırının tamamı dokunulabilir, medya düğmeleri 64–84 dp). Telefon ekranında Favoriler yerine **Rehber** var (yıldızlılar üstte, sonra harf başlıklarıyla tüm rehber, sağda sayfa yukarı/aşağı düğmeleri), sekmeler 72 dp, tuş takımı ekrana yayılır.
- **Arama ekranı**: üstte saat, hız ve sıradaki dönüş şeridi; ses seviyesi − / +; görüşmede tuş takımı (DTMF, sesli menüler için); sessiz ve ses yolu seçici. Ses seviyesi ve DTMF yalnızca MotoDash varsayılan telefon uygulamasıyken çalışır. Gerçek aramada denenmedi.

- **2026-10-19, kaldırıldı**: OBD (ELM327, devir yayı, vites, arıza kodları, Bluetooth izinleri) ve bakım takibi (kilometre sayacı, hatırlatıcılar, uyarı şeridi) tamamen çıkarıldı; ayarlarda "Motor" sekmesi yok. Ana ekran müzik kartı sığacak şekilde küçüldü, SOS düğmesi hız kartının sol altına taşındı, SOS ekranı tam ekran, navigasyon kartı yönlendirme sırasında canlı mini harita, kalan mesafe ve varış saatini gösterir (Google düğmesi kalktı, kartın tamamı haritayı açar).

- **2026-10-19, ikinci kaldırma ve düzenlemeler**: eğim (viraj) göstergesi ve GPX (içe aktarma, takip, yolculuk paylaşımı) tamamen çıkarıldı. Müzik: kaynak seçici yok, yalnızca müzik uygulamalarının oturumu gösterilir; hiçbir şey çalmıyorken "Oynat" son müzik uygulamasını başlatır (önce medya tuşu, olmazsa uygulamayı açar). Ana ekran müzik kartı yeniden düzenlendi. Navigasyon kartı: canlı haritanın üstünde tek dokunuşla harita, atıf sağ altta, dönüş yazısı iki satır. Telefon: tuş takımı yeniden kuruldu (numara ipucu, eşleşen kişi önerisi, uzun basınca tümünü sil, 0'a uzun basınca +, yapıştır, kenarlıklı tuşlar), sayfa düğmeleri inceldi. Gerçek telefonda denenmedi.

- **2026-10-19, üçüncü tur**: uygulamaya gömülü Türkçe arama klavyesi (`SearchKeyboard`, sistem klavyesi yok, ekranı yeniden boyutlandırmaz, "Ara" hep görünür, uzun basınca sil, 123/sembol sayfası, İ/ı büyük-küçük kuralı); harita ekranında logo ve (i) kalktı, yerine küçük atıf yazısı (`MapCredit`), sağ düğmeler tek sütunda eşit aralıklı; dönüş talimatındaki "Caddesi caddesine" tekrarı temizlenir (`InstructionText`); hız kartında rakım ve GPS durumu (doğruluk yarıçapından iyi/orta/zayıf); müzikte kapak ve başlık "sonraki" ile sola, "önceki" ile sağa kayar, ana ekran müzik kartında da aynı animasyon, nefes alan kapak ve ekolayzer. Gerçek telefonda denenmedi.

## Araç profili (2026-10-19)
- İlk açılışta **Motosiklet / Araba / Bisiklet** seçimi (`VehicleChooser`); Ayarlar > Kurulum'dan değiştirilir. Seçim rota `costing`'ini (`motorcycle` / `auto` / `bicycle`), hız göstergesi ölçeğini, kaza algılama eşiklerini, durak türlerini ve düzeni belirler. Kullanıcının elle değiştirdiği ayarlar korunur, yalnızca hız limiti varsayılanı araca uyar. Gezinme sürerken araç değişirse rota yeniden hesaplanır.
- **Araba**: ⛽ düğmesi benzinlik ve şarj istasyonlarını birlikte listeler (soket filtresi yok). **Bisiklet**: tamir noktası, bisiklet dükkânı, içme suyu; yokuşsuz / düz zemin seçenekleri, bisiklet tipi (yol/şehir/dağ), rüzgâr uyarısı (esinti >= 35 km/s), şarj yok varsayımıyla pil %30 altında harita düşük FPS, **dik ekran** (ana ekran dikey düzen + alt dock, ayarlarda sekmeler üstte).
- Eşikler (`ProfileConfig`): kaza etkisi motosiklet 4 g / araba 5 g / bisiklet 3 g, hareket sayılan hız 25 / 30 / 10 km/s. **Sahada ayarlanmadı.**
- Emülatörde görüldü: seçim ekranı, dik bisiklet ana ekranı, dik ayarlar ve harita, bisiklet durak listesi, Araba'ya geçişte yatay düzene dönüş. **Görülmedi**: araba şarj istasyonu listesi, gezinirken profil değişimi, dik düzende telefon / müzik / arama / gezinti ekranları (yalnızca harita ve ayarlar bakıldı), gerçek telefonda hiçbiri.
- Yapılmadı: profile göre dokunma ölçeği (hepsi 64-84 dp), profile göre park etiketi ("Park" ortak), uygulama adı değişikliği.

## Hata düzeltmeleri (2026-10-19)
Tarama listesindeki 33 maddenin hepsi ele alındı (numaralar taramadaki sıradır). 156 birim testi, lint (hatasız) ve release derlemesi geçiyor; emülatörde dik bisiklet ana ekranı, dik telefon klavyesi, SOS geri sayımı ve sonucu, ayarlar, 360 dp genişlik ve 1,5 yazı boyutu görüldü. **Telefona yüklenmedi.**
- **Güvenlik ve doğruluk**: 1 SOS SMS'i ve açıklamalar araca göre ("Motor / Araba / Bisiklet kazası", nötr metinler). 2 Kaza algılamada iki tekerlekte eğim şartı (`CrashDetector`: darbe sonrası telefonun yönü ≥25° değişmeli ya da darbe eşiğin 2 katı olmalı; araba için gerekmez; eğim okunamazsa eski kural). 3 Hız limiti araca uygun `costing` ile. 4-5 Yeniden rota iş parçacığı artık "Bitir" ve varışta iptal oluyor; çevrimdışıyken ses bir kez, yeniden deneme 10 sn sonra. 6 Yolculuk mesafesi durağan GPS titremesini saymıyor, kayıt son durağan dakikaları dışarıda bırakıyor. 7 `LocationListener` artık nesne (Android 8-10 çökmesi önlendi). 8-9 Gelen arama bildirimi cevaplanınca/bitince iptal, kendi bildirimimiz arayan adını bozmuyor. 10 Hız şeridi uygulama açılınca yeniden başlıyor, konum izni yoksa başlamıyor, MotoDash ekrandayken gizleniyor. 11 `°` artık okunuyor. 12 Bildirim erişimi kapanıp açılınca müzik dinleyicisi yeniden kaydoluyor.
- **Dik/dar ekran**: 13 müzik, 14 telefon (sekmeler, klavye, liste), 15 SOS, 16 yolculuk geçmişi ve arama ekranı dik düzende; 17 dock genişliğe göre küçülüyor; 18 `cappedSp` ile yazı boyutu en çok %115 büyüyor (hız kartı, dock); 19 ayarlar başlığı/sekmeleri; 20 haritada klavye açıkken sağ düğmeler gizli, rota önizleme dolgusu ve gezinti çubuğu yönelime göre; 21 yatay düzenler artık genişlik ölçütüyle (`isNarrowScreen`) seçiliyor, Android 16 büyük ekranlarda yön kilidi yok sayılsa da dik düzen çalışır.
- **Ayarlar ve küçükler**: 22 Ana ekran ve pil satırları gerçek durumu gösteriyor, ayarlar yalnızca konum eksikse Kurulum'da açılıyor. 23 GPX metni kalktı. 24 Sesli hava uyarısı (yağmur, buzlanma, rüzgâr) tek anahtar olarak adlandırıldı. 25 Geri tuşu ayarları kapatıyor, SOS sonucunu kapatıyor, geri sayımı ve araç seçimini atlamıyor. 26 Durak aramasında Nominatim'in bulmadığı türler için Overpass da soruluyor, Overpass sorgusu tür başına ayrı sınırlı. 27 Park karosu eski günlerde tarih de gösteriyor. 28 Varış saati 15 sn'de bir yenileniyor. 29 Birleşme manevraları (37, 38) doğru ikon. 30 SOS ayar kartı izni geri dönünce yeniden okuyor, alarm sesi süreç ölürse açılışta geri yükleniyor. 31 Yalnızca yaklaşık konum verilmişse ayar açıklaması bunu söylüyor. 32 `markDialing` zamanlayıcısı üst üste çağrılarda kesilmiyor. 33 Ekolayzer artık yerleşimi her karede yeniden hesaplatmıyor (ölçüm yapılmadı, kare süresi iyileşmesi doğrulanmadı).
- **Doğrulanmayanlar**: Android 8-10 davranışı (7), gerçek arama bildirimi ve arayan adı (8-9), hız şeridinin yeniden başlaması (10), araba şarj istasyonu listesi, bisiklette Valhalla hız limiti, tablet/katlanabilir dik düzeni, kaza algılamanın gerçek düşüşte eğim eşiği (25°) ve iki kat darbe eşiği sahada ayarlanmadı.

## Sıradaki işler
1. **Telefonda dene** (üstteki "denenmedi" listesi): önce bir arama, Oynat, kapak kaydırma, klavye ile adres arama, canlı mini harita.
2. **SOS eşiklerini sahada ayarla**: `CrashDetector.IMPACT_G` (4 g) ve 8 sn hareketsizlik yalnızca sentetik verilerle test edildi. Gidonda yanlış alarm olursa önce eşiği artır. Gerçek SIM ile bir test SMS'i (kendi numarana) gönder.
3. **v0.3.0 yayını**: release anahtarını oluştur (`keytool`), `keystore.properties` yaz, `assembleRelease`, etiketle, `gh release create`. Ekran görüntülerini (özellikle yeni telefon, müzik ve klavye ekranları) Türkçe arayüzle yenile.
4. Çevrimdışı harita ve rota (eski #5, şimdilik planlanmıyor): büyük iş, MapLibre çevrimdışı bölgeler + yerel Valhalla/GraphHopper karşılığı gerekir.
5. Telefonda **araç profilini** dene: arabada şarj istasyonu listesi, bisiklette dik düzen ve durak aramaları, gezinirken araç değiştirme, kaza eşiklerinde eğim şartı (25°) ve iki kat darbe eşiği.

### Kararlaştırıldı, yapılmayacak
- Gidon kumandası desteği, yakıt menzili hesabı, TPMS, grup sürüşü/canlı konum paylaşımı (donanım veya sunucu gerektiriyor, anahtarsız/ücretsiz ilkesine uymuyor).
- Navigasyon kartında varış saatindeki hava: ikinci bir tahmin isteği gerekir, kazancı az.
- SOS kişileri için rehber izni: kişi seçici izin gerektirmeden aynı işi görüyor (telefon ekranı için `READ_CONTACTS` ayrıca istenir).
- Uygulama arka plandayken kaza algılama: Android arka plan sensörlerini kısıtlar, sürekli foreground servis ve bildirim gerekir. Dashboard zaten ekranda kalan launcher olduğu için ertelendi.

## Yarım kalan
- Yok.

## Bilinen sorunlar ve riskler
- Genel Overpass sunucuları sık 500/502 veriyor veya 30 sn sürüyor; benzinlik araması bu yüzden birkaçına birden soruyor ve Nominatim'e düşüyor. Yine de rota boyunca arama yedek yolda daha az isabetli (ilk ~20 km, 700 m koridor).
- Arayan şirket adı (rehberde olmayan numaralar) Google Telefon'un kendi sorgusundan geliyordu; MotoDash varsayılan telefon uygulamasıyken sadece rehberdeki ad, operatörün gönderdiği ad veya numara görünür (arama geçmişindeki kayıtlı ad tuş takımı önerilerinde kullanılır).
- Sistem çubukları ilk açılışta kısa süre görünüp düzeni kaydırabiliyor (emülatörde gözlendi, eskiden de vardı).
- SOS ve kaza algılama bir güvenlik garantisi değildir; README'de belirtildi.

## Günlük
- **2026-10-16**: PR #8 birleştirildi (6 issue kapandı). Durum incelendi, çalışma rutini (`WORKFLOW.md`) ve bu dosya eklendi.
- **2026-10-16**: Benzer projelerle (DMD2, Calimoto, Sygic, MOTO-HUB vb.) karşılaştırma yapıldı; v0.3 ve v0.4 özellik planı kararlaştırıldı.
- **2026-10-18 (ikinci tur)**: benzer projeler incelendi; rota seçenekleri, GPX takip, bakım hatırlatıcıları ve park yeri eklendi; lint hataları (Android 8–9 mesaj okuma çökmesi dahil) giderildi.
- **2026-10-18**: v0.2, v0.3 ve v0.4 planındaki işlerin hepsi tek dalda tamamlandı; emülatörde doğrulandı (yukarıya bak). Overpass güvenilmez çıktığı için benzinlikte Nominatim yedeği eklendi.
- **2026-10-19**: telefon, arama, müzik, klavye, mini harita ve kaldırma turları (ayrıntı yukarıda); tüm işler `main`'e alındı, `feature/obd` dalı kullanımdan kalktı.
- **2026-10-19 (akşam)**: araç profilleri eklendi (ilk açılışta seçim, dik bisiklet düzeni, araca göre rota, duraklar, eşikler); ardından tüm kod tarandı ve bulunan 33 hata düzeltildi (yeniden rota yarışı ve ses tekrarı, yolculuk mesafesi, kaza algılamada eğim şartı, arama bildirimi, hız şeridi, durak araması, dik ekranlar). Commit geçmişi ortak yazar satırlarından temizlendi (içerik ve yazar aynı), #5 kapatıldı. Tüm belgeler güncellendi.
