# Android Auto / Apple CarPlay karşısında eksikler

Tarih: 2026-10-20. MotoDash tarafı `docs/STATUS.md` ve koddan, Android Auto/CarPlay tarafı web araştırmasından (çoğu teknoloji basını, resmi sayfalar sınırlı) alındı. Rakip ayrıntıları yaklaşık kabul edilmeli.

Not: MotoDash bir yansıtma sistemi değil, telefonun kendisini gösterge paneline çeviren launcher. Aşağıdaki maddeler bu farkın doğurduğu eksiklerdir.

Durum sütunu: **Yapılabilir** = anahtarsız/ücretsiz ilkeye uyuyor. **Kısıtlı** = kısmen yapılabilir. **Yapılmaz** = sunucu, donanım, ortaklık veya ilke engeli.

| # | Eksik özellik | Android Auto / CarPlay'de | MotoDash'te | Durum |
|---|---|---|---|---|
| 1 | Sesle komut ve dikte | Gemini / Siri: sesle hedef, arama, mesaj, müzik | Yok. TTS yalnızca çıkış yönünde | Yapılabilir (`SpeechRecognizer`) |
| 2 | Mesajlara sesle cevap | Okuma ve sesle cevap, WhatsApp/Signal dahil | Bildirimleri sesli okur, cevap veremez | Kısıtlı (bildirim "yanıtla" eylemi) |
| 3 | Üçüncü taraf uygulama ekosistemi | Onaylı navigasyon, müzik, mesaj, şarj, park, hava uygulamaları | Yalnızca kendi modülleri; başka uygulama gömülemez | Yapılmaz |
| 4 | Canlı trafik ve olay bildirimi | Google Maps / Waze: trafik, kaza, polis, yol çalışması | Yok | Yapılmaz (sunucu/veri) |
| 5 | Çevrimdışı harita ve rota | Google Maps çevrimdışı haritalar | Yok (eski #5, planlanmıyor) | Kısıtlı, büyük iş |
| 6 | 3D bina ve zengin harita (Immersive Navigation) | Google Maps'te 3D binalar, yükseltili yollar, arazi | 2D vektör harita, 55° eğim | Yapılmaz |
| 7 | Şerit bilgisi ve kavşak görünümü | Şerit yönlendirme, kavşak görseli | Dönüş ikonu ve metin | Kısıtlı (Valhalla verisine bağlı) |
| 8 | Viraj ağırlıklı / manzaralı rota | Calimoto, Kurviger gibi uygulamalarda var; AA/CarPlay'de yok | Yok | Yapılmaz (anahtarsız karşılığı yok) |
| 9 | Widget ve Live Activity | CarPlay (iOS 26): yığınlanabilir widget, canlı etkinlik | Ayarlanabilir iki kart yuvası (harita, müzik, uygulama kartı, boş); sistem widget'ı denendi ve kaldırıldı | Kısmen yapıldı |
| 10 | Araç donanımı entegrasyonu | Direksiyon/gidon tuşları, araç mikrofonu, ekranı, klima | Yok | Yapılmaz (gidon kumandası kararlaştırıldı: yapılmayacak) |
| 11 | Araç verisi (OBD, TPMS, yakıt menzili) | Araç üreticisine bağlı | Çıkarıldı / yapılmayacak | Yapılmaz |
| 12 | Gösterge paneli (cluster) kontrolü | CarPlay Ultra | Yok | Yapılmaz |
| 13 | Park halinde video | Android Auto: YouTube, hareketle ses moduna geçer | Yok | Yapılmaz |
| 14 | Müzik uygulaması tarama ve kütüphane | Uygulamanın kütüphanesi araç ekranından gezilir | Yalnızca çalan oturum ve oynat/duraklat/geç | Kısıtlı (`MediaBrowser`) |
| 15 | Bağımsız sürücü dikkati doğrulaması | Google/Apple şablon ve içerik sınırları, onay süreci | Kendi kuralları (5 km/sa kilidi, büyük hedefler), doğrulama yok | Yapılmaz |
| 16 | Saha doğrulaması | Milyonlarca araçta denendi | Gerçek telefonda denenmemiş çok madde (arama ses yolu, SMS, kaza eşikleri, ısınma, mini harita pili) | Yapılabilir (test) |
| 17 | iOS desteği | iPhone (CarPlay) | Yalnızca Android | Yapılmaz |
| 18 | Birden çok ekran / çok kullanıcılı | Araç ekranı + telefon aynı anda | Tek ekran | Yapılmaz |
| 19 | Grup sürüşü, canlı konum paylaşımı | Google Maps / Find My ile | Yok | Yapılmaz (sunucu gerekir) |
| 20 | mph / mil birimi | Bölgeye göre | Yalnızca km | Yapılabilir |

## Yapılabilir olanlar, öncelik önerisi

1. Sesle komut (#1)
2. Sesle mesaj cevabı (#2, bildirim eylemiyle)
3. Düzenlenebilir ana ekran kartları (#9)
4. mph / mil birimi (#20)
5. Müzik kütüphanesi gezinme (#14)
6. Gerçek telefonda saha testi (#16)
