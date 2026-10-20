# Plan listesi

Onaylanan maddeler. Uygulama, kullanıcı "yap" diyene kadar başlamaz. Kaynak: [EKSIKLER.md](EKSIKLER.md).

## 1. Sesle komut ve dikte (onaylandı 2026-10-20)

**Kararlar**
- Motor: Android `SpeechRecognizer`, `tr-TR`; Android 13+ta mümkünse cihaz üstü, olmazsa normal tanıyıcı. Ses Google servisine gidebilir, kullanıcı kabul etti; README ve ayarlara gizlilik notu.
- Tetikleyici: ekrandaki büyük mikrofon düğmesi (72 dp) + isteğe bağlı Bluetooth kask düğmesi (medya oturumu tuşu). Wake word yok, sürekli dinleme yok.
- Kapsam: komutlar + harita arama kutusuna dikte. Mesaja sesle cevap, madde #2'ye bırakıldı.

**Komutlar** ("X'e git", "eve/işe git", "benzinlik/şarj/tamir", "Ali'yi ara", müzik aç/durdur/sonraki/önceki, ses aç/kıs, "navigasyonu bitir"). SOS/yardım komutu yok (yanlış tanıma riski).

**Güvenlik**: arama ve rota başlatmada tanınan metin okunur, 3 sn iptal penceresi; düşük güven veya çok eşleşmede büyük karolarla seçim, otomatik eylem yok.

**Adımlar**
1. `VoiceCommand` saf ayrıştırıcı (Türkçe normalizasyon, ek ayıklama) + birim testleri.
2. `VoiceInput` kabuğu (`SpeechRecognizer`, `RECORD_AUDIO` izni ilk kullanımda ekrandaki düğmeyle, durum akışı).
3. Ses odağı: dinlerken müziği kıs, TTS ile yarışma.
4. `DashboardViewModel` komutları mevcut denetleyicilere bağlar.
5. UI: mikrofon düğmesi, dinleme katmanı (dalga, tanınan metin, iptal), dik/dar düzenler.
6. Ayarlar: aç/kapa, yalnızca cihaz üstü seçeneği, kask düğmesi.
7. README, STATUS, proje rehberi notları.

**Riskler**: rüzgâr/motor gürültüsü, her telefonda cihaz üstü Türkçe model yok, emülatörde mikrofon güvenilmez (gerçek telefon testi şart), ekran kapalıyken çalışma kapsam dışı.

## 2. Üçüncü taraf uygulama ekosistemi (onaylandı 2026-10-20)

Başka uygulamanın arayüzünü gömmek mümkün değil; MotoDash zaten HOME launcher olduğu için uygulamalarla köprü kurulur. Şu an `Launch.kt`'de sabit `NAV_APPS` ve `MUSIC_APPS` listeleri var.

**Kararlar**: dört parçanın hepsi bu maddede. Uygulama rafı sınırsız, kaydırılabilir. Harici navigasyon düğmesi rota önizleme kartında, yalnızca dururken.

