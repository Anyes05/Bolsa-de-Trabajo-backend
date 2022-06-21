import { ConnectionOptions, createConnections, getConnection } from "typeorm";
import { startUp } from "./libs/startUp";

/* ----- DataBase Connection ----- */

// createConnection method will automatically read connection options from the ormconfig file or environment variables
export const connection = {
  async create(options? : ConnectionOptions[]){
    await createConnections().then(async () => {
      await startUp();

      console.info("DB is connected...")    
      
    }).catch(e => console.log(e));   
  },
  
  async close() {
    await getConnection().close();
  }
}
