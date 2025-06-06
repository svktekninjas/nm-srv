# CI Implementation Guide

This guide provides detailed instructions for implementing Continuous Integration (CI) for a Spring Boot microservice application using GitHub Actions. It includes comprehensive documentation on requirements, setup, configuration, and best practices.

## Table of Contents

1. [Project Assessment](#1-project-assessment)
2. [CI Pipeline Requirements](#2-ci-pipeline-requirements)
3. [Implementation Components](#3-implementation-components)
4. [Environment Configuration](#4-environment-configuration)
5. [Security Scanning](#5-security-scanning)
6. [Amazon ECR Integration](#6-amazon-ecr-integration)
7. [Workflow Implementation Details](#7-workflow-implementation-details)
8. [Configuration and Security](#8-configuration-and-security)

---

## 1. Project Assessment

- **Project Type:** Spring Boot Java application (Spring Cloud Netflix Eureka Server)
- **Build System:** Maven
- **Java Version:** 17
- **Repository Structure:** Single module Maven project

The project is a naming service microservice based on Spring Boot, which serves as a service discovery server (Eureka Server) in a microservices architecture.

## 2. CI Pipeline Requirements

### Functional Requirements

1. **Maven Build and Test**
   - Compile and build the Java application
   - Run unit and integration tests
   - Generate test reports

2. **Code Quality Analysis**
   - Integrate with SonarQube/SonarCloud for code quality scanning
   - Perform static code analysis
   - Generate code quality reports
   - Define quality gates for build pass/fail

3. **Docker Image Building**
   - Build Docker image for the Spring Boot application
   - Tag image with proper version information
   - Scan the Docker image for vulnerabilities using Trivy
   - Push image to Amazon ECR repository

4. **Security Scanning**
   - Scan dependencies for vulnerabilities
   - Enforce security best practices
   - Scan Docker images for security issues

### Non-Functional Requirements

1. **Performance**
   - Optimize CI pipeline for faster builds
   - Use appropriate caching mechanisms for Maven dependencies
   - Minimize build time while maintaining quality checks

2. **Reliability**
   - Ensure consistent build results
   - Properly handle failures and provide clear error messages
   - Implement retry mechanisms for transient failures

3. **Security**
   - Securely manage AWS credentials using GitHub secrets
   - Implement least privilege principles for AWS access
   - Protect sensitive information in build logs

4. **Maintainability**
   - Document CI workflow configuration
   - Use parameterized workflows where possible
   - Keep workflow files organized and well-commented

5. **Scalability**
   - Design workflow to handle future expansion of the codebase
   - Support multi-module projects if the application grows
   - Allow for easy integration of additional quality tools

## 3. Implementation Components

To implement the CI workflow, the following files and changes are required:

1. **GitHub Workflow File**
   - Create `.github/workflows/ci.yml` to define the CI pipeline

2. **Docker Configuration**
   - Create `naming-server-service/Dockerfile` for containerizing the Spring Boot application
   - Update `application.properties` for containerized environment if needed

3. **SonarQube Configuration**
   - Add SonarQube plugin to `naming-server-service/pom.xml`
   - Create `sonar-project.properties` file if needed

4. **Build Script Enhancements**
   - Enhance `naming-server-service/pom.xml` with necessary plugins for quality checks
   - Configure testing and reporting in Maven build

5. **Security Configuration Files**
   - Create `naming-server-service/dependency-check-suppressions.xml` for managing false positives
   - Create `naming-server-service/spotbugs-security-include.xml` and `naming-server-service/spotbugs-security-exclude.xml` for security checks

6. **Environment Variables Configuration**
   - Create `.github/ENV/ci-variables.env` file for centralized configuration

## 4. Environment Configuration

### Required Variables and Secrets

#### GitHub Secrets

These values should be stored as GitHub repository secrets for security:

1. **AWS Credentials**
   - `AWS_ACCESS_KEY_ID`: Access key for AWS authentication
   - `AWS_SECRET_ACCESS_KEY`: Secret key for AWS authentication
   - `AWS_REGION`: AWS region where resources are deployed (default: us-east-1)

2. **SonarCloud/SonarQube**
   - `SONAR_TOKEN`: Authentication token for SonarCloud
   - `SONAR_HOST_URL`: URL of SonarQube server (if self-hosted)
   - `SONAR_PROJECT_KEY`: Project key in SonarCloud/SonarQube

3. **Repository-specific**
   - `ECR_REPOSITORY_URI`: Full URI of the ECR repository (e.g., 940482440184.dkr.ecr.us-east-1.amazonaws.com/test)

#### Environment Variables

These can be defined in the workflow file directly or in a dedicated env file:

1. **Build Configuration**
   - `JAVA_VERSION`: Java version to use (17 for this project)
   - `MAVEN_CLI_OPTS`: Additional Maven CLI options
   - `MAVEN_GOALS`: Maven goals to execute

2. **Docker Configuration**
   - `DOCKER_IMAGE_NAME`: Name for the Docker image
   - `DOCKER_FILE_PATH`: Path to the Dockerfile

3. **Workflow Control**
   - `ENABLE_SONAR_SCAN`: Flag to enable/disable SonarQube scanning
   - `ENABLE_DEPENDENCY_CHECK`: Flag to enable/disable dependency vulnerability scanning
   - `ENABLE_DOCKER_SCAN`: Flag to enable/disable Docker image scanning
   - `FAIL_ON_VULNERABILITY`: Whether to fail the build on finding vulnerabilities

4. **Versioning**
   - `VERSION_STRATEGY`: How to version the Docker image (e.g., semantic, git-sha, etc.)
   - `MAIN_BRANCH`: Name of the main branch (usually 'main' or 'master')

### Environment Variables Management

We've implemented a centralized approach to environment variable management using a dedicated env file:

**File Structure:** `.github/ENV/ci-variables.env`

```
# Build Configuration
JAVA_VERSION=17                 # Java version to use
MAVEN_CLI_OPTS=--batch-mode --errors --fail-at-end --show-version
MAVEN_GOALS=clean verify        # Maven goals to execute

# Docker Configuration
DOCKER_IMAGE_NAME=naming-server-service
DOCKER_FILE_PATH=./naming-server-service/Dockerfile

# Feature Flags
ENABLE_SONAR_SCAN=true
ENABLE_DEPENDENCY_CHECK=true
ENABLE_DOCKER_SCAN=true
FAIL_ON_VULNERABILITY=true

# Versioning
VERSION_STRATEGY=git-sha       # Options: git-sha, latest
MAIN_BRANCH=main
```

**Implementation Strategy:**
1. **Separation of Configuration**: Variables are stored in a separate file, allowing for easier maintenance
2. **Comments and Documentation**: Each variable includes a comment explaining its purpose
3. **Default Values**: The workflow provides sensible defaults when variables are not specified
4. **Environment Overrides**: Support for environment-specific variables (dev, test, prod)

**Loading Mechanism:**
```yaml
- name: Load Environment Variables
  id: set-env
  run: |
    ENV_FILE=".github/ENV/ci-variables.env"
    if [ -f "$ENV_FILE" ]; then
      echo "Loading variables from $ENV_FILE"
      # Set default values first
      echo "java_version=17" >> $GITHUB_OUTPUT
      echo "maven_cli_opts=--batch-mode --errors --fail-at-end --show-version" >> $GITHUB_OUTPUT
      echo "maven_goals=clean verify" >> $GITHUB_OUTPUT
      # Then override with values from env file
      while IFS= read -r line || [[ -n "$line" ]]; do
        # Skip comments and empty lines
        [[ "$line" =~ ^#.*$ || -z "$line" ]] && continue
        # Parse key=value pairs
        if [[ "$line" =~ ^([^=]+)=(.*)$ ]]; then
          key="${BASH_REMATCH[1]}"
          value="${BASH_REMATCH[2]}"
          # Convert to lowercase for GitHub outputs
          output_key=$(echo "$key" | tr '[:upper:]' '[:lower:]')
          echo "$output_key=$value" >> $GITHUB_OUTPUT
        fi
      done < "$ENV_FILE"
    else
      echo "Environment file not found, using defaults"
      # Set all default values
      echo "java_version=17" >> $GITHUB_OUTPUT
      echo "maven_cli_opts=--batch-mode --errors --fail-at-end --show-version" >> $GITHUB_OUTPUT
      echo "maven_goals=clean verify" >> $GITHUB_OUTPUT
      # ...and other defaults
    fi
```

## 5. Security Scanning

Security scanning is implemented at multiple levels:

### 1. Dependency Scanning with OWASP Dependency Check

The OWASP Dependency Check tool is used to scan Java dependencies for known vulnerabilities. It is configured in the `pom.xml` file of the project:

```xml
<plugin>
    <groupId>org.owasp</groupId>
    <artifactId>dependency-check-maven</artifactId>
    <version>7.4.4</version>
    <configuration>
        <failBuildOnCVSS>8</failBuildOnCVSS>
        <suppressionFiles>
            <suppressionFile>dependency-check-suppressions.xml</suppressionFile>
        </suppressionFiles>
        <formats>
            <format>HTML</format>
            <format>JSON</format>
        </formats>
    </configuration>
</plugin>
```

**Integration in the CI pipeline:**
```yaml
- name: Run OWASP Dependency Check
  if: needs.load-env.outputs.enable_dependency_check == 'true'
  run: |
    ./mvnw org.owasp:dependency-check-maven:check \
      -DfailBuildOnCVSS=8 \
      -DsuppressionFiles=dependency-check-suppressions.xml
  working-directory: naming-server-service
```

### 2. Static Code Analysis with SpotBugs

SpotBugs with a security focus is configured to detect security issues in the code:

```xml
<plugin>
    <groupId>com.github.spotbugs</groupId>
    <artifactId>spotbugs-maven-plugin</artifactId>
    <version>4.7.3.4</version>
    <configuration>
        <includeFilterFile>spotbugs-security-include.xml</includeFilterFile>
        <excludeFilterFile>spotbugs-security-exclude.xml</excludeFilterFile>
        <effort>Max</effort>
        <threshold>Low</threshold>
        <failOnError>true</failOnError>
    </configuration>
</plugin>
```

**Integration in the CI pipeline:**
```yaml
- name: Run SpotBugs Security Analysis
  run: |
    ./mvnw com.github.spotbugs:spotbugs-maven-plugin:check
  working-directory: naming-server-service
```

### 3. Container Scanning with Trivy

Trivy is used to scan Docker images for vulnerabilities:

```yaml
- name: Run Trivy Scan on Docker Image
  if: needs.load-env.outputs.enable_docker_scan == 'true'
  uses: aquasecurity/trivy-action@master
  with:
    image-ref: ${{ env.FULL_IMAGE_NAME }}
    format: 'table'
    exit-code: ${{ needs.load-env.outputs.fail_on_vulnerability == 'true' && '1' || '0' }}
    ignore-unfixed: true
    vuln-type: 'os,library'
    severity: 'CRITICAL,HIGH'
```

### Security Configuration Files

#### OWASP Dependency Check Suppressions

File: `naming-server-service/dependency-check-suppressions.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<suppressions xmlns="https://jeremylong.github.io/DependencyCheck/dependency-suppression.1.3.xsd">
   <!-- Example suppression for false positives or vulnerabilities that can't be fixed yet -->
   <!--
   <suppress>
      <notes>
         This suppresses CVE-XXXX-YYYY in the specified library which is a false positive
         because we're not using the vulnerable functionality or have mitigated the risk.
      </notes>
      <cve>CVE-XXXX-YYYY</cve>
   </suppress>
   -->
</suppressions>
```

#### SpotBugs Security Include Filter

File: `naming-server-service/spotbugs-security-include.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<FindBugsFilter
    xmlns="https://github.com/spotbugs/filter/3.0.0" 
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="https://github.com/spotbugs/filter/3.0.0 https://raw.githubusercontent.com/spotbugs/spotbugs/3.1.0/spotbugs/etc/findbugsfilter.xsd">

    <!-- Include all security-related bugs -->
    <Match>
        <Bug category="SECURITY"/>
    </Match>
    
    <!-- Include critical and high-priority bugs -->
    <Match>
        <Rank value="1"/>
    </Match>
    <Match>
        <Rank value="2"/>
    </Match>
    
    <!-- Include specific bug patterns related to security -->
    <Match>
        <Bug pattern="SQL_INJECTION"/>
    </Match>
    <Match>
        <Bug pattern="COMMAND_INJECTION"/>
    </Match>
    <Match>
        <Bug pattern="PATH_TRAVERSAL_IN"/>
    </Match>
    <Match>
        <Bug pattern="PATH_TRAVERSAL_OUT"/>
    </Match>
    <Match>
        <Bug pattern="WEAK_FILENAMEUTILS"/>
    </Match>
    <Match>
        <Bug pattern="SERVLET_PARAMETER"/>
    </Match>
    <Match>
        <Bug pattern="SERVLET_QUERY_STRING"/>
    </Match>
    <Match>
        <Bug pattern="SERVLET_HEADER"/>
    </Match>
    <Match>
        <Bug pattern="SERVLET_HEADER_REFERER"/>
    </Match>
    <Match>
        <Bug pattern="SERVLET_HEADER_USER_AGENT"/>
    </Match>
    <Match>
        <Bug pattern="COOKIE_USAGE"/>
    </Match>
    <Match>
        <Bug pattern="XSS_REQUEST_PARAMETER_TO_JSP_WRITER"/>
    </Match>
    <Match>
        <Bug pattern="XSS_REQUEST_PARAMETER_TO_SERVLET_WRITER"/>
    </Match>
    <Match>
        <Bug pattern="WEAK_MESSAGE_DIGEST_MD5"/>
    </Match>
    <Match>
        <Bug pattern="WEAK_MESSAGE_DIGEST_SHA1"/>
    </Match>
    <Match>
        <Bug pattern="WEAK_HOSTNAME_VERIFIER"/>
    </Match>
    <Match>
        <Bug pattern="WEAK_TRUST_MANAGER"/>
    </Match>
</FindBugsFilter>
```

#### SpotBugs Security Exclude Filter

File: `naming-server-service/spotbugs-security-exclude.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<FindBugsFilter
    xmlns="https://github.com/spotbugs/filter/3.0.0" 
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="https://github.com/spotbugs/filter/3.0.0 https://raw.githubusercontent.com/spotbugs/spotbugs/3.1.0/spotbugs/etc/findbugsfilter.xsd">

    <!-- Exclude test classes -->
    <Match>
        <Class name="~.*\.*Test"/>
    </Match>
    <Match>
        <Class name="~.*\.*Tests"/>
    </Match>
    <Match>
        <Class name="~.*\.*TestCase"/>
    </Match>
    
    <!-- Exclude test resources -->
    <Match>
        <Package name="~.*\.test.*"/>
    </Match>
    
    <!-- Exclude generated code -->
    <Match>
        <Package name="~.*\.generated.*"/>
    </Match>
    <Match>
        <Package name="~.*\.dto.*"/>
    </Match>
    <Match>
        <Package name="~.*\.model.*"/>
    </Match>
</FindBugsFilter>
```

## 6. Amazon ECR Integration

### What is Amazon ECR?

Amazon Elastic Container Registry (ECR) is a fully managed container registry service provided by AWS that makes it easy to store, manage, and deploy Docker container images. It integrates seamlessly with other AWS services and provides high availability and scalability.

### ECR Setup Requirements

To use Amazon ECR in your CI/CD pipeline, you need:

1. **AWS Account and ECR Repository**
   - An existing ECR repository or permissions to create one
   - The full URI of your ECR repository (e.g., `940482440184.dkr.ecr.us-east-1.amazonaws.com/test`)

2. **AWS Credentials**
   - IAM user with appropriate permissions:
     - ecr:GetAuthorizationToken
     - ecr:BatchCheckLayerAvailability
     - ecr:GetDownloadUrlForLayer
     - ecr:BatchGetImage
     - ecr:InitiateLayerUpload
     - ecr:UploadLayerPart
     - ecr:CompleteLayerUpload
     - ecr:PutImage

3. **GitHub Secrets Configuration**
   - `AWS_ACCESS_KEY_ID`: Your AWS access key
   - `AWS_SECRET_ACCESS_KEY`: Your AWS secret key
   - `AWS_REGION`: The AWS region where your ECR repository exists
   - `ECR_REPOSITORY_URI`: The full URI of your ECR repository

### Authentication with AWS

The workflow authenticates with AWS using the official AWS GitHub Actions:

```yaml
- name: Configure AWS credentials
  uses: aws-actions/configure-aws-credentials@v2
  with:
    aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
    aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
    aws-region: ${{ needs.load-env.outputs.aws_region }}

- name: Login to Amazon ECR
  id: login-ecr
  uses: aws-actions/amazon-ecr-login@v1
```

### Docker Image Publishing Workflow

The Docker image publishing process follows these steps:

1. **Download Built Artifact**
   ```yaml
   - name: Download JAR artifact
     uses: actions/download-artifact@v3
     with:
       name: application-jar
       path: naming-server-service/target/
   ```

2. **Generate Docker Image Tag**
   ```yaml
   - name: Generate Docker Image Tag
     id: docker-tag
     run: |
       if [[ "${{ needs.load-env.outputs.version_strategy }}" == "git-sha" ]]; then
         SHORT_SHA=$(git rev-parse --short HEAD)
         echo "IMAGE_TAG=${SHORT_SHA}" >> $GITHUB_ENV
       else
         echo "IMAGE_TAG=latest" >> $GITHUB_ENV
       fi
       echo "FULL_IMAGE_NAME=${{ needs.load-env.outputs.ecr_repository_uri }}:${IMAGE_TAG}" >> $GITHUB_ENV
   ```

3. **Build Docker Image**
   ```yaml
   - name: Build Docker Image for ECR
     run: |
       docker build -t ${{ env.FULL_IMAGE_NAME }} -f ${{ needs.load-env.outputs.docker_file_path }} .
       docker images
   ```

4. **Scan Docker Image** (Optional)
   ```yaml
   - name: Run Trivy Scan on Docker Image
     if: needs.load-env.outputs.enable_docker_scan == 'true'
     uses: aquasecurity/trivy-action@master
     with:
       image-ref: ${{ env.FULL_IMAGE_NAME }}
       format: 'table'
       exit-code: ${{ needs.load-env.outputs.fail_on_vulnerability == 'true' && '1' || '0' }}
       ignore-unfixed: true
       vuln-type: 'os,library'
       severity: 'CRITICAL,HIGH'
   ```

5. **Push to ECR**
   ```yaml
   - name: Push the Docker Image
     run: |
       docker push ${{ env.FULL_IMAGE_NAME }}
   ```

### Dockerfile for Spring Boot Application

File: `naming-server-service/Dockerfile`

```Dockerfile
FROM eclipse-temurin:17-jre-alpine

# Set working directory
WORKDIR /app

# Add a non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy the JAR file
COPY --chown=spring:spring target/*.jar app.jar

# Set JVM options
ENV JAVA_OPTS="-Xms512m -Xmx512m"

# Expose the application port
EXPOSE 8761

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

## 7. Workflow Implementation Details

### CI YAML Structure

The GitHub Actions workflow file (`.github/workflows/ci.yml`) defines a CI/CD pipeline with three main jobs:

1. **Load Environment Variables Job**
   - Reads variables from `.github/ENV/ci-variables.env`
   - Sets default values for missing variables
   - Exports variables as job outputs for other jobs

2. **Build Job**
   - Compiles and tests the Java application
   - Runs SonarQube analysis (conditional)
   - Performs dependency vulnerability scanning (conditional)
   - Generates test and analysis reports
   - Archives the built JAR file as an artifact

3. **Docker Build Job**
   - Downloads the JAR artifact from the build job
   - Authenticates with AWS ECR
   - Builds the Docker image
   - Scans the image for vulnerabilities (conditional)
   - Pushes the image to Amazon ECR

### Complete CI Workflow File

File: `.github/workflows/ci.yml`

```yaml
name: Java CI with Maven and Docker

on:
  push:
    branches: [ "main", "ci_gh_action" ]
    paths-ignore:
      - '**/*.md'
      - '.github/ENV/**'
  pull_request:
    branches: [ "main" ]
    paths-ignore:
      - '**/*.md'
      - '.github/ENV/**'
  workflow_dispatch:

jobs:
  load-env:
    runs-on: ubuntu-latest
    outputs:
      java_version: ${{ steps.set-env.outputs.java_version }}
      maven_cli_opts: ${{ steps.set-env.outputs.maven_cli_opts }}
      maven_goals: ${{ steps.set-env.outputs.maven_goals }}
      docker_image_name: ${{ steps.set-env.outputs.docker_image_name }}
      docker_file_path: ${{ steps.set-env.outputs.docker_file_path }}
      enable_sonar_scan: ${{ steps.set-env.outputs.enable_sonar_scan }}
      enable_dependency_check: ${{ steps.set-env.outputs.enable_dependency_check }}
      enable_docker_scan: ${{ steps.set-env.outputs.enable_docker_scan }}
      fail_on_vulnerability: ${{ steps.set-env.outputs.fail_on_vulnerability }}
      version_strategy: ${{ steps.set-env.outputs.version_strategy }}
      main_branch: ${{ steps.set-env.outputs.main_branch }}
      aws_region: ${{ steps.set-env.outputs.aws_region || 'us-east-1' }}
      ecr_repository_uri: ${{ secrets.ECR_REPOSITORY_URI }}
    steps:
      - uses: actions/checkout@v3
      
      - name: Load Environment Variables
        id: set-env
        run: |
          ENV_FILE=".github/ENV/ci-variables.env"
          if [ -f "$ENV_FILE" ]; then
            echo "Loading variables from $ENV_FILE"
            # Set default values first
            echo "java_version=17" >> $GITHUB_OUTPUT
            echo "maven_cli_opts=--batch-mode --errors --fail-at-end --show-version" >> $GITHUB_OUTPUT
            echo "maven_goals=clean verify" >> $GITHUB_OUTPUT
            echo "docker_image_name=naming-server-service" >> $GITHUB_OUTPUT
            echo "docker_file_path=./naming-server-service/Dockerfile" >> $GITHUB_OUTPUT
            echo "enable_sonar_scan=true" >> $GITHUB_OUTPUT
            echo "enable_dependency_check=true" >> $GITHUB_OUTPUT
            echo "enable_docker_scan=true" >> $GITHUB_OUTPUT
            echo "fail_on_vulnerability=true" >> $GITHUB_OUTPUT
            echo "version_strategy=git-sha" >> $GITHUB_OUTPUT
            echo "main_branch=main" >> $GITHUB_OUTPUT
            
            # Then override with values from env file
            while IFS= read -r line || [[ -n "$line" ]]; do
              # Skip comments and empty lines
              [[ "$line" =~ ^#.*$ || -z "$line" ]] && continue
              # Parse key=value pairs
              if [[ "$line" =~ ^([^=]+)=(.*)$ ]]; then
                key="${BASH_REMATCH[1]}"
                value="${BASH_REMATCH[2]}"
                # Convert to lowercase for GitHub outputs
                output_key=$(echo "$key" | tr '[:upper:]' '[:lower:]')
                echo "$output_key=$value" >> $GITHUB_OUTPUT
              fi
            done < "$ENV_FILE"
          else
            echo "Environment file not found, using defaults"
            # Set all default values
            echo "java_version=17" >> $GITHUB_OUTPUT
            echo "maven_cli_opts=--batch-mode --errors --fail-at-end --show-version" >> $GITHUB_OUTPUT
            echo "maven_goals=clean verify" >> $GITHUB_OUTPUT
            echo "docker_image_name=naming-server-service" >> $GITHUB_OUTPUT
            echo "docker_file_path=./naming-server-service/Dockerfile" >> $GITHUB_OUTPUT
            echo "enable_sonar_scan=true" >> $GITHUB_OUTPUT
            echo "enable_dependency_check=true" >> $GITHUB_OUTPUT
            echo "enable_docker_scan=true" >> $GITHUB_OUTPUT
            echo "fail_on_vulnerability=true" >> $GITHUB_OUTPUT
            echo "version_strategy=git-sha" >> $GITHUB_OUTPUT
            echo "main_branch=main" >> $GITHUB_OUTPUT
          fi

  build:
    needs: load-env
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v3
      with:
        fetch-depth: 0  # Shallow clones should be disabled for a better relevancy of SonarQube analysis
    
    - name: Set up JDK
      uses: actions/setup-java@v3
      with:
        java-version: ${{ needs.load-env.outputs.java_version }}
        distribution: 'temurin'
        cache: maven
    
    - name: Cache Maven packages
      uses: actions/cache@v3
      with:
        path: ~/.m2
        key: ${{ runner.os }}-m2-${{ hashFiles('**/pom.xml') }}
        restore-keys: ${{ runner.os }}-m2
    
    - name: Build with Maven
      run: |
        cd naming-server-service
        ./mvnw ${{ needs.load-env.outputs.maven_cli_opts }} ${{ needs.load-env.outputs.maven_goals }}
    
    - name: Run SonarQube Analysis
      if: needs.load-env.outputs.enable_sonar_scan == 'true'
      run: |
        cd naming-server-service
        ./mvnw ${{ needs.load-env.outputs.maven_cli_opts }} \
          org.sonarsource.scanner.maven:sonar-maven-plugin:sonar \
          -Dsonar.projectKey=${{ secrets.SONAR_PROJECT_KEY }} \
          -Dsonar.host.url=${{ secrets.SONAR_HOST_URL }} \
          -Dsonar.login=${{ secrets.SONAR_TOKEN }} \
          -Dsonar.java.source=${{ needs.load-env.outputs.java_version }} \
          -Dsonar.qualitygate.wait=true
    
    - name: Run OWASP Dependency Check
      if: needs.load-env.outputs.enable_dependency_check == 'true'
      run: |
        cd naming-server-service
        ./mvnw ${{ needs.load-env.outputs.maven_cli_opts }} \
          org.owasp:dependency-check-maven:check \
          -DfailBuildOnCVSS=8 \
          -DsuppressionFiles=dependency-check-suppressions.xml
    
    - name: Run SpotBugs Security Analysis
      run: |
        cd naming-server-service
        ./mvnw ${{ needs.load-env.outputs.maven_cli_opts }} \
          com.github.spotbugs:spotbugs-maven-plugin:check
    
    - name: Archive JAR file
      uses: actions/upload-artifact@v3
      with:
        name: application-jar
        path: naming-server-service/target/*.jar
        retention-days: 5

  docker-build:
    needs: [load-env, build]
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v3
    
    - name: Download JAR artifact
      uses: actions/download-artifact@v3
      with:
        name: application-jar
        path: naming-server-service/target/
    
    - name: Configure AWS credentials
      uses: aws-actions/configure-aws-credentials@v2
      with:
        aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
        aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
        aws-region: ${{ needs.load-env.outputs.aws_region }}
    
    - name: Login to Amazon ECR
      id: login-ecr
      uses: aws-actions/amazon-ecr-login@v1
    
    - name: Generate Docker Image Tag
      id: docker-tag
      run: |
        if [[ "${{ needs.load-env.outputs.version_strategy }}" == "git-sha" ]]; then
          SHORT_SHA=$(git rev-parse --short HEAD)
          echo "IMAGE_TAG=${SHORT_SHA}" >> $GITHUB_ENV
        else
          echo "IMAGE_TAG=latest" >> $GITHUB_ENV
        fi
        echo "FULL_IMAGE_NAME=${{ needs.load-env.outputs.ecr_repository_uri }}:${IMAGE_TAG}" >> $GITHUB_ENV
    
    - name: Build Docker Image for ECR
      run: |
        docker build -t ${{ env.FULL_IMAGE_NAME }} -f ${{ needs.load-env.outputs.docker_file_path }} .
        docker images
    
    - name: Run Trivy Scan on Docker Image
      if: needs.load-env.outputs.enable_docker_scan == 'true'
      uses: aquasecurity/trivy-action@master
      with:
        image-ref: ${{ env.FULL_IMAGE_NAME }}
        format: 'table'
        exit-code: ${{ needs.load-env.outputs.fail_on_vulnerability == 'true' && '1' || '0' }}
        ignore-unfixed: true
        vuln-type: 'os,library'
        severity: 'CRITICAL,HIGH'
    
    - name: Push the Docker Image
      run: |
        docker push ${{ env.FULL_IMAGE_NAME }}
```

### Variable Usage Analysis

The workflow uses variables from multiple sources:

1. **Environment File Variables**
   - Loaded from `.github/ENV/ci-variables.env`
   - Passed between jobs via job outputs
   - Accessed using `${{ needs.load-env.outputs.variable_name }}`

2. **GitHub Secrets**
   - Securely stored in GitHub repository settings
   - Accessed using `${{ secrets.SECRET_NAME }}`
   - Used for sensitive information like API tokens and credentials

3. **Computed Variables**
   - Generated during workflow execution
   - Stored using `echo "VARIABLE_NAME=value" >> $GITHUB_ENV`
   - Accessed using `${{ env.VARIABLE_NAME }}`

### Job Dependencies

The workflow uses a clear job dependency chain:

```
load-env → build → docker-build
```

This ensures that:
1. Variables are loaded first
2. The build job completes successfully before Docker image creation
3. Artifacts are properly passed between jobs

Job dependencies are specified using the `needs` keyword:
```yaml
docker-build:
  needs: [load-env, build]
```

### Performance Optimization

The workflow includes several performance optimizations:

1. **Maven Dependency Caching**
   ```yaml
   - name: Cache Maven packages
     uses: actions/cache@v3
     with:
       path: ~/.m2
       key: ${{ runner.os }}-m2-${{ hashFiles('**/pom.xml') }}
       restore-keys: ${{ runner.os }}-m2
   ```

2. **Java Setup with Built-in Caching**
   ```yaml
   - name: Set up JDK
     uses: actions/setup-java@v3
     with:
       java-version: ${{ needs.load-env.outputs.java_version }}
       distribution: 'temurin'
       cache: maven
   ```

3. **Conditional Execution of Expensive Steps**
   ```yaml
   - name: Run SonarQube Analysis
     if: needs.load-env.outputs.enable_sonar_scan == 'true'
     # ...
   ```

These optimizations significantly reduce build time, especially for repeated builds with minimal changes.

## 8. Configuration and Security

### Credentials Management

Secure credential management is implemented using GitHub Secrets:

1. **AWS Credentials**
   - `AWS_ACCESS_KEY_ID`: Access key for AWS authentication
   - `AWS_SECRET_ACCESS_KEY`: Secret key for AWS authentication

2. **SonarCloud/SonarQube**
   - `SONAR_TOKEN`: Authentication token for SonarCloud
   - `SONAR_HOST_URL`: URL of SonarQube server
   - `SONAR_PROJECT_KEY`: Project key in SonarCloud/SonarQube

3. **Repository-specific**
   - `ECR_REPOSITORY_URI`: Full URI of the ECR repository

**Security Best Practices:**
- Never hardcode credentials in the workflow file
- Rotate credentials regularly
- Use repository/environment scoping for secrets
- Implement least privilege principle

### Vulnerability Management

Our CI/CD pipeline includes comprehensive vulnerability management:

1. **Maven Dependency Vulnerabilities**
   - OWASP Dependency Check integration
   - Configurable vulnerability threshold (CVSS score)
   - Suppression file for managing false positives

2. **Container Vulnerabilities**
   - Trivy scanning for container image vulnerabilities
   - Coverage for OS packages and application dependencies
   - Configurable severity thresholds (CRITICAL, HIGH, MEDIUM)

3. **Code Vulnerabilities**
   - SpotBugs static code analysis with security focus
   - Custom filter files to focus on important security patterns
   - Integration with SonarQube for additional code quality checks

### Repository Configuration Best Practices

1. **Branch Protection Rules**
   - Enable branch protection for the main branch
   - Require status checks to pass before merging
   - Require pull request reviews before merging

2. **GitHub Actions Permissions**
   - Ensure GitHub Actions has permission to create pull requests, comment on issues, etc.
   - Configure GITHUB_TOKEN permissions in the workflow as needed

3. **Documentation and Naming Conventions**
   - Document the CI workflow configuration
   - Use consistent naming for jobs and steps
   - Use descriptive names for artifacts and environment variables