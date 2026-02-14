#!
cd /storage/internal_new/project/AppsUsageMonitor && find app/src/main/res/layout -name "*.xml" -exec sh -c 'echo "\n===== $(basename {}) =====\n"; cat {}' \; > layouts.txt