package com.patrickreynolds.reviewmarks

import com.google.gson.JsonParser

const val AGENT_REPLIES_FILE_PATH = ".idea/review-marks-agent-replies.json"

data class AgentReply(val noteId: String, val reply: String, val resolved: Boolean)

fun parseAgentReplies(repliesJson: String): Map<String, AgentReply> {
    val replyArray = runCatching { JsonParser.parseString(repliesJson).asJsonArray }.getOrNull() ?: return emptyMap()
    return replyArray.mapNotNull { element ->
        val replyObject = runCatching { element.asJsonObject }.getOrNull() ?: return@mapNotNull null
        val noteId = replyObject.get("noteId")?.takeIf { it.isJsonPrimitive }?.asString ?: return@mapNotNull null
        AgentReply(
            noteId = noteId,
            reply = replyObject.get("reply")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
            resolved = replyObject.get("resolved")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false,
        )
    }.associateBy { it.noteId }
}
