# Plan listesi

Bekleyen maddeler. Uygulama, kullanıcı "planı yap" diyene kadar başlamaz; yapılanlar buradan silinir (kayıt için `docs/STATUS.md` ve git geçmişi). Kaynak: [EKSIKLER.md](EKSIKLER.md).

## 6. Telefon listesinde 4 satır (eklendi 2026-10-20, bekliyor)

Şu an Son aramalar ve Rehber listesinde yatay telefonda 3 tam satır sığıyor (satır 80 dp). Liste alanı yaklaşık 310 dp, yani 4 satır için satır yüksekliği ~68-70 dp olmalı (dokunma hedefi 64 dp altına inmez).

**Plan**: `PagedList` satır yüksekliğini sabit 80 dp yerine ekran yüksekliğine göre seçer: yatay düzende önce 4 satır sığıyor mu bakar (`(alan - 3 aralık) / 4`, 64-80 dp arası kısıtlı), sığmazsa 3 satıra döner; dik düzende bugünkü 80 dp kalır. Satırın içindeki ArA düğmesi 60 → 56 dp, avatar 44 dp kalır, iki satır yazı (ad 24 sp + numara 14 sp) sığar. Sayfa düğmeleri gene tam satır sayısı kadar kaydırır. Telefonda ve emülatörde (360 dp ve 411 dp yükseklik) görülür.

## 7. Tuş takımı: yapıştır kalksın, silme tuşu büyüsün (eklendi 2026-10-20, bekliyor)

- "Yapıştır" düğmesi ve panoyu okuyan kod (`ClipboardManager`, `ContentPaste` ikonu, `phone_paste` metni) kaldırılır.
- Silme tuşu (şimdi 72 dp daire, ikon 32 dp) büyütülür: 84 dp daire, ikon 40 dp. Numara boşken de aynı yerde durur (soluk, tıklanmaz) ki "Ara" düğmesi yer değiştirmesin; dokunuşta bir hane siler, uzun basınca hepsini siler (bugünkü davranış).
- Dar (dik) ekranda sığması için 76 dp ile sınırlanır.
- README'deki "yapıştır" ibaresi çıkarılır.

## 8. Ana ekranda rakım kalksın (eklendi 2026-10-20, bekliyor)

Hız kartındaki "▲ 40 m" rakım yazısı kaldırılır (`SpeedCard`, `altitude_fmt`); GPS durumu ("GPS iyi / orta / zayıf / bekleniyor") kalır, ortalanır. Kullanılmayan `altitudeM` alanı ve dize kaynağı varsa temizlenir, README ve STATUS'taki "rakım" geçen satırlar düzeltilir.

## 9. Widget özelliği silinsin (eklendi 2026-10-20, bekliyor)

