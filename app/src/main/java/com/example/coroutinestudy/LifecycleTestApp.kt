package com.example.coroutinestudy

import android.app.Application
import android.content.ComponentCallbacks2
import android.util.Log

/**
 * onTerminate() 검증용 Application.
 *
 * 실험 목적: "앱 설정 > 강제 중지(Force Stop) 를 누르면 onTerminate() 가 호출되는가?"
 *
 * 기대 결과: 호출 안 됨.
 *  - 강제 중지 = SIGKILL 로 프로세스를 즉시 종료 → 어떤 유저 코드도 실행되지 않음.
 *  - onTerminate() 는 에뮬레이트된 런타임 환경에서만 호출되며 실기기 프로덕션에서는 호출되지 않음.
 *
 * 확인 방법:
 *   adb logcat -s LifecycleTest
 *   앱 실행 후 시나리오별(뒤로가기 / 홈 / 최근앱 스와이프 / 강제 중지) 로그를 비교한다.
 *   강제 중지 자체는 앱이 죽어 로그를 못 남기므로, scripts/lifecycle_test.sh 가
 *   `adb shell log` 로 외부 마커를 주입해 타임라인에 표시한다.
 */
class LifecycleTestApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.e(TAG, "┌──────────────────────────────────────────")
        Log.e(TAG, "│ APP  onCreate()        프로세스 시작 (pid=${android.os.Process.myPid()})")
        Log.e(TAG, "└──────────────────────────────────────────")
    }

    override fun onTerminate() {
        super.onTerminate()
        // 실기기에서는 여기에 절대 도달하지 않는다 (도달하면 가설이 맞는 것).
        Log.e(TAG, "★ APP  onTerminate()    ← 호출됨!! (실기기에선 안 나와야 정상)")
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Log.e(TAG, "  APP  onLowMemory()     시스템 메모리 부족 신호")
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Log.e(TAG, "  APP  onTrimMemory()    level=$level (${trimLevelName(level)})")
    }

    private fun trimLevelName(level: Int): String = when (level) {
        ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> "COMPLETE(80) 곧 종료 후보"
        ComponentCallbacks2.TRIM_MEMORY_MODERATE -> "MODERATE(60)"
        ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> "BACKGROUND(40) 백그라운드 진입"
        ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> "UI_HIDDEN(20) UI 가려짐"
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> "RUNNING_CRITICAL(15)"
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> "RUNNING_LOW(10)"
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> "RUNNING_MODERATE(5)"
        else -> "UNKNOWN"
    }

    companion object {
        const val TAG = "LifecycleTest"
    }
}
