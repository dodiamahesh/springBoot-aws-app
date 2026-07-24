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