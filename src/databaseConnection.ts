import { ConnectionOptions, createConnections, getConnection } from "typeorm";
import { startUp } from "./libs/startUp";

/* ----- DataBase Connection ----- */
require('dotenv').config()

const connectionConfig: ConnectionOptions[] = [{
  name: "default",
  type: "postgres",
  host: process.env.POSTGRES_HOST,
  port: parseInt(<string>process.env.POSTGRES_PORT),
  username: process.env.POSTGRES_USER,
  password: process.env.POSTGRES_PASSWORD,
  database: process.env.POSTGRES_DB,
  entities: [
    "build/models/*.js"
  ],
  logging: false,
  synchronize: true,
  dropSchema: true
}]

// createConnection method will automatically read connection options from the ormconfig file or environment variables
export const connection = {
  async create(options?: ConnectionOptions[]) {

    await createConnections(connectionConfig).then(async () => {
      await startUp();

      console.info("DB is connected...")

    }).catch(e => console.log(e));
  },

  async close() {
    await getConnection().close();
  }
}
