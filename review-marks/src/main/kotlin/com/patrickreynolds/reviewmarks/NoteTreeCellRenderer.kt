package com.patrickreynolds.reviewmarks

import com.intellij.icons.AllIcons
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode

private const val ANCHOR_PREVIEW_LENGTH = 40

data class NoteKindHeader(val kind: NoteKind, val noteCount: Int)

data class NoteRow(val note: StoredNote, val linesText: String?, val agentReply: AgentReply?)

data class AgentReplyRow(val agentReply: AgentReply)

class NoteTreeCellRenderer : ColoredTreeCellRenderer() {

    override fun customizeCellRenderer(
        tree: JTree,
        value: Any?,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean,
    ) {
        when (val shownItem = (value as? DefaultMutableTreeNode)?.userObject) {
            is NoteKindHeader -> {
                icon = ICON_BY_NOTE_KIND.getValue(shownItem.kind)
                val kindColor = NOTE_ACCENT_BY_KIND.getValue(shownItem.kind)
                append(shownItem.kind.pluralLabel, SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, kindColor))
                append("   ${shownItem.noteCount}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
            }
            is NoteRow -> {
                val note = shownItem.note
                icon = DotIcon(NOTE_ACCENT_BY_KIND.getValue(noteKindNamed(note.kind)))
                val fileName = note.fileUrl.substringAfterLast('/')
                val placeText = shownItem.linesText?.let { "$fileName:$it" } ?: "$fileName (code not found)"
                append("$placeText  ", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                if (note.floating) append("▲ ", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                append(note.text.lineSequence().first())
                append("   ${note.anchorText.lineSequence().first().take(ANCHOR_PREVIEW_LENGTH)}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
            }
            is AgentReplyRow -> {
                val agentReply = shownItem.agentReply
                icon = if (agentReply.resolved) AllIcons.Actions.Checked else AllIcons.General.Balloon
                val status = if (agentReply.resolved) "Claude (done): " else "Claude: "
                append(status, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                append(agentReply.reply.lineSequence().firstOrNull().orEmpty())
            }
        }
    }
}
