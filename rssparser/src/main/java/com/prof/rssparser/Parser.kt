/*
*   Copyright 2016 Marco Gomiero
*
*   Licensed under the Apache License, Version 2.0 (the "License");
*   you may not use this file except in compliance with the License.
*   You may obtain a copy of the License at
*
*       http://www.apache.org/licenses/LICENSE-2.0
*
*   Unless required by applicable law or agreed to in writing, software
*   distributed under the License is distributed on an "AS IS" BASIS,
*   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
*   See the License for the specific language governing permissions and
*   limitations under the License.
*
*/

package com.prof.rssparser

import com.prof.rssparser.engine.XMLFetcher
import com.prof.rssparser.engine.XMLParser
import com.prof.rssparser.enginecoroutine.CoroutineEngine
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext

class Parser(private val okHttpClient: OkHttpClient? = null) {

    private lateinit var onComplete: OnTaskCompleted
    private lateinit var service: ExecutorService

    private val parserJob = Job()
    private val coroutineContext: CoroutineContext
        get() = parserJob + Dispatchers.Default

    fun onFinish(onComplete: OnTaskCompleted) {
        this.onComplete = onComplete
    }

    fun execute(url: String) {
        Executors.newSingleThreadExecutor().submit {
            service = Executors.newFixedThreadPool(2)
            val f1 = service.submit<String>(XMLFetcher(url, okHttpClient))
            try {
                val rssFeed = f1.get()
                val f2 = service.submit(XMLParser(rssFeed))
                onComplete.onTaskCompleted(f2.get())
            } catch (e: Exception) {
                onComplete.onError(e)
            } finally {
                service.shutdown()
            }
        }
    }

    /**
     *  Cancel the execution of the fetching and the parsing.
     */
    fun cancel() {
        if (::service.isInitialized) {
            // The client is using Java!
            try {
                if (!service.isShutdown) {
                    service.shutdownNow()
                }
            } catch (e: Exception) {
                onComplete.onError(e)
            }
        } else {
            // The client is using Kotlin and coroutines
            if (coroutineContext.isActive) {
                coroutineContext.cancel()
            }
        }
    }

    @Throws(Exception::class)
    suspend fun getChannel(url: String) =
            withContext(coroutineContext) {
                val xml = CoroutineEngine.fetchXML(url, okHttpClient)
                return@withContext CoroutineEngine.parseXML(xml)
            }
}

