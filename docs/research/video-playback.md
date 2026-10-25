# Video playback on Google TV

Research for the player screen in [the mockup](../mockups/tv-mockup.html): how Dugout TV
can play approved baseball skill videos with slow-mo, remote control, a watched check
(at least 90% viewed), and "next in Hitting" instead of YouTube suggestions. Gathered
2026-10-25. These are findings, not a decision.

## What the app needs from a player

| Need | Why |
| --- | --- |
| Play a specific approved video | The catalog is a fixed list chosen by a parent |
| 0.5× playback | Slow-mo for studying a swing |
| Current position and duration | Watched check at 90%, "Keep watching" resume |
| An end event | Leave for "next in Hitting" before any suggestions matter |
| Control from Compose with the D-pad | Remote first; focus must not get trapped |
| Videos under 15 minutes | Kid-sized lessons; enforced when a video is added |

## Where the videos live

Little League, USA Baseball, and MLB publish free drill videos on YouTube channels, so
most of the catalog will likely be YouTube video IDs. USA Baseball also hosts drill videos
inside its own Mobile Coach app, which offers no embed. CoachTube is a course site; it
has free courses but no documented way to embed its videos elsewhere, so treat it as
unavailable unless a specific video is also on YouTube.

## Options

### A. YouTube IFrame Player in a WebView

