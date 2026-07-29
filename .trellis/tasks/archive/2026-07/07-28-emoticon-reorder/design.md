# 技术设计：表情分类内拖拽排序

## 1. 设计原则

1. **自定义顺序不写回静态数组**：`EmoticonUtils.EMOTICON_URL` 保持为只读常量，
   自定义顺序以一份独立的「下标排列表」存在，面板读取数组的**次序**变化，数组本身不变。

   > 注意：不能用「改数组会破坏正文解码」当理由——那是错的。
   > `getPathByURI()` 是按内容线性搜索、与顺序无关，且它当前恒返回 null（见 `prd.md` Confirmed Facts）。
   > 真正的理由是下面三条：

   - `EMOTICON_URL` 是 `public static final` 的进程级共享常量。把用户偏好写进去等于制造
     全局可变单例：任何读它的代码拿到的内容取决于用户上次拖拽的时间点，可测性和可推理性都变差。
   - 写回数组意味着必须在 App 启动时、任何界面读它之前完成重排，凭空引入初始化顺序依赖
     （需等 `ContextUtils` 就绪）。当前该数组零初始化成本。
   - 「重置为内置顺序」会失去参照物——内存里的默认顺序已被覆盖，仍需另存一份默认值。

   存独立排列表则以上三个问题都不存在。
2. **纯逻辑与 Android 依赖分离**：顺序的解析/合并/移动写成不依赖 Android 的纯静态方法，
   可在 `lib_base_common` 用 JVM 单元测试覆盖；SharedPreferences 读写单独放一层。
   这一点是必要的——`PreferenceUtils` 的静态初始化块依赖 `ContextUtils.getContext()`，
   在 host JVM 测试里会炸，所以纯逻辑类**不得**引用 `PreferenceUtils`。
3. **单一事实来源**：拖拽期间 Adapter 内部只维护一个「顺序下标数组」，
   不再维护多个需要同步的平行数组，避免拖拽把 code 与 image 对错位。
4. **最小 UI 改动**：沿用现有 `ViewPager` + `RecyclerView` 结构，
   只增加 `ItemTouchHelper`，不引入并行 UI 架构（遵守 `component-guidelines.md`）。

## 2. 数据模型

### 2.1 标识

| 概念 | 取值 | 来源 |
| --- | --- | --- |
| 分类下标 | `0..5` | `EMOTICON_LABEL` 的数组下标 |
| 分类 id | `ac` / `a2` / `ng` / `pg` / `pst` / `dt` | `EMOTICON_LABEL[i][0]` |
| 表情稳定 key | 图片文件名，如 `ac0.png`、`pt00.png` | `EMOTICON_URL[i][j][1]` |

选文件名而不是表情名作为 key：两者在分类内都唯一（已校验），但文件名与 assets 目录一一对应，
即使将来官方改了中文显示名也不会导致顺序丢失。

> 提醒：`EMOTICON_URL` 第 0 列是表情名、第 1 列是文件名，而 `getFilePath()` 读的是第 0 列
> 却按 URL 处理——这是既有 bug（`prd.md` Confirmed Facts）。本任务的 key 一律取**第 1 列**，
> 不要参照 `getFilePath()` 的取列方式。

**不使用数组下标持久化**：下标会随版本增删表情而整体错位，等于顺序全乱。

### 2.2 存储

- 载体：`PreferenceUtils`（默认 SharedPreferences）。
- key：`"key_emoticon_order_" + 分类 id`，例如 `key_emoticon_order_ac`。
- value：JSON 字符串数组（fastjson），元素为文件名，如 `["ac5.png","ac0.png",...]`。
- 未自定义的分类**不写入** key。
- 体量：最大分类 65 项 × 约 10 字节 ≈ 0.7KB，6 个分类合计 < 3KB，SharedPreferences 完全够用，
  不需要走 `ForumBoardRepository` 那种文件 + 原子 rename 方案。

## 3. 组件设计

### 3.1 `EmoticonOrderResolver`（新增，纯逻辑，`lib_base_common`）

