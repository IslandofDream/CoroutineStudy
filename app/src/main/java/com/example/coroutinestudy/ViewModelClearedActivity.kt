package com.example.coroutinestudy

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel

/**
 * "비정상 종료(크래시 / 프로세스 종료 / 활동 유지 안함)에서 ViewModel.onCleared() 가 호출되는가" 실험용.
 *
 * 판정 기준은 하나다. onCleared() 는 ViewModelStore.clear() 에서만 호출되고,
 * clear() 는 ComponentActivity 가 ON_DESTROY 를 받았을 때 isChangingConfigurations == false 인 경우에만 실행된다.
 * 그래서 onDestroy 로그에 isChangingConfigurations / isFinishing 값을 같이 찍는다.
 *
 * 기대 결과 (scripts/viewmodel_cleared_test.sh 로 확인):
 *  - BACK(finish)            : onDestroy(changingConfig=false, finishing=true)  → onCleared O, 재진입 시 새 VM
 *  - 회전                     : onDestroy(changingConfig=true,  finishing=false) → onCleared X, 같은 VM id 유지
 *  - 활동 유지 안함 + HOME     : onDestroy(changingConfig=false, finishing=false) → onCleared O (프로세스는 살아있음)
 *  - HOME 후 am kill          : onDestroy 자체가 없음 → onCleared X, PID 변경, SavedStateHandle 값만 복원
 *  - 크래시                   : onDestroy 자체가 없음 → onCleared X
 */
class ClearedTestViewModel(private val handle: SavedStateHandle) : ViewModel() {

    val id: String = Integer.toHexString(System.identityHashCode(this))

    /** 메모리에만 있는 값. ViewModel 인스턴스가 바뀌면 0 으로 돌아간다. */
    var memoryCount = 0

    /** SavedStateHandle 에 있는 값. 프로세스가 죽어도 onSaveInstanceState 가 돌았으면 복원된다. */
    var savedCount: Int
        get() = handle[KEY_COUNT] ?: 0
        set(value) { handle[KEY_COUNT] = value }

    init {
        Log.e(LifecycleTestApp.TAG, "[VM] init            id=$id  savedCount=$savedCount (0 이 아니면 SavedStateHandle 복원)")
    }

    override fun onCleared() {
        super.onCleared()
        Log.e(LifecycleTestApp.TAG, "[VM] ★ onCleared()   id=$id")
    }

    companion object {
        private const val KEY_COUNT = "count"
    }
}

class ViewModelClearedActivity : AppCompatActivity() {

    private val viewModel: ClearedTestViewModel by viewModels()
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.e(LifecycleTestApp.TAG, "[VMAct] onCreate()     savedInstanceState=${if (savedInstanceState == null) "null(첫 생성)" else "있음(복원)"}  pid=${android.os.Process.myPid()}")

        status = TextView(this).apply { textSize = 16f; setPadding(40, 40, 40, 40) }
        val btnCount = Button(this).apply { text = "카운트 +1 (memory / SavedStateHandle 둘 다)" }
        val btnRotate = Button(this).apply { text = "회전 (구성 변경 → onCleared 안 불림)" }
        val btnFinish = Button(this).apply { text = "finish() (정상 종료 → onCleared 불림)" }
        val btnCrash = Button(this).apply { text = "크래시 (RuntimeException → onCleared 안 불림)" }
        val guide = TextView(this).apply {
            textSize = 13f
            setPadding(40, 40, 40, 40)
            text = """
                나머지 시나리오는 adb 로:
                · 활동 유지 안함: settings put global always_finish_activities 1 → HOME
                · 프로세스 종료:   HOME → am kill com.example.coroutinestudy
                scripts/viewmodel_cleared_test.sh 가 전부 순서대로 실행한다.
            """.trimIndent()
        }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 50, 0, 0)
            addView(status)
            addView(btnCount)
            addView(btnRotate)
            addView(btnFinish)
            addView(btnCrash)
            addView(guide)
        })

        btnCount.setOnClickListener {
            viewModel.memoryCount += 1
            viewModel.savedCount += 1
            render()
        }
        btnRotate.setOnClickListener {
            requestedOrientation = if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT) {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
        btnFinish.setOnClickListener { finish() }
        btnCrash.setOnClickListener { crash() }

        // 스크립트에서 `am start --ez crash true` 로 띄우면 화면이 뜬 직후 크래시
        if (intent.getBooleanExtra(EXTRA_CRASH, false)) {
            window.decorView.post { crash() }
        }
        render()
    }

    private fun crash() {
        Log.e(LifecycleTestApp.TAG, "[VMAct] 지금 크래시 발생시킴 (이 뒤에 onDestroy / onCleared 가 없어야 정상)")
        throw RuntimeException("실험용 크래시: onCleared 가 호출되지 않아야 한다")
    }

    private fun render() {
        status.text = "ViewModel id = ${viewModel.id}\nmemoryCount = ${viewModel.memoryCount}\nsavedCount  = ${viewModel.savedCount}\npid = ${android.os.Process.myPid()}"
    }

    override fun onStart() { super.onStart(); Log.e(LifecycleTestApp.TAG, "[VMAct] onStart()") }
    override fun onResume() { super.onResume(); Log.e(LifecycleTestApp.TAG, "[VMAct] onResume()      vm=${viewModel.id} memory=${viewModel.memoryCount} saved=${viewModel.savedCount}") }
    override fun onPause() { super.onPause(); Log.e(LifecycleTestApp.TAG, "[VMAct] onPause()") }
    override fun onStop() { super.onStop(); Log.e(LifecycleTestApp.TAG, "[VMAct] onStop()") }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        Log.e(LifecycleTestApp.TAG, "[VMAct] onSaveInstanceState()  (SavedStateHandle 내용이 여기서 Bundle 로 나감)")
    }

    override fun onDestroy() {
        // super.onDestroy() 안에서 ON_DESTROY 가 디스패치되고, 그때 ViewModelStore.clear() 여부가 결정된다.
        Log.e(LifecycleTestApp.TAG, "[VMAct] onDestroy()    isChangingConfigurations=$isChangingConfigurations  isFinishing=$isFinishing  → super 호출 직후 onCleared 유무를 보라")
        super.onDestroy()
    }

    companion object {
        const val EXTRA_CRASH = "crash"
    }
}
