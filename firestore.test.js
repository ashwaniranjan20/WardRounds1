const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "clinician_alice_01";
const BOB_UID = "clinician_bob_02";

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

function buildPatientPayload(uid, now) {
  return {
    userId: uid,
    uhid: "DEMO-00125",
    name: "Rahul Sharma",
    age: 46,
    sex: "Male",
    ward: "Medicine Ward",
    bedNumber: "12",
    department: "Internal Medicine",
    consultant: "Dr. S. Mukherjee, MD",
    admissionDate: "2026-10-24",
    primaryDiagnosis: "Community Acquired Pneumonia (Right Lower Lobe)",
    allergies: "NKDA",
    discharged: false,
    createdAt: now,
    updatedAt: now,
  };
}

function buildClinicalDocPayload(uid, patientId, now, status = "ACTIVE") {
  return {
    userId: uid,
    patientId,
    docCategory: "DAILY_ROUND",
    title: "Day 2 Morning Ward Round",
    clinicalText: "Afebrile overnight. SpO2 97% on room air. Continue IV antibiotics.",
    authorName: "Dr. Ananya Ray",
    ward: "Medicine Ward",
    docDate: "2026-10-25",
    docStatus: status,
    createdAt: now,
    updatedAt: now,
  };
}

test("Unauthenticated user cannot read or write profiles, patients, or clinical notes", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const past = new Date(Date.now() - 2000);

  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Dr. Ananya Ray",
      role: "DOCTOR",
      department: "Internal Medicine",
      createdAt: past,
      updatedAt: past,
    })
  );
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("patients").get());
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("patients")
      .doc("pt_1")
      .set(buildPatientPayload(ALICE_UID, past))
  );
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("clinical_notes").get());
});

test("Clinician owner can create profile, patient, and clinical docs with Kotlin-mirrored payloads and list subcollections", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const past = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Dr. Ananya Ray",
      role: "DOCTOR",
      department: "Internal Medicine",
      createdAt: past,
      updatedAt: past,
    })
  );

  // Empty subcollection list queries must succeed cleanly
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("patients").get()
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("clinical_notes").get()
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("patients")
      .doc("demo-patient-001")
      .set(buildPatientPayload(ALICE_UID, past))
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("clinical_notes")
      .doc("round-p1-d1")
      .set(buildClinicalDocPayload(ALICE_UID, "demo-patient-001", past))
  );

  // Partial update on active clinical note
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("clinical_notes")
      .doc("round-p1-d1")
      .update({
        clinicalText: "Afebrile 24h. Plan step down to oral Cefpodoxime.",
        docStatus: "FINALIZED",
        updatedAt: new Date(Date.now() - 1000),
      })
  );

  // Terminal state locking: once FINALIZED, subsequent updates must be denied
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("clinical_notes")
      .doc("round-p1-d1")
      .update({
        clinicalText: "Tampered after finalization",
        updatedAt: new Date(Date.now() - 500),
      })
  );
});

test("Cross-user isolation denies reading or writing another clinician's clinical documentation", async () => {
  const past = new Date(Date.now() - 5000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db
      .collection("users")
      .doc(BOB_UID)
      .collection("patients")
      .doc("bob-pt-1")
      .set(buildPatientPayload(BOB_UID, past));
    await db
      .collection("users")
      .doc(BOB_UID)
      .collection("clinical_notes")
      .doc("bob-note-1")
      .set(buildClinicalDocPayload(BOB_UID, "bob-pt-1", past));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("patients").get());
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("clinical_notes").doc("bob-note-1").get()
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc(BOB_UID)
      .collection("clinical_notes")
      .doc("bob-note-1")
      .update({
        title: "Hijacked",
        updatedAt: new Date(Date.now() - 1000),
      })
  );
});

test("Security rules reject shadow ghost fields, @DocumentId id injection, orphaned clinical docs, and future timestamps", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const past = new Date(Date.now() - 5000);

  // Orphaned clinical note (parent patient does not exist yet) must fail
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("clinical_notes")
      .doc("orphan-note")
      .set(buildClinicalDocPayload(ALICE_UID, "missing-pt", past))
  );

  // Ghost field & unexpected @DocumentId `id` key on patient create must fail
  const ghostPatient = buildPatientPayload(ALICE_UID, past);
  ghostPatient.id = "pt-ghost";
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("patients").doc("pt-ghost").set(ghostPatient)
  );

  // Future timestamp manipulation must fail
  const futurePatient = buildPatientPayload(ALICE_UID, new Date(Date.now() + 60000));
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("patients").doc("pt-future").set(futurePatient)
  );

  // Invalid docCategory or oversized string must fail
  await aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("patients")
    .doc("valid-pt")
    .set(buildPatientPayload(ALICE_UID, past));

  const badCat = buildClinicalDocPayload(ALICE_UID, "valid-pt", past);
  badCat.docCategory = "UNKNOWN_TYPE";
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("clinical_notes").doc("bad-cat").set(badCat)
  );
});
