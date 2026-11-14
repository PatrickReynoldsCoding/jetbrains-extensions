package com.patrickreynolds.reviewmarks

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class PomodoroStartup : ProjectActivity {
    override suspend fun execute(project: Project) {
        project.service<PomodoroTimer>().startChecking()
    }
}
