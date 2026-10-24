# Sesli asistan ölçüm rutini

Amaç: asistanın gerçek ortamda ne kadarını anladığını sayıyla görmek. Birim testleri sahte tanıyıcıyla çalışır; gerçek başarı yalnızca telefonda ölçülür. Sonuçlar `docs/STATUS.md`'ye işlenir.

## Hazırlık
- Ayarlar > Sürüş'te **Sesli komut geçmişi** açık olsun (anlaşılmayanlar yalnızca bu açıkken tutulur).
- Telefon şarjda ya da %50 üstünde, ısı koruması kapalı değil ama sıcak değil.
- Her ortam için aynı 20 cümleyi, aynı sırayla söyle. Her cümleden sonra asistanın ne yaptığını **doğru / yanlış / anlamadı** diye işaretle.

## Ortamlar
1. **Sessiz oda**, telefon elde, normal ses.
2. **Rüzgârlı**: açık havada ya da fan karşısında, telefon gidonda.
3. **Kasklı**: Bluetooth kask mikrofonu (Ayarlar'da kask mikrofonu açık), motor rölantide.

## 20 cümlelik liste
Doğal söylenişler; bazıları kalıba tam uymuyor (hedef bu).

| # | Söylenen | Beklenen |
|---|---|---|
| 1 | "eve git" | eve rota |
| 2 | "bugün hava nasıl olacak" | hava durumu |
| 3 | "yarın yağmur yağacak mı" | yarının havası |
| 4 | "müziği biraz kıs" | sesi kısar |
| 5 | "müziği kıs ve eve git" | iki komut |
| 6 | "bu şarkıyı geç" | sonraki şarkı |
| 7 | "kaç dakika sonra varırım" | kalan süre |
| 8 | "yakında bir benzinlik var mı" | durak arama |
| 9 | "Ali'yi ara" (sonra) "onu ara" | iki kez Ali |
| 10 | "Kadıköy'e git" (sonra) "hayır Kadıköy değil Moda" | düzeltme |
| 11 | "annemi ara" | ilk seferde kim olduğunu sorar |
| 12 | "Ayşe'ye mesaj at" → mesajı söyle → "evet" | SMS gider |
| 13 | "bir de eve uğra" | ara durak |
| 14 | "yirmi dakika sonra hatırlat" | bildirim kurulur |
| 15 | "şu an hızım kaç" | hız |
| 16 | "pil kaç kalmış" | pil |
| 17 | "tekrar et" | son cevabı yineler |
| 18 | "geri al" | son komutun tersi |
| 19 | "haritayı bir aç" | harita |
| 20 | "sus" | önerileri kapatır |

## Sonuç tablosu
Her ortam için: doğru sayısı / 20, yanlış eylem sayısı (komut olmayan şeyin komut sanılması dahil), "anlamadı" sayısı, ortalama gecikme (konuşma bitişinden sonuca, saniye).

| Ortam | Doğru | Yanlış eylem | Anlamadı | Gecikme |
|---|---|---|---|---|
| Sessiz oda | | | | |
| Rüzgârlı | | | | |
| Kasklı | | | | |

## Gerçek cümleleri derleme eklemek
Ayarlar > Kurulum > **Anlaşılmayanları dışa aktar** paylaşım menüsüyle anlaşılmayan cümleleri metin olarak verir (ses kaydı yok). Bunları `VoiceNaturalTest`'e doğru komutlarıyla ekle; kapsam büyüdükçe doğal varyant oranı yükselir.

## Ek ölçümler
- Hey RideDeck (kapalı varsayılan): 5 dakikalık dinlemede pil ve sıcaklık, yanlış uyanma sayısı (Ayarlar'da gösterilen sayaç).
- Asistan önerileri (kapalı varsayılan): bir sürüşte kaç öneri geldi, kaçı yerindeydi, "sus" gerekti mi.
- Sürekli konuşma modu: cevaptan sonra 5 sn dinlemesi rahatsız etti mi, rüzgârda kendiliğinden komut yakaladı mı.
