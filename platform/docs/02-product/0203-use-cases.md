# 0203 · Kullanım Senaryoları

| Alan | Değer |
|---|---|
| Doküman ID | 0203 |
| Proje | GeoLens Platform |
| Versiyon | 1.2 |
| Durum | Draft |
| Sahip | U2 AI Studio · Product |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0201, 0202, 0204, 0205 |

---

## 1. Amaç

Bu doküman GeoLens Platform'un P3 (ajans) ve P2 (KOBİ) kullanıcıları için tüm kullanım senaryolarını tanımlar. Her senaryo, 0202'deki yolculuk adımlarından türetilmiştir.

**Kullanım:** 0204 (PRD) gereksinimlerinin kaynağı, 0205 (MVP) kesit kararının girdisi.

---

## 2. UC Formatı

Her kullanım senaryosu şu yapıda tanımlanır:

```
UC-XX: [Başlık]
  Aktör:       [P2, P3, P2+P3]
  Aşama:       [Keşif/Kurulum/İlk Değer/Ritim/Genişleme/Savunuculuk]
  Tetikleyici: [Kullanıcı ne yapmak ister?]
  Ön koşul:    [Sistemin durumu]
  Ana akış:    [Adımlar]
  Başarı:      [Kullanıcı ne kazanır?]
  Öncelik:     [MVP/MVP+/V2]
```

---

## 3. Ortak Omurga (P2+P3)

0202 §3'teki yedi adımlı ortak omurgadan türetilen senaryolar. Tüm kullanıcılar için zorunlu temel akış.

### UC-01: Hesap Oluşturma

| Alan | Değer |
|---|---|
| **Aktör** | P2, P3 |
| **Aşama** | Kurulum |
| **Tetikleyici** | Kullanıcı GeoLens'i kullanmak istiyor |
| **Ön koşul** | GeoLens ana sayfası açık, kayıt formu görünür |
| **Ana akış** | 1. Kullanıcı e-posta adresini girer<br>2. Kullanıcı şifresini belirler<br>3. Sistem e-posta doğrulaması gönderir<br>4. Kullanıcı e-postasını doğrular<br>5. Sistem kiracı oluşturur ve panoya yönlendirir |
| **Başarı** | Kullanıcı panoya erişir. Ödeme bilgisi istenmez. |
| **Öncelik** | **MVP** |

### UC-02: Marka Tanımlama

| Alan | Değer |
|---|---|
| **Aktör** | P2, P3 |
| **Aşama** | Kurulum |
| **Tetikleyici** | Kullanıcı hangi markayı izleyeceğini belirlemek istiyor |
| **Ön koşul** | Kayıt tamamlanmış, kullanıcı ilk kez giriş yapıyor |
| **Ana akış** | 1. Sistem marka tanımlama sihirbazını açar<br>2. Kullanıcı marka adını girer<br>3. Kullanıcı alan adını girer (opsiyonel)<br>4. Kullanıcı pazarı seçer (TR varsayılan)<br>5. Kullanıcı rakip marka ekler (opsiyonel)<br>6. Sistem markayı kaydeder |
| **Başarı** | Marka profili oluşur. Prompt seti kurulumuna geçilir. |
| **Öncelik** | **MVP** |

### UC-03: Prompt Seti Kurulumu

| Alan | Değer |
|---|---|
| **Aktör** | P2, P3 |
| **Aşama** | Kurulum |
| **Tetikleyici** | Kullanıcı hangi sorulara yanıt aranacağını belirlemek istiyor |
| **Ön koşul** | Marka tanımlanmış |
| **Ana akış** | 1. Sistem sektör bazlı şablon kütüphanesini gösterir<br>2. Kullanıcı sektörünü seçer<br>3. Sistem varsayılan prompt setini önerir<br>4. Kullanıcı promptları düzenler/onaylar<br>5. Kullanıcı prompt tipini etiketler (markalı/kategori)<br>6. Sistem prompt setini kaydeder |
| **Başarı** | Prompt seti hazır. Kullanıcı hiçbir şey yazmak zorunda kalmamıştır (boş sayfa yok). |
| **Öncelik** | **MVP** |

### UC-04: Site Erişim Denetimi Çalıştırma

