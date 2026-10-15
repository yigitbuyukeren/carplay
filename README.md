# MotoDash

Motor için CarPlay tarzı Android sürüş paneli (launcher). Telefonu gidonda yatay ekran dashboard'a çevirir.

## Özellikler
- GPS hız göstergesi, saat/tarih, pil ve sıcaklık uyarısı
- Müzik kontrolü (Spotify, YouTube Music vb.; bildirim erişimi gerekir)
- Gelen arama ekranı (büyük Cevapla/Reddet)
- Gömülü harita ve navigasyon: MapLibre + OpenStreetMap (OpenFreeMap karoları), Nominatim arama, Valhalla motosiklet rotası, Türkçe sesli yönlendirme, rota dışına çıkınca yeniden hesaplama
- Google Haritalar / Waze / Yandex kısayolları
- Diğer uygulamaların üstünde hız/saat şeridi (overlay)
- Ana ekran (HOME) launcher olarak ayarlanabilir

## Derleme
```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
./gradlew testDebugUnitTest assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
`local.properties` içinde `sdk.dir` ayarlı olmalı (dosya git'e girmez).

## Güvenlik notu
Bu uygulama sürüş sırasında dikkat dağıtabilir. Telefonu sabitle, ayarları park halindeyken yap ve trafik kurallarına uy. Yazılım garantisiz sunulur (MIT).

## Kullanılan servisler ve atıflar
- Harita verisi © [OpenStreetMap](https://www.openstreetmap.org/copyright) katkıda bulunanlar (ODbL)
- Harita karoları: [OpenFreeMap](https://openfreemap.org); harita motoru: [MapLibre Native](https://maplibre.org)
- Adres arama: [Nominatim](https://nominatim.org) (genel sunucu; kullanım politikasına uygun olarak yalnızca Ara'ya basınca sorgular)
- Rota: [Valhalla](https://github.com/valhalla/valhalla) genel sunucusu (`valhalla1.openstreetmap.de`)

Bunlar ücretsiz, garantisiz genel servislerdir. Yoğun veya ticari kullanım için kendi sunucunu kur.

## Katkı
Issue ve PR'lar açık. Fikirler: gündüz teması, hız limiti, çevrimdışı harita, yolculuk kaydı.

## Lisans
[MIT](LICENSE)
