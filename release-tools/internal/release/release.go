// Package release creates and authenticates deterministic Spice editor artifacts.
package release

import (
	"archive/zip"
	"bytes"
	"crypto/ed25519"
	"crypto/sha1" // SPDX 2.3 defines its package verification code as SHA-1.
	"crypto/sha256"
	"crypto/x509"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"encoding/pem"
	"encoding/xml"
	"errors"
	"fmt"
	"io"
	"io/fs"
	"os"
	"path"
	"path/filepath"
	"regexp"
	"slices"
	"strings"
	"time"
)

const (
	configPath        = "release-tools/release.json"
	publicKeyPath     = "security/release/ed25519-public.pem"
	signingKeyEnv     = "SPICE_EDITOR_RELEASE_SIGNING_KEY"
	maxArtifactBytes  = 128 << 20
	maxExpandedBytes  = 256 << 20
	maxArchiveEntries = 4096
	maxArchiveDepth   = 4
)

var (
	versionPattern = regexp.MustCompile(`^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$`)
	commitPattern  = regexp.MustCompile(`^[0-9a-f]{40}$`)
)

type Options struct {
	Root    string
	Input   string
	Output  string
	Version string
	Commit  string
	Epoch   int64
}

type Result struct {
	Repository string
	Version    string
	Commit     string
	Artifacts  int
}

type config struct {
	Schema       int    `json:"schema"`
	Repository   string `json:"repository"`
	ArtifactBase string `json:"artifactBase"`
	DisplayName  string `json:"displayName"`
	Kind         string `json:"kind"`
	PluginID     string `json:"pluginID,omitempty"`
	VersionFile  string `json:"versionFile"`
	VersionKey   string `json:"versionKey"`
}

type metadata struct {
	config config
	root   string
	base   string
	commit string
	epoch  time.Time
	names  artifactNames
}

type artifactNames struct {
	Package    string
	SBOM       string
	Provenance string
}

func Package(options Options) error {
	meta, err := validateOptions(options, true, true)
	if err != nil {
		return err
	}
	input, err := readRegular(options.Input, maxArtifactBytes)
	if err != nil {
		return fmt.Errorf("read package input: %w", err)
	}
	var packaged []byte
	switch meta.config.Kind {
	case "goland":
		packaged, err = normalizeZIP(input, meta.epoch)
	case "zed":
		packaged, err = packageZed(meta, input)
	default:
		err = fmt.Errorf("unsupported artifact kind %q", meta.config.Kind)
	}
	if err != nil {
		return fmt.Errorf("package %s: %w", meta.config.Kind, err)
	}
	files := map[string][]byte{meta.names.Package: packaged}
	files[meta.names.SBOM], err = renderSBOM(meta, packaged)
	if err != nil {
		return err
	}
	files[meta.names.Provenance], err = renderProvenance(meta, packaged)
	if err != nil {
		return err
	}
	files["checksums.txt"] = renderChecksums(files)
	if err := validateUnsigned(meta, files); err != nil {
		return fmt.Errorf("validate packaged artifacts: %w", err)
	}
	return writeNewDirectory(options.Output, files)
}

func Sign(options Options) error {
	meta, err := validateOptions(options, false, true)
	if err != nil {
		return err
	}
	files, err := readDirectory(options.Input, maxArtifactBytes)
	if err != nil {
		return fmt.Errorf("read unsigned artifacts: %w", err)
	}
	if err := validateUnsigned(meta, files); err != nil {
		return fmt.Errorf("refuse unsigned artifacts: %w", err)
	}
	privateKey, err := signingKeyFromEnvironment()
	if err != nil {
		return err
	}
	defer clear(privateKey)
	publicKey := privateKey.Public().(ed25519.PublicKey)
	trustedPEM, err := readRegular(filepath.Join(meta.root, publicKeyPath), 4096)
	if err != nil {
		return fmt.Errorf("read trusted public key: %w", err)
	}
	canonicalPEM, err := encodePublicKey(publicKey)
	if err != nil {
		return err
	}
	if !bytes.Equal(trustedPEM, canonicalPEM) {
		return errors.New("private signing key does not match committed trust anchor")
	}
	checksums := files["checksums.txt"]
	signature := ed25519.Sign(privateKey, checksums)
	if !ed25519.Verify(publicKey, checksums, signature) {
		return errors.New("self-verification of release signature failed")
	}
	files["checksums.txt.pem"] = canonicalPEM
	files["checksums.txt.sig"] = signature
	if err := validateSigned(meta, files, publicKey); err != nil {
		return fmt.Errorf("validate signed artifacts: %w", err)
	}
	return writeNewDirectory(options.Output, files)
}