`gov.anzong.androidnga.common.util.EmoticonOrderResolver`

不依赖任何 Android 类。包级/公开静态方法：

```java
/**
 * 把保存的文件名顺序解析为对 defaultFileNames 的下标排列。
 * 返回值一定是 0..n-1 的一个完整排列。
 */
public static int[] resolve(String[] defaultFileNames, List<String> savedFileNames);

/** 把下标排列还原为文件名列表，用于持久化。 */
public static List<String> toFileNames(String[] defaultFileNames, int[] order);

/** 拖拽移动：把 from 处的元素取出并插入到 to 处，返回新数组。 */
public static int[] move(int[] order, int from, int to);
```

`resolve` 算法：

1. `savedFileNames` 为 null 或空 → 返回恒等排列 `[0,1,...,n-1]`。
2. 建 `fileName -> defaultIndex` 映射。
3. 顺序遍历 `savedFileNames`：命中映射且该下标尚未使用 → 追加到结果。
   （未命中 = 该表情已被官方移除，忽略；已使用 = 重复项，忽略。）
4. 再按内置顺序遍历 `0..n-1`，把仍未使用的下标追加到结果末尾。
   （这些是官方新增表情，按 PRD R3 追加到末尾，且彼此保持内置相对顺序。）
5. 结果长度必然等于 `n`。

`move` 语义与 `ItemTouchHelper` 的 `onMove(from, to)` 一致：删除再插入（而非交换），
配合 `notifyItemMoved` 才能得到正确的连续拖拽动画。

### 3.2 `EmoticonOrderStore`（新增，Android，`lib_base_common`）

`gov.anzong.androidnga.common.util.EmoticonOrderStore`

```java
public static int[] loadOrder(int categoryIndex);
public static void saveOrder(int categoryIndex, int[] order);
public static void resetAll();
static String prefKey(String categoryId);   // 供测试与设置页复用
```

- `loadOrder`：读 pref → `JSON.parseArray(..., String.class)` → `EmoticonOrderResolver.resolve(...)`。
  **整段 try/catch `Exception`**，任何解析异常回退到恒等排列（PRD R3 最后一条）。
- `saveOrder`：`EmoticonOrderResolver.toFileNames(...)` → `PreferenceUtils.putData(key, list)`。
  可选优化：若结果等于内置顺序则 `remove` 该 key，保持「未自定义不落盘」。
- `resetAll`：对 6 个分类 `remove` key，一次 `Editor` 提交。
- 分类下标越界时返回空数组而不是抛异常（防御）。

### 3.3 `EmoticonUtils` 增补（`lib_base_common`）

只**新增**一个只读辅助方法，不动既有数组与 `getPathByURI`：

```java
/** 返回某分类的图片文件名数组（EMOTICON_URL[category] 的第 1 列）。 */
public static String[] getFileNames(int category);
```

### 3.4 `EmoticonChildAdapter` 改造（`nga_phone_base_3.0`）

**当前**：持有 `String[] mImageUrls` + `String[] mEmotionCodes` 两个平行数组，
由 `EmoticonParentAdapter.getEmotionList()` 预先拼好。

**改造后**：只持有分类下标 + 顺序数组，code / 文件名按需从 `EmoticonUtils` 派生。
拖拽时只需要移动一个数组，从根本上消除平行数组错位的风险。

```java
private int mCategoryIndex;
private String mCategoryId;      // EMOTICON_LABEL[i][0]
private int[] mOrder;

public void setData(int categoryIndex, int[] order);
public void moveItem(int from, int to);   // mOrder = move(...); notifyItemMoved(from,to)
public int[] getOrder();                  // 返回副本，供 clearView 持久化
```

