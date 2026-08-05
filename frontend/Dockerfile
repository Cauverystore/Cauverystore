ARG REACT_APP_API_URL=
ARG REACT_APP_GA_MEASUREMENT_ID=

FROM node:20-alpine AS build
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm install
COPY . .
RUN REACT_APP_API_URL=$REACT_APP_API_URL REACT_APP_GA_MEASUREMENT_ID=$REACT_APP_GA_MEASUREMENT_ID npm run build

FROM nginx:alpine
COPY --from=build /app/build /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
