#!/usr/bin/env bash
# exaroton API helper for AEGIR. Token is read from %USERPROFILE%/.aegir-secrets/exaroton.token (never committed).
# Usage: tools/exa.sh <server: aegir|aegir-test|ID> <command> [args]
#   list                          all servers (server arg ignored, use "-")
#   status                        name, status, players, RAM
#   start | stop | restart
#   cmd "<console command>"       run a console command
#   logs                          latest log
#   ram [GB]                      get or set RAM
#   get <remote path> [local]     download a file (e.g. config/voicechat/voicechat-server.properties)
#   put <local file> <remote path>
#   ls [remote dir]               list a directory
#   credits                       account credits
set -euo pipefail
SECRETS="${USERPROFILE:-$HOME}/.aegir-secrets"
TOKEN=$(tr -d '\r\n' < "$SECRETS/exaroton.token")
API=https://api.exaroton.com/v1
auth=(-H "Authorization: Bearer $TOKEN")
resolve() {  # server name -> ID, using $SECRETS/servers.env (AEGIR_ID=..., AEGIR_TEST_ID=...)
  case "$1" in
    aegir)      grep '^AEGIR_ID=' "$SECRETS/servers.env" | cut -d= -f2 | tr -d '\r' ;;
    aegir-test) grep '^AEGIR_TEST_ID=' "$SECRETS/servers.env" | cut -d= -f2 | tr -d '\r' ;;
    *)          echo "$1" ;;
  esac
}
call() { curl -sS --fail-with-body "${auth[@]}" "$@"; echo; }
SRV=$(resolve "${1:?server}"); CMD=${2:?command}; shift 2
case "$CMD" in
  list)    call "$API/servers/" | jq -r '.data[] | "\(.id)  \(.name)  status=\(.status)  \(.address)"' ;;
  status)  call "$API/servers/$SRV/" | jq '.data | {name, status, address, players: .players.list, software: .software.version}'
           call "$API/servers/$SRV/options/ram/" | jq -c '.data' ;;
  start|stop|restart) call "$API/servers/$SRV/$CMD/" | jq -c . ;;
  cmd)     call -X POST -H "Content-Type: application/json" -d "$(jq -n --arg c "$1" '{command:$c}')" "$API/servers/$SRV/command/" | jq -c . ;;
  logs)    call "$API/servers/$SRV/logs/" | jq -r '.data.content' ;;
  ram)     if [ $# -ge 1 ]; then call -X POST -H "Content-Type: application/json" -d "{\"ram\":$1}" "$API/servers/$SRV/options/ram/" | jq -c .;
           else call "$API/servers/$SRV/options/ram/" | jq -c '.data'; fi ;;
  get)     curl -sS --fail "${auth[@]}" -o "${2:-$(basename "$1")}" "$API/servers/$SRV/files/data/$1" ;;
  put)     curl -sS --fail-with-body "${auth[@]}" -X PUT -H "Content-Type: application/octet-stream" --data-binary "@$1" "$API/servers/$SRV/files/data/$2"; echo ;;
  ls)      call "$API/servers/$SRV/files/info/${1:-}" | jq -r '.data.children[]? | "\(if .isDirectory then "d" else "-" end) \(.size)\t\(.name)"' ;;
  credits) call "$API/account/" | jq '.data | {name, credits}' ;;
  *) echo "unknown command $CMD" >&2; exit 2 ;;
esac
