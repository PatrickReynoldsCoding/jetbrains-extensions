package com.patrickreynolds.reviewmarks

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import git4idea.repo.GitRepositoryManager

fun repositoryOfProject(project: Project): GitRepository? {
    val repositories = GitRepositoryManager.getInstance(project).repositories
    return repositories.firstOrNull { it.root.path == project.basePath } ?: repositories.singleOrNull()
}

fun followCurrentBranch(project: Project) {
    val branchName = repositoryOfProject(project)?.currentBranchName ?: return
    ApplicationManager.getApplication().invokeLater({ project.service<ReviewMarksService>().switchToBranch(branchName) }, project.disposed)
}

class FollowGitBranchListener(private val project: Project) : GitRepositoryChangeListener {
    override fun repositoryChanged(repository: GitRepository) {
        if (repository == repositoryOfProject(project)) followCurrentBranch(project)
    }
}

class FollowGitBranchOnStartup : ProjectActivity {
    override suspend fun execute(project: Project) = followCurrentBranch(project)
}
