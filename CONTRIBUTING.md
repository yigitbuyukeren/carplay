# Katkı rehberi

MotoDash'e katkılarını bekliyoruz. Kod, hata bildirimi, fikir ve çeviri hepsi değerli.

## Başlamak
1. Depoyu fork'la ve klonla.
2. Android SDK ve JDK 17 kurulu olmalı. `local.properties` dosyasına `sdk.dir=/yol/Android/sdk` yaz (git'e girmez).
3. Derle ve test et:
   ```bash
   export JAVA_HOME=/yol/jdk-17
   ./gradlew testDebugUnitTest assembleDebug
   ```
4. APK: `app/build/outputs/apk/debug/app-debug.apk`. Emülatörde veya telefonda dene. Sahte GPS için emülatörde `adb emu geo fix <boylam> <enlem> <irtifa> <uydu> <hız>` kullanabilirsin.

## Pull request açmak
- Küçük ve tek konulu PR'lar tercih edilir. Büyük bir değişiklik için önce issue aç.
- Yeni mantık için birim testi ekle (`app/src/test`).
- Arayüz değişikliğinde ekran görüntüsü ekle.
- Kod stili: Kotlin resmi stili, çevredeki kodun diliyle uyumlu ol.
- PR'ın CI'dan (derleme ve test) geçmeli.

## Motor için tasarım ilkeleri
- Sürüş sırasında yazı girişi yok. Büyük dokunma hedefleri (en az 56dp), eldivenle kullanılabilir.
- Düşük dikkat gerektirir: kısa metin, yüksek kontrast, tek dokunuşla işlem.
- Ağ servisleri ücretsiz ve anahtarsız olmalı. Yeni bir harici servis eklemeden önce issue'da tartış.

## Nereden başlamalı?
`good first issue` etiketli issue'lara bak.

## Davranış kuralları
Saygılı ol. Bkz. [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).

## Lisans
Katkı yaparak kodunun [Apache 2.0](LICENSE) altında yayınlanmasını kabul edersin.
