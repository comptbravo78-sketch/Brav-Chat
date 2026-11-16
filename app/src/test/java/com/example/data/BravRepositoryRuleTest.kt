package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.example.model.UserProfile
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BravRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun createAndReadProfile_authenticatedOwner_succeedsAndSyncsPublicDirectory() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = BravRepository(firestore, auth)
    val phone = "+2250701020304"

    val saveResult =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.createOrUpdateUserProfile(
          UserProfile(
            userId = uid,
            displayName = "Alice Kouassi",
            firstName = "Alice",
            lastName = "Kouassi",
            bravId = "@alice_ci",
            phoneNumber = phone,
            countryCode = "+225",
            countryIso = "CI",
            biometricEnabled = true,
          )
        )
      }
    assertTrue(saveResult.isSuccess)

    val fetched = withTimeout(DEFAULT_TIMEOUT_MS) { repo.getUserProfile(uid).getOrThrow() }
    assertNotNull(fetched)
    assertEquals("Alice Kouassi", fetched?.displayName)

    val hash = BravRepository.sha256Hex(BravRepository.normalizePhone(phone))
    val bobUid = signInTestUser(BOB_EMAIL)
    val bobRepo = BravRepository(firestore, auth)
    val lookup = withTimeout(DEFAULT_TIMEOUT_MS) { bobRepo.lookupBravUsersByPhoneHashes(listOf(hash)) }
    assertTrue(lookup.containsKey(hash))
    assertEquals(uid, lookup[hash]?.userId)
    assertTrue(bobUid != uid)
  }

  @Test
  fun conversationAndMessage_participantCanSendAndObserve_nonMemberDenied() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val aliceRepo = BravRepository(firestore, auth)

    val convId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        aliceRepo
          .createOrGetConversation(
            title = "Bob",
            peerUserId = "bob_peer_id",
            peerPhone = "+33612345678",
            peerAvatarUrl = "",
          )
          .getOrThrow()
      }

    val msgResult =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        aliceRepo.sendMessage(
          conversationId = convId,
          memberUids = listOf(aliceUid),
          type = "TEXT",
          text = "Bonjour depuis BRAV CHAT",
        )
      }
    assertTrue(msgResult.isSuccess)

    val messages =
      withTimeout(FLOW_TIMEOUT_MS) {
        aliceRepo.observeMessages(aliceUid, convId).first { it.isNotEmpty() }
      }
    assertEquals("Bonjour depuis BRAV CHAT", messages.first().text)

    // Cross-user unauthorized read check
    signInTestUser(BOB_EMAIL)
    val bobRepo = BravRepository(firestore, auth)
    val crossConvResult = withTimeout(DEFAULT_TIMEOUT_MS) { bobRepo.getConversation(convId) }
    assertTrue(crossConvResult.isFailure)
    val firestoreErr = crossConvResult.exceptionOrNull()?.unwrapFirestoreException()
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreErr?.code)
  }

  @Test
  fun statusesAndLogs_authenticatedOwnerWorks_unauthenticatedRejected() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = BravRepository(firestore, auth)

    val statusId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo
          .publishStatus(
            authorName = "Alice",
            authorAvatarUrl = "",
            type = "TEXT",
            textContent = "Nouvelle refonte BRAV CHAT",
          )
          .getOrThrow()
      }
    val statuses =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeActiveStatuses(uid).first { list -> list.any { it.id == statusId } }
      }
    assertTrue(statuses.any { it.id == statusId })

    val logResult =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.recordCallLog(
          peerName = "Bob",
          peerPhone = "+33612345678",
          peerAvatarUrl = "",
          callType = "AUDIO",
          direction = "OUTGOING",
          durationSeconds = 42L,
          noiseSuppressionUsed = true,
        )
      }
    assertTrue(logResult.isSuccess)

    // Unauthenticated negative test
    auth.signOut()
    try {
      withTimeout(FLOW_TIMEOUT_MS) { repo.observeConversations(uid).first() }
      fail("Expected PERMISSION_DENIED when unauthenticated")
    } catch (e: Exception) {
      val fsErr = e.unwrapFirestoreException()
      assertNotNull("Expected nested FirebaseFirestoreException, got $e", fsErr)
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsErr?.code)
    }
  }

  private companion object {
    const val ALICE_EMAIL = "alice_brav@test.com"
    const val BOB_EMAIL = "bob_brav@test.com"
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 3000L
  }
}
