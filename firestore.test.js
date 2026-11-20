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
    kdfAlgorithm: "PBKDF2WithHmacSHA256",
    pinVerifierBase64: "cGluX3ZlcmlmaWVyX2Jhc2U2NA==",
    schemaVersion: 2,
    createdAt: past,
    updatedAt: past,
  };
}

function validVaultConfigV3(userId) {
  return {
    ...validVaultConfig(userId),
    schemaVersion: 3,
    deviceBinding: "ANDROID_KEYSTORE",
    // Outer device-sealed blob (placeholder, must be 64..1024 chars).
    wrappedDekBase64: "ZGV2aWNlX3NlYWxlZF9pbm5lcl9wYXlsb2FkX3BsYWNlaG9sZGVyX2Jhc2U2NF9kYXRhX3BhZGRlZF90b19zaXp0eV9jaGFyc19sb25n",
    pinVerifierBase64: "device-bound:v3",
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
    cryptoVersion: 2,
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

test("Vault config v2: downgraded KDF parameters and legacy schema writes are rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const cfgRef = aliceDb.collection("users").doc(ALICE_UID).collection("vault").doc("config");

  await assertSucceeds(cfgRef.set(validVaultConfig(ALICE_UID)));

  // Iteration floor: a session attacker cannot weaken the KDF.
  await assertFails(
    cfgRef.update({
      kdfIterations: 10000,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Algorithm downgrade rejected.
  await assertFails(
    cfgRef.update({
      kdfAlgorithm: "PBKDF2WithHmacSHA1",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // v1 documents can no longer be created.
  const legacyCfg = validVaultConfig(ALICE_UID);
  delete legacyCfg.kdfAlgorithm;
  legacyCfg.schemaVersion = 1;
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("vault").doc("legacy_cfg").set(legacyCfg)
  );
});

test("Vault config migration: a seeded v1 document can only be rewritten as full v2", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const cfgRef = aliceDb.collection("users").doc(ALICE_UID).collection("vault").doc("config");

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const past = new Date(Date.now() - 2000);
    await ctx.firestore().collection("users").doc(ALICE_UID).collection("vault").doc("config").set({
      userId: ALICE_UID,
      wrappedDekBase64: "d3JhcHBlZF9kZWtfYmFzZTY0X3Rlc3RfcGF5bG9hZA==",
      wrappedDekIvBase64: "MTIzNDU2Nzg5MDEy",
      kdfSaltBase64: "c2FsdF9iYXNlNjRfdGVzdA==",
      kdfIterations: 120000,
      pinVerifierBase64: "cGluX3ZlcmlmaWVyX2Jhc2U2NA==",
      schemaVersion: 1,
      createdAt: past,
      updatedAt: past,
    });
  });

  const upgraded = validVaultConfig(ALICE_UID);
  upgraded.createdAt = (await cfgRef.get()).get("createdAt").toDate();
  await assertSucceeds(cfgRef.set(upgraded));
});

test("Vault config v3: device-bound shape is enforced and v2 migrates to v3", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const cfgRef = aliceDb.collection("users").doc(ALICE_UID).collection("vault").doc("config");

  // v2 -> v3 full rewrite (client migration after a successful PIN unlock); createdAt is immutable.
  await assertSucceeds(cfgRef.set(validVaultConfig(ALICE_UID)));
  const createdAt = (await cfgRef.get()).get("createdAt").toDate();
  await assertSucceeds(cfgRef.set({ ...validVaultConfigV3(ALICE_UID), createdAt }));

  // A v3 vault may not expose a PIN-derived verifier in cleartext.
  await assertFails(
    cfgRef.update({
      pinVerifierBase64: "cGluX3ZlcmlmaWVyX2Jhc2U2NA==",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Unknown device binding values are rejected.
  await assertFails(
    cfgRef.update({
      deviceBinding: "SOFTWARE_FALLBACK",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // v2 documents may not smuggle a deviceBinding field.
  await assertFails(
    cfgRef.set({ ...validVaultConfig(ALICE_UID), deviceBinding: "ANDROID_KEYSTORE", createdAt })
  );

  // schemaVersion may only move forward: v3 -> v2 downgrade is rejected.
  await assertFails(cfgRef.set({ ...validVaultConfig(ALICE_UID), createdAt }));
});

test("Access cards v1: seeded legacy docs are no longer writable (eviction)", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const cardRef = aliceDb.collection("users").doc(ALICE_UID).collection("cards").doc("legacy_card");

  const legacyCard = validCard(ALICE_UID);
  delete legacyCard.cryptoVersion;
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(ALICE_UID).collection("cards").doc("legacy_card").set(legacyCard);
  });

  // Even a harmless HCE toggle must fail: the doc lacks the required v2 declaration.
  await assertFails(
    cardRef.update({ activeHce: true, updatedAt: new Date(Date.now() - 500) })
  );

  // Rewriting it as a full v2 document remains possible (migration path); createdAt is immutable.
  let cardCreatedAt;
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const snap = await ctx.firestore()
      .collection("users").doc(ALICE_UID).collection("cards").doc("legacy_card").get();
    cardCreatedAt = snap.get("createdAt").toDate();
  });
  await assertSucceeds(cardRef.set({ ...validCard(ALICE_UID), createdAt: cardCreatedAt }));
});

test("Access cards v2: creation requires cryptoVersion 2 and version never moves backwards", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const cardsCol = aliceDb.collection("users").doc(ALICE_UID).collection("cards");

  const legacyCard = validCard(ALICE_UID);
  delete legacyCard.cryptoVersion;
  await assertFails(cardsCol.doc("legacy_create").set(legacyCard));
  await assertFails(
    cardsCol.doc("legacy_create_1").set({ ...validCard(ALICE_UID), cryptoVersion: 1 })
  );

  await assertSucceeds(cardsCol.doc("card_v2").set(validCard(ALICE_UID)));

  // Downgrade v2 -> v1 rejected.
  await assertFails(
    cardsCol.doc("card_v2").update({
      cryptoVersion: 1,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Access cards: emulation counter only accepts an atomic +1 increment", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const cardRef = aliceDb.collection("users").doc(ALICE_UID).collection("cards").doc("counter_card");
  await assertSucceeds(cardRef.set(validCard(ALICE_UID)));

  await assertSucceeds(
    cardRef.update({
      emulationCount: 1,
      lastEmulatedAt: new Date(Date.now() - 900),
      updatedAt: new Date(Date.now() - 900),
    })
  );

  // +2 rejected: lost-update / cross-device race must fail loudly.
  await assertFails(
    cardRef.update({
      emulationCount: 3,
      lastEmulatedAt: new Date(Date.now() - 800),
      updatedAt: new Date(Date.now() - 800),
    })
  );

  await assertSucceeds(
    cardRef.update({
      emulationCount: 2,
      lastEmulatedAt: new Date(Date.now() - 700),
      updatedAt: new Date(Date.now() - 700),
    })
  );
});

test("Access cards: createCardAndActivate transit card atomically deactivates previously active and null-lastEmulatedAt cards", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const cardsCol = aliceDb.collection("users").doc(ALICE_UID).collection("cards");
  const past = new Date(Date.now() - 5000);

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const col = ctx.firestore().collection("users").doc(ALICE_UID).collection("cards");
    // v1 cards are evicted (see the eviction test); only v2 active cards are switchable.
    const previousActive = validCard(ALICE_UID);
    previousActive.activeHce = true;
    previousActive.createdAt = past;
    previousActive.updatedAt = past;
    await col.doc("previous_active").set(previousActive);

    await col.doc("legacy_null_emul").set({
      ...validCard(ALICE_UID),
      activeHce: true,
      lastEmulatedAt: null,
      createdAt: past,
      updatedAt: past,
    });
  });

  const batch = aliceDb.batch();
  batch.set(cardsCol.doc("transit_changantong"), {
    ...validCard(ALICE_UID),
    label: "长安通",
    category: "TRANSIT",
    accentColor: "ROSE",
    primaryTech: "ISO_DEP",
    maskedUid: "•••• D7BD",
    hceSupported: true,
    activeHce: true,
    cryptoVersion: 2,
  });
  batch.update(cardsCol.doc("previous_active"), {
    activeHce: false,
    updatedAt: new Date(Date.now() - 500),
  });
  batch.update(cardsCol.doc("legacy_null_emul"), {
    activeHce: false,
    updatedAt: new Date(Date.now() - 500),
  });

  await assertSucceeds(batch.commit());
});

