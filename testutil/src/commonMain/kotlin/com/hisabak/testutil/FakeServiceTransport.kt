package com.hisabak.testutil

import com.hisabak.core.domain.remote.ServiceTransport

/** Records every POST and answers from [responses] by path; an unlisted path is a failure (`null`). */
class FakeServiceTransport(
    override var isConfigured: Boolean = true,
) : ServiceTransport {
    val responses = mutableMapOf<String, String?>()
    val posts = mutableListOf<Post>()

    data class Post(val path: String, val body: String, val timeoutMs: Int)

    override suspend fun postJson(path: String, body: String, timeoutMs: Int): String? {
        posts += Post(path, body, timeoutMs)
        return responses[path]
    }
}
