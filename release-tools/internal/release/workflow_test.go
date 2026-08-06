package release

import (
	"os"
	"path/filepath"
	"regexp"
	"runtime"
	"strings"
	"testing"
)

func TestReleaseWorkflowSecurityBoundary(t *testing.T) {
	t.Parallel()
	_, source, _, ok := runtime.Caller(0)
	if !ok {
		t.Fatal("resolve test source")
	}
	workflowPath := filepath.Clean(filepath.Join(filepath.Dir(source), "..", "..", "..", ".github", "workflows", "release.yml"))
	content, err := os.ReadFile(workflowPath)
	if err != nil {
		t.Fatal(err)
	}
	workflow := string(content)
	required := []string{
		"permissions: {}",
		"name: release-signing",
		"name: release-publish",
		"SPICE_EDITOR_RELEASE_SIGNING_KEY: ${{ secrets.SPICE_EDITOR_RELEASE_SIGNING_KEY }}",
		"Independently rebuild on Windows",
		"Require exact unsigned reproducibility before signing",
		"Independently verify signature and reproducibility",
		"persist-credentials: false",
		"editor-release package",
		"editor-release sign",
		"editor-release verify",
	}
	for _, value := range required {
		if !strings.Contains(workflow, value) {
			t.Errorf("release workflow lacks %q", value)
		}
	}
	if count := strings.Count(workflow, "contents: write"); count != 1 {
		t.Errorf("contents:write count = %d, require exactly one", count)
	}
	if count := strings.Count(workflow, "${{ secrets.SPICE_EDITOR_RELEASE_SIGNING_KEY }}"); count != 1 {
		t.Errorf("signing secret use count = %d, require exactly one", count)
	}
	for _, forbidden := range []string{"secrets: inherit", "pull_request_target", "persist-credentials: true", "contents: admin"} {
		if strings.Contains(workflow, forbidden) {
			t.Errorf("release workflow contains forbidden authority %q", forbidden)
		}
	}
	actionReference := regexp.MustCompile(`(?m)^\s*uses:\s+[^@\s]+@([^\s#]+)`)
	for _, match := range actionReference.FindAllStringSubmatch(workflow, -1) {
		if !regexp.MustCompile(`^[0-9a-f]{40}$`).MatchString(match[1]) {
			t.Errorf("action reference %q is not pinned by a full commit", match[0])
		}
	}
	publish := strings.Index(workflow, "  publish:")
	verify := strings.Index(workflow, "  verify-artifacts:")
	compare := strings.Index(workflow, "  compare-unsigned:")
	sign := strings.Index(workflow, "  sign:")
	if publish < 0 || verify < 0 || publish < verify {
		t.Error("protected publish must follow independent verification")
	}
	if compare < 0 || sign < 0 || sign < compare || !strings.Contains(workflow[sign:verify], "compare-unsigned") {
		t.Error("protected signing must wait for the unsigned reproducibility gate")
	}
}
