# Proje durumu

Her gün sonunda güncellenir (bkz. [WORKFLOW.md](WORKFLOW.md)).

**Son güncelleme:** 2026-10-16

## Şu anki durum
- `main` temiz, CI yeşil, 27 birim testi geçiyor, debug APK ~41 MB.
- Son yayınlanan sürüm: `v0.1.0` (2026-10-15). PR #8 ile gelen özellikler henüz hiçbir sürümde yok.
- Açık issue: #5 (çevrimdışı harita ve rota).

## Sıradaki işler
1. README'yi güncelle: yeni özellikler (gündüz teması, İngilizce, hız limiti, yolculuk kaydı, mesaj okuma, yatış açısı, Now Playing), "Fikirler" satırı, yeni ekran görüntüleri.
2. `v0.2.0` sürümü: `versionCode`/`versionName` artır, release yayınla.
3. `NavigationController` için birim testleri (konumu rotaya eşleme, yeniden rota, 600 m / 150 m anonsları).
4. Release imzalama ayarı ve `isMinifyEnabled` (şu an sadece debug APK yayınlanabiliyor).
5. Nominatim/Overpass isteklerindeki `User-Agent` satırını güncelle (sürüm ve iletişim bilgisi).
6. #5 çevrimdışı harita ve rota (büyük iş, ayrı planlanmalı).

## Yarım kalan
- Yok.

## Bilinen sorunlar
- Yok.

## Günlük
- **2026-10-16**: PR #8 birleştirildi (6 issue kapandı). Durum incelendi, çalışma rutini (`WORKFLOW.md`) ve bu dosya eklendi.
