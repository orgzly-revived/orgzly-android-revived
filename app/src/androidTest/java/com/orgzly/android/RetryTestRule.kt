package com.orgzly.android

import android.util.Log
import org.junit.AssumptionViolatedException
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Retry test rule used to retry test that failed. Retry failed test 3 times
 */
class RetryTestRule(val retryCount: Int = 3) : TestRule {

    private val TAG = RetryTestRule::class.java.simpleName

    override fun apply(base: Statement, description: Description): Statement {
        return statement(base, description)
    }

    private fun statement(base: Statement, description: Description): Statement {
        return object : Statement() {
            @Throws(Throwable::class)
            override fun evaluate() {
                var caughtThrowable: Throwable? = null

                // implement retry logic here
                for (i in 0 until retryCount) {
                    try {
                        base.evaluate()
                        return
                    } catch (t: Throwable) {
                        caughtThrowable = t
                        if (caughtThrowable is AssumptionViolatedException) {
                            throw caughtThrowable
                        }
                        Log.e(TAG, description.displayName + ": run " + (i + 1) + " failed")
                    }
                }

                Log.e(TAG, description.displayName + ": giving up after " + retryCount + " failures")
                throw caughtThrowable!!
            }
        }
    }
}