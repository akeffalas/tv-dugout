# ADR-0008: App controls over the YouTube player while the app is unpublished

- **Status:** Accepted
- **Date:** 2026-10-25

## Context

[ADR-0006](./0006-youtube-embedded-player.md) plays videos in YouTube's embedded player
and keeps the app's controls below the video, never over it, because YouTube's Required
Minimum Functionality says clients "must not display overlays, frames, or other visual
elements in front of any part of an embedded player, including player controls."

Below-the-video controls cap the video at about two thirds of the screen width. The owner
asked for a full-screen player (spec [0007](../specs/0007-fullscreen-player/spec.md)). A
no-overlay version is possible: the video fills the screen and shrinks back to the
current layout on any remote press. It makes the picture jump in size every time a child
touches the remote.

Dugout TV is installed on one family's TV and is not published in the Play Store or
anywhere else. The rule exists to protect YouTube's player in distributed clients; the
practical risk for a private install is close to nil. A spike on the owner's TV on
2026-10-25 confirmed that Compose content draws over the playing WebView video.

## Decision

While Dugout TV is unpublished, we will draw the app's own player controls over the
bottom of the YouTube player: a dark fade, the title, a progress bar, and the control
buttons, plus a loading indicator over the video before it first plays.

The rest of ADR-0006 stands: the IFrame embed through android-youtube-player, the player
options, focus blocked from the WebView, captions unloaded, and the app's Up next screen
on `ENDED`.

Before any public release (Play Store or other distribution), this decision must be
revisited, and the overlay removed or the policy question settled.

## Consequences

- The video can fill the screen at all times, with controls fading in and out over it,
  as children expect from other TV video apps.
- The app no longer meets YouTube's Required Minimum Functionality. Publishing it as is
  would risk removal or loss of API access.
- YouTube's own title strip and "More videos" bar, shown at the start of a video and
  after a seek, can appear under the app's controls for a few seconds. The app cannot
  hide them.
- Compose drawing over the WebView worked on the owner's TV; another device that renders
  video on a separate hardware layer could draw the video over the controls. The
  full-screen player is checked on a real TV, not only in Robolectric.

## Alternatives considered

- **No overlay; the video shrinks when the controls show:** stays within the policy but
  the picture changes size on every press. Rejected for a private install; it is the
  fallback if the app is ever published.
- **YouTube's own controls (`controls=1`):** allowed by the policy, but they are not
  built for a D-pad, have no slow-mo button, and show "More videos" to children.
- **Keep the current layout:** no full screen, which is what the owner asked to change.
