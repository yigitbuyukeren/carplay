# Plan listesi

Bekleyen maddeler. Uygulama, kullanıcı "planı yap" diyene kadar başlamaz; yapılanlar buradan silinir (kayıt için `docs/STATUS.md` ve git geçmişi). Kaynak: [EKSIKLER.md](EKSIKLER.md).

Telefonda ve kaskla denenmesi gerekenler (kod hazır, doğrulama kullanıcıda; ayrıntı `docs/STATUS.md`): sesle komut tanıma kalitesi, kask mikrofonu, kaskın sesli komut düğmesi ve çift basış tetikleyicisi, "Hey RideDeck", `playFromSearch` ile ada göre çalma, bildirimden mesaja cevap, başka rota ve yolda durak. 2026-10-24 turundan: yeni ses akışları ([SES-OLCUM.md](SES-OLCUM.md) rutini), SMS gönderimi, hatırlatıcı bildirimi, önerici asistan, hız süzgeci (araç göstergesiyle karşılaştırma), uzun basışla Ayarlar.

**Önce yapılacak:** 2026-10-24 turunda testler, lint ve R8 çalıştırılmadı. `./gradlew testDebugUnitTest lintDebug assembleRelease` çalıştırıp kırmızıları düzelt (yeni: `VoiceNaturalTest`, `VoiceDialogTest`, `SpeedFilterTest`, `GaugeScaleTest`, `VoiceAssistantTest`'teki yeni senaryolar).

---

## Görselleri ve belgeleri yenile (2026-10-24)
Dock logosu kalktı ve kadran, çipler, nav kartı, Ayarlar değişti; eski görseller güncel değil. Emülatörde (Türkçe arayüz, uydurma adres, sahte GPS) tek turda:
- `docs/screenshots/*.png` ve `demo.gif`, README kapağı (`banner-light.jpg`, `banner-dark.jpg`; `yigitbuyukeren/tools/banners.html?p=ride`, maketteki dock logosu da kalkmalı).
- Sitede (`yigitbuyukeren.github.io`), profil README'sindeki kartlarda, `sosyal-onizleme/` ve `paylasim-taslaklari/` içindeki RideDeck görselleri.
- Emülatörde ayrıca bakılacaklar: yatay 360-415 dp ve dik bisiklet düzeni, büyük yazıda kadran sayıları ile büyük rakam çakışması, gündüz/gece temasında mavi navigasyon karosu, Ayarlar altındaki degrade.
`deploy.sh` ve GitHub'a itmek ayrı, açık istek gerektirir.

## Ayarlara erişim: kalan (2026-10-24)
İsteğe bağlı: Ayarlar 5 km/sa üstünde kilitlenir (telefon tuş takımıyla aynı kural), kilitliyken kısa bir açıklama gösterir. Konum izni yokken kendiliğinden açılma davranışı aynen kalır. Ayrı karar.

## Hız göstergesi: kalan (2026-10-24)
Aşama 3, yalnızca ölçüm gerekçelendirirse:
- Daha sık konum isteği: aralığı 1000 ms'den 200-250 ms'ye çekip telefonun gerçekten daha sık fix verip vermediğini ölçmek (gizli GPS satırı fix aralığını gösterir). Çoğu cihaz 1 Hz'de kalır; vermiyorsa eski değere dönülür.
- İvmeölçer birleştirme: yalnızca süzgeç yetmezse; motor titreşimi, `CrashSensor` çakışması ve pil maliyeti riskli.
- Telefonda ölçüm: A059'da çipin gerçek fix hızı ve `speedAccuracy`'nin gelip gelmediği; süzgeçli hızın araç göstergesiyle farkı. Plandaki gecikme tablosu ölçümle güncellenir.

## Sesli asistan: kalan (2026-10-24)
Aşama 0-1 uygulandı (STATUS). Kalanlar ölçüm ister:

### Aşama 2 - tanıma katmanı ve uyandırma sözcüğü (önce ölçüm)
Hedef: gürültüde doğruluk ve düşük pille sürekli dinleme.
1. Çevrimdışı tanıyıcı adayları: **Vosk** (Türkçe modeli var, kelime dağarcığını/gramerini sınırlayabiliyor, küçük model; boyutu ve Türkçe doğruluğu doğrulanmadı) ve **whisper.cpp** (Android'de çalışır, Türkçe iyi olabilir ama CPU'da yavaş, ısınma riski). İkisi de modeli **indirilebilir** tutar; APK ~41 MB kalmalı (`abiFilters` aynı).
2. Karar bir **kıyaslama** ile: aynı 40 cümle, A059 telefonda, kasklı/rüzgârlı/sessiz; ölçüler: doğru komut oranı, gecikme (konuşma bitişinden sonuç), 5 dakikalık dinlemede pil ve sıcaklık. `SpeechRecognizer` (bugünkü) taban çizgisi.
3. Seçilen motor `VoiceAssistant.SpeechListener` arayüzünün arkasına gelir; bugünkü `VoiceInput` varsayılan kalır, yeni motor Ayarlar'da seçenek olur. Testler değişmez (arayüz sahte).
4. **Uyandırma sözcüğü**: sürekli oturum yeniden başlatma yerine küçük, hep açık bir anahtar sözcük dinleyicisi (aynı motorun anahtar sözcük kipi ya da ayrı hafif model). Kurallar bugünküyle aynı: yalnızca ekran açıkken, çağrıda değilken, pil %30 üstündeyken; varsayılan kapalı. Yanlış uyanma sayacı yerelde tutulur ve Ayarlar'da gösterilir.
5. Kelime dağarcığı sınırlı tanıma: komut kümesi + rehber/yer/uygulama adları (bugünkü `biasing` listesinin karşılığı) gramer olarak verilir; serbest dikte yalnızca mesaj metninde.

Çıkış koşulu: yeni motor, taban çizgisine göre gürültülü kümede belirgin iyi değilse **eklenmez**; bu aşama "ölçtük, gerek yok" diye de bitebilir.

### Aşama 3 - cihaz içi dil modeli (yalnızca yedek ve ancak kapıyı geçerse)

**Rolü.** Kural/gevşek ayrıştırma `Unknown` döndürdüğünde son çare. Bilinen komutların yoluna **girmez**. "Garip ama anlamlı" cümleleri komuta çevirir ("şu önümdeki yolun sonunda bi kahve içelim", "müziği biraz kıs da konuşalım").

**Neden isteğe bağlı ve varsayılan kapalı.** Model yüzlerce MB ile birkaç GB arası; gidonda ısınma ve pil riski var; küçük modellerin Türkçesi zayıf olabilir. Kuralların zaten iyi işlediği durumda LLM yalnızca gecikme ve risk ekler.

**Mimari.**
- `IntentModel` arayüzü: `suspend fun interpret(heard: List<String>, context: AssistantContext): VoiceCommand?`. Gerçeği Android'de, testte sahte. `VoiceAssistant` yalnızca `Unknown` sonrası çağırır.
- Çıktı **katı JSON** (kısıtlı üretim: gramer ya da şema ile zorlanır, serbest metin yok) ve kapalı bir niyet listesine eşlenir: `navigate`, `call`, `message`, `music`, `info`, `settings`… Doğrulayıcı bilinmeyen alan/niyet/tür gelirse sonucu reddeder. Sonuç bir `VoiceCommand`'a çevrilir ve **aynı `run()` yolundan** geçer; yani arama geri sayımı, mesaj onayı, SOS onayı aynen işler.
- **LLM'in yapamayacakları**: SOS/acil durum başlatmak (yalnızca kural yoluyla), mesaj metni üretmek ya da değiştirmek (metin tanıyıcıdan olduğu gibi gelir), onay sorusuna kendisi cevap vermek, ayar dışı gizli eylem yapmak. Düşük güvenli çıktıda "şunu mu demek istedin?" ile sorar.
- **Bağlam** (`AssistantContext`): açık ekran, navigasyon var mı, müzik çalıyor mu, çağrı var mı, rehber adları (en çok 50), kayıtlı yerler, uygulama adları; yalnızca yerelde, istem içinde.
- **Bütçe ve koruma**: zaman aşımı ~4 sn ("bir saniye" diye bekleme sesi), aşılırsa "anlamadım"; yalnızca ekran açık, `ThermalGuard` sıcak değil, pil %30 üstü; model tembel yüklenir, 60 sn kullanılmazsa RAM'den bırakılır; aynı anda tek çağrı.

**Çalışma zamanı adayları (hepsi araştırılacak, hiçbiri seçilmedi).**
| Aday | Artı | Eksi / bilinmeyen |
|---|---|---|
| **LiteRT-LM** (Google AI Edge) | Android destekli, işlev çağırma ve kısıtlı üretim var; Gemma, Llama, Phi, Qwen listeli | Play hizmetlerine bağımlı mı **doğrulanmadı**; örnek Gemma modeli ~2,6 GB; Gemma indirmek lisans onayı/giriş isteyebilir (anahtarsız ilkeye ters) |
| **llama.cpp** (JNI, GGUF) | Play hizmeti yok, GBNF ile JSON'u gramerle zorlar, onaysız indirilebilen açık modeller (ör. Qwen 0.5-1.5B sınıfı) | NDK/JNI bakımı, APK büyür (arm64 yalnız), hızlandırma sınırlı |
| **MediaPipe LLM Inference** | Basit API | LiteRT-LM tarafından yerine geçilmiş olabilir, durumu **doğrulanmadı** |

Not: Aday modellerin Türkçe başarısı, boyutu ve lisansı **hiçbiri için doğrulanmadı**; aşağıdaki kapı bunun için var.

**Önce kapı (go / no-go), entegrasyondan önce.**
1. Mac'te seçilen çalışma zamanı + 2-3 model ile **300+ cümlelik derlem** koş (Aşama 0'daki doğal varyantlar, olumsuz örnekler ve gerçek "anlaşılmayan" kayıtları).
2. Ölçüler: doğru komut oranı, **yanlış eylem oranı** (komut olmayan şeyi komut sanma), JSON geçerlilik oranı, token/sn.
3. A059'da: ilk token gecikmesi, toplam gecikme (p50/p95), 10 ardışık çağrıda sıcaklık ve pil, RAM.
4. **Geçme eşiği** (öneri): kuralların kaçırdığı cümlelerin ≥%80'ini doğru çözmeli, yanlış eylem oranı <%2, p50 gecikme <3 sn, 10 çağrıda ısınma uyarısı eşiğine (42°C) varmamalı. Eşik tutmazsa Aşama 3 uygulanmaz, bu bölüm "denendi, gerek yok" diye kapanır.

