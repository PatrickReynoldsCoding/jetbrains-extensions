# Review Marks

An IntelliJ plugin to help you read code in small pieces and keep track of what you have read. It is made to keep what you must hold in your head small.

## How it works

You choose what to read. Select code and mark it:

- **To review** (blue). Code you plan to read. It is listed in the *To review* tab.
- **Reviewed** (green). Code you have read.
- **Changed** (amber). Reviewed code that you edited. Only the edited line turns amber, and it stays amber until you mark it as reviewed again.

The progress text, for example `Reviewed 40% (120/300 lines) · 2 changed`, counts reviewed lines out of all the lines you marked (to review, reviewed or changed). Code you did not mark is not counted. Empty lines are not counted.

Marks are saved per project in `.idea/workspace.xml`. When a file is opened again, a mark comes back only if its text is still at the same place, or the text now appears exactly once in the file.

## The Review Marks sidebar

Open the **Review Marks** tool window on the right. From top to bottom:

- **Buttons.** *To review*, *Reviewed*, *Reviewed & next* (marks, then jumps to the next code to review), *Clear*, *Next*, *Where I stopped*, *Note*, and a *Review from git* menu. The toolbar has icons only, so hover over an icon to read what it does. The *Review from git* menu has *Mark All Branch Changes to Review*, *Mark Uncommitted Changes to Review*, *Review Branch Changes…* (pick any branch) and *PR review…*. While you review a PR, *Post review…* and *End PR* also show. The buttons work on the editor you last used.
- **Fold reviewed.** Folds reviewed blocks into one line, such as `✓ 12 reviewed lines`. Only whole lines are folded. Expand a fold to peek at it. It stays open until the marks change.
- **A progress bar** for the open file.
- **Tabs.** *To review* lists the code you marked to review, by file and line range, with changed lines first. *Reviewed* lists all reviewed code by file and line range, with a Clear all button. *Notes* lists your notes. *Today* shows the summary. In the lists, double-click or press Enter to open a row, and press Delete to clear a block.
- **A footer** to turn Pomodoro breaks on or off, with the minutes left and a link to Settings. The gear in the title bar opens Settings too.

Everything in the sidebar is also in the right-click menu, under **Review Marks**.

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

## Notes

**Note** (or right-click a line, Review Marks, Add or Edit Note) parks a note on a line, such as a question to ask later. Pick what kind of note it is:

- **Note.** Just for you.
- **PR comment** (💬). Posted as a line comment when you post a PR review.
- **Agent note** (🤖). Feedback for Claude. See below.

The gutter icon shows the kind. Click the icon to edit the note. Leave the note empty to delete it. **Show this note above the code** makes the note float above the line, in the editor. All notes are listed in the *Notes* tab. A note whose line was edited while the file was closed is dropped. A note is also lost when you delete its line.

### Agent notes for Claude

Every agent note is written to `.idea/review-marks-agent-notes.md` in your project folder, with the file, the line, the code on that line and your text. The file is rewritten when notes or lines change, and `.idea` is not committed. To get Claude to act on them, copy the skill once:

```
cp -r claude-skill/review-agent-notes ~/.claude/skills/
```

Then tell Claude `/review-agent-notes`. It reads the file, answers questions, makes the changes you asked for and replies line by line. It never edits the file. You delete a note in IntelliJ when you are happy.

## PR review

**PR review…** uses the GitHub account signed in to the IDE (Settings, Version Control, GitHub). It does not use `gh`.

1. It reads the GitHub owner and repository from your `origin` remote and finds the open PR of the current branch. If there is none, it asks for the PR number.
2. The lines changed in the PR (as GitHub shows them) are marked to review.
3. Notes of the kind **PR comment** are posted as review comments. The *Notes* tab marks them with 💬.
4. **Post review…** shows a summary box, a choice of Comment, Approve or Request changes, and the list of comments. Nothing is sent until you press **Post review**. After GitHub accepts it, PR mode ends. Your marks and notes stay.

Things to know:
- Comments are placed by line number on the PR's latest commit. You are warned when your checkout is on another commit. A comment on a line that is not part of the PR's changes is refused by GitHub, and nothing is posted.
- The remote host must match a GitHub account signed in to the IDE. A host alias such as `github-personal` does not match, so the plugin does not guess an account.
- **End PR** stops the review without posting.

## Pomodoro reminders

Off by default. Turn them on in the sidebar footer (*Pomodoro breaks*) or in Settings, Tools, Review Marks. Set the minutes of work and the minutes of break (25 and 5 to start). The plugin keeps a timer: when the work minutes are up, a message says it is time for a break. When the break is over, a message says the next work period starts. Then it repeats. The messages stay in the corner until you close them. Nothing is blocked. The footer shows how many minutes are left.

The timer runs while the IDE is open and reminders are on. It does not wait for you to mark code.

## Keyboard shortcuts

The actions have no shortcuts. Bind them in Settings, Keymap, by searching "Review Marks".

## Build

Needs Java 21. The IDE's own Java works:

```
export JAVA_HOME="/Applications/IntelliJ IDEA.app/Contents/jbr/Contents/Home"
./gradlew test
./gradlew buildPlugin
```

The plugin zip is in `build/distributions/`. Install it with Settings, Plugins, the gear icon, Install Plugin from Disk.

`platformPath` in `gradle.properties` points at the local IntelliJ IDEA install the plugin is built and tested against. The plugin needs the bundled GitHub plugin of the IDE.
