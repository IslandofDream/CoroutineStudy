package com.example.coroutinestudy

import kotlinx.coroutines.*
import kotlin.system.measureTimeMillis

// 공통 유틸리티 함수
fun getElapsedTime(startTime: Long): String = "지난 시간: ${System.currentTimeMillis() - startTime}ms"

/**
 * 예제 1: 순차적 실행 (Sequential Execution)
 * withContext는 중단점(suspension point)을 생성하므로, 해당 블록이 완료될 때까지 다음 라인이 실행되지 않고 기다립니다.
 * Dispatchers.IO를 사용하더라도, 같은 코루틴 내에서는 호출이 직렬화(순차 실행)됩니다.
 *
 */
object Example1_SequentialExecution {
    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        println("=== 1. WithContext 순차 실행 예제 ===")
        val startTime = System.currentTimeMillis()
        val helloString = withContext(Dispatchers.IO) {
            delay(1000L)
            return@withContext "Hello"
        }

        val worldString = withContext(Dispatchers.IO) {
            delay(1000L)
            return@withContext "World"
        }

        println("[${getElapsedTime(startTime)}] ${helloString} ${worldString}")
    }
}

/**
 * 예제 2: Async를 이용한 병렬 실행 (Parallel Execution)
 * async를 사용하면 작업이 즉시 시작되며 병렬(동시)로 실행될 수 있습니다.
 *
 */
object Example2_ParallelExecution {
    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        println("=== 2. Async 병렬 실행 예제 ===")
        val startTime = System.currentTimeMillis()
        val helloDeferred = async(Dispatchers.IO) {
            delay(1000L)
            return@async "Hello"
        }

        val worldDeferred = async(Dispatchers.IO) {
            delay(1000L)
            return@async "World"
        }

        val results = awaitAll(helloDeferred, worldDeferred)

        println("[${getElapsedTime(startTime)}] ${results[0]} ${results[1]}")
    }
}

/**
 * 예제 3: 컨텍스트 스위칭 오버헤드 (Context Switching Overhead)
 * 빈번한 컨텍스트 스위칭(예: Main -> IO -> Main)은 오버헤드를 발생시킵니다.
 * 이 예제는 단일 컨텍스트에서 작업하는 것과 불필요하게 스위칭하는 것의 시간 차이를 측정합니다.
 *
 */
object Example3_ContextSwitchOverhead {
    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        println("=== 3. 컨텍스트 스위칭 오버헤드 ===")
        val iterations = 10_000

        // Case A: 스위칭 없음 (단일 Dispatcher 유지 시뮬레이션)
        val timeNoSwitch = measureTimeMillis {
            withContext(Dispatchers.Default) {
                repeat(iterations) {
                    // 아주 작은 작업 시뮬레이션
                    val result = it * 2 
                }
            }
        }

        // Case B: 과도한 스위칭
        // 참고: 실제 로직이 복잡하면 오버헤드 비율은 줄어들겠지만, 
        // 촘촘한 루프(tight loops) 내에서는 누적되어 큰 성능 저하를 일으킵니다.
        val timeWithSwitch = measureTimeMillis {
            withContext(Dispatchers.Default) {
                repeat(iterations) {
                    withContext(Dispatchers.IO) {
                        // 아주 작은 작업을 위해 불필요하게 스위칭
                        val result = it * 2
                    }
                }
            }
        }

        println("스위칭 없을 때 (단일 Dispatcher): ${timeNoSwitch}ms")
        println("스위칭 과다 사용 시 (IO <-> Default): ${timeWithSwitch}ms")
        println("결론: withContext는 세밀한(fine-grained) 작업보다는 굵직한(coarse-grained) 작업 단위로 전환할 때 사용하세요.")
    }
}

/**
 * 예제 5: 메모리 누수 및 좀비 작업 (Memory Leak / Zombie Job)
 * 뷰(Screen)가 파괴되었지만, 취소되지 않는 영역(GlobalScope 등)에서
 * withContext로 긴 작업을 수행하면 메모리 누수가 발생하거나 좀비 작업이 남습니다.
 *
 */