func Verify(options Options) (Result, error) {
	meta, err := validateOptions(options, false, false)
	if err != nil {
		return Result{}, err
	}
	files, err := readDirectory(options.Input, maxArtifactBytes)
	if err != nil {
		return Result{}, fmt.Errorf("read signed artifacts: %w", err)
	}
	trustedPEM, err := readRegular(filepath.Join(meta.root, publicKeyPath), 4096)
	if err != nil {
		return Result{}, fmt.Errorf("read trusted public key: %w", err)
	}
	trusted, err := parsePublicKey(trustedPEM)
	if err != nil {
		return Result{}, err
	}
	if err := validateSigned(meta, files, trusted); err != nil {
		return Result{}, err
	}
	return Result{Repository: meta.config.Repository, Version: options.Version, Commit: options.Commit, Artifacts: len(files)}, nil
}

func validateOptions(options Options, requirePackageInput, requireOutput bool) (metadata, error) {
	root, err := filepath.Abs(options.Root)
	if err != nil {
		return metadata{}, fmt.Errorf("resolve repository root: %w", err)
	}
	data, err := readRegular(filepath.Join(root, configPath), 16<<10)
	if err != nil {
		return metadata{}, fmt.Errorf("read release configuration: %w", err)
	}
	var value config
	decoder := json.NewDecoder(bytes.NewReader(data))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(&value); err != nil {
		return metadata{}, fmt.Errorf("decode release configuration: %w", err)
	}
	if decoder.Decode(new(any)) != io.EOF {
		return metadata{}, errors.New("release configuration has trailing JSON")
	}
	if value.Schema != 1 || !strings.HasPrefix(value.Repository, "spice-framework/") || value.ArtifactBase == "" || value.DisplayName == "" ||
		(value.Kind != "goland" && value.Kind != "zed") || value.VersionFile == "" || value.VersionKey == "" {
		return metadata{}, errors.New("release configuration does not match schema 1")
	}
	if value.Kind == "goland" && value.PluginID == "" {
		return metadata{}, errors.New("GoLand release configuration requires pluginID")
	}
	if !versionPattern.MatchString(options.Version) {
		return metadata{}, fmt.Errorf("version %q must be canonical vMAJOR.MINOR.PATCH", options.Version)
	}
	if !commitPattern.MatchString(options.Commit) {
		return metadata{}, fmt.Errorf("commit %q must be a lowercase full object ID", options.Commit)
	}
	if options.Epoch <= 0 {
		return metadata{}, errors.New("source date epoch must be positive")
	}
	if options.Input == "" {
		return metadata{}, errors.New("input path is required")
	}
	if requireOutput && options.Output == "" {
		return metadata{}, errors.New("output path is required")
	}
	if requirePackageInput {
		info, statErr := os.Stat(options.Input)
		if statErr != nil || !info.Mode().IsRegular() {
			return metadata{}, errors.New("package input must be a regular file")
		}
	}
	declared, err := declaredVersion(filepath.Join(root, value.VersionFile), value.VersionKey)
	if err != nil {
		return metadata{}, err
	}
	if options.Version != "v"+declared {
		return metadata{}, fmt.Errorf("tag version %s does not match declared version %s", options.Version, declared)
	}
	base := strings.TrimPrefix(options.Version, "v")
	return metadata{
		config: value, root: root, base: base, commit: options.Commit, epoch: time.Unix(options.Epoch, 0).UTC(),
		names: artifactNames{
			Package:    value.ArtifactBase + "_" + base + ".zip",
			SBOM:       value.ArtifactBase + "_" + base + "_sbom.spdx.json",
			Provenance: value.ArtifactBase + "_" + base + "_provenance.intoto.jsonl",
		},
	}, nil
}

