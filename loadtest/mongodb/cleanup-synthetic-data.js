const prefix = process.env.LOADTEST_PREFIX;

if (!prefix || !prefix.startsWith("loadtest-")) {
  throw new Error("LOADTEST_PREFIX must be set and start with loadtest-");
}

const database = db.getSiblingDB(process.env.LOADTEST_DB || "stayops");
const idRegex = new RegExp(`^${prefix.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}`);
const emailRegex = new RegExp(`^${prefix.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}|^loadtest-customer-`);

const deletes = [
  ["payments", { _id: idRegex }],
  ["payment_outbox_messages", { _id: idRegex }],
  ["reservations", { _id: idRegex }],
  ["guests", { _id: idRegex }],
  ["room_inventories", { _id: idRegex }],
  ["rooms", { _id: idRegex }],
  ["room_types", { _id: idRegex }],
  ["channels", { _id: idRegex }],
  ["properties", { _id: idRegex }],
  ["members", { $or: [{ _id: idRegex }, { email: emailRegex }] }],
  ["loadtest_runs", { _id: prefix }],
];

for (const [collection, query] of deletes) {
  const result = database.getCollection(collection).deleteMany(query);
  print(`${collection}: deleted ${result.deletedCount}`);
}

print(`Load-test cleanup complete. prefix=${prefix}`);
