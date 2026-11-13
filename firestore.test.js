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

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validCustomerPayload(userId = ALICE_UID, overrides = {}) {
  const pastTime = new Date(Date.now() - 2000);
  return {
    userId,
    fullName: "أحمد محمد",
    phone: "0501234567",
    nationalId: "1010101010",
    address: "الرياض",
    notes: "عميل منتظم",
    clientCreatedAtMillis: Date.now() - 2000,
    clientUpdatedAtMillis: Date.now() - 2000,
    createdAt: pastTime,
    updatedAt: pastTime,
    ...overrides,
  };
}

function validPlanPayload(userId = ALICE_UID, customerId = "cust_1", overrides = {}) {
  const pastTime = new Date(Date.now() - 2000);
  return {
    userId,
    customerId,
    itemName: "لابتوب عملي",
    totalCashPrice: 3000,
    downPayment: 1000,
    interestRatePercent: 10,
    financedTotal: 2200,
    installmentCount: 4,
    intervalMonths: 1,
    startDateMillis: Date.now(),
    status: "ACTIVE",
    notes: "",
    clientCreatedAtMillis: Date.now() - 2000,
    clientUpdatedAtMillis: Date.now() - 2000,
    createdAt: pastTime,
    updatedAt: pastTime,
    ...overrides,
  };
}

function validInstallmentPayload(
  userId = ALICE_UID,
  customerId = "cust_1",
  planId = "plan_1",
  overrides = {}
) {
  const pastTime = new Date(Date.now() - 2000);
  return {
    userId,
    planId,
    customerId,
    installmentNumber: 1,
    dueDateMillis: Date.now() + 86400000,
    amountDue: 550,
    amountPaid: 0,
    status: "PENDING",
    paymentMethod: "CASH",
    receiptNumber: "",
    notes: "",
    clientUpdatedAtMillis: Date.now() - 2000,
    createdAt: pastTime,
    updatedAt: pastTime,
    ...overrides,
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

// --- SECURITY BOUNDS TESTS ---

test("Unauthenticated user: cannot read or write customers, plans, or installments", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("customers").get());
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("customers")
      .doc("cust_1")
      .set(validCustomerPayload())
  );
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("plans").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("installments").get());
});

test("Cross-user isolation: Bob cannot read, list, create, update, or delete Alice's records", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("users").doc(ALICE_UID).collection("customers").doc("cust_1").set(validCustomerPayload());
    await db.collection("users").doc(ALICE_UID).collection("plans").doc("plan_1").set(validPlanPayload());
    await db
      .collection("users")
      .doc(ALICE_UID)
      .collection("installments")
      .doc("inst_1")
      .set(validInstallmentPayload());
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("customers").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("customers").doc("cust_1").get());
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("customers")
      .doc("cust_2")
      .set(validCustomerPayload(BOB_UID))
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("customers")
      .doc("cust_1")
      .update({ fullName: "Hacked" })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("customers").doc("cust_1").delete());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("plans").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("installments").get());
});

test("Owner CRUD & Query Alignment: Alice can create, list, and update her customers, plans, and installments", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const aliceUserRef = aliceDb.collection("users").doc(ALICE_UID);

  await assertSucceeds(aliceUserRef.collection("customers").doc("cust_1").set(validCustomerPayload()));
  await assertSucceeds(aliceUserRef.collection("customers").get());

  await assertSucceeds(aliceUserRef.collection("plans").doc("plan_1").set(validPlanPayload()));
  await assertSucceeds(aliceUserRef.collection("plans").get());

  await assertSucceeds(aliceUserRef.collection("installments").doc("inst_1").set(validInstallmentPayload()));
  await assertSucceeds(aliceUserRef.collection("installments").get());

  const newerTime = new Date(Date.now() - 500);
  await assertSucceeds(
    aliceUserRef.collection("customers").doc("cust_1").update({
      fullName: "أحمد محمد علي",
      updatedAt: newerTime,
    })
  );

  await assertSucceeds(
    aliceUserRef.collection("installments").doc("inst_1").update({
      amountPaid: 550,
      paidDateMillis: Date.now(),
      status: "PAID",
      paymentMethod: "BANK_TRANSFER",
      receiptNumber: "REC-01",
      updatedAt: newerTime,
    })
  );
});

test("Red Team: rejects ghost fields (Shadow Update), spoofed userId, orphan plans, and terminal state tampering", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const aliceUserRef = aliceDb.collection("users").doc(ALICE_UID);

  // Ghost field on create
  await assertFails(
    aliceUserRef
      .collection("customers")
      .doc("cust_bad")
      .set(validCustomerPayload(ALICE_UID, { isVerified: true }))
  );

  // Identity spoofing
  await assertFails(
    aliceUserRef
      .collection("customers")
      .doc("cust_spoof")
      .set(validCustomerPayload(BOB_UID))
  );

  // Orphan plan referencing missing customer
  await assertFails(
    aliceUserRef
      .collection("plans")
      .doc("plan_orphan")
      .set(validPlanPayload(ALICE_UID, "missing_customer"))
  );

  // Create valid chain
  await assertSucceeds(aliceUserRef.collection("customers").doc("cust_1").set(validCustomerPayload()));
  await assertSucceeds(aliceUserRef.collection("plans").doc("plan_1").set(validPlanPayload()));
  await assertSucceeds(aliceUserRef.collection("installments").doc("inst_1").set(validInstallmentPayload()));

  // Shadow Update ghost field
  await assertFails(
    aliceUserRef.collection("customers").doc("cust_1").update({
      fullName: "Updated",
      adminOverride: true,
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Pay installment to terminal PAID state
  await assertSucceeds(
    aliceUserRef.collection("installments").doc("inst_1").update({
      amountPaid: 550,
      status: "PAID",
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Terminal state locking: cannot mutate paid installment again
  await assertFails(
    aliceUserRef.collection("installments").doc("inst_1").update({
      amountPaid: 100,
      status: "PARTIAL",
      updatedAt: new Date(Date.now() - 50),
    })
  );
});
