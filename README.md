# Journal Canvas

## Giải thích kiến trúc

Mình dùng practical Clean Architecture 3 lớp + MVVM cho presentation, custom view + Canvas API cho phần editor. Quy tắc chính: domain không biết gì về Android, data implement interface của domain, presentation chỉ gọi UseCase chứ không động trực tiếp Repository.

### Presentation layer

`CanvasEditorActivity` chỉ làm việc UI:
- Inflate XML, setup toolbar click listeners, setup `PickVisualMedia` launcher.
- `observe(uiState)` qua `repeatOnLifecycle(STARTED)` → `journalCanvasView.submitState(state)`.
- `collect(effect)` cho one-shot effect: open picker, show toast, start/stop inline text editing.
- Forward callback `journalCanvasView.onEvent` về `viewModel::onEvent`.

Không có business logic, không truy cập DataStore, không decode bitmap.

`CanvasEditorViewModel` là single source of truth:
- `MutableStateFlow<CanvasUiState>` chứa toàn bộ state (objects, selection, canvas transform, editing state, pending placement, undo/redo flags).
- `MutableSharedFlow<CanvasUiEffect>` cho hiệu ứng dùng 1 lần.
- `onEvent(CanvasUiEvent)` là cửa vào duy nhất từ UI, dispatch tới handler private.
- Undo/redo: 2 `ArrayDeque<CanvasHistorySnapshot>` cap 30 entry, chỉ lưu metadata (objects, selectedId, canvas transform). Không lưu Bitmap.

### Custom View — JournalCanvasView

`JournalCanvasView` extends `android.view.View` thuần (~80 dòng). View là shell, mỗi loại trách nhiệm tách thành component riêng:

- `CanvasRenderer` — orchestration render. Gồm `TextObjectRenderer` (cache `StaticLayout` per object id), `ImageObjectRenderer` (lookup bitmap qua cache), `SelectionRenderer` (border + 4 corner handle + 2 edge handle), `SnapGuideRenderer`. Mỗi renderer giữ Paint/Rect riêng, reuse mọi frame, zero alloc trong `onDraw`.
- `CanvasGestureController` — state machine cho `MotionEvent`. Sealed `TouchState`: `None`, `TapCandidate`, `DragObject`, `DragCanvas`, `ScaleObject`, `RotateObject`, `ResizeLeft/Right`, `TransformObject`, `ZoomCanvas`. Emit `CanvasUiEvent` lên VM.
- `CanvasViewportController` — initial fit A4 page vào màn, clamp offset khi pan/zoom, clamp khi view size đổi.
- `CanvasHitTester` — body hit test, dùng trig inverse (cos/sin trực tiếp) thay cho `Matrix.invert`. Zero alloc.
- `SelectionHandleHitTester` — hit test 4 góc + 2 edge handle với bán kính 28dp screen-space.
- `VisibleImageRequestCollector` — quét object Image giao viewport mở rộng 512px, trả `Map<URI, targetBucketPx>` cho Activity load bitmap chỉ những ảnh visible.
- `SnapController` — page grid 3x3 cố định (25%/50%/75%), snap khi bounds rơi vào threshold 6px screen-space.

### Data flow

State đi xuống, event đi lên. View không tự lưu state lâu dài.

```
User touch
  → JournalCanvasView.onTouchEvent
  → CanvasGestureController.onTouchEvent
  → emit CanvasUiEvent
  → Activity → viewModel.onEvent(event)
  → VM updateLive { ... } hoặc updateAndSave { ... }
  → _uiState.value = state.copy(...)
  → StateFlow emit
  → Activity collect
  → journalCanvasView.submitState(state)
  → invalidate()
  → onDraw()
```

ACTION_MOVE đi qua `updateLive` (không save DataStore). `GestureCommitted` ở ACTION_UP mới save 1 lần. Cursor blink cũng phát qua event để VM giữ control toàn bộ.

---

## Thảo luận về sự đánh đổi

### 1. `List<CanvasObjectUiModel>` + `objectsById` index thay vì chỉ Map

Container chính là `List` (giữ thứ tự, JSON-friendly cho DataStore). Để lookup nhanh thì mình thêm derived property:

```kotlin
val objectsById: Map<String, CanvasObjectUiModel> by lazy(LazyThreadSafetyMode.NONE) {
    if (objects.isEmpty()) emptyMap() else objects.associateBy { it.id }
}

val selectedObject: CanvasObjectUiModel?
    get() = selectedObjectId?.let { objectsById[it] }
```