**Kapıyı geçerse yapılacaklar.**
1. `IntentModel` arayüzü, doğrulayıcı ve `Fake` ile `VoiceAssistantTest` senaryoları (zaman aşımı, saçma JSON, yasak niyet, düşük güven, SOS reddi).
2. Ayarlar > Kurulum'da "Akıllı anlama (deneysel)" satırı: boyutu ve süresi gösterir, **kullanıcı isteyince** indirir (Wi-Fi uyarısı), silme düğmesi, varsayılan kapalı. İlk indirmeden sonra internet gerekmez. Anahtar, hesap, giriş istemeyen bir model kaynağı seçilir.
3. Hata ayıklama için yerel "LLM ne anladı" kaydı (`VoiceHistory` yanına, yalnızca metin, kapatılabilir).
4. README/`CONTRIBUTING`/STATUS'ta özelliğin deneysel olduğu, çevrimdışı çalıştığı ve verinin cihazdan çıkmadığı yazılır.

**Bulut seçeneği**: projenin "anahtarsız" ilkesine aykırı; yapılmayacak. İleride istenirse kullanıcının kendi anahtarını girdiği, varsayılan kapalı, sesin değil yalnızca metnin gittiği ayrı bir madde olarak yeniden düşünülür.

### Riskler
- Gevşek ayrıştırma yanlış komut çalıştırabilir → yalnızca zararsız komutlar, belirsizlikte sorar, kritik eylemler onaylı, olumsuz örnek grubu testte (gerçek "anlaşılmayan" cümleleri oraya ekle).
- Önerici asistan sürüşte dikkat dağıtabilir → varsayılan kapalı, 30 dk/5 dk sınırı, "sus" ile kapanır; sahada ölç.
- Sürekli dinleme (uyandırma sözcüğü, Aşama 2) pil ve gizlilik beklentisi taşır → ekran açıkken, görünür gösterge, kapalı varsayılan.
