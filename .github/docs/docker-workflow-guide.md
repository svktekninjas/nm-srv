# Docker Workflow Guide for CI/CD Pipelines

This guide outlines best practices for Docker image building in CI/CD pipelines, with specific focus on GitHub Actions workflows.

## Table of Contents

1. [Common Issues and Solutions](#common-issues-and-solutions)
2. [GitHub Actions Environment Variables](#github-actions-environment-variables)
3. [Docker Build Context](#docker-build-context)
4. [Secrets Management](#secrets-management)
5. [ECR Integration](#ecr-integration)
6. [Recommended Patterns](#recommended-patterns)
7. [Troubleshooting](#troubleshooting)

## Common Issues and Solutions

### Image Tag Generation

**Issue**: Empty or malformed Docker image tags
```
ERROR: invalid tag "registry.example.com/image:": invalid reference format
```

**Solution**: Always use step outputs to pass variables between steps
```yaml
- name: Generate tag
  id: tag-gen
  run: |
    TAG=$(git rev-parse --short HEAD)
    echo "tag=$TAG" >> $GITHUB_OUTPUT

- name: Build image
  run: |
    docker build -t "${{ secrets.REGISTRY }}:${{ steps.tag-gen.outputs.tag }}" .
```

### Build Context Path

**Issue**: Files not found during build
```
ERROR: failed to solve: lstat /target: no such file or directory
```

**Solution**: Ensure the Docker build context matches the Dockerfile expectations
```yaml
- name: Build Docker image
  run: |
    # Change to the directory containing the Dockerfile
    cd app-directory
    # Build with current directory as context
    docker build -t "${{ env.IMAGE_NAME }}" .
```

### Artifact Paths

**Issue**: JAR/WAR files not found by Dockerfile
```
COPY --chown=appuser:appgroup target/*.jar app.jar
```

**Solution**: Verify artifact paths and directory structure
```yaml
- name: Download artifacts
  uses: actions/download-artifact@v4
  with:
    name: application-jar
    path: app-directory/target/
    
- name: List files
  run: |
    ls -la app-directory/target/
```

## GitHub Actions Environment Variables

### Environment Variable Scope

1. **Step-level variables** are only available within the current step
2. **Job-level variables** are available to all steps in the job
3. **Workflow-level variables** are available to all jobs in the workflow

### Setting and Using Variables

```yaml
# Setting environment variables
env:
  GLOBAL_VAR: "available to all jobs"

jobs:
  build:
    env:
      JOB_VAR: "available to all steps in this job"
    steps:
      - name: Set Step Variables
        run: echo "STEP_VAR=value" >> $GITHUB_ENV
      
      - name: Use Variables
        run: echo "$GLOBAL_VAR $JOB_VAR $STEP_VAR"
```

### Passing Data Between Steps

Always use `GITHUB_OUTPUT` to pass data between steps:

```yaml
- name: Generate Data
  id: generator
  run: |
    echo "value=some-data" >> $GITHUB_OUTPUT
    
- name: Use Data
  run: |
    echo "Using value: ${{ steps.generator.outputs.value }}"
```

## Docker Build Context

### Understanding Build Context

The Docker build context is the set of files that the Docker daemon can access during the build. It's crucial to understand:

1. **Context Directory**: The directory from which files are sent to the Docker daemon
2. **Dockerfile Path**: Can be within or outside the context directory
3. **COPY/ADD Commands**: Paths are relative to the context directory

### Best Practices

1. **Minimize Context Size**: Include only necessary files
2. **Match Directory Structure**: Ensure context matches Dockerfile expectations
3. **Verify Paths**: Use `ls` commands to verify paths before building
4. **Use .dockerignore**: Exclude unnecessary files

### Examples

**Correct Context Setup**:
```yaml
- name: Build Docker Image
  run: |
    # Navigate to the project directory
    cd service-directory
    
    # List contents to verify
    echo "Context directory contents:"
    ls -la
    
    # Build using current directory as context
    docker build -t "image:tag" .
```

## Secrets Management

### GitHub Secrets

1. **Access Pattern**: Use `${{ secrets.SECRET_NAME }}` to access
2. **Visibility**: Secrets appear as `***` in logs
3. **Scope**: Repository, environment, or organization-level

### AWS Credential Best Practices

1. **Use Short-lived Credentials**: OIDC is preferred over long-term keys
2. **Limit IAM Permissions**: Restrict to required ECR actions
3. **Validate Setup**: Test auth before pushing

```yaml
- name: Configure AWS credentials
  uses: aws-actions/configure-aws-credentials@v2
  with:
    role-to-assume: ${{ secrets.AWS_ROLE_ARN }}
    aws-region: ${{ env.AWS_REGION }}
```

## ECR Integration

### Login Process

```yaml
- name: Login to Amazon ECR
  id: login-ecr
  uses: aws-actions/amazon-ecr-login@v2
  
- name: Extract Registry URI
  id: registry
  run: |
    echo "uri=${{ steps.login-ecr.outputs.registry }}" >> $GITHUB_OUTPUT
```

### Image Tagging Strategy

1. **Git SHA**: Immutable, traceable tags
2. **Semantic Versioning**: For releases
3. **Branch-based**: For development environments

```yaml
- name: Set Tags
  id: tags
  run: |
    # Git SHA tag (always)
    SHA=$(git rev-parse --short HEAD)
    TAGS="${{ steps.registry.outputs.uri }}/${{ env.REPO_NAME }}:$SHA"
    
    # For release tags
    if [[ $GITHUB_REF == refs/tags/* ]]; then
      VERSION=${GITHUB_REF#refs/tags/v}
      TAGS="$TAGS,${{ steps.registry.outputs.uri }}/${{ env.REPO_NAME }}:$VERSION"
    fi
    
    # Main branch gets latest
    if [[ $GITHUB_REF == refs/heads/main ]]; then
      TAGS="$TAGS,${{ steps.registry.outputs.uri }}/${{ env.REPO_NAME }}:latest"
    fi
    
    echo "tags=$TAGS" >> $GITHUB_OUTPUT
```

## Recommended Patterns

### Improved Docker Build Job

```yaml
docker-build:
  name: Docker Build and Push
  runs-on: ubuntu-latest
  needs: build
  steps:
    - name: Checkout code
      uses: actions/checkout@v4
      
    - name: Download artifacts
      uses: actions/download-artifact@v4
      with:
        name: application-jar
        path: ./app/target/
        
    - name: Verify artifacts
      run: |
        ls -la ./app/target/
        
    - name: Login to ECR
      uses: aws-actions/amazon-ecr-login@v2
      id: ecr-login
      
    - name: Set image info
      id: image-info
      run: |
        SHA=$(git rev-parse --short HEAD)
        echo "tag=$SHA" >> $GITHUB_OUTPUT
        echo "repo=${{ steps.ecr-login.outputs.registry }}/${{ env.REPO_NAME }}" >> $GITHUB_OUTPUT
    
    - name: Build and push
      run: |
        cd ./app
        docker build -t "${{ steps.image-info.outputs.repo }}:${{ steps.image-info.outputs.tag }}" .
        docker push "${{ steps.image-info.outputs.repo }}:${{ steps.image-info.outputs.tag }}"
```

### Using Docker BuildKit

```yaml
- name: Build with BuildKit
  env:
    DOCKER_BUILDKIT: 1
  run: |
    docker build \
      --build-arg BUILD_DATE=$(date -u +'%Y-%m-%dT%H:%M:%SZ') \
      --build-arg VERSION=${{ steps.version.outputs.value }} \
      --build-arg REVISION=${{ github.sha }} \
      -t ${{ env.IMAGE_NAME }} \
      .
```

## Troubleshooting

### Diagnostic Steps

1. **Verify File Paths**:
   ```bash
   find . -name "*.jar" -type f
   ```

2. **Check Docker Context**:
   ```bash
   # Show what would be included in build context
   find . -type f -not -path "*/\.*" | sort
   ```

3. **Test Docker Build Locally**:
   ```bash
   # Simulate the CI environment
   mkdir -p test/target
   touch test/target/app.jar
   cd test
   docker build -t test-image .
   ```

4. **Validate Image Content**:
   ```bash
   # Check if files were correctly copied
   docker build -t debug-image .
   docker run --rm debug-image ls -la /app
   ```

### Common Error Messages

| Error | Likely Cause | Solution |
|-------|--------------|----------|
| `no such file or directory` | Incorrect build context | Change directory or adjust file paths |
| `invalid reference format` | Malformed tag | Check environment variable expansion |
| `denied: authentication required` | ECR login issues | Verify AWS credentials |
| `unknown flag: --platform` | Docker version mismatch | Use Docker BuildX or update version |

### GitHub Actions Debugging

Enable debug logs by setting the following secrets:
- `ACTIONS_RUNNER_DEBUG: true`
- `ACTIONS_STEP_DEBUG: true`

## Conclusion

By following these patterns and best practices, you can ensure reliable Docker image building in your CI/CD pipeline. The key points to remember are:

1. **Proper variable passing** between steps using `GITHUB_OUTPUT`
2. **Correct build context** matching your Dockerfile expectations
3. **Verification steps** to confirm files are in the right locations
4. **Comprehensive logging** to aid in troubleshooting

For further assistance with Docker in CI/CD pipelines, refer to Docker's official documentation and GitHub Actions documentation.