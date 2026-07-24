# Spring Boot AWS Deployment Tutorial

This repository contains a simple Spring Boot application with one REST endpoint that returns a hello message. It is designed for a beginner-friendly learning path covering:

- local Spring Boot development
- Docker containerization
- GitHub repository setup
- Jenkins CI/CD pipeline
- AWS deployment

## Project Overview

The app exposes:

- GET /hello -> returns "Hello World from Spring Boot!"
- GET / -> returns "Spring Boot app is running"

## Step 1: Prerequisites

Install the following tools on your machine:

- Java 17
- Maven
- Docker Desktop
- Git
- A GitHub account
- A Jenkins server
- An AWS account

### Verify installations

Run these commands in your terminal:

```bash
java -version
mvn -version
git --version
docker --version
```

## Step 2: Create the Spring Boot Application

If you are starting from scratch, create a new Spring Boot project using Spring Initializr:

- Go to https://start.spring.io/
- Choose:
  - Project: Maven
  - Language: Java
  - Spring Boot: 3.3.x
  - Java: 17
- Add dependencies:
  - Spring Web
  - Spring Boot Actuator
- Generate and unzip the project

This repository already includes a minimal working version.

## Step 3: Run the Application Locally

From the project root, run:

```bash
mvn spring-boot:run
```

Open the following URL in your browser:

- http://localhost:8080/hello

Expected response:

```text
Hello World from Spring Boot!
```

## Step 4: Build the Application

Run:

```bash
mvn clean package
```

This creates a JAR file in the target folder.

## Step 5: Run with Docker Locally

### 5.1 Build the Docker image

```bash
docker build -t springboot-aws-app:latest .
```

### 5.2 Run the container

```bash
docker run -p 8080:8080 springboot-aws-app:latest
```

Then open:

- http://localhost:8080/hello

### 5.3 Use Docker Compose

```bash
docker compose up --build
```

To stop it:

```bash
docker compose down
```

## Step 6: Push Code to GitHub

### 6.1 Initialize Git

```bash
git init
git add .
git commit -m "Initial commit"
```

### 6.2 Create a GitHub repository

- Go to https://github.com/
- Click New repository
- Name it, for example: springboot-aws-app
- Do not initialize with README if you already have files
- Create repository

### 6.3 Connect local project to GitHub

```bash
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/springboot-aws-app.git
git push -u origin main
```

## Step 7: Create a Jenkins Pipeline

### 7.1 Install Jenkins

Install Jenkins on a machine or server. A common beginner-friendly option is:

- local machine
- AWS EC2 instance
- Docker container

### 7.2 Install required plugins

In Jenkins, install these plugins:

- Git Plugin
- Maven Integration Plugin
- Docker Plugin
- Pipeline Plugin

### 7.3 Configure tools

In Jenkins global tool configuration, add:

- JDK 17
- Maven

### 7.4 Create a Pipeline job

- Click New Item
- Choose Pipeline
- Name it springboot-aws-app
- Under Pipeline, choose Definition: Pipeline script from SCM
- Set Repository URL to your GitHub repo
- Set Branch Specifier to main
- Save and Build

The Jenkinsfile in this repository already contains a sample pipeline.

## Step 8: Deploy to AWS with Jenkins

### 8.1 Create an EC2 instance

In AWS:

- Open EC2 Dashboard
- Click Launch Instance
- Choose Ubuntu or Amazon Linux
- Select a key pair
- Allow ports 22 and 8080

### 8.2 Connect to the EC2 instance

```bash
ssh -i your-key.pem ubuntu@YOUR_EC2_PUBLIC_IP
```

### 8.3 Install Docker on EC2

```bash
sudo apt-get update
sudo apt-get install -y docker.io
sudo systemctl enable docker
sudo systemctl start docker
sudo usermod -aG docker ubuntu
```

### 8.4 Install Java and Maven on EC2 (optional if Jenkins runs elsewhere)

If your Jenkins server is also on EC2, install:

```bash
sudo apt-get install -y openjdk-17-jdk maven
```

### 8.5 Run the app on EC2

After the Jenkins pipeline builds the image, run:

```bash
docker run -d -p 8080:8080 --name springboot-aws-app springboot-aws-app:latest
```

Then open:

- http://YOUR_EC2_PUBLIC_IP:8080/hello

## Step 9: Recommended Next Improvements

As you learn more, you can add:

- PostgreSQL database
- Environment variables
- Docker Hub or ECR push
- AWS ECS or Elastic Beanstalk deployment
- Kubernetes deployment

## Troubleshooting

### Maven build fails

Check Java version:

```bash
java -version
```

### Docker daemon not running

Start Docker Desktop or Docker service.

### Jenkins cannot find Maven or JDK

Ensure the tools are configured in Jenkins Global Tool Configuration.

## Summary

You now have:

- a simple Spring Boot app
- a Dockerfile for containerization
- a Jenkins pipeline file
- a beginner-friendly deployment guide

This is a solid first project to learn DevOps with Java, Docker, Jenkins, GitHub, and AWS.