func declaredVersion(name, key string) (string, error) {
	data, err := readRegular(name, 1<<20)
	if err != nil {
		return "", fmt.Errorf("read declared version: %w", err)
	}
	for _, line := range strings.Split(string(data), "\n") {
		line = strings.TrimSpace(line)
		if strings.HasPrefix(line, key+"=") {
			return strings.TrimSpace(strings.TrimPrefix(line, key+"=")), nil
		}
		if strings.HasPrefix(line, key+" = ") {
			return strings.Trim(strings.TrimSpace(strings.TrimPrefix(line, key+" = ")), `"`), nil
		}
	}
	return "", fmt.Errorf("%s does not declare %s", name, key)
}

func packageZed(meta metadata, wasm []byte) ([]byte, error) {
	manifest, err := readRegular(filepath.Join(meta.root, "extension.toml"), 1<<20)
	if err != nil {
		return nil, err
	}
	license, err := readRegular(filepath.Join(meta.root, "LICENSE"), 1<<20)
	if err != nil {
		return nil, err
	}
	return createZIP(meta.epoch, map[string][]byte{
		"spice/LICENSE": license, "spice/extension.toml": manifest, "spice/extension.wasm": wasm,
	})
}

func normalizeZIP(data []byte, epoch time.Time) ([]byte, error) {
	return normalizeZIPAtDepth(data, epoch, 0)
}

func normalizeZIPAtDepth(data []byte, epoch time.Time, depth int) ([]byte, error) {
	if depth > maxArchiveDepth {
		return nil, errors.New("nested archive exceeds depth limit")
	}
	reader, err := zip.NewReader(bytes.NewReader(data), int64(len(data)))
	if err != nil {
		return nil, err
	}
	if len(reader.File) > maxArchiveEntries {
		return nil, errors.New("archive exceeds entry limit")
	}
	entries := make(map[string][]byte, len(reader.File))
	var expanded uint64
	for _, item := range reader.File {
		if err := validateArchiveName(item.Name); err != nil {
			return nil, err
		}
		if _, duplicate := entries[item.Name]; duplicate {
			return nil, fmt.Errorf("duplicate archive entry %q", item.Name)
		}
		if item.FileInfo().IsDir() {
			entries[item.Name] = nil
			continue
		}
		content, err := readZIPEntry(item, maxArtifactBytes)
		if err != nil {
			return nil, err
		}
		expanded += uint64(len(content))
		if expanded > maxExpandedBytes {
			return nil, errors.New("archive exceeds expanded size limit")
		}
		if (strings.HasSuffix(strings.ToLower(item.Name), ".jar") || strings.HasSuffix(strings.ToLower(item.Name), ".zip")) && bytes.HasPrefix(content, []byte("PK\x03\x04")) {
			content, err = normalizeZIPAtDepth(content, epoch, depth+1)
			if err != nil {
				return nil, fmt.Errorf("normalize nested archive %s: %w", item.Name, err)
			}
		}
		entries[item.Name] = content
	}
	return createZIP(epoch, entries)
}

func createZIP(epoch time.Time, entries map[string][]byte) ([]byte, error) {
	if len(entries) > maxArchiveEntries {
		return nil, errors.New("archive exceeds entry limit")
	}
	names := make([]string, 0, len(entries))
	var expanded uint64
	for name := range entries {
		if err := validateArchiveName(name); err != nil {
			return nil, err
		}
		names = append(names, name)
		expanded += uint64(len(entries[name]))
	}
	if expanded > maxExpandedBytes {
		return nil, errors.New("archive exceeds expanded size limit")
	}
	slices.Sort(names)
	var buffer bytes.Buffer
	writer := zip.NewWriter(&buffer)
	for _, name := range names {
		header := &zip.FileHeader{Name: name, Method: zip.Deflate, Modified: epoch}
		if strings.HasSuffix(name, "/") {
			header.Method = zip.Store
			header.SetMode(fs.ModeDir | 0o755)
		} else {
			header.SetMode(0o644)
		}
		entry, err := writer.CreateHeader(header)
		if err != nil {
			return nil, err
		}
		if _, err := entry.Write(entries[name]); err != nil {
			return nil, err
		}
	}
	if err := writer.Close(); err != nil {
		return nil, err
	}
	return buffer.Bytes(), nil
}