object Example5_MemoryLeak {
    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        println("=== 5. 취소되지 않는 Blocking 작업 시뮬레이션 ===")
        println("상황: 사용자가 화면을 들어왔다가 0.5초 만에 나갑니다.")
        
        // 화면의 수명주기 스코프 (예: lifecycleScope)
        val screenScope = CoroutineScope(Dispatchers.Default + Job())

        screenScope.launch {
            println("[Working] 무거운 작업 시작 (withContext 사용)")

            // withContext는 자신의 작업이 끝날 때까지 스레드를 유지하지는 않지만,
            // 내부에서 '협조적이지 않은' Blocking 코드를 쓰면 취소가 안 됩니다.
            withContext(Dispatchers.IO) {
                println("[IO Thread] 데이터를 읽는 중... (Block 발생)")
                
                // 문제 상황: 취소 체크(isActive) 없이 무작정 Thread.sleep으로 멈춤
                // 이는 코루틴의 취소 신호를 무시하고 강제로 스레드를 잡고 있습니다.
                val startTime = System.currentTimeMillis()
                Thread.sleep(3000) 
                
                println("[IO Thread] 작업 완료! (하지만 이미 화면은 닫혔음 - 3초 소요) 경과: ${System.currentTimeMillis() - startTime}ms")
            }
        }
        
        delay(500)
        println("사용자가 화면을 닫음! (onDestroy -> cancel 호출)")
        screenScope.cancel() // 화면 스코프 취소 요청
        
        println("취소 요청 완료. 작업이 진짜 멈췄을까요?")
        delay(3000) // 좀비 작업이 끝까지 도는지 확인하기 위해 대기
        println("테스트 종료.")
    }
}

/**
 * 예제 6: Dispatcher 기아 상태 (Starvation)
 * withContext 사용 시 적절한 Dispatcher를 사용하지 않으면 문제가 생깁니다.
 * CPU 연산 위주의 Dispatchers.Default에서 Blocking I/O를 수행하면,
 * CPU 코어 수만큼 스레드가 꽉 차서 다른 정당한 CPU 작업들이 실행되지 못하고 "굶게(Starve)" 됩니다.
 */
object Example6_DispatcherStarvation {
    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        println("=== 6. 스레드 기아 상태 (Starvation) 시뮬레이션 ===")
        
        // Dispatchers.Default의 코어 수 제한 (보통 CPU 코어 수, 최소 2개)
        // 여기서는 강제로 제한된 환경이라 가정하고 병렬 작업을 여러 개 띄웁니다.
        
        val blockingJobs = List(10) { index ->
            launch(Dispatchers.Default) {
                // [문제 코드] Default 디스패처(CPU용)에서 I/O성 Blocking 작업을 수행
                // 이렇게 하면 스레드 풀이 모두 잠자느라(sleep) 바빠서, 실제 계산해야 할 작업이 밀립니다.
                println("Blocking 작업 $index 시작 (Thread: ${Thread.currentThread().name})")
                Thread.sleep(1000) // 1초간 점유
                println("Blocking 작업 $index 끝")
            }
        }
        
        // 정당한 CPU 작업
        val cpuJob = launch(Dispatchers.Default) {
             // 위 Blocking 작업들이 스레드를 다 먹어버리면 이 로그는 1초 뒤에 찍힙니다.
             // 만약 Dispatchers.IO를 썼다면(Blocking 작업들에), 이 로그는 즉시 찍혔을 것입니다.
             println(">>> [중요] CPU 연산 작업 수행 (언제 실행될까요?) <<<")
        }
        
        joinAll(*blockingJobs.toTypedArray(), cpuJob)
        println("테스트 종료: Blocking 작업을 Default에서 돌리면 CPU 작업이 늦게 실행됨을 확인.")
    }
}
