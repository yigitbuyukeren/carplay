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
