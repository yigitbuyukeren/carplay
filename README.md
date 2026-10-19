# MotoDash

[![CI](https://github.com/yigitbuyukeren/carplay/actions/workflows/ci.yml/badge.svg)](https://github.com/yigitbuyukeren/carplay/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

Motor için CarPlay tarzı Android sürüş paneli (launcher). Telefonu gidonda yatay ekran dashboard'a çevirir.

![Dashboard](docs/screenshots/dashboard.png)

| Rota | Navigasyon |
|---|---|
| ![Rota](docs/screenshots/route.png) | ![Navigasyon](docs/screenshots/navigation.png) |
| **Benzinlikler** |
|---|
| ![Benzinlikler](docs/screenshots/fuel.png) |
| **SOS geri sayımı** |
|---|
| ![SOS](docs/screenshots/sos.png) |

## Özellikler
**Gösterge ve sürüş**
- GPS hız göstergesi, saat, pil ve telefon sıcaklığı; gündüz/gece teması (ortam ışığına göre otomatik)
- Yolculuk kaydı: otomatik, geçmiş ekranı
- Türkçe ve İngilizce arayüz

**Navigasyon**
- Gömülü harita ve sesli yönlendirme: MapLibre + OpenStreetMap (OpenFreeMap), Nominatim arama, Valhalla motosiklet rotası, rota dışına çıkınca yeniden hesaplama
- Rota seçenekleri: otoyolsuz ve ücretsiz yol (Valhalla), seçim hatırlanır
- Kayıtlı yerler: Ev, İş ve favoriler; büyük karolarla tek dokunuşla rota, dashboard'da "Eve git"
- Park yeri: yolculuk bittiğinde motorun bırakıldığı yer otomatik kaydedilir ("Park yerine git")
- Benzinlikler: yakınında veya rotanın üzerinde; navigasyon sırasında tek dokunuşla **ara durak** ekle, varınca "Yola devam"
- Yolun hız limiti ve aşınca sesli uyarı
- Sürerken arama kutusu kilitlenir (sürüş kuralı: hareket halindeyken yazı girişi yok); hedefi kayıtlı yerlerden veya haritaya uzun basarak seç
- Google Haritalar / Waze / Yandex kısayolları

**Güvenlik**
- Hava durumu: sıcaklık, 1 saat içinde yağmur ve buzlanma uyarısı (şerit + sesli)
- Isınma koruması: telefon 42°C'yi aşınca ekran kısılır ve harita yavaşlar, motor durunca ekran kararır
- SOS (varsayılan kapalı): otomatik kaza algılama (sert darbe + hareketsizlik) veya uzun basmalı düğme; geri sayımdan sonra en fazla 3 kişiye konum linkli **tek** SMS, arama yok

**Telefon ve medya**
- Müzik kontrolü ve Now Playing (bildirim erişimi gerekir), gelen arama ekranı, mesajları sesli okuma
- Diğer uygulamaların üstünde hız/saat şeridi; ana ekran (HOME) launcher olarak ayarlanabilir

## Derleme
```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
./gradlew testDebugUnitTest assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
`local.properties` içinde `sdk.dir` ayarlı olmalı (dosya git'e girmez).

Sürüm APK'sı (R8 ile küçültülmüş, ~24 MB): kök dizine `keystore.properties` koy (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`; dosya git'e girmez) ve `./gradlew assembleRelease` çalıştır. Dosya yoksa APK imzasız çıkar.

## Gizlilik ve izinler
Hesap ve sunucu yok; veriler telefonda kalır. Dışarı giden tek şey, aşağıdaki genel servislere giden sorgulardır: arama metni (Nominatim, yalnızca Ara'ya basınca), rota uç noktaları ve hız limiti için son konum noktaları (Valhalla), yaklaşık konum (hava durumu için 1 km hassasiyetle, Open-Meteo), çevre veya rota boyunca benzinlik sorgusu (Nominatim / Overpass).

SOS yalnızca sen açarsan çalışır ve `SEND_SMS` izni ister; kişiler telefonun kişi seçicisinden eklenir (rehber izni gerekmez). Kaza algılama yalnızca uygulama ekrandayken çalışır; Android arka plandaki sensörleri kısıtlar. Operatörün SMS ücreti uygulanabilir. Bu özellik bir güvenlik garantisi değildir.

## Güvenlik notu
Bu uygulama sürüş sırasında dikkat dağıtabilir. Telefonu sabitle, ayarları park halindeyken yap ve trafik kurallarına uy. Yazılım garantisiz sunulur (Apache 2.0).

## Kullanılan servisler ve atıflar
- Harita verisi © [OpenStreetMap](https://www.openstreetmap.org/copyright) katkıda bulunanlar (ODbL)
- Harita karoları: [OpenFreeMap](https://openfreemap.org); harita motoru: [MapLibre Native](https://maplibre.org)
- Adres arama: [Nominatim](https://nominatim.org) (genel sunucu; kullanım politikasına uygun olarak yalnızca Ara'ya basınca sorgular)
- Rota: [Valhalla](https://github.com/valhalla/valhalla) genel sunucusu (`valhalla1.openstreetmap.de`)
- Hava durumu: [Open-Meteo](https://open-meteo.com) (ücretsiz, anahtarsız; ticari olmayan kullanım)
- Benzinlikler: [Overpass API](https://overpass-api.de) genel sunucuları (birkaçına birden sorulur) ve Nominatim

Bunlar ücretsiz, garantisiz genel servislerdir. Yoğun veya ticari kullanım için kendi sunucunu kur.

## Katkı
Issue ve PR'lar açık. Başlamak için [CONTRIBUTING.md](CONTRIBUTING.md) dosyasına ve `good first issue` etiketli konulara bak. Fikirler: çevrimdışı harita ve rota (#5), yeni dil çevirileri, ek OBD adaptörleri (BLE).

## Lisans
[Apache 2.0](LICENSE)
