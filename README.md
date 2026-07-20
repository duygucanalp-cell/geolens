# AI Visibility Intelligence Platform (AVIP)

> Kurumların yapay zekâ destekli arama ve yanıt motorlarında nasıl temsil edildiklerini ölçmelerini, anlamalarını ve iyileştirmelerini sağlayan platform.

## Nedir?

Geleneksel SEO platformları Google sıralamalarına odaklanırken AVIP, büyük dil modelleri (LLM) ve AI yanıt motorlarındaki görünürlüğe odaklanır: **ChatGPT, Gemini, Claude, Perplexity, Copilot, Grok** ve gelecekte ortaya çıkacak sistemler.

Kurumlar artık yalnızca arama sıralamaları için rekabet etmiyor; AI sistemlerinin önerdiği, alıntıladığı ve özetlediği güvenilir kaynaklar olmak için rekabet ediyor. AVIP bu yeni dijital görünürlük biçimini izlemek, analiz etmek ve sürekli iyileştirmek için gereken zekâyı sağlar.

## Vizyon

AI görünürlüğü ölçümünde küresel standart platform olmak. Nihai hedef: AI araması için "Google Analytics" konumuna ulaşmak.

## Temel Özellikler

- **Çoklu Motor Desteği**: ChatGPT, Gemini, Perplexity ve daha fazlası
- **Açıklanabilir Skorlar**: Her skor calculation_run_id ile izlenebilir
- **Alıntı Analizi**: AI yanıtlarını etkileyen kaynakları görünür kılar
- **Rakip Kıyası**: Aynı prompt setinde markalar arası görünürlük farkı
- **Uyarılar ve Öneriler**: Görünürlüğü artıracak veriye dayalı öneriler
- **Kurumsal Hazırlık**: Tek şema + RLS ile çok kiracılı izolasyon

## Teknoloji Yığını

| Katman | Seçim |
|--------|-------|
| Backend | Go (modüler monolit + işçi havuzu) |
| Veritabanı | PostgreSQL 16+ (RLS ile çok kiracılı) |
| Kuyruk/Önbellek | Redis 7+ (Streams + tüketici grupları) |
| Depolama | S3-uyumlu arayüz |
| Frontend | React + TypeScript SPA |
| Gözlemlenebilirlik | OpenTelemetry + Prometheus |

Detaylı teknik kararlar: [docs/0304-technology-selection.md](docs/0304-technology-selection.md)

## Proje Yapısı

```
ai_visibility/
├── docs/                    # Tüm dokümanlar (kaynak gerçek)
│   ├── 000x-*.md            # Meta: vizyon, sorun, yönetişim, metrikler
│   ├── 010x-*.md            # Pazar araştırması
│   ├── 020x-*.md            # Ürün: personas, PRD, MVP kapsamı
│   ├── 030x-*.md            # Mimari: alan modeli, DB, teknoloji, API
│   ├── 040x-*.md            # Mühendislik: süreç, ortamlar, CI/CD, test
│   └── adr/                 # Mimari Karar Kayıtları (ADR)
└── AGENTS.md                # Ajan yönlendirmeleri
```

## Dokümantasyon

Tüm dokümanlar Türkçe yazılmıştır ve `NNNN-title.md` formatını izler. Her dokümanın başında künye tablosu, sonunda changelog bulunur.

| Grup | Kapsam |
|------|--------|
| 000x | Vizyon, sorun bildirimi, hedefler, başarı metrikleri, sözlük, marka, yönetişim |
| 010x | GEO manzarası, AI arama manzarası, rakip analizi, SWOT, pazar fırsatı |
| 020x | Kullanıcı personaları, yolculuk, senaryolar, PRD, MVP kapsamı, yol haritası |
| 030x | Alan modeli, veritabanı tasarımı, teknoloji seçimi, servisler, API, işler |
| 040x | Geliştirme süreci, ortamlar, CI/CD, test stratejisi, güvenlik, sürüm |

## Mimari Kararlar (ADR)

Kabul edilmiş kararlar:

- **ADR-001**: Go backend, PostgreSQL, Redis, S3
- **ADR-002**: React + TypeScript SPA (Flutter mobil pencereye rezerve)
- **ADR-003**: Modüler monolit + işçi havuzu
- **ADR-004**: Tek şema + RLS ile çok kiracılı izolasyon
- **ADR-005**: Redis Streams + tüketici grupları

ADR dosyaları: `docs/adr/`

## Mevcut Durum

Proje **Faz 0 (Tasarım)** aşamasındadır. Tüm mimari kararlar alınmış, doküman seti hazırlanmıştır. Uygulama geliştirme Faz 4'te başlayacaktır.

### Tamamlanan Faz 0 Teslimatları

| ID | Doküman | Durum |
|----|---------|-------|
| 0000 | Master Plan | Onaylandı |
| 0001 | Vizyon | Onaylandı |
| 0002 | Sorun Bildirimi | Onaylandı |
| 0003 | Hedefler & Hedef Olmayanlar | Onaylandı |
| 0004 | Başarı Metrikleri | Onaylandı |
| 0005 | Sözlük | Onaylandı |
| 0006 | Marka & Alan Adı | Onaylandı |
| 0007 | Yönetişim | Onaylandı |

## Katkıda Bulunma

Katkı süreci [0007-governance.md](docs/0007-governance.md) ile tanımlanmıştır. Temel kurallar:

1. Tüm değişiklikler PR ile gelir; en az bir onay gerekir
2. PR küçük ve tek amaçlı olmalıdır
3. Doküman değişiklikleri changelog güncellemesi içerir
4. Mimari kararlar ADR olarak `docs/adr/` altına yazılır
5. Kararlar iki tiptir: Tip 1 (geri döndürülmesi pahalı) ve Tip 2 (geri döndürülebilir)

## Roller

| Rol | Sorumluluk |
|-----|------------|
| Ürün Sahibi (PO) | Kapsam, öncelik ve onay mercii |
| Teknik Lider (TL) | Mimari bütünlük, teknik kalite |
| Analist (AN) | Doküman üretimi, izlenebilirlik |
| Geliştirici (DEV) | Uygulama, test |
| Paydaş (PY) | Dış bağımlılıklar, ortaklıklar |

## Lisans

Bu proje henüz kamuoyuna açılmamıştır. Tüm hakları saklıdır.

---

*Bu dosya, [0001-vision.md](docs/0001-vision.md) ve ilişkili dokümanlardan türetilmiştir.*
