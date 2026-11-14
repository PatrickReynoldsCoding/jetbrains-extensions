# Review Marks

An IntelliJ plugin to help you read code in small pieces and keep track of what you have read.

## Use

Right-click in the editor, then **Review Marks**:

- **Mark Selection as Reviewed.** The selected code turns green.
- **Mark Method or Class at Caret as Reviewed.** Marks the innermost multi-line named declaration (method, class, field) around the caret. Needs no selection.
- **Clear Mark at Caret.** Removes the mark under the caret.
- **Go to Next Code to Review.** Moves the caret to the start of the next block that is not reviewed or has changed. At the end of the file it continues from the top.
- **Focus Mode: Fade Reviewed Code.** Reviewed code is shown in faded text, so unread code stands out.

When you edit a line inside reviewed code, only that line turns amber. The lines above and below stay green. The amber line stays amber, also after restarting the IDE, until you mark it as reviewed again.

The status bar shows progress for the current file, for example `Reviewed 40% (120/300 lines) · 2 changed`. Empty lines are not counted. Click it to go to the next code to review.

Marks are saved per project in `.idea/workspace.xml`. When a file is opened again, a mark comes back only if its text is still at the same place, or the text now appears exactly once in the file.

The actions have no keyboard shortcuts. Bind them in Settings, Keymap, by searching "Review Marks".

## Build

Needs Java 21. The IDE's own Java works:

```
export JAVA_HOME="/Applications/IntelliJ IDEA.app/Contents/jbr/Contents/Home"
./gradlew test
./gradlew buildPlugin
```

The plugin zip is in `build/distributions/`. Install it with Settings, Plugins, the gear icon, Install Plugin from Disk.

`platformPath` in `gradle.properties` points at the local IntelliJ IDEA install the plugin is built and tested against.
