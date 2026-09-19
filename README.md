# FileXplor

A file explorer for the phone and for FTP, SFTP and SMB servers. Files are read
and written where they are - no account, no cloud, no telemetry, no activation
key.

This repository holds the source. The signed APK is published under Releases.

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

## Install it

Android will not install an app from outside a store until you allow it once.
That is a normal part of installing anything this way, not a sign of a problem.

1. On the phone, open [Releases](../../releases) and download
   `FileXplor-release.apk`.

2. Tap the downloaded file. Android will say it is not allowed to install unknown
   apps from this source.

3. Tap **Settings** in that message, turn on **Allow from this source**, then press
   back and tap **Install**.

4. Open it. It will ask for permission to see your files, which is the whole
   point of a file explorer. Servers are added later from inside the app.

You can turn that permission back off afterwards. It applies to the app you
downloaded with, usually your browser, and not to the phone as a whole.

Updating later is the same steps, and installing over the top keeps your data.

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

## If you want to say thanks

FileXplor is free and stays free. There is nothing to unlock, no account, and
nothing here is gated behind a donation.

If you get use out of it and feel like sending something, these are the only
addresses I use. Check them character by character - transfers on both chains
are irreversible.

| Chain | Address |
|---|---|
| Solana | `862YZXoRvaoTiP1AkQEEZ5FFGgPFsUhbEoRu4r44RhSe` |
| Ethereum | `0xF890c6A128920D145D47753D0b1159fA4Db2861d` |

Send only native SOL or ETH, or standard tokens on those chains. Anything sent
on a different network is lost.

No obligation either way. A bug report is worth just as much.