func validateArchiveName(name string) error {
	containsControl := strings.IndexFunc(name, func(character rune) bool {
		return character < 0x20 || character == 0x7f
	}) >= 0
	if name == "" || containsControl || strings.ContainsAny(name, `\:`) || strings.HasPrefix(name, "/") || path.Clean(name) != strings.TrimSuffix(name, "/") || strings.HasPrefix(path.Clean(name), "../") {
		return fmt.Errorf("unsafe archive entry %q", name)
	}
	return nil
}

func readZIPEntry(item *zip.File, limit int64) ([]byte, error) {
	if item.UncompressedSize64 > uint64(limit) {
		return nil, fmt.Errorf("archive entry %q exceeds size limit", item.Name)
	}
	reader, err := item.Open()
	if err != nil {
		return nil, err
	}
	defer reader.Close()
	return io.ReadAll(io.LimitReader(reader, limit+1))
}

type spdxDocument struct {
	SPDXVersion       string             `json:"spdxVersion"`
	DataLicense       string             `json:"dataLicense"`
	SPDXID            string             `json:"SPDXID"`
	Name              string             `json:"name"`
	DocumentNamespace string             `json:"documentNamespace"`
	CreationInfo      spdxCreation       `json:"creationInfo"`
	Packages          []spdxPackage      `json:"packages"`
	Files             []spdxFile         `json:"files"`
	Relationships     []spdxRelationship `json:"relationships"`
}

type spdxCreation struct {
	Created  string   `json:"created"`
	Creators []string `json:"creators"`
}

type spdxPackage struct {
	Name                    string            `json:"name"`
	SPDXID                  string            `json:"SPDXID"`
	VersionInfo             string            `json:"versionInfo"`
	DownloadLocation        string            `json:"downloadLocation"`
	FilesAnalyzed           bool              `json:"filesAnalyzed"`
	PackageVerificationCode map[string]string `json:"packageVerificationCode"`
	LicenseConcluded        string            `json:"licenseConcluded"`
	LicenseDeclared         string            `json:"licenseDeclared"`
	CopyrightText           string            `json:"copyrightText"`
}

type spdxFile struct {
	FileName         string         `json:"fileName"`
	SPDXID           string         `json:"SPDXID"`
	Checksums        []spdxChecksum `json:"checksums"`
	LicenseConcluded string         `json:"licenseConcluded"`
	CopyrightText    string         `json:"copyrightText"`
}

type spdxChecksum struct {
	Algorithm     string `json:"algorithm"`
	ChecksumValue string `json:"checksumValue"`
}

type spdxRelationship struct {
	SPDXElementID      string `json:"spdxElementId"`
	RelationshipType   string `json:"relationshipType"`
	RelatedSPDXElement string `json:"relatedSpdxElement"`
}

func renderSBOM(meta metadata, artifact []byte) ([]byte, error) {
	sha256Sum := sha256.Sum256(artifact)
	fileSHA1 := sha1.Sum(artifact)
	verificationCode := sha1.Sum([]byte(hex.EncodeToString(fileSHA1[:])))
	namespace := sha256.Sum256([]byte(meta.config.Repository + "\n" + meta.base + "\n" + hex.EncodeToString(sha256Sum[:])))
	document := spdxDocument{
		SPDXVersion:       "SPDX-2.3",
		DataLicense:       "CC0-1.0",
		SPDXID:            "SPDXRef-DOCUMENT",
		Name:              meta.config.DisplayName + " " + meta.base,
		DocumentNamespace: "https://github.com/" + meta.config.Repository + "/releases/v" + meta.base + "/spdx/" + hex.EncodeToString(namespace[:]),
		CreationInfo: spdxCreation{
			Created:  meta.epoch.Format(time.RFC3339),
			Creators: []string{"Organization: Spice Framework", "Tool: Spice editor-release/v1"},
		},
		Packages: []spdxPackage{{
			Name:                    meta.config.DisplayName,
			SPDXID:                  "SPDXRef-Package",
			VersionInfo:             meta.base,
			DownloadLocation:        "NOASSERTION",
			FilesAnalyzed:           true,
			PackageVerificationCode: map[string]string{"packageVerificationCodeValue": hex.EncodeToString(verificationCode[:])},
			LicenseConcluded:        "Apache-2.0",
			LicenseDeclared:         "Apache-2.0",
			CopyrightText:           "NOASSERTION",
		}},
		Files: []spdxFile{{
			FileName: "./" + meta.names.Package,
			SPDXID:   "SPDXRef-File-Artifact",
			Checksums: []spdxChecksum{{
				Algorithm: "SHA256", ChecksumValue: hex.EncodeToString(sha256Sum[:]),
			}},
			LicenseConcluded: "Apache-2.0",
			CopyrightText:    "NOASSERTION",
		}},
		Relationships: []spdxRelationship{
			{SPDXElementID: "SPDXRef-DOCUMENT", RelationshipType: "DESCRIBES", RelatedSPDXElement: "SPDXRef-Package"},
			{SPDXElementID: "SPDXRef-Package", RelationshipType: "CONTAINS", RelatedSPDXElement: "SPDXRef-File-Artifact"},
		},
	}
	return canonicalJSON(document)
}

