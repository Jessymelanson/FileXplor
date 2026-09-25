# Changelog

## 1.0.1 - 2026-09-24

### New

- **A folder's Details show what is inside it**: how many files and folders,
  all the way down, and their total size, for example
  `1,234 files in 56 folders` and `3.2 GB (3,435,973,836 bytes)`. The numbers
  fill in while it counts, on the phone and on servers. A file's Details give
  its exact size in bytes as well.

### Transfers

- **A download the server cut short was reported as finished.** If an FTP
  server stopped sending part way through a file, FileXplor kept the partial
  file under the real name and said "1 item copied". On a move it then deleted
  the original from the server, so the only complete copy was lost. Every file
  is now checked against its size when it arrives. A short one is fetched again,
  and a move removes the originals only after all of them have arrived whole.
- **Stop now stops.** During a single large file it did nothing until the file
  had finished, then kept it. It now stops within a moment and removes what was
  written.
- **A file only gets its real name once it has all arrived.** It is written
  under a hidden `.filexplor-part` name first, so an interrupted transfer never
  leaves a file that looks complete.
- **A paste that will not fit is refused before it starts**, with how much it
  needs and how much is free. It used to fill the phone for minutes and fail at
  the end.
- **After FTP dropped a connection, the next action on that server failed**
  with a `NullPointerException` message instead of reconnecting. This happened
  after a stopped or failed transfer, and after previewing a large text file.
- **The progress bar shows how far along it is**, for example
  `57% · 1.2 GB of 2.1 GB`, and it no longer sits under the gesture bar.
- **The paste bar hides while a paste is running.** It comes back only if
  nothing was pasted or the paste was stopped.

### Names

- **Names are compared ignoring case**, as the phone's storage and Windows
  servers do. Pasting `notes.txt` into a folder holding `Notes.txt` replaced
  that file; it now arrives as `notes (2).txt`.
- **Renaming onto a name already in use is refused.** SMB and most FTP servers
  replaced the other file without a word.
- **Changing only the case of a name works on the phone**, for example
  `notes.txt` to `Notes.txt`. It used to be refused as a clash with itself.
- **FTP servers that list `.` and `..` no longer trap a copy or delete in an
  endless loop.**

### Other fixes

- Editing a server to point at a different address forgets the old SSH host
  key. The new machine used to be reported as an intercepted connection until
  the server was deleted and added again.
- Folder item counts leave out hidden files unless hidden files are shown.
- Saving in the text editor updates the file's size in the folder straight
  away.
- The filter box has the keyboard as soon as it opens.
- The FTP warning said it sends your "photos" unencrypted. It says "files".

## 1.0.0 - 2026-09-18

First release.
