package com.bka.ssi.generator.application.testrunners

import com.bka.ssi.generator.application.testflows.TestFlow
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit


@Service
@ConditionalOnProperty(
    name = ["test-runners.increasing-load-runner.active"],
    matchIfMissing = false
)
class IncreasingLoadTestRunner(
    private val testFlow: TestFlow,
    @Value("\${test-runners.increasing-load-runner.peak-duration-in-minutes}") val peakDurationInMinutes: Long,
    @Value("\${test-runners.increasing-load-runner.sleep-between-peaks-in-minutes}") val sleepBetweenPeaksInMinutes: Long,
    @Value("\${test-runners.increasing-load-runner.initial-number-of-iterations-per-minute}") val initialNumberOfIterationsPerMinute: Int,
    @Value("\${test-runners.increasing-load-runner.final-number-of-iterations-per-minute}") val finalNumberOfIterationsPerMinute: Int,
    @Value("\${test-runners.increasing-load-runner.step-size-of-iterations-per-minute}") val stepSizeOfIterationsPerMinute: Int,
    @Value("\${test-runners.increasing-load-runner.core-thread-pool-size}") val coreThreadPoolSize: Int
) : TestRunner(
) {


    protected companion object {
        var numberOfIterationsFinishedInCurrentPeak = 0L
        var expectedNumberOfIterationsInCurrentPeak = 0L
        var totalNumberOfPeaks = 0
        var totalNumberOfPeaksStarted = 0
        var startScheduler: ScheduledFuture<*>? = null
        var killScheduler: ScheduledFuture<*>? = null
        var loadScheduler: ScheduledFuture<*>? = null
    }

    override fun run() {
        testFlow.initialize(this)
    }

    override fun finishedInitialization() {
        logger.info("Starting IncreasingLoadTestRunner...")
        logger.info("Peak duration in minutes: $peakDurationInMinutes")
        logger.info("Sleep between peaks in minutes: $sleepBetweenPeaksInMinutes")
        logger.info("Initial number of iterations per minute: $initialNumberOfIterationsPerMinute")
        logger.info("Final number of iterations per minute: $finalNumberOfIterationsPerMinute")
        logger.info("Step size of iterations per minute: $stepSizeOfIterationsPerMinute")
        logger.info("Core thread pool size: $coreThreadPoolSize")

        totalNumberOfPeaks =
            ((finalNumberOfIterationsPerMinute - initialNumberOfIterationsPerMinute) / stepSizeOfIterationsPerMinute) + 1


        val numberOfMinutesSleepingBetweenPeaks = totalNumberOfPeaks * sleepBetweenPeaksInMinutes
        val numberOfMinutesExecutingLoad = totalNumberOfPeaks * peakDurationInMinutes
        logger.info("Test will finish in ${numberOfMinutesSleepingBetweenPeaks + numberOfMinutesExecutingLoad} minutes")

        val startExecutor = Executors.newScheduledThreadPool(coreThreadPoolSize)
        startScheduler = startExecutor.scheduleWithFixedDelay(
            Runnable {
                startNewPeakLoad()
            },
            0,
            peakDurationInMinutes + sleepBetweenPeaksInMinutes,
            TimeUnit.MINUTES
        )

        val killExecutor = Executors.newScheduledThreadPool(coreThreadPoolSize)
        killScheduler = killExecutor.scheduleWithFixedDelay(
            Runnable {
                killCurrentPeakLoad()
            },
            peakDurationInMinutes,
            peakDurationInMinutes + sleepBetweenPeaksInMinutes,
            TimeUnit.MINUTES
        )
    }

    private fun startNewPeakLoad() {
        val currentNumberOfIterationsPerMinute =
            initialNumberOfIterationsPerMinute + stepSizeOfIterationsPerMinute * totalNumberOfPeaksStarted

        numberOfIterationsFinishedInCurrentPeak = 0L
        expectedNumberOfIterationsInCurrentPeak = currentNumberOfIterationsPerMinute * peakDurationInMinutes

        val loadExecutor = Executors.newScheduledThreadPool(coreThreadPoolSize)
        loadScheduler = loadExecutor.scheduleAtFixedRate(
            Runnable { testFlow.startIteration() },
            0,
            60000L / currentNumberOfIterationsPerMinute,
            TimeUnit.MILLISECONDS
        )

        totalNumberOfPeaksStarted++

        if (totalNumberOfPeaksStarted >= totalNumberOfPeaks) {
            startScheduler?.cancel(false)
        }
    }

    private fun killCurrentPeakLoad() {
        loadScheduler?.cancel(false)

        if (totalNumberOfPeaksStarted >= totalNumberOfPeaks) {
            killScheduler?.cancel(false)
        }
    }

    override fun finishedIteration() {
        numberOfIterationsFinishedInCurrentPeak++
        logger.info("Finished $numberOfIterationsFinishedInCurrentPeak of $expectedNumberOfIterationsInCurrentPeak iterations")
    }

}
