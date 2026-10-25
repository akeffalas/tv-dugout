# Parent settings and PIN: Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A 4-digit parent PIN (with a recovery code and a lockout) guards a two-pane parent settings area where a parent sees each child's progress, edits their profile and video list, resets progress, removes them, adds a player, and changes the PIN.

**Architecture:** Database version 3 adds `profile.offered_library_version` and a single-row `parent_lock` table. A new `ParentLock` (in memory, written through like `InMemoryProfileRepository`) checks PINs with a `PinHasher` (PBKDF2 then an HMAC whose key lives in the Android Keystore, behind `PinKey`) and counts the lockout on the monotonic clock. `ProfileRepository` gains update, set videos, and remove; `WatchHistory` gains reset. A `parent/pin` package holds one `PinFlowViewModel` for every PIN screen; a `parent/settings` package holds one `ParentSettingsViewModel` and stateless pane composables. `DugoutApp` starts on the first-run PIN screen when there is no PIN and no child.

**Tech Stack:** Kotlin, Compose for TV (`androidx.tv:tv-material`), Navigation Compose, Room 2.8.5 with KSP, `javax.crypto` and `java.security` (no new dependency), kotlinx-coroutines, JUnit 4, Robolectric, Truth, Kover, UiAutomator.

**Spec:** [spec.md](./spec.md). Layout: [parent-settings-mockup.html](../../mockups/parent-settings-mockup.html). Decision: [ADR-0007](../../adr/0007-room-for-saved-data.md).

## Global Constraints

- Branch `feat/0006-parent-settings` (holds the spec). Conventional commits, human author only, no co-author trailer, never `--no-verify`.
- No new dependency. Crypto uses `javax.crypto.SecretKeyFactory("PBKDF2WithHmacSHA256")`, `javax.crypto.Mac("HmacSHA256")`, `java.security.SecureRandom`, `java.security.MessageDigest.isEqual`, and the `AndroidKeyStore` provider.
- Database version 3, schema exported to `app/schemas/tv.dugout.storage.DugoutDatabase/3.json` and committed. Migration 2 to 3 sets `offered_library_version` to 1 for existing children. No destructive fallback.
- PIN: exactly 4 digits. Recovery code: 8 characters from `23456789ABCDEFGHJKMNPQRSTUVWXYZ`, shown as `XXXX-XXXX`; input is uppercased with dashes and whitespace removed before checking.
- Lockout: one count shared by PIN and code. When a wrong entry brings the count to a multiple of 5, the wait is `min(60, 2^(count / 5 - 1))` minutes (1, 2, 4, 8, 16, 32, 60, 60...). A correct entry sets the count to 0. The wait is timed with `SystemClock.elapsedRealtime` and saved with the boot count (`Settings.Global.BOOT_COUNT`): a restart in the same boot resumes it, and after a reboot a wait still in force starts again at its full length. The wall clock is never read by the lock.
- A new PIN and its code are saved together only on `I've saved it`.
- Library versions: the 16 videos added in 0005 have `addedIn = 2` (`kAmceQXXS-Q`, `TZ9CQUmPjn8`, `U3Rj-f1xN04`, `iQ7e96-iLzo`, `qvwkdxepqTk`, `i89e1L9yKdI`, `UYItjZ8hPg0`, `8T_KoHtTlo0`, `4_1yyhAaT_8`, `1Xcli14cENE`, `hI-TQskjhGw`, `htV-X-KYK2I`, `E0PiNTFyrqU`, `U2-PTYnPmCM`, `NaFuBrLfj78`, `NmAsa944EN4`); every other video `addedIn = 1`.
- Copy (exact). PIN: `Enter parent PIN`, `Choose a parent PIN`, `Enter it again`, `Enter current PIN`, `Choose a new PIN`, `Wrong PIN`, `Those PINs didn't match. Try again.`, `Try again in 1 minute` / `Try again in N minutes` (N rounded up), `Forgot PIN`. Code: `Your recovery code`, `Write this down or take a photo. You need it if you forget the PIN.`, `I've saved it`. Recover: `Enter your recovery code`, `Next`, `That code didn't work.`, `Lost the code too? Clearing Dugout TV's data in the TV's settings removes the PIN, but it also erases every player and all progress.` Key lost: `The parent lock can't be opened on this TV.` followed by the "Lost the code too?" note. Settings: `Add a player`, `Change PIN`, tabs `Progress`, `Profile`, `Videos`, `Remove`. Progress: chip `<Skill> N of M`, states `Finished`, `Watched N%`, `Not started`, row `Reset all <Skill> progress`, dialogs `Reset <title>?` / `<Name> will start it from the beginning.` and `Reset all <Skill> progress?` / `<Name> will start every <Skill> video from the beginning.`, buttons `Cancel`, `Reset`. Profile rows `Name`, `Age range`, `Level`. Videos: chip `<Skill> · N chosen` plus ` · N new` when N > 0, badges `Recommended`, `New`, messages `A player needs at least one video.`, `Couldn't save. Try again.` Remove: `Remove <Name>?`, `This deletes <Name>'s profile, video list, and all progress. It can't be undone.`, buttons `Keep <Name>`, `Remove <Name>`. Dates as `MMM d` in the device locale.
- Every destructive confirmation opens with focus on the safe button.
- Database work and hashing stay off the main thread (hashing on `Dispatchers.Default`).
- Kover floor stays 90% lines with no new exclusions. `KeystorePinKey` is the only class Robolectric cannot run; it stays small and the acceptance suite exercises it.
- Writing style for all prose and copy: no em or en dashes, no spaced or double hyphen as a dash, no sentence starting with "And" or "So".
- No device details in tracked files; never the owner's children's names in code, tests, or docs.

