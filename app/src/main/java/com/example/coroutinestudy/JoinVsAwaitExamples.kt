package com.example.coroutinestudy

import kotlinx.coroutines.*
import java.io.IOException

/**
 * 1. 예외 전파 (Exception Propagation) - 가장 중요한 차이
 *
 * join() (침묵): 대상 Job이 실패해서 예외가 발생했더라도, join()을 호출한 곳에서는 예외를 다시 던지지 않습니다.
 * await() (확성기): 대상 작업이 실패했다면, await()를 호출하는 순간 그 예외를 다시 던집니다(Re-throw).
 */
object Example1_ExceptionPropagation {
    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        println("=== 1. 예외 전파 차이 (Join vs Await) ===")

        // SupervisorJob을 쓰는 이유:
        // 일반 Job은 자식이 터지면 부모도 같이 터져서 예외 처리를 테스트하기 어렵습니다.
        supervisorScope {
            
            println("--- 테스트 준비: 에러가 발생하는 Async 작업 생성 ---")
            // 작업 실패 시뮬레이션
            val deferred = async {
                println("   (Async) 작업 시작... 으악 실패! 💣")
                throw RuntimeException("Error! Something went wrong.")
            }

            // 잠시 대기하여 Async 내부가 먼저 터지도록 유도
            delay(100)

            println("\n[1. join() 사용 시]")
            try {
                // 1. join 사용 시
                deferred.join() 
                println("   -> [통과] 여기서 멈추지 않고 그냥 통과합니다. (예외를 뱉지 않음)")
                println("   -> 단, 부모가 SupervisorJob이라서 살아남은 것입니다.")
                println("   -> Deferred 상태: isCancelled=${deferred.isCancelled}")
            } catch (e: Exception) {
                println("   [Join] 예외 발생 (이 로그는 찍히지 않아야 함): $e")
            }

            println("\n[2. await() 사용 시]")
            try {
                // 2. await 사용 시
                deferred.await()
                println("   -> [실패] 이 로그는 찍히지 않습니다.")
            } catch (e: Exception) {
                println("   -> [CRASH!] await() 호출 시점에서 예외가 다시 던져짐(Re-throw)!")
                println("   -> Catch된 예외: ${e.message}")
            }
        }
        println("\n=== 테스트 1 종료 ===")
    }
}

/**
 * 2. 포함 관계 (Hierarchy)
 *
 * Deferred는 Job이다: Deferred는 Job을 상속받습니다. 따라서 Deferred 객체에 대해 .join()을 호출할 수도 있습니다.
 * Job은 Deferred가 아니다: 일반 Job에는 .await()가 존재하지 않습니다.
 */
object Example2_Hierarchy {
    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        println("=== 2. 계층 관계 확인 (Deferred is a Job) ===")

        val deferred: Deferred<String> = async {
            delay(500)
            "Result Data"
        }

        // 1. Deferred를 Job처럼 다루기 (Upcasting)
        val job: Job = deferred // 가능! Deferred는 Job의 자식 인터페이스
        
        println("1. Deferred에 join() 호출 시도...")
        job.join() // Job 인터페이스의 기능
        println("   -> join() 완료! (결과값은 받지 못하고 끝남)")

        // 2. 이미 완료된 상태에서 결과 꺼내기
        // (주의: 이미 join으로 기다렸으므로 즉시 리턴됨)
        val result = deferred.await() 
        println("   -> 뒤늦게 await()로 꺼낸 결과: $result")

        // 3. 반대는 불가능
        val normalJob = launch { delay(100) }
        // val impossible = normalJob.await() // 컴파일 에러! Job에는 await가 없습니다.
        
        println("\n=== 테스트 2 종료 ===")
    }
}

/**
 * 3. 목적 (Semantic)
 *
 * join(): 동기화(Synchronization)가 목적. "끝나는 타이밍을 맞추겠다"
 * await(): 데이터 접근(Data Access)이 목적. "결과값을 가져오겠다"
 */
object Example3_Semantic {
    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        println("=== 3. 사용 목적(Semantic) 차이 ===")

        // 상황 1: 데이터가 필요 없는 '반영' 작업 (예: 서버에 로그 보내기, DB 업데이트)
        // 결과값이 Unit(없음)이므로 굳이 배달부를 기다릴 필요 없이, "끝났는지"만 알면 됨.
        val saveJob = launch {
            delay(500)
            println("   (DB) 데이터 저장 완료")
        }

        println("메인: 저장 작업이 끝날 때까지 대기(Sync)...")
        saveJob.join() // "타이밍 동기화"
        println("메인: 저장 끝났으니 다음 단계 진행!")

        println("--------------------------------")

        // 상황 2: 데이터가 필요한 '조회' 작업 (예: 서버에서 유저 정보 가져오기)
        // 결과가 없으면 다음 로직 수행 불가.
        val fetchJob = async {
            delay(500)
            "User(name=Kim)" // 결과값
        }

        println("메인: 유저 정보 가져오는 중...")
        // val result = fetchJob.join() // 에러! join은 결과를 리턴하지 않음 Unit 반환.
        
        val user = fetchJob.await() // "데이터 접근"
        println("메인: 가져온 유저 정보로 UI 갱신 -> $user")
        
        println("\n=== 테스트 3 종료 ===")
    }
}
