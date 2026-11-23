const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "admin_neerab_uid";
const ADMIN_EMAIL = "neerab.sarkar00@gmail.com";
const ALICE_UID = "student_alice_uid";
const BOB_UID = "student_bob_uid";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function nowPast() {
  return new Date(Date.now() - 2000);
}

function validUserPayload(uid, name, email, role = "STUDENT", status = "ACTIVE") {
  const ts = nowPast();
  return {
    userId: uid,
    name,
    email,
    role,
    status,
    tradingCapital: 100000,
    dailyGoal: 2500,
    dailyMaxLoss: 1500,
    maxTradesPerDay: 3,
    createdAt: ts,
    updatedAt: ts,
  };
}

function validTradePayload(uid, userName, overrides = {}) {
  const ts = nowPast();
  return {
    userId: uid,
    tradeCode: "TBB-20261123-001",
    userName,
    tradeDate: "2026-11-23",
    tradeTimestampMillis: Date.now() - 5000,
    instrument: "NIFTY",
    direction: "BUY",
    timeframe: "M15",
    entryPrice: 24150.5,
    exitPrice: 24230.0,
    stopLoss: 24110.0,
    takeProfit: 24250.0,
    lotSize: 50,
    riskAmount: 2025.0,
    profitLoss: 3975.0,
    result: "WIN",
    riskRewardRatio: 1.96,
    stopLossUsed: true,
    strategy: "Price Action",
    entryReason: "Support bounce on 15m VWAP",
    exitReason: "Near resistance target",
    mistakes: "",
    lessonsLearned: "Waited for candle close",
    notes: "Clean execution",
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
  };
}

function validDisciplinePayload(uid, userName) {
  const ts = nowPast();
  return {
    userId: uid,
    userName,
    entryType: "DAILY_PSYCHOLOGY",
    periodLabel: "2026-11-23",
    moodTag: "Calm",
    followedRules: true,
    respectedStopLoss: true,
    overtraded: false,
    psychologyNotes: "Stayed patient before London/NY open",
    mistakesSummary: "None",
    improvementsPlan: "Keep max 3 trades/day",
    disciplineScore: 9,
    createdAt: ts,
    updatedAt: ts,
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

test("Unauthenticated user: denied all reads and writes", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("trades").where("userId", "==", ALICE_UID).get());
  await assertFails(unauthDb.collection("discipline_entries").where("userId", "==", ALICE_UID).get());
  await assertFails(unauthDb.collection("trades").doc("trade_1").set(validTradePayload(ALICE_UID, "Alice")));
});

test("Role escalation & Email spoofing guards: unverified admin email or self-assigned ADMIN role fails", async () => {
  const spoofedAdminDb = testEnv.authenticatedContext("spoof_uid", {
    email: ADMIN_EMAIL,
    email_verified: false,
  }).firestore();

  await assertFails(spoofedAdminDb.collection("users").get());
  await assertFails(
    spoofedAdminDb.collection("users").doc("spoof_uid").set(
      validUserPayload("spoof_uid", "Spoofer", ADMIN_EMAIL, "ADMIN", "ACTIVE")
    )
  );

  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "alice@example.com",
    email_verified: true,
  }).firestore();

  // Student cannot self-assign ADMIN role or SUSPENDED status
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set(
      validUserPayload(ALICE_UID, "Alice", "alice@example.com", "ADMIN", "ACTIVE")
    )
  );
  // Student cannot list all users
  await assertFails(aliceDb.collection("users").get());
  // Valid student self-registration succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(
      validUserPayload(ALICE_UID, "Alice", "alice@example.com", "STUDENT", "ACTIVE")
    )
  );
  // Student cannot escalate their own role via update
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      role: "ADMIN",
      updatedAt: nowPast(),
    })
  );
});