**Parçalar**
1. **Uygulama rafı**: dock'tan açılan eldiven boyutlu (64-84 dp) ızgara; kullanıcı kurulu uygulamalardan sabitler, yukarı/aşağı taşır, dokununca `launchPackage`. 5 km/sa üstünde yalnızca sabitlenmiş listeden başlatma, ekleme/kaldırma/taşıma kilitli.
2. **Harici navigasyona gönder**: rota önizleme kartında "Waze'de / Google Haritalar'da aç" (`geo:` ve `waze://` derin bağlantıları); navigasyon tercihi sabit liste yerine seçilen uygulama.
3. **Müzik ve mesaj genelleştirme**: `MUSIC_APPS` yerine `CATEGORY_APP_MUSIC` taraması ve kullanıcı seçimi; mesajları sesli okuma için uygulama bazlı filtre (ör. WhatsApp açık, sosyal medya kapalı).
4. **Widget barındırma** (`AppWidgetHost`): kullanıcı sistem widget'larını dashboard'a koyar; düzenlenebilir kartlar (#9) ile birlikte tasarlanır. İzin akışı, boyutlandırma, ısı/pil etkisi ve dik düzen ayrıca ele alınır.

**Adımlar**
1. `Prefs`'e sabitlenmiş uygulama listesi (JSON, `SavedPlaces` modeli gibi) + saf yardımcı (ekleme, çıkarma, sıralama) ve testleri.
2. Kurulu uygulama sorgusu; mevcut `<queries>` ile yeterli mi gerçek telefonda doğrulanır (`QUERY_ALL_PACKAGES` kullanılmaz).
3. `AppsScreen` (ızgara, düzenleme modu, hız kilidi), dock karosu, dik/dar düzen.
4. Rota kartında harici navigasyon düğmesi, `Launch.kt` derin bağlantı yardımcıları, tercih.
5. Müzik/mesaj uygulama tarama ve filtre ayar satırı.
6. Widget barındırma (en son, #9 ile birlikte).
7. README, STATUS, proje rehberi notları.

**Kapsam dışı**: başka uygulama arayüzünü gömmek, kendi eklenti SDK'sı, Android Auto Car App Library host'u.

**Riskler**: başka uygulamaya geçince MotoDash arka plana düşer (HOME tuşu geri döndürür); paket görünürlüğü Android 11+ta gerçek telefonda denenmeli; çok uygulama dikkat dağıtır (hız kilidi şart); widget'lar pil ve ısıyı artırabilir.

## 3. Widget ve Live Activity (onaylandı 2026-10-20)

Widget barındırma, madde 2'nin 4. parçasıyla aynı iş; burada birlikte tasarlanır.

**Kararlar**: önce yerleşik kartlar düzenlenebilir olur (göster/gizle, sırala, boyut küçük/orta; hız, harita, müzik kartları aynı sisteme taşınır), sonra `AppWidgetHost` ile sistem widget'ları. Live Activity karşılığı: Android 16+ "Live Updates" bildirimleri `CarNotificationListener` ile okunup dashboard'da canlı karo olarak gösterilir; eski cihazlarda yok, bildirim alanlarının okunabilirliği gerçek telefonda doğrulanır.

**Riskler**: widget güncellemeleri pil/ısıyı artırır, izin akışı, dik düzen, sürüş dikkati (karo sayısı sınırı).

## 4. Park halinde video (onaylandı 2026-10-20)

**Kararlar**: ilk sürümde uygulama rafı (madde 2) üzerinden video uygulaması başlatma, 5 km/sa üstünde kilit; yerel video oynatıcı (Media3) sonraya bırakıldı, ayrı onayla. Tüm araç profillerinde görünebilir. YouTube vb. hizmetlerin videosu gömülmez. Media3 bağımlılığı yerel oynatıcı aşamasında kabul edildi.

## 5. mph / mil birimi (onaylandı 2026-10-20)

**Kararlar**: ayar elle seçilir (km varsayılan, mil seçilebilir), otomatik yok. Dahili hesaplar km/m kalır, yalnızca gösterim çevrilir; güvenlik eşikleri (5 km/sa kilidi, kaza eşikleri) sabit. Valhalla'ya `units=miles`. Kapsam: hız göstergesi ve ölçek (`SpeedCard`), hız şeridi (`overlay_speed_fmt`), `SpeedUtil`, `TripStats`, hız limiti tabelası (birim karışımı dikkat), rüzgâr uyarısı, durak mesafeleri, navigasyon mesafe metinleri ve sesli anonslar. Saf `Units` yardımcısı + testler. Uygulamada yakıt tüketimi/menzil özelliği olmadığı için mil/galon gösterilecek bir yer yok.


## Durum (2026-10-20 akşam)
- 5 (mph/mil): **yapıldı**. 2.1 uygulama rafı, 2.2 harici navigasyona gönder, 2.3 mesaj uygulaması sessize alma: **yapıldı** (müzik uygulaması genelleştirme zaten vardı: `CATEGORY_APP_MUSIC` taraması ve son müzik uygulaması). 4 (park halinde video): uygulama rafından video uygulaması başlatma **yapıldı**, yerel oynatıcı yapılmadı. 1 (sesle komut): komutlar ve arama kutusu dikte yedeği **yapıldı**, kask düğmesi tetikleyici yapılmadı. 3 (widget ve Live Activity): düzenlenebilir kartlar ve sistem widget barındırma **yapıldı**, Live Updates karosu yapılmadı.
- Müzik kütüphanesi gezinme (madde C) yanıtsız kaldığı ve doğrulanamadığı için **planlanmadı**.

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
