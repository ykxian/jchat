# Files & Light RAG Module

> 文件上传 → 抽文本 → 关联消息 → 按 token 预算注入 prompt。
>
> Context：v1 **不做向量检索**，只做"文本直接注入"的轻量 RAG。pgvector 扩展已装但不建表。v1.x 加 embedding。

---

## 1. 职责边界

**做**：
- 文件上传接口（multipart），sha256 去重
- 本地文件系统存储（`${STORAGE_ROOT}/<userId>/<fileId>`）
- Apache Tika 异步抽文本 → 写 `files.text_extracted`
- 提供 `buildReferenceContext(fileIds, tokenBudget)` 给 PromptBuilder
- 文件 CRUD（列表、下载、删除）
- 状态管理（processing / ready / failed）

**不做**：
- 向量检索（v1.x）
- 图像 OCR（v2，但 Tika 对有文字层的 PDF 已能抽）
- 超大文件分块索引（v2）

---

## 2. 数据模型

详见 [DATA-MODEL.md](../DATA-MODEL.md#27-files)：
- `files` — 元数据 + `text_extracted`
- `message_files` — 多对多关联

---

## 3. 包结构

```
backend/src/main/java/com/jchat/file/
├── controller/FileController.java
├── service/
│   ├── FileService.java
│   ├── FileStorageService.java       # 磁盘读写
│   └── FileTextExtractor.java        # Tika 抽取
├── entity/
│   ├── FileEntity.java
│   └── MessageFile.java
├── repository/
│   ├── FileRepository.java
│   └── MessageFileRepository.java
└── dto/
    ├── FileResponse.java
    └── UploadResponse.java
```

（命名 `FileEntity` 避免与 `java.io.File` 冲突。）

---

## 4. Entity

```java
@Entity @Table(name = "files")
@SQLRestriction("deleted_at is null")
public class FileEntity {
    @Id @GeneratedValue(strategy = IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "conversation_id") private Long conversationId;
    @Column(nullable = false) private String filename;
    @Column(name = "mime_type", nullable = false) private String mimeType;
    @Column(name = "size_bytes", nullable = false) private long sizeBytes;
    @Column(nullable = false) private String sha256;
    @Column(name = "storage_path", columnDefinition = "text", nullable = false) private String storagePath;
    @Column(name = "text_extracted", columnDefinition = "text") private String textExtracted;
    @Column(nullable = false) private String status = "processing";
    @Column(name = "error_message") private String errorMessage;
    @CreationTimestamp @Column(name = "created_at") private Instant createdAt;
    @Column(name = "deleted_at") private Instant deletedAt;
}

@Entity @Table(name = "message_files")
@IdClass(MessageFileId.class)
public class MessageFile {
    @Id @Column(name = "message_id") private Long messageId;
    @Id @Column(name = "file_id")    private Long fileId;
    @Column private int position;
}
```

---

## 5. Service

### 5.1 `FileService`

```java
@Service @Transactional
public class FileService {

    public FileEntity upload(User user, MultipartFile file, Long conversationId) throws IOException {
        if (file.getSize() > 50L * 1024 * 1024)
            throw new ApiException(VALIDATION_FAILED, "file too large (>50MB)");

        rateLimit.tryAcquire("upload:" + user.getId(), 10, 10);   // 10/hour

        var bytes = file.getBytes();
        var sha = Hashing.sha256Hex(bytes);

        // 去重
        var existing = files.findByUserIdAndSha256(user.getId(), sha);
        if (existing.isPresent()) return existing.get();

        var fe = new FileEntity();
        fe.setUserId(user.getId());
        fe.setConversationId(conversationId);
        fe.setFilename(file.getOriginalFilename());
        fe.setMimeType(file.getContentType() != null ? file.getContentType() : "application/octet-stream");
        fe.setSizeBytes(bytes.length);
        fe.setSha256(sha);
        fe.setStatus("processing");
        fe = files.save(fe);

        // 写磁盘
        var relPath = user.getId() + "/" + fe.getId();
        storage.write(relPath, bytes);
        fe.setStoragePath(relPath);
        files.save(fe);

        // 异步抽文本
        textExtractor.extractAsync(fe.getId());

        return fe;
    }

    public FileEntity get(User user, Long id) {
        var f = files.findById(id).orElseThrow(() -> new ApiException(NOT_FOUND, "file not found"));
        if (!f.getUserId().equals(user.getId())) throw new ApiException(NOT_FOUND, "file not found");
        return f;
    }

    public CursorPage<FileEntity> list(User user, String cursor, int limit, Long conversationId) {
        // 按 created_at desc
    }

    public byte[] download(User user, Long id) throws IOException {
        var f = get(user, id);
        return storage.read(f.getStoragePath());
    }

    public void delete(User user, Long id) {
        var f = get(user, id);
        f.setDeletedAt(Instant.now());
        files.save(f);
        storage.deleteAsync(f.getStoragePath());
    }

    /**
     * 供 PromptBuilder 调用：组装文件参考上下文
     */
    public String buildReferenceContext(User user, List<Long> fileIds, int tokenBudget) {
        if (fileIds == null || fileIds.isEmpty()) return null;
        var list = files.findAllByUserIdAndIdIn(user.getId(), fileIds);
        if (list.isEmpty()) return null;

        var entries = new ArrayList<String>();
        var remainingChars = tokenBudget * 3;   // 近似 1 token ~ 3 chars (中英混合)
        for (var f : list) {
            if (remainingChars <= 200) break;
            if (!"ready".equals(f.getStatus())) continue;
            var text = truncate(f.getTextExtracted(), remainingChars - 100);
            entries.add("---\n文件名: %s\n%s".formatted(f.getFilename(), text));
            remainingChars -= entries.get(entries.size() - 1).length();
        }
        if (entries.isEmpty()) return null;
        return "【参考资料】\n" + String.join("\n", entries);
    }

    private String truncate(String text, int chars) {
        if (text == null) return "";
        if (text.length() <= chars) return text;
        // 简单策略：保留前 60% + 尾部 20%，中间丢
        var head = (int) (chars * 0.6);
        var tail = (int) (chars * 0.2);
        return text.substring(0, head) + "\n... [中间部分已省略] ...\n" + text.substring(text.length() - tail);
    }
}
```

### 5.2 `FileStorageService`

```java
@Component
public class FileStorageService {
    private final Path root;

    public FileStorageService(AppProperties props) {
        this.root = Paths.get(props.storage().root());
        try { Files.createDirectories(root); } catch (IOException e) { throw new RuntimeException(e); }
    }

    public void write(String relPath, byte[] bytes) throws IOException {
        var target = root.resolve(relPath);
        Files.createDirectories(target.getParent());
        Files.write(target, bytes, CREATE, TRUNCATE_EXISTING, WRITE);
    }

    public byte[] read(String relPath) throws IOException {
        return Files.readAllBytes(root.resolve(relPath));
    }

    @Async("virtualThreadExecutor")
    public void deleteAsync(String relPath) {
        try {
            Files.deleteIfExists(root.resolve(relPath));
        } catch (IOException e) {
            log.warn("failed to delete file {}", relPath, e);
        }
    }
}
```

### 5.3 `FileTextExtractor`

```java
@Component @RequiredArgsConstructor
public class FileTextExtractor {
    private final FileRepository files;
    private final FileStorageService storage;

    @Async("virtualThreadExecutor")
    @Transactional
    public void extractAsync(Long fileId) {
        var f = files.findById(fileId).orElse(null);
        if (f == null) return;
        try {
            var bytes = storage.read(f.getStoragePath());
            var text = extract(bytes, f.getMimeType(), f.getFilename());
            f.setTextExtracted(text);
            f.setStatus("ready");
            files.save(f);
        } catch (Exception e) {
            log.warn("text extraction failed: fileId={}", fileId, e);
            f.setStatus("failed");
            f.setErrorMessage(truncate(e.getMessage(), 500));
            files.save(f);
        }
    }

    private String extract(byte[] bytes, String mime, String filename) throws Exception {
        var parser = new AutoDetectParser();
        var handler = new BodyContentHandler(-1);   // -1 = 无大小限制
        var metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
        try (var is = new ByteArrayInputStream(bytes)) {
            parser.parse(is, handler, metadata, new ParseContext());
        }
        var text = handler.toString().trim();
        // 规范化换行、多余空白
        text = text.replaceAll("\r\n", "\n").replaceAll("\n{3,}", "\n\n");
        return text;
    }
}
```

Tika 支持：PDF、DOCX、XLSX、PPTX、HTML、Markdown、纯文本、RTF、ODF 等。图片 OCR 默认不启用（需要 tesseract 运行时）。

---

## 6. Controller

```java
@RestController @RequestMapping("/api/v1/files")
@SecurityRequirement(name = "bearer")
public class FileController {

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResponse> upload(@CurrentUser User u,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) Long conversationId) throws IOException {
        var f = service.upload(u, file, conversationId);
        return ResponseEntity.status(201).body(UploadResponse.from(f));
    }

    @GetMapping
    public CursorPage<FileResponse> list(@CurrentUser User u, @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit, @RequestParam(required = false) Long conversationId) {
        return service.list(u, cursor, limit, conversationId).map(FileResponse::from);
    }

    @GetMapping("/{id}")
    public FileResponse get(@CurrentUser User u, @PathVariable Long id) {
        return FileResponse.from(service.get(u, id));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@CurrentUser User u, @PathVariable Long id) throws IOException {
        var f = service.get(u, id);
        var bytes = service.download(u, id);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(f.getMimeType()))
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + URLEncoder.encode(f.getFilename(), UTF_8).replace("+", "%20") + "\"")
            .body(bytes);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@CurrentUser User u, @PathVariable Long id) {
        service.delete(u, id);
        return ResponseEntity.noContent().build();
    }
}
```

### `UploadResponse`

```json
{
  "id": "7",
  "filename": "spec.pdf",
  "mimeType": "application/pdf",
  "sizeBytes": 123456,
  "sha256": "...",
  "status": "processing",
  "createdAt": "..."
}
```

前端 upload 成功后 → 轮询 `GET /files/{id}` 直到 `status=ready`（间隔 1s，最多 60 次）；或前端只在发消息时检查，未 ready 就本地提示"抽取中"。

---

## 7. 与 Chat 模块的协作

`ChatCompletionRequest` 带 `fileIds: ["7", "8"]` → `ChatService` 调 `MessageService` 把 `message_files` 插好 → `PromptBuilder` 调 `FileService.buildReferenceContext()` → 插入 `system` 消息。

**约束**：
- 注入预算默认 `model.contextWindow * 0.3`（见 `PromptBuilder` 调用处）
- 超出预算则截断（见 `truncate` 逻辑）
- 若 `text_extracted` 为 null（还在抽），注入 "(文件尚未解析完成)"

---

## 8. 前端

`frontend/src/pages/FilesPage.tsx`：

- 表格：filename、size、status（processing 转圈 / ready 绿勾 / failed 红叉）、createdAt
- 行操作：下载、删除、重命名（可选）

`frontend/src/components/chat/Composer.tsx`：

- 拖拽文件到输入框 → 触发上传 → 芯片显示
- 发送时把已上传的 fileIds 带到 request body

`frontend/src/components/chat/AttachmentBubble.tsx`：

- 消息气泡顶部显示关联的附件（icon + 文件名 + 下载图标）

---

## 9. 测试

### 单元
- `FileServiceTest`：
  - 上传成功 → `files` 行正确
  - 同 user 同 sha256 二次上传 → 返回已存在 id，不重复存盘
  - 超 50MB 抛 `VALIDATION_FAILED`
  - `buildReferenceContext` 多文件按序、按预算截断
- `FileTextExtractorTest`：
  - 纯文本 → 原样
  - 简单 PDF fixture → 能抽
  - 损坏 PDF → `status=failed`
- `FileStorageServiceTest`：write / read / delete 流程

### 集成
- `FileControllerIT`：
  - POST `/files` multipart → 201
  - GET `/files` 列表返回自己文件
  - GET `/files/{id}/download` 返回原字节
  - 跨用户访问 → 404
  - DELETE → 软删，磁盘异步清

---

## 10. 常见陷阱

- **`MultipartFile.getBytes()` 一次读全**：50MB 还好，但如果涨到 200MB+ 应改为 `transferTo` 直接写盘 + 二次读算 sha256。v1 容忍一次性读。
- **Tika 解析耗时**：大 PDF 可能 10+ 秒。异步 + 状态字段必要。前端别在 upload 接口等。
- **Tika 对加密 PDF / 图片 PDF** 抽不出文本（没开 OCR）：`text_extracted` 为空；user 应看到 `status=failed` + 原因。
- **中文文件名下载**：`Content-Disposition` 要 URL 编码（RFC 5987：`filename*=UTF-8''%E4%B8%AD%E6%96%87.pdf`）。
- **Tika 依赖巨大**：`tika-parsers-standard-package` ~50MB。若最终镜像需要瘦身，按文件类型裁剪。v1 不优化。
- **SHA-256 冲突**：实际不可能碰上，但加 `(user_id, sha256)` 唯一以防竞态下重复插。
- **private IP** 禁止：`http_fetch` 在 plugin 模块处理；本模块纯本地文件，无网络风险。

---

## 11. v1.x 向量 RAG 升级预览（不在 v1 范围）

```
1. V7 迁移：建 file_chunks 表 + ivfflat 索引
2. EmbeddingService（复用 LlmProvider 加 embed() 接口）
3. ChunkingService：按段落切 + overlap
4. 文件上传流程：
   ready 后 → ChunkingService → EmbeddingService → 写 file_chunks
5. PromptBuilder 调整：
   根据用户最新 message 做 embedding → 在 file_chunks 里 top-K 检索 → 只注入相关块
6. 成本控制：
   embedding 只按需（文件被提问时生成，不是上传时）；缓存结果
```
