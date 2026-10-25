# Design: more kid-pitch videos

- **Date:** 2026-10-25
- **Status:** Accepted

## Intent

The 31-video library from [0002](../0002-library-home-player/spec.md) serves children 9
to 12 who play kid pitch. The owner wants more for those children before reaching for
other ages: a catching video on the one-knee-down stance, and more of Ripken Baseball's
short training tips, whose fielding videos are already in the library.

## Goals

1. Add 16 owner-approved videos, taking the library from 31 to 47.
2. Each new video is tagged kid pitch, ages 9 to 12, like the rest, so the setup wizard
   recommends it for children of that age and level.
3. New videos go at the end of their skill, so existing order and each skill's first
   video stay as they are.

## Non-goals

- Videos for tee ball, coach pitch, or 13+. The 0004 follow-up for those stays open,
  for a later spec.
- Adding the new videos to children who already exist. Saved lists stay frozen (0003);
  changing a child's videos is parent settings, a later spec.
- Any change to screens, the database, or the schema.

## The videos

All were checked on 2026-10-25 to be embeddable, marked family safe, and under 15
minutes. Lengths are in seconds.

| Skill | YouTube ID | Title | Channel | Length |
|---|---|---|---|---|
| Hitting | kAmceQXXS-Q | Hitting Stance | Ripken Baseball | 74 |
| Hitting | TZ9CQUmPjn8 | Soft Toss | Ripken Baseball | 98 |
| Hitting | U3Rj-f1xN04 | One Arm Drill | Ripken Baseball | 113 |
| Fielding | iQ7e96-iLzo | The Backhand | Ripken Baseball | 105 |
| Fielding | qvwkdxepqTk | Outfield Drop Step | Ripken Baseball | 119 |
| Fielding | i89e1L9yKdI | Receiving the Relay | Ripken Baseball | 87 |
| Fielding | UYItjZ8hPg0 | Throwing from the Outfield | Ripken Baseball | 94 |
| Baserunning | 8T_KoHtTlo0 | The Walking Base | Ripken Baseball | 102 |
| Baserunning | 4_1yyhAaT_8 | Primary and Secondary Leads | Ripken Baseball | 149 |
| Pitching | 1Xcli14cENE | How to Throw a Baseball | Ripken Baseball | 189 |
| Pitching | hI-TQskjhGw | The Balance Drill | Ripken Baseball | 116 |
| Pitching | htV-X-KYK2I | The 1 2 3 Drill | Ripken Baseball | 101 |
| Catching | E0PiNTFyrqU | The One Knee Down Stance | YouGoProBaseball | 651 |
| Catching | U2-PTYnPmCM | Catcher Blocking | Ripken Baseball | 67 |
| Catching | NaFuBrLfj78 | Catcher's Throwing Hand Position | Ripken Baseball | 59 |
| Catching | NmAsa944EN4 | The Low Strike | Ripken Baseball | 60 |

Skill counts become hitting 10, fielding 11, baserunning 8, pitching 9, catching 9.

## Decided by the owner

- Scope is children 9 and older; 13+ is held for now.
- The one-knee video is YouGoProBaseball's "One Knee Down Catching Technique (and the
  Fruit Ninja Catching Drill)".
- The Ripken list above, with "Bunting" dropped.

## Left to the builder

- Shortened display titles, in the style of the existing entries.
