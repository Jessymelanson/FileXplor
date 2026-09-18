# FileXplor

A file explorer for the phone and for FTP, SFTP and SMB servers. Sixth app in
the JApps family, and the only one that is not about a single kind of document.

No account, no cloud, no telemetry, no activation key. Files are read and
written where they are.

## What it does

- **Browse the phone.** Internal storage, an SD card if there is one, and
  shortcuts to Download, Pictures, Camera, Music, Movies and Documents.
- **Browse servers.** SFTP, FTP and SMB, each shown exactly like a folder on
  the phone.
- **Move things about.** Cut, copy, paste, rename, delete, new folder - in any
  direction, including server to server.
- **Three views.** List, Details and Grid, with six sort orders and a switch
  for hidden files. Pull down to re-list - which matters most on a server,
  where the folder is someone else's and changes without telling you.
- **Fifteen file types**, each with its own icon and colour, and the extension
  badged across the glyph so `.kt` and `.json` are told apart as well as CODE
  from ARCHIVE. Folders carry the same badge showing how many items are inside
  - on the phone only, since on a server each count is another round trip.
- **Thumbnails.** Pictures show themselves, videos show a frame, and an APK
  shows the icon of the app inside it - which is usually the only thing that
  says which app a file called `base.apk` actually is.
- **Built-in viewers.** Pictures open full screen with swipe, pinch and
  double-tap. Text, Markdown, JSON and source open in a small editor with a
  save button - and saving goes back through the same interface, so editing a
  config file on a server works without copying it to the phone and back. PDFs
  render page by page with no dependency at all.
- **Folder shortcuts, to either home screen.** Any folder - on the phone or
  deep inside a server - can be kept under *Your folders* on FileXplor's own
  home screen, put on the phone's home screen as a launcher icon, or both.
  The saved list is renameable and reorderable; a pinned icon opens the app
  straight into that folder, and one pointing at a server still goes through
  the unlock prompt.
- **Transfers keep going with the screen off.** A copy, move, delete or
  download runs behind a foreground service holding a wake lock, so locking the
  phone no longer kills it - verified against forced Doze. Progress shows in a
  notification and on a bar the app carries below every screen, and a job that
  finishes while you are not looking leaves its result in the shade.
- **A transfer log.** Every file a transfer could not manage is written down
  with the job, the time and the reason, and kept until you clear it - under
  the menu on the home screen or on any folder. A snackbar saying "3 failed"
  never names the three.
- **Open and share.** Hands anything else to whatever on the phone opens it,
  including APKs to the package installer. A file on a server is downloaded to
  the cache first, since Android can only pass another app a real path.

## The one idea it is built on

Everything hangs off `FileSource`: one interface with list, read, write,
rename, delete and mkdir on it. `LocalFileSource` implements it with
`java.io.File`; `RemoteFileSource` implements it by delegating to the
`RemoteClient` the rest of this family already uses for FTP, SFTP and SMB.

Nothing above that layer knows which it is talking to. Copying a folder from an
SMB share into Download is the same code as copying it the other way, the
browse screen draws either without a branch, and a fourth protocol would mean
writing one class and touching nothing else.

## Storage permission

FileXplor asks for `MANAGE_EXTERNAL_STORAGE` - "All files access". Scoped storage
shows an app its own sandbox and media it can name, which is not a file
explorer: browsing Download, following a folder another app made, or moving a
zip out of one are all out of reach without it.

The app runs without it and says so plainly on the home screen, with a button
to the Settings page that grants it. Servers work either way.

## Building

JDK 17 (not 25), Gradle 8.13 via the committed wrapper, AGP 8.13.2, Kotlin
2.1.0, compileSdk 36, minSdk 26 - the same toolchain as the JApps, though this
is not one of them.

```
./gradlew assembleDebug assembleRelease lintDebug testDebugUnitTest
```

Both APKs are copied to the project root at the end of a build, as with the
other five.

## Known gaps

- **No live server test here.** The FTP, SFTP and SMB clients are the ones
  shipping in the other five apps, but the streaming `openRead` added here, and
  the reconnect-on-idle in `RemoteFileSource`, have not been run against a real
  server from this machine. The decision the reconnect turns on - whether a
  failure is a dead socket or a real answer from the server - is covered by
  `ConnectionLostTest`, but the reconnect itself wants a live box and an idle
  timeout to prove out.
- **No archive support.** Zips are listed like any other file and opened by
  whatever else handles them.
- **Thumbnails are local only.** Previewing a server folder would mean
  downloading every picture in it to draw one screen, and again on every
  scroll. Remote images keep the typed icon.
- **Opening a server file opens a copy.** Edits to it are not written back.
