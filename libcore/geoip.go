package libcore

import (
	"fmt"
	"net"
	"strings"

	"github.com/oschwald/maxminddb-golang"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/option"
)

type geoipReader struct {
	reader *maxminddb.Reader
}

func openGeoIP(path string) (*geoipReader, error) {
	reader, err := maxminddb.Open(path)
	if err != nil {
		return nil, err
	}
	return &geoipReader{reader: reader}, nil
}

func (g *geoipReader) Close() {
	g.reader.Close()
}

func (g *geoipReader) Rules(countryCode string) ([]option.HeadlessRule, error) {
	networks := g.reader.Networks(maxminddb.SkipAliasedNetworks)
	countryMap := make(map[string][]*net.IPNet)
	var (
		ipNet           *net.IPNet
		nextCountryCode string
		err             error
	)
	for networks.Next() {
		ipNet, err = networks.Network(&nextCountryCode)
		if err != nil {
			return nil, fmt.Errorf("failed to get network: %w", err)
		}
		countryMap[nextCountryCode] = append(countryMap[nextCountryCode], ipNet)
	}

	ipNets := countryMap[strings.ToLower(countryCode)]
	if len(ipNets) == 0 {
		return nil, fmt.Errorf("no networks found for country code: %s", countryCode)
	}

	var headlessRule option.DefaultHeadlessRule
	headlessRule.IPCIDR = make([]string, 0, len(ipNets))
	for _, cidr := range ipNets {
		headlessRule.IPCIDR = append(headlessRule.IPCIDR, cidr.String())
	}

	return []option.HeadlessRule{
		{
			Type:           C.RuleTypeDefault,
			DefaultOptions: headlessRule,
		},
	}, nil
}
