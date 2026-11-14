# Review Marks

An IntelliJ plugin to help you read code in small pieces and keep track of what you have read. It is made to keep what you must hold in your head small.

## How it works

You choose what to read. Select code and mark it:

- **To review** (blue). Code you plan to read. It is listed in the *To review* tab.
- **Reviewed** (green). Code you have read.
- **Changed** (amber). Reviewed code that you edited. Only the edited line turns amber, and it stays amber until you mark it as reviewed again.

The progress text, for example `Reviewed 40% (120/300 lines) · 2 changed`, counts reviewed lines out of all the lines you marked (to review, reviewed or changed). Code you did not mark is not counted. Empty lines are not counted.

Marks and comments are saved per project in `.idea/workspace.xml`. When a file is opened again, each mark and comment looks for its code: first at the same place, then at the copy of that code nearest to where it was. If the code changed while the file was closed:

- a mark is removed, because that code needs reading again;
- a comment is put on the nearest line of code, so your words are never lost.

A message in the corner says how many marks were removed and how many comments were moved.

**Undo.** Clearing marks or deleting comments can be undone, up to 20 steps. The *Undo* button shows in the sidebar after you clear or delete something, and says what it will bring back.

## The Review Marks sidebar

Open the **Review Marks** tool window on the right. From top to bottom:

- **Mark.** *To review* (blue), *Reviewed* (green), *Reviewed & next* (marks, then jumps to the next code to review) and *Clear mark*.
- **Go to.** *Next to review* and *Where I stopped*.
- **Comment on selection.** *Note*, *PR comment* and *Agent note*. Each adds a new comment of that kind on the selected lines.
- **Review from git.** A menu with *Mark All Branch Changes to Review*, *Mark Uncommitted Changes to Review*, *Review Branch Changes…* (pick any branch) and *PR review…*. While you review a PR, *Post review…* and *End PR* also show.
- **Fold reviewed.** Folds reviewed blocks into one line, such as `✓ 12 reviewed lines`. Expand a fold to peek at it.
- **Progress** for the open file, and below it the progress of all marked code in the project (with the PR, during a PR review).
- **Undo**, after you clear marks or delete comments.
- **Tabs.** *To review* and *Reviewed* show your marks as a file tree: folders, then files, then blocks (a chain of folders that each hold one folder is shown as one row). Select several rows (Cmd- or Shift-click), then press *Clear selected* or Delete. Selecting a file or folder selects all its blocks. *Reviewed* also has *Clear all*. *Notes* groups your comments under a header for each kind, with the file and lines of each one, and Claude's reply under an agent note. Click a header to fold it. Select comments and press *Delete selected* or Delete. *Today* shows the summary. Double-click or press Enter to open a row.
- **Pomodoro.** *Start reviewing!*, then *Pause* / *Resume* and *Finish*, with the minutes left. A link to Settings.

Hover over a button to see what it does and its shortcut. The buttons work on the editor you last used. Everything is also in the right-click menu, under **Review Marks**.

## More actions

- **Mark Method or Class at Caret as Reviewed.** Marks the innermost multi-line named declaration around the caret. *To review* and *Reviewed & next* also use it when nothing is selected.
- **Go to Next Code to Review.** Moves to the start of the next block marked to review or changed. At the end of the file it continues from the top.
- **Go Back to Where I Stopped.** Opens the end of the last line you marked as reviewed, also after a restart. If the file changed, it follows that line.
- **Show Today's Review Summary** (also the *Today* tab). Lines and files reviewed today, and the lines still marked to review.

## Review from git

- **Mark All Branch Changes to Review.** One click. Marks every line changed on this branch since the branch set in Settings (`main` unless you change it), committed and uncommitted.
- **Mark Uncommitted Changes to Review.** Marks every line that is not committed yet: staged and unstaged edits, and every line of new files git does not track yet. Ignored files, binary files and files over 1 MB are skipped.
- **Review Branch Changes…** shows a list of your local and remote branches. Type to search, pick one, and the lines changed since you branched from it are marked to review. Files that are new and not yet added to git are not included here.

Lines you had already marked stay as they are. These use `git`.

## Notes and comments

Select code (or put the caret on a line) and press **Note**, **PR comment** or **Agent note**:

