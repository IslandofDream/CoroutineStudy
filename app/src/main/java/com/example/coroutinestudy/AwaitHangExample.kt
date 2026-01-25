package com.example.coroutinestudy

import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.*

/**
 * await() 호출 시 대상 작업이 완료되지 않으면 발생하는 현상을 다룹니다.
 */
object AwaitHangExample {

    @RequiresApi(Build.VERSION_CODES.O)
    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        println("=== await() Hanging 예제 시작 ===")

        // 1. 끝나지 않는 작업 생성 (예: 무한루프나 아주 긴 delay)
        val foreverJob = async {
            val formatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")
            println("   (Async) foreverJob 시작됨... 1분마다 생존 로그를 출력합니다.")
            while (isActive) {
                delay(60_000) // 1분 대기
                val now = java.time.LocalDateTime.now().format(formatter)
                println("   [$now] (Async) 1분이 지났습니다. 여전히 작업 중입니다... (끝나지 않음)")
            }
            "결과값"
        }

        try {
            println("main: 1. withTimeout 없이 await()를 호출하면 영원히 멈춥니다.")
//            println("main: 2. 테스트를 위해 3초 타임아웃을 걸고 호출해봅니다.")
//
//            // withTimeout이 없으면 이 라인 아래는 절대 실행되지 않음
//            withTimeout(3000) {
//                println("   (Main) await() 호출 시도...")
//                val result = foreverJob.await() // 여기서 멈춤! 영원히 리턴되지 않음.
//                println("   (Main) await() 호출 후: 이 로그는 절대 찍히지 않음. 값: $result")
//            }
        } catch (e: TimeoutCancellationException) {
            println("\n[타임아웃 발생!]")
            println("   -> await() 호출 부에서 코드 실행이 멈춰있었음을 확인했습니다.")
            println("   -> 3초가 지나서 강제로 취소되었습니다.")
        }
    }
}