- `getItemCount()` → `mOrder == null ? 0 : mOrder.length`
- `getFileName(position)` → `mCategoryId + "/" + EMOTICON_URL[mCategoryIndex][mOrder[position]][1]`
- tag 字符串保持**逐字符不变**：
  `"[s:" + mCategoryId + ":" + 名称 + "]" + "-" + mCategoryId + "/" + 文件名`
  （对齐现有 `mEmotionCodes[position] + "-" + mCategoryName + "/" + mImageUrls[position]`。
  注意现有代码里 `mCategoryName` 传的就是 `EMOTICON_LABEL[i][0]`，语义等价。）
- 夜间模式白底判断 `switch (mCategoryId) { case "ac": case "a2": case "dt": }` 原样保留。
- `onCreateViewHolder` 的 padding、`mHeight / 3`、点击监听、`RxBus` 事件全部不变。

`EmoticonParentAdapter.getEmotionList()` 随之删除（唯一调用点消失）。

### 3.5 `EmoticonParentAdapter` 改造（`nga_phone_base_3.0`）

`instantiateItem` 中新增拖拽装配：

```java
EmoticonChildAdapter adapter = new EmoticonChildAdapter(mContext, mHeight);
adapter.setData(position, EmoticonOrderStore.loadOrder(position));
recyclerView.setAdapter(adapter);

ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP | ItemTouchHelper.DOWN
                | ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT, 0) {

    @Override public boolean onMove(rv, vh, target) {
        int from = vh.getBindingAdapterPosition();
        int to = target.getBindingAdapterPosition();
        if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false;
        adapter.moveItem(from, to);
        return true;
    }

    @Override public void onSwiped(vh, direction) { /* 不启用 */ }

    @Override public boolean isItemViewSwipeEnabled() { return false; }

    @Override public void onSelectedChanged(vh, actionState) {
        super.onSelectedChanged(vh, actionState);
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && vh != null) {
            // 拖拽期间禁止 ViewPager 抢横向手势
            vh.itemView.getParent().requestDisallowInterceptTouchEvent(true);
            vh.itemView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        }
    }

    @Override public void clearView(rv, vh) {
        super.clearView(rv, vh);
        EmoticonOrderStore.saveOrder(position, adapter.getOrder());
    }
});
helper.attachToRecyclerView(recyclerView);
```

设计要点：

- **drag 方向给全四向**：`GridLayoutManager` 下必须包含 `LEFT|RIGHT`，否则同一行内无法换位。
- **swipe 方向传 0** + `isItemViewSwipeEnabled()` 返回 `false`：明确不要滑动删除。
- **长按启动拖拽**：`SimpleCallback.isLongPressDragEnabled()` 默认 `true`，直接复用，
  与现有单击插入天然区分（PRD R1）。
- **持久化时机在 `clearView`**（拖拽结束）而不是每次 `onMove`，避免一次拖拽写十几次 pref。
- **`requestDisallowInterceptTouchEvent`**：`ItemTouchHelper` 内部虽然在部分路径会调用，
  但在 `ViewPager` 里横向拖拽仍有被翻页抢走的已知风险，显式声明一次成本极低（对应 AC5）。
- `position` 是 `instantiateItem` 的入参，即分类下标，可安全闭包捕获（`PagerAdapter` 每页一个实例）。

### 3.6 设置页（`nga_phone_base_3.0`）

- `PreferenceKey` 新增：`KEY_RESET_EMOTICON_ORDER = "key_reset_emoticon_order"`。
- `res/xml/settings.xml` 的「其他设置」分组内，紧邻「清除缓存」新增：

  ```xml
  <Preference
      android:key="key_reset_emoticon_order"
      android:summary="恢复所有表情分类的默认排列"
      android:title="重置表情顺序" />
  ```

  用 `<Preference>` 而非 `<PreferenceScreen>`：后者带 `fragment` 会被
  `SettingsFragment.onPreferenceTreeClick` 路由去开新 Activity。

