package com.bka.ssi.generator.application.testrunners

import com.bka.ssi.generator.application.testflows.fullflow.TestFlow
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit


@Service
@ConditionalOnProperty(
    name = ["test-runners.constant-load-runner.active"],
    matchIfMissing = false
)
class ConstantLoadTestRunner(
    private val testFlow: TestFlow,
    @Value("\${test-runners.constant-load-runner.number-of-total-iterations}") val numberOfTotalIterations: Int,
    @Value("\${test-runners.constant-load-runner.number-of-iterations-per-minute}") val numberOfIterationsPerMinute: Int,
    @Value("\${test-runners.constant-load-runner.core-thread-pool-size}") val coreThreadPoolSize: Int
) : TestRunner(
) {

    lateinit var scheduledFuture: ScheduledFuture<*>

    protected companion object {
        var numberOfIterationsFinished = 0
    }

    override fun run() {
        logger.info("Starting ConstantLoadTestRunner...")
        logger.info("Number of Iterations: $numberOfTotalIterations")
        logger.info("Number of Iterations per Minute: $numberOfIterationsPerMinute")

        testFlow.initialize(this)

        val executor = Executors.newScheduledThreadPool(coreThreadPoolSize)
        scheduledFuture = executor.scheduleAtFixedRate(
            Runnable { testFlow.startIteration() },
            0,
            60000L / numberOfIterationsPerMinute,
            TimeUnit.MILLISECONDS
        )
    }

    override fun finishedIteration() {
        numberOfIterationsFinished++
        logger.info("Finished ${numberOfIterationsFinished} of $numberOfTotalIterations iterations")

        if (numberOfIterationsFinished >= numberOfTotalIterations) {
            scheduledFuture.cancel(false)
        }
    }

}
