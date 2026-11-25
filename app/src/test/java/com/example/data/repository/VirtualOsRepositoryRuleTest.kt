package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.CalendarEvent
import com.example.data.model.DevicePreferences
import com.example.data.model.VirtualAlarm
import com.example.data.model.VirtualContact
import com.example.data.model.VirtualFileRecord
import com.example.data.model.VirtualNote
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class VirtualOsRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun saveAndObserveNotes_authenticatedOwner_succeeds() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = VirtualOsRepository(firestore, auth)

    val noteId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.saveNote(
          VirtualNote(
            title = "US Virtual OS Roadmap",
            content = "Test diagnostics & OAuth",
            colorKey = "COBALT",
            isPinned = true,
            isChecklist = false,
          )
        )
      }
    assertTrue(noteId.isNotBlank())

    val notes =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeNotes(uid).first { list -> list.any { it.id == noteId } }
      }
    val created = notes.first { it.id == noteId }
    assertEquals("US Virtual OS Roadmap", created.title)
    assertTrue(created.isPinned)
    assertNotNull(created.createdAt)

    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.saveNote(created.copy(title = "Updated Roadmap", isPinned = false))
    }

    val updated =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeNotes(uid).first { list -> list.any { it.id == noteId && it.title == "Updated Roadmap" } }
      }
    assertEquals(false, updated.first { it.id == noteId }.isPinned)
  }

  @Test
  fun saveContactEventAlarmFilePreferences_authenticatedOwner_succeeds() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = VirtualOsRepository(firestore, auth)

    val contactId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.saveContact(
          VirtualContact(
            name = "Jordan Lee",
            phone = "+1-212-555-0144",
            email = "jordan@example.com",
            company = "Pixel Studio",
            isFavorite = true,
          )
        )
      }
    assertTrue(contactId.isNotBlank())

    val threadId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.createOrGetThread("Jordan Lee", "+1-212-555-0144")
      }
    val msgId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.sendMessage(threadId, "Me", "Checking US timezone sync!", true)
      }
    assertTrue(msgId.isNotBlank())

    val eventId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.saveEvent(
          CalendarEvent(
            title = "Diagnostics Check",
            description = "Verify public IP",
            dateEpochMillis = 1794800000000L,
            timeLabel = "09:00 AM EST",
            location = "New York, NY",
          )
        )
      }
    assertTrue(eventId.isNotBlank())

    val alarmId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.saveAlarm(VirtualAlarm(hour = 8L, minute = 15L, label = "Standup", isEnabled = true, daysMask = 62L))
      }
    assertTrue(alarmId.isNotBlank())

    val fileId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.saveFileRecord(
          VirtualFileRecord(
            name = "system_notes.txt",
            folder = "Documents",
            mimeType = "text/plain",
            sizeBytes = 64L,
            localPath = "internal://system_notes.txt",
            contentPreview = "USA Virtual Android Pro",
          )
        )
      }
    assertTrue(fileId.isNotBlank())

    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.savePreferences(
        DevicePreferences(
          themePalette = "MEADOW",
          darkMode = true,
          usTimezoneId = "America/Los_Angeles",
          oauthClientId = "test-client-id.apps.googleusercontent.com",
        )
      )
    }

    val prefs =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observePreferences(uid).first { it.themePalette == "MEADOW" }
      }
    assertEquals("America/Los_Angeles", prefs.usTimezoneId)
    assertTrue(prefs.darkMode)
  }

  @Test
  fun crossUserAccess_bobCannotReadAliceNotes_failsWithPermissionDenied() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val aliceRepo = VirtualOsRepository(firestore, auth)
    val noteId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        aliceRepo.saveNote(VirtualNote(title = "Alice Secret", content = "Only Alice"))
      }

    signInTestUser(BOB_EMAIL)
    val bobRepo = VirtualOsRepository(firestore, auth)
    try {
      withTimeout(FLOW_TIMEOUT_MS) {
        bobRepo.observeNotes(aliceUid).first()
      }
      fail("Expected PERMISSION_DENIED for cross-user read")
    } catch (e: Exception) {
      val firestoreEx = checkNotNull(e.unwrapFirestoreException()) {
        "Expected nested FirebaseFirestoreException, got $e"
      }
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx.code)
    }

    try {
      withTimeout(DEFAULT_TIMEOUT_MS) {
        firestore.collection("users").document(aliceUid).collection("notes").document(noteId).get().await()
      }
      fail("Expected PERMISSION_DENIED for cross-user doc get")
    } catch (e: FirebaseFirestoreException) {
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
    }
  }

  @Test
  fun unauthenticatedUser_observeNotes_failsWithPermissionDenied() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    auth.signOut()
    val repo = VirtualOsRepository(firestore, auth)

    try {
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeNotes(aliceUid).first()
      }
      fail("Expected PERMISSION_DENIED when signed out")
    } catch (e: Exception) {
      val firestoreEx = checkNotNull(e.unwrapFirestoreException()) {
        "Expected nested FirebaseFirestoreException, got $e"
      }
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx.code)
    }
  }

  private companion object {
    const val ALICE_EMAIL = "alice_os@test.com"
    const val BOB_EMAIL = "bob_os@test.com"
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 3000L
  }
}
