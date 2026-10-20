# Plan listesi

Bekleyen maddeler. Uygulama, kullanıcı "planı yap" diyene kadar başlamaz; yapılanlar buradan silinir (kayıt için `docs/STATUS.md` ve git geçmişi). Kaynak: [EKSIKLER.md](EKSIKLER.md).

## Araç seçimi ekranı (`VehicleChooser`, 2026-10-20 telefon görüntüsü)

1. RideDeck adı ve logosu bu ekranda yok; yeni markayı ilk gören ekran burası. Üste logo ve ad (veya kısa slogan) eklenir.
2. Kartlar dikeyde ortalanmamış, üste yapışık; altta ekranın yaklaşık beşte biri boş kalıyor. İçerik dikeyde ortalanır.
3. Kart açıklamalarındaki gri metin zeminle düşük kontrastlı, güneş altında zor okunur. Metin rengi `textSecondary` yerine daha açık bir tona çekilir (kontrast en az 4.5:1).

4. Araç kartına dokununca seçim hemen uygulanmaz: kart seçili olur (vurgu), altta "Devam et" düğmesi çıkar; seçim ancak ona basınca kaydedilir. Yanlış dokunuşta başka karta geçilebilir. Düğme eldivene uygun büyüklükte olur.
5. Araç seçimi bittiğinde Ayarlar > Kurulum artık otomatik açılmaz (`DashboardScreen.kt` içindeki `showSettings = !locationGranted`). Yerine kısa bir izin ekranı gelir: konum, bildirim erişimi, telefon, SMS satırları ve her biri için "İzin ver"; altta "Sonra" ile atlanır ve dashboard hemen görünür. Atlanan izinler Ayarlar > Kurulum'da durur.

## Dashboard (2026-10-20 telefon görüntüsü)

6. Navigasyon kartındaki mavi "Harita ve rota" düğmesi (`NavCard.kt`, `nav_map_and_route`) kaldırılır; kartın tamamı zaten `onOpenMap` ile açılıyor. Rota yokken kartta başlık, Ev/İş/Park kısayolları ve mini harita kalır; boşalan yer harita çizimine verilir. Kartın dokunulabilir olduğu anlaşılsın diye başlığın yanına küçük bir ok (›) konabilir. Kullanılmayan `nav_map_and_route` metni TR ve EN'den silinir.
7. Hız kartında (`SpeedCard.kt`, `gps_waiting`) konum izni yokken de "GPS bekleniyor…" yazıyor; izin eksikliğini söylemiyor. İzin yokken metin "Konum izni gerekli" olur ve yanında "İzin ver" düğmesi çıkar (izin isteği ya da sistem ayarı); izin varken GPS gerçekten bekleniyorsa eski metin kalır. `resumeTick` ile izin verilince kart kendiliğinden güncellenir. TR ve EN metinleri eklenir.
8. Dashboard'da RideDeck logosu yok. Sol dock'un üstüne (simgelerin üstü, saatin karşı ucu) küçük logo (`docs/logo.svg`'den çıkarılmış vektör drawable, yaklaşık 40 dp) eklenir; dokununca bir şey yapmaz. Dikey ekranda (bisiklet) alt dock'ta yer yoksa logo gizlenir. Telefon yüksekliği ~360 dp olduğundan dock simgeleri küçülmemeli; sığmıyorsa logo konmaz.

Telefonda ve kaskla denenmesi gerekenler (kod hazır, doğrulama kullanıcıda; ayrıntı `docs/STATUS.md`): sesle komut tanıma kalitesi, kask mikrofonu, kaskın sesli komut düğmesi ve çift basış tetikleyicisi, "Hey RideDeck", `playFromSearch` ile ada göre çalma, bildirimden mesaja cevap, başka rota ve yolda durak.
