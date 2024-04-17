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

function createTopic() {
    sh /home/rocketmq/rocketmq-5.1.4/bin/mqadmin updateTopic -n 127.0.0.1:9876 -b 127.0.0.1:10911 -t $1
}

cleanup() {
  jps | grep -v Jps | awk '{print $1}' | xargs -I {} kill {}
  exit 0
}

trap cleanup SIGINT SIGTERM

sh /home/rocketmq/rocketmq-5.1.4/bin/mqnamesrv > ~/ns.log 2>&1 &
check "The Name Server boot success" ~/ns.log

sh  /home/rocketmq/rocketmq-5.1.4/bin/mqbroker -n 127.0.0.1:9876 -c /broker.conf > ~/broker.log 2>&1 &
check "boot success" ~/broker.log

createTopic testSend
createTopic testSendOneway
createTopic testSendAsync
createTopic tracingMessageListenerConcurrently
createTopic tracingMessageListenerOrderly
createTopic testAll

echo "--JoeKerouac--"

while true
do
  sleep 1
done
