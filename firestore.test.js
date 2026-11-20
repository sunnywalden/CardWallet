const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_001";
const BOB_UID = "bob_uid_002";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validVaultConfig(userId) {
  const past = new Date(Date.now() - 2000);
  return {
    userId,
    wrappedDekBase64: "d3JhcHBlZF9kZWtfYmFzZTY0X3Rlc3RfcGF5bG9hZA==",
    wrappedDekIvBase64: "MTIzNDU2Nzg5MDEy",
    kdfSaltBase64: "c2FsdF9iYXNlNjRfdGVzdA==",
    kdfIterations: 120000,
    pinVerifierBase64: "cGluX3ZlcmlmaWVyX2Jhc2U2NA==",
    schemaVersion: 1,
    createdAt: past,
    updatedAt: past,
  };
}

function validCard(userId) {
  const past = new Date(Date.now() - 2000);
  return {
    userId,
    label: "公司大门门禁",
    category: "OFFICE",
    accentColor: "CYAN",
    primaryTech: "ISO_DEP",
    maskedUid: "•••• 8F2A",
    hceSupported: true,
    activeHce: false,
    encryptedPayloadBase64: "ZW5jcnlwdGVkX2NhcmRfcGF5bG9hZF9hZXNfZ2Nt",
    payloadIvBase64: "MTIzNDU2Nzg5MDEy",
    emulationCount: 0,
    createdAt: past,
    updatedAt: past,
  };
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

test("Unauthenticated user: cannot read or write vault config or cards", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("vault").doc("config").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("cards").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("cards").doc("card_1").set(validCard(ALICE_UID))
  );
});

test("Vault config: owner can create and update wrapped DEK; non-owner and ghost fields rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const cfgRef = aliceDb.collection("users").doc(ALICE_UID).collection("vault").doc("config");

  await assertSucceeds(cfgRef.set(validVaultConfig(ALICE_UID)));
  await assertSucceeds(cfgRef.get());

  // Partial update when rotating PIN (createdAt preserved)
  await assertSucceeds(
    cfgRef.update({
      wrappedDekBase64: "bmV3X3dyYXBwZWRfZGVrX2Jhc2U2NA==",
      wrappedDekIvBase64: "OTg3NjU0MzIxMDEy",
      kdfSaltBase64: "bmV3X3NhbHRfYmFzZTY0",
      pinVerifierBase64: "bmV3X3ZlcmlmaWVyX2Jhc2U2NA==",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject ghost field on create
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("vault").doc("other_cfg").set({
      ...validVaultConfig(ALICE_UID),
    })
  );

  // Reject immutable createdAt modification
  await assertFails(
    cfgRef.update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject Bob reading or writing Alice's vault config
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("vault").doc("config").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("vault").doc("config").set(validVaultConfig(BOB_UID))
  );
});

test("Access cards: owner create, list, action-based partial updates, and delete succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const cardsCol = aliceDb.collection("users").doc(ALICE_UID).collection("cards");
  const cardRef = cardsCol.doc("card_office_01");

  // Empty list succeeds cleanly
  await assertSucceeds(cardsCol.get());

  // Create without optional lastEmulatedAt or @DocumentId `id`
  await assertSucceeds(cardRef.set(validCard(ALICE_UID)));

  // List non-empty collection
  await assertSucceeds(cardsCol.get());

  // Action 1: toggle activeHce
  await assertSucceeds(
    cardRef.update({
      activeHce: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Action 2: record HCE emulation tap
  await assertSucceeds(
    cardRef.update({
      emulationCount: 1,
      lastEmulatedAt: new Date(Date.now() - 900),
      updatedAt: new Date(Date.now() - 900),
    })
  );

  // Action 3: edit label and re-encrypted payload
  await assertSucceeds(
    cardRef.update({
      label: "研发中心 A 座",
      category: "OFFICE",
      accentColor: "EMERALD",
      encryptedPayloadBase64: "dXBkYXRlZF9jaXBoZXJ0ZXh0X2Flc19nY20=",
      payloadIvBase64: "MTExMjIyMzMzNDQ0",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Delete card
  await assertSucceeds(cardRef.delete());
});

test("Access cards: rejects cross-user access, @DocumentId id injection, invalid enums, future timestamps, and oversized payloads", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(ALICE_UID).collection("cards").doc("alice_c1").set(validCard(ALICE_UID));
  });

  // Bob cannot get, list, update, or delete Alice's cards
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("cards").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("cards").doc("alice_c1").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("cards").doc("alice_c1").update({
      label: "Hijacked",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("cards").doc("alice_c1").delete());

  // Reject storing `id` field
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("cards").doc("card_with_id").set({
      id: "card_with_id",
      ...validCard(ALICE_UID),
    })
  );

  // Reject invalid enum
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("cards").doc("bad_enum").set({
      ...validCard(ALICE_UID),
      category: "INVALID_CAT",
    })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("cards").doc("future_ts").set({
      ...validCard(ALICE_UID),
      createdAt: new Date(Date.now() + 60000),
    })
  );

  // Reject oversized label (> 40 chars)
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("cards").doc("long_label").set({
      ...validCard(ALICE_UID),
      label: "A".repeat(41),
    })
  );
});
