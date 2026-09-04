import { after, before, beforeEach, test } from "node:test";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from "@firebase/rules-unit-testing";
import { doc, getDoc, serverTimestamp, setDoc, updateDoc } from "firebase/firestore";
import { getBytes, ref as storageRef, uploadBytes } from "firebase/storage";

const projectId = "motionfuel-test";
const ownerId = "owner-user";
const ownerEmail = "owner@example.com";
let environment;

const profile = (overrides = {}) => ({
  name: "MotionFuel Tester",
  email: ownerEmail,
  age: 24,
  sex: "MALE",
  heightCm: 175,
  weightKg: 72,
  activityLevel: "MODERATE",
  activityFactor: 1.55,
  maintenanceCaloriesKcal: 2400,
  dailyCalorieGoalKcal: 2200,
  profileComplete: true,
  createdAt: serverTimestamp(),
  ...overrides,
});

const context = (uid, email, verified) => environment.authenticatedContext(uid, {
  email,
  email_verified: verified,
});

const createOwnerProfile = () => {
  const db = context(ownerId, ownerEmail, false).firestore();
  return setDoc(doc(db, "users", ownerId), profile());
};

before(async () => {
  environment = await initializeTestEnvironment({
    projectId,
    firestore: {
      host: "127.0.0.1",
      port: 8080,
      rules: readFileSync(fileURLToPath(new URL("../firestore.rules", import.meta.url)), "utf8"),
    },
    storage: {
      host: "127.0.0.1",
      port: 9199,
      rules: readFileSync(fileURLToPath(new URL("../storage.rules", import.meta.url)), "utf8"),
    },
  });
});

beforeEach(async () => {
  await environment.clearFirestore();
  await environment.clearStorage();
});
after(async () => environment.cleanup());

test("unauthenticated clients cannot read or create profiles", async () => {
  const db = environment.unauthenticatedContext().firestore();
  await assertFails(getDoc(doc(db, "users", ownerId)));
  await assertFails(setDoc(doc(db, "users", ownerId), profile()));
});

test("an unverified account can create its initial valid profile but cannot read it", async () => {
  const db = context(ownerId, ownerEmail, false).firestore();
  await assertSucceeds(setDoc(doc(db, "users", ownerId), profile()));
  await assertFails(getDoc(doc(db, "users", ownerId)));
});

test("verified owners can read their profile while another user cannot", async () => {
  await assertSucceeds(createOwnerProfile());
  const ownerDb = context(ownerId, ownerEmail, true).firestore();
  const attackerDb = context("other-user", "other@example.com", true).firestore();
  await assertSucceeds(getDoc(doc(ownerDb, "users", ownerId)));
  await assertFails(getDoc(doc(attackerDb, "users", ownerId)));
});

test("profile schema rejects unknown fields and out-of-range values", async () => {
  const db = context(ownerId, ownerEmail, false).firestore();
  await assertFails(setDoc(doc(db, "users", ownerId), profile({ administrator: true })));
  await assertFails(setDoc(doc(db, "users", ownerId), profile({ age: 12 })));
  await assertFails(setDoc(doc(db, "users", ownerId), profile({ email: "different@example.com" })));
});

test("verified owners can update valid profile fields only with a server timestamp", async () => {
  await assertSucceeds(createOwnerProfile());
  const db = context(ownerId, ownerEmail, true).firestore();
  const reference = doc(db, "users", ownerId);
  await assertSucceeds(updateDoc(reference, { weightKg: 73, updatedAt: serverTimestamp() }));
  await assertSucceeds(updateDoc(reference, {
    name: "Updated Member",
    age: 25,
    heightCm: 176,
    activityLevel: "VERY_ACTIVE",
    activityFactor: 1.725,
    maintenanceCaloriesKcal: 2800,
    dailyCalorieGoalKcal: 2500,
    updatedAt: serverTimestamp(),
  }));
  await assertSucceeds(updateDoc(reference, {
    photoUrl: "https://firebasestorage.googleapis.com/example/avatar",
    updatedAt: serverTimestamp(),
  }));
  await assertFails(updateDoc(reference, { weightKg: 351, updatedAt: serverTimestamp() }));
  await assertFails(updateDoc(reference, { email: "attacker@example.com", updatedAt: serverTimestamp() }));
  await assertFails(updateDoc(reference, { photoUrl: "x".repeat(2049), updatedAt: serverTimestamp() }));
});

test("weight entries require a verified owner and bounded schema", async () => {
  const verifiedDb = context(ownerId, ownerEmail, true).firestore();
  const unverifiedDb = context(ownerId, ownerEmail, false).firestore();
  const valid = { weightKg: 72.5, recordedAt: serverTimestamp() };
  await assertSucceeds(setDoc(doc(verifiedDb, "users", ownerId, "weightEntries", "valid"), valid));
  await assertFails(setDoc(doc(unverifiedDb, "users", ownerId, "weightEntries", "unverified"), valid));
  await assertFails(setDoc(doc(verifiedDb, "users", ownerId, "weightEntries", "bad"), { ...valid, weightKg: 500 }));
});

test("profile images are private to their authenticated owner", async () => {
  const ownerStorage = context(ownerId, ownerEmail, false).storage();
  const attackerStorage = context("other-user", "other@example.com", true).storage();
  const unauthenticatedStorage = environment.unauthenticatedContext().storage();
  const avatar = storageRef(ownerStorage, `profile-images/${ownerId}/avatar`);
  const imageBytes = new Uint8Array([0xff, 0xd8, 0xff, 0xd9]);

  await assertSucceeds(uploadBytes(avatar, imageBytes, { contentType: "image/jpeg" }));
  await assertSucceeds(getBytes(avatar));
  await assertFails(getBytes(storageRef(attackerStorage, `profile-images/${ownerId}/avatar`)));
  await assertFails(getBytes(storageRef(unauthenticatedStorage, `profile-images/${ownerId}/avatar`)));
});

test("profile image writes reject other users, non-images and unsupported paths", async () => {
  const ownerStorage = context(ownerId, ownerEmail, true).storage();
  const attackerStorage = context("other-user", "other@example.com", true).storage();
  const imageBytes = new Uint8Array([0xff, 0xd8, 0xff, 0xd9]);

  await assertFails(uploadBytes(
    storageRef(attackerStorage, `profile-images/${ownerId}/avatar`),
    imageBytes,
    { contentType: "image/jpeg" },
  ));
  await assertFails(uploadBytes(
    storageRef(ownerStorage, `profile-images/${ownerId}/avatar`),
    new TextEncoder().encode("not an image"),
    { contentType: "text/plain" },
  ));
  await assertFails(uploadBytes(
    storageRef(ownerStorage, `profile-images/${ownerId}/extra-file`),
    imageBytes,
    { contentType: "image/jpeg" },
  ));
});