| Alan | Değer |
|---|---|
| **Aktör** | P2, P3 |
| **Aşama** | İlk Değer |
| **Tetikleyici** | Kullanıcı "hemen bir şey öğreneyim" istiyor |
| **Ön koşul** | Marka ve alan adı tanımlanmış |
| **Ana akış** | 1. Kullanıcı "Site Denetimi" butonuna tıklar<br>2. Sistem robots.txt, SSR durumu ve AI bot izinlerini kontrol eder<br>3. Sistem 30 saniye içinde sonuçları gösterir<br>4. Sistem iyileştirme önerileri listeler (varsa)<br>5. Kullanıcı önerileri uygulamak için dışarı yönlendirilir |
| **Başarı** | Kullanıcı ilk oturumunda somut bir kazanım elde eder. Kazara GPTBot engeli varsa hemen düzeltme şansı. |
| **Öncelik** | **MVP** — düşük maliyet, yüksek değer |

### UC-05: Ölçüm Çalıştırma

| Alan | Değer |
|---|---|
| **Aktör** | P2, P3 |
| **Aşama** | İlk Değer |
| **Tetikleyici** | Kullanıcı AI motorlarındaki görünürlüğünü ölçmek istiyor |
| **Ön koşul** | Marka ve prompt seti tanımlanmış |
| **Ana akış** | 1. Kullanıcı "Ölçüm Başlat" butonuna tıklar<br>2. Sistem ölçümü kuyruğa alır<br>3. Sistem ilerleme durumunu gösterir (hangi motor, kaç prompt kaldı)<br>4. Sistem ölçüm tamamlandığında kullanıcıya bildirim gönderir<br>5. Kullanıcı sonuçları görüntüleyebilir |
| **Başarı** | Ölçüm < 24 saat içinde tamamlanır. İlk skor kullanıcıya sunulur. |
| **Öncelik** | **MVP** |

### UC-06: Skor ve Güven Anı Görüntüleme

| Alan | Değer |
|---|---|
| **Aktör** | P2, P3 |
| **Aşama** | İlk Değer |
| **Tetikleyici** | Ölçüm tamamlandı, kullanıcı sonuçları görmek istiyor |
| **Ön koşul** | Ölçüm tamamlanmış, skor hesaplanmış |
| **Ana akış** | 1. Sistem genel skoru gösterir (0-100)<br>2. Sistem skorun yanında **fidelite etiketini** gösterir (Kademe 1/2/3)<br>3. Sistem **güven aralığını** gösterir<br>4. Sistem motor kırılımını gösterir (ChatGPT, Gemini, Perplexity)<br>5. Kullanıcı "Bu skor nasıl hesaplandı?" bağlantısına tıklayarak detaya iner<br>6. Sistem calculation_run detayını, faktör katkılarını ve kaynak listesini açar |
| **Başarı** | Kullanıcı skorun anlamlı olduğuna güvenir. Şeffaflık ilkesi deneyimle doğrulanır. |
| **Öncelik** | **MVP** |

### UC-07: Öneri Görüntüleme ve İşaretleme

| Alan | Değer |
|---|---|
| **Aktör** | P2, P3 |
| **Aşama** | İlk Değer / Ritim |
| **Tetikleyici** | Kullanıcı görünürlüğünü nasıl iyileştireceğini öğrenmek istiyor |
| **Ön koşul** | En az bir ölçüm tamamlanmış |
| **Ana akış** | 1. Sistem kanıt dereceli öneri listesini gösterir<br>2. Her önerinin yanında kanıt seviyesi belirtilir (Deneysel/Korelasyonel/Uygulayıcı)<br>3. Kullanıcı öneriyi "Uyguladım" veya "Reddettim" olarak işaretler<br>4. Sistem işaretlemeyi kaydeder<br>5. Sistem bir sonraki ölçümde işaretlenen önerinin etkisini karşılaştırır |
| **Başarı** | Aksiyon döngüsü başlar. Kullanıcı "ölçer, düzeltmez" eleştirisini deneyimlemez. |
| **Öncelik** | **MVP** |

---

## 4. P3 · Ajans Senaryoları

0202 §4'teki ajans yolculuğundan türetilen senaryolar.

### UC-08: Ajans Çalışma Alanı Oluşturma

