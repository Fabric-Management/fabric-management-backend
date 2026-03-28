# FlowBoard Frontend Implementation Guide (Next.js + TypeScript)

Bu doküman, FlowBoard modülünün (Kanban, Görevler, Otomasyon, Dashboard) frontend tarafında **hatasız, performanslı ve type-safe** bir şekilde sıfırdan implemente edilmesi için hazırlanmış kusursuz mimari rehberdir.

Bu dokümanın asıl amacı; projenin backend tarafında tamamlanan %100 DTO izolasyonlu ve `ApiResponse<T>` standartlı güçlü yapısını, frontend'in (React/Next.js) tam kapasiteyle tüketebilmesini sağlamaktır.

---

## 🏗️ 1. Hazırlık ve Altyapı (Infrastructure)

Hiçbir UI kodu yazmadan önce, frontend altyapısının backend'in yeni standartlarına göre yapılandırılması zorunludur.

### 1.1. DTO ve Type Senkronizasyonu (Zorunlu)
*   `src/types/flowboard.ts` adında bir dosya oluşturulmalı.
*   Backend `com.fabricmanagement.flowboard` altındaki **~26 adet DTO ve Enum**, birebir TypeScript arayüzlerine (interfaces/enums) çevrilmeli.
*   *Asla `interface Board extends BaseEntity` gibi backend'den gelmeyen (`tenantId`, `version`) field'lar Frontend'e dahil edilmemeli.* Tüm tipler **Saf DTO** formatında olmalı (`BoardResponse`, `TaskResponse`).

### 1.2. API İstemcisi (Axios/Fetcher) ve Unwrap Mekanizması
*   **Kritik Hata Riski:** Backend tüm endpoint'lerde `{ success: boolean, data: T, message: string }` (`ApiResponse<T>`) dönmektedir.
*   **Çözüm:** `src/lib/api-client.ts` üzerindeki Axios interceptor, `status === 200` cevaplarını otomatik olarak unwrap etmeli (kapsülden çıkarmalı).
    ```typescript
    apiClient.interceptors.response.use((response) => {
       // Backend standart ApiResponse<T> sarmalayıcısını kullanıyorsa doğrudan datayı dön.
       if (response.data && response.data.success !== undefined) {
           return response.data; // Ya da projenin kurgusuna göre response.data.data
       }
       return response;
    });
    ```
*   Böylece component'ler `.then(res => res.data.data.tasks)` çirkinliğine girmeden direkt `const payload: TaskResponse[] = await api.getTasks();` yapabilecektir.

---

## 🎨 2. Component Mimarisi ve State Yönetimi

FlowBoard devasa bir interaktif bileşendir. Yanlış state yönetimi saniyeler içinde ölümcül re-render döngülerine yol açar.

### 2.1. Klasör Yapısı (Feature-Sliced Design)
Önerilen Next.js `src/features/flowboard` yapısı:
```text
src/features/flowboard/
├── api/             # API hook'ları (useBoard.ts, useTasks.ts vb.)
├── components/      # (Aşağıda detaylandırıldı)
├── stores/          # Zustand / Context state'leri
├── types/           # Sadece bu feature'a özel form tipleri
└── hooks/           # Drag-drop ve WS hesaplama hook'ları
```

### 2.2. Hiyerarşik Bileşenler
1.  **`BoardContainer` (Smart):** Sadece tahtanın iskeletini tutar, ana state'i veya Context'i sağlar.
2.  **`ColumnList` (Smart):** Grupları (Grup Bazlı veya Workflow Bazlı) map'ler.
3.  **`Column / Lane` (Dumb):** Belirli gruba ait Task'leri alır ve render eder (DropZone).
4.  **`TaskCard` (Dumb):** Çok hafif olmalıdır. Görevin detaylarını (`badge`, `dueDate`, `assignee`) gösterir. İçerisinde API isteği **olmamalıdır**. Sadece onClick tetiklemelidir.