Đổi lấy: lookup theo id ở mọi nơi (renderer, gesture controller, ViewModel) thành O(1). Map chỉ build khi có chỗ truy cập (lazy)

Cái giá: mỗi state instance mới có khả năng build 1 HashMap 10k entry khi handler đầu tiên access. Tổng cost cao nhất vẫn là 1 × O(N) per MOVE thay vì dùng firstOrNull để lấy ra object 3-5 × O(N), cùng dữ liệu xuất hiện ở 2 nơi (List và Map) nhưng map là derived nên không bị lệch.

Phương án mình đã nghĩ đến và bỏ đổi hoàn toàn sang `Map<String, ...>` làm container chính. Bỏ vì mất thứ tự render và phá schema JSON đã ổn định trong DataStore.

---

### 2. Linear cull + linear hit test
Thuật toán này dùng vòng lặp chạy qua 10.000 phần tử, dùng toán học để dựng một hình chữ nhật quanh đối tượng. Nếu hình chữ nhật đó nằm ngoài màn hình thì sẽ không vẽ đối tượng đó 
Cull viewport trong renderer (`CanvasRenderer.shouldDrawObject`) và hit test (`CanvasHitTester.findObjectAt`) đều forEach toàn bộ object.

Đổi lấy: code đơn giản, đúng, ít bug. mỗi lần vẫn quét qua O(n) vẫn ônt

Cái giá: lên cỡ vài chục nghìn thì cull bắt đầu ăn ngân sách frame. Hit test thì vẫn ổn vì chỉ chạy 1 lần.

thuật toán tối ưu hơn cần thêm 200-300 dòng code để maintain index khi object move/add/delete. Nhưng đây dự án demo mình sẽ chọn thuật toán cân bằng nhất như yêu cấu là 1000 phần tử

### 3. JSON full-state save mỗi gesture commit

`SaveCanvasStateUseCase` serialize toàn bộ `CanvasState` thành 1 JSON blob rồi ghi vào DataStore Preferences với key duy nhất `"canvas_json"`.

Đổi lấy: đơn giản nhất có thể — 1 key, 1 blob, không cần migration, không cần version schema, code gọn

Cái giá: ở 10k object thì serialize JSON tốn vài trăm ms mỗi commit. Chạy coroutine nên user không lag nhưng vẫn ăn cpu

Phương án : dùng room nếu dự án lớn và cần xử lý nhiều đối tượng hơn còn nếu 1000 obj thì phương pháp nãy vẫn mượt

---

### Câu hỏi thảo luận

**1. If the canvas contains 1000 objects, what would become the first bottleneck?**

Vấn đề chung của canvas editor loại này khi lên 1000 object:

- **Redraw toàn bộ mỗi frame**: 1000 × 60fps lượt `drawText`/`drawBitmap`
- **Alloc trong `onDraw`**: `new Paint/RectF/Matrix` mỗi object → GC chạy liên tục, frame drop.
- **Decode bitmap trong draw loop**: ảnh decode lại mỗi frame
- **Linear lookup mỗi MOVE**: gesture handler `firstOrNull` O(N) ở 60Hz → 60k iter/giây.
- **`StaticLayout` re-measure + persistence + sort z-order mỗi frame**: mỗi cái đều ăn CPU tuyến tính theo N.

App mình giải quyết:

- **Viewport cull** (`CanvasRenderer.shouldDrawObject`):Object offscreen skip ngay → drawn count gần như không phụ thuộc N.
- **Zero-alloc `onDraw`**: tất cả `Paint`/`RectF`/`Rect`/`Matrix` là field class-level, reuse mỗi frame.
- **Bitmap LRU cache + 5 bucket size**: renderer chỉ lookup cache; decode async với `Semaphore(3)` + 3s backoff. `VisibleImageRequestCollector` đảm bảo chỉ load ảnh visible.
- **`objectsById` O(1) lookup**:`objectsById[id]`. Lazy property nên không phá `equals`/`copy`.
- **`StaticLayout` cache per object id**: chỉ rebuild khi `(text, textSize, width, color)` đổi.
- **Hit test trig inverse, không `Matrix.invert`**: tự tinhs toán thủ công thay vì dùng các hàm có sẵn

---

**2. How would you handle very large images?**

Vấn đề chung khi xử lý ảnh lớn trong app vẽ:

- **Hết RAM khi giải nén ảnh**
- **GPU vẽ ảnh có giới hạn**
- **Giải nén làm đứng app**
- **Giữ ảnh to khi vẽ nhỏ**

