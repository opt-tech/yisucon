package config

import (
	"os"
	"time"
)

var PortalHost = os.Getenv("OPT_ISUCON_PORTAL_HOST")

const (
	MaxWorkerCount     = 5
	MaxCheckers        = 30
	InitializeTimeout  = time.Second * 10
	BenchTimeLimit     = time.Minute
	QueueCheckDuration = time.Second * 2
	RequestTimeout     = time.Second * 30
	BenchMarkerUA      = "OPTISUCON"
	LogFilePath        = "/tmp/isucon/benchmarker.log"
)