Madde 3'ün sistem widget'ı kısmı geri alınır; düzenlenebilir kartlar (harita, müzik, uygulama, boş) ve uygulama kartı kalır.
- Silinecek: `WidgetHost.kt`, `WidgetUi.kt` (kart, seçici, güncelleme), `DashCard.WIDGET`, ayardaki "Widget" seçeneği, `Prefs.widgetId`, `DashboardViewModel.widgetId/setWidget`, `MainActivity.onActivityResult` widget kolu, ilgili dize kaynakları; `ChoiceRow`'un 3'lü satıra bölme düzeni geri alınabilir ama dursa da zararsız.
- Eski kayıt: telefonda üst kart "widget" olarak kayıtlı olabilir. `DashLayout.fromString` bilinmeyen adı artık varsayılana düşürmek yerine o yuvayı `NONE` yapar, böylece ayar bozulmaz. Eşleştirilmiş widget kimliği için tek seferlik `AppWidgetHost.deleteHost()` temizliği yapılır (sonra kod silinir ya da bir sürüm tutulur).
- Test: `DashLayoutTest`'e "WIDGET kaydı boş yuva olur" testi.
- Belgeler: README, proje rehberi (mimari satırı), STATUS, `docs/PLAN.md` durum satırı, EKSIKLER.md (madde 9'daki widget ibaresi) güncellenir.

## 10. Kapsamlı sesli asistan (eklendi 2026-10-20, bekliyor; açık sorular en altta)

Amaç: kasklı, eldivenli, gürültülü sürüşte ekrana dokunmadan gidiş, telefon, müzik, mesaj ve bilgi işlerini sesle yapmak. İlke aynı kalır: anahtarsız, hesapsız, ücretsiz; bulut yapay zekâ servisi **yok**. Bugünkü hali: tek seferlik `SpeechRecognizer` + kural tabanlı `VoiceParser` (≈12 komut, tek tur, yalnız ekran düğmesi).

### A. Tetikleme (el değmeden başlat)
1. **Bluetooth kask düğmesi**: `ACTION_VOICE_COMMAND` ve `ACTION_ASSIST` niyetleri `MainActivity`'de yakalanır, dinleme başlar; kask ahizesinin ses tuşu (HFP voice recognition) ve uzun basış bununla çalışır. İsteğe bağlı olarak MotoDash'i "varsayılan dijital asistan" (`RoleManager.ROLE_ASSISTANT`) seçmek için ayar satırı (Google Asistan'ı değiştirir, kullanıcı karar verir).
2. **Medya tuşu / gidon kumandası**: oynat tuşuna çift basış gibi `MediaSession` düğmesi isteğe bağlı tetikleyici.
3. **Uyandırma sözcüğü ("Hey MotoDash")** deneysel, varsayılan kapalı: ekran açıkken (Android 14 mikrofon kuralı) çevrimdışı anahtar sözcük tanıma (Vosk küçük Türkçe model gibi açık lisanslı, anahtarsız seçenek; Porcupine gibi anahtar isteyenler elenir). Model APK'ya girmez, ayardan isteğe bağlı indirilir (APK ~41 MB kalır). Pil ve ısı ölçülür; sürekli mikrofon olduğu için açık gizlilik notu.
4. Dinlemeye başlama/bitme için kısa **bip sesleri** (kasklı kullanıcı ekrana bakmaz) ve titreşim.

### B. Ses girişi kalitesi
1. **Bluetooth kask mikrofonu**: kask bağlıysa `AudioManager` ile SCO/iletişim cihazı üzerinden onun mikrofonu kullanılır (telefon cebindeyken rüzgârda en büyük kazanç).
2. **Bağlamsal ipucu**: Android 13+ `EXTRA_BIASING_STRINGS` ile rehber isimleri, kayıtlı yer adları (Ev, İş, favoriler) ve sabitlenmiş uygulama adları tanıyıcıya verilir.
3. **Çevrimdışı Türkçe modeli**: `RecognitionSupport` ile var mı bakılır, yoksa ayarda "çevrimdışı Türkçe tanıma paketini indir" kısayolu (sistem ayarına gider).
4. **Güven puanı** (`CONFIDENCE_SCORES`): düşük güvenli komutlar çalıştırılmaz, "… mı dedin?" diye sorulur.
5. Sessizlik süresi ve bekleme sınırı sürüşe göre ayarlanır (rüzgârda erken kesmesin); "iptal", "dur", "vazgeç" her an dinlemeyi keser.

### C. Anlama (NLU), yine cihaz içinde
1. **Türkçe biçimbilim**: ek ayıklama (eve/işe/Ali'yi/annemi/Kadıköy'e), ünlü uyumu, ç/ş/ğ/ı/ö/ü yazım farkları, sayı ve saat sözcükleri ("on beş dakika", "yarım saat").
2. **Eş anlamlılar ve çok kalıp**: her komut için onlarca söyleniş ("rotayı bitir", "gitmekten vazgeç", "navigasyonu kapat"); kalıplar kod yerine veri tablosunda (kolay genişler).
3. **Bulanık eşleştirme** (Levenshtein + Türkçe ses benzerliği) rehber, kayıtlı yer, uygulama adı için; birden çok aday varsa sormak.
4. **Çok turlu konuşma** (durum makinesi): eksik bilgiyi sorar ("Nereye gideyim?"), belirsizi netleştirir ("Ali Yılmaz mı, Ali Kaya mı?"), sesle "evet / hayır / ikincisi / tekrar" kabul eder; zaman aşımında vazgeçer.
5. Bağlam: "oraya", "buraya kaydet", "bir daha", "geri al" bir önceki komuta göre çözülür.
6. İsteğe bağlı deney: cihazda çalışan küçük dil modeli (ör. ML Kit GenAI/Gemini Nano, yalnız destekleyen cihazlarda) yalnızca kural tabanlı çözümleyici anlamayınca yedek olarak. Bulut modeli kullanılmaz. Kapsam dışı olabilir; ilk sürüm sadece kural + bulanık eşleştirme.

### D. Komut kataloğu (genişletilen)
- **Navigasyon**: git, ara durak ekle ("yolda benzinlik", "yolda kafe"), en yakın X (kafe, eczane, hastane, ATM, otopark, market, lokanta: Nominatim/Overpass kategorileri), "kalan süre / kaç km kaldı / ne zaman varırım", "sıradaki dönüş ne", "otoyolsuz git / ücretsiz yol", "alternatif rota", "rotayı tekrar hesapla", "burayı favoriye ekle", "park yerime git", "evimi buraya ayarla".
- **Telefon**: ara (isim, "son aradığımı ara", "cevapsız aramalara bak"), gelen aramada "cevapla / reddet / sustur / hoparlör", numarayı rakamla söyleyerek arama.
- **Mesaj**: "son mesajı oku", "kim yazdı", "cevap yaz: …" (dikte, tekrar okuma, "gönder"/"iptal" onayı; bildirimin yanıtla eylemi `RemoteInput` ile, uygulamayı açmadan; olmazsa SMS). Yanlış kişiye gitmesin diye gönderim her zaman sesli onay ister.
- **Müzik**: açık/kapat, sonraki/önceki, **ada göre çalma** ("X sanatçısını çal", "Y çalma listesi") `transportControls.playFromSearch`, "bunu kaç dakika kaldı", ses seviyesi yüzdesi ("sesi yüzde elli yap").
- **Bilgi soruları**: hava ("yağmur var mı", "kaç derece"), saat, pil durumu, hız ve hız limiti, yolculuk özeti (mesafe, ortalama hız), bir sonraki dinlenme/benzin tavsiyesi yok (yakıt menzili yapılmayacak kararı sürer).
- **Uygulama ve ayar**: "WhatsApp'ı aç" (rafta olan ve kurulu uygulamalar), "gece/gündüz modu", "mil / kilometre", "sesli uyarıları kapat", "ekranı kıs".
- **Güvenlik**: "yardım çağır" SOS'u **doğrudan tetiklemez**, mevcut SOS geri sayım ekranını açar (yanlış tanımaya karşı); "iptal" geri sayımı durdurur.

### E. Ses çıkışı
1. Her işin sonunda kısa sesli geri bildirim (TTS): "Eve rota hazır, 18 dakika. Başlatayım mı?"; kapatılabilir ("yalnız ekranda göster").
2. **Konuşma kuyruğu ve öncelik**: dönüş anonsları önce gelir, asistan cümlesi anonsu bölmez; müzik kısılır (`USAGE_ASSISTANCE_NAVIGATION_GUIDANCE` / odak yönetimi).
3. Konuşma hızı ve ses ayarı, Türkçe ses seçimi.

### F. Güvenlik ve onay katmanları
- **Düşük risk** (müzik, ses, bilgi): hemen yapılır.
- **Orta** (rota başlat, uygulama aç): sesli veya ekran onayı, 3 sn varsayılan.
- **Yüksek** (arama, mesaj gönderme, SOS): açık "evet" ya da geri sayım + iptal; rehberde tek eşleşme olsa bile.
- Konuşulan her şey cihazda kalır; ses kaydı saklanmaz; yalnız son 10 komutun **metni** isteğe bağlı kayıtta (hata ayıklama için, kapatılabilir, kendiliğinden silinir).

### G. Arayüz
- Dinleme kartı: dalga göstergesi, duyulan metin, "ne diyebilirim?" önerileri (bağlama göre 3 örnek).
- **"Ne diyebilirim?" ekranı**: kategorilere göre örnek komut listesi (Ayarlar'dan ve kartın yardım düğmesinden).
- Ayarlar > Ses: asistanı aç/kapa, sesli yanıt, çevrimdışı tercih, Bluetooth kask mikrofonu, tetikleyiciler, uyandırma sözcüğü (deneysel), komut geçmişi.
- Eldiven kuralı: tüm düğmeler ≥64 dp; mikrofon her ekranda aynı yerde (şimdi navigasyon kartında ve harita arama kutusunda; ana ekranda ve telefon/müzik ekranlarında da).

### H. Test ve ölçüm
- Yüzlerce örnek cümlelik **birim test derlemi** (Türkçe ek ve ses varyantlarıyla), bulanık eşleştirme testleri, çok turlu diyalog durum makinesi testleri (sahte ses motoruyla).
- Saha testi: rüzgâr/motor gürültüsünde başarı oranı, kask mikrofonuyla ve telefon mikrofonuyla karşılaştırma; her sürümde "anlamadım" nedenleri yerel günlükten (sunucu yok).

### I. Aşamalar (öneri)
1. **Faz 1, temel**: tetikleyiciler (kask düğmesi, `VOICE_COMMAND`), bip sesleri, bağlamsal ipucu, güven puanı, çok turlu diyalog iskeleti, sesli geri bildirim, "Ne diyebilirim?" ekranı, veri tablosu kalıpları.
2. **Faz 2, komut kataloğu**: navigasyon sorguları, bilgi soruları, uygulama/ayar, müzik arama, gelen aramaya sesle cevap.
3. **Faz 3, mesaj**: sesli okuma, dikte ile cevap, onay akışı.
4. **Faz 4, deneysel**: uyandırma sözcüğü, cihaz içi dil modeli, varsayılan asistan rolü.

### Açık sorular (cevapla, plana işlerim)
1. Uyandırma sözcüğü ("Hey MotoDash") istiyor musun? (Pil, ısı ve ~40 MB isteğe bağlı model indirmesi getirir; öneri: Faz 4'e bırak.)
2. Bluetooth kask mikrofonunu varsayılan kullanalım mı? (Öneri: evet, kask bağlıysa.)
3. Sesli yanıt (TTS) varsayılan açık mı? (Öneri: açık, ayardan kapatılır.)
4. Mesaj göndermeyi (WhatsApp/SMS cevabı) kapsama alalım mı? (Öneri: evet, her zaman sesli onayla.)
5. MotoDash'i "varsayılan dijital asistan" yapma seçeneği olsun mu? (Kask düğmesi için en sağlam yol, ama Google Asistan'ın yerini alır; öneri: isteğe bağlı ayar.)
6. Cihaz içi dil modeli deneyine izin var mı, yoksa yalnızca kural tabanlı mı kalalım? (Öneri: ilk sürümde kural tabanlı.)
