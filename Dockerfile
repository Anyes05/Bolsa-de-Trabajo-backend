#Step 1

FROM node:14

WORKDIR /app

COPY package*.json ./

RUN npm install

COPY . .

RUN npm run build --prod

EXPOSE 3000

CMD [ "node", "./build/index.js" ]