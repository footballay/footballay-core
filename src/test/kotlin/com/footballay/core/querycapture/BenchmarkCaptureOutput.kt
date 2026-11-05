package com.footballay.core.querycapture

import java.nio.file.Files
import java.nio.file.Path

/** 수동 벤치마크 캡처의 실행 단계별 출력 위치를 관리합니다. */
object BenchmarkCaptureOutput {
    fun phase(): String {
        val phase = System.getenv("BENCHMARK_CAPTURE_PHASE") ?: error("Specify BENCHMARK_CAPTURE_PHASE")
        require(phase.matches(Regex("[a-z0-9-]+")))
        return phase
    }

    fun directory(kind: String): Path {
        val output = Path.of("plan", "query-optimization", "work", "capture-${phase()}", kind)
        check(!Files.exists(output)) { "Preserve previous measurements: $output already exists" }
        Files.createDirectories(output)
        return output
    }
}