| Alan | Değer |
|---|---|
| **Aktör** | P3 |
| **Aşama** | Kurulum |
| **Tetikleyici** | Ajans, GeoLens'i birden çok müşteri için kullanmak istiyor |
| **Ön koşul** | Kullanıcı Business paketine sahip |
| **Ana akış** | 1. Sistem "Ajans Çalışma Alanı" kurulum sihirbazını açar<br>2. Kullanıcı ajans adını ve logosunu girer<br>3. Kullanıcı ekip üyelerini davet eder (e-posta)<br>4. Kullanıcı rol ataması yapar (yönetici/editor/izleyici)<br>5. Sistem çalışma alanını oluşturur |
| **Başarı** | Ajansın çok müşterili yapısı hazır. Müşteri eklemeye hazır. |
| **Öncelik** | **MVP** |

### UC-09: Müşteri Ekleme

| Alan | Değer |
|---|---|
| **Aktör** | P3 |
| **Aşama** | Kurulum / Genişleme |
| **Tetikleyici** | Ajans yeni bir müşterisi için izleme başlatmak istiyor |
| **Ön koşul** | Ajans çalışma alanı oluşturulmuş |
| **Ana akış** | 1. Kullanıcı "Müşteri Ekle" butonuna tıklar<br>2. Kullanıcı müşteri marka adını ve alan adını girer<br>3. Sistem ortak omurga akışını tekrarlar (UC-02, UC-03)<br>4. Kullanıcı müşteriye özel prompt setini belirler<br>5. Kullanıcı raporlama tercihlerini ayarlar (PDF/BI/white-label)<br>6. Sistem müşteriyi çalışma alanına ekler |
| **Başarı** | Müşteri bağımsız olarak izlenebilir. Diğer müşterilerden veri izolasyonu sağlanır. |
| **Öncelik** | **MVP** |

### UC-10: White-label Rapor Oluşturma

| Alan | Değer |
|---|---|
| **Aktör** | P3 |
| **Aşama** | İlk Değer / Ritim |
| **Tetikleyici** | Ajans müşterisine kendi markalı raporu sunmak istiyor |
| **Ön koşul** | Müşteri için en az bir ölçüm tamamlanmış |
| **Ana akış** | 1. Kullanıcı "Rapor Oluştur" butonuna tıklar<br>2. Kullanıcı rapor şablonunu seçer (ajans logosu, renkleri ile)<br>3. Kullanıcı rapor kapsamını belirler (hangi motorlar, hangi promptlar)<br>4. Sistem PDF raporu üretir<br>5. Sistem raporu müşteriye e-posta ile gönderebilir (opsiyonel)<br>6. Kullanıcı raporu indirir/paylaşır |
| **Başarı** | Ajans, müşterisine kendi markasıyla profesyonel bir AI görünürlük raporu sunar. Rapor GAVF uyumludur. |
| **Öncelik** | **MVP** |

### UC-11: Zamanlanmış Rapor Ayarlama

| Alan | Değer |
|---|---|
| **Aktör** | P3 |
| **Aşama** | Ritim |
| **Tetikleyici** | Ajans müşteri raporlarını otomatikleştirmek istiyor |
| **Ön koşul** | En az bir müşteri eklenmiş, white-label şablonu hazır |
| **Ana akış** | 1. Kullanıcı "Zamanlanmış Rapor" ayarlarını açar<br>2. Kullanıcı sıklığı seçer (haftalık/aylık)<br>3. Kullanıcı teslim kanalını seçer (e-posta/Slack/BI)<br>4. Kullanıcı hedef müşterileri seçer<br>5. Sistem zamanlayıcıyı kaydeder<br>6. Sistem belirtilen sıklıkta raporları otomatik üretir ve gönderir |
| **Başarı** | Rapor üretimi tamamen otomatik. Ajansın operasyonel yükü sıfırlanır. |
| **Öncelik** | **MVP** |

### UC-12: Çok Müşteri Panorama Görüntüleme

| Alan | Değer |
|---|---|
| **Aktör** | P3 |
| **Aşama** | Ritim |
| **Tetikleyici** | Ajans tüm müşterilerinin durumunu tek ekranda görmek istiyor |
| **Ön koşul** | Birden çok müşteri eklenmiş, en az bir ölçüm tamamlanmış |
| **Ana akış** | 1. Kullanıcı "Panorama" görünümüne geçer<br>2. Sistem tüm müşterilerin skorlarını özet tabloda gösterir<br>3. Sistem kritik değişim yaşayan müşterileri vurgular<br>4. Kullanıcı bir müşteriye tıklayarak detaya iner<br>5. Sistem müşterinin detaylı panosunu açar |
| **Başarı** | Ajans, müşteri portföyünün tamamını bir bakışta değerlendirebilir. |
| **Öncelik** | **MVP+** |

