# Spring Boot + Docker + Jenkins + AWS: A-to-Z Deployment Guide

This guide is written for this repository and for someone new to AWS. Follow the
steps in order. Read the **Why** line before doing each step.

## 1. What this project contains

The source review found:

- Maven Spring Boot application using Java 17 and Spring Boot 3.3.2.
- Application port: `8080`.
- Endpoints:
  - `/` returns an application-running message.
  - `/hello` returns `Hello World from Spring Boot!`.
  - `/actuator/health` is the health endpoint.
- `Dockerfile` uses a Maven/Java 17 build image and a Java 17 runtime image.
- `docker-compose.yml` maps host port `8080` to container port `8080`.
- One controller test exists.
- No database, AWS secret, or environment variable is currently required.

The Dockerfile packages with `-DskipTests`, so the Jenkins pipeline below runs
tests separately before it builds the image.

> Note: Spring Boot 3.3.2 is old. The deployment will work, but plan a separate
> dependency-upgrade task after the first successful deployment.

## 2. Target architecture

```text
GitHub repository
      |
      | Jenkins checks every 5 minutes
      v
Jenkins EC2 (build/test) ----push image----> Amazon ECR
      |
      | SSH through private VPC address
      v
Application EC2 ----pull image from ECR----> Docker container :8080
      |
      v
http://APPLICATION_ELASTIC_IP:8080/hello
```

AWS resources that will be created:

| Resource | Suggested name | Why it is needed |
|---|---|---|
| Key pair | `springboot-aws-key` | Lets you SSH into both new EC2 instances |
| Security group | `jenkins-sg` | Controls access to Jenkins and Jenkins SSH |
| Security group | `springboot-app-sg` | Controls access to the app and deployment SSH |
| EC2 instance | `jenkins-server` | Dedicated CI/CD server |
| EC2 instance | `springboot-app-server` | Runs the Dockerized application |
| ECR repository | `springboot-aws-app` | Private storage for Docker images |
| IAM role | `JenkinsEcrPushRole` | Lets Jenkins push images without stored AWS keys |
| IAM role | `AppEcrPullRole` | Lets the app server pull images without stored AWS keys |
| Elastic IPs | one per instance | Keep public addresses stable after stop/start |

## 3. Important security and cost rules

**Why:** These rules prevent the most common beginner mistakes.

1. Do not use the AWS account root user for daily work. The phrase “I am root”
   normally means the AWS root account, which has unrestricted power.
2. On the root user, enable MFA and do not create root access keys.
3. Create an administrative user through IAM Identity Center (preferred) or IAM,
   enable MFA, then use that identity for this guide.
4. Never put an AWS access key, GitHub token, password, or `.pem` file in Git.
5. Use EC2 IAM roles. This guide does not create permanent AWS access keys.
6. EC2, EBS, Elastic IP, and public IPv4 usage can cost money. Check the AWS
   Pricing Calculator and Billing dashboard. Stop or terminate learning resources
   when finished. An allocated but unused Elastic IP can still incur charges.
7. Create a small AWS Budget before creating servers. A budget alerts you; it
   does not automatically stop charges.

Official references:

- [Secure the AWS account root user](https://docs.aws.amazon.com/IAM/latest/UserGuide/root-user-best-practices.html)
- [AWS Budgets tutorial](https://docs.aws.amazon.com/cost-management/latest/userguide/budgets-create.html)
- [Amazon EC2 On-Demand pricing](https://aws.amazon.com/ec2/pricing/on-demand/)

## 4. Values to write down

Choose one AWS Region and use it everywhere. This example uses Mumbai.

```text
AWS_REGION=ap-south-1
AWS_ACCOUNT_ID=12-digit account number
MY_PUBLIC_IP=x.x.x.x/32
GITHUB_REPOSITORY_URL=https://github.com/USERNAME/springBoot-aws-app.git
JENKINS_ELASTIC_IP=created later
APP_ELASTIC_IP=created later
APP_PRIVATE_IP=created later
ECR_URI=ACCOUNT_ID.dkr.ecr.ap-south-1.amazonaws.com/springboot-aws-app
```

Find the account ID from the account menu at the top-right of the AWS Console.
Find your public IP by searching the web for `what is my IP`, then add `/32`.

Do not type the angle brackets shown in examples. Replace every `YOUR_...` value.

## 5. Test the project locally first

**Why:** This proves the application and Docker image work before AWS is involved.

From this repository:

```powershell
mvn clean test
docker build -t springboot-aws-app:local .
docker run --rm -p 8080:8080 --name springboot-aws-app-local springboot-aws-app:local
```

In another terminal:

```powershell
curl.exe http://localhost:8080/hello
curl.exe http://localhost:8080/actuator/health
```

Expected responses include:

```text
Hello World from Spring Boot!
{"status":"UP"}
```

Stop the foreground container with `Ctrl+C`.

## 6. Select the AWS Region

**Why:** EC2 instances, ECR, key pairs, IAM-related selections, and network
resources must be created consistently. Key pairs are Region-specific.

1. Sign in to the AWS Console with the administrative identity, not root.
2. In the top-right Region selector, choose **Asia Pacific (Mumbai)
   `ap-south-1`**, or choose the Region closest to the users.
3. Keep that Region selected throughout this guide.

## 7. Create the EC2 key pair before either instance

**Why:** AWS places the public key on a new instance only when the key pair is
selected during launch. Creating a `.pem` later does **not** automatically attach
it to an existing instance.

1. Open **EC2 > Network & Security > Key Pairs**.
2. Click **Create key pair**.
3. Name: `springboot-aws-key`.
4. Key pair type: **RSA**.
5. Private key format: **`.pem`**.
6. Click **Create key pair**. The browser downloads
   `springboot-aws-key.pem` once.
7. Move it to a secure local directory outside the Git repository. Back it up in
   a secure password manager or encrypted storage.

For example, use:

```text
C:\Users\YOUR_WINDOWS_USER\.ssh\springboot-aws-key.pem
```

Restrict the key permissions in PowerShell:

```powershell
$Key = "$env:USERPROFILE\.ssh\springboot-aws-key.pem"
icacls $Key /inheritance:r
icacls $Key /grant:r "$($env:USERNAME):(R)"
```

Important:

- AWS cannot give you the same private key again.
- Do not email it, paste it into a document, or commit it to GitHub.
- This guide deliberately selects this key while launching **both** instances.
- If a key is lost, use EC2 Instance Connect or Systems Manager recovery where
  configured; a newly created key pair is not automatically associated.

[AWS EC2 key-pair documentation](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-key-pairs.html)

## 8. Create the Jenkins security group

**Why:** A security group is the EC2 firewall. Jenkins must not be open to
everyone.

1. Open **EC2 > Network & Security > Security Groups**.
2. Click **Create security group**.
3. Name: `jenkins-sg`.
4. Description: `Restricted access to Jenkins server`.
5. VPC: select the **default VPC**.
6. Add inbound rules:

| Type | Port | Source | Purpose |
|---|---:|---|---|
| SSH | 22 | `MY_PUBLIC_IP/32` | SSH only from your current computer |
| Custom TCP | 8080 | `MY_PUBLIC_IP/32` | Jenkins UI only from your current computer |

7. Keep the default outbound rule allowing all traffic.
8. Create the group.

If your internet provider changes your IP, edit these two rules with the new
`/32` address. Never use `0.0.0.0/0` for SSH or the Jenkins UI.

## 9. Create the application security group

**Why:** Users need the application port, but only Jenkins should SSH to the app
during deployment.

1. Create another security group in the same default VPC.
2. Name: `springboot-app-sg`.
3. Description: `Access to Spring Boot application`.
4. Add inbound rules:

| Type | Port | Source | Purpose |
|---|---:|---|---|
| Custom TCP | 8080 | `0.0.0.0/0` | Public demo application |
| SSH | 22 | security group `jenkins-sg` | Jenkins deploys over the private VPC |
| SSH | 22 | `MY_PUBLIC_IP/32` | Initial setup and troubleshooting |

5. Keep the default outbound rule.

Opening port 8080 publicly is acceptable for this learning demo, but a real
production service should use an Application Load Balancer with HTTPS and keep
the application instance private.

[Security-group rules documentation](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/security-group-rules.html)

## 10. Create the ECR repository

**Why:** ECR is AWS's private Docker-image registry. Jenkins pushes versioned
images there, and the app server pulls them.

1. Open **Amazon ECR > Private registry > Repositories**.
2. Click **Create repository**.
3. Visibility: **Private**.
4. Repository name: `springboot-aws-app`.
5. Tag immutability: **Mutable** for this tutorial.
6. Image scan settings: enable **Scan on push** if shown.
7. Encryption: keep **AES-256**.
8. Create the repository.
9. Copy its URI, for example:

```text
123456789012.dkr.ecr.ap-south-1.amazonaws.com/springboot-aws-app
```

Optional cost control: under the repository, create a lifecycle policy that keeps
only the most recent 10 tagged images.

[Creating a private ECR repository](https://docs.aws.amazon.com/AmazonECR/latest/userguide/repository-create.html)

## 11. Create the Jenkins EC2 IAM role

**Why:** The Jenkins instance needs permission to authenticate and push to ECR.
An EC2 role supplies temporary credentials automatically.

Beginner path using an AWS-managed policy:

1. Open **IAM > Roles > Create role**.
2. Trusted entity type: **AWS service**.
3. Use case: **EC2**.
4. Attach `AmazonEC2ContainerRegistryPowerUser`.
5. Role name: `JenkinsEcrPushRole`.
6. Create the role.

For a mature setup, replace the broad managed policy with a least-privilege
policy limited to this one ECR repository.

## 12. Create the application EC2 IAM role

**Why:** The application server needs permission to authenticate and pull from
ECR, but it must not push or delete images.

1. Open **IAM > Roles > Create role**.
2. Trusted entity: **AWS service**; use case: **EC2**.
3. Attach `AmazonEC2ContainerRegistryReadOnly`.
4. Role name: `AppEcrPullRole`.
5. Create the role.

[IAM roles for EC2](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/iam-roles-for-amazon-ec2.html)

## 13. Launch the dedicated Jenkins EC2 instance

**Why:** Jenkins builds can use significant CPU, memory, and disk, so Jenkins is
kept separate from the application.

1. Open **EC2 > Instances > Launch instances**.
2. Name: `jenkins-server`.
3. AMI: **Ubuntu Server 24.04 LTS**, 64-bit x86.
4. Instance type: `t3.medium` (recommended for Jenkins; check current price).
5. **Key pair: select `springboot-aws-key`**. Do not choose “Proceed without a
   key pair.”
6. Network: default VPC and a public subnet.
7. Auto-assign public IP: enabled.
8. Firewall: select existing security group `jenkins-sg`.
9. Storage: 30 GiB `gp3`.
10. Expand **Advanced details**.
11. IAM instance profile: `JenkinsEcrPushRole`.
12. Launch the instance.
13. Wait until instance state is **Running** and both status checks pass.

Verify the instance details page shows:

```text
Key pair assigned at launch: springboot-aws-key
IAM role: JenkinsEcrPushRole
Security group: jenkins-sg
```

## 14. Allocate and associate the Jenkins Elastic IP

**Why:** A normal EC2 public IP can change after stop/start. Jenkins needs a
stable address for administration.

1. Open **EC2 > Network & Security > Elastic IP addresses**.
2. Click **Allocate Elastic IP address**, then **Allocate**.
3. Select it and choose **Actions > Associate Elastic IP address**.
4. Resource type: **Instance**.
5. Instance: `jenkins-server`.
6. Associate it.
7. Record it as `JENKINS_ELASTIC_IP`.

## 15. Launch the application EC2 instance

**Why:** This server runs only Docker and the deployed application.

1. Open **EC2 > Instances > Launch instances**.
2. Name: `springboot-app-server`.
3. AMI: **Ubuntu Server 24.04 LTS**, 64-bit x86.
4. Instance type: `t3.micro` or `t3.small` (check current price and free-tier
   eligibility for your account).
5. **Key pair: select the same `springboot-aws-key`**.
6. Network: the **same default VPC** as Jenkins and a public subnet.
7. Auto-assign public IP: enabled.
8. Firewall: select existing `springboot-app-sg`.
9. Storage: 15 GiB `gp3`.
10. Advanced details > IAM instance profile: `AppEcrPullRole`.
11. Launch and wait for both status checks.
12. Record the instance's **Private IPv4 address** as `APP_PRIVATE_IP`.

Verify:

```text
Key pair assigned at launch: springboot-aws-key
IAM role: AppEcrPullRole
Security group: springboot-app-sg
```

Allocate and associate a second Elastic IP using the same procedure as Step 14.
Associate it with `springboot-app-server` and record it as `APP_ELASTIC_IP`.

## 16. Confirm SSH works before installing anything

**Why:** This immediately catches key-pair, username, path, IP, and firewall
problems.

From Windows PowerShell:

```powershell
$Key = "$env:USERPROFILE\.ssh\springboot-aws-key.pem"
ssh -i $Key ubuntu@YOUR_JENKINS_ELASTIC_IP
```

Type `yes` only after confirming the IP is the instance you created. Exit:

```bash
exit
```

Test the application server:

```powershell
ssh -i $Key ubuntu@YOUR_APP_ELASTIC_IP
```

The Ubuntu AMI username is `ubuntu`, not `root`.

Common SSH failures:

| Error | Check |
|---|---|
| `Permission denied (publickey)` | Correct `.pem`, `ubuntu` username, and key shown under “Key pair assigned at launch” |
| Timeout | Instance is running, correct Elastic IP, SSH rule has your current IP `/32` |
| `UNPROTECTED PRIVATE KEY FILE` | Fix local permissions with the `icacls` commands in Step 7 |

## 17. Install Docker and AWS CLI on the application server

**Why:** Docker runs the application, and AWS CLI obtains a temporary ECR login
using `AppEcrPullRole`.

SSH into the application server, then install Docker separately:

```bash
sudo apt update
sudo apt install -y docker.io curl unzip
sudo systemctl enable --now docker
sudo usermod -aG docker ubuntu
```

Install AWS CLI version 2 using the official AWS installer. The AMI selected in
this guide is x86-64:

```bash
mkdir -p /tmp/aws-cli-install
cd /tmp/aws-cli-install
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
unzip awscliv2.zip
sudo ./aws/install
aws --version
cd
exit
```

SSH back in so the new group membership applies:

```powershell
ssh -i $Key ubuntu@YOUR_APP_ELASTIC_IP
```

Verify:

```bash
docker --version
aws --version
docker run --rm hello-world
aws sts get-caller-identity
```

The final command should show an assumed role containing `AppEcrPullRole`, not a
personal access key.

## 18. Install Jenkins, Java, Maven, Docker, Git, and AWS CLI

**Why:** Jenkins needs Java 21 to run, while this project compiles and runs with
Java 17. Docker performs the Java 17 image build. Maven runs the repository test.

SSH into the Jenkins server:

```powershell
ssh -i $Key ubuntu@YOUR_JENKINS_ELASTIC_IP
```

Install prerequisites and Jenkins LTS:

```bash
sudo apt update
sudo apt install -y fontconfig openjdk-21-jre maven docker.io git wget curl unzip
mkdir -p /tmp/aws-cli-install
cd /tmp/aws-cli-install
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
unzip awscliv2.zip
sudo ./aws/install
cd
sudo install -d -m 0755 /etc/apt/keyrings
sudo wget -O /etc/apt/keyrings/jenkins-keyring.asc \
  https://pkg.jenkins.io/debian-stable/jenkins.io-2026.key
echo "deb [signed-by=/etc/apt/keyrings/jenkins-keyring.asc] https://pkg.jenkins.io/debian-stable binary/" \
  | sudo tee /etc/apt/sources.list.d/jenkins.list >/dev/null
sudo apt update
sudo apt install -y jenkins
sudo systemctl enable --now docker
sudo systemctl enable --now jenkins
sudo usermod -aG docker jenkins
sudo systemctl restart jenkins
```

Verify:

```bash
java -version
mvn -version
docker --version
aws --version
git --version
sudo systemctl --no-pager status jenkins
aws sts get-caller-identity
```

The AWS identity should contain `JenkinsEcrPushRole`.

Jenkins currently requires Java 21 or later; this is why Java 21 is installed on
the Jenkins server even though the application uses Java 17.

[Official Jenkins Ubuntu installation instructions](https://www.jenkins.io/doc/book/installing/linux/)

## 19. Complete the Jenkins setup wizard

**Why:** This creates the Jenkins administrator and installs pipeline support.

On the Jenkins SSH session, display the one-time password:

```bash
sudo cat /var/lib/jenkins/secrets/initialAdminPassword
```

1. Browse to `http://YOUR_JENKINS_ELASTIC_IP:8080`.
2. Paste the initial password.
3. Select **Install suggested plugins**.
4. Create a new Jenkins administrator user with a strong unique password.
5. Set Jenkins URL to `http://YOUR_JENKINS_ELASTIC_IP:8080/`.
6. Open **Manage Jenkins > Plugins > Available plugins**.
7. Install these if not already present:
   - Pipeline
   - Git
   - GitHub
   - Credentials Binding
   - SSH Agent
8. Restart Jenkins from the UI if requested.

Do not create an anonymous account and do not enable anonymous write access.

## 20. Give Jenkins the EC2 deployment SSH key

**Why:** Jenkins must SSH to the application instance during deployment. Jenkins
stores the private key as an encrypted credential instead of placing it in Git.

1. In Jenkins, go to **Manage Jenkins > Credentials**.
2. Select **System > Global credentials (unrestricted)**.
3. Click **Add Credentials**.
4. Kind: **SSH Username with private key**.
5. Scope: **Global**.
6. ID: `app-ec2-ssh`.
7. Username: `ubuntu`.
8. Private Key: **Enter directly > Add**.
9. On your computer, open `springboot-aws-key.pem` in a text editor and copy the
   complete content, including the BEGIN and END lines.
10. Paste it, save, and close the local file.

Never use `echo` to place this key on a server and never commit it.

[Jenkins credentials documentation](https://www.jenkins.io/doc/book/using/using-credentials/)

## 21. Prepare SSH host verification on Jenkins

**Why:** SSH must recognize the application server. The private IP is used so
deployment traffic stays inside the VPC.

From your computer, SSH to Jenkins. On Jenkins, run the following after replacing
the private IP:

```bash
sudo -u jenkins mkdir -p /var/lib/jenkins/.ssh
sudo -u jenkins chmod 700 /var/lib/jenkins/.ssh
ssh-keyscan -H YOUR_APP_PRIVATE_IP | sudo -u jenkins tee -a /var/lib/jenkins/.ssh/known_hosts
sudo -u jenkins chmod 600 /var/lib/jenkins/.ssh/known_hosts
sudo -u jenkins ssh-keygen -F YOUR_APP_PRIVATE_IP
```

Before accepting the scan in a sensitive environment, verify the app server host
fingerprint out of band. For this learning deployment, verify that the private IP
exactly matches the EC2 console.

## 22. Add the Jenkins pipeline file to the repository

**Why:** Pipeline-as-code makes the deployment repeatable and version-controlled.

Create a file named `Jenkinsfile` at the repository root with the content below.
Replace all four values in the `environment` block.

```groovy
pipeline {
    agent any

    options {
        disableConcurrentBuilds()
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    triggers {
        pollSCM('H/5 * * * *')
    }

    environment {
        AWS_REGION = 'ap-south-1'
        AWS_ACCOUNT_ID = 'YOUR_12_DIGIT_ACCOUNT_ID'
        ECR_REPOSITORY = 'springboot-aws-app'
        APP_HOST = 'YOUR_APP_PRIVATE_IP'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Test') {
            steps {
                sh 'mvn -B clean test'
            }
        }

        stage('Build Docker image') {
            steps {
                script {
                    env.IMAGE_TAG = sh(
                        script: 'git rev-parse --short HEAD',
                        returnStdout: true
                    ).trim()
                    env.ECR_REGISTRY =
                        "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
                    env.IMAGE_URI =
                        "${ECR_REGISTRY}/${ECR_REPOSITORY}:${IMAGE_TAG}"
                }
                sh 'docker build --pull -t "$IMAGE_URI" .'
            }
        }

        stage('Push to ECR') {
            steps {
                sh '''
                    aws ecr get-login-password --region "$AWS_REGION" \
                      | docker login --username AWS --password-stdin "$ECR_REGISTRY"
                    docker push "$IMAGE_URI"
                    docker logout "$ECR_REGISTRY"
                '''
            }
        }

        stage('Deploy to application EC2') {
            steps {
                sshagent(credentials: ['app-ec2-ssh']) {
                    sh '''
                        ssh -o BatchMode=yes ubuntu@"$APP_HOST" \
                          "AWS_REGION='$AWS_REGION' \
                           ECR_REGISTRY='$ECR_REGISTRY' \
                           IMAGE_URI='$IMAGE_URI' bash -s" <<'REMOTE'
                        set -euo pipefail
                        aws ecr get-login-password --region "$AWS_REGION" \
                          | docker login --username AWS --password-stdin "$ECR_REGISTRY"
                        docker pull "$IMAGE_URI"
                        docker rm -f springboot-aws-app 2>/dev/null || true
                        docker run -d \
                          --name springboot-aws-app \
                          --restart unless-stopped \
                          -p 8080:8080 \
                          "$IMAGE_URI"
                        for attempt in 1 2 3 4 5 6; do
                          if curl --fail --silent http://localhost:8080/actuator/health; then
                            exit 0
                          fi
                          sleep 5
                        done
                        docker logs springboot-aws-app
                        exit 1
REMOTE
                    '''
                }
            }
        }
    }

    post {
        always {
            sh 'docker image prune -f'
            deleteDir()
        }
    }
}
```

Commit and push from PowerShell:

```powershell
git add Jenkinsfile AWS-JENKINS-DOCKER-DEPLOYMENT-GUIDE.md
git commit -m "Add AWS Jenkins Docker deployment pipeline and guide"
git push origin main
```

Pipeline behavior:

1. Jenkins checks GitHub every five minutes.
2. Maven runs the test.
3. Docker builds an image tagged with the Git commit ID.
4. Jenkins authenticates with its EC2 role and pushes to ECR.
5. Jenkins connects to the app's private IP.
6. The app server pulls the exact image, replaces the old container, and performs
   a health check.

## 23. Configure GitHub access in Jenkins

**Why:** Jenkins needs to clone the repository.

For a public GitHub repository, no GitHub credential is required.

For a private repository:

1. In GitHub, create a fine-grained personal access token with read-only access to
   this one repository's contents.
2. In Jenkins credentials, add **Username with password**:
   - ID: `github-read-token`
   - Username: your GitHub username
   - Password: the token
3. Do not put the token in the repository URL or Jenkinsfile.

An SSH deploy key or GitHub App is preferable for a long-lived production setup.

## 24. Create the Jenkins Pipeline job

**Why:** The job connects the Jenkins UI to the version-controlled Jenkinsfile.

1. Jenkins dashboard > **New Item**.
2. Name: `springboot-aws-app`.
3. Choose **Pipeline**, then **OK**.
4. Under **Pipeline**:
   - Definition: **Pipeline script from SCM**
   - SCM: **Git**
   - Repository URL: your GitHub repository URL
   - Credentials: none for public repo, or `github-read-token` for private repo
   - Branch specifier: `*/main`
   - Script path: `Jenkinsfile`
   - Lightweight checkout: enabled
5. Save.
6. Click **Build Now** for the first build.
7. Open **Build History > Console Output** and watch every stage.

The `pollSCM` trigger makes later GitHub pushes start a build within about five
minutes. This avoids opening Jenkins to all internet traffic for a webhook.

## 25. Verify the deployment

**Why:** A green pipeline is useful only if the public service is healthy.

From PowerShell:

```powershell
curl.exe http://YOUR_APP_ELASTIC_IP:8080/
curl.exe http://YOUR_APP_ELASTIC_IP:8080/hello
curl.exe http://YOUR_APP_ELASTIC_IP:8080/actuator/health
```

Also browse to:

```text
http://YOUR_APP_ELASTIC_IP:8080/hello
```

On the app server:

```bash
docker ps
docker logs --tail 100 springboot-aws-app
docker inspect --format='{{.Config.Image}}' springboot-aws-app
```

In **ECR > springboot-aws-app > Images**, confirm that an image tagged with the
Git commit ID exists.

## 26. Prove CI/CD works

**Why:** This validates the complete GitHub-to-AWS path.

1. Change the response text in:
   `src/main/java/com/example/springbootawsapp/HelloController.java`.
2. Update its matching test expectation if changing `/hello`.
3. Commit and push:

```powershell
git add src
git commit -m "Change hello response"
git push origin main
```

4. Wait up to five minutes.
5. Confirm Jenkins automatically starts a build.
6. Refresh `http://YOUR_APP_ELASTIC_IP:8080/hello`.

## 27. Optional near-real-time GitHub webhook

Polling is the recommended first working setup here. A webhook requires GitHub to
reach Jenkins, which conflicts with restricting Jenkins port 8080 to your IP.

Do **not** simply open Jenkins port 8080 to `0.0.0.0/0`. For a production-quality
webhook:

1. Put Jenkins behind an Application Load Balancer or reverse proxy.
2. Use a domain name and an ACM TLS certificate.
3. Expose only HTTPS port 443.
4. Keep authentication enabled and Jenkins updated.
5. Configure the GitHub webhook URL:
   `https://jenkins.example.com/github-webhook/`.
6. Select the `application/json` content type and push events.
7. In the Jenkins job, enable **GitHub hook trigger for GITScm polling**.
8. Remove `pollSCM` from the Jenkinsfile after webhook deliveries are verified.

[GitHub webhook overview](https://docs.github.com/en/webhooks/about-webhooks)

## 28. Routine operations

Check Jenkins:

```bash
sudo systemctl status jenkins
sudo journalctl -u jenkins --since "30 minutes ago"
```

Check the application:

```bash
docker ps
docker logs --tail 200 springboot-aws-app
curl --fail http://localhost:8080/actuator/health
```

Restart:

```bash
sudo systemctl restart jenkins
docker restart springboot-aws-app
```

Disk usage and cleanup:

```bash
df -h
docker system df
docker image prune -f
```

Do not run `docker system prune --volumes` casually; it can delete needed data.

## 29. Roll back to an earlier image

**Why:** Commit-based Docker tags let you restore a previously successful build.

1. In ECR, identify the previous healthy image tag, for example `a1b2c3d`.
2. SSH to the app instance.
3. Set the real values and deploy:

```bash
AWS_REGION=ap-south-1
AWS_ACCOUNT_ID=YOUR_12_DIGIT_ACCOUNT_ID
OLD_TAG=a1b2c3d
ECR_REGISTRY="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
IMAGE_URI="${ECR_REGISTRY}/springboot-aws-app:${OLD_TAG}"

aws ecr get-login-password --region "$AWS_REGION" \
  | docker login --username AWS --password-stdin "$ECR_REGISTRY"
docker pull "$IMAGE_URI"
docker rm -f springboot-aws-app
docker run -d --name springboot-aws-app --restart unless-stopped \
  -p 8080:8080 "$IMAGE_URI"
curl --fail http://localhost:8080/actuator/health
```

## 30. Troubleshooting

### Jenkins cannot run Docker

```bash
id jenkins
ls -l /var/run/docker.sock
sudo usermod -aG docker jenkins
sudo systemctl restart jenkins
```

The `jenkins` user must appear in the `docker` group.

### ECR says `no basic auth credentials` or `AccessDenied`

```bash
aws sts get-caller-identity
aws ecr get-login-password --region ap-south-1 >/dev/null
```

Check the correct IAM role is attached, the Region matches ECR, and Jenkins uses
the push role while the app uses the read-only role.

### Jenkins SSH deployment times out

- Both instances must be in the same VPC.
- `APP_HOST` must be the app **private** IPv4 address.
- `springboot-app-sg` port 22 must allow source `jenkins-sg`.
- The app instance must be running.

### Jenkins reports host-key verification failure

Repeat Step 21 with the current private IP. If the instance was replaced, verify
the new fingerprint before removing its old known-host entry.

### Container starts but the page does not open

```bash
docker ps
docker logs springboot-aws-app
curl -v http://localhost:8080/actuator/health
sudo ss -lntp | grep 8080
```

Confirm `springboot-app-sg` allows inbound TCP 8080.

### Jenkins UI does not open

```bash
sudo systemctl status jenkins
sudo journalctl -u jenkins -n 100 --no-pager
sudo ss -lntp | grep 8080
```

Also update `jenkins-sg` if your home public IP changed.

### Build runs repeatedly without a new commit

Confirm the job has only one trigger. Keep `pollSCM` in the Jenkinsfile and do
not also add a separate SCM polling schedule in the UI.

## 31. Backups, patching, and production improvements

After the learning deployment works:

1. Take encrypted EBS snapshots or back up `/var/lib/jenkins`.
2. Update Ubuntu, Jenkins, Docker, plugins, and project dependencies regularly.
3. Add an ECR lifecycle policy.
4. Use Route 53, an Application Load Balancer, ACM HTTPS, and port 443.
5. Remove public SSH and use AWS Systems Manager Session Manager.
6. Put the app EC2 instance in a private subnet.
7. Replace broad ECR managed permissions with repository-specific policies.
8. Add CloudWatch logs, metrics, alarms, and an application uptime alarm.
9. Add a deployment rollback stage.
10. Consider ECS/Fargate for application orchestration after learning EC2.

## 32. Shut down or delete resources

**Why:** AWS resources can continue charging when the tutorial is not being used.

For a temporary pause:

1. Stop both EC2 instances.
2. Remember that EBS, ECR storage, public IPv4, and Elastic IP charges may remain.

For permanent cleanup, in this order:

1. Terminate `jenkins-server` and `springboot-app-server`.
2. Deregister/delete unneeded ECR images, then delete the ECR repository.
3. Release both Elastic IP addresses.
4. Delete `jenkins-sg` and `springboot-app-sg`.
5. Delete `JenkinsEcrPushRole` and `AppEcrPullRole`.
6. Delete the EC2 key pair in AWS.
7. Securely delete the local `.pem` only after no remaining instance needs it.
8. Check **Billing and Cost Management** for remaining resources/charges.

## 33. Official command references

- [Push an image to Amazon ECR](https://docs.aws.amazon.com/AmazonECR/latest/userguide/docker-push-ecr-image.html)
- [ECR authentication with `get-login-password`](https://docs.aws.amazon.com/cli/latest/reference/ecr/get-login-password.html)
- [ECR push IAM permissions](https://docs.aws.amazon.com/AmazonECR/latest/userguide/image-push-iam.html)
- [Connect to an EC2 Linux instance using SSH](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/connect-linux-inst-ssh.html)
- [Jenkins Pipeline syntax](https://www.jenkins.io/doc/book/pipeline/syntax/)
- [Using Docker with Jenkins Pipeline](https://www.jenkins.io/doc/book/pipeline/docker/)

## Final checklist

- [ ] Root-user MFA enabled; daily admin identity created.
- [ ] Budget alert created.
- [ ] One AWS Region selected everywhere.
- [ ] `.pem` created before instances and stored outside Git.
- [ ] Both EC2 instances show `springboot-aws-key` as the launch key.
- [ ] Jenkins ports 22 and 8080 are limited to your `/32` IP.
- [ ] App SSH allows your IP and `jenkins-sg`; app port 8080 is reachable.
- [ ] Jenkins and app instances have the correct IAM roles.
- [ ] `aws sts get-caller-identity` shows roles, not personal credentials.
- [ ] Jenkins SSH credential ID is exactly `app-ec2-ssh`.
- [ ] Jenkinsfile placeholders are all replaced.
- [ ] Jenkins test, build, ECR push, deploy, and health-check stages are green.
- [ ] `/hello` and `/actuator/health` work through the app Elastic IP.
- [ ] A second Git push automatically deploys.
