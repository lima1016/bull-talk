pipeline {
    agent any

    environment {
        DOCKER_IMAGE = 'bull-talk-docker-image'  // Docker 이미지 이름
        DOCKER_TAG = "latest"  // 이미지 태그
    }

    stages {
        stage('Checkout') {
            steps {
                // Git에서 코드 가져오기
                checkout scm
            }
        }

        stage('Build Docker Image') {
            steps {
                script {
                    // Docker 이미지 빌드
                    sh "docker build -t ${DOCKER_IMAGE}:${DOCKER_TAG} ."
                }
            }
        }
        stage('Deploy') {
            steps {
                script {
                    // 배포 관련 스크립트 (예: 새로운 Docker 컨테이너 실행)
                    sh "docker run -d --name ${DOCKER_IMAGE} ${DOCKER_IMAGE}:${DOCKER_TAG}"
                }
            }
        }
    }
}
