pipeline {
    agent any

    tools {
        maven 'Maven' 
        jdk 'JDK17'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build') {
            steps {
                sh 'mvn clean test package'
            }
        }

        stage('Docker Build') {
            steps {
                sh 'docker build -t springboot-aws-app:latest .' 
            }
        }
    }
}