type statement struct {
	Type          string    `json:"_type"`
	Subject       []subject `json:"subject"`
	PredicateType string    `json:"predicateType"`
	Predicate     predicate `json:"predicate"`
}

type subject struct {
	Name   string            `json:"name"`
	Digest map[string]string `json:"digest"`
}

type predicate struct {
	BuildDefinition buildDefinition `json:"buildDefinition"`
	RunDetails      runDetails      `json:"runDetails"`
}

type buildDefinition struct {
	BuildType            string               `json:"buildType"`
	ExternalParameters   map[string]string    `json:"externalParameters"`
	InternalParameters   map[string]int64     `json:"internalParameters"`
	ResolvedDependencies []resolvedDependency `json:"resolvedDependencies"`
}

type resolvedDependency struct {
	URI    string            `json:"uri"`
	Digest map[string]string `json:"digest"`
}

type runDetails struct {
	Builder  map[string]string `json:"builder"`
	Metadata map[string]bool   `json:"metadata"`
}

func renderProvenance(meta metadata, artifact []byte) ([]byte, error) {
	sum := sha256.Sum256(artifact)
	value := statement{
		Type:          "https://in-toto.io/Statement/v1",
		PredicateType: "https://slsa.dev/provenance/v1",
		Subject: []subject{{
			Name: meta.names.Package, Digest: map[string]string{"sha256": hex.EncodeToString(sum[:])},
		}},
		Predicate: predicate{
			BuildDefinition: buildDefinition{
				BuildType:          "https://github.com/" + meta.config.Repository + "/release-tools/editor-release/v1",
				ExternalParameters: map[string]string{"repository": meta.config.Repository, "version": "v" + meta.base},
				InternalParameters: map[string]int64{"sourceDateEpoch": meta.epoch.Unix()},
				ResolvedDependencies: []resolvedDependency{{
					URI:    "git+https://github.com/" + meta.config.Repository + "@refs/tags/v" + meta.base,
					Digest: map[string]string{"gitCommit": meta.commit},
				}},
			},
			RunDetails: runDetails{
				Builder:  map[string]string{"id": "https://github.com/" + meta.config.Repository + "/actions/workflows/release.yml@" + meta.commit},
				Metadata: map[string]bool{"reproducible": true},
			},
		},
	}
	return canonicalJSONLine(value)
}

func canonicalJSON(value any) ([]byte, error) {
	data, err := json.MarshalIndent(value, "", "  ")
	if err != nil {
		return nil, err
	}
	return append(data, '\n'), nil
}

func canonicalJSONLine(value any) ([]byte, error) {
	data, err := json.Marshal(value)
	if err != nil {
		return nil, err
	}
	return append(data, '\n'), nil
}

func renderChecksums(files map[string][]byte) []byte {
	names := make([]string, 0, len(files))
	for name := range files {
		names = append(names, name)
	}
	slices.Sort(names)
	var result strings.Builder
	for _, name := range names {
		sum := sha256.Sum256(files[name])
		fmt.Fprintf(&result, "%x  %s\n", sum, name)
	}
	return []byte(result.String())
}

