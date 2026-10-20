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

## 10. Kapsamlı sesli asistan (planlandı 2026-10-20, bekliyor; başlanmadı)

Amaç: kasklı, eldivenli, gürültülü sürüşte ekrana dokunmadan gidiş, telefon, müzik, mesaj ve bilgi işlerini sesle yapmak. İlke aynı: anahtarsız, hesapsız, ücretsiz, bulut yapay zekâ yok. Bugün: tek seferlik `SpeechRecognizer` + kural tabanlı `VoiceParser` (~12 komut, tek tur, yalnız ekran düğmesi).

### 0. Kararlar (kullanıcı "en iyisini planla" dedi; öneriler karar kabul edildi)
1. **Uyandırma sözcüğü**: son aşama, deneysel, varsayılan kapalı; önce ölçüm deneyi (aşağıda S6).
2. **Bluetooth kask mikrofonu**: kask bağlıysa varsayılan kullan (ayardan kapatılır).
3. **Sesli yanıt**: varsayılan açık ("yalnız ekranda göster" ayarı var).
4. **Mesaj gönderme**: kapsamda, Faz 3, her zaman sesli onayla.
5. **Varsayılan dijital asistan rolü**: isteğe bağlı ayar (Faz 4); Faz 1'de rol olmadan `VOICE_COMMAND` / `ASSIST` niyetleri yakalanır.
6. **Cihaz içi dil modeli**: ilk sürümlerde yok; kural tabanlı + bulanık eşleştirme.

### 1. Başlamadan önce kısa deneyler (spike; her biri yarım gün, kod ana dala girmez)
Araştırmada kesinleşmeyenler gerçek telefonda (A059) ve kullanıcının kaskıyla doğrulanır; sonuç bu plana yazılır, plan ona göre ayarlanır.
- **S1, kask mikrofonu**: Bluetooth kask bağlıyken SCO / iletişim cihazı ile `SpeechRecognizer` tanıma başarısı; telefon mikrofonuyla karşılaştırma (sessiz oda, rüzgâr/motor kaydı).
- **S2, tanıyıcı özellikleri**: `RecognizerIntent` ipucu listesi (`EXTRA_BIASING_STRINGS`, API 33; sabit adı ve etkisi doğrulanacak), `checkRecognitionSupport` ile çevrimdışı Türkçe, `createOnDeviceSpeechRecognizer`; hangi Android sürümünde ne çalışıyor tablosu.
- **S3, kask düğmesi**: kaskın düğmesi telefonda hangi niyeti yolluyor (`VOICE_COMMAND`, `ASSIST` veya yalnızca medya tuşu). Araştırma, `VOICE_COMMAND` niyetinin başka tetiklerde de geldiğini, bazı kulaklıkların yalnızca varsayılan asistan Google iken çalıştığını gösterdi; MotoDash'in rol olmadan alıp alamayacağı denenir.
- **S4, müzik araması**: Spotify ve YouTube Music `playFromSearch` ile ada göre çalıyor mu.
- **S5, mesaj cevabı**: WhatsApp ve SMS uygulamasının bildirimi yanıtla eylemi (`RemoteInput`) sunuyor mu, uygulamayı açmadan gönderilebiliyor mu.
- **S6, uyandırma sözcüğü**: çevrimdışı Türkçe anahtar sözcük tanıma seçenekleri (Vosk Türkçe küçük modelin boyutu ve lisansı alphacephei.com modellerinden okunur; openWakeWord benzeri hafif modeller); CPU, pil, ısı ölçümü. Anahtar isteyen çözümler elenir. Android 14'te sürekli mikrofon için `FOREGROUND_SERVICE_MICROPHONE` ve "uygulama görünürken başlat" kuralı: MotoDash ekranı açık kalan launcher olduğu için ekran açıkken dinleme yeterli kabul edilir.