## Review Focus

1. A child presses 5 wrong PINs, force-stops the app, and reopens it: the pad is still locked, for the full wait. Task 6 test `lockSurvivesRestartAtFullLength`.
2. A parent starts Change PIN, sees the new code, then presses Back: the old PIN and old code still work. Task 7 test `backOnCodeScreenKeepsOldPinAndCode`.
3. A held remote button toggles one video many times quickly: what is saved matches what the screen shows once it settles, with no out-of-order write. Task 12 test `rapidTogglesSaveTheFinalList`.
4. A video's progress is reset while its last few seconds are still waiting to be written: the pending write must not bring it back. Task 4 test `resetDropsPendingWrite`.
5. Renaming a child to their own name in different case (`alex` to `Alex`) is accepted, not refused as a duplicate of themselves. Task 11 test `renameToOwnNameInOtherCaseIsAllowed`.

---

### Task 1: Library versions and each child's offered version

**Files:**
- Modify: `app/src/main/kotlin/tv/dugout/library/Video.kt`, `library/VideoLibrary.kt`, `profile/Profile.kt`, `setup/SetupViewModel.kt`
- Test: `app/src/test/kotlin/tv/dugout/library/BundledVideoLibraryTest.kt`, `setup/SetupViewModelTest.kt`

**Interfaces:**
- Produces:
  - `Video` gains `val addedIn: Int = 1`.
  - `val LIBRARY_VERSION: Int` in `VideoLibrary.kt`, the highest `addedIn` in the bundled library (2).
  - `Profile` gains `val offeredVersion: Int = LIBRARY_VERSION`.
  - `fun Video.isNewFor(profile: Profile): Boolean = addedIn > profile.offeredVersion`

