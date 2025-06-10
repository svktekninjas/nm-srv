# Trivy Scan Integration Guide

This guide explains how Trivy container scanning is integrated with the Docker build and push workflow.

## Integration Overview

Trivy is integrated in the CI/CD pipeline after the Docker image is built but before it's pushed to the registry. This ensures that vulnerable images aren't published to the container registry.

```
Build Image → Run Trivy Scan → Push Image (if scan passes)
```

## Workflow Configuration

The Trivy scan is implemented in the GitHub Actions workflow using the official `aquasecurity/trivy-action` action:

```yaml
- name: Run Trivy Scan on Docker Image
  if: ${{ env.ENABLE_DOCKER_SCAN == 'true' }}
  uses: aquasecurity/trivy-action@0.17.0
  with:
    image-ref: ${{ env.FULL_IMAGE_NAME }}
    format: 'table'
    severity: 'CRITICAL,HIGH,MEDIUM'
    exit-code: ${{ env.FAIL_ON_VULNERABILITY == 'true' && '1' || '0' }}
    vuln-type: 'os,library'
    scanners: 'vuln'
  # Continue even if vulnerabilities are found
  continue-on-error: true
```

## Key Configuration Parameters

### 1. Conditional Execution
```yaml
if: ${{ env.ENABLE_DOCKER_SCAN == 'true' }}
```
The scan is controlled by the `ENABLE_DOCKER_SCAN` environment variable, allowing it to be enabled or disabled as needed.

### 2. Trivy Version
```yaml
uses: aquasecurity/trivy-action@0.17.0
```
The workflow pins to a specific version (0.17.0) for stability and reproducibility.

### 3. Image Reference
```yaml
image-ref: ${{ env.FULL_IMAGE_NAME }}
```
Scans the Docker image that was just built, using the same tag.

### 4. Scan Configuration
```yaml
format: 'table'
severity: 'CRITICAL,HIGH,MEDIUM'
vuln-type: 'os,library'
scanners: 'vuln'
```
- `format`: Output format (table is human-readable)
- `severity`: Only report vulnerabilities of these severity levels
- `vuln-type`: Scan both OS packages and language-specific libraries
- `scanners`: Use vulnerability scanner mode

### 5. Failure Handling
```yaml
exit-code: ${{ env.FAIL_ON_VULNERABILITY == 'true' && '1' || '0' }}
continue-on-error: true
```
- `exit-code`: Conditionally fail the step based on `FAIL_ON_VULNERABILITY` environment variable
- `continue-on-error`: Allow the workflow to continue even if vulnerabilities are found

## How It Works

1. **Pre-Scan**: The Docker image is built and tagged locally
2. **Scanning**: Trivy analyzes the image for vulnerabilities in:
   - Base image OS packages
   - Application dependencies
   - Language-specific libraries
3. **Reporting**: Results are displayed in table format in the workflow logs
4. **Decision**: The workflow can be configured to:
   - Fail if vulnerabilities are found (strict mode)
   - Continue despite vulnerabilities (advisory mode)

## Security Enforcement

The workflow can be configured for different security postures:

### Advisory Mode (Current Configuration)
```yaml
exit-code: 0
continue-on-error: true
```
- Reports vulnerabilities but doesn't block deployment
- Suitable for initial implementation or development environments

### Enforcement Mode
```yaml
exit-code: 1
continue-on-error: false
```
- Fails the workflow if vulnerabilities are found
- Suitable for production deployments

## Additional Options

The current implementation can be extended with these Trivy options:

### 1. SARIF Output for GitHub Security Tab
```yaml
format: 'sarif'
output: 'trivy-results.sarif'

- name: Upload SARIF file
  uses: github/codeql-action/upload-sarif@v3
  with:
    sarif_file: 'trivy-results.sarif'
```

### 2. Artifact Reporting
```yaml
- name: Upload Trivy scan results
  uses: actions/upload-artifact@v4
  with:
    name: trivy-scan-results
    path: trivy-results.txt
```

### 3. Custom Policies
```yaml
- name: Run Trivy with custom policies
  uses: aquasecurity/trivy-action@0.17.0
  with:
    image-ref: ${{ env.FULL_IMAGE_NAME }}
    policy-bundle: './security-policies'
```

## Best Practices

1. **Pin Trivy Version**: Always specify an exact version for reproducible scans
2. **Scan Before Push**: Always scan images before pushing to registries
3. **Adjust Severity Levels**: Set appropriate threshold based on environment
4. **Regular Updates**: Update Trivy action version periodically for new vulnerability databases
5. **Review Results**: Regularly review scan results, even in advisory mode
6. **Custom Policies**: Consider adding custom policies for organization-specific rules

## Related Resources

- [Trivy GitHub Action Documentation](https://github.com/aquasecurity/trivy-action)
- [Trivy CLI Documentation](https://aquasecurity.github.io/trivy/)
- [Container Security Best Practices](https://github.com/aquasecurity/trivy/blob/main/docs/getting-started/overview.md)