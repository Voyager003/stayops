const appUsername = process.env.MONGO_APP_USERNAME;
const appPassword = process.env.MONGO_APP_PASSWORD;
const exporterUsername = process.env.MONGO_EXPORTER_USERNAME;
const exporterPassword = process.env.MONGO_EXPORTER_PASSWORD;

if (!appUsername || !appPassword || !exporterUsername || !exporterPassword) {
  throw new Error("MONGO_APP_USERNAME, MONGO_APP_PASSWORD, MONGO_EXPORTER_USERNAME, and MONGO_EXPORTER_PASSWORD are required");
}

const adminDb = db.getSiblingDB("admin");

upsertUser(appUsername, appPassword, [
  { role: "readWrite", db: "stayops" }
]);

upsertUser(exporterUsername, exporterPassword, [
  { role: "clusterMonitor", db: "admin" },
  { role: "readAnyDatabase", db: "admin" }
]);

function upsertUser(username, password, roles) {
  if (adminDb.getUser(username)) {
    adminDb.updateUser(username, { pwd: password, roles });
    print(`Updated MongoDB user: ${username}`);
    return;
  }

  adminDb.createUser({
    user: username,
    pwd: password,
    roles
  });
  print(`Created MongoDB user: ${username}`);
}
