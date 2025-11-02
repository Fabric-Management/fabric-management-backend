# 🧵 Pamuk Fiber Oluşturma - Frontend Form Akış Diyagramı

## 📋 Genel Bakış

Kullanıcı bir "Pamuk" (Cotton) fiber oluşturmak istediğinde, önce Material (FIBER type) oluşturulmalı, sonra Fiber oluşturulmalıdır.

---

## 🔄 Form Akış Diyagramı

```mermaid
flowchart TD
    Start([Kullanıcı: Pamuk Fiber Oluştur]) --> CheckMaterial{Material<br/>Var mı?}
    
    CheckMaterial -->|Hayır| Step1[1. Material Oluştur Formu]
    CheckMaterial -->|Evet| Step2[2. Fiber Oluştur Formu]
    
    Step1 --> MaterialForm[Material Form<br/>━━━━━━━━━━<br/>Type: FIBER<br/>Unit: kg<br/>━━━━━━━━━━]
    
    MaterialForm --> MaterialValidate{Validasyon}
    MaterialValidate -->|Geçersiz| MaterialForm
    MaterialValidate -->|Geçerli| MaterialSubmit[POST /api/materials]
    
    MaterialSubmit --> MaterialSuccess{Material<br/>Oluşturuldu?}
    MaterialSuccess -->|Hata| MaterialError[Hata Mesajı<br/>Göster]
    MaterialSuccess -->|Başarılı| MaterialCreated[✅ Material Created<br/>ID: xxx]
    
    MaterialError --> MaterialForm
    MaterialCreated --> Step2
    
    Step2 --> FiberForm[Fiber Form<br/>━━━━━━━━━━<br/>Material ID: xxx<br/>Category: Natural<br/>Name: Cotton<br/>━━━━━━━━━━]
    
    FiberForm --> LoadCategories[GET /api/fibers/categories]
    LoadCategories --> CategoryDropdown[Kategori Dropdown<br/>• Natural<br/>• Synthetic<br/>• Artificial]
    
    CategoryDropdown --> FillForm[Doldur:<br/>• Fiber Name: Cotton<br/>• Fiber Grade: Optional<br/>• Fineness: Optional<br/>• Length: Optional<br/>• Strength: Optional<br/>• Elongation: Optional<br/>• Remarks: Optional]
    
    FillForm --> FiberValidate{Validasyon}
    FiberValidate -->|Geçersiz| FiberForm
    FiberValidate -->|Geçerli| FiberSubmit[POST /api/fibers]
    
    FiberSubmit --> FiberSuccess{Fiber<br/>Oluşturuldu?}
    FiberSuccess -->|Hata| FiberError[Hata Mesajı<br/>Göster]
    FiberSuccess -->|Başarılı| End([✅ Cotton Fiber<br/>Başarıyla Oluşturuldu!])
    
    FiberError --> FiberForm
    
    style Start fill:#e1f5ff
    style End fill:#d4edda
    style MaterialForm fill:#fff3cd
    style FiberForm fill:#fff3cd
    style MaterialError fill:#f8d7da
    style FiberError fill:#f8d7da
    style MaterialCreated fill:#d4edda
```

---

## 📝 Detaylı Form Adımları

### **ADIM 1: Material Kontrolü ve Oluşturma**

```
┌─────────────────────────────────────────────────────────────┐
│  🔍 ADIM 1: Material Kontrolü                              │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  1.1. Material Var mı Kontrol Et                            │
│       GET /api/materials?type=FIBER&query=cotton            │
│                                                               │
│      ┌─────────────────────────────────────────┐            │
│      │  Sonuçlar:                              │            │
│      │  • Material bulundu? → ADIM 2'ye git   │            │
│      │  • Material yok? → Material Form aç    │            │
│      └─────────────────────────────────────────┘            │
│                                                               │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│  📝 Material Oluştur Formu                                  │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ Material Type *                                      │   │
│  │ ╭───────────────────────────────────────────╮       │   │
│  │ │ FIBER ▼                                    │       │   │
│  │ ╰───────────────────────────────────────────╯       │   │
│  │ [YARN, FABRIC, CHEMICAL, CONSUMABLE] (disabled)     │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                               │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ Unit *                                               │   │
│  │ ╭───────────────────────────────────────────╮       │   │
│  │ │ kg ▼                                        │       │   │
│  │ ╰───────────────────────────────────────────╯       │   │
│  │ [kg, ton, m, m², piece, liter]                      │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                               │
│  [İptal]  [Material Oluştur →]                              │
│                                                               │
│  * Zorunlu Alanlar                                          │
└─────────────────────────────────────────────────────────────┘
```

### **ADIM 2: Fiber Oluşturma**

```
┌─────────────────────────────────────────────────────────────┐
│  📝 Fiber Oluştur Formu                                      │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ Material * (Auto-filled veya Seç)                   │   │
│  │ ╭───────────────────────────────────────────╮       │   │
│  │ │ Cotton (FIBER) - ID: xxx ▼                 │       │   │
│  │ ╰───────────────────────────────────────────╯       │   │
│  │ Material Type: FIBER, Unit: kg ✅                   │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                               │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ Fiber Category *                                      │   │
│  │ ╭───────────────────────────────────────────╮       │   │
│  │ │ Natural ▼                                   │       │   │
│  │ ╰───────────────────────────────────────────╯       │   │
│  │ [Natural, Synthetic, Artificial, ...]                │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                               │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ Fiber Name *                                         │   │
│  │ ╭───────────────────────────────────────────╮       │   │
│  │ │ Cotton                                      │       │   │
│  │ ╰───────────────────────────────────────────╯       │   │
│  │ Örnek: "Cotton", "Premium Cotton", "Organic Cotton" │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                               │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ Fiber ISO Code (Opsiyonel)                          │   │
│  │ ╭───────────────────────────────────────────╮       │   │
│  │ │ CO ▼                                        │       │   │
│  │ ╰───────────────────────────────────────────╯       │   │
│  │ NOT: Pure fiber oluşturmak için ISO Code gerekli    │   │
│  │ ama sistem tarafından bloklanır (sadece blended)     │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                               │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ Teknik Özellikler (Opsiyonel)                       │   │
│  ├─────────────────────────────────────────────────────┤   │
│  │ Fiber Grade:    [_________]                         │   │
│  │                 Örn: "Premium", "Grade A"            │   │
│  │                                                      │   │
│  │ Fineness:      [_________] dtex                    │   │
│  │                 Örn: 1.5, 2.0                        │   │
│  │                                                      │   │
│  │ Length:         [_________] mm                      │   │
│  │                 Örn: 25, 30                         │   │
│  │                                                      │   │
│  │ Strength:       [_________] cN/dtex                 │   │
│  │                 Örn: 3.5, 4.0                       │   │
│  │                                                      │   │
│  │ Elongation:     [_________] %                       │   │
│  │                 Örn: 5.0, 6.5                       │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                               │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ Remarks (Opsiyonel)                                  │   │
│  │ ╭───────────────────────────────────────────╮       │   │
│  │ │                                             │       │   │
│  │ │                                             │       │   │
│  │ ╰───────────────────────────────────────────╯       │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                               │
│  [← Geri]  [İptal]  [Fiber Oluştur →]                       │
│                                                               │
│  * Zorunlu Alanlar                                          │
└─────────────────────────────────────────────────────────────┘
```

---

## 🔄 İki Yol: Akıllı vs Manuel

### **YOL 1: Akıllı Akış (Önerilen)**

```
Kullanıcı: "Pamuk fiber oluştur"
    ↓
[AI Assistant veya Smart Form]
    ↓
1. Otomatik Material kontrolü
2. Material yoksa → Material oluştur (FIBER, kg)
3. Otomatik Category seçimi (Cotton → Natural)
4. Otomatik Fiber Name (Cotton)
5. Kullanıcı onayı → Fiber oluştur
```

### **YOL 2: Manuel Akış (Geleneksel)**

```
Kullanıcı: "Yeni Fiber Oluştur" butonuna tıkla
    ↓
1. Material seç/oluştur
2. Category seç
3. Form alanlarını doldur
4. Submit
```

---

## ✅ Validasyon Kuralları

### **Material Validasyonu:**
- ✅ Material Type: `FIBER` olmalı
- ✅ Unit: Boş olamaz (örn: "kg", "ton")

### **Fiber Validasyonu:**
- ✅ Material ID: Geçerli UUID, Material mevcut olmalı
- ✅ Material Type: Material'ın type'ı `FIBER` olmalı
- ✅ Material: Daha önce Fiber ile ilişkilendirilmemiş olmalı (unique constraint)
- ✅ Fiber Category ID: Geçerli UUID, Category mevcut olmalı
- ✅ Fiber Name: Boş olamaz, min 2 karakter
- ⚠️ ISO Code: Eğer verilirse, pure fiber oluşturma denenir ve sistem tarafından reddedilir

### **Teknik Özellikler (Opsiyonel ama geçerli olmalı):**
- Fineness: Pozitif sayı (0 < fineness)
- Length: Pozitif sayı (0 < lengthMm)
- Strength: Pozitif sayı (0 < strengthCndTex)
- Elongation: 0-100 arası (0 ≤ elongationPercent ≤ 100)

