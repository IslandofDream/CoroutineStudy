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


// [공통 함수] CPU를 점유하는 무거운 작업을 시뮬레이션 (e.g. 암호화, 비트맵 인코딩, 복잡한 파싱)
// Thread.sleep()과 달리 실제 스레드(Core)를 사용합니다.
fun simulateHeavyWork(durationMs: Long) {
    val endTime = System.currentTimeMillis() + durationMs
    while (System.currentTimeMillis() < endTime) {
        // Busy Wait: CPU를 100% 사용하여 다른 작업이 비집고 들어오지 못하게 함
        Math.sin(Math.random()) 
    }
}

/**
 * 예제 4: 메모리 누수 및 좀비 작업 (Memory Leak / Zombie Job)
 *
 * [주의] 이 문제는 `withContext` 자체의 결함이 아닙니다!
 * 코루틴의 "협조적 취소(Cooperative Cancellation)" 규칙을 지키지 않아서 발생하는 문제입니다.
 *
 * 하지만 개발자들이 `withContext`로 무거운 작업(암호화, 파싱 등)을 백그라운드로 보낼 때
 * 가장 자주 저지르는 실수이기 때문에 여기서 다룹니다.
 *
 * 핵심:
 * 1. `withContext`를 썼다고 해서 내부의 무한 루프나 무거운 연산이 자동으로 취소되지는 않습니다.
 * 2. CPU를 계속 쓰는 작업 중에는 반드시 `isActive` 체크나 `yield()`를 넣어줘야 합니다.
 */
object Example4_ZombieJob_MemoryLeak {
    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        println("=== 4. 취소되지 않는 작업 시뮬레이션 ===")
        println("상황: 사용자가 화면 진입 후 바로 나감 (0.5초)")

        // 화면의 수명주기 스코프
        val screenScope = CoroutineScope(Dispatchers.Default + Job())

        screenScope.launch {
            println("[Working] 대용량 데이터 파싱 시작...")

            // withContext는 멈추고 기다리지만, 내부 로직이 '협조적'이어야 취소됩니다.
            withContext(Dispatchers.Default) {
                println("[Worker Thread] 암호화/파싱 수행 중 (CPU 점유)")

                // 복잡한 연산 라이브러리나 레거시 코드를 호출할 때,
                // 내부에서 3초 동안 CPU를 태우며 루프를 돕니다. 취소 체크가 없습니다.
                val startTime = System.currentTimeMillis()

                simulateHeavyWork(3000)

                println("[Worker Thread] 작업 완료! (화면 닫혔는데도 끝까지 돔) 경과: ${System.currentTimeMillis() - startTime}ms")
            }
        }

        delay(500)
        println("사용자가 화면을 닫음! (cancel 호출)")
        screenScope.cancel() 

        println("취소 요청 완료. 백그라운드 작업 확인 대기...")
        delay(3000) 
        println("테스트 종료.")
    }
}

/**
 * 예제 4-2 (해결편): 협조적인 취소 (Cooperative Cancellation)
 *
 * 위 문제를 해결하려면, CPU를 사용하는 긴 작업 중간중간에
 * "나 취소됐니?"라고 확인하는 코드를 넣어야 합니다.
 *
 * 방법:
 * 1. ensureActive() 호출 (가장 권장)
 * 2. isActive 프로퍼티 확인
 * 3. yield() 호출 (다른 코루틴에게 양보하면서 취소 체크도 함)
 */
object Example4_Fixed_CooperativeCancellation {
    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        println("=== 4-2. 협조적인 취소 (올바른 코드) ===")
        val screenScope = CoroutineScope(Dispatchers.Default + Job())

        screenScope.launch {
            println("[Working] 대용량 데이터 파싱 시작 (협조적)")

            withContext(Dispatchers.Default) {
                println("[Worker Thread] 암호화/파싱 수행 중...")
                val startTime = System.currentTimeMillis()

                // [해결책] ensureActive()를 루프 안에서 호출
                // simulateHeavyWorkWithCheck 함수 내부를 보세요.
                simulateHeavyWorkWithCheck(this, 3000)

                println("[Worker Thread] 작업 완료! (이 로그는 찍히면 안 됨)")
            }
        }
        
        delay(500)
        println("사용자가 화면을 닫음! (cancel 호출)")
        screenScope.cancel()
        
        delay(1000)
        println("테스트 종료: 취소가 즉시 되어서 '작업 완료' 로그가 안 떠야 함.")
    }

    // 협조적인 무거운 작업 시뮬레이션
    suspend fun simulateHeavyWorkWithCheck(scope: CoroutineScope, durationMs: Long) {
        val endTime = System.currentTimeMillis() + durationMs
        while (System.currentTimeMillis() < endTime) {
            // [핵심] 주기적으로 취소 여부를 체크!
            // 만약 scope가 cancel 상태라면 여기서 CancellationException이 발생하고 멈춤.
            scope.ensureActive() 
            
            // 실제 작업
            Math.sin(Math.random())
        }
    }
}

/**
 * 예제 5: withContext 오용으로 인한 기아 상태 (Dispatcher Starvation)
 *
 * `withContext`는 실행 컨텍스트를 변경하지만, 만약 [잘못된 Dispatcher]로 변경하면
 * 해당 Dispatcher의 스레드 풀을 고갈시켜 다른 작업을 방해합니다.
 *
 * 시나리오:
 * 1. UI에서 어떤 작업을 요청했는데, 내부에서 `withContext(Dispatchers.Default)`를 호출.
 * 2. 근데 그 안에서 무거운 Blocking 작업(DB/네트워크 등)을 수행.
 * 3. `Dispatchers.Default`의 스레드가 다 잠겨버려서, 다른 정당한 CPU 작업(애니메이션 계산 등)이 멈춤.
 */
object Example5_DispatcherStarvation {
    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        println("=== 5. withContext 오용 시 기아 상태 (Starvation) ===")
        val startTime = System.currentTimeMillis()
        fun log(msg: String) = println("[${System.currentTimeMillis() - startTime}ms] [${Thread.currentThread().name}] $msg")

        // 1. Dispatchers.Default(CPU용, 스레드 수 적음)를 사용하는 스코프 생성
        val workerScope = CoroutineScope(Dispatchers.Default)

        log("작업 시작: 스레드 풀(Default)을 고갈시킬 준비")

        // 확실한 기아 상태를 위해 100개를 실행합니다. (Default 풀 사이즈는 보통 코어 수로 제한됨)
        val heavyJobs = List(100) { index ->
            workerScope.launch {
                withContext(Dispatchers.Default) {
                    // log("방해꾼 $index 진입") // 너무 시끄러우면 주석 자리
                    simulateHeavyWork(1000) 
                }
            }
        }

        delay(10)
        log(">>> [요청] 긴급 CPU 작업 요청! 바로 실행되어야 함!")

        val urgentJob = workerScope.launch {
            withContext(Dispatchers.Default) {
                log(">>> [실행!] 드디어 실행됨!")
            }
        }

        joinAll(*heavyJobs.toTypedArray(), urgentJob)
        log("태스트 종료")
    }
}
