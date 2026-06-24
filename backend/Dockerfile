# Use official Node.js light runtime
FROM node:18-alpine

# Create app directory
WORKDIR /usr/src/app

# Copy package configurations
COPY package*.json ./

# Install production dependencies
RUN npm ci --only=production

# Copy source code files
COPY index.js ./

# Expose port (Cloud Run defaults to 8080)
EXPOSE 8080

# Run the web service on container startup
CMD [ "npm", "start" ]
