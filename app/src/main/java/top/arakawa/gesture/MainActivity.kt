package top.arakawa.gesture

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.google.android.material.snackbar.Snackbar
import top.arakawa.gesture.MultiFingerSwipeDetector
import top.arakawa.gesture.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var multiFingerSwipeDetector: MultiFingerSwipeDetector

    private var lastTraceLine: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        multiFingerSwipeDetector = MultiFingerSwipeDetector(
            this,
            onResult = { result ->
                val gesture = result.gesture
                val metrics = result.metrics
                if (metrics != null) {
                    binding.gestureDebugDetail.visibility = android.view.View.VISIBLE
                    val p = metrics.primaryPx?.toInt()?.toString() ?: "-"
                    val o = metrics.offAxisPx?.toInt()?.toString() ?: "-"
                    val opp = metrics.oppositePx?.toInt()?.toString() ?: "-"
                    val end = metrics.endPrimaryPx?.toInt()?.toString() ?: "-"
                    val lin = metrics.linearity?.let { String.format("%.2f", it) } ?: "-"
                    binding.gestureDebugDetail.text =
                        "t=${metrics.durationMs}ms d=${metrics.netDistancePx.toInt()}px path=${metrics.totalPathPx.toInt()}px p=$p o=$o opp=$opp end=$end lin=$lin"
                } else {
                    binding.gestureDebugDetail.visibility = android.view.View.GONE
                    binding.gestureDebugDetail.text = ""
                }

                when (result.kind) {
                    MultiFingerSwipeDetector.Kind.LongPress -> {
                        if (gesture != null) {
                            binding.gestureDebugText.text = "${gesture.fingers}指 长按"
                        } else {
                            val reasonText = when (result.invalidReason) {
                                MultiFingerSwipeDetector.InvalidReason.HoldTooShort -> "未满5秒"
                                MultiFingerSwipeDetector.InvalidReason.Moved -> "发生移动"
                                MultiFingerSwipeDetector.InvalidReason.FingerChanged -> "手指数变化"
                                MultiFingerSwipeDetector.InvalidReason.Incomplete, null -> "数据不足"
                                else -> "无效"
                            }
                            binding.gestureDebugText.text =
                                "${result.fingers}指 长按无效：$reasonText"
                        }
                    }

                    MultiFingerSwipeDetector.Kind.Swipe -> {
                        if (gesture != null) {
                            val directionText = when (gesture.direction) {
                                MultiFingerSwipeDetector.Direction.Up -> "上滑"
                                MultiFingerSwipeDetector.Direction.Down -> "下滑"
                                MultiFingerSwipeDetector.Direction.Left -> "左滑"
                                MultiFingerSwipeDetector.Direction.Right -> "右滑"
                                null -> "滑动"
                            }
                            binding.gestureDebugText.text = "${gesture.fingers}指 $directionText"
                        } else {
                            val reasonText = when (result.invalidReason) {
                                MultiFingerSwipeDetector.InvalidReason.TooShort -> "位移太短"
                                MultiFingerSwipeDetector.InvalidReason.OffAxis -> "偏移太大"
                                MultiFingerSwipeDetector.InvalidReason.Reversed -> "中途反向"
                                MultiFingerSwipeDetector.InvalidReason.EndMismatch -> "回弹太多"
                                MultiFingerSwipeDetector.InvalidReason.Wiggly -> "轨迹不直"
                                MultiFingerSwipeDetector.InvalidReason.Incomplete, null -> "数据不足"
                                else -> "无效"
                            }

                            val candidateText = when (result.candidateDirection) {
                                MultiFingerSwipeDetector.Direction.Up -> "上滑"
                                MultiFingerSwipeDetector.Direction.Down -> "下滑"
                                MultiFingerSwipeDetector.Direction.Left -> "左滑"
                                MultiFingerSwipeDetector.Direction.Right -> "右滑"
                                null -> null
                            }

                            binding.gestureDebugText.text = if (candidateText != null) {
                                "${result.fingers}指 滑动无效：$reasonText（候选：$candidateText）"
                            } else {
                                "${result.fingers}指 滑动无效：$reasonText"
                            }
                        }
                    }
                }
            },
            onTrace = { trace ->
                // For custom gesture recognition: trace.samples contains the centroid path.
                // This app just shows basic trace stats.
                lastTraceLine =
                    "trace points=${trace.samples.size} t=${trace.endTimeMs - trace.startTimeMs}ms d=${trace.netDistancePx.toInt()}px path=${trace.totalPathPx.toInt()}px"

                binding.gestureDebugDetail.visibility = android.view.View.VISIBLE
                val current = binding.gestureDebugDetail.text?.toString().orEmpty().trim()
                val traceLine = lastTraceLine.orEmpty()
                binding.gestureDebugDetail.text = when {
                    current.isEmpty() -> traceLine
                    traceLine.isEmpty() -> current
                    else -> "$current | $traceLine"
                }
            },
        )

        binding.fab.setOnClickListener { view ->
            binding.gestureDebugText.text = "等待手势（3/4/5指 上/下/左/右滑，抬手触发）"
            binding.gestureDebugDetail.visibility = android.view.View.GONE
            binding.gestureDebugDetail.text = ""
            Snackbar.make(view, "已清空手势显示", Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (multiFingerSwipeDetector.onTouchEvent(ev)) {
            return true
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }
}
