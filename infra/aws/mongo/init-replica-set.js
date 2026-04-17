const replicaSet = process.env.MONGO_REPLICA_SET || "rs0";
const mongo1Host = process.env.MONGO1_HOST;
const mongo2Host = process.env.MONGO2_HOST;
const arbiterHost = process.env.MONGO_ARBITER_HOST;

if (!mongo1Host || !mongo2Host || !arbiterHost) {
  throw new Error("MONGO1_HOST, MONGO2_HOST, and MONGO_ARBITER_HOST are required");
}

const config = {
  _id: replicaSet,
  members: [
    { _id: 0, host: `${mongo1Host}:27017`, priority: 2 },
    { _id: 1, host: `${mongo2Host}:27017`, priority: 1 },
    { _id: 2, host: `${arbiterHost}:27017`, arbiterOnly: true }
  ]
};

try {
  rs.initiate(config);
  print(`Replica set ${replicaSet} initiated`);
} catch (error) {
  if (error.codeName !== "AlreadyInitialized") {
    throw error;
  }
  print(`Replica set ${replicaSet} already initialized; applying reconfig`);
  rs.reconfig(config);
}

printjson(rs.status());
