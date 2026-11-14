package com.patrickreynolds.reviewmarks

import org.junit.Assert.assertEquals
import org.junit.Test

class FileTreeTest {

    private fun file(name: String) = FileTreeEntry.File(name, name)

    @Test
    fun `puts files in their folders, folders first and each sorted by name`() {
        val tree = fileTreeOf(
            listOf(
                "README.md" to "README.md",
                "src/Client.kt" to "Client.kt",
                "build.gradle.kts" to "build.gradle.kts",
                "src/Config.kt" to "Config.kt",
                "docs/guide.md" to "guide.md",
            ),
        )

        assertEquals(
            listOf(
                FileTreeEntry.Folder("docs", listOf(file("guide.md"))),
                FileTreeEntry.Folder("src", listOf(file("Client.kt"), file("Config.kt"))),
                file("README.md"),
                file("build.gradle.kts"),
            ),
            tree,
        )
    }

    @Test
    fun `joins a chain of folders that each hold only one folder into one row`() {
        val tree = fileTreeOf(
            listOf(
                "src/main/kotlin/com/acme/Client.kt" to "Client.kt",
                "src/main/kotlin/com/acme/http/Retry.kt" to "Retry.kt",
            ),
        )

        assertEquals(
            listOf(
                FileTreeEntry.Folder(
                    "src/main/kotlin/com/acme",
                    listOf(FileTreeEntry.Folder("http", listOf(file("Retry.kt"))), file("Client.kt")),
                ),
            ),
            tree,
        )
    }

    @Test
    fun `nests deeper folders one level further in`() {
        val tree = fileTreeOf(
            listOf(
                "app/Main.kt" to "Main.kt",
                "app/ui/Panel.kt" to "Panel.kt",
                "app/ui/widgets/Button.kt" to "Button.kt",
            ),
        )

        assertEquals(
            listOf(
                FileTreeEntry.Folder(
                    "app",
                    listOf(
                        FileTreeEntry.Folder(
                            "ui",
                            listOf(FileTreeEntry.Folder("widgets", listOf(file("Button.kt"))), file("Panel.kt")),
                        ),
                        file("Main.kt"),
                    ),
                ),
            ),
            tree,
        )
    }
}
