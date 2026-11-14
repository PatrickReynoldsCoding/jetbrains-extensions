package com.patrickreynolds.reviewmarks

import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress

class GitHubApiTest {

    private data class RecordedRequest(val method: String, val pathAndQuery: String, val authorization: String?, val body: String)

    private class FakeResponse(val statusCode: Int, val body: String)

    private lateinit var server: HttpServer
    private val recordedRequests = mutableListOf<RecordedRequest>()
    private val responsesByPathAndQuery = mutableMapOf<String, FakeResponse>()

    private val pullRequestJson = """{"number": 7, "title": "Fix retries", "html_url": "https://github.com/acme/widgets/pull/7", "head": {"sha": "abc123"}}"""

    @Before
    fun startFakeGitHub() {
        server = HttpServer.create(InetSocketAddress("localhost", 0), 0)
        server.createContext("/") { exchange -> answer(exchange) }
        server.start()
    }

    @After
    fun stopFakeGitHub() {
        server.stop(0)
    }

    private fun answer(exchange: HttpExchange) {
        val pathAndQuery = exchange.requestURI.rawPath + (exchange.requestURI.rawQuery?.let { "?$it" } ?: "")
        recordedRequests += RecordedRequest(
            method = exchange.requestMethod,
            pathAndQuery = pathAndQuery,
            authorization = exchange.requestHeaders.getFirst("Authorization"),
            body = exchange.requestBody.readBytes().decodeToString(),
        )
        val response = responsesByPathAndQuery[pathAndQuery] ?: FakeResponse(404, """{"message": "Not Found"}""")
        val bytes = response.body.toByteArray()
        exchange.sendResponseHeaders(response.statusCode, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun api() = GitHubApi("http://localhost:${server.address.port}", "token-123")

    @Test
    fun `finds the open pull request of a branch and sends the token`() {
        responsesByPathAndQuery["/repos/acme/widgets/pulls?head=acme%3Afeature%2Fretries&state=open"] = FakeResponse(200, "[$pullRequestJson]")

        val result = api().findOpenPullRequest("acme", "widgets", "feature/retries")

        assertEquals(
            GitHubResult.Success(PullRequestInfo(7, "Fix retries", "https://github.com/acme/widgets/pull/7", "abc123")),
            result,
        )
        assertEquals("Bearer token-123", recordedRequests.single().authorization)
    }

    @Test
    fun `finds no pull request when the branch has none`() {
        responsesByPathAndQuery["/repos/acme/widgets/pulls?head=acme%3Alonely&state=open"] = FakeResponse(200, "[]")

        assertEquals(GitHubResult.Success(null), api().findOpenPullRequest("acme", "widgets", "lonely"))
    }

    @Test
    fun `reports the message and status when GitHub refuses`() {
        val result = api().getPullRequest("acme", "widgets", 99)
        assertEquals(GitHubResult.Failure(404, "Not Found"), result)
    }

    @Test
    fun `loads the changed lines of every page of files and skips files without a patch`() {
        val firstPage = (1..100).joinToString(",", "[", "]") { """{"filename": "f$it.kt", "patch": "@@ -0,0 +1 @@\n+line"}""" }
        val secondPage = """[{"filename": "last.kt", "patch": "@@ -1,2 +1,3 @@\n a\n+b\n c"}, {"filename": "image.png"}]"""
        responsesByPathAndQuery["/repos/acme/widgets/pulls/7/files?per_page=100&page=1"] = FakeResponse(200, firstPage)
        responsesByPathAndQuery["/repos/acme/widgets/pulls/7/files?per_page=100&page=2"] = FakeResponse(200, secondPage)

        val result = api().changedLines("acme", "widgets", 7) as GitHubResult.Success

        assertEquals(101, result.value.size)
        assertEquals(listOf(LineRange(0, 0)), result.value.getValue("f1.kt"))
        assertEquals(listOf(LineRange(1, 1)), result.value.getValue("last.kt"))
        assertTrue(!result.value.containsKey("image.png"))
    }

    @Test
    fun `posts the review with its comments on the right side of the diff and gives back its address`() {
        responsesByPathAndQuery["/repos/acme/widgets/pulls/7/reviews"] =
            FakeResponse(200, """{"id": 1, "html_url": "https://github.com/acme/widgets/pull/7#pullrequestreview-1"}""")
        val review = ReviewSubmission(
            commitSha = "abc123",
            summary = "Looks good overall",
            event = ReviewEvent.REQUEST_CHANGES,
            comments = listOf(ReviewComment("src/A.kt", 12, "Why retry here?")),
        )

        val result = api().postReview("acme", "widgets", 7, review)

        assertEquals(GitHubResult.Success("https://github.com/acme/widgets/pull/7#pullrequestreview-1"), result)
        val request = recordedRequests.single()
        assertEquals("POST", request.method)
        val sentBody = JsonParser.parseString(request.body).asJsonObject
        assertEquals("abc123", sentBody.get("commit_id").asString)
        assertEquals("Looks good overall", sentBody.get("body").asString)
        assertEquals("REQUEST_CHANGES", sentBody.get("event").asString)
        val sentComment = sentBody.getAsJsonArray("comments").single().asJsonObject
        assertEquals("src/A.kt", sentComment.get("path").asString)
        assertEquals(12, sentComment.get("line").asInt)
        assertEquals("RIGHT", sentComment.get("side").asString)
        assertEquals("Why retry here?", sentComment.get("body").asString)
    }

    @Test
    fun `reports the reason when GitHub refuses the review`() {
        responsesByPathAndQuery["/repos/acme/widgets/pulls/7/reviews"] =
            FakeResponse(422, """{"message": "Unprocessable Entity"}""")

        val result = api().postReview("acme", "widgets", 7, ReviewSubmission("abc123", "", ReviewEvent.COMMENT, emptyList()))

        assertEquals(GitHubResult.Failure(422, "Unprocessable Entity"), result)
    }

    @Test
    fun `reports a failure when GitHub cannot be reached`() {
        server.stop(0)

        val result = api().getPullRequest("acme", "widgets", 7)

        assertTrue(result is GitHubResult.Failure)
        assertNull(recordedRequests.firstOrNull())
    }
}
