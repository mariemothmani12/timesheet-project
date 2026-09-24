pipeline {
    agent any
    environment {
        DOCKERHUB_CREDENTIALS = credentials('dockerhub-creds')
        SONAR_TOKEN = credentials('sonar-token')
    }
    stages {
        stage('GIT') {
            steps {
                git branch: 'devsecops', url: 'https://github.com/mariemothmani12/timesheet-project.git'
            }
        }
        stage('CLEAN') {
            steps {
                sh 'mvn clean'
            }
        }
        stage('COMPILE') {
            steps {
                sh 'mvn compile'
            }
        }
        stage('JUNIT/MOCKITO TESTS') {
            steps {
                sh 'mvn test'
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: '**/target/surefire-reports/*.xml'
                }
            }
        }
        stage('PRE-COMMIT HOOKS') {
            steps {
                catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                    sh 'talisman --scan'
                }
            }
        }
        stage('OWASP DEP-CHECK') {
            steps {
                catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                    sh 'mvn org.owasp:dependency-check-maven:12.1.0:check -DnvdApiKey=3C2C450B-8BCF-4EE6-9B18-6EA032A776D1 -DfailBuildOnCVSS=7'
                }
            }
        }
        stage('SONARQUBE ANALYSIS') {
            steps {
                sh """
                    mvn sonar:sonar \
                    -Dsonar.host.url=http://localhost:9000 \
                    -Dsonar.login=${SONAR_TOKEN} \
                    -Dsonar.projectKey=timesheet-devops \
                    -Dsonar.projectName=timesheet-devops \
                    -Dsonar.java.binaries=target/classes
                """
            }
        }
        stage('PACKAGE') {
            steps {
                sh 'mvn package -DskipTests'
            }
        }
        stage('NEXUS DEPLOY') {
            steps {
                sh 'mvn deploy -DskipTests'
            }
        }
        stage('DOCKER BUILD') {
            steps {
                sh 'docker build -t mariemothmani/timesheet:latest .'
            }
        }
        stage('DOCKER PUSH') {
            steps {
                sh 'echo ${DOCKERHUB_CREDENTIALS_PSW} | docker login -u ${DOCKERHUB_CREDENTIALS_USR} --password-stdin'
                sh 'docker push mariemothmani/timesheet:latest'
            }
        }
        stage('DOCKER COMPOSE') {
            steps {
                sh 'docker-compose up -d'
            }
        }
        stage('KUBERNETES DEPLOY') {
            steps {
                sh 'kubectl apply -f k8s-deployment.yml'
                sh 'kubectl apply -f k8s-service.yml'
            }
        }
        stage('KUBERNETES VERIFY') {
            steps {
                sh 'kubectl get pods -n devsecops'
                sh 'kubectl get svc -n devsecops'
            }
        }
        stage('MONITORING') {
            steps {
                echo 'Prometheus: http://localhost:9090'
                echo 'Grafana: http://localhost:3000'
            }
        }
    }
    post {
        success {
            echo 'Pipeline completed successfully!'
        }
        failure {
            echo 'Pipeline failed!'
        }
    }
}