### 2.3. State Yönetimi (Performans Kritik)
*   **Sunucu Durumu (Server State):** Tercihen `@tanstack/react-query` kullanılmalıdır. Kanban verileri asenkron ve çok katmanlıdır. Sık güncellemeler Mutation üzerinden yürütülmelidir.
*   **İstemci Durumu (Client State):** Sürükle-bırak (DnD) işlemi esnasında Board State'i `Zustand` veya React `useReducer` içinde tutulmalıdır. *Task sürüklendiğinde tüm uygulamanın değil, sadece sürüklenen Lane'in re-render olması şarttır!*

---

## 🖱️ 3. Sürükle Bırak (Drag & Drop) Dinamikleri

*   **Kütüphane Önerisi:** `@hello-pangea/dnd` (eski `react-beautiful-dnd` fork'u) veya `dnd-kit`. İkisi de React 18 / Next.js uyumludur.
*   **İşlem Akışı:**
    1.  Kullanıcı kartı taşır (UI anında güncellenir — **Optimistic UI Update**).
    2.  Arka planda Axios ile `PUT /api/v1/flowboard/tasks/{uuid}/status` (veya konum) isteği atılır.
    3.  *(Başarısız olursa)* Optimistic Update geri alınır (Rollback) ve hata toast mesajı basılır (Agnostic Error Handling devrede).

---

## 🔌 4. WebSocket (Real-Time) Entegrasyonu

Board'lar birden çok kullanıcı tarafından aynı anda izlenebilir (Miro/Trello gibi).

*   **Dinlenecek Olay (Event):** Backend `BoardWebSocketEventType` enum'u ile bildirim göndermektedir (`TASK_CREATED`, `TASK_ASSIGNED`, `TASK_MOVED`).
*   **Hook (`useBoardWebSocket.ts`):** Mount olduğunda ilgili tahtanın kanalına (örn: `/topic/board/{boardId}`) bağlanır (SockJS + STOMP / Pusher / SignalR).
*   **Reaksiyon:** `TASK_ASSIGNED` olayı geldiğinde, React Query `queryClient.invalidateQueries({ queryKey: ['boardTasks', boardId] })` çalıştırılarak UI'ın sihirli gibi kendiliğinden güncellenmesi sağlanır. (Ya da doğrudan payload'daki Task parse edilip mevcut Zustand state'i yamanır).

---

## ✅ 5. Formlar, Şablonlar ve Otomasyon UI

Modül sadece drag-drop değil, aynı zamanda şablon ve kural motorudur.

*   `CreateTaskRequest`: `assigneeId`, `dueDate` ve `labels` atamalarını destekler. Modal içinde bu alanlar `react-hook-form` ve `zod` veya `yup` kullanılarak doğrulanmalı (Backend'deki `jakarta.validation` tiplerine eşdeğer).
*   **Otomasyon (AutomationEngine) UI:** Kullanıcının `AutomationTriggerType` (örn: `TASK_MOVED_TO_DONE`) ve `AutomationActionType` (örn: `ASSIGN_TO_QA`) seçebileceği bağımlı/kademeli (Cascading) Select bileşenleri tasarlanmalıdır.
*   **Dashboard Widget’ları:** `DashboardWidgetDto` sınıfından gelen verilere göre, bileşenler `WidgetType` enum'una (Chart, Metric, List) bakarak doğru React/Recharts komponentini render edecek bir `WidgetRenderer` Pattern'i ile yazılmalıdır.

---

## 🛡️ 6. Kalite ve Test Standartları (Checklist)

Frontend pull request'i (PR) atılmadan önce uyulması gerekenler:

- [ ] `tsc --noEmit` çalıştırıldı ve FlowBoard sınırları içinde **0 Type Error** oluştu.
- [ ] Task silindiğinde, oluşturulduğunda veya form fail olduğunda `i18n.t("errors.FLOW_XXX")` (Agnostic Error mesajları) kusursuz gösteriliyor.
- [ ] `TaskCard` üzerine hızlı tıklandığında çoklu re-render tetiklenmiyor (React Profiler ile test edildi).
- [ ] Drag & Drop esnasında ekran titremiyor ve "DropZone" vurguları temiz yakalanıyor.
- [ ] Dark Mode (Tailwind veya CSS Variable) class'ları `TaskCard` bordürlerinde uyumlu çalışıyor (Board'un karmaşası kontrast problemlerine yol açmamalı).