App mình giải quyết:

- **State chỉ lưu đường dẫn ảnh, không lưu pixel**: `CanvasUiState` và DataStore chỉ chứa URI dạng `String`. Pixel luôn nằm ở bộ nhớ tạm, không lẫn vào dữ liệu chính.
- **đọc size trước ** (`BitmapLoader.loadDimensions`): bật cờ `inJustDecodeBounds = true` để chỉ đọc kích thước mà không tốn RAM. Dùng để biết tỉ lệ ảnh khi user thêm ảnh.
- **Giải nén ở 5 mức kích cỡ** (128 / 256 / 512 / 1024 / 2048): cần nét bao nhiêu thì chọn mức gần đó, dùng `inSampleSize` để giảm pixel. Mức to nhất 2048 vẫn dưới ngưỡng GPU nên không vẽ lỗi.
- **Bộ nhớ đệm theo cặp (URI, mức kích cỡ)**: tổng dung lượng cap `maxMemory/8`. Cùng 1 ảnh có thể có nhiều mức cùng tồn tại — zoom to thì bản nhỏ vẫn được vẽ trong lúc bản to đang giải nén.
- **Giải nén chạy nền + giới hạn 3 ảnh cùng lúc + chờ 3 giây nếu lỗi**: luồng UI luôn mượt; ảnh lỗi không bị thử lại liên tục làm tốn pin.
- **Chỉ giải nén ảnh đang nhìn thấy** (`VisibleImageRequestCollector`): 100 ảnh trên trang mà chỉ 5 cái hiển thị → app chỉ động vào 5. Cuộn page mới giải nén thêm.
- **Vẽ ô xám tạm khi chưa có ảnh**: render không bao giờ phải dừng chờ ảnh load. Ảnh load xong thì hiện ngay frame sau.

5 mức kích cỡ là cố định, không phải lúc nào cũng nét tuyệt đối. app chỉnh ảnh chuyên nghiệp thì cần kỹ hơn.

---

**3. How would the architecture change if cloud sync was added?**

**Tầng domain**
- Mỗi object có thêm vài thông tin phụ: `updatedAt`, `dirty` (cờ đánh dấu chưa đồng bộ lên server), `deleted` (xoá mềm — đánh dấu là đã xoá nhưng vẫn giữ lại để sync biết mà xoá bên server).
- Thêm interface `RemoteCanvasDataSource` cho tầng data cài đặt.
- Thêm 2 UseCase: `SyncCanvasUseCase` (đẩy lên / kéo về) và `ObserveSyncStateUseCase` (cho UI biết đang đồng bộ tới đâu).

**Tầng data**
- Local hiện đang dùng DataStore + JSON. Nếu cần truy vấn riêng những object `dirty = true` để gửi từng phần thì đổi sang Room sẽ hợp hơn. Còn không thì giữ JSON + thêm 1 map cờ dirty riêng.
- Thêm `RemoteCanvasDataSourceImpl` dùng Retrofit hoặc Ktor để gọi API.
- `CanvasRepositoryImpl` điều phối lại: ghi local trước → đánh dấu `dirty = true` → kích hoạt sync ngầm.

**sync**
- Dùng `WorkManager` của Android chạy worker ngầm khi máy có mạng.
- Quy trình: kéo về object có `updatedAt` mới hơn local → merge theo nguyên tắc "ai sửa sau thắng" → đẩy lên những object có `dirty = true` → bỏ cờ dirty sau khi server xác nhận.
- Fail thì retry với chờ tăng dần để không thrash mạng.

**Ảnh**
- File local vẫn lưu trong `filesDir/canvas_images/` như hiện tại. Sync worker upload

**Tầng presentation**
- ViewModel có thêm `syncStatus: StateFlow<SyncStatus>` (Đang đồng bộ / Đã đồng bộ / Mất mạng / Lỗi).
- Toolbar có 1 icon nhỏ hiển thị trạng thái.

**Giữ nguyên**
- ID của object vẫn là `UUID` do client tự sinh — đã an toàn cho hệ nhiều thiết bị, không bao giờ trùng.
- Custom View, Renderer, Gesture controller, hit test — toàn bộ phần vẽ và tương tác. Cloud sync chỉ đổi nguồn dữ liệu, không đụng cách hiển thị.

---

**4. How would you support realtime collaboration?**

Câu này khác cloud sync thay đổi của user khác phải hiện trên màn mình trong tưcs thì.

