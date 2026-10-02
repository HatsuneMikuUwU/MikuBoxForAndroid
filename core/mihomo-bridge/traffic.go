package main

import (
	"encoding/json"
	"sync"
	"time"

	"github.com/metacubex/mihomo/tunnel/statistic"
)

// trafficSampler keeps per-proxy traffic for the running session.
//
// The core's statistic manager only holds *open* connections: a tracker and its
// byte counters disappear the moment a connection closes, so the totals cannot
// be read off the live list. The sampler therefore walks the live connections
// on a short interval, remembers the last value seen for each connection and
// folds the difference into every proxy of that connection's chain — which
// holds the group the traffic was routed through and the node that carried it.
// Transfers that live shorter than one interval can be missed, but they are the
// ones that carry the least data.
type trafficSampler struct {
	mu       sync.Mutex
	byProxy  map[string]*proxyTraffic
	lastSeen map[string][2]int64
	baseline [2]int64
	stop     chan struct{}
	done     chan struct{}
}

// proxyTraffic is the session total carried by one node or proxy group.
type proxyTraffic struct {
	Upload   int64 `json:"upload"`
	Download int64 `json:"download"`
}

const trafficSampleInterval = 500 * time.Millisecond

var sampler = &trafficSampler{}

// startTrafficSampler resets the session totals and begins sampling.
func startTrafficSampler() {
	sampler.mu.Lock()
	// Fold anything still pending from a previous session before resetting, so
	// a restart never loses the bytes of a connection that was still open.
	if sampler.done != nil {
		sampler.mu.Unlock()
		stopTrafficSampler()
		sampler.mu.Lock()
	}
	sampler.byProxy = map[string]*proxyTraffic{}
	sampler.lastSeen = map[string][2]int64{}
	sampler.baseline = currentTotals()
	sampler.stop = make(chan struct{})
	sampler.done = make(chan struct{})
	stop, done := sampler.stop, sampler.done
	sampler.mu.Unlock()

	go func() {
		defer close(done)
		ticker := time.NewTicker(trafficSampleInterval)
		defer ticker.Stop()
		for {
			select {
			case <-stop:
				sampleTraffic()
				return
			case <-ticker.C:
				sampleTraffic()
			}
		}
	}()
}

// stopTrafficSampler folds the last interval in and stops the sampler. The
// breakdown is dropped afterwards: the app has folded it into the profile's
// stored totals by then, and keeping it would count the same bytes twice.
func stopTrafficSampler() {
	sampler.mu.Lock()
	stop, done := sampler.stop, sampler.done
	sampler.stop, sampler.done = nil, nil
	sampler.mu.Unlock()
	if stop == nil {
		return
	}
	close(stop)
	<-done

	sampler.mu.Lock()
	sampler.byProxy = nil
	sampler.lastSeen = nil
	sampler.mu.Unlock()
}

// trafficByProxy reports the session totals per proxy and group as JSON.
func trafficByProxy() string {
	sampler.mu.Lock()
	defer sampler.mu.Unlock()
	if sampler.byProxy == nil {
		return "{}"
	}
	payload, err := json.Marshal(sampler.byProxy)
	if err != nil {
		return "{}"
	}
	return string(payload)
}

// trafficSession reports the traffic moved since the session started.
func trafficSession() (up int64, down int64) {
	sampler.mu.Lock()
	baseline := sampler.baseline
	sampler.mu.Unlock()

	totals := currentTotals()
	return totals[0] - baseline[0], totals[1] - baseline[1]
}

// sampleTraffic folds the bytes moved since the previous pass into the totals of
// every proxy in each connection's chain.
func sampleTraffic() {
	type connection struct {
		id       string
		chain    []string
		up, down int64
	}

	var live []connection
	statistic.DefaultManager.Range(func(tracker statistic.Tracker) bool {
		info := tracker.Info()
		if info == nil {
			return true
		}
		live = append(live, connection{
			id:    tracker.ID(),
			chain: info.Chain,
			up:    info.UploadTotal.Load(),
			down:  info.DownloadTotal.Load(),
		})
		return true
	})

	sampler.mu.Lock()
	defer sampler.mu.Unlock()
	if sampler.byProxy == nil {
		return
	}
	seen := make(map[string]struct{}, len(live))
	for _, conn := range live {
		seen[conn.id] = struct{}{}
		previous, known := sampler.lastSeen[conn.id]
		if !known {
			previous = [2]int64{}
		}
		deltaUp := conn.up - previous[0]
		deltaDown := conn.down - previous[1]
		sampler.lastSeen[conn.id] = [2]int64{conn.up, conn.down}
		if deltaUp < 0 {
			deltaUp = 0
		}
		if deltaDown < 0 {
			deltaDown = 0
		}
		if deltaUp == 0 && deltaDown == 0 {
			continue
		}
		for _, name := range conn.chain {
			entry := sampler.byProxy[name]
			if entry == nil {
				entry = &proxyTraffic{}
				sampler.byProxy[name] = entry
			}
			entry.Upload += deltaUp
			entry.Download += deltaDown
		}
	}
	// A connection that is gone has had all of its bytes folded in above.
	for id := range sampler.lastSeen {
		if _, ok := seen[id]; !ok {
			delete(sampler.lastSeen, id)
		}
	}
}

func currentTotals() [2]int64 {
	up, down := statistic.DefaultManager.Total()
	return [2]int64{up, down}
}
