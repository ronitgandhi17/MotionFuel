import { after, before, beforeEach, test } from "node:test";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from "@firebase/rules-unit-testing";
import { deleteDoc, doc, getDoc, serverTimestamp, setDoc, updateDoc } from "firebase/firestore";
import { deleteObject, getBytes, ref as storageRef, uploadBytes } from "firebase/storage";

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

test("only a verified owner can delete the profile", async () => {
  await assertSucceeds(createOwnerProfile());
  const ownerDb = context(ownerId, ownerEmail, true).firestore();
  const attackerDb = context("other-user", "other@example.com", true).firestore();
  await assertFails(deleteDoc(doc(attackerDb, "users", ownerId)));
  await assertSucceeds(deleteDoc(doc(ownerDb, "users", ownerId)));
});

test("only an avatar owner can delete the stored image", async () => {
  const ownerStorage = context(ownerId, ownerEmail, true).storage();
  const attackerStorage = context("other-user", "other@example.com", true).storage();
  const avatar = storageRef(ownerStorage, `profile-images/${ownerId}/avatar`);
  await assertSucceeds(uploadBytes(avatar, new Uint8Array([0xff, 0xd8, 0xff, 0xd9]), { contentType: "image/jpeg" }));
  await assertFails(deleteObject(storageRef(attackerStorage, `profile-images/${ownerId}/avatar`)));
  await assertSucceeds(deleteObject(avatar));
});

test("safety shares are time limited and writable only by their owner", async () => {
  const ownerDb = context(ownerId, ownerEmail, true).firestore();
  const viewerDb = context("viewer", "viewer@example.com", true).firestore();
  const share = doc(ownerDb, "safetyShares", "safe-token");
  await assertSucceeds(setDoc(share, { ownerUid: ownerId, expiresAtMillis: Date.now() + 3000000, active: true, latitude: -37.8, longitude: 144.9, updatedAtMillis: Date.now() }));
  await assertSucceeds(getDoc(doc(viewerDb, "safetyShares", "safe-token")));
  await assertFails(updateDoc(doc(viewerDb, "safetyShares", "safe-token"), { latitude: 0 }));
  await assertFails(setDoc(doc(ownerDb, "safetyShares", "too-long"), { ownerUid: ownerId, expiresAtMillis: Date.now() + 7200000, active: true, latitude: 0, longitude: 0, updatedAtMillis: Date.now() }));
});

test("verified users can join challenges but cannot write another member score", async () => {
  const ownerDb = context(ownerId, ownerEmail, true).firestore();
  const memberDb = context("member", "member@example.com", true).firestore();
  const challenge = doc(ownerDb, "challenges", "weekly-5k");
  await assertSucceeds(setDoc(challenge, { title: "Weekly 5K", metric: "km", target: 5, endsAtMillis: Date.now() + 604800000, ownerUid: ownerId }));
  await assertSucceeds(setDoc(doc(memberDb, "challenges", "weekly-5k", "members", "member"), { displayName: "Member", score: 2.5 }));
  await assertFails(setDoc(doc(memberDb, "challenges", "weekly-5k", "members", ownerId), { displayName: "Fake owner", score: 999 }));
  await assertSucceeds(getDoc(doc(memberDb, "challenges", "weekly-5k", "members", "member")));
});

test("sync manifests require verified ownership, exact schema and server time", async () => {
  const db = context(ownerId, ownerEmail, true).firestore();
  const path = `users/${ownerId}/sync/current`;
  const value = { version: "12345678-1234-1234-1234-123456789abc", updatedAt: serverTimestamp() };
  await assertSucceeds(setDoc(doc(db, path), value));
  await assertFails(getDoc(doc(context("attacker", "a@example.com", true).firestore(), path)));
  await assertFails(getDoc(doc(context(ownerId, ownerEmail, false).firestore(), path)));
  await assertFails(setDoc(doc(db, path), { ...value, extra: true }));
  await assertFails(setDoc(doc(db, path), { ...value, version: "../../other" }));
  await assertSucceeds(deleteDoc(doc(db, path)));
});

test("cloud snapshots are private, verified-owner JSON objects and immutable", async () => {
  const path = `backups/${ownerId}/12345678-1234-1234-1234-123456789abc.json`;
  const bytes = new TextEncoder().encode('{"schema":6}');
  const owner = context(ownerId, ownerEmail, true).storage();
  await assertSucceeds(uploadBytes(storageRef(owner, path), bytes, { contentType: "application/json" }));
  await assertSucceeds(getBytes(storageRef(owner, path)));
  await assertFails(uploadBytes(storageRef(owner, path), bytes, { contentType: "application/json" }));
  await assertFails(getBytes(storageRef(context("attacker", "a@example.com", true).storage(), path)));
  await assertFails(getBytes(storageRef(environment.unauthenticatedContext().storage(), path)));
  await assertFails(getBytes(storageRef(context(ownerId, ownerEmail, false).storage(), path)));
  await assertSucceeds(deleteObject(storageRef(owner, path)));
});

test("backup uploads reject excess size, wrong type, path and ownership", async () => {
  const owner = context(ownerId, ownerEmail, true).storage();
  const path = `backups/${ownerId}/12345678-1234-1234-1234-123456789abc.json`;
  await assertFails(uploadBytes(storageRef(owner, path), new Uint8Array(10 * 1024 * 1024 + 1), { contentType: "application/json" }));
  await assertFails(uploadBytes(storageRef(owner, path), new Uint8Array(1), { contentType: "image/jpeg" }));
  await assertFails(uploadBytes(storageRef(owner, `backups/${ownerId}/bad.txt`), new Uint8Array(1), { contentType: "application/json" }));
  await assertFails(uploadBytes(storageRef(context("attacker", "a@example.com", true).storage(), path), new Uint8Array(1), { contentType: "application/json" }));
});