### UC-13: Uyarı Kanalı ve Eşik Ayarlama

| Alan | Değer |
|---|---|
| **Aktör** | P3 |
| **Aşama** | Ritim |
| **Tetikleyici** | Ajans önemli değişimlerden haberdar olmak istiyor |
| **Ön koşul** | En az bir müşteri eklenmiş |
| **Ana akış** | 1. Kullanıcı "Uyarı Ayarları"nı açar<br>2. Kullanıcı uyarı kanalını seçer (Slack/e-posta/webhook)<br>3. Kullanıcı eşik değerlerini belirler (skor düşüşü %X, yeni kaynak, vb.)<br>4. Sistem uyarıları kaydeder<br>5. Sistem eşik aşıldığında belirtilen kanaldan bildirim gönderir<br>6. Her bildirim "yanlış alarm" geri bildirimi içerir (M11) |
| **Başarı** | Ajans kritik değişimleri kaçırmaz. Uyarı yorgunluğu minimize edilir. |
| **Öncelik** | **MVP** |

### UC-14: API/BI Entegrasyonu

| Alan | Değer |
|---|---|
| **Aktör** | P3 |
| **Aşama** | Genişleme |
| **Tetikleyici** | Ajans GeoLens verisini kendi raporlama sistemine bağlamak istiyor |
| **Ön koşul** | Business paketi aktif |
| **Ana akış** | 1. Kullanıcı "API Ayarları" sayfasını açar<br>2. Sistem API anahtarını gösterir<br>3. Kullanıcı API anahtarını kopyalar<br>4. Kullanıcı BI aracını (Looker/Tableau) bağlamak için yönergeleri takip eder<br>5. Kullanıcı erişim izinlerini belirler (hangi müşteriler/hangi veriler)<br>6. Sistem bağlantıyı doğrular |
| **Başarı** | GeoLens verisi ajansın mevcut raporlama altyapısına entegre olur. |
| **Öncelik** | **MVP+** |

### UC-15: Koltuk Yönetimi

| Alan | Değer |
|---|---|
| **Aktör** | P3 |
| **Aşama** | Genişleme |
| **Tetikleyici** | Ajans ekibine yeni üye eklemek veya çıkarmak istiyor |
| **Ön koşul** | Business paketi aktif |
| **Ana akış** | 1. Kullanıcı "Ekip Yönetimi" sayfasını açar<br>2. Kullanıcı mevcut koltuk kullanımını görür<br>3. Kullanıcı yeni üye davet eder veya üye çıkarır<br>4. Kullanıcı rol ataması yapar (yönetici/editör/izleyici)<br>5. Sistem değişikliği uygular ve kota bilgisini günceller |
| **Başarı** | Ekip yönetimi esnek ve kontrol edilebilir. |
| **Öncelik** | **MVP** |

### UC-16: Müşteri Arşivleme

| Alan | Değer |
|---|---|
| **Aktör** | P3 |
| **Aşama** | Genişleme |
| **Tetikleyici** | Müşteri ayrıldı; ajans pasif müşteriyi temizlemek istiyor |
| **Ön koşul** | Müşteri eklenmiş |
| **Ana akış** | 1. Kullanıcı müşteri sayfasında "Arşivle" seçeneğini seçer<br>2. Sistem onay ister (veri korunacak mı, tamamen silinecek mi)<br>3. Kullanıcı tercihini belirler<br>4. Sistem müşteriyi arşivler/kotaları serbest bırakır<br>5. Sistem onay mesajı gösterir |
| **Başarı** | Çalışma alanı temiz kalır. Boş müşteri kaydı birikmez. |
| **Öncelik** | **V2** |

---

## 5. P2 · KOBİ Senaryoları

0202 §5'teki KOBİ yolculuğundan türetilen senaryolar.

### UC-17: Haftalık Özet Görüntüleme

| Alan | Değer |
|---|---|
| **Aktör** | P2 |
| **Aşama** | Ritim |
| **Tetikleyici** | Kullanıcı haftalık durumunu öğrenmek istiyor |
| **Ön koşul** | En az bir ölçüm tamamlanmış |
| **Ana akış** | 1. Sistem haftalık e-posta özeti gönderir<br>2. Özet şunları içerir: bu hafta skor, geçen haftaya göre değişim, en önemli bulgu, tek aksiyon önerisi<br>3. Kullanıcı e-postadaki "Detaya Git" bağlantısına tıklar<br>4. Sistem panoda ilgili detayı açar<br>5. Kullanıcı öneriyi uygular/işaretler |
| **Başarı** | Kullanıcı panoya girmeden haftalık durumu anlar. İhtiyaç duyarsa detaya iner (WAT%). |
| **Öncelik** | **MVP** |