func validateUnsigned(meta metadata, files map[string][]byte) error {
	want := []string{"checksums.txt", meta.names.Package, meta.names.Provenance, meta.names.SBOM}
	if err := exactNames(files, want); err != nil {
		return err
	}
	checksummed := map[string][]byte{
		meta.names.Package:    files[meta.names.Package],
		meta.names.Provenance: files[meta.names.Provenance],
		meta.names.SBOM:       files[meta.names.SBOM],
	}
	if !bytes.Equal(files["checksums.txt"], renderChecksums(checksummed)) {
		return errors.New("checksums.txt is not canonical or does not authenticate exact artifacts")
	}
	expectedSBOM, err := renderSBOM(meta, files[meta.names.Package])
	if err != nil {
		return err
	}
	if !bytes.Equal(files[meta.names.SBOM], expectedSBOM) {
		return errors.New("SBOM does not match artifact identity")
	}
	expectedProvenance, err := renderProvenance(meta, files[meta.names.Package])
	if err != nil {
		return err
	}
	if !bytes.Equal(files[meta.names.Provenance], expectedProvenance) {
		return errors.New("provenance does not match artifact identity")
	}
	return validatePackage(meta, files[meta.names.Package])
}

func validateSigned(meta metadata, files map[string][]byte, trusted ed25519.PublicKey) error {
	want := []string{"checksums.txt", "checksums.txt.pem", "checksums.txt.sig", meta.names.Package, meta.names.Provenance, meta.names.SBOM}
	if err := exactNames(files, want); err != nil {
		return err
	}
	unsigned := make(map[string][]byte, 4)
	for _, name := range []string{"checksums.txt", meta.names.Package, meta.names.Provenance, meta.names.SBOM} {
		unsigned[name] = files[name]
	}
	if err := validateUnsigned(meta, unsigned); err != nil {
		return err
	}
	emitted, err := parsePublicKey(files["checksums.txt.pem"])
	if err != nil {
		return err
	}
	if !bytes.Equal(emitted, trusted) {
		return errors.New("emitted public key does not match committed trust anchor")
	}
	if len(files["checksums.txt.sig"]) != ed25519.SignatureSize || !ed25519.Verify(trusted, files["checksums.txt"], files["checksums.txt.sig"]) {
		return errors.New("invalid Ed25519 checksum signature")
	}
	return nil
}

func validatePackage(meta metadata, data []byte) error {
	reader, err := zip.NewReader(bytes.NewReader(data), int64(len(data)))
	if err != nil {
		return err
	}
	seen := map[string]bool{}
	hasPluginXML := false
	pluginJARs := 0
	if len(reader.File) > maxArchiveEntries {
		return errors.New("package exceeds entry limit")
	}
	for _, item := range reader.File {
		if err := validateArchiveName(item.Name); err != nil {
			return err
		}
		if seen[item.Name] {
			return fmt.Errorf("duplicate package entry %q", item.Name)
		}
		seen[item.Name] = true
		if !item.Modified.Equal(meta.epoch) {
			return fmt.Errorf("package entry %q has nondeterministic timestamp", item.Name)
		}
		if strings.HasSuffix(item.Name, "META-INF/plugin.xml") {
			hasPluginXML = true
		}
		if !item.FileInfo().IsDir() && strings.HasSuffix(strings.ToLower(item.Name), ".jar") {
			pluginJARs++
			content, readErr := readZIPEntry(item, maxArtifactBytes)
			if readErr != nil {
				return readErr
			}
			if inspectErr := inspectPluginJAR(meta, content); inspectErr != nil {
				return fmt.Errorf("inspect plugin archive %s: %w", item.Name, inspectErr)
			}
			hasPluginXML = true
			if validateErr := validateZIPMetadata(content, meta.epoch, 1); validateErr != nil {
				return fmt.Errorf("validate nested plugin archive %s: %w", item.Name, validateErr)
			}
		}
	}
	if meta.config.Kind == "goland" {
		for name := range seen {
			if !strings.HasPrefix(name, "spice-goland/") {
				return fmt.Errorf("GoLand package entry %q is outside spice-goland root", name)
			}
		}
		if !hasPluginXML || pluginJARs != 1 {
			return fmt.Errorf("GoLand package requires one plugin JAR containing META-INF/plugin.xml; got jars=%d descriptor=%t", pluginJARs, hasPluginXML)
		}
	}
	if meta.config.Kind == "zed" {
		want := []string{"spice/LICENSE", "spice/extension.toml", "spice/extension.wasm"}
		actual := make([]string, 0, len(seen))
		for name := range seen {
			actual = append(actual, name)
		}
		slices.Sort(actual)
		if !slices.Equal(actual, want) {
			return fmt.Errorf("Zed package entries %v do not match %v", actual, want)
		}
	}
	return nil
}