- [ ] **Step 1: Write the failing tests:** `BundledVideoLibraryTest.videosAddedIn0005AreVersionTwo` (exactly the 16 IDs in Global Constraints have `addedIn == 2`, all others 1, and `LIBRARY_VERSION == 2`); `SetupViewModelTest.newChildIsOfferedTheCurrentLibrary` (the saved profile's `offeredVersion == LIBRARY_VERSION`).
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests 'tv.dugout.library.*' --tests 'tv.dugout.setup.SetupViewModelTest'`. Expected: compile failure.
- [ ] **Step 3: Implement.** `kidPitch(...)` takes `addedIn: Int = 1`; the 16 entries pass 2. The wizard sets `offeredVersion = LIBRARY_VERSION` explicitly.
- [ ] **Step 4: Run** `./gradlew :app:testDebugUnitTest`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(library): track which library version each child has been offered`.

### Task 2: Database version 3

**Files:**
- Modify: `storage/Entities.kt`, `storage/DugoutDao.kt`, `storage/DugoutDatabase.kt`, `storage/Migrations.kt`, `storage/SavedStore.kt` (map `offeredVersion`)
- Create: `app/schemas/tv.dugout.storage.DugoutDatabase/3.json` (generated)
- Test: `storage/MigrationTest.kt`, `storage/DugoutDaoTest.kt`

**Interfaces:**
- Produces:
  - `ProfileEntity` gains `@ColumnInfo(name = "offered_library_version", defaultValue = "1") val offeredLibraryVersion: Int = 1`.
  - `@Entity(tableName = "parent_lock") data class ParentLockEntity(@PrimaryKey val id: Int = 1, @ColumnInfo(name = "pin_hash") val pinHash: ByteArray, @ColumnInfo(name = "pin_salt") val pinSalt: ByteArray, @ColumnInfo(name = "recovery_hash") val recoveryHash: ByteArray, @ColumnInfo(name = "recovery_salt") val recoverySalt: ByteArray, @ColumnInfo(name = "failed_attempts") val failedAttempts: Int, @ColumnInfo(name = "lock_wait_ms") val lockWaitMs: Long)` (override `equals`/`hashCode` with `contentEquals` for the arrays).
  - `val MIGRATION_2_3: Migration`: `ALTER TABLE profile ADD COLUMN offered_library_version INTEGER NOT NULL DEFAULT 1`, then the `CREATE TABLE parent_lock` statement copied from `3.json`.
  - `DugoutDao`: `@Update suspend fun updateProfile(row: ProfileEntity)`; `@Transaction suspend fun replaceProfileVideos(profileId: String, videos: List<ProfileVideoEntity>)` (delete that child's rows, insert the new ones); `@Transaction suspend fun removeProfile(id: String)` (delete the profile, letting the cascade remove its rows, then renumber remaining positions 0..n-1 in their current order); `@Query("DELETE FROM watch_progress WHERE profile_id = :profileId AND video_id IN (:videoIds)") suspend fun deleteProgress(profileId: String, videoIds: List<String>)`; `@Query("SELECT * FROM parent_lock WHERE id = 1") suspend fun parentLock(): ParentLockEntity?`; `@Upsert suspend fun saveParentLock(row: ParentLockEntity)`.

- [ ] **Step 1: Write the failing tests.** `MigrationTest.upgradeFromTwoKeepsEverythingAndOffersVersionOne`: build a version 2 file from `2.json` (generalize `createVersion1` to take the version) with two children, lists, and progress; open with `dugoutDatabase`; every row survives, both children read `offeredLibraryVersion == 1`, `parentLock()` is null. Keep the existing 1 to 2 test passing through to 3. `DugoutDaoTest`: `replaceProfileVideosSwapsOnlyThatChild`, `removeProfileCascadesAndRenumbers` (three children, remove the middle one: its lists and progress are gone, positions are 0 and 1), `deleteProgressLeavesOtherVideos`, `parentLockRoundTrips`.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests 'tv.dugout.storage.*'`. Expected: FAIL.
- [ ] **Step 3: Implement** the entities, DAO methods, `version = 3`, and `addMigrations(MIGRATION_1_2, MIGRATION_2_3)`. Run `./gradlew :app:kspDebugKotlin` to generate `3.json`, and copy its `parent_lock` `createSql` into the migration.
- [ ] **Step 4: Run** `./gradlew :app:testDebugUnitTest`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(storage): database version 3 for the parent lock and offered videos` (include `3.json`).

### Task 3: Changing and removing children

**Files:**
- Modify: `profile/ProfileRepository.kt`, `profile/ProfileSaver.kt`, `profile/InMemoryProfileRepository.kt`, `library/FixedProfileVideos.kt`, `storage/SavedStore.kt`
- Test: `profile/InMemoryProfileRepositoryTest.kt`, `storage/SavedStoreTest.kt`

**Interfaces:**
- Consumes: Task 2 DAO methods.
- Produces:
  - `ProfileRepository` gains `suspend fun update(profile: Profile)`, `suspend fun setVideos(profileId: String, videoIds: List<String>)`, `suspend fun remove(profileId: String)`. Each writes through first and changes memory only after the write succeeds; a failed write throws and changes nothing.
  - `ProfileSaver` becomes a plain interface with `save` (unchanged), `update(profile: Profile)`, `saveVideos(profileId: String, videoIds: List<String>)`, `remove(profileId: String)`; `NONE` does nothing for all four.
  - `FixedProfileVideos.remove(profileId: String)`.

- [ ] **Step 1: Write the failing tests:** `updateReplacesTheProfileInPlace` (order kept), `setVideosChangesTheList`, `removeDropsProfileAndList`, `failedUpdateChangesNothing` (a saver that throws), and in `SavedStoreTest` `changesSurviveReload` (update, set videos, remove through the graph, then load a new `SavedStore` on the same file and see the changes).
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests 'tv.dugout.profile.*' --tests 'tv.dugout.storage.SavedStoreTest'`. Expected: FAIL.
- [ ] **Step 3: Implement.** `SavedStore`'s saver runs each write in `withContext(scope.coroutineContext)` as `save` does today; `saveVideos` stores positions in list order.
- [ ] **Step 4: Run** `./gradlew :app:testDebugUnitTest`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(profile): update, re-list, and remove children`.

### Task 4: Resetting progress

**Files:**
- Modify: `history/WatchHistory.kt`, `history/InMemoryWatchHistory.kt`, `history/ProgressSink.kt`, `storage/ProgressWriter.kt`, `storage/SavedStore.kt`
- Test: `history/InMemoryWatchHistoryTest.kt`, `storage/ProgressWriterTest.kt`

**Interfaces:**
- Produces:
  - `WatchHistory.reset(profileId: String, videoIds: Set<String>)`: removes those entries from memory at once and calls `ProgressSink.reset`.
  - `ProgressSink.reset(profileId: String, videoIds: Set<String>)` (`NONE` does nothing).
  - `ProgressWriter` gains a constructor parameter `delete: suspend (profileId: String, videoIds: Set<String>) -> Unit = { _, _ -> }`. `reset` runs on `scope`: drops matching `pending` entries, cancels their `scheduled` jobs, then calls `delete` inside `saving.withLock` so a save already under way lands first and is then deleted.

- [ ] **Step 1: Write the failing tests:** `resetRemovesOnlyThoseVideos` (memory); `resetDropsPendingWrite` (record a change, reset before the interval passes, advance time: `save` is never called for it and `delete` is); `resetAfterAnInFlightSaveStillDeletes` (a `save` that suspends; reset during it; `delete` runs after `save` returns).
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests 'tv.dugout.history.*' --tests 'tv.dugout.storage.ProgressWriterTest'`. Expected: FAIL.
- [ ] **Step 3: Implement**, wiring `delete` to `dao.deleteProgress(profileId, videoIds.toList())` in `SavedStore`.
- [ ] **Step 4: Run** `./gradlew :app:testDebugUnitTest`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(history): reset a child's progress for chosen videos`.

### Task 5: PIN hashing and recovery codes

**Files:**
- Create: `parent/PinKey.kt`, `parent/KeystorePinKey.kt`, `parent/PinHasher.kt`, `parent/RecoveryCode.kt`
- Create (tests): `app/src/sharedTest/kotlin/tv/dugout/parent/FixedPinKey.kt`, `app/src/test/kotlin/tv/dugout/parent/PinHasherTest.kt`, `parent/RecoveryCodeTest.kt`, `app/src/androidTest/kotlin/tv/dugout/KeystorePinKeyTest.kt`

**Interfaces:**
- Produces:
  - `interface PinKey { fun exists(): Boolean; fun create(); fun mac(data: ByteArray): ByteArray }`. `mac` throws `IllegalStateException` when the key does not exist.
  - `class KeystorePinKey(alias: String = "dugout_parent_pin") : PinKey`: an `HmacSHA256` key generated with `KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore")` and `KeyGenParameterSpec` purpose `PURPOSE_SIGN`. The `KeyStore` is opened lazily, never in the constructor.
  - `class FixedPinKey(private val key: ByteArray = ByteArray(32) { it.toByte() }, var present: Boolean = true) : PinKey`, plain JVM `Mac` (shared so the acceptance suite can use it too).
  - `class PinHasher(private val key: PinKey, private val iterations: Int = PIN_ITERATIONS, private val random: SecureRandom = SecureRandom())` with `fun newSalt(): ByteArray` (16 bytes), `fun hash(secret: String, salt: ByteArray): ByteArray` (PBKDF2 256-bit output, then `key.mac`), `fun matches(secret: String, salt: ByteArray, expected: ByteArray): Boolean` (`MessageDigest.isEqual`). `const val PIN_ITERATIONS = 100_000` as a starting value; Task 14 measures it on the TV and adjusts toward 100 to 300 ms.
  - `object RecoveryCode { const val ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"; fun generate(random: SecureRandom): String; fun format(code: String): String; fun normalize(input: String): String }`. `generate` returns 8 characters with no dash; `format` inserts the dash after 4.

- [ ] **Step 1: Write the failing tests:** `sameSecretAndSaltMatch`, `differentSaltsDiffer`, `differentKeysDiffer` (two `FixedPinKey`s), `wrongSecretDoesNotMatch`, `hashDoesNotContainThePin` (the hash bytes do not contain `"1234".toByteArray()`); `codesUseOnlyTheAlphabet` (1,000 generated codes), `formatAddsOneDash` (`"ABCD2345"` to `"ABCD-2345"`), `normalizeIgnoresCaseDashAndSpaces` (`" abcd-2345 "` to `"ABCD2345"`). Use `iterations = 1` in unit tests. Acceptance `KeystorePinKeyTest`: `createThenMacIsStable`, `macWithoutKeyThrows` (unique alias per test, deleted after).
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests 'tv.dugout.parent.*'`. Expected: compile failure.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `./gradlew :app:testDebugUnitTest`. Expected: PASS. (The acceptance test runs in Task 14.)
- [ ] **Step 5: Commit** `feat(parent): hash the PIN with a Keystore key and make recovery codes`.

### Task 6: The parent lock

**Files:**
- Create: `parent/ParentLock.kt`, `parent/InMemoryParentLock.kt`, `parent/Lockout.kt`
- Modify: `AppGraph.kt`, `storage/SavedStore.kt`, `DugoutApplication.kt`, `app/src/test/kotlin/tv/dugout/TestGraph.kt`, `SampleDugoutApplication.kt`
- Test: `parent/InMemoryParentLockTest.kt`, `parent/LockoutTest.kt`, `storage/SavedStoreTest.kt`

**Interfaces:**
- Consumes: Task 5 `PinHasher`, `PinKey`, `RecoveryCode`; Task 2 `ParentLockEntity`, DAO lock methods.
- Produces:
  - `enum class LockStatus { NO_PIN, SET, KEY_LOST }` (`KEY_LOST`: a record exists but `PinKey.exists()` is false).
  - `sealed interface Check { data object Correct; data object Wrong; data class Locked(val millis: Long) }`.
  - `data class LockRecord(pinHash, pinSalt, recoveryHash, recoverySalt: ByteArray, failedAttempts: Int, lockWaitMs: Long)` and `fun interface LockSaver { suspend fun save(record: LockRecord) }` with `NONE`.
  - `class PendingPin internal constructor(val recoveryCode: String, internal val record: LockRecord)`; `recoveryCode` is the formatted code.
  - `interface ParentLock { val status: StateFlow<LockStatus>; fun lockedForMillis(): Long; suspend fun checkPin(pin: String): Check; suspend fun checkRecoveryCode(code: String): Check; suspend fun prepare(pin: String): PendingPin; suspend fun commit(pending: PendingPin) }`. `prepare` saves nothing (it calls `PinKey.create()` only when no key exists). `commit` saves the new record with count 0 and wait 0, replacing any old one.
  - `class InMemoryParentLock(initial: LockRecord?, hasher: PinHasher, key: PinKey, saver: LockSaver = LockSaver.NONE, elapsed: () -> Long = SystemClock::elapsedRealtime, random: SecureRandom = SecureRandom()) : ParentLock`. On creation a record with `lockWaitMs > 0` is locked until `elapsed() + lockWaitMs`. Each wrong entry, and each lock expiry, is saved.
  - `fun lockWaitMillis(failedAttempts: Int): Long` in `Lockout.kt` (0 unless a multiple of 5; formula in Global Constraints).
  - `AppGraph` gains `val lock: ParentLock`. `SavedStore` gains a constructor parameter `pinKey: PinKey` and loads `dao.parentLock()`. `DugoutApplication` gains `protected open fun pinKey(): PinKey = KeystorePinKey()`; `SampleDugoutApplication` returns `FixedPinKey()`.
  - Test helpers in `TestGraph.kt`: `fun noPinLock(): ParentLock` and `fun pinLock(pin: String = "1234"): ParentLock` (a committed lock using `FixedPinKey` and `iterations = 1`); `testGraph(lock: ParentLock = noPinLock())`.

- [ ] **Step 1: Write the failing tests.** `LockoutTest.scheduleDoublesToAnHour` (4 to 0, 5 to 60_000, 6 to 0, 10 to 120_000, 15 to 240_000, 35 to 3_600_000, 40 to 3_600_000). `InMemoryParentLockTest`: `noRecordMeansNoPin`; `preparedPinIsNotSavedUntilCommit`; `committedPinChecksCorrectAndWrong`; `recoveryCodeIgnoresCaseAndDash`; `newCodeReplacesOldCode`; `fifthWrongEntryLocks` (returns `Locked(60_000)`, and `checkPin` with the right PIN while locked returns `Locked` too); `wrongCodesShareTheCount`; `correctEntryClearsTheCount`; `lockEndsWhenElapsedPasses` (fake `elapsed`); `lockSurvivesRestartAtFullLength` (lock, advance 50 s, build a new lock from the saved record with `elapsed` reset to 0: `lockedForMillis() == 60_000`); `missingKeyIsKeyLost`. `SavedStoreTest.lockLoadsFromDatabase`.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests 'tv.dugout.parent.*' --tests 'tv.dugout.storage.SavedStoreTest'`. Expected: FAIL.
- [ ] **Step 3: Implement.** Hashing runs in `withContext(Dispatchers.Default)`; saves go through the store scope like the profile saver.
- [ ] **Step 4: Run** `./gradlew :app:testDebugUnitTest`. Expected: PASS (existing tests compile with the new `testGraph` default).
- [ ] **Step 5: Commit** `feat(parent): a parent lock with recovery code and lockout`.

### Task 7: PIN screens

**Files:**
- Create: `parent/pin/PinFlowViewModel.kt`, `parent/pin/PinUiState.kt`, `parent/pin/PinScreen.kt` (pad), `parent/pin/RecoveryCodeScreen.kt` (show), `parent/pin/RecoverScreen.kt` (enter code), `parent/pin/PinFlowScreen.kt` (switches on step)
- Test: `parent/pin/PinFlowViewModelTest.kt`, `parent/pin/PinScreenTest.kt`, `parent/pin/RecoverScreenTest.kt`

**Interfaces:**
- Consumes: Task 6 `ParentLock`, `Check`, `PendingPin`, `LockStatus`.
- Produces:
  - `enum class PinFlow { FIRST_RUN, UNLOCK, CHANGE }`. FIRST_RUN: choose, confirm, code. UNLOCK: enter (or choose, confirm, code when `NO_PIN`), with Forgot leading to recover, choose, confirm, code. CHANGE: enter current, choose, confirm, code.
  - `enum class PinStep { ENTER, CHOOSE, CONFIRM, SHOW_CODE, RECOVER, KEY_LOST }`.
  - `data class PinUiState(step, title: String, digits: Int, message: String?, lockedMinutes: Int?, recoveryCode: String?, codeInput: String, showForgot: Boolean, busy: Boolean, finished: Boolean)`.
  - `class PinFlowViewModel(flow: PinFlow, lock: ParentLock)` with `onDigit(d: Int)`, `onDelete()`, `onOk()`, `onForgot()`, `onCodeChanged(text: String)`, `onCodeSubmitted()`, `onSaved()`, `onBack(): Boolean` (false when Back should leave the flow). The fourth digit checks at once; `onOk` with fewer than four does nothing. `finished` turns true after a correct ENTER in UNLOCK or after `onSaved` commits.
  - Test tags: `pin_key_0` to `pin_key_9`, `pin_key_delete`, `pin_key_ok`, `pin_forgot`, `code_saved`, `recover_field`, `recover_next`.

- [ ] **Step 1: Write the failing ViewModel tests:** `unlockWithRightPinFinishes`; `wrongPinClearsAndSaysWrong`; `lockedShowsMinutesAndIgnoresDigits`; `chooseMismatchStartsAgain`; `firstRunCommitsOnlyOnSaved`; `backOnCodeScreenKeepsOldPinAndCode` (CHANGE: current, new twice, code shown, `onBack`: old PIN still `Correct`, old code still `Correct`, step back to ENTER); `forgotLeadsThroughRecoverToNewPin`; `wrongCodeSaysSo`; `keyLostShowsKeyLostStep`; `noPinUnlockStartsWithChoose`.
- [ ] **Step 2: Write the failing screen tests:** focus starts on `pin_key_5`; Down from the bottom row reaches `pin_forgot` only when `showForgot`; dots count follows `digits`; `code_saved` is focused on the code screen; the recover field takes the TV keyboard as the wizard's name field does.
- [ ] **Step 3: Run** `./gradlew :app:testDebugUnitTest --tests 'tv.dugout.parent.pin.*'`. Expected: FAIL.
- [ ] **Step 4: Implement.** Map remote number keys (`Key.Zero` to `Key.Nine`, `Key.NumPad0` to `Key.NumPad9`) to `onDigit` with `onPreviewKeyEvent` on the pad. Reuse the wizard's `Choice` for pad keys and buttons, and copy the name field's "Select opens the keyboard" pattern for the recover field.
- [ ] **Step 5: Run** `./gradlew :app:testDebugUnitTest`. Expected: PASS.
- [ ] **Step 6: Commit** `feat(parent): PIN entry, recovery code, and recovery screens`.

### Task 8: PIN in the app's flow

**Files:**
- Modify: `DugoutApp.kt`, `picker/ProfilePickerViewModel.kt`, `parent/ParentPlaceholderScreen.kt` (kept as the unlocked destination until Task 9)
- Test: `DugoutAppSetupTest.kt`, `DugoutAppRoutingTest.kt`, `FreshInstallTest.kt`, `picker/ProfilePickerViewModelTest.kt`, new `DugoutAppParentTest.kt`

**Interfaces:**
- Consumes: Task 7 `PinFlowViewModel`, `PinFlowScreen`; Task 6 `AppGraph.lock`.
- Produces:
  - Routes `FIRST_PIN_ROUTE = "first-pin"`, `PARENT_ROUTE = "parent"` (the UNLOCK flow), `PARENT_SETTINGS_ROUTE = "parent/settings"`, `CHANGE_PIN_ROUTE = "parent/change-pin"`.
  - Start destination: `first-pin` when the lock is `NO_PIN` and there are no children; `setup` when there are no children; otherwise `picker`.
  - First-run finish navigates to `setup`, popping `first-pin`. UNLOCK finish navigates to `parent/settings`, popping `parent`. Back leaves the flow when `onBack()` returns false.
  - `ProfilePickerViewModel(repository, profileVideos, history, lock: ParentLock)`: `canAddPlayer = profiles.size < MAX_PLAYERS && lock.status == NO_PIN`, tracking both flows.

- [ ] **Step 1: Write the failing tests:** `freshInstallChoosesPinThenOpensWizard`; `backOnFirstPinLeavesTheApp` (the activity finishes); `pickerHidesAddTileOnceAPinExists`; `parentAsksForPinThenOpensSettings` (with `pinLock()`); `parentWithoutPinChoosesOne`; `backFromSettingsFocusesParentButton`. Update existing fresh-install tests to pass `lock = pinLock()` where they expect the wizard first.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests 'tv.dugout.DugoutApp*' --tests 'tv.dugout.FreshInstallTest' --tests 'tv.dugout.picker.*'`. Expected: FAIL.
- [ ] **Step 3: Implement**, with every navigation guarded by "only while this entry is current", as the existing destinations do.
- [ ] **Step 4: Run** `./gradlew :app:testDebugUnitTest`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(parent): ask for the PIN on first run and before parent settings`.

### Task 9: Parent settings shell

**Files:**
- Create: `parent/settings/ParentSettingsViewModel.kt`, `parent/settings/ParentSettingsUiState.kt`, `parent/settings/ParentSettingsScreen.kt` (two panes, tab row)
- Modify: `DugoutApp.kt` (settings, change PIN, and setup-from-parent destinations); delete `parent/ParentPlaceholderScreen.kt`
- Test: `parent/settings/ParentSettingsViewModelTest.kt`, `parent/settings/ParentSettingsScreenTest.kt`, `DugoutAppParentTest.kt`

**Interfaces:**
- Consumes: Task 3 repository, Task 4 history, Task 1 library version, Task 8 routes.
- Produces:
  - `enum class ParentTab { PROGRESS, PROFILE, VIDEOS, REMOVE }`.
  - `ParentSettingsUiState(profiles: List<Profile>, selectedId: String?, tab: ParentTab, canAddPlayer: Boolean, focus: ParentFocus?)`, where `ParentFocus` names the item focus returns to after a change or a return from another screen (a child, Add a player, Change PIN, or a pane item).
  - `class ParentSettingsViewModel(graph: AppGraph)` with `onChildFocused(id)` (selects, tab back to PROGRESS), `onTabFocused(tab)`, `onReturnedWithAdded(id)`, `onLeave()`. Later tasks add pane handlers to this class.
  - `@Composable fun ParentSettingsScreen(state, callbacks, pane: @Composable (Profile, ParentTab) -> Unit)` lays out the left column (tags `parent_kid_<id>`, `parent_add`, `parent_change_pin`) and the tab row (tags `parent_tab_progress`, `parent_tab_profile`, `parent_tab_videos`, `parent_tab_remove`), with tabs selected on focus as Home's skill tabs are.
  - Setup from parent: route `setup?from=parent`. Back on the name step pops to settings. On the ready screen, Back pops to settings and sets `RESULT_ADDED_PROFILE` on the settings entry; Start watching pops to the picker and opens the new child's Home.

- [ ] **Step 1: Write the failing tests.** Screen: focus starts on the first child; Down moves through children to `parent_add` then `parent_change_pin`; moving to another child shows their name and the Progress tab; Right from a child focuses `parent_tab_progress`; Back from the tab row focuses the selected child; `parent_add` hidden at 6 children. App: `addAPlayerFromSettingsReturnsWithTheChildSelected`; `backOnWizardFirstStepReturnsToSettings`; `changePinReturnsFocusToChangePin`.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests 'tv.dugout.parent.settings.*' --tests 'tv.dugout.DugoutAppParentTest'`. Expected: FAIL.
- [ ] **Step 3: Implement.** The pane slot shows nothing yet; Tasks 10 to 13 fill it.
- [ ] **Step 4: Run** `./gradlew :app:testDebugUnitTest`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(parent): two-pane parent settings with add a player and change PIN`.

### Task 10: Progress tab and reset

**Files:**
- Create: `parent/settings/ProgressPane.kt`, `parent/settings/ConfirmDialog.kt`
- Modify: `parent/settings/ParentSettingsViewModel.kt`, `ParentSettingsUiState.kt`
- Test: `ParentSettingsViewModelTest.kt`, new `parent/settings/ProgressPaneTest.kt`

**Interfaces:**
- Consumes: Task 4 `WatchHistory.reset`; existing `WatchProgress.started`, `fraction`, `watched`.
- Produces:
  - `data class ProgressRow(video: Video, state: ProgressState, lastPlayed: Long?)`, `sealed interface ProgressState { Finished; data class Watched(percent: Int); NotStarted }`.
  - State gains `progressSkill: Skill?`, `progressSkills: Map<Skill, Pair<Int, Int>>` (finished, on list), `progressRows: List<ProgressRow>`, `confirm: Confirm?` (`ResetVideo(videoId)`, `ResetSkill(skill)`, later `RemoveChild`).
  - Handlers `onProgressSkillFocused(skill)`, `onResetVideoRequested(videoId)`, `onResetSkillRequested(skill)`, `onConfirm()`, `onCancelConfirm()`.
  - `@Composable fun ConfirmDialog(title: String, body: String, cancelLabel: String, confirmLabel: String, onCancel, onConfirm)` with tags `dialog_cancel` (focused first) and `dialog_confirm`.
  - Tags `progress_skill_<SKILL>`, `progress_video_<id>`, `progress_reset_skill`.

- [ ] **Step 1: Write the failing tests:** `rowsShowStateAndDate` (finished, 60% watched, not started); `skillChipsCountOnlyListedVideos`; `resetVideoClearsItsProgress`; `resetSkillAlsoClearsUncheckedVideos` (progress on a library video in that skill that is not on the list is cleared too); `notStartedRowDoesNotOfferReset`; screen: `dialogOpensOnCancel`, `backClosesDialogAndRefocusesRow`.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests 'tv.dugout.parent.settings.*'`. Expected: FAIL.
- [ ] **Step 3: Implement.** `Watched N%` uses `(fraction * 100).toInt()`; finished follows `watched`; not started is no row or `!started`.
- [ ] **Step 4: Run** `./gradlew :app:testDebugUnitTest`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(parent): progress tab with reset`.

### Task 11: Profile tab

**Files:**
- Create: `parent/settings/ProfilePane.kt`
- Modify: `setup/ChoiceSteps.kt`, `setup/NameStep.kt` (extract `AgeChoices`, `LevelChoices`, `NameField` that take plain values; the wizard steps call them), `parent/settings/ParentSettingsViewModel.kt`, `ParentSettingsUiState.kt`, `setup/SetupViewModel.kt` (move the name rule into a shared function)
- Test: `ParentSettingsViewModelTest.kt`, new `parent/settings/ProfilePaneTest.kt`; existing setup tests stay green

**Interfaces:**
- Produces:
  - `fun nameError(name: String, others: List<Profile>): NameError?` in `setup/` (trims; others excludes the child being edited). The wizard uses it too.
  - `internal @Composable fun AgeChoices(selected: AgeRange?, onSelected: (AgeRange) -> Unit)`, `LevelChoices(selected: Level?, usual: Level?, onSelected: (Level) -> Unit)`, `NameField(name: String, error: NameError?, onChanged: (String) -> Unit, onSubmitted: () -> Unit)`, keeping their existing test tags.
  - State gains `editing: ProfileField?` (`NAME`, `AGE`, `LEVEL`), `nameDraft: String`, `nameError: NameError?`. Handlers `onEditField(field)`, `onNameDraftChanged`, `onNameSubmitted`, `onAgeChosen`, `onLevelChosen`, `onCancelEdit`.
  - Tags `profile_name`, `profile_age`, `profile_level`.

- [ ] **Step 1: Write the failing tests:** `renameSavesTrimmed`; `renameToAnotherChildsNameIsRefused`; `renameToOwnNameInOtherCaseIsAllowed`; `changingAgeOrLevelKeepsTheVideoList`; `backCancelsWithoutSaving`; screen: rows show current values, Select opens the matching choice with focus on the current value, confirming returns focus to the row.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests 'tv.dugout.parent.settings.*' --tests 'tv.dugout.setup.*'`. Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `./gradlew :app:testDebugUnitTest`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(parent): edit a child's name, age range, and level`.

### Task 12: Videos tab with New marks

**Files:**
- Create: `parent/settings/VideosPane.kt`
- Modify: `parent/settings/ParentSettingsViewModel.kt`, `ParentSettingsUiState.kt`
- Test: `ParentSettingsViewModelTest.kt`, new `parent/settings/VideosPaneTest.kt`

**Interfaces:**
- Consumes: Task 1 `isNewFor`, `LIBRARY_VERSION`; Task 3 `setVideos`, `update`; existing `isRecommendedFor`.
- Produces:
  - `data class EditableVideo(video: Video, checked: Boolean, recommended: Boolean, new: Boolean)`.
  - State gains `videosSkill: Skill`, `videoChips: Map<Skill, Pair<Int, Int>>` (chosen, new), `videos: List<EditableVideo>`, `videosMessage: String?`.
  - Handlers `onVideosSkillFocused(skill)`, `onVideoToggled(videoId)`. Each toggle shows at once; saves run one at a time in press order through a `Mutex`, each saving the full list in library order. A failed save shows `Couldn't save. Try again.` and reloads the list from the repository.
  - Leaving a child's Videos tab (another tab, another child, or `onLeave`) calls `profiles.update(profile.copy(offeredVersion = LIBRARY_VERSION))` when it is lower.
  - Tags `videos_skill_<SKILL>`, `videos_video_<id>`.

- [ ] **Step 1: Write the failing tests:** `allFiveSkillsAreListed`; `newMarksVideosPastTheOfferedVersion` (a child at version 1 sees the 16 0005 videos as New, chips count them); `leavingTheTabClearsNewMarks`; `uncheckKeepsProgressAndRecheckRestoresIt`; `lastVideoCannotBeUnchecked`; `rapidTogglesSaveTheFinalList` (a slow saver; toggle one video five times: the last saved list matches the screen and saves arrive in order); `failedSaveRestoresTheSavedList`.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests 'tv.dugout.parent.settings.*'`. Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `./gradlew :app:testDebugUnitTest`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(parent): edit a child's videos with new videos marked`.

### Task 13: Remove tab

**Files:**
- Create: `parent/settings/RemovePane.kt`
- Modify: `parent/settings/ParentSettingsViewModel.kt`, `DugoutApp.kt` (last child leaves to the wizard)
- Test: `ParentSettingsViewModelTest.kt`, new `parent/settings/RemovePaneTest.kt`, `DugoutAppParentTest.kt`

**Interfaces:**
- Produces:
  - Handlers `onRemoveRequested()` and `onConfirm()` for `Confirm.RemoveChild`: calls `history.reset(id, every library video ID)` then `profiles.remove(id)`, then selects the next child (or the previous one when it was last).
  - State gains `allRemoved: Boolean`; the destination then navigates to `setup`, clearing the back stack, so Back on the wizard's first step leaves the app.
  - Tags `remove_keep` (focused when the tab's content is entered), `remove_confirm`.

- [ ] **Step 1: Write the failing tests:** `removeDeletesChildAndSelectsNext`; `removingLastInListSelectsPrevious`; `removingTheLastChildOpensTheWizard` (app test; the PIN stays: picker later has no "+" tile); `keepIsFocusedFirst`; picker no longer shows the removed child after Back.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests 'tv.dugout.parent.settings.*' --tests 'tv.dugout.DugoutAppParentTest'`. Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `./gradlew :app:testDebugUnitTest`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(parent): remove a child`.

### Task 14: Acceptance, docs, and the TV check

**Files:**
- Create: `app/src/androidTest/kotlin/tv/dugout/ParentSettingsAcceptanceTest.kt`
- Modify: `SetupWizardAcceptanceTest.kt` (choose a PIN first), `ProfilePickerAcceptanceTest.kt` (Parent now shows `Choose a parent PIN`), `AGENTS.md` (What exists: PIN, parent settings; remove "parent settings and the PIN" from Not built yet), `docs/architecture/app-structure.md` (parent package, lock, navigation)

**Interfaces:**
- Consumes: everything above. Acceptance builds its `SavedStore` with `FixedPinKey()` except `KeystorePinKeyTest`.

- [ ] **Step 1: Write the acceptance tests** with real D-pad key events: `chooseAPinThenAddAPlayer` (fresh database: digits on the pad twice, `I've saved it`, wizard); `parentEditsVideosAndResetsProgress` (sample children plus a set PIN: enter it, Right into tabs, Videos, toggle a video, Progress, reset a watched video); `wrongPinSaysSo`; `removeAChild`.
- [ ] **Step 2: Run** `just check`. Expected: PASS, coverage at least 90%.
- [ ] **Step 3: Run** `just acceptance`. Expected: every test passes, including `KeystorePinKeyTest`.
- [ ] **Step 4: Measure** one `PinHasher.hash` with `PIN_ITERATIONS` on the owner's TV (a temporary log line in a debug build, removed before commit) and set `PIN_ITERATIONS` so it takes 100 to 300 ms.
- [ ] **Step 5: Hand check on the owner's TV** (`just install <serial>`): install over the 0005 build; the children and progress survive; choose a PIN and save the code; the 0005 videos show New; add some and see them on the child's Home; wrong PIN five times locks; Forgot PIN with the code works.
- [ ] **Step 6: Commit** `test(acceptance): parent settings and PIN` and `docs: describe parent settings`.
