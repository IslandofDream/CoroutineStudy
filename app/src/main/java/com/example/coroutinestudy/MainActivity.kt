package com.example.coroutinestudy

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 간단한 UI 생성 (Button & TextView)
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
        }
        val statusText = TextView(this).apply {
            text = "버튼을 눌러 테스트하세요."
            textSize = 16f
            setPadding(20, 20, 20, 50)
        }
        val anrButton = Button(this).apply {
            text = "ANR 유발하기 (5초 멈춤)"
        }
        val cancelTestButton = Button(this).apply {
            text = "취소 안되는 작업 (IO + Sleep)"
        }
        val lifecycleTestButton = Button(this).apply {
            text = "LifecycleScope + CPU Loop (No Sleep)"
        }
        val immediateTestButton = Button(this).apply {
             text = "Main vs Main.immediate 차이 확인"
        }
        
        container.addView(statusText)
        container.addView(anrButton)
        container.addView(cancelTestButton)
        container.addView(lifecycleTestButton)
        container.addView(immediateTestButton)
        setContentView(container)

        anrButton.setOnClickListener {
            triggerANR(statusText)
        }
        
        cancelTestButton.setOnClickListener {
            triggerNonCancellableWork(statusText)
        }
        
        lifecycleTestButton.setOnClickListener {
            testLifecycleScopeIssue(statusText)
        }
        
        immediateTestButton.setOnClickListener {
            testMainImmediateDifference(statusText)
        }
    }

    private fun triggerANR(textView: TextView) {
        scope.launch {
            textView.text = "ANR 시작! 5초간 UI가 멈춥니다..."
            delay(100) 
            withContext(Dispatchers.Main) {
                Thread.sleep(5000) 
            }
            textView.text = "ANR 종료! 이제 UI가 다시 반응합니다."
        }
    }

    private fun triggerNonCancellableWork(textView: TextView) {
        val job = scope.launch {
            textView.text = "작업 시작 (3초 소요 예정)..."
            withContext(Dispatchers.IO) {
                val start = System.currentTimeMillis()
                Thread.sleep(3000)
                val end = System.currentTimeMillis()
                
                withContext(Dispatchers.Main + NonCancellable) {
                    textView.text = "작업 완료 (취소 실패!) 소요: ${end - start}ms\n(이 메시지가 뜨면 백그라운드 작업이 취소되지 않고 끝까지 돈 것입니다)"
                }
            }
        }
        
        scope.launch {
            delay(500)
            textView.text = "취소 시도!"
            job.cancel() 
            textView.append("\n취소 요청 보냄.")
        }
    }

    private fun testLifecycleScopeIssue(textView: TextView) {
        // [사용자 요청] lifecycleScope를 사용하고, Thread.sleep 대신 실제 CPU 연산 사용
        val job = lifecycleScope.launch {
            textView.text = "LifecycleScope: CPU 연산 시작 (3초)..."
            
            // Dispatchers.Default (CPU 연산용 스레드)
            withContext(Dispatchers.Default) {
                val startTime = System.currentTimeMillis()
                val endTime = startTime + 3000
                
                // [문제의 핵심]
                // while 루프가 돌아가는 동안 코루틴 취소 여부(isActive)를 전혀 체크하지 않음.
                // Thread.sleep()을 쓰지 않고 실제로 CPU를 계속 태우는 상황.
                // 이 경우에도 cancel 신호가 들어오면 '멈춰야' 하지만, 검사를 안 하니 못 멈춤.
                while (System.currentTimeMillis() < endTime) {
                    // CPU Busy Wait: 단순 연산 반복
                    // 만약 여기서 ensureActive()나 isActive 체크를 넣었다면 취소되었을 것임.
                    
                    // 아주 무거운 수학 연산 등을 상상하시면 됩니다.
                    Math.sin(System.currentTimeMillis().toDouble())
                }
                
                // 여기까지 도달했다면 취소에 실패한 것임 (좀비 작업)
                withContext(Dispatchers.Main + NonCancellable) {
                     textView.append("\n[결과] 작업 종료! (좀비처럼 살아남음 🧟‍♂️)")
                     textView.append("\n소요 시간: ${System.currentTimeMillis() - startTime}ms")
                }
            }
        }
        
        // 1초 뒤에 Activity가 종료되었다고 가정하고 취소
        lifecycleScope.launch {
            delay(1000)
            textView.append("\n[이벤트] 1초 경과: 화면 종료 시뮬레이션 (Job Cancel)")
            // 실제 onDestroy가 불리면 lifecycleScope 전체가 cancel 됩니다.
            // 여기서는 job을 취소하여 그 상황을 재현합니다.
            job.cancel() 
        }
    }
    
    private fun testMainImmediateDifference(textView: TextView) {
        textView.text = "테스트 시작...\n"
        
        scope.launch {
            // 비교 1: Dispatchers.Main (Standard)
            textView.append("1. [Standard Main] 테스트 준비\n")
            Handler(Looper.getMainLooper()).post {
                textView.append("   -> (가로채기) Handler에 의해 예약된 작업 실행됨\n")
            }
            
            withContext(Dispatchers.Main) {
                textView.append("   -> withContext(Main) 실행됨 (순서가 밀렸을 가능성 높음)\n")
            }
            
            textView.append("--------------------------------\n")
            
            // 비교 2: Dispatchers.Main.immediate (Optimized)
            textView.append("2. [Immediate Main] 테스트 준비\n")
             Handler(Looper.getMainLooper()).post {
                textView.append("   -> (가로채기 실패?) Handler에 의해 예약된 작업이 나중에 실행됨\n")
            }
            
            withContext(Dispatchers.Main.immediate) {
                textView.append("   -> withContext(Main.immediate) 실행됨 (즉시 실행!)\n")
            }
            
            delay(100)
            textView.append("--------------------------------\n테스트 완료. 순서를 확인하세요.")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