**Đổi cách lưu state**
- Hiện tại app lưu cả `CanvasState` mỗi lần commit. Khi làm realtime, mình đổi sang **nhật ký thao tác** (operation log): mỗi action thành 1 op nhỏ — `AddObject`, `MoveObject(id, dx, dy)`, `UpdateText(id, text)`, `DeleteObject(id)`. State hiện tại là kết quả replay các op từ đầu (hoặc từ snapshot gần nhất).
- User thao tác → app apply op ngay tại local (vẽ trước, đồng bộ sau) → gửi op qua mạng → server phát lại op đó cho thiết bị khác.

**Mạng**
- Dùng WebSocket cho kênh 2 chiều giữa client và server, độ trễ thấp. REST chỉ cho đăng nhập + load lần đầu.
- Xử lý mất kết nối: buffer op chưa gửi vào queue local, kết nối lại thì gửi tiếp theo đúng thứ tự.

**Xử lý xung đột**
- **CRDT**: kiểu dữ liệu thiết kế để tự merge — mỗi op kèm 1 thời gian

**Hiện ai đang ở đâu**
- Hiển thị cursor và selection của user khác trên canvas, mỗi user 1 màu khác nhau.
- Server phát event presence riêng, không lưu vào database.

**Cụ thể app mình đổi gì**
- Domain: thêm sealed type `CanvasOperation`, `OperationLog` thay cho snapshot.
- Data: `RealtimeRepository` qua WebSocket; luồng là apply local trước, gửi op sau.
- ViewModel: gesture không emit `ObjectTransformChanged` nữa mà emit `Operation`. State hiện tại vẫn derive từ log.
- Custom View: không đổi — vẫn nhận `CanvasUiState` đầy đủ qua `submitState`. Chỉ thêm component vẽ cursor của user khác.
- Undo/redo phải đổi cách nghĩ: mà tạo 1 "inverse operation" để huỷ thao tác của chính mình. Không bao giờ undo op của user khác.
- Thêm khái niệm `documentId`, quyền (`read` / `edit`), share link.

---

**5. How would you optimize undo/redo memory usage?**

Vấn đề chung khi làm undo/redo cho canvas editor:

- **Lưu cả snapshot mỗi action**
- **Push mỗi ACTION_MOVE**
- **Stack không giới hạn**
- **Snapshot giữ reference object đã xoá**
- **Redo stack giữ nhánh cũ**

App mình giải quyết:

- **`CanvasHistorySnapshot` chỉ lưu metadata** — `objects` (list reference), `selectedObjectId`, `canvasScale`, `canvasOffsetX/Y`. Không Bitmap, không Paint, không Matrix, không `pendingPlacement`, không `cursorVisible`. Snapshot là data class nhỏ vài chục byte + reference list.
- **Object immutable, share-by-reference**: 30 snapshot vẫn dùng chung object instance — chỉ khi user sửa transform của 1 object thì snapshot mới có instance mới (qua `copyTransform`), còn lại trỏ về reference cũ. RAM tăng theo **số object đã đổi**, không phải **N × số snapshot**.
- **Push 1 lần per gesture, không per frame**: capture snapshot ở `GestureStarted` (ACTION_DOWN), push vào undo stack ở `GestureCommitted` (ACTION_UP) nếu state thực sự đổi. Kéo object 200 frame = 1 entry. ACTION_MOVE chỉ `updateLive` state, không đụng stack.
- **Cap 30 entry** (`MAX_HISTORY_STEPS = 30`): vượt thì `removeFirst()` bỏ entry cũ nhất. Mức 30 đủ cho use case journal — user ít khi undo quá 30 bước; đổi lại RAM cho stack luôn bounded.
- **Redo stack clear khi có action mới**: làm action mới sau undo → nhánh cũ không bao giờ truy lại được, clear ngay.
- **Sweep ảnh orphan**: `cleanupUnusedImportedImages` chạy sau khi trim history hoặc clear redo. file ảnh chỉ bị xoá khi không còn gì tham chiếu.
- **Text edit không spam stack**: cả 1 phiên gõ vài chục ký tự chỉ push 1 entry ở `TextEditCommitted`, không push mỗi phím.
- **Command/diff model thay snapshot**: mỗi entry chỉ lưu delta — `MoveCommand(id, oldTransform, newTransform)`, `AddCommand(object)`, `DeleteCommand(object)` tối ưu hơn nhưng mình vẫn chưa triển khai


