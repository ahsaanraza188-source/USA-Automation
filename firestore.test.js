const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const now = () => new Date(Date.now() - 1000);

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

test("Unauthenticated user: cannot list or write notes", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/notes`).get());
  await assertFails(
    unauthDb.collection(`users/${ALICE_UID}/notes`).doc("n1").set({
      userId: ALICE_UID,
      title: "Hack",
      content: "Bad",
      colorKey: "COBALT",
      isPinned: false,
      isChecklist: false,
      createdAt: now(),
      updatedAt: now(),
    })
  );
});

test("Cross-user isolation: Bob cannot read or write Alice's notes, contacts, or preferences", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const adminDb = context.firestore();
    await adminDb.doc(`users/${ALICE_UID}/notes/n1`).set({
      userId: ALICE_UID,
      title: "Secret Note",
      content: "Private",
      colorKey: "COBALT",
      isPinned: true,
      isChecklist: false,
      createdAt: new Date(),
      updatedAt: new Date(),
    });
    await adminDb.doc(`users/${ALICE_UID}/contacts/c1`).set({
      userId: ALICE_UID,
      name: "Jane Doe",
      phone: "555-0100",
      email: "jane@example.com",
      company: "Acme",
      isFavorite: true,
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.doc(`users/${ALICE_UID}/notes/n1`).get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/notes`).get());
  await assertFails(bobDb.doc(`users/${ALICE_UID}/contacts/c1`).get());
  await assertFails(
    bobDb.doc(`users/${ALICE_UID}/preferences/device_settings`).set({
      userId: BOB_UID,
      themePalette: "COBALT",
      darkMode: false,
      countryCode: "US",
      countryName: "United States",
      stateRegion: "New York",
      localeTag: "en-US",
      languageName: "English (United States)",
      usTimezoneId: "America/New_York",
      dateFormat: "MM/DD/YYYY",
      use24HourTime: false,
      currencyCode: "USD",
      numberFormat: "1,234.56",
      temperatureUnit: "FAHRENHEIT",
      measurementSystem: "US_CUSTOMARY",
      oauthClientId: "",
      pinnedAppIds: ["chrome"],
      createdAt: now(),
      updatedAt: now(),
    })
  );
});

