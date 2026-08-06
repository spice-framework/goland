package release

import (
	"archive/zip"
	"bytes"
	"crypto/ed25519"
	"crypto/x509"
	"encoding/base64"
	"encoding/pem"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

const testCommit = "0123456789abcdef0123456789abcdef01234567"

func TestPackageSignVerify(t *testing.T) {
	root := newTestRepository(t, "goland")
	epoch := time.Date(2026, 8, 6, 12, 0, 0, 0, time.UTC)
	input := filepath.Join(root, "input.zip")
	writeTestZIP(t, input, map[string][]byte{
		"spice-goland/lib/spice.jar": nestedPluginJAR(t),
	})
	unsigned := filepath.Join(root, "unsigned")
	options := Options{Root: root, Input: input, Output: unsigned, Version: "v0.2.0", Commit: testCommit, Epoch: epoch.Unix()}
	if err := Package(options); err != nil {
		t.Fatalf("Package: %v", err)
	}
	second := filepath.Join(root, "unsigned-second")
	options.Output = second
	if err := Package(options); err != nil {
		t.Fatalf("Package second: %v", err)
	}
	assertDirectoriesEqual(t, unsigned, second)

	public, privateKey, err := ed25519.GenerateKey(nil)
	if err != nil {
		t.Fatal(err)
	}
	publicDER, err := x509.MarshalPKIXPublicKey(public)
	if err != nil {
		t.Fatal(err)
	}
	writeTestFile(t, filepath.Join(root, publicKeyPath), pem.EncodeToMemory(&pem.Block{Type: "PUBLIC KEY", Bytes: publicDER}))
	privateDER, err := x509.MarshalPKCS8PrivateKey(privateKey)
	if err != nil {
		t.Fatal(err)
	}
	t.Setenv(signingKeyEnv, base64.StdEncoding.EncodeToString(privateDER))
	signed := filepath.Join(root, "signed")
	options.Input, options.Output = unsigned, signed
	if err := Sign(options); err != nil {
		t.Fatalf("Sign: %v", err)
	}
	options.Input = signed
	result, err := Verify(options)
	if err != nil {
		t.Fatalf("Verify: %v", err)
	}
	if result.Artifacts != 6 || result.Commit != testCommit || result.Version != "v0.2.0" {
		t.Fatalf("unexpected result: %+v", result)
	}

	signature := filepath.Join(signed, "checksums.txt.sig")
	corrupt := readTestFile(t, signature)
	corrupt[0] ^= 0xff
	if err := os.WriteFile(signature, corrupt, 0o644); err != nil {
		t.Fatal(err)
	}
	if _, err := Verify(options); err == nil || !strings.Contains(err.Error(), "invalid Ed25519") {
		t.Fatalf("Verify corrupt signature error = %v", err)
	}
}

func TestPackageRejectsUnsafeOrMismatchedInput(t *testing.T) {
	t.Parallel()
	root := newTestRepository(t, "goland")
	input := filepath.Join(root, "unsafe.zip")
	writeTestZIP(t, input, map[string][]byte{"../escape": []byte("bad")})
	options := Options{Root: root, Input: input, Output: filepath.Join(root, "out"), Version: "v0.2.0", Commit: testCommit, Epoch: 1_786_016_400}
	if err := Package(options); err == nil || !strings.Contains(err.Error(), "unsafe archive") {
		t.Fatalf("Package unsafe error = %v", err)
	}
	options.Version = "v0.2.1"
	if err := Package(options); err == nil || !strings.Contains(err.Error(), "does not match declared") {
		t.Fatalf("Package mismatched version error = %v", err)
	}
}

func TestSignRejectsWrongKeyAndExtraArtifact(t *testing.T) {
	root := newTestRepository(t, "goland")
	epoch := int64(1_786_016_400)
	input := filepath.Join(root, "input.zip")
	writeTestZIP(t, input, map[string][]byte{"spice-goland/lib/spice.jar": nestedPluginJAR(t)})
	unsigned := filepath.Join(root, "unsigned")
	options := Options{Root: root, Input: input, Output: unsigned, Version: "v0.2.0", Commit: testCommit, Epoch: epoch}
	if err := Package(options); err != nil {
		t.Fatal(err)
	}
	_, trustedPrivate, _ := ed25519.GenerateKey(nil)
	trustedPublic := trustedPrivate.Public().(ed25519.PublicKey)
	trustedDER, _ := x509.MarshalPKIXPublicKey(trustedPublic)
	writeTestFile(t, filepath.Join(root, publicKeyPath), pem.EncodeToMemory(&pem.Block{Type: "PUBLIC KEY", Bytes: trustedDER}))
	_, wrongPrivate, _ := ed25519.GenerateKey(nil)
	wrongDER, _ := x509.MarshalPKCS8PrivateKey(wrongPrivate)
	t.Setenv(signingKeyEnv, base64.StdEncoding.EncodeToString(wrongDER))
	options.Input, options.Output = unsigned, filepath.Join(root, "signed")
	if err := Sign(options); err == nil || !strings.Contains(err.Error(), "does not match") {
		t.Fatalf("Sign wrong key error = %v", err)
	}
	writeTestFile(t, filepath.Join(unsigned, "unexpected"), []byte("no"))
	if err := Sign(options); err == nil || !strings.Contains(err.Error(), "artifact names") {
		t.Fatalf("Sign extra artifact error = %v", err)
	}
}

func newTestRepository(t *testing.T, kind string) string {
	t.Helper()
	root := t.TempDir()
	writeTestFile(t, filepath.Join(root, configPath), []byte(`{
  "schema": 1,
  "repository": "spice-framework/test-editor",
  "artifactBase": "spice-test",
  "displayName": "Spice Test Editor",
  "kind": "`+kind+`",
  "pluginID": "com.github.stevenbuglione.spice",
  "versionFile": "version.properties",
  "versionKey": "pluginVersion"
}
`))
	writeTestFile(t, filepath.Join(root, "version.properties"), []byte("pluginVersion=0.2.0\n"))
	writeTestFile(t, filepath.Join(root, "LICENSE"), []byte("test license\n"))
	return root
}

func nestedPluginJAR(t *testing.T) []byte {
	t.Helper()
	var result bytes.Buffer
	writer := zip.NewWriter(&result)
	header := &zip.FileHeader{Name: "META-INF/plugin.xml", Modified: time.Now()}
	entry, err := writer.CreateHeader(header)
	if err != nil {
		t.Fatal(err)
	}
	if _, err := entry.Write([]byte("<idea-plugin><id>com.github.stevenbuglione.spice</id><version>0.2.0</version></idea-plugin>")); err != nil {
		t.Fatal(err)
	}
	licenseHeader := &zip.FileHeader{Name: "LICENSE", Modified: time.Now()}
	licenseEntry, err := writer.CreateHeader(licenseHeader)
	if err != nil {
		t.Fatal(err)
	}
	if _, err := licenseEntry.Write([]byte("test license\n")); err != nil {
		t.Fatal(err)
	}
	if err := writer.Close(); err != nil {
		t.Fatal(err)
	}
	return result.Bytes()
}

func writeTestZIP(t *testing.T, name string, entries map[string][]byte) {
	t.Helper()
	if err := os.MkdirAll(filepath.Dir(name), 0o755); err != nil {
		t.Fatal(err)
	}
	file, err := os.Create(name)
	if err != nil {
		t.Fatal(err)
	}
	writer := zip.NewWriter(file)
	for entryName, content := range entries {
		header := &zip.FileHeader{Name: entryName, Modified: time.Now()}
		entry, err := writer.CreateHeader(header)
		if err != nil {
			t.Fatal(err)
		}
		if _, err := entry.Write(content); err != nil {
			t.Fatal(err)
		}
	}
	if err := writer.Close(); err != nil {
		t.Fatal(err)
	}
	if err := file.Close(); err != nil {
		t.Fatal(err)
	}
}

func writeTestFile(t *testing.T, name string, data []byte) {
	t.Helper()
	if err := os.MkdirAll(filepath.Dir(name), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(name, data, 0o644); err != nil {
		t.Fatal(err)
	}
}

func readTestFile(t *testing.T, name string) []byte {
	t.Helper()
	data, err := os.ReadFile(name)
	if err != nil {
		t.Fatal(err)
	}
	return data
}

func assertDirectoriesEqual(t *testing.T, first, second string) {
	t.Helper()
	firstEntries, err := os.ReadDir(first)
	if err != nil {
		t.Fatal(err)
	}
	secondEntries, err := os.ReadDir(second)
	if err != nil {
		t.Fatal(err)
	}
	if len(firstEntries) != len(secondEntries) {
		t.Fatalf("entry counts differ: %d != %d", len(firstEntries), len(secondEntries))
	}
	for _, entry := range firstEntries {
		if !bytes.Equal(readTestFile(t, filepath.Join(first, entry.Name())), readTestFile(t, filepath.Join(second, entry.Name()))) {
			t.Fatalf("artifact %s differs", entry.Name())
		}
	}
}
