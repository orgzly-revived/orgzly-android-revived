package com.orgzly.android

import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import com.orgzly.android.data.DataRepository
import com.orgzly.android.db.NotesClipboard
import com.orgzly.android.usecase.UseCaseRunner
import org.junit.rules.ExternalResource
import java.util.concurrent.Executor

/**
 * Empty database of the app, which use cases run against.
 * Executors run on the test thread, so use cases are done when they return.
 */
class AppDataRule : ExternalResource() {
    lateinit var dataRepository: DataRepository
        private set

    private lateinit var executors: AppExecutors

    override fun before() {
        executors = App.EXECUTORS
        val direct = Executor { it.run() }
        App.EXECUTORS = AppExecutors(direct, direct, direct)

        // The app disables WorkManager's automatic initialization
        if (!WorkManager.isInitialized()) {
            WorkManager.initialize(
                ApplicationProvider.getApplicationContext(),
                Configuration.Builder().setExecutor { it.run() }.build())
        }

        dataRepository = UseCaseRunner.Factory().dataRepository
        dataRepository.clearDatabase()
        NotesClipboard.clear()
    }

    override fun after() {
        App.EXECUTORS = executors
    }

    /** Returns the ID of the loaded notebook. */
    fun loadBook(name: String, content: String): Long {
        val file = dataRepository.getTempBookFile()
        try {
            file.writeText(content)
            return dataRepository.loadBookFromFile(name, BookFormat.ORG, file)!!.book.id
        } finally {
            file.delete()
        }
    }

    fun noteId(title: String) = dataRepository.getLastNote(title)!!.id
}
