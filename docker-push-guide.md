# How to Push a Docker Image to Docker Hub from Docker Desktop

This guide explains step by step how to build a Docker image for your Spring Boot app, log in to Docker Hub from Docker Desktop, and push the image to Docker Hub.

## 1. Prerequisites

Before you start, make sure you have:

- Docker Desktop installed and running
- A Docker Hub account
- A Dockerfile in your project
- Your Spring Boot app built locally or ready to containerize

## 2. Verify Docker Desktop is Running

Open Docker Desktop and make sure the engine is running.

Check in your terminal:

```bash
docker --version
docker info
```

If these commands work, Docker is ready.

## 3. Build the Docker Image

Go to your project folder:

```bash
cd /path/to/your/project
```

Build the image:

```bash
docker build -t springboot-aws-app:latest .
```

### What this command does

- `docker build` creates an image from the Dockerfile
- `-t springboot-aws-app:latest` gives the image a name and tag
- `.` means use the current folder as the build context

## 4. Check the Image

List your local images:

```bash
docker images
```

You should see an image similar to:

```bash
springboot-aws-app   latest
```

## 5. Create a Docker Hub Account

If you do not already have one:

1. Go to https://hub.docker.com/
2. Create a free account
3. Verify your email
4. Sign in

## 6. Log In to Docker Hub from Docker Desktop

Open Docker Desktop.

Then sign in using your Docker Hub credentials:

1. Click the Docker Desktop icon
2. Open the Docker Hub sign-in section
3. Enter your Docker Hub username and password
4. Click Sign In

You can also sign in from the terminal:

```bash
docker login
```

When prompted, enter:

```bash
Username: your_dockerhub_username
Password: your_dockerhub_password
```

If login is successful, you will see:

```bash
Login Succeeded
```

## 7. Tag the Image for Docker Hub

Docker Hub expects images in this format:

```bash
dockerhub_username/image_name:tag
```

Tag your image like this:

```bash
docker tag springboot-aws-app:latest your_dockerhub_username/springboot-aws-app:latest
```

Example:

```bash
docker tag springboot-aws-app:latest john/springboot-aws-app:latest
```

## 8. Push the Image to Docker Hub

Now push the tagged image:

```bash
docker push your_dockerhub_username/springboot-aws-app:latest
```

Example:

```bash
docker push john/springboot-aws-app:latest
```

## 9. Verify the Image on Docker Hub

After the upload finishes:

1. Open https://hub.docker.com/
2. Go to your account
3. Open the repository you pushed to
4. Confirm that the image appears there

## 10. Run the Image from Docker Hub

To test the image from Docker Hub on another machine, run:

```bash
docker pull your_dockerhub_username/springboot-aws-app:latest
docker run -p 8080:8080 your_dockerhub_username/springboot-aws-app:latest
```

Then open:

```text
http://localhost:8080/hello
```

## 11. Common Problems and Fixes

### Problem: Docker Desktop is not running

Solution:

- Start Docker Desktop
- Wait until the engine is fully started

### Problem: Login failed

Solution:

- Check your username and password
- Make sure your Docker Hub account is verified
- Retry with `docker login`

### Problem: Permission denied or unauthorized

Solution:

- Make sure the image is tagged correctly
- Check that you are pushing to your own repository
- Ensure you are logged in to the correct account

### Problem: Image not found after push

Solution:

- Wait a few seconds
- Refresh Docker Hub
- Check the repository name carefully

## 12. Example Full Flow

Here is the complete command sequence:

```bash
docker build -t springboot-aws-app:latest .
docker login
docker tag springboot-aws-app:latest your_dockerhub_username/springboot-aws-app:latest
docker push your_dockerhub_username/springboot-aws-app:latest
```

## 13. Important Notes

- Use lowercase names for Docker Hub repositories if possible
- Always use a tag such as `latest`, `v1`, or `dev`
- Keep your Docker Hub credentials safe
- Do not share your password in public repositories or screenshots

## 14. Summary

You now know how to:

- build an image
- log in to Docker Hub
- tag the image correctly
- push the image to Docker Hub
- pull and run it later
