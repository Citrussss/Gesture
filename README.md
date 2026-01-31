# Gesture

一个 Android Demo：识别 3/4/5 指手势（滑动 + 长按）。

核心实现：`app/src/main/java/top/arakawa/gesture/MultiFingerSwipeDetector.kt`

接入示例：`app/src/main/java/top/arakawa/gesture/MainActivity.kt`（通过重写 `dispatchTouchEvent` 拦截触摸事件）。

## 支持的手势

- 3/4/5 指滑动：上 / 下 / 左 / 右
- 3/4/5 指长按：持续按住 5 秒

## 长按判定规则

- 连续按住满 5 秒才触发（`longPressTimeoutMs = 5000`）。
- 长按期间如果发生移动（以"质心"位移计算），超过 `touchSlop` 直接判定长按无效（`longPressMoveTolerancePx = touchSlopPx`）。
- 长按期间如果手指数发生变化（多按一指 / 少一指），直接判定长按无效（`InvalidReason.FingerChanged`）。
- 长按触发后，会消费后续事件并在抬手时重置状态。

## 滑动判定规则

滑动使用"当前跟踪手指集合的质心（centroid）"作为轨迹点。

方向选择：基于相对起点的"峰值位移"（而不是抬手时最终位置）确定候选方向；之后再做一系列严格校验，过滤掉偏轴过大、反向、回弹过多、轨迹不直等情况。

关键阈值（见 `MultiFingerSwipeDetector.kt`）：

- 最小位移：`minDistancePx = touchSlop * 7`
- 最大偏轴比例：`maxOffAxisRatio = 0.25`
- 反向判定：`reverseMinDistancePx = touchSlop * 1.5` 且 `reverseMaxRatio = 0.15`
- 回弹判定：`endKeepRatio = 0.8`（抬手时的净位移必须保留峰值的 80% 以上）
- 线性度（直线程度）：`minLinearityRatio = 0.88`，其中 `linearity = netDistance / totalPath`

## 质心轨迹采样

从 `startTracking` 到识别结束（成功或失败），系统会持续记录质心位置：

- `CentroidSample`：单次采样点（时间戳 tMs、x、y）
- `CentroidTrace`：完整轨迹数据
  - `fingers`：手指数
  - `startTimeMs / endTimeMs`：起止时间
  - `samples`：采样点列表（避免过密采样；内存上限 800 个点，超过时丢弃旧点）
  - `netDistancePx`：起点到终点的直线距离
  - `totalPathPx`：总路径长度（所有采样段长度累加）
  - `endKind`：结束原因（`Swipe` / `LongPress`）
  - `endInvalidReason`：失败原因（仅当识别失败时非空）

采样策略：在每次 `ACTION_MOVE`、`ACTION_POINTER_UP`、`ACTION_UP` 时记录当前质心；同时间戳不重复采样。

用途：支持扩展自定义手势识别算法（如签名、复杂图形等）。

## 如何使用

Demo 在 `dispatchTouchEvent` 中优先让 detector 处理触摸事件；当 detector 返回 `true` 时，表示它正在识别或已经消费了该事件。

```kotlin
private lateinit var detector: MultiFingerSwipeDetector

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    detector = MultiFingerSwipeDetector(
        context = this,
        onResult = { result ->
            // result.kind: Swipe / LongPress
            // result.gesture != null => 识别成功
            // result.invalidReason != null => 被判定为无效（包含原因）
            // result.metrics => 调试用指标
        },
        onTrace = { trace ->
            // trace.samples: 完整质心轨迹（用于自定义手势识别）
            // trace.endKind: Swipe / LongPress
            // trace.endInvalidReason: 失败原因（若非空）
        },
    )
}

override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
    if (detector.onTouchEvent(ev)) return true
    return super.dispatchTouchEvent(ev)
}
```

## Result 数据结构

`MultiFingerSwipeDetector.Result` 包含：

- `kind`：`Swipe` 或 `LongPress`
- `gesture`：识别成功时非空；滑动手势会带 `direction`
- `invalidReason`：无效原因（例如 `TooShort`、`OffAxis`、`Moved`、`FingerChanged`）
- `metrics`：调试指标（持续时间、位移、轨迹长度、线性度等）

## 运行

用 Android Studio 打开工程并运行 `app` 模块即可。