---

## 🎨 UI/UX Önerileri

### **1. Material Seçimi:**
- **Dropdown** ile mevcut Material'ları göster
- **Arama** özelliği ekle
- Material yoksa **"+ Yeni Material Oluştur"** butonu göster
- Material seçildiğinde **Type ve Unit bilgisini** göster

### **2. Category Seçimi:**
- **Dropdown** ile kategorileri göster
- Her kategori için **açıklama** ekle
- **Cotton** için otomatik **"Natural"** seçimi öner

### **3. Fiber Name:**
- **Auto-suggest** ekle (Material adından türet)
- **Real-time validation** (min 2 karakter)
- **Duplicate kontrolü** (aynı isimde fiber var mı?)

### **4. Teknik Özellikler:**
- **Collapsible section** (başlangıçta kapalı)
- **Unit bilgileri** göster (dtex, mm, cN/dtex, %)
- **Input mask** ekle (örn: sadece sayı kabul et)

### **5. Form Durumları:**
- **Loading state**: API çağrıları sırasında
- **Error state**: Validasyon hatalarını göster
- **Success state**: Başarılı oluşturma mesajı + yeni fiber detayları

---

## 🔌 API Endpoints

### **Material Oluştur:**
```http
POST /api/production/materials
Content-Type: application/json

{
  "materialType": "FIBER",
  "unit": "kg"
}
```

### **Fiber Categories Listesi:**
```http
GET /api/production/fibers/categories
```

### **Fiber Oluştur:**
```http
POST /api/production/fibers
Content-Type: application/json

{
  "materialId": "320ce0ab-5155-45fb-b0cc-aa8678f3dc80",
  "fiberCategoryId": "550e8400-e29b-41d4-a716-446655440000",
  "fiberName": "Cotton",
  "fiberGrade": "Premium",
  "fineness": 1.5,
  "lengthMm": 30.0,
  "strengthCndTex": 3.8,
  "elongationPercent": 5.5,
  "remarks": "Premium quality cotton fiber"
}
```

---

## 📱 Responsive Design Önerileri

### **Desktop (>1024px):**
- **2 kolon layout**: Sol tarafta form, sağ tarafta önizleme
- **Sidebar** ile adımlar göster (Step 1/2)

### **Tablet (768px - 1024px):**
- **Single column**: Form full width
- **Sticky header** ile progress bar

### **Mobile (<768px):**
- **Single column**: Form full width
- **Bottom sheet** ile adımlar
- **Floating action button** ile submit

---

## 🚀 İleri Seviye Özellikler

1. **Auto-save Draft**: Form verilerini localStorage'da sakla
2. **Template System**: Daha önce oluşturulan fiber'lerden template oluştur
3. **Bulk Import**: CSV/Excel ile toplu fiber oluşturma
4. **AI Assistant Integration**: Form doldurma yardımcısı
5. **Real-time Validation**: Her alan değiştiğinde backend'e validate isteği gönder

---

## ⚠️ Hata Senaryoları

### **1. Material Bulunamadı:**
```
❌ Material not found: xxx
Possible reasons:
- Material ID is incorrect
- Material belongs to another tenant
- Material was deleted

[Yeni Material Oluştur] [Material Ara]
```

### **2. Material Type Hatası:**
```
❌ Material type must be FIBER, got: YARN

[Farklı Material Seç]
```

### **3. Material Zaten Fiber'e Sahip:**
```
❌ Material already has fiber details. 
Each material can only have one fiber.

[Farklı Material Seç] [Mevcut Fiber'i Görüntüle]
```

### **4. Category Bulunamadı:**
```
❌ Fiber category not found: xxx

[Kategorileri Yenile]
```

---

## 📊 Örnek Başarılı Response

```json
{
  "success": true,
  "data": {
    "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "uid": "FIB-2025-001",
    "fiberName": "Cotton",
    "fiberGrade": "Premium",
    "materialId": "320ce0ab-5155-45fb-b0cc-aa8678f3dc80",
    "fiberCategoryId": "550e8400-e29b-41d4-a716-446655440000",
    "fiberCategoryName": "Natural",
    "status": "NEW",
    "createdAt": "2025-11-02T10:00:00Z"
  },
  "message": "Fiber created successfully"
}
```

---

## 🎯 Sonuç

Bu diyagram, kullanıcının "Pamuk fiber oluştur" işlemini tamamlaması için gereken tüm adımları ve form yapılarını gösterir. Form, Material oluşturma ve Fiber oluşturma adımlarını içerir ve kullanıcı dostu bir deneyim sunar.

