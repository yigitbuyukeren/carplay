# Çalışma rutini

Her geliştirme oturumunun başında ve sonunda bu listeyi uygula. Güncel durum ve sıradaki işler [STATUS.md](STATUS.md) dosyasında.

## Güne başlarken

1. **Kodu güncelle**
   ```bash
   git switch main && git pull
   git status            # temiz olmalı
   ```
2. **Projenin sağlam olduğunu doğrula** (bir şey bozuksa önce onu düzelt)
   ```bash
   export JAVA_HOME=/opt/homebrew/opt/openjdk@17
   ./gradlew testDebugUnitTest assembleDebug
   ```
3. **Dışarıda ne olmuş bak**
   ```bash
   gh run list --limit 3      # CI yeşil mi
   gh pr list                 # bekleyen topluluk PR'ı var mı
   gh issue list              # yeni issue var mı
   ```
   Gelen PR ve issue'lar yeni işten önce gelir: yanıtla, etiketle, gözden geçir.
4. **Bugünün işini seç**: `STATUS.md` içindeki "Sıradaki işler" listesinden bir tane. Tek oturumda bitecek boyutta olsun.
5. **Dal aç**: `git switch -c feature/<kisa-ad>` (main'e doğrudan commit atma).
6. Arayüz işi yapılacaksa emülatörü aç: `$ANDROID_HOME/emulator/emulator -avd moto -no-snapshot`

## Günü kapatırken

1. **Testler, lint ve derleme yeşil**: `./gradlew testDebugUnitTest lintDebug assembleDebug` (CI ayrıca `assembleRelease` ile R8'li sürümü derler)
2. **Yeni mantık eklendiyse testi de eklendi mi?** (`app/src/test/...`)
3. **Arayüz değiştiyse** emülatörde kontrol et; gerekirse `docs/screenshots/` güncelle.
4. **Sürüş kuralları korunuyor mu?** Sürerken yazı girişi yok, dokunma alanları ≥56dp, yüksek kontrast, yeni metinler hem `values/strings.xml` hem `values-en/strings.xml` içinde.
5. **Commit ve push**: yarım iş bile olsa dalda commit'lenip push'lanır, yerelde bırakılmaz. Bitmişse PR aç (`Closes #<issue>` ile), bitmemişse taslak PR.
6. **Belgeleri güncelle**
   - `STATUS.md`: bugün yapılanlar, yarım kalan, sıradaki işler, bilinen sorunlar
   - Özellik eklendiyse `README.md` özellik listesi
   - Mimari değiştiyse `CLAUDE.md`
7. **Sürüm zamanı mı?** Kullanıcıya görünür birkaç özellik birikmişse:
   `app/build.gradle.kts` içinde `versionCode` +1 ve `versionName` artır, etiketle, `gh release create` ile APK'yı yayınla.