### 2. Mimari (yeni dosyalar `data/voice/` altında, ViewModel tek bağlantı noktası kalır)
- `Turkish.kt`: normalleştirme, ek ayıklama, ünlü uyumu, ç/ş/ğ/ı/ö/ü ve sesli sayı/saat sözcükleri, Levenshtein + Türkçe ses benzerliği. Saf, testli.
- `VoiceGrammar.kt`: komut kalıpları **veri tablosu** (kalıp, alanlar, eş anlamlılar, risk düzeyi); yeni komut = bir satır.
- `VoiceIntent.kt`: kapalı komut ailesi (şimdiki `VoiceCommand` genişler): navigasyon, telefon, mesaj, müzik, bilgi, uygulama/ayar, sistem.
- `VoiceDialog.kt`: çok turlu durum makinesi (bekle, soru sor, belirsizi seç, onay, zaman aşımı, iptal); saf, sahte motorla test.
- `VoiceInput.kt`: tanıyıcı sarmalayıcı: ipuçları, güven puanı, SCO mikrofonu, sessizlik süresi, bip sesleri.
- `VoiceSpeaker.kt`: TTS kuyruğu ve öncelik (dönüş anonsu > asistan), müzik kısma, hız ve ses ayarı.
- `VoiceActions.kt`: komutu çalıştıran küçük arayüzler (`NavActions`, `PhoneActions`, `MessageActions`, `MediaActions`, `InfoActions`, `AppActions`), mevcut denetleyicilere bağlanır; testte sahtesi.
- `VoiceHistory.kt`: son 10 komutun metni (kapatılabilir, ses kaydı yok).
- Arayüz: `VoiceUi.kt` (dinleme kartı, seçenek karoları, yardım), `Ayarlar > Ses`.

### 3. Tetikleme
- Ekrandaki mikrofon her ekranda aynı yerde (ana ekran kartı, harita, telefon, müzik, uygulama rafı), ≥64 dp.
- `ACTION_VOICE_COMMAND` ve `ACTION_ASSIST` niyetlerini `MainActivity` yakalar (kask düğmesi, sistem uzun basışı); ekran kapalı/arka plandaysa önce MotoDash öne gelir.
- Medya tuşu (çift basış, ayardan seçilir) ve isteğe bağlı varsayılan asistan rolü (`RoleManager`).
- Dinleme başlayınca/bitince bip + hafif titreşim; "iptal / dur / vazgeç" her an keser.
- Uyandırma sözcüğü yalnız S6 olumluysa, ekran açıkken, açık gizlilik notuyla.

### 4. Ses girişi kalitesi
- Kask bağlıysa SCO mikrofon; yoksa telefon.
- İpucu listesi: rehber adları, Ev/İş/favori adları, sabitlenmiş uygulama adları, sık hedefler.
- Güven puanı eşiği: düşük güvende "… mı dedin?" diye sorulur, çalıştırılmaz.
- Çevrimdışı Türkçe paket yoksa Ayarlar'da sistem ayarına kısayol; çevrimdışı tercih ayarı.
- Rüzgâr için sessizlik süresi uzun, "dinleme süresi doldu" kısa uyarı.

### 5. Komut kataloğu
- **Navigasyon**: git; ara durak ("yolda benzinlik/kafe"); en yakın kategori (kafe, eczane, hastane, ATM, otopark, market, lokanta; Nominatim/Overpass); kalan süre / kaç km kaldı / ne zaman varırım; sıradaki dönüş; otoyolsuz / ücretsiz yol; alternatif rota; yeniden hesapla; burayı favoriye ekle; evimi buraya ayarla; park yerime git; navigasyonu bitir.
- **Telefon**: ara (isim, "son aradığımı ara", "cevapsızlara bak"); gelen aramada cevapla / reddet / sustur / hoparlör; rakamlı numara.
- **Mesaj**: "son mesajı oku", "kim yazdı", "cevap yaz …" (dikte, tekrar okuma, "gönder/iptal"); bildirim yanıtla eylemi, olmazsa SMS.
- **Müzik**: aç/durdur/sonraki/önceki; ada göre çalma (`playFromSearch`); "yüzde elli yap"; "bu ne çalıyor".
- **Bilgi**: hava, yağmur, saat, pil, hız ve hız limiti, yolculuk özeti.
- **Uygulama ve ayar**: rafta/kurulu uygulamayı aç; gündüz/gece; km/mil; sesli uyarıları kapat; ekran parlaklığı (ısı korumasıyla çakışmaz).
- **Güvenlik**: "yardım çağır" SOS'u doğrudan tetiklemez, SOS geri sayım ekranını açar; "iptal" geri sayımı durdurur.

