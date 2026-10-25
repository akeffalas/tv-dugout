# Plan: more kid-pitch videos

Spec: [spec.md](./spec.md). One task; no schema or screen changes.

- [x] **Step 1:** In `BundledVideoLibraryTest`, expect 47 videos, the new skill counts,
  and the 16 new IDs at the end of their skills. Run it and watch it fail.
- [x] **Step 2:** Add the 16 entries to `VideoLibrary.kt`.
- [x] **Step 3:** Update the wizard tests whose counts follow the hitting list (7 becomes
  10): `SetupViewModelTest.chosenCountsPerSkill` and `VideosStepTest`.
- [x] **Step 4:** Update the library count and the not-built line in `AGENTS.md`.
- [ ] **Step 5:** `just check` passes. Install on the owner's TV and play one new video.