test("Student trade & discipline CRUD + Shadow Update + Cross-Student Isolation", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID, "Alice", "alice@example.com"));
    await db.collection("users").doc(BOB_UID).set(validUserPayload(BOB_UID, "Bob", "bob@example.com"));
    await db.collection("trades").doc("bob_trade_1").set(validTradePayload(BOB_UID, "Bob"));
    await db.collection("discipline_entries").doc("bob_disc_1").set(validDisciplinePayload(BOB_UID, "Bob"));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "alice@example.com",
    email_verified: true,
  }).firestore();

  // Alice creates her own trade (no `id` field, optional screenshot omitted)
  await assertSucceeds(
    aliceDb.collection("trades").doc("alice_trade_1").set(validTradePayload(ALICE_UID, "Alice"))
  );

  // Shadow update / ghost field rejected
  await assertFails(
    aliceDb.collection("trades").doc("alice_trade_2").set({
      ...validTradePayload(ALICE_UID, "Alice"),
      ghostField: true,
    })
  );

  // @DocumentId `id` field inside body rejected
  await assertFails(
    aliceDb.collection("trades").doc("alice_trade_3").set({
      ...validTradePayload(ALICE_UID, "Alice"),
      id: "alice_trade_3",
    })
  );

  // Partial update on Alice's trade succeeds; immutable createdAt/tradeCode/userId protected
  await assertSucceeds(
    aliceDb.collection("trades").doc("alice_trade_1").update({
      profitLoss: 4200.0,
      notes: "Updated exit note",
      updatedAt: nowPast(),
    })
  );
  await assertFails(
    aliceDb.collection("trades").doc("alice_trade_1").update({
      tradeCode: "TBB-HACKED",
      updatedAt: nowPast(),
    })
  );

  // Filtered query succeeds; unfiltered query fails for student
  await assertSucceeds(aliceDb.collection("trades").where("userId", "==", ALICE_UID).get());
  await assertFails(aliceDb.collection("trades").get());

  // Cross-student isolation: Alice cannot read or modify Bob's trades or discipline entries
  await assertFails(aliceDb.collection("trades").doc("bob_trade_1").get());
  await assertFails(aliceDb.collection("trades").where("userId", "==", BOB_UID).get());
  await assertFails(aliceDb.collection("trades").doc("bob_trade_1").update({ notes: "Hijack", updatedAt: nowPast() }));
  await assertFails(aliceDb.collection("trades").doc("bob_trade_1").delete());
  await assertFails(aliceDb.collection("discipline_entries").doc("bob_disc_1").get());

  // Alice creates and queries her own discipline entry
  await assertSucceeds(
    aliceDb.collection("discipline_entries").doc("alice_disc_1").set(validDisciplinePayload(ALICE_UID, "Alice"))
  );
  await assertSucceeds(aliceDb.collection("discipline_entries").where("userId", "==", ALICE_UID).get());
});

test("Suspended student cannot create or update trades or discipline entries", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(BOB_UID).set(
      validUserPayload(BOB_UID, "Bob", "bob@example.com", "STUDENT", "SUSPENDED")
    );
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID, {
    email: "bob@example.com",
    email_verified: true,
  }).firestore();

  // Suspended user can read their own profile to see suspension status
  await assertSucceeds(bobDb.collection("users").doc(BOB_UID).get());
  // Suspended user is blocked from creating trades or discipline logs
  await assertFails(bobDb.collection("trades").doc("bob_trade_x").set(validTradePayload(BOB_UID, "Bob")));
  await assertFails(bobDb.collection("discipline_entries").doc("bob_disc_x").set(validDisciplinePayload(BOB_UID, "Bob")));
});

test("Verified Admin can monitor all students, manage invites, suspend accounts, and view all trades", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID, "Alice", "alice@example.com"));
    await db.collection("trades").doc("alice_trade_1").set(validTradePayload(ALICE_UID, "Alice"));
    await db.collection("discipline_entries").doc("alice_disc_1").set(validDisciplinePayload(ALICE_UID, "Alice"));
  });

  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore();

  // Admin can list all users, all trades, and all discipline entries
  await assertSucceeds(adminDb.collection("users").get());
  await assertSucceeds(adminDb.collection("trades").get());
  await assertSucceeds(adminDb.collection("discipline_entries").get());

  // Admin can suspend a student
  await assertSucceeds(
    adminDb.collection("users").doc(ALICE_UID).update({
      status: "SUSPENDED",
      adminNotes: "Reviewing risk management",
      updatedAt: nowPast(),
    })
  );

  // Admin can create and update student invites
  const ts = nowPast();
  await assertSucceeds(
    adminDb.collection("student_invites").doc("inv_1").set({
      email: "newstudent@example.com",
      name: "New Student",
      status: "INVITED",
      tradingCapital: 50000,
      dailyGoal: 1000,
      dailyMaxLoss: 500,
      maxTradesPerDay: 3,
      notes: "Batch 1",
      createdByUid: ADMIN_UID,
      createdAt: ts,
      updatedAt: ts,
    })
  );
});
