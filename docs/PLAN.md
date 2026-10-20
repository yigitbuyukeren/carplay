# Plan listesi

Bekleyen maddeler. Uygulama, kullanıcı "planı yap" diyene kadar başlamaz; yapılanlar buradan silinir (kayıt için `docs/STATUS.md` ve git geçmişi). Kaynak: [EKSIKLER.md](EKSIKLER.md).

## 10. Sesli asistan: kalan işler (2026-10-20'de Faz 1-3 yapıldı, bkz. STATUS)

Yapılanlar `docs/STATUS.md`'de. Aşağıdakiler yapılmadı:
- **Faz 0 deneyleri (S1-S6)**: kask mikrofonu (SCO) ile tanıma karşılaştırması, ipucu listesinin etkisi, kaskın düğmesinin hangi niyeti yolladığı, Spotify/YouTube Music `playFromSearch`, WhatsApp yanıtla eylemi ve uyandırma sözcüğü ölçümü kullanıcının kaskı ve gerçek telefonla yapılmalı; kod yazıldı ama bu varsayımlar telefonda denenmedi.
- **Güven puanı eşiği** (`CONFIDENCE_SCORES`): düşük güvenli komutta "… mı dedin?" diye sormak.
- **Konuşma kuyruğu önceliği**: dönüş anonsu ile asistan cümlesi aynı anda konuşabilir (iki ayrı TTS örneği); ortak kuyruk ve öncelik.
- **Mikrofon her ekranda**: şu an navigasyon kartında, harita arama kutusunda, kask düğmesinde (`VOICE_COMMAND`/`ASSIST`) var; müzik ve telefon ekranlarına da eklenmeli.
- **Medya tuşu (çift basış) tetikleyicisi** ve isteğe bağlı **varsayılan dijital asistan rolü** (`RoleManager`).
- **Çevrimdışı Türkçe paket denetimi** (`checkRecognitionSupport`) ve ayarda indirme kısayolu.
- **Komut geçmişi** (son 10 komutun metni, kapatılabilir) ve yerel "anlamadım" günlüğü.
- **Bilgi soruları**: yolculuk özeti; **navigasyon**: gerçek "alternatif rota" (Valhalla alternatifleri), ara durak olarak kafe/eczane ekleme; **ayar**: ekran parlaklığı, sesli uyarıları sesle kapatma.
- **Uyandırma sözcüğü ("Hey MotoDash")**: deneysel, S6 olumluysa; çevrimdışı anahtar sözcük modeli (Vosk Türkçe vb.), pil/ısı ölçümü, açık gizlilik notu.
- **Cihaz içi dil modeli** deneyi (isteğe bağlı, bulut yok).
