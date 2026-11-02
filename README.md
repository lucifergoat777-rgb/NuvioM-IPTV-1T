<div align="center">

# NuvioM + IPTV + 1Tube

**Movies and shows, live TV, sports and YouTube in one app for Android phones and tablets, with casting.**

[![Latest release](https://img.shields.io/github/v/release/zoga2o3-ops/NuvioMPlusIPTV-1Tube?include_prereleases&label=latest&style=for-the-badge)](https://github.com/zoga2o3-ops/NuvioMPlusIPTV-1Tube/releases)
[![Downloads](https://img.shields.io/github/downloads/zoga2o3-ops/NuvioMPlusIPTV-1Tube/total?style=for-the-badge)](https://github.com/zoga2o3-ops/NuvioMPlusIPTV-1Tube/releases)
[![License](https://img.shields.io/badge/license-GPL--3.0-blue?style=for-the-badge)](LICENSE)
<a href="https://www.buymeacoffee.com/zoga2o3m"><img src="https://img.shields.io/badge/Buy%20me%20a%20coffee-5F7FFF?style=for-the-badge&logo=buymeacoffee&logoColor=white" alt="Buy me a coffee" /></a>

[What is it](#what-is-it) · [What you get](#what-you-get) · [Install](#install) · [Getting started](#getting-started) · [Bugs](#reporting-bugs-and-asking-for-features) · [Support](#support-the-project) · [Credits](#credits)

</div>

> ⚠️ **Beta software.** Expect bugs, rough edges and things that change between releases. Please report what you find.
>
> This app **does not provide any content, playlists or streams.** You bring your own sources.

## What is it

NuvioM + IPTV + 1Tube puts three things in one phone and tablet app:

1. **[Nuvio Mobile](https://github.com/NuvioMedia/NuvioMobile):** the home screen, addons, Trakt and Simkl, your Nuvio account and Nuvio's player.
2. **IPTV:** Live TV, Sports, Calendar, recordings and On Demand, from [NuvioPlusIPTV](https://github.com/homelessbrian/NuvioPlusIPTV) by homelessbrian, brought over to the phone.
3. **1Tube:** a YouTube section of its own, played in the same player.

It's the phone companion to the **[Nuvio + IPTV + 1Tube TV app](https://github.com/zoga2o3-ops/NuvioPlusIPTV-1T)**, and the two share your Live TV setup through Google Drive. It has its own package name, so it installs next to the official Nuvio app.

## What you get

**Live TV**
- M3U links and Xtream logins, XMLTV guides, several at once, each one editable and switchable.
- List or grid channels, a guide grid, your own groups, copy channels between groups, rename, renumber, change logos, hide, favorites and recents.
- Search by channel, or by programme to see what's on now or soon.
- Catch-up replay (where your provider supports it), a pause and rewind buffer, a backup channel if one drops, captions, passthrough audio and frame-rate matching.
- Swipe up or down in the picture to change channel, an in-player channel list, quality badges, reminders and a sleep timer.
- A parental PIN for groups, with a setting for how long they stay unlocked.
- Playlist repair, so provider streams with small faults still play.

**Recording**
- Record now, schedule from the guide, record a whole series, keep the last N, and resume where you left off.

**On Demand**
- Your provider's movies and series as rows, with search, genres, sorting, several versions of a title and an episode picker.
- Continue watching on Home, show or hide, rename, reorder and lock groups, and Fix poster.
- "Watch On Demand" shows up in Nuvio's own stream list.

**Sports**
- Scores by day and league, My Teams, league tables, game details, which of your channels carry a game, a video window on the page, record a game, reminders for the teams you follow, and an optional switch to the game when it starts.

**Calendar**
- Upcoming, recently aired, airing today and Following, movie release dates for your Library, "On my TV" (where your channels carry a show), a bell on a show's page, and a New episodes row on Home.

**YouTube**
- A tab that needs no account: browse, search, captions, chat and SponsorBlock, with sound tracks that start on the original language.

**Casting**
- Google Cast from the shared player: movies, series, live channels and YouTube.

**Everywhere**
- Search shows matching channels, shows on now and On Demand titles next to Nuvio's results, and Home gets Live TV, New episodes and On Demand rows.
- Simkl signs in with a code. Signing out of Nuvio also clears Live TV and the Drive link.
- **Google Drive sync:** back up and restore your Live TV setup between your phone and your TV, with your own Drive.

## Known rough edges

- Not every phone and tablet has been tried, so other devices may behave differently.
- Casting has only been tried with a limited set of Cast devices. Some streams can't be cast, such as the phone's own P2P server or local files.
- Recording, "On my TV", auto-switch to a game, protected (DRM) channels have had the least testing.

## Install

Download the APK for your phone from the [Releases](https://github.com/zoga2o3-ops/NuvioMPlusIPTV-1Tube/releases) page. Most phones need `arm64-v8a`. After that the app updates itself (Settings → About).

## Getting started

1. Open the app and sign in with your Nuvio account, or continue without one.
2. Go to **Live TV → Sources** and add an M3U link or Xtream login, plus a guide if you have one.
3. The YouTube tab works straight away. To cast, press the Cast button in the player.

## Reporting bugs and asking for features

Please check the [existing issues](https://github.com/zoga2o3-ops/NuvioMPlusIPTV-1Tube/issues) first, then open a new one with your phone model, the app version and what you did. Problems that also happen in the official app belong with [Nuvio](https://github.com/NuvioMedia/NuvioMobile/issues).

## FAQ

<details>
<summary>Does it come with channels, movies or videos?</summary>

No. It plays what you add yourself: your own playlists, guides, Xtream logins and addons, plus YouTube.
</details>

<details>
<summary>Will it replace my Nuvio app?</summary>

No. It has its own package name and installs next to it.
</details>

<details>
<summary>What does Google Drive sync store?</summary>

Your Live TV setup, in a file in your own Google Drive.
</details>

## Support the project

If you like it, a coffee keeps the updates coming. Totally optional.

<a href="https://www.buymeacoffee.com/zoga2o3m"><img src="https://img.shields.io/badge/Buy%20me%20a%20coffee-5F7FFF?style=for-the-badge&logo=buymeacoffee&logoColor=white" alt="Buy me a coffee" /></a>

## Credits

- **Nuvio** by the NuvioMedia team, which this app is built on.
- **NuvioPlusIPTV** by homelessbrian: the Live TV guide, Sports, recordings and On Demand.
- **SmartTube / MediaServiceCore** by Yurii Liskov (MIT licence): the YouTube engine.
- **SponsorBlock** by Ajay Ramachandran and its community.
- Sports data from ESPN's public feed and TheSportsDB. Guide design inspired by TiviMate.
- This product uses the TMDB API but is not endorsed or certified by TMDB.

This is an unofficial fork. It is **not affiliated with or endorsed by NuvioMedia, NuvioPlusIPTV, SmartTube, SponsorBlock, TiviMate, Google or YouTube.**

## Legal

This app is a client-side player. It does not host, store or distribute any media, and it ships with no channels, playlists or content sources. Only use sources you own or are authorized to access. Nuvio's [legal and DMCA information](https://github.com/NuvioMedia/NuvioTV#legal--dmca) applies here too.

Licensed under the **GNU General Public License v3.0**, like upstream Nuvio. See [LICENSE](LICENSE). If you share a build, share its source too.

## Building

You need JDK 17 or newer and the Android SDK.

```bash
git clone --recurse-submodules https://github.com/zoga2o3-ops/NuvioMPlusIPTV-1Tube.git
cd NuvioMPlusIPTV-1Tube
./gradlew :androidApp:assembleFullDebug
```

The debug build installs as `app.onetube.mobile.debug`.

<details>
<summary><code>local.properties</code> keys (optional, not committed)</summary>

Features that need a key stay switched off without one.

| Key | Used for |
| --- | --- |
| `TMDB_API_KEY` | Movie dates in the Calendar, title logos, Fix poster |
| `SIMKL_CLIENT_ID`, `SIMKL_APP_NAME` | Simkl sign-in |
| `TRAKT_CLIENT_ID`, `TRAKT_CLIENT_SECRET`, `TRAKT_REDIRECT_URI` | Trakt sign-in |
| `GOOGLE_DRIVE_CLIENT_ID`, `GOOGLE_DRIVE_CLIENT_SECRET` | Live TV Drive sync (a Google OAuth client of type "TVs and limited input devices") |
| `NUVIO_SUPABASE_URL`, `NUVIO_SUPABASE_ANON_KEY` | Nuvio account sync |
| `NUVIO_RELEASE_STORE_FILE`, `NUVIO_RELEASE_STORE_PASSWORD`, `NUVIO_RELEASE_KEY_ALIAS`, `NUVIO_RELEASE_KEY_PASSWORD` | Signing release builds |
</details>

<details>
<summary>Making a release</summary>

```powershell
.\scripts\release-mobile.ps1 -Version 0.6.6-beta1 -Build 1
```

builds the signed APKs into `build\releases`. Add `-Publish -Beta -Notes "..."` to put them on GitHub. The comments in the script explain the rules the updater follows. Keep your signing key safe: without it installed copies cannot be updated.
</details>
