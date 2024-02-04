function check() {
  for i in {1..10}
  do
    if grep -q "$1" $2; then
      break
    else
      sleep 1
    fi
  done
}

cleanup() {
  jps | grep -v Jps | awk '{print $1}' | xargs -I {} kill {}
  exit 0
}

trap cleanup SIGINT SIGTERM

sh /home/rocketmq/rocketmq-4.6.0/bin/mqnamesrv > ~/ns.log &
check "The Name Server boot success" ~/ns.log

sh  /home/rocketmq/rocketmq-4.6.0/bin/mqbroker -n 127.0.0.1:9876 -c /broker.conf > ~/broker.log &
check "boot success" ~/broker.log

echo "--JoeKerouac--"

while true
do
  sleep 1
done
