package libcore

import (
	"crypto/sha256"
	stdjson "encoding/json"
	"encoding/hex"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"sync"

	"github.com/sagernet/sing-box/common/srs"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/option"
)

var resolveGeoRuleSetsMu sync.Mutex

func srsCacheDir() string {
	base := internalAssetsPath
	if base == "" {
		base = os.TempDir()
	}
	return filepath.Join(base, "srs_cache")
}

// resolveGeoRuleSets rewrites legacy `geoip:<code>` and `geosite:<code>`
// local rule-set placeholders emitted by ConfigBuilder into cached binary `.srs`
// files compiled from `externalAssetsPath/{geoip,geosite}.db` so upstream
// sing-box LocalRuleSet can open them directly from disk.
func resolveGeoRuleSets(configContent string) (string, error) {
	if !strings.Contains(configContent, `"geoip:`) && !strings.Contains(configContent, `"geosite:`) {
		return configContent, nil
	}

	resolveGeoRuleSetsMu.Lock()
	defer resolveGeoRuleSetsMu.Unlock()

	var root map[string]stdjson.RawMessage
	if err := stdjson.Unmarshal([]byte(configContent), &root); err != nil {
		return configContent, nil
	}
	rawRoute, ok := root["route"]
	if !ok {
		return configContent, nil
	}
	var route map[string]stdjson.RawMessage
	if err := stdjson.Unmarshal(rawRoute, &route); err != nil {
		return configContent, nil
	}
	rawRuleSet, ok := route["rule_set"]
	if !ok {
		return configContent, nil
	}
	var ruleSets []map[string]any
	if err := stdjson.Unmarshal(rawRuleSet, &ruleSets); err != nil {
		return configContent, nil
	}

	var (
		geoipR   *geoipReader
		geositeR *geositeReader
	)
	defer func() {
		if geoipR != nil {
			geoipR.Close()
		}
		if geositeR != nil {
			geositeR.Close()
		}
	}()

	modified := false
	for _, entry := range ruleSets {
		pathStr, _ := entry["path"].(string)
		var (
			isGeoIP   bool
			isGeoSite bool
			code      string
			dbName    string
		)
		switch {
		case strings.HasPrefix(pathStr, "geoip:"):
			isGeoIP = true
			code = strings.TrimPrefix(pathStr, "geoip:")
			dbName = geoipDat
		case strings.HasPrefix(pathStr, "geosite:"):
			isGeoSite = true
			code = strings.TrimPrefix(pathStr, "geosite:")
			dbName = geositeDat
		default:
			continue
		}

		dbPath := filepath.Join(externalAssetsPath, dbName)
		dbInfo, err := os.Stat(dbPath)
		if err != nil {
			// Asset DB not present yet; emit an empty fallback rule-set rather than aborting service
			sum := sha256.Sum256([]byte(pathStr))
			key := hex.EncodeToString(sum[:16])
			cacheDir := srsCacheDir()
			srsPath := filepath.Join(cacheDir, key+".srs")
			_ = writeRuleSetSrs(srsPath, "", "", nil)
			entry["type"] = "local"
			entry["format"] = "binary"
			entry["path"] = srsPath
			modified = true
			continue
		}

		sum := sha256.Sum256([]byte(pathStr))
		key := hex.EncodeToString(sum[:16])
		cacheDir := srsCacheDir()
		srsPath := filepath.Join(cacheDir, key+".srs")
		metaPath := filepath.Join(cacheDir, key+".meta")
		expectedMeta := fmt.Sprintf("%d:%d", dbInfo.Size(), dbInfo.ModTime().UnixNano())

		upToDate := false
		if _, err := os.Stat(srsPath); err == nil {
			if metaBytes, err := os.ReadFile(metaPath); err == nil && string(metaBytes) == expectedMeta {
				upToDate = true
			}
		}

		if !upToDate {
			var (
				rules []option.HeadlessRule
				err   error
			)
			if isGeoIP {
				if geoipR == nil {
					geoipR, err = openGeoIP(dbPath)
				}
				if err == nil {
					rules, err = geoipR.Rules(code)
					if err != nil {
						rules = nil
					}
				}
			} else if isGeoSite {
				if geositeR == nil {
					geositeR, err = openGeoSite(dbPath)
				}
				if err == nil {
					rules, err = geositeR.Rules(code)
					if err != nil {
						rules = nil
					}
				}
			}
			if err := writeRuleSetSrs(srsPath, metaPath, expectedMeta, rules); err != nil {
				return "", fmt.Errorf("write rule-set %s: %w", pathStr, err)
			}
		}

		entry["type"] = "local"
		entry["format"] = "binary"
		entry["path"] = srsPath
		modified = true
	}

	if !modified {
		return configContent, nil
	}

	newRuleSet, err := stdjson.Marshal(ruleSets)
	if err != nil {
		return "", err
	}
	route["rule_set"] = newRuleSet
	newRoute, err := stdjson.Marshal(route)
	if err != nil {
		return "", err
	}
	root["route"] = newRoute
	out, err := stdjson.Marshal(root)
	if err != nil {
		return "", err
	}
	return string(out), nil
}

func writeRuleSetSrs(srsPath, metaPath, meta string, rules []option.HeadlessRule) error {
	if err := os.MkdirAll(filepath.Dir(srsPath), 0o755); err != nil {
		return err
	}
	tmpPath := srsPath + ".tmp"
	f, err := os.OpenFile(tmpPath, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, 0o644)
	if err != nil {
		return err
	}
	writeErr := srs.Write(f, option.PlainRuleSet{Rules: rules}, C.RuleSetVersionCurrent)
	closeErr := f.Close()
	if writeErr != nil {
		_ = os.Remove(tmpPath)
		return writeErr
	}
	if closeErr != nil {
		_ = os.Remove(tmpPath)
		return closeErr
	}
	if err := os.Rename(tmpPath, srsPath); err != nil {
		_ = os.Remove(tmpPath)
		return err
	}
	_ = os.WriteFile(metaPath, []byte(meta), 0o644)
	return nil
}
