package com.patrickreynolds.reviewmarks

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

private const val FILES_PER_PAGE = 100
private const val MOST_FILE_PAGES = 30
private const val CONNECT_TIMEOUT_SECONDS = 15L
private const val REQUEST_TIMEOUT_SECONDS = 60L
private const val ERROR_PREVIEW_LENGTH = 200

enum class ReviewEvent {
    COMMENT,
    APPROVE,
    REQUEST_CHANGES,
}

data class PullRequestChanges(
    val changedLinesByPath: Map<String, List<LineRange>>,
    val commentableLines: List<CommentableLines>,
)

data class ReviewComment(val path: String, val firstLine: Int, val lastLine: Int, val body: String)

data class ReviewSubmission(
    val commitSha: String,
    val summary: String,
    val event: ReviewEvent,
    val comments: List<ReviewComment>,
)

data class PullRequestInfo(
    val number: Int,
    val title: String,
    val htmlUrl: String,
    val headSha: String,
)

sealed interface GitHubResult<out T> {
    data class Success<T>(val value: T) : GitHubResult<T>
    data class Failure(val statusCode: Int, val message: String) : GitHubResult<Nothing>
}

class GitHubApi(private val apiUrl: String, private val token: String) {

    private val httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS)).build()

    fun findOpenPullRequest(owner: String, repository: String, branch: String): GitHubResult<PullRequestInfo?> {
        val head = URLEncoder.encode("$owner:$branch", StandardCharsets.UTF_8)
        return when (val response = get("/repos/$owner/$repository/pulls?head=$head&state=open")) {
            is GitHubResult.Failure -> response
            is GitHubResult.Success -> GitHubResult.Success(response.value.asJsonArray.firstOrNull()?.let { pullRequestOf(it.asJsonObject) })
        }
    }

    fun getPullRequest(owner: String, repository: String, number: Int): GitHubResult<PullRequestInfo> =
        when (val response = get("/repos/$owner/$repository/pulls/$number")) {
            is GitHubResult.Failure -> response
            is GitHubResult.Success -> GitHubResult.Success(pullRequestOf(response.value.asJsonObject))
        }

    fun changedLines(owner: String, repository: String, number: Int): GitHubResult<PullRequestChanges> {
        val changedLinesByPath = mutableMapOf<String, List<LineRange>>()
        val commentableLines = mutableListOf<CommentableLines>()
        for (page in 1..MOST_FILE_PAGES) {
            val response = get("/repos/$owner/$repository/pulls/$number/files?per_page=$FILES_PER_PAGE&page=$page")
            val files = when (response) {
                is GitHubResult.Failure -> return response
                is GitHubResult.Success -> response.value.asJsonArray
            }
            files.forEach { file ->
                val fileObject = file.asJsonObject
                val patch = fileObject.get("patch")?.asString
                val path = fileObject.get("filename").asString
                if (patch != null) {
                    changedLinesByPath[path] = parseChangedLinesFromPatch(patch)
                    commentableLines += parseCommentableLinesFromPatch(path, patch)
                }
            }
            if (files.size() < FILES_PER_PAGE) break
        }
        return GitHubResult.Success(PullRequestChanges(changedLinesByPath, commentableLines))
    }

    fun postReview(owner: String, repository: String, number: Int, review: ReviewSubmission): GitHubResult<String> =
        when (val response = post("/repos/$owner/$repository/pulls/$number/reviews", reviewJson(review))) {
            is GitHubResult.Failure -> response
            is GitHubResult.Success -> GitHubResult.Success(response.value.asJsonObject.get("html_url")?.asString.orEmpty())
        }

    private fun reviewJson(review: ReviewSubmission): String {
        val comments = JsonArray()
        review.comments.forEach { comment ->
            val commentObject = JsonObject()
            commentObject.addProperty("path", comment.path)
            if (comment.firstLine < comment.lastLine) {
                commentObject.addProperty("start_line", comment.firstLine)
                commentObject.addProperty("start_side", "RIGHT")
            }
            commentObject.addProperty("line", comment.lastLine)
            commentObject.addProperty("side", "RIGHT")
            commentObject.addProperty("body", comment.body)
            comments.add(commentObject)
        }
        val reviewObject = JsonObject()
        reviewObject.addProperty("commit_id", review.commitSha)
        reviewObject.addProperty("body", review.summary)
        reviewObject.addProperty("event", review.event.name)
        reviewObject.add("comments", comments)
        return reviewObject.toString()
    }

    private fun pullRequestOf(pullRequest: JsonObject) = PullRequestInfo(
        number = pullRequest.get("number").asInt,
        title = pullRequest.get("title").asString,
        htmlUrl = pullRequest.get("html_url").asString,
        headSha = pullRequest.getAsJsonObject("head").get("sha").asString,
    )

    private fun get(path: String): GitHubResult<JsonElement> = send(requestBuilder(path).GET())

    private fun post(path: String, jsonBody: String): GitHubResult<JsonElement> =
        send(requestBuilder(path).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(jsonBody)))

    private fun requestBuilder(path: String): HttpRequest.Builder = HttpRequest.newBuilder(URI.create(apiUrl.trimEnd('/') + path))
        .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
        .header("Authorization", "Bearer $token")
        .header("Accept", "application/vnd.github+json")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .header("User-Agent", "jetbrains-review-marks-plugin")

    private fun send(request: HttpRequest.Builder): GitHubResult<JsonElement> {
        val response = try {
            httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString())
        } catch (failure: IOException) {
            return GitHubResult.Failure(0, failure.message ?: "Could not reach GitHub")
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            return GitHubResult.Failure(0, "The request was cancelled")
        }
        if (response.statusCode() !in 200..299) {
            return GitHubResult.Failure(response.statusCode(), errorMessageOf(response.body()))
        }
        return GitHubResult.Success(JsonParser.parseString(response.body()))
    }

    private fun errorMessageOf(responseBody: String): String {
        val messageFromJson = runCatching { JsonParser.parseString(responseBody).asJsonObject.get("message")?.asString }.getOrNull()
        return messageFromJson ?: responseBody.take(ERROR_PREVIEW_LENGTH)
    }
}
