def call(Map config = [:]){
    withSonarQubeEnv(config.installationName){
        bat """
        cd client
        sonar-scanner -Dsonar.projectKey=inotebook-frontend -Dsonar.projectName=inotebook-frontend -Dsonar.sources=.
        """
    }

    withCredentials([string(credentialsId: 'dockerhub', variable: 'dockerhub_password')]) {
        sh """#!/bin/bash
        cd client
        docker build -t ${config.imageName} .

        docker logout
        docker login -u hirentimbadiya -p ${dockerhub_password}

        docker push ${config.imageName}
        """
    }

     withKubeConfig(caCertificate: '', clusterName: 'gke_gcp-learning-417116_us-central1-c_main-cluster', contextName: '', credentialsId: 'k8s-cred', namespace: 'jenkins', restrictKubeConfigAccess: true, serverUrl: 'https://35.226.254.176') {
        def chartDir = "../helmChart-Client"
        def chart = libraryResource "helmChart-Client/Chart.yaml"
        def deployment = libraryResource "helmChart-Client/templates/deployment.yaml"
        def service = libraryResource "helmChart-Client/templates/service.yaml"
        

        writeFile file: "./helmChart-Client/Chart.yaml", text: chart
        writeFile file: "./helmChart-Client/templates/deployment.yaml", text: deployment
        writeFile file: "./helmChart-Client/templates/service.yaml", text: service

        sh """#!/bin/bash
        cd client
        DIGEST=\$(docker image inspect --format='{{index .RepoDigests 0}}' ${config.imageName} | awk -F@ '{print \$2}') 
        echo \$DIGEST

        helm upgrade --install --namespace jenkins ${config.releaseName} ${chartDir} --set image.digest=\$DIGEST --values values.yaml
        """
    }
}