- **Note** (yellow). Just for you.
- **PR comment** (purple). Posted as a comment on those lines when you post a PR review. A comment on several lines is posted as a multi-line comment.
- **Agent note** (teal). Feedback for Claude. See below.

A comment covers the selected lines, with a bar of its colour next to them. The same lines can have many comments, each with its own bar side by side. New comments float above the code by default (Settings, Tools, Review Marks, to change it). Click the gutter icon to edit a comment, change its kind, or delete it by leaving it empty.

### Agent notes for Claude

Every agent note is written to `.idea/review-marks-agent-notes.md` in your project folder, with the file, the lines, the code on those lines and your text. The file is rewritten when notes or lines change, and `.idea` is not committed. To get Claude to act on them, copy the skill once:

```
cp -r claude-skill/review-agent-notes ~/.claude/skills/
```

Then tell Claude `/review-agent-notes`. It reads the file, answers questions and makes the changes you asked for. It writes a reply to each note in `.idea/review-marks-agent-replies.json`, and marks a note as done when it has dealt with it. It never edits the notes file.

The *Notes* tab shows Claude's reply under each agent note (it checks for new replies every 15 seconds). **Clear resolved agent notes** deletes the agent notes Claude marked as done. Undo brings them back.

## PR review

**PR review…** uses the GitHub account signed in to the IDE (Settings, Version Control, GitHub). It does not use `gh`.

1. It reads the GitHub owner and repository from your `origin` remote and finds the open PR of the current branch. If there is none, it asks for the PR number.
2. The lines changed in the PR (as GitHub shows them) are marked to review.
3. Comments of the kind **PR comment** are posted as review comments.
4. **Post review…** shows a summary box, a choice of Comment, Approve or Request changes, and the list of comments. A comment on lines outside the PR's changes cannot be a line comment on GitHub. The dialog says how many there are, and they are added to the end of the summary instead, with their file and lines. Nothing is sent until you press **Post review**. After GitHub accepts it, PR mode ends. Your marks and notes stay.

Things to know:
- Comments are placed by line number on the PR's latest commit. You are warned when your checkout is on another commit.
- The remote host must match a GitHub account signed in to the IDE. If your remote uses an SSH host alias, list it under Settings, Tools, Review Marks, GitHub host aliases, as `alias=host`. `github-personal=github.com` is set to start.
- **End PR** stops the review without posting.

## Pomodoro

Press **Start reviewing!** at the bottom of the sidebar. The timer counts your review minutes (25 to start). When they are up, a message says it is time for a break (5 minutes to start). When the break is over, a message says the next review period starts. It repeats until you press **Finish**, which sums up the session: the minutes you reviewed, the full review periods and the lines you marked as reviewed. **Pause** stops the clock, and **Resume** carries on where it was. If you close the IDE while the timer runs, it comes back paused. The messages stay in the corner until you close them. Nothing is blocked. Set the minutes in Settings, Tools, Review Marks.

## Keyboard shortcuts

| Action | Shortcut |
|---|---|
| Add Note | Ctrl+Alt+Shift+1 |
| Add PR Comment | Ctrl+Alt+Shift+2 |
| Add Agent Note | Ctrl+Alt+Shift+3 |
| Go Back to Where I Stopped | Ctrl+Alt+Shift+6 |
| Mark Selection to Review | Ctrl+Alt+Shift+7 |
| Mark Selection as Reviewed | Ctrl+Alt+Shift+8 |
| Mark as Reviewed and Go to Next | Ctrl+Alt+Shift+9 |
| Go to Next Code to Review | Ctrl+Alt+Shift+0 |

On a Mac, Ctrl is the Control key and Alt is Option. Change them in Settings, Keymap, by searching "Review Marks".

## Build

Needs Java 21. The IDE's own Java works:

```
export JAVA_HOME="/Applications/IntelliJ IDEA.app/Contents/jbr/Contents/Home"
./gradlew test
./gradlew buildPlugin
```

The plugin zip is in `build/distributions/`. Install it with Settings, Plugins, the gear icon, Install Plugin from Disk.

`platformPath` in `gradle.properties` points at the local IntelliJ IDEA install the plugin is built and tested against. The plugin needs the bundled GitHub plugin of the IDE.
