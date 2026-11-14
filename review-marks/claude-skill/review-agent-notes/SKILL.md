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

Each note is a section. A note is on one line or on a range of lines:

```
## src/Client.kt:42-44

Code on those lines:

    retry(3) {
        call()
    }

Why 3 retries? Make it a setting.
```

- The path is relative to the project folder. The numbers are lines, counted from 1. `42` is one line, `42-44` is lines 42 to 44.
- The code is there so you can find the place again if lines have moved. If the lines no longer match, search the file for that code. If you cannot find it, say so. Do not guess.
- Several notes can be on the same lines. Treat each one on its own.
- Each note has an id, in a line such as ``Note id: `ab12cd34` ``. You need it to reply.

## Act on the notes

1. Read every note before you change anything.
2. For each note, decide: is it a question, a request for a change, or a disagreement?
   - **Question:** answer it in your reply. Do not change code.
   - **Request for a change:** make the smallest change that does what the note says.
   - **Unclear:** ask Patrick. Do not fill the gap yourself.
3. Follow the project's coding style and its tests. Run the tests after your changes.
4. Write a reply to every note in `.idea/review-marks-agent-replies.json`, next to the notes file. The plugin shows each reply under its note in the IntelliJ sidebar. The file is a JSON array with one object per note:

   ```json
   [
     {"noteId": "ab12cd34", "reply": "Made the retry count a setting, retries in Config.", "resolved": true},
     {"noteId": "ef56gh78", "reply": "3 comes from the API's rate limit docs. Should it be a setting?", "resolved": false}
   ]
   ```

   - `reply`: one or two short sentences. What you did, or your answer.
   - `resolved`: `true` when you did what the note asked. `false` when you answered a question, disagreed, or need Patrick to decide.
   - Write the whole file each time: keep replies to notes that are still in the notes file, and drop replies to notes that are gone. Write valid JSON; the plugin ignores a file it cannot read.
5. Reply in the chat too, with one line per note: the file and lines, what you did or answered. Use the clickable idea:// link format from the global instructions.

## Do not

- Do not edit or delete the notes file. The plugin owns it and rewrites it. Patrick deletes notes in IntelliJ (Clear resolved agent notes) when he is happy with your answers.
- Do not commit the notes file or the replies file. They are in `.idea`, which is not committed.
- Do not act on a note that is outside the project folder.
