package com.example.coroutinestudy

import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * 테스트 1: 같은 디스패처로 재진입 (Re-dispatching)
 * 질문: myDispatcher1 -> myDispatcher1 로 전환하면 어떻게 되나요?
 *
 */
object Test1_SameDispatcher {
    @JvmStatic
    fun main(args: Array<String>) = runBlocking<Unit> {
        println("=== 테스트 1: 같은 디스패처로 재진입 실험 ===")
        val myDispatcher1 = newSingleThreadContext("MyThread1")

        // 우리는 main 스레드에서 시작합니다
        println("[${Thread.currentThread().name}] 시작 (Outer)")

        withContext(myDispatcher1) {
            println("[${Thread.currentThread().name}] 레벨 1 진입 (myDispatcher1)")

            // 같은 디스패처로 중첩 호출 (Nested call)
            // 코틀린은 이를 최적화하여 불필요한 스레드 전환 오버헤드를 줄입니다.
            // 실행 중인 스레드(MyThread1)가 그대로 안쪽 블록을 실행합니다.
            withContext(myDispatcher1) {
                println("[${Thread.currentThread().name}] 레벨 2 진입 (myDispatcher1) - 중첩됨")
            }

            println("[${Thread.currentThread().name}] 레벨 1 복귀")
        }
        println("[${Thread.currentThread().name}] 종료 (Outer)")
    }
}

/**
 * 테스트 2: Dispatchers.IO 깊은 중첩 (스레드 풀 한도 초과 테스트)
 * 질문: "64개 스레드 한도를 넘어서 중첩 호출하면 어떻게 되나요?"
 *
 */
object Test2_DeepNestedIO {
    @JvmStatic
    fun main(args: Array<String>) = runBlocking<Unit> {
        val maxDepth = 100
        println("=== 테스트 2: Dispatchers.IO ${maxDepth}단계 중첩 (스레드 풀 한도 초과 실험) ===")
        println("참고: Dispatchers.IO는 한도(기본 64)가 있습니다. 만약 working 스레드를 '점유(Block)'한다면 64번째에서 멈춰야 합니다.")
        println("하지만 withContext는 호출자를 '일시 중단(Suspend)'시키고 스레드를 풀에 반납하므로 멈추지 않습니다.")

        val counter = AtomicInteger(0)

        // withContext(Dispatchers.IO)를 재귀적으로 중첩 호출하는 함수
        suspend fun nestedCall(depth: Int) {
            if (depth > maxDepth) {
                println("[${Thread.currentThread().name}] 최대 깊이 $maxDepth 도달!")
                return
            }

            withContext(Dispatchers.IO) {
                val current = counter.incrementAndGet()
                val activeThreadCount = Thread.activeCount()
                println("[${Thread.currentThread().name}] 깊이 $depth (현재 활성 스레드 수 추정: ~$current, JVM 전체 활성 스레드: $activeThreadCount)")

                nestedCall(depth + 1)

                counter.decrementAndGet()
            }
        }

        val time = kotlin.system.measureTimeMillis {
            nestedCall(1)
        }

        println("100단계 깊이 완료 소요 시간: ${time}ms - 멈추지(Hang) 않고 성공!")
        println("=> 결론: 깊이가 100이 넘어가도 스레드 개수가 폭발적으로 늘어나지 않음을 확인했습니다. (Blocking이었다면 이미 고갈됨)")
    }
}

/**
 * 테스트 3: 최적화 검증 (Optimization Verification)
 * 질문: "정말로 같은 디스패처면 dispatch를 안 하나요?"
 *
 * 이 테스트는 커스텀 디스패처를 만들어 dispatch 호출 횟수를 셉니다.
 *
 */
object Test3_OptimizationCheck {
    @JvmStatic
    fun main(args: Array<String>) = runBlocking<Unit> {
        println("=== 테스트 3: 디스패처 최적화 검증 (dispatch 호출 횟수 세기) ===")
        val counter = AtomicInteger(0)

        // dispatch 호출될 때마다 카운트를 세는 래퍼 디스패처
        val trackingDispatcher = object : CoroutineDispatcher() {
            // 실제 실행은 이 친구에게 위임
            val actualDispatcher = newSingleThreadContext("TrackingThread")

            override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) {
                val c = counter.incrementAndGet()
                println("[Dispatch 발생] #$c (작업을 스레드 풀에 던짐)")
                actualDispatcher.dispatch(context, block)
            }
            
            // 리소스 정리를 위한 함수 노출
            fun close() = actualDispatcher.close()
        }

        println("1. 첫 번째 withContext 진입 전 (예상 Dispatch: +1)")
        withContext(trackingDispatcher) {
             println("   -> 첫 번째 블록 실행 중 (현재 Dispatch 누적 횟수: ${counter.get()})")

             println("2. 두 번째(중첩) withContext 진입 전 (예상 Dispatch: +0 (최적화))")
             withContext(trackingDispatcher) {
                 println("      -> 두 번째 블록 실행 중 (현재 Dispatch 누적 횟수: ${counter.get()})")
                 
                 if (counter.get() == 1) {
                     println("      [성공] Dispatch 횟수가 늘어나지 않았습니다! (코틀린 최적화 동작 확인)")
                 } else {
                     println("      [실패?] Dispatch 횟수가 늘어났습니다. (${counter.get()})")
                 }
             }
             println("   -> 첫 번째 블록 복귀")
        }
        
        trackingDispatcher.close()
        println("테스트 3 완료")
    }
}
