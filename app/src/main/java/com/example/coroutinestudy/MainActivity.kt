package com.example.coroutinestudy

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import kotlin.math.sin

class MainActivity : AppCompatActivity() {
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var job: Job? = null

    // 로그 출력을 위한 뷰
    private lateinit var logTextView: TextView
    private lateinit var scrollView: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.e(LifecycleTestApp.TAG, "[Main] onCreate()        화면 생성")

        // UI 생성 (XML 없이 코드로 구성)
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(0, 50, 0, 0)
        }

        // [Section 1] 좀비 작업 vs 협조적 취소
        val section1Title = TextView(this).apply { text = "--- 1. 생명주기 & 좀비 작업 ---"; textSize = 18f }
        val btnZombie = Button(this).apply { text = "비협조적 작업 시작 (Zombie)" }
        val btnCooperative = Button(this).apply { text = "협조적 작업 시작 (Good)" }
        val btnCancel = Button(this).apply { text = "작업 취소 (화면 종료 시뮬레이션)" }

        // [Section 2] 디스패처 기아 상태
        val section2Title = TextView(this).apply { text = "\n--- 2. Dispatcher 기아 상태 ---"; textSize = 18f }
        val btnStarve = Button(this).apply { text = "Default 스레드 다 쓰기 (100개)" }
        val btnUrgent = Button(this).apply { text = "긴급 작업 요청 (지연 확인)" }

        // [Section 3] 투명 vs 불투명 Activity 생명주기
        val section3Title = TextView(this).apply { text = "\n--- 3. 투명 vs 불투명 Activity ---"; textSize = 18f }
        val btnOpaqueDetail = Button(this).apply { text = "불투명 Detail 열기 (Main onStop O)" }
        val btnTransparentDetail = Button(this).apply { text = "투명 Detail 열기 (Main onStop X)" }

        // [Section 4] Dialog/BottomSheet 는 Activity 생명주기를 바꾸는가?
        val section4Title = TextView(this).apply { text = "\n--- 4. Dialog / BottomSheet (onPause 타나?) ---"; textSize = 18f }
        val btnAlertDialog = Button(this).apply { text = "AlertDialog 열기 (onPause X 예상)" }
        val btnBottomSheet = Button(this).apply { text = "BottomSheet 열기 (onPause X 예상)" }

        // 로그 영역
        logTextView = TextView(this).apply { textSize = 14f }
        scrollView = ScrollView(this).apply { addView(logTextView) }

        container.addView(section1Title)
        container.addView(btnZombie)
        container.addView(btnCooperative)
        container.addView(btnCancel)
        container.addView(section2Title)
        container.addView(btnStarve)
        container.addView(btnUrgent)
        container.addView(section3Title)
        container.addView(btnOpaqueDetail)
        container.addView(btnTransparentDetail)
        container.addView(section4Title)
        container.addView(btnAlertDialog)
        container.addView(btnBottomSheet)
        container.addView(scrollView)
        
        setContentView(container)

        // 리스너 설정
        btnZombie.setOnClickListener { startHeavyJob(cooperative = false) }
        btnCooperative.setOnClickListener { startHeavyJob(cooperative = true) }
        btnCancel.setOnClickListener { 
            log("🚫 취소 요청 보냄!")
            job?.cancel() 
        }

        btnStarve.setOnClickListener { startStarvationMode() }
        btnUrgent.setOnClickListener { requestUrgentJob() }

        // 투명/불투명 Detail 열기 → logcat(LifecycleTest 태그)에서 Main 의 onStop 유무를 비교
        btnOpaqueDetail.setOnClickListener {
            startActivity(Intent(this, DetailActivity::class.java))
        }
        btnTransparentDetail.setOnClickListener {
            startActivity(Intent(this, TransparentDetailActivity::class.java))
        }

        // Dialog / BottomSheet — 같은 Activity 윈도우에 뜨므로 Main 생명주기를 안 바꾼다(onPause 미발생) 검증
        btnAlertDialog.setOnClickListener {
            Log.e(LifecycleTestApp.TAG, "[Dialog] AlertDialog show() 호출")
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("AlertDialog")
                .setMessage("이게 떠 있는 동안 [Main] onPause 가 찍히는지 보세요")
                .setPositiveButton("닫기") { d, _ ->
                    Log.e(LifecycleTestApp.TAG, "[Dialog] AlertDialog dismiss")
                    d.dismiss()
                }
                .show()
        }
        btnBottomSheet.setOnClickListener {
            Log.e(LifecycleTestApp.TAG, "[BottomSheet] show() 호출")
            val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
            sheet.setContentView(TextView(this).apply {
                text = "BottomSheet\n\n이게 떠 있는 동안 [Main] onPause 가 찍히는지 보세요"
                textSize = 18f
                setPadding(48, 96, 48, 96)
            })
            sheet.setOnDismissListener {
                Log.e(LifecycleTestApp.TAG, "[BottomSheet] dismiss")
            }
            sheet.show()
        }
    }

    // =================================================================
    // 1. 좀비 작업 테스트 (Example 4 기반)
    // =================================================================
    class LeakedObject {
        protected fun finalize() {
            println("[GC] LeakedObject 메모리 해제됨 (정상)")
        }
    }

    private fun startHeavyJob(cooperative: Boolean) {
        job?.cancel() // 이전 작업 취소
        logTextView.text = "" // 로그 초기화
        
        val leaked = LeakedObject() // 메모리 누수 감시용
        
        log("작업 시작 (협조적 모드: $cooperative)")
        
        job = lifecycleScope.launch {
            withContext(Dispatchers.Default) {
                val modeName = if(cooperative) "협조적" else "비협조적(Zombie)"
                log("[${Thread.currentThread().name}] $modeName 작업 시작... (3초 소요)")
                
                val endTime = System.currentTimeMillis() + 3000
                while (System.currentTimeMillis() < endTime) {
                    // [핵심 차이] 협조적인 코드는 여기서 멈춥니다.
                    if (cooperative) {
                        ensureActive()
                    }
                    
                    // 무거운 연산 (Busy Wait)
                    Math.sin(Math.random())
                }
                
                // 여기가 실행된다는 건 취소가 안 됐다는 뜻
                log("⚠️ [${Thread.currentThread().name}] 작업 완료! (취소 실패)")
                log("참조 중인 객체: $leaked")
            }
        }
    }

    // =================================================================
    // 2. 기아 상태 테스트 (Example 5 기반)
    // =================================================================
    private fun startStarvationMode() {
        logTextView.text = ""
        log("🔥 스레드 고갈 공격 시작 (100개 작업)...")
        
        // Default Dispatcher를 꽉 채우는 100개의 작업 생성
        repeat(100) { i ->
            lifecycleScope.launch(Dispatchers.Default) {
                // withContext(Dispatchers.Default)를 중첩해도 마찬가지
                val endTime = System.currentTimeMillis() + 2000 // 2초간 점유
                while(System.currentTimeMillis() < endTime) {
                    Math.sin(Math.random())
                }
            }
        }
        log("🔥 공격 실행 중! 이제 '긴급 작업'을 눌러보세요.")
    }
    
    private fun requestUrgentJob() {
        val requestTime = System.currentTimeMillis()
        log("⏰ 긴급 작업 요청함!")
        
        lifecycleScope.launch(Dispatchers.Default) {
            val executionTime = System.currentTimeMillis()
            log("✅ 긴급 작업 실행됨! (지연: ${executionTime - requestTime}ms)")
        }
    }

    private fun log(msg: String) {
        runOnUiThread {
            logTextView.append(msg + "\n")
            scrollView.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }
    
    override fun onStart() {
        super.onStart()
        Log.e(LifecycleTestApp.TAG, "[Main] onStart()         화면 보이기 시작")
    }

    override fun onRestart() {
        super.onRestart()
        Log.e(LifecycleTestApp.TAG, "[Main] onRestart()       중지 후 재시작")
    }

    override fun onResume() {
        super.onResume()
        Log.e(LifecycleTestApp.TAG, "[Main] onResume()        포그라운드 (상호작용 가능)")
    }

    override fun onPause() {
        super.onPause()
        Log.e(LifecycleTestApp.TAG, "[Main] onPause()         포커스 잃음 (부분 가림)")
    }

    override fun onStop() {
        super.onStop()
        Log.e(LifecycleTestApp.TAG, "[Main] onStop()          완전히 안 보임")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.e(LifecycleTestApp.TAG, "[Main] onDestroy()       화면 소멸")
        scope.cancel()
    }
}
