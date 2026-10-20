# Plan listesi

Bekleyen maddeler. Uygulama, kullanıcı "planı yap" diyene kadar başlamaz; yapılanlar buradan silinir (kayıt için `docs/STATUS.md` ve git geçmişi). Kaynak: [EKSIKLER.md](EKSIKLER.md).

## Araç seçimi ekranı (`VehicleChooser`, 2026-10-20 telefon görüntüsü)

1. RideDeck adı ve logosu bu ekranda yok; yeni markayı ilk gören ekran burası. Üste logo ve ad (veya kısa slogan) eklenir.
2. Kartlar dikeyde ortalanmamış, üste yapışık; altta ekranın yaklaşık beşte biri boş kalıyor. İçerik dikeyde ortalanır.
3. Kart açıklamalarındaki gri metin zeminle düşük kontrastlı, güneş altında zor okunur. Metin rengi `textSecondary` yerine daha açık bir tona çekilir (kontrast en az 4.5:1).

Telefonda ve kaskla denenmesi gerekenler (kod hazır, doğrulama kullanıcıda; ayrıntı `docs/STATUS.md`): sesle komut tanıma kalitesi, kask mikrofonu, kaskın sesli komut düğmesi ve çift basış tetikleyicisi, "Hey RideDeck", `playFromSearch` ile ada göre çalma, bildirimden mesaja cevap, başka rota ve yolda durak.
