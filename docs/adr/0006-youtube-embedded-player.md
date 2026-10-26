# ADR-0006: YouTube's embedded player for video playback

- **Status:** Amended by ADR-0008
- **Date:** 2026-10-25

## Context

Dugout TV plays an approved list of baseball skill videos (spec
[0002](../specs/0002-library-home-player/spec.md)). The sources the owner chose (Little
League, Ripken Baseball, MLB Network, and youth instructors) host their free videos on
YouTube. The app needs 0.5× slow-mo, the playback position for the 90% watched check, an
end event so it can show its own "Up next" screen instead of YouTube's suggestions, and
control from a TV remote.

The options and an on-device spike are recorded in
[the video playback research note](../research/video-playback.md). YouTube's terms forbid
downloading or extracting streams and forbid drawing over, modifying, or blocking the
embedded player.

## Decision

We will play videos with YouTube's IFrame embedded player inside a WebView, through
android-youtube-player `core` 13.0.0, behind the app's own `VideoPlayer` interface.

- Player options: `controls=0`, `rel=0`, `iv_load_policy=3`, `fullscreen=0`.
- The app's controls (Play/Pause, Back 10s, Slow-mo) and progress bar sit below the
  video, never over it.
- The player view blocks descendant focus, so the remote never enters the WebView.
- Once a video plays, the app calls `player.unloadModule('captions')`, because the embed
  has no documented way to keep captions off.
- When a video ends, the app replaces the player with its own "Up next" screen. Creator
  end-screen cards that appear before the end are allowed to show; they cannot be
  selected with the remote.

## Consequences

- Playback stays within YouTube's terms, and works for every source in the library.
- YouTube's ads may play and cannot be blocked. Videos marked Made for Kids receive only
  non-personalized ads.
- Seeking briefly shows YouTube's title strip and a "More videos" button over the video.
- `unloadModule` is not in the IFrame API documentation. If YouTube removes it, captions
  return and nothing else breaks.
- 0.5× is a request YouTube may refuse; the app shows slow-mo as on only after YouTube
  confirms it.
- The player needs a WebView, which Robolectric cannot run. Tests use a fake
  `PlayerSurface`; real playback is checked on a TV.
- The `VideoPlayer` seam and the library's `VideoSource` field leave room for a Media3
  player for non-YouTube videos later.

## Alternatives considered

- **Hand off to the YouTube or YouTube Kids app:** no slow-mo, no position, no end event,
  and the child lands in YouTube's suggestions.
- **Media3 (ExoPlayer) on extracted YouTube streams:** full control, but it breaks
  YouTube's developer policies and breaks whenever YouTube changes its internals.
- **Media3 on direct video files:** full control, but none of the chosen sources offer
  files.
- **A hand-written IFrame WebView:** the same player with more code to own, including the
  client identity handling the library already does.