### UC-18: Benchmark Karşılaştırma

| Alan | Değer |
|---|---|
| **Aktör** | P2 |
| **Aşama** | İlk Değer / Ritim |
| **Tetikleyici** | Kullanıcı "benim skorum iyi mi" sorusuna yanıt arıyor |
| **Ön koşul** | En az bir ölçüm tamamlanmış |
| **Ana akış** | 1. Kullanıcı skor kartında benchmark göstergesini görür<br>2. Sistem "Sektöründe tipik aralık: X-Y" bilgisini gösterir<br>3. Kullanıcı "Detaylı Karşılaştırma" butonuna tıklar<br>4. Sistem anonim toplulaştırılmış kıyası gösterir (sektör/boyut bazlı)<br>5. Kullanıcı kendi konumunu görür |
| **Başarı** | Kullanıcı skorunu bağlam içinde değerlendirebilir. Düşük skor moral bozmaz, aksiyon yönlendirir. |
| **Öncelik** | **MVP+** |

### UC-19: Rakip Ekleme ve Karşılaştırma

| Alan | Değer |
|---|---|
| **Aktör** | P2 |
| **Aşama** | Genişleme |
| **Tetikleyici** | Kullanıcı rakiplerine göre durumunu görmek istiyor |
| **Ön koşul** | En az bir ölçüm tamamlanmış |
| **Ana akış** | 1. Kullanıcı "Rakip Ekle" butonuna tıklar<br>2. Kullanıcı rakip marka adını girer<br>3. Sistem rakip için prompt setini kopyalar (kendi markasından)<br>4. Sistem rakip ölçümünü başlatır (aynı motorlar, aynı promptlar)<br>5. Sistem karşılaştırmalı skor görünümünü açar |
| **Başarı** | Kullanıcı kendisini rakipleriyle aynı koşullarda karşılaştırabilir. |
| **Öncelik** | **MVP+** |

### UC-20: Pro'dan Business'a Paket Yükseltme

| Alan | Değer |
|---|---|
| **Aktör** | P2 (büyüyen) |
| **Aşama** | Genişleme |
| **Tetikleyici** | Kullanıcı ikinci marka eklemek veya API erişimi almak istiyor |
| **Ön koşul** | Pro paketi aktif |
| **Ana akış** | 1. Kullanıcı bir özelliğe erişmeye çalışır (ikinci marka, API, vs.)<br>2. Sistem paket yükseltme önerisi gösterir<br>3. Kullanıcı yükseltme detaylarını inceler<br>4. Kullanıcı onaylar<br>5. Sistem paketi Business'a yükseltir ve yeni özellikleri açar |
| **Başarı** | Sorunsuz paket geçişi. Kullanıcı veri kaybı yaşamaz. |
| **Öncelik** | **MVP** (özellik erişim kontrolü ile birlikte) |

### UC-21: Öneri Etki Takibi

| Alan | Değer |
|---|---|
| **Aktör** | P2 |
| **Aşama** | Genişleme |
| **Tetikleyici** | Kullanıcı uyguladığı önerinin işe yarayıp yaramadığını görmek istiyor |
| **Ön koşul** | Kullanıcı en az bir öneriyi "Uyguladım" olarak işaretlemiş |
| **Ana akış** | 1. Kullanıcı "Öneri Geçmişi" sayfasını açar<br>2. Sistem uygulanan önerileri ve sonraki ölçüm sonuçlarını gösterir<br>3. Sistem skor değişimini görselleştirir (öneri öncesi/sonrası)<br>4. Kullanıcı "tekrar uygula/reddet" kararı verebilir |
| **Başarı** | Kullanıcı hangi aksiyonun işe yaradığını görür. Döngü kopmaz. |
| **Öncelik** | **MVP+** |

---

## 6. Senaryo-Matris Eşlemesi

0201 §4 ihtiyaç matrisine göre senaryoların kritiklik dağılımı:

| Yetenek | Kritik Persona | Kapsayan UC'ler |
|---------|:--------------:|-----------------|
| Çok motorlu izleme | P2+P3 | UC-05, UC-06 |
| Alıntı/kaynak analizi | P3 | UC-06 |
| Kanıt dereceli öneriler | P2+P3 | UC-07, UC-21 |
| Rakip kıyası | P3 | UC-19 |
| Trend ve uyarılar | P3 | UC-13, UC-17 |
| White-label / dışa aktarım | **P3** | UC-10, UC-11 |
| SSO, denetim izi | P1 (ileri) | — |
| API / BI entegrasyonu | P3 | UC-14 |
| TR dil/prompt setleri | P2+P3 | UC-03 |

---

## 7. MVP Sınıflandırması

| Öncelik | Sayı | UC'ler |
|:-------:|:----:|--------|
| **MVP** | 15 | UC-01, UC-02, UC-03, UC-04, UC-05, UC-06, UC-07, UC-08, UC-09, UC-10, UC-11, UC-13, UC-15, UC-17, UC-20 |
| **MVP+** | 5 | UC-12, UC-14, UC-18, UC-19, UC-21 |
| **V2** | 1 | UC-16 |
| **Toplam** | **21** | |

---

## 8. GeoLens İçin Çıkarımlar

1. **21 kullanım senaryosu** tanımlanmıştır: 15'i MVP, 5'i MVP+, 1'i V2.
2. **P3 ağırlıklıdır:** 9 senaryo yalnızca P3'e özgü. Ajans segmentinin ürün yüzeyi KOBİ'den daha geniştir.
3. **Specification bağlantısı:** White-label rapor (UC-10) ve zamanlanmış rapor (UC-11), GAVF standardının ticarileştiği noktalardır. Raporun altında "GAVF uyumlu" ibaresi, ajansın fiyatlandırma gücünü artırır.
4. **Güven anı:** UC-06 (skor görüntüleme), ürünün vaadinin kanıtlandığı en kritik senaryodur. Fidelite etiketi burada devreye girer.
5. **Filtre bağlantısı:** MVP senaryolarının tekrarlanabilirliği **F2** (ölçek) ve uzun vadeli kullanım sürekliliği **F1** (5 yıl) filtrelerini karşılar.

---

## 9. Açık Sorular

| ID | Soru | Durum |
|----|------|-------|
| O-1 | Benchmark kıyas verisinin gizlilik sınırları | ⏳ AVIP D-60: ≥5 kiracı eşiği devralındı. |
| O-2 | UC-10 white-label rapor şablonu özelleştirme derecesi | ⏳ MVP'de logo+renk yeterli. Tam domain özelleştirmesi HT1'de. |

### Devralınan AVIP Kararları

| ID | Karar | Kaynak |
|----|-------|--------|
| **D-08** | **Benchmark (UC-12) MVP dışı, HT2'ye bırakıldı.** PO 21.07.2026. | AVIP 0203 O-1 |
| **D-24** | **Self-serve ödeme (UC-26):** HT2 — genel açılışla. Pilotta arka ofis. PO 21.07.2026. | AVIP 0203 O-2 |
| **D-09** | **Rol modeli:** Yönetici+üye yeterli. İzleyici MVP dışı. PO 21.07.2026. | AVIP 0203 O-3 |
| **D-10** | **API kapsamı (UC-21):** Okuma-yalnız başlar. Yazma HT1'de. PO 21.07.2026. | AVIP 0203 O-4 |

---

## Kaynaklar

- 0201 User Personas — aktör seti, ihtiyaç matrisi
- 0202 User Journeys — yolculuk adımları, sürtünme haritası
- 0102 Rekabet Analizi — fidelite kuralı, white-label boşluğu
- 0101 Pazar Analizi — site denetim fırsatı, uygulama açığı

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: GeoLens Platform kullanım senaryoları. P3 (ajans) ve P2 (KOBİ) odaklı. 21 UC tanımı (UC-01–UC-21), 7 UC'lik ortak omurga, 9 P3 senaryosu, 5 P2 senaryosu. MVP/MVP+/V2 sınıflandırması. |
| 1.1 | 22.07.2026 | Tutarlılık düzeltmesi: changelog'daki UC sayısı 20'den 21'e güncellendi (tabloyla uyum). |
| 1.2 | 22.07.2026 | AVIP kapalı kararları taşındı: D-08 (benchmark HT2), D-24 (self-serve ödeme HT2), D-09 (rol modeli), D-10 (API okuma-yalnız). Devralınan Kararlar eklendi. |
