import { ConnectionOptions } from "typeorm";

export const connectionConfig: ConnectionOptions[] = [{
    name: "default",
    type: "postgres",
    host: process.env.DB_HOST,
    port: process.env.DB_PORT,
    username: process.env.DB_USER,
    password: process.env.DB_PASS,
    database: process.env.DB_NAME,
    entities: [
        "build/models/**/*.js",
        "src/models/**/*.ts"
    ],
    logging: false,
    synchronize: true,
    dropSchema: true
}]