The official embedded player, driven through the IFrame Player API. On Android this can
be done by hand or through
[android-youtube-player](https://github.com/PierfrancescoSoffritti/android-youtube-player)
(`core` 13.0.0, September 2025), which wraps the same API in a `View` and sends the app
package as the origin. That fixes error 153 (missing Referer or client identity), which a
hand-rolled WebView must handle itself.

- **Slow-mo:** `setPlaybackRate(0.5)` is a request, not a guarantee. Check
  `getAvailablePlaybackRates()` per video and confirm with `onPlaybackRateChange`. Loading
  a new video resets the rate to 1.
- **Watched tracking:** `getCurrentTime()` and `getDuration()` are available; duration is
  0 until metadata loads.
- **End of video:** `onStateChange` reports `ENDED` (0). The app can navigate to its own
  "next in Hitting" screen at that point.
- **Suggestions cannot be turned off.** Since 2018 `rel=0` only limits suggestions to the
  same channel. The developer policies forbid modifying, blocking, or obscuring any part of
  the player (III.I.4, III.I.6), so the app must not cover or hide them. Leaving the player
  screen on `ENDED` is the compliant way to avoid showing them. Creator end screens are
  already off for videos marked Made for Kids.
- **No overlays.** The required minimum functionality forbids drawing anything in front
  of the player, including over its controls. The mockup's control bar must sit beside or
  below the video, not on top of it.
- **Ads** served by YouTube cannot be blocked (III.I.5). Videos marked Made for Kids get
  only non-personalized ads.
- **Kids obligations:** for Made for Kids videos the client must turn off tracking and keep
  data collection COPPA compliant (III.E.4.j). A child-directed client must self-designate
  with Google and may not do write actions (III.J). Dugout TV collects nothing and writes
  nothing, which fits.
- **Other rules:** player viewport at least 200×200 px; no autoplay unless more than half
  the player is visible; no background play; no downloading or caching.

### B. Hand off to the YouTube or YouTube Kids app

Send an intent with the video ID. Simple and fully compliant, but the app loses slow-mo,
position tracking, the end event, and "next in Hitting", and the child lands in YouTube's
own suggestions. This defeats the reason for the app.

### C. Media3 (ExoPlayer) on extracted YouTube streams

Pull stream URLs the way NewPipe-style tools do and play them natively. Full control of
the UI and speed, but it breaks the developer policies (III.E.1, III.I.7, III.I.14) and
breaks whenever YouTube changes its internals. Not viable.

### D. Media3 (ExoPlayer) on direct video files

Native playback of MP4 or HLS URLs the parent has the right to use. Full control: any
speed, custom controls drawn anywhere, no suggestions or ads. It only works for videos
hosted outside YouTube, which the current sources do not offer. Worth keeping as a second
source type later.

## Comparison

| | A. IFrame in WebView | B. YouTube app | C. Extracted streams | D. Direct files |
| --- | --- | --- | --- | --- |
| Allowed by YouTube terms | Yes | Yes | No | Not YouTube |
| 0.5× | Usually (check per video) | No | Yes | Yes |
| Position for 90% check | Yes | No | Yes | Yes |
| End event for "next" | Yes | No | Yes | Yes |
| No suggestions | Only by leaving on end | No | Yes | Yes |
| Custom controls over video | No | No | Yes | Yes |
| Works with the planned sources | Yes | Yes | Yes | No |

## Leaning

Option A, through android-youtube-player, is the only option that is allowed and works
with the sources in the mockup. The catalog should store a source type alongside the video
ID so option D can be added later without reshaping the data.

After the spike (below), the owner agreed on these details:

- Controls sit below the player, and the player view blocks descendant focus.
- Captions are unloaded once playing, accepting the gray area in result 9.
- Creator end-screen cards are allowed to show, since they cannot be selected. Videos are
  not cut short by a fixed amount, and Made for Kids status is not required: many good
  drill videos come from coaching channels aimed at adults, and checking the flag needs a
  Data API key.
- A possible later addition for parent settings: an optional per-video "stop at" time,
  after which the app moves to "Up next".

## On-device spike results

A throwaway build (since deleted) ran option A on a Google TV
2026-10-25: android-youtube-player 13.0.0, `controls=0`, `rel=0`, `iv_load_policy=3`,
the player 560dp wide with Compose buttons below it, driven by D-pad key events.

1. **Playback works** with no client identity error (153); the library's origin handling
   is enough.
2. **0.5× worked** on both drill videos tried. `onPlaybackRateChange` confirmed it within
   about 0.2 s.
3. **Pausing shows no "More videos" panel** with `controls=0`. Seeking briefly shows the
   video title, a channel avatar, and a "More videos" strip over the top of the video.
4. **`ENDED` arrives cleanly.** Swapping to an app-drawn "Up next" screen on `ENDED` meant
   the suggestion grid was never seen.
5. **Creator end screens are the real leak.** A non-Made-for-Kids video showed the
   channel's own "WATCH MORE" cards (one for a soccer video) over the last 20 seconds,
   before `ENDED`. The app cannot hide them. With player focus blocked they cannot be
   selected with the remote, but a child still sees them.
6. **Focus:** with `FOCUS_BLOCK_DESCENDANTS` on the player view, focus stayed in Compose
   throughout. When the WebView was allowed focus, Up moved focus into it with no visible
   indicator; Down brought it back out. Blocking is the right default.
7. **Ads:** none played across three loads. Not conclusive.
8. **Embedding:** all five candidate videos returned 200 from YouTube's oEmbed endpoint,
   which is a cheap check a parent screen could run when a video is added.
9. **Captions** appeared without being requested, even though the TV's caption setting
   is off and the library sends `cc_load_policy=0`. The embed has no documented way to
   force captions off. Calling `player.unloadModule('captions')` from the WebView once
   playing removed them. That call is not in the IFrame API documentation, so it sits in
   a gray area under the rule against undocumented player changes.
10. **Layout:** the TV reports 960dp of width at 1080p, so sizes in the mockup halve when
    written in dp.

## Sources

- [YouTube embedded player parameters](https://developers.google.com/youtube/player_parameters)
- [YouTube IFrame Player API reference](https://developers.google.com/youtube/iframe_api_reference)
- [YouTube required minimum functionality](https://developers.google.com/youtube/terms/required-minimum-functionality)
- [YouTube API Services developer policies](https://developers.google.com/youtube/terms/developer-policies)
- [YouTube API Services terms of service](https://developers.google.com/youtube/terms/api-services-terms-of-service)
- [Finding the Made for Kids status of a video](https://developers.google.com/youtube/v3/guides/made_for_kids_status)
- [YouTube Help: end screens](https://support.google.com/youtube/answer/6388789)
- [android-youtube-player releases](https://github.com/PierfrancescoSoffritti/android-youtube-player/releases)
- [CoachTube baseball courses](https://coachtube.com/courses/baseball)
- [USA Baseball Mobile Coach](https://www.sartellbaseball.com/mobilecoach)