test("Owner: can create, update, list, and delete valid Note", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const noteRef = aliceDb.doc(`users/${ALICE_UID}/notes/note_1`);

  await assertSucceeds(
    noteRef.set({
      userId: ALICE_UID,
      title: "US Trip Checklist",
      content: "Passport\nCharger",
      colorKey: "SAGE",
      isPinned: false,
      isChecklist: true,
      createdAt: now(),
      updatedAt: now(),
    })
  );

  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/notes`).get());

  await assertSucceeds(
    noteRef.update({
      isPinned: true,
      updatedAt: now(),
    })
  );

  await assertSucceeds(noteRef.delete());
});

test("Shadow update & schema validation: rejects ghost fields and invalid colorKey", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/notes/bad_ghost`).set({
      userId: ALICE_UID,
      title: "Note",
      content: "Body",
      colorKey: "COBALT",
      isPinned: false,
      isChecklist: false,
      isAdmin: true,
      createdAt: now(),
      updatedAt: now(),
    })
  );

  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/notes/bad_color`).set({
      userId: ALICE_UID,
      title: "Note",
      content: "Body",
      colorKey: "NEON_PINK",
      isPinned: false,
      isChecklist: false,
      createdAt: now(),
      updatedAt: now(),
    })
  );
});

test("Messages subcollection requires existing parent thread and enforces ownership", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const threadRef = aliceDb.doc(`users/${ALICE_UID}/threads/t_1`);
  const msgRef = aliceDb.doc(`users/${ALICE_UID}/threads/t_1/messages/m_1`);

  // Orphan message without parent thread fails
  await assertFails(
    msgRef.set({
      userId: ALICE_UID,
      threadId: "t_1",
      sender: "Me",
      body: "Orphan",
      isOutgoing: true,
      createdAt: now(),
      updatedAt: now(),
    })
  );

  await assertSucceeds(
    threadRef.set({
      userId: ALICE_UID,
      contactName: "Alex Mercer",
      contactPhone: "+1-415-555-0192",
      lastMessage: "Hello!",
      unreadCount: 0,
      createdAt: now(),
      updatedAt: now(),
    })
  );

  await assertSucceeds(
    msgRef.set({
      userId: ALICE_UID,
      threadId: "t_1",
      sender: "Me",
      body: "Hello!",
      isOutgoing: true,
      createdAt: now(),
      updatedAt: now(),
    })
  );
});

test("Owner: can create and update valid CalendarEvent, VirtualAlarm, VirtualFileRecord, and DevicePreferences", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/events/ev_1`).set({
      userId: ALICE_UID,
      title: "Product Sync",
      description: "Weekly US review",
      dateEpochMillis: 1794800000000,
      timeLabel: "10:00 AM EST",
      location: "New York",
      createdAt: now(),
      updatedAt: now(),
    })
  );

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/alarms/al_1`).set({
      userId: ALICE_UID,
      hour: 7,
      minute: 30,
      label: "Morning Briefing",
      isEnabled: true,
      daysMask: 62,
      createdAt: now(),
      updatedAt: now(),
    })
  );

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/files/f_1`).set({
      userId: ALICE_UID,
      name: "readme.txt",
      folder: "Documents",
      mimeType: "text/plain",
      sizeBytes: 128,
      localPath: "/data/readme.txt",
      contentPreview: "Welcome to USA Virtual Android Pro",
      createdAt: now(),
      updatedAt: now(),
    })
  );

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/preferences/device_settings`).set({
      userId: ALICE_UID,
      themePalette: "COBALT",
      darkMode: false,
      countryCode: "US",
      countryName: "United States",
      stateRegion: "New York",
      localeTag: "en-US",
      languageName: "English (United States)",
      usTimezoneId: "America/New_York",
      dateFormat: "MM/DD/YYYY",
      use24HourTime: false,
      currencyCode: "USD",
      numberFormat: "1,234.56",
      temperatureUnit: "FAHRENHEIT",
      measurementSystem: "US_CUSTOMARY",
      oauthClientId: "12345.apps.googleusercontent.com",
      pinnedAppIds: ["chrome", "gmail", "youtube", "diagnostics", "settings"],
      createdAt: now(),
      updatedAt: now(),
    })
  );

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/preferences/device_settings`).update({
      stateRegion: "California",
      usTimezoneId: "America/Los_Angeles",
      use24HourTime: true,
      updatedAt: now(),
    })
  );
});

test("BrowserVault: owner can create/update/read while unauthenticated and cross-user access fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const vaultRef = aliceDb.doc(`users/${ALICE_UID}/browser_vault/default`);

  await assertSucceeds(
    vaultRef.set({
      userId: ALICE_UID,
      tabsJson: '[{"id":1,"title":"Gmail","url":"https://mail.google.com/"}]',
      activeTabIndex: 0,
      bookmarksJson: '[{"title":"Gmail","url":"https://mail.google.com/"}]',
      cookiesJson: '{"https://mail.google.com/":"SID=abc"}',
      webStorageJson: '{"https://example.com":{"token":"xyz"}}',
      createdAt: now(),
      updatedAt: now(),
    })
  );

  await assertSucceeds(
    vaultRef.update({
      activeTabIndex: 1,
      cookiesJson: '{"https://mail.google.com/":"SID=def"}',
      updatedAt: now(),
    })
  );

  await assertFails(bobDb.doc(`users/${ALICE_UID}/browser_vault/default`).get());
  await assertFails(unauthDb.doc(`users/${ALICE_UID}/browser_vault/default`).get());
  await assertFails(
    bobDb.doc(`users/${ALICE_UID}/browser_vault/default`).set({
      userId: BOB_UID,
      tabsJson: "[]",
      activeTabIndex: 0,
      bookmarksJson: "[]",
      cookiesJson: "{}",
      webStorageJson: "{}",
      createdAt: now(),
      updatedAt: now(),
    })
  );
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/browser_vault/bad`).set({
      userId: ALICE_UID,
      tabsJson: "[]",
      activeTabIndex: -1,
      bookmarksJson: "[]",
      cookiesJson: "{}",
      webStorageJson: "{}",
      createdAt: now(),
      updatedAt: now(),
    })
  );
});
