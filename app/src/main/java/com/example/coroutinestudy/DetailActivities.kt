package com.example.coroutinestudy

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * 투명 액티비티 생명주기 비교 실험용.
 *
 * 핵심: 투명 액티비티는 뒤 액티비티를 "완전히 가리지" 않으므로,
 *       뒤(Main) 액티비티가 onStop 에 들어가지 않는다.
 *
 *  - 불투명 Detail 진입 → [Main] onStop 찍힘
 *  - 투명   Detail 진입 → [Main] onStop 안 찍힘 (PAUSED 로만 머묾)
 *  - 복귀 시: 불투명이면 [Main] onRestart→onStart→onResume,
 *            투명이면   [Main] onResume 만
 */
abstract class BaseDetailActivity : AppCompatActivity() {

    abstract val label: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 투명 테마에서도 살짝 보이도록 라벨만 표시
        setContentView(TextView(this).apply {
            text = label
            textSize = 24f
            setPadding(40, 200, 40, 40)
        })
        Log.e(LifecycleTestApp.TAG, "[$label] onCreate()")
    }

    override fun onStart() { super.onStart(); Log.e(LifecycleTestApp.TAG, "[$label] onStart()") }
    override fun onRestart() { super.onRestart(); Log.e(LifecycleTestApp.TAG, "[$label] onRestart()") }
    override fun onResume() { super.onResume(); Log.e(LifecycleTestApp.TAG, "[$label] onResume()") }
    override fun onPause() { super.onPause(); Log.e(LifecycleTestApp.TAG, "[$label] onPause()") }
    override fun onStop() { super.onStop(); Log.e(LifecycleTestApp.TAG, "[$label] onStop()") }
    override fun onDestroy() { super.onDestroy(); Log.e(LifecycleTestApp.TAG, "[$label] onDestroy()") }
}

/** 불투명 Detail (앱 기본 테마) */
class DetailActivity : BaseDetailActivity() {
    override val label = "Detail·불투명"
}

/** 투명 Detail (Theme.Translucent) */
class TransparentDetailActivity : BaseDetailActivity() {
    override val label = "Detail·투명"
}