- `SettingsFragment.configPreference()` 内挂点击监听，完全对齐既有「清除缓存」写法：

  ```java
  findPreference(PreferenceKey.KEY_RESET_EMOTICON_ORDER).setOnPreferenceClickListener(p -> {
      showResetEmoticonOrderDialog();
      return true;
  });
  ```

  确认框用 `AlertDialogFragment.create("确认要重置所有表情的自定义顺序吗？")`，
  positive 回调执行 `EmoticonOrderStore.resetAll()` + `ToastUtils.success("表情顺序已重置")`。
  `resetAll` 只是几次 pref 删除，无需切子线程。

## 4. 模块依赖

- `EmoticonOrderResolver` / `EmoticonOrderStore` 放 `lib_base_common`，与 `EmoticonUtils` 同包，
  不引入新的模块间依赖（`nga_phone_base_3.0` 已依赖 `lib_base_common`）。
- 无新增第三方依赖：`ItemTouchHelper` 来自已在用的 `androidx.recyclerview`，
  JSON 用已有的 fastjson。

## 5. 测试策略

### 5.1 纯 JVM 单元测试（`lib_base_common/src/test`）

`EmoticonOrderResolverTest`：

| 用例 | 期望 |
| --- | --- |
| saved 为 null / 空列表 | 恒等排列 |
| saved 是完整合法排列 | 原样采纳 |
| saved 含内置列表中不存在的文件名 | 忽略该项，其余顺序保持 |
| 内置列表新增了 saved 中没有的表情 | 新增项追加到末尾，且保持内置相对顺序 |
| saved 含重复项 | 只保留首次出现 |
| 任意输入 | 输出恒为 `0..n-1` 的完整排列（不重不漏） |
| `toFileNames` ∘ `resolve` | 往返一致 |
| `move(from<to)` / `move(from>to)` / `move(i,i)` | 元素落位正确、长度不变 |

`EmoticonUtilsContractTest`（守住关键前提，对应 AC9）：

- 每个分类内文件名唯一、表情名唯一。
- `EMOTICON_LABEL.length == EMOTICON_URL.length`。
- 每个 `EMOTICON_URL[i][j]` 长度为 2 且非空。
- `getFileNames(i)` 与 `EMOTICON_URL[i]` 第 1 列一致。

这两个测试类都不加载 Android 类，可在 host JVM 直接跑。

### 5.2 不做的测试

用户 2026-07-28 决定**不跑设备自动化测试**；项目规范
`.trellis/spec/backend/android-quality-guidelines.md` 同样禁止在未获明确授权时
编译 `androidTest` 或构建测试 APK。两者一致，因此本任务只做 host JVM 单元测试。

拖拽手势、ViewPager 手势冲突、夜间模式白底、设置页重置（AC1–AC6、AC12）
作为交付后由用户真机确认的项，实现方不得自行标记通过。

## 6. 兼容性与回滚

- **向前兼容**：老版本升级上来时不存在任何 `key_emoticon_order_*`，`resolve` 走恒等排列，
  表现与改动前完全一致。
- **向后兼容**：若回滚到旧版本 APK，遗留的 pref key 被忽略，表情面板回到内置顺序，不会崩溃。
- **数据损坏**：`loadOrder` 全量 try/catch 回退恒等排列。
- **回滚点**：改动集中在 2 个新增类 + 3 个既有文件 + 1 个 xml，
  按 `implement.md` 的提交切分可单独 revert 设置页部分或整体 revert。

## 7. 风险

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| `ViewPager` 抢横向手势导致拖拽变翻页 | AC5 不达标 | `onSelectedChanged` 显式 `requestDisallowInterceptTouchEvent(true)`；手动验证 |
| 误触长按导致顺序被无意改动 | 体验受损 | 拖拽起始有振动反馈；设置页提供重置入口 |
| `EmoticonChildAdapter` 数据源重构引入 tag 字符串偏差 | 插入的表情代码错误 | tag 拼接逐字符对齐旧实现，由实现方做代码比对（AC11）；无设备验证兜底，故此项审查必须严格 |
| 拖拽结束时 `saveOrder` 在主线程写 pref | 理论卡顿 | `PreferenceUtils` 用 `apply()` 异步落盘，数据量 < 1KB，可接受 |
