package top.arakawa.gesture

import android.os.Bundle
import android.view.MotionEvent
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import android.view.Menu
import android.view.MenuItem
import top.arakawa.gesture.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var multiFingerSwipeDetector: MultiFingerSwipeDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        multiFingerSwipeDetector = MultiFingerSwipeDetector(this) { result ->
            val gesture = result.gesture
            val metrics = result.metrics
            if (metrics != null) {
                binding.gestureDebugDetail.visibility = android.view.View.VISIBLE
                binding.gestureDebugDetail.text =
                    "p=${metrics.primaryPx.toInt()}px o=${metrics.offAxisPx.toInt()}px opp=${metrics.oppositePx.toInt()}px end=${metrics.endPrimaryPx.toInt()}px lin=${String.format("%.2f", metrics.linearity)} t=${metrics.durationMs}ms"
            } else {
                binding.gestureDebugDetail.visibility = android.view.View.GONE
                binding.gestureDebugDetail.text = ""
            }

            if (gesture != null) {
                val directionText = when (gesture.direction) {
                    MultiFingerSwipeDetector.Direction.Up -> "上滑"
                    MultiFingerSwipeDetector.Direction.Down -> "下滑"
                    MultiFingerSwipeDetector.Direction.Left -> "左滑"
                    MultiFingerSwipeDetector.Direction.Right -> "右滑"
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
                }

                val candidateText = when (result.candidateDirection) {
                    MultiFingerSwipeDetector.Direction.Up -> "上滑"
                    MultiFingerSwipeDetector.Direction.Down -> "下滑"
                    MultiFingerSwipeDetector.Direction.Left -> "左滑"
                    MultiFingerSwipeDetector.Direction.Right -> "右滑"
                    null -> null
                }

                binding.gestureDebugText.text = if (candidateText != null) {
                    "${result.fingers}指 无效：$reasonText（候选：$candidateText）"
                } else {
                    "${result.fingers}指 无效：$reasonText"
                }
            }
        }

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
