# FileXplor

A file explorer for the phone and for FTP, SFTP and SMB servers. Files are read
and written where they are - no account, no cloud, no telemetry, no activation
key.

This repository holds the signed APK. The source is not published here.

## What it does

- **Browse the phone.** Internal storage, an SD card if there is one, and
  shortcuts to Download, Pictures, Camera, Music, Movies and Documents.
- **Browse servers.** SFTP, FTP and SMB, each shown exactly like a folder on
  the phone.
- **Move things about.** Cut, copy, paste, rename, delete, new file, new
  folder - in any direction, including server to server.
- **Three views.** List, Details and Grid, with six sort orders and a switch for
  hidden files. Pull down to re-list, which matters most on a server, where the
  folder is someone else's and changes without telling you.
- **Fifteen file types**, each with its own icon and colour, with the extension
  badged across the glyph so `.kt` and `.json` are told apart as well as CODE
  from ARCHIVE. Folders carry the same badge showing how many items are inside -
  on the phone only, since on a server each count is another round trip.
- **Thumbnails.** Pictures show themselves, videos show a frame, and an APK
  shows the icon of the app inside it, which is usually the only thing that says
  what a file called `base.apk` actually is.
- **Built-in viewers.** Pictures open full screen with swipe, pinch and
  double-tap. Text, Markdown, JSON and source open in a small editor.

## A note on saving

A save never writes in place. `FileXplor` writes a temporary file beside the
target, flushes it to disk, then renames over it - a rename within a directory
is atomic, so a save that fails part way leaves the old file intact rather than
leaving neither the old nor the new. On a text file you are editing, the
destination is often the only copy, which is what makes that worth doing.

## Verify what you downloaded

| File | SHA-256 |
|---|---|
| `FileXplor-release.apk` | `3600bfe760c170f3fa8567355e484810904f533b614cceadb7e129ee5b39b78d` |

```bash
sha256sum FileXplor-release.apk                    # Linux, macOS, git bash
certutil -hashfile FileXplor-release.apk SHA256    # Windows
```

## Signing

```
CN=JApps, OU=JFamily, O=JApps, C=CA
753bf85dbbf2cd55862e494733ffe69a14eff96d9c0b8e0883fe347464c7a02f
```

```bash
apksigner verify --print-certs FileXplor-release.apk
```

That prints `v1 scheme (JAR signing): false`. It is not missing - with
`minSdk 26`, apksigner only verifies the schemes that platform range uses. Add
`--min-sdk-version 21` and v1, v2 and v3 all verify.

## Requirements

Android 8.0 or later (minSdk 26), built against SDK 36.
