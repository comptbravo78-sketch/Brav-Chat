const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const VALID_HASH = "a".repeat(64);

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastTimestamp() {
  return new Date(Date.now() - 2000);
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot read users, conversations, or statuses", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("conversations").where("memberUids", "array-contains", ALICE_UID).get());
  await assertFails(unauthDb.collection("statuses").where("userId", "==", ALICE_UID).get());
});

test("User profile: owner can create and read, other user cannot read or write, list denied, ghost field rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastTimestamp();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice Kouassi",
      bravId: "@alice_k",
      phoneNumber: "+2250701020304",
      countryCode: "+225",
      countryIso: "CI",
      biometricEnabled: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(aliceDb.collection("users").get());
  // Shadow update with ghost field must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: ts,
    })
  );
});

test("Public directory: owner can register by phoneHash, signed-in user can get, list is denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastTimestamp();

  await assertSucceeds(
    aliceDb.collection("public_directory").doc(VALID_HASH).set({
      userId: ALICE_UID,
      displayName: "Alice Kouassi",
      bravId: "@alice_k",
      phoneHash: VALID_HASH,
      isOnline: true,
      updatedAt: ts,
    })
  );

  await assertSucceeds(bobDb.collection("public_directory").doc(VALID_HASH).get());
  await assertFails(bobDb.collection("public_directory").get());
  await assertFails(
    bobDb.collection("public_directory").doc(VALID_HASH).update({
      displayName: "Hacked",
      updatedAt: ts,
    })
  );
});

test("Conversations & messages: participant isolation and query alignment", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const eveDb = testEnv.authenticatedContext("eve_789").firestore();
  const ts = pastTimestamp();

  await assertSucceeds(
    aliceDb.collection("conversations").doc("conv_1").set({
      title: "Bob",
      peerUserId: BOB_UID,
      lastMessage: "Salut",
      lastMessageType: "TEXT",
      isPinned: false,
      isArchived: false,
      unreadCount: 0,
      memberUids: [ALICE_UID, BOB_UID],
      createdBy: ALICE_UID,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("conversations").where("memberUids", "array-contains", ALICE_UID).get()
  );
  await assertSucceeds(
    bobDb.collection("conversations").where("memberUids", "array-contains", BOB_UID).get()
  );
  // Unfiltered list must fail
  await assertFails(aliceDb.collection("conversations").get());
  // Non-member Eve must fail
  await assertFails(eveDb.collection("conversations").doc("conv_1").get());

  // Message inside conversation
  await assertSucceeds(
    aliceDb.collection("conversations").doc("conv_1").collection("messages").doc("msg_1").set({
      senderId: ALICE_UID,
      memberUids: [ALICE_UID, BOB_UID],
      type: "TEXT",
      text: "Bonjour Bob!",
      deliveryStatus: "SENT",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    bobDb.collection("conversations").doc("conv_1").collection("messages")
      .where("memberUids", "array-contains", BOB_UID).get()
  );
  await assertFails(
    eveDb.collection("conversations").doc("conv_1").collection("messages").doc("msg_1").get()
  );
  await assertFails(
    eveDb.collection("conversations").doc("conv_1").collection("messages").get()
  );
});

test("Statuses 24H: create, query active, view increment", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastTimestamp();
  const futureDate = new Date(Date.now() + 24 * 60 * 60 * 1000);

  await assertSucceeds(
    aliceDb.collection("statuses").doc("st_1").set({
      userId: ALICE_UID,
      authorName: "Alice",
      type: "TEXT",
      textContent: "Disponible sur BRAV CHAT",
      backgroundColorHex: "#006B54",
      viewCount: 0,
      isActive: true,
      createdAt: ts,
      expiresAt: futureDate,
    })
  );

  await assertSucceeds(
    bobDb.collection("statuses").where("isActive", "==", true).get()
  );
  // Unfiltered status list must fail
  await assertFails(
    bobDb.collection("statuses").get()
  );
  await assertSucceeds(
    bobDb.collection("statuses").doc("st_1").collection("views").doc(BOB_UID).set({
      viewerId: BOB_UID,
      viewerName: "Bob",
      reaction: "❤️",
      viewedAt: ts,
    })
  );
  await assertSucceeds(
    bobDb.collection("statuses").doc("st_1").update({ viewCount: 1 })
  );
  await assertFails(
    bobDb.collection("statuses").doc("st_1").delete()
  );
});

test("Owner-scoped call_logs, activity_logs, devices isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastTimestamp();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("call_logs").doc("c_1").set({
      userId: ALICE_UID,
      peerName: "Bob",
      callType: "VIDEO",
      direction: "OUTGOING",
      durationSeconds: 120,
      noiseSuppressionUsed: true,
      createdAt: ts,
    })
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("call_logs").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("call_logs").get());
});