type pluginDescriptor struct {
	XMLName xml.Name `xml:"idea-plugin"`
	ID      string   `xml:"id"`
	Version string   `xml:"version"`
}

func inspectPluginJAR(meta metadata, data []byte) error {
	reader, err := zip.NewReader(bytes.NewReader(data), int64(len(data)))
	if err != nil {
		return err
	}
	var descriptor, license []byte
	for _, item := range reader.File {
		if err := validateArchiveName(item.Name); err != nil {
			return err
		}
		switch item.Name {
		case "META-INF/plugin.xml":
			descriptor, err = readZIPEntry(item, 1<<20)
		case "LICENSE":
			license, err = readZIPEntry(item, 1<<20)
		}
		if err != nil {
			return err
		}
	}
	if len(descriptor) == 0 {
		return errors.New("plugin JAR lacks META-INF/plugin.xml")
	}
	var plugin pluginDescriptor
	if err := xml.Unmarshal(descriptor, &plugin); err != nil {
		return fmt.Errorf("decode META-INF/plugin.xml: %w", err)
	}
	if plugin.XMLName.Local != "idea-plugin" || strings.TrimSpace(plugin.ID) != meta.config.PluginID || strings.TrimSpace(plugin.Version) != meta.base {
		return fmt.Errorf("plugin descriptor identity id=%q version=%q does not match id=%q version=%q", strings.TrimSpace(plugin.ID), strings.TrimSpace(plugin.Version), meta.config.PluginID, meta.base)
	}
	committedLicense, err := readRegular(filepath.Join(meta.root, "LICENSE"), 1<<20)
	if err != nil {
		return fmt.Errorf("read committed license: %w", err)
	}
	if !bytes.Equal(license, committedLicense) {
		return errors.New("plugin JAR license is absent or differs from committed LICENSE")
	}
	return nil
}

func validateZIPMetadata(data []byte, epoch time.Time, depth int) error {
	if depth > maxArchiveDepth {
		return errors.New("nested archive exceeds depth limit")
	}
	reader, err := zip.NewReader(bytes.NewReader(data), int64(len(data)))
	if err != nil {
		return err
	}
	if len(reader.File) > maxArchiveEntries {
		return errors.New("archive exceeds entry limit")
	}
	seen := make(map[string]bool, len(reader.File))
	var expanded uint64
	for _, item := range reader.File {
		if err := validateArchiveName(item.Name); err != nil {
			return err
		}
		if seen[item.Name] {
			return fmt.Errorf("duplicate archive entry %q", item.Name)
		}
		seen[item.Name] = true
		if !item.Modified.Equal(epoch) {
			return fmt.Errorf("archive entry %q has nondeterministic timestamp", item.Name)
		}
		expanded += item.UncompressedSize64
		if expanded > maxExpandedBytes {
			return errors.New("archive exceeds expanded size limit")
		}
		if !item.FileInfo().IsDir() && (strings.HasSuffix(strings.ToLower(item.Name), ".jar") || strings.HasSuffix(strings.ToLower(item.Name), ".zip")) {
			content, readErr := readZIPEntry(item, maxArtifactBytes)
			if readErr != nil {
				return readErr
			}
			if bytes.HasPrefix(content, []byte("PK\x03\x04")) {
				if err := validateZIPMetadata(content, epoch, depth+1); err != nil {
					return err
				}
			}
		}
	}
	return nil
}

func exactNames(files map[string][]byte, want []string) error {
	actual := make([]string, 0, len(files))
	for name := range files {
		actual = append(actual, name)
	}
	slices.Sort(actual)
	slices.Sort(want)
	if !slices.Equal(actual, want) {
		return fmt.Errorf("artifact names %v do not match %v", actual, want)
	}
	return nil
}

