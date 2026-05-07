const replicaSet = process.env.MONGO_REPLICA_SET || "rs0";
const mongoHost = process.env.MONGO_HOST || "mongo";

const config = {
  _id: replicaSet,
  members: [
    { _id: 0, host: `${mongoHost}:27017`, priority: 1 }
  ]
};

try {
  rs.initiate(config);
  print(`Single-node replica set ${replicaSet} initiated`);
} catch (error) {
  if (error.codeName !== "AlreadyInitialized") {
    throw error;
  }
  print(`Single-node replica set ${replicaSet} already initialized; applying reconfig`);
  rs.reconfig(config);
}

printjson(rs.status());
