# Review Marks

An IntelliJ plugin to help you read code in small pieces and keep track of what you have read. It is made to keep what you must hold in your head small.

## The Review Marks sidebar

Open the **Review Marks** tool window on the right. From top to bottom:

- **Buttons** for Mark & next, Mark, Method, Clear, Next, Where I stopped and Note. They work on the editor you last used.
- **Check boxes** for Fade reviewed, Fold reviewed and Only branch changes. They show the current state and follow changes made from the right-click menu.
- **A progress bar** for the open file.
- **Tabs.** *To review* lists the code you have not read, by file and line range, with changed lines first. *Reviewed* lists all reviewed code by file and line range, with a Clear all button. *Notes* lists your notes. *Today* shows the summary. In the lists, double-click or press Enter to open a row. In *Reviewed*, press Delete to clear a block.
- **A footer** to turn pause reminders on or off, with a link to Settings. The gear in the title bar opens Settings too.

Everything in the sidebar is also in the right-click menu, under **Review Marks**.

## Marks

Right-click in the editor, then **Review Marks**:

- **Mark as Reviewed and Go to Next.** Marks the selection (or, with no selection, the method or class at the caret) as reviewed, then moves to the next code to review. With Fold Mode on, the block you just marked folds away.
- **Mark Selection as Reviewed.** The selected code turns green.
- **Mark Method or Class at Caret as Reviewed.** Marks the innermost multi-line named declaration (method, class, field) around the caret.
- **Clear Mark at Caret.** Removes the mark under the caret.

When you edit a line inside reviewed code, only that line turns amber. The lines above and below stay green. The amber line stays amber, also after restarting the IDE, until you mark it as reviewed again.

Marks are saved per project in `.idea/workspace.xml`. When a file is opened again, a mark comes back only if its text is still at the same place, or the text now appears exactly once in the file.

## Finding your way

- **Go to Next Code to Review.** Moves to the start of the next block that is not reviewed or has changed. At the end of the file it continues from the top.
- **Go Back to Where I Stopped.** Opens the end of the last line you marked as reviewed, also after a restart. If the file changed, it follows that line.
- **Progress in the status bar**, for example `Reviewed 40% (120/300 lines) · 2 changed`. Empty lines are not counted. Click it to go to the next code to review.
- **Show Today's Review Summary** (also the *Today* tab). Shows how many lines in how many files you reviewed today, and how many lines are left in the files you are reviewing (files with marks or notes, or changed on the branch).

## Less on screen

- **Focus Mode: Fade Reviewed Code.** Reviewed code is shown in faded text.
- **Focus Mode: Fold Reviewed Code.** Reviewed blocks fold into one line, such as `✓ 12 reviewed lines`. Only whole lines are folded. Expand a fold to peek at it. It stays open until the marks change.
- **Review Only Branch Changes...** Asks for a base branch or commit (it suggests `origin/main`; type another one if you need). It uses `git` to find the lines changed since you branched from it, committed and uncommitted, and fades everything else. Only the changed lines count for progress, Go to Next, and the summary. **Stop Reviewing Only Branch Changes** counts all code again. Files that are new and not yet added to git are not included.

## Notes

**Add or Edit Note on This Line...** parks a short note on a line, such as a question to ask later. The line gets an icon in the gutter that shows the note. Click the icon to edit the note. Leave the note empty to delete it. All notes are listed in the sidebar's *Notes* tab: double-click or press Enter to open one, press Delete to remove one. A note whose line was edited while the file was closed is dropped. A note is also lost when you delete its line.

## Pause reminders

Off by default. Settings, Tools, Review Marks. Choose to be reminded after a number of minutes of reviewing, after a number of reviewed blocks, or both (0 turns a limit off). The reminder is a quiet message in the corner. Nothing is blocked. If you stop marking code for longer than the minutes you set, the count starts again.

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

`platformPath` in `gradle.properties` points at the local IntelliJ IDEA install the plugin is built and tested against.
