#!/usr/bin/env bash

# Upload files to cockfile.com.
# Files stay up for 24 hours (86400 seconds).

log=/tmp/upload-$RANDOM.log

curl -i -F name=$(basename "$1") -F file=@"$1" 'https://cockfile.com/api.php?d=upload-tool' > ${log}
status=$?

if [[ ${status} -ne 0 ]]; then
    exit 1
fi

cat<<EOF
upload_url $(tail -1 ${log})
upload_duration 86400
EOF

rm ${log}