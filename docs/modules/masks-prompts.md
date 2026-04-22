# Masks & Prompts Module

> 角色 / 模板（Masks）的 CRUD、系统内置种子、NextChat JSON 导入导出。
>
> Context：Mask 是 NextChat 的核心概念 — 一个"角色"捆绑 system prompt、默认模型、参数。用户基于 mask 新建会话，快速获得特定场景体验（翻译、代码审查等）。

---

## 1. 职责边界

**做**：
- Mask 实体 CRUD（用户自建 + 系统内置）
- 系统 mask 种子数据（~20 个高质量模板）
- 权限：用户只能改 / 删自己的；系统 mask（owner_id=null）任何人不能动
- Mask 公开 / 私有（用户可勾选公开，其他用户可见）
- NextChat JSON 格式导入导出

**不做**：
- Few-shot 示例注入（v1 不支持，NextChat 的 `context` 多条消息只取第一条 system）
- Mask 调用逻辑（使用 mask 在 conversation/chat 模块，查 mask.systemPrompt 即可）

---

## 2. 数据模型

详见 [DATA-MODEL.md](../DATA-MODEL.md#26-masks)：

```
masks (
  id, owner_id (null=系统), name, avatar, system_prompt,
  default_provider, default_model, temperature, top_p, max_tokens,
  tags text[], is_public, created_at, updated_at, deleted_at
)
```

## 3. 包结构

```
backend/src/main/java/com/jchat/mask/
├── controller/MaskController.java
├── service/
│   ├── MaskService.java
│   └── MaskImportExportService.java
├── dto/
│   ├── MaskResponse.java
│   ├── CreateMaskRequest.java
│   ├── UpdateMaskRequest.java
│   └── NextChatMaskJson.java         # 导入兼容 schema
├── entity/
│   └── Mask.java
└── repository/
    └── MaskRepository.java
```

Frontend: `frontend/src/pages/MasksPage.tsx` + `components/mask/*`.

---

## 4. Entity

```java
@Entity @Table(name = "masks")
@SQLRestriction("deleted_at is null")
public class Mask {
    @Id @GeneratedValue(strategy = IDENTITY) private Long id;
    @Column(name = "owner_id") private Long ownerId;          // null = system
    @Column(nullable = false) private String name;
    @Column private String avatar;                            // emoji 或 URL
    @Column(name = "system_prompt", columnDefinition = "text", nullable = false)
    private String systemPrompt;
    @Column(name = "default_provider") private String defaultProvider;
    @Column(name = "default_model") private String defaultModel;
    @Column private Double temperature;
    @Column(name = "top_p") private Double topP;
    @Column(name = "max_tokens") private Integer maxTokens;
    @Type(StringArrayType.class)
    @Column(columnDefinition = "text[]") private String[] tags;
    @Column(name = "is_public", nullable = false) private boolean isPublic;
    @CreationTimestamp @Column(name = "created_at") private Instant createdAt;
    @UpdateTimestamp   @Column(name = "updated_at") private Instant updatedAt;
    @Column(name = "deleted_at") private Instant deletedAt;
}
```

---

## 5. Service

```java
@Service @Transactional
public class MaskService {

    public CursorPage<Mask> list(User user, String cursor, int limit, String q, boolean mineOnly) {
        // 可见性：
        //   owner_id IS NULL (系统)
        //   OR owner_id = :user.id (自己)
        //   OR is_public = true (公开的别人)
        // mineOnly=true 时仅第二条
        // q 过滤 name ILIKE 或 'tag' = ANY(tags)
        // cursor 用 created_at
    }

    public Mask get(User user, Long id) {
        var m = masks.findById(id).orElseThrow(() -> new ApiException(NOT_FOUND, "mask not found"));
        // 可见性检查
        if (m.getOwnerId() != null && !m.getOwnerId().equals(user.getId()) && !m.isPublic()) {
            throw new ApiException(NOT_FOUND, "mask not found");
        }
        return m;
    }

    public Mask create(User user, CreateMaskRequest req) {
        var m = new Mask();
        m.setOwnerId(user.getId());
        applyChanges(m, req);
        return masks.save(m);
    }

    public Mask update(User user, Long id, UpdateMaskRequest req) {
        var m = masks.findById(id).orElseThrow(() -> new ApiException(NOT_FOUND, "mask not found"));
        if (m.getOwnerId() == null || !m.getOwnerId().equals(user.getId())) {
            throw new ApiException(FORBIDDEN, "cannot modify mask");
        }
        applyChanges(m, req);
        return masks.save(m);
    }

    public void delete(User user, Long id) {
        var m = masks.findById(id).orElseThrow(() -> new ApiException(NOT_FOUND, "mask not found"));
        if (m.getOwnerId() == null) throw new ApiException(FORBIDDEN, "cannot delete system mask");
        if (!m.getOwnerId().equals(user.getId())) throw new ApiException(FORBIDDEN, "cannot delete others' mask");
        m.setDeletedAt(Instant.now());
        masks.save(m);
    }
}
```

---

## 6. NextChat 导入兼容

### 6.1 NextChat mask JSON schema（参考）

```json
{
  "id": "nextchat-id-123",
  "avatar": "🧐",
  "name": "代码审查员",
  "context": [
    { "role": "system", "content": "你是一位资深代码审查员..." }
  ],
  "modelConfig": {
    "model": "gpt-4o-mini",
    "temperature": 0.3,
    "top_p": 1.0,
    "max_tokens": 2048,
    "presence_penalty": 0,
    "frequency_penalty": 0,
    "sendMemory": true,
    "historyMessageCount": 4,
    "compressMessageLengthThreshold": 1000
  },
  "lang": "cn",
  "builtin": false,
  "createdAt": 1700000000000
}
```

### 6.2 字段映射表

| NextChat | jchat | 备注 |
|---|---|---|
| `name` | `name` | 必须 |
| `avatar` | `avatar` | emoji / URL 原样保留 |
| `context[0].role == "system"` 的 `content` | `systemPrompt` | 首条 system；多条 system 合并；非 system 的丢弃 + warn |
| `modelConfig.model` | `defaultModel` | 可能 NextChat 的 model 在 jchat 不可用，保留但前端会警告 |
| `modelConfig.temperature` | `temperature` | 0-2 |
| `modelConfig.top_p` | `topP` | 0-1 |
| `modelConfig.max_tokens` | `maxTokens` | |
| `lang` | tags 加 `lang:cn` / `lang:en` | NextChat 用 lang 区分语言模板；转 tag |
| `builtin` | 丢弃 | builtin 在 jchat 无意义（只由 owner_id=null 表达） |
| `modelConfig.presence_penalty/frequency_penalty/sendMemory/...` | 丢弃 + warn | v1 不支持 |

### 6.3 MaskImportExportService

```java
@Service @Transactional
public class MaskImportExportService {

    public ImportResult importNextChat(User user, JsonNode json) {
        var items = json.isArray() ? json : JsonNodeFactory.instance.arrayNode().add(json);
        var imported = new ArrayList<Mask>();
        var skipped = new ArrayList<SkippedEntry>();
        for (var node : items) {
            try {
                var m = toMask(user, node);
                imported.add(masks.save(m));
            } catch (Exception e) {
                skipped.add(new SkippedEntry(node.path("name").asText("?"), e.getMessage()));
            }
        }
        return new ImportResult(imported, skipped);
    }

    private Mask toMask(User user, JsonNode n) {
        var m = new Mask();
        m.setOwnerId(user.getId());
        m.setName(require(n, "name"));
        m.setAvatar(n.path("avatar").asText(null));
        m.setSystemPrompt(extractSystemPrompt(n));
        m.setDefaultModel(n.path("modelConfig").path("model").asText(null));
        m.setTemperature(nullableDouble(n, "modelConfig", "temperature"));
        m.setTopP(nullableDouble(n, "modelConfig", "top_p"));
        m.setMaxTokens(nullableInt(n, "modelConfig", "max_tokens"));
        m.setTags(extractTags(n));
        m.setIsPublic(false);   // 导入默认私有
        return m;
    }

    private String extractSystemPrompt(JsonNode n) {
        var context = n.path("context");
        if (!context.isArray()) return "";
        var parts = new ArrayList<String>();
        for (var c : context) {
            if ("system".equals(c.path("role").asText())) {
                parts.add(c.path("content").asText());
            }
            // role=user/assistant 的 context 在 v1 不支持，忽略
        }
        return String.join("\n\n", parts);
    }

    public NextChatMaskJson exportNextChat(Mask m) {
        // 反向映射；只填 NextChat 必要字段
        return NextChatMaskJson.builder()
            .id("jchat-" + m.getId())
            .name(m.getName())
            .avatar(m.getAvatar())
            .context(List.of(Map.of("role", "system", "content", m.getSystemPrompt())))
            .modelConfig(Map.of(
                "model", m.getDefaultModel(),
                "temperature", m.getTemperature(),
                "top_p", m.getTopP(),
                "max_tokens", m.getMaxTokens()
            ))
            .lang(extractLang(m.getTags()))
            .builtin(false)
            .createdAt(m.getCreatedAt().toEpochMilli())
            .build();
    }
}
```

### 6.4 JSON 导出（jchat 原生格式）

直接 Jackson 序列化 `Mask` + DTO mapper。

---

## 7. 内置 Mask 种子

### 7.1 存储位置

- 源数据：`backend/src/main/resources/masks/*.json`（每个 mask 一个文件，便于编辑）
- 迁移：`V5__seed_masks_and_plugins.sql` 里写成静态 INSERT（把 JSON 手工转换为 SQL）

### 7.2 种子 mask 清单（~20 个）

| 文件名 | name | 场景 | 默认模型 |
|---|---|---|---|
| `coder-assistant.json` | 程序员助手（通用） | 编码辅助 | gpt-4o-mini |
| `code-reviewer.json` | 代码审查员 | PR review | claude-sonnet-4-6 |
| `translator-zh-en.json` | 翻译官（中⇄英） | 精准翻译 | gpt-4o-mini |
| `text-polisher.json` | 文案润色 | 中文润色 | gpt-4o-mini |
| `academic-polisher.json` | 学术论文润色 | 正式学术语气 | claude-sonnet-4-6 |
| `marketing-copy.json` | 营销文案 | 商业推广 | gpt-4o-mini |
| `sql-expert.json` | SQL 专家 | SQL 查询与优化 | gpt-4o-mini |
| `frontend-reviewer.json` | 前端 UI 评审 | UI/UX 建议 | claude-sonnet-4-6 |
| `math-tutor.json` | 数学家教 | 推理题、公式 | o1-mini |
| `english-partner.json` | 英语对话伙伴 | 日常口语练习 | gpt-4o-mini |
| `writing-coach.json` | 写作教练 | 文章结构改进 | claude-sonnet-4-6 |
| `interviewer-frontend.json` | 面试官（前端） | 模拟面试 | gpt-4o |
| `interviewer-backend.json` | 面试官（后端） | 模拟面试 | gpt-4o |
| `interviewer-algo.json` | 面试官（算法） | 模拟面试 | gpt-4o |
| `legal-consultant.json` | 法律咨询 | 一般性问题 + **免责声明** | gpt-4o |
| `medical-consultant.json` | 医学咨询 | 一般性问题 + **免责声明** | gpt-4o |
| `brainstorm-buddy.json` | 头脑风暴伙伴 | 发散思维 | claude-opus-4-7 |
| `product-manager.json` | 产品经理 | PRD 起草 / 需求分析 | gpt-4o-mini |
| `devops-expert.json` | DevOps 专家 | 部署 / k8s / CI | claude-sonnet-4-6 |
| `learning-guide.json` | 学习计划顾问 | 学习路径规划 | gpt-4o-mini |

### 7.3 种子模板示例（`code-reviewer.json`）

```json
{
  "name": "代码审查员",
  "avatar": "🧐",
  "systemPrompt": "你是一位拥有 10 年经验的高级软件工程师，专职代码审查。\n\n审查原则：\n1. 先通览整体结构与目的，再看细节。\n2. 重点关注：正确性 > 可读性 > 性能 > 风格。\n3. 指出问题时，简述原因 + 给出修改建议。\n4. 不发表主观偏好；以行业最佳实践为依据。\n5. 对每处改动标注严重程度（Critical / Major / Minor / Nit）。\n6. 若代码整体良好，直接称赞；不编造问题。\n\n请等待用户贴出代码，然后开始审查。",
  "defaultProvider": "anthropic",
  "defaultModel": "claude-sonnet-4-6",
  "temperature": 0.3,
  "topP": 1.0,
  "maxTokens": null,
  "tags": ["code", "review", "quality"]
}
```

法律 / 医学 mask 必须带 **免责声明**：
```
⚠️ 本回答仅供参考，不构成专业法律/医疗意见。具体问题请咨询持证律师/医生。
```

### 7.4 Flyway 种子迁移生成策略

由于 JSON → SQL 手工转换繁琐，推荐**脚本化**：

```bash
# backend/scripts/gen-mask-seed.sh
python3 scripts/gen_mask_seed.py \
  --source src/main/resources/masks \
  --output src/main/resources/db/migration/V5__seed_masks_and_plugins.sql
```

`gen_mask_seed.py` 读所有 JSON，生成：

```sql
insert into masks (owner_id, name, avatar, system_prompt, default_provider, default_model,
                    temperature, top_p, max_tokens, tags, is_public, created_at, updated_at)
values
  (null, '代码审查员', '🧐', '你是一位...', 'anthropic', 'claude-sonnet-4-6', 0.3, 1.0, null, '{code,review,quality}', true, now(), now()),
  ...
;
```

每次改 JSON 后重跑脚本，然后新建 V5.1 迁移（增量）。**不能**直接改 V5（Flyway checksum）。

或者更灵活：用 Flyway Java callback 在 V5 里读 resources 并插入。实现复杂度更高但维护更方便。**v1 用脚本生成 SQL**，简单直接。

---

## 8. Controller

```java
@RestController @RequestMapping("/api/v1/masks")
@SecurityRequirement(name = "bearer")
public class MaskController {

    @GetMapping                            public CursorPage<MaskResponse> list(@CurrentUser User u, ...)    { ... }
    @PostMapping                           public MaskResponse create(@CurrentUser User u, @RequestBody CreateMaskRequest r) { ... }
    @GetMapping("/{id}")                   public MaskResponse get(@CurrentUser User u, @PathVariable Long id) { ... }
    @PatchMapping("/{id}")                 public MaskResponse update(...)                                     { ... }
    @DeleteMapping("/{id}")                public ResponseEntity<Void> delete(...)                             { ... }

    @PostMapping("/import")
    public ImportResponse importMasks(@CurrentUser User u, @RequestBody ImportRequest req) {
        if (!"nextchat".equals(req.source())) throw new ApiException(VALIDATION_FAILED, "unsupported source");
        var r = importExport.importNextChat(u, req.json());
        return ImportResponse.from(r);
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<Object> export(@CurrentUser User u, @PathVariable Long id, @RequestParam(defaultValue = "jchat") String format) {
        var m = masks.get(u, id);
        var body = "nextchat".equals(format) ? importExport.exportNextChat(m) : MaskResponse.from(m);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"mask-" + id + "." + format + ".json\"")
            .body(body);
    }
}
```

---

## 9. 前端

`frontend/src/pages/MasksPage.tsx`：

- 左侧 tag 筛选（checkbox）
- 中间卡片网格（`MaskCard`：emoji + name + tags + "基于此新建会话"按钮）
- 顶部搜索框 + 新建按钮 + 导入按钮
- 右侧可选预览抽屉（看 system_prompt 全文）
- `MaskEditor`（新建 / 编辑 dialog）：表单 + 实时预览

**导入交互**：
1. 点"导入"→ 拖拽 JSON 或粘贴文本
2. POST `/masks/import`（source=nextchat）
3. 显示导入成功 N 个，失败 M 个（带原因）

---

## 10. 测试

### 单元
- `MaskServiceTest`：CRUD、可见性（自己的 / 公开 / 系统）、修改系统 mask 应 403
- `MaskImportExportServiceTest`：
  - NextChat JSON → jchat mask：字段映射正确
  - 多个 system context 合并
  - 无 name → 抛异常（被 skip）
  - 整个数组导入：部分成功 / 部分失败

### 集成
- `MaskControllerIT`（Testcontainers）：
  - GET /masks 只列出可见
  - POST /masks 成功建自己的
  - PATCH /masks/{id} 别人的 → 403
  - DELETE 系统 mask → 403
  - POST /masks/import（NextChat JSON）→ 201 + 返回列表

---

## 11. 常见陷阱

- `text[]` 在 JPA 里映射：用 `io.hypersistence.utils.hibernate.type.array.StringArrayType` 或手动 converter。不要用 `List<String>`（没现成映射）。
- 导入时避免重复：按 `(owner_id, name)` 唯一约束？**不加**，允许同名（用户可能想做变体）。
- 系统 mask 不能被软删：`deleted_at` 也不许设。在 service 层拦截。
- `is_public = true` 的 mask 如果被 owner 删了怎么办？— 软删 → 前端也查不到。合理。
- 导入粘贴的 JSON 可能是数组也可能是单对象；适配两种。