### 6. Ses çıkışı
- Kısa sesli geri bildirim ("Eve rota hazır, on sekiz dakika. Başlatayım mı?"); kapatılabilir.
- Konuşma kuyruğu: dönüş anonsu asistan cümlesini bekletir, asistan anonsu bölmez; müzik kısılır.
- Konuşma hızı/ses ayarı, Türkçe ses.

### 7. Güvenlik ve onay
- Düşük risk (müzik, ses, bilgi): hemen.
- Orta (rota başlat, uygulama aç): 3 sn sesli/ekran onayı.
- Yüksek (arama, mesaj gönderme, SOS ekranı): açık "evet" ya da geri sayım + iptal; tek eşleşme olsa bile.
- Ses kaydı saklanmaz; komut geçmişi yalnız metin, kapatılabilir, 10 kayıttan fazlası silinir.
- Sürerken tüm komutlar serbest (eller serbest), ama ayar değiştiren komutlar yalnız bir sesli onayla.

### 8. Arayüz
- Dinleme kartı: dalga, duyulan metin, bağlama göre 3 örnek.
- **"Ne diyebilirim?"** ekranı: kategoriye göre örnek komutlar, kartın yardım düğmesinden ve Ayarlar'dan.
- Belirsizlikte büyük seçenek karoları ("Ali Yılmaz / Ali Kaya"), sesle "birincisi" de çalışır.
- Ayarlar > Ses: asistan aç/kapa, sesli yanıt, çevrimdışı tercih, kask mikrofonu, tetikleyiciler, geçmiş, uyandırma sözcüğü (deneysel).

### 9. Test ve ölçüm
- Birim: Türkçe ek ve ses varyantlı **en az 300 cümlelik** derlem (beklenen komut ve alanlar), bulanık eşleştirme, durum makinesi (sahte ses motoru), onay katmanı, kuyruk önceliği.
- Saha: sessiz oda, kasksız/kasklı, rüzgârlı; başarı oranı tablosu telefon mikrofonu vs kask mikrofonu (S1 ile aynı betik).
- Yerel günlükte "anlamadım" nedenleri (sunucu yok), hangi kalıp eksik çıktığını görmek için.

### 10. Aşamalar (her biri kendi commit/PR'ı, sonunda emülatör + telefon doğrulaması)
1. **Faz 0**: S1-S6 deneyleri, sonuçların bu plana işlenmesi.
2. **Faz 1, temel**: `Turkish`, `VoiceGrammar` veri tablosu, `VoiceDialog` iskeleti, bip/titreşim, ipucu listesi, güven puanı, kask mikrofonu, `VOICE_COMMAND` niyeti, sesli geri bildirim kuyruğu, "Ne diyebilirim?" ekranı; mevcut komutlar yeni yapıya taşınır. Ölçüt: eski komutlar aynen çalışır, derlem testleri geçer.
3. **Faz 2, katalog**: navigasyon sorguları, bilgi, uygulama/ayar, gelen aramaya sesle cevap, ada göre müzik. Ölçüt: kataloğun her satırı için en az 10 cümlelik test.
4. **Faz 3, mesaj**: okuma, dikte ile cevap, onay akışı. Ölçüt: yanlış kişiye gönderim imkânsız (her gönderim sesli onaylı).
5. **Faz 4, deneysel**: uyandırma sözcüğü (S6 olumluysa), varsayılan asistan rolü, medya tuşu tetikleyici. Ölçüt: pil/ısı ölçümü eşik altında, açık gizlilik notu.

### Riskler
- Rüzgâr/motor gürültüsü başarıyı düşürür: kask mikrofonu (S1) belirleyici.
- Tanıyıcı telefona göre Google servisini kullanır (ses dışarı gidebilir); çevrimdışı paket yoksa başarı değişir.
- Kask düğmesi her kaskta aynı niyeti göndermez (S3).
- Mesaj uygulamalarının yanıtla eylemi garanti değil (S5).
- Sürekli mikrofon (uyandırma) pil, ısı ve gizlilik riski; bu yüzden en sona bırakıldı.
- Kapsam büyük: aşamalara bölündü, her aşama tek başına yayınlanabilir.
