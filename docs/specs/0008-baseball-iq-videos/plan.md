# Plan: Baseball IQ videos

Spec: [spec.md](./spec.md). No schema change; one new skill, one icon, ten videos.

- [ ] **Step 1:** In `BundledVideoLibraryTest`, expect 57 videos, an IQ count of 10, the
  ten IDs in spec order with `-3cUVRvwUiU` first, `addedIn` 3 for exactly those ten, and
  `LIBRARY_VERSION` 3. Split the tag test: the five body skills stay kid pitch 9 to 12,
  basics are coach pitch and kid pitch 7 to 12, situations are kid pitch 10 to 12. Run
  it and watch it fail.
- [ ] **Step 2:** Add `Skill.IQ` last, the `ic_skill_iq` light bulb drawable, its
  gradient in `VideoCard.kt`, a basics helper beside `kidPitch`, and the ten entries in
  `VideoLibrary.kt`. Step 1 passes.
- [ ] **Step 3:** Add recommendation cases: 7 to 8 coach pitch gets only the basics,
  9 to 10 and 11 to 12 kid pitch get all ten. Update tests whose counts follow
  `Skill.entries` or `LIBRARY_VERSION` (wizard, home, parent settings, acceptance).
- [ ] **Step 4:** A native-graphics Robolectric test that the wizard's skill row and the
  home tab row fit a 1920 by 1080 TV screen with six entries. Shrink spacing if not.
- [ ] **Step 5:** Update the library count and data line in `AGENTS.md`.
- [ ] **Step 6:** `just check` and `just acceptance` pass. Install on the owner's TV, add
  the IQ videos to a child from parent settings, and play one.
