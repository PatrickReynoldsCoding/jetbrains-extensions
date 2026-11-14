---
name: review-agent-notes
description: Read the agent notes Patrick left in IntelliJ with the Review Marks plugin and act on them. Use on /review-agent-notes, or when Patrick says "read my review notes", "act on my agent notes", or "I left comments on your changes".
---

# Review agent notes

Patrick reviews your changes in IntelliJ with the Review Marks plugin. He leaves **agent notes** on lines of code. The plugin saves them in a file in the project, so you can read them.

## Find the notes

1. The file is `.idea/review-marks-agent-notes.md`, in the project folder (the folder that holds `.idea`). Look in the current folder, then in the parent folders.
2. If the file is not there, tell Patrick: no agent notes were found. Ask him to check that he picked "Agent note" in the note dialog. Do not look anywhere else and do not guess.
3. If the file says "There are no agent notes right now.", say so and stop.

## Read the notes

Each note is a section:

```
## src/Client.kt:42

Code on that line: `retry(3) { call() }`

Why 3 retries? Make it a setting.
```

- The path is relative to the project folder. The number is the line, counted from 1.
- The code on that line is there so you can find the place again if lines have moved. If the line no longer matches, search the file for that code. If you cannot find it, say so. Do not guess.

## Act on the notes

1. Read every note before you change anything.
2. For each note, decide: is it a question, a request for a change, or a disagreement?
   - **Question:** answer it in your reply. Do not change code.
   - **Request for a change:** make the smallest change that does what the note says.
   - **Unclear:** ask Patrick. Do not fill the gap yourself.
3. Follow the project's coding style and its tests. Run the tests after your changes.
4. Reply with one line per note: the file and line, what you did or answered. Use the clickable idea:// link format from the global instructions.

## Do not

- Do not edit or delete the notes file. The plugin owns it and rewrites it. Patrick deletes a note in IntelliJ when he is happy with your answer.
- Do not commit the notes file. It is in `.idea`, which is not committed.
- Do not act on a note that is outside the project folder.