func signingKeyFromEnvironment() (ed25519.PrivateKey, error) {
	encoded := os.Getenv(signingKeyEnv)
	if encoded == "" {
		return nil, fmt.Errorf("%s is required", signingKeyEnv)
	}
	data, err := base64.StdEncoding.DecodeString(encoded)
	if err != nil {
		return nil, fmt.Errorf("decode signing key: %w", err)
	}
	if block, _ := pem.Decode(data); block != nil {
		data = block.Bytes
	}
	if parsed, parseErr := x509.ParsePKCS8PrivateKey(data); parseErr == nil {
		key, ok := parsed.(ed25519.PrivateKey)
		if !ok {
			return nil, errors.New("signing key is not Ed25519")
		}
		return append(ed25519.PrivateKey(nil), key...), nil
	}
	if len(data) == ed25519.SeedSize {
		return ed25519.NewKeyFromSeed(data), nil
	}
	return nil, errors.New("signing key must be base64 PKCS#8 Ed25519 or a 32-byte seed")
}

func parsePublicKey(data []byte) (ed25519.PublicKey, error) {
	block, rest := pem.Decode(data)
	if block == nil || block.Type != "PUBLIC KEY" || len(bytes.TrimSpace(rest)) != 0 {
		return nil, errors.New("public key is not one canonical PEM block")
	}
	parsed, err := x509.ParsePKIXPublicKey(block.Bytes)
	if err != nil {
		return nil, err
	}
	key, ok := parsed.(ed25519.PublicKey)
	if !ok || len(key) != ed25519.PublicKeySize {
		return nil, errors.New("public key is not Ed25519")
	}
	canonical, err := encodePublicKey(key)
	if err != nil {
		return nil, err
	}
	if !bytes.Equal(data, canonical) {
		return nil, errors.New("public key PEM is not canonical")
	}
	return append(ed25519.PublicKey(nil), key...), nil
}
func encodePublicKey(key ed25519.PublicKey) ([]byte, error) {
	der, err := x509.MarshalPKIXPublicKey(key)
	if err != nil {
		return nil, err
	}
	return pem.EncodeToMemory(&pem.Block{Type: "PUBLIC KEY", Bytes: der}), nil
}

func readRegular(name string, limit int64) ([]byte, error) {
	info, err := os.Lstat(name)
	if err != nil {
		return nil, err
	}
	if !info.Mode().IsRegular() {
		return nil, errors.New("not a regular file")
	}
	if info.Size() > limit {
		return nil, fmt.Errorf("file exceeds %d bytes", limit)
	}
	file, err := os.Open(name)
	if err != nil {
		return nil, err
	}
	defer file.Close()
	data, err := io.ReadAll(io.LimitReader(file, limit+1))
	if err != nil {
		return nil, err
	}
	if int64(len(data)) > limit {
		return nil, errors.New("file exceeds size limit")
	}
	return data, nil
}
func readDirectory(directory string, limit int64) (map[string][]byte, error) {
	entries, err := os.ReadDir(directory)
	if err != nil {
		return nil, err
	}
	files := make(map[string][]byte, len(entries))
	for _, entry := range entries {
		if entry.IsDir() || entry.Type()&fs.ModeSymlink != 0 || filepath.Base(entry.Name()) != entry.Name() {
			return nil, fmt.Errorf("unexpected non-file artifact %q", entry.Name())
		}
		data, err := readRegular(filepath.Join(directory, entry.Name()), limit)
		if err != nil {
			return nil, err
		}
		files[entry.Name()] = data
	}
	return files, nil
}
func writeNewDirectory(directory string, files map[string][]byte) error {
	if directory == "" {
		return errors.New("output directory is required")
	}
	if _, err := os.Lstat(directory); !errors.Is(err, fs.ErrNotExist) {
		return errors.New("output directory already exists")
	}
	parent := filepath.Dir(directory)
	staging, err := os.MkdirTemp(parent, ".editor-release-*")
	if err != nil {
		return err
	}
	defer os.RemoveAll(staging)
	names := make([]string, 0, len(files))
	for name := range files {
		names = append(names, name)
	}
	slices.Sort(names)
	for _, name := range names {
		if filepath.Base(name) != name {
			return errors.New("unsafe artifact name")
		}
		if err := os.WriteFile(filepath.Join(staging, name), files[name], 0o644); err != nil {
			return err
		}
	}
	return os.Rename(staging, directory)
}
