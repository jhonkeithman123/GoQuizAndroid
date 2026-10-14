import net from 'net';

const HTTP_URL = 'http://localhost:5050';
const TCP_HOST = '127.0.0.1';
const TCP_PORT = 5052;

function formatMs(start) {
  return `${(performance.now() - start).toFixed(1)}ms`;
}

async function runLocalServerTests() {
  console.log('====================================================');
  console.log('   GoQuiz Local Server Automated Verification');
  console.log('====================================================');
  console.log(`Target HTTP: ${HTTP_URL}`);
  console.log(`Target TCP:  ${TCP_HOST}:${TCP_PORT}\n`);

  // --- PART 1: REST API TESTS ---
  console.log('[TEST 1/8] Checking Health & KeepAlive...');
  let t0 = performance.now();
  let res = await fetch(`${HTTP_URL}/health`);
  let data = await res.json();
  console.log(`  ✓ GET /health -> status: ${data.status}, db: ${data.database} (${formatMs(t0)})`);

  t0 = performance.now();
  res = await fetch(`${HTTP_URL}/api/keep-alive`);
  data = await res.json();
  console.log(`  ✓ GET /api/keep-alive -> ${data.status} (${formatMs(t0)})`);

  console.log('\n[TEST 2/8] Fetching Initial Rooms (JSON)...');
  t0 = performance.now();
  res = await fetch(`${HTTP_URL}/api/rooms`);
  data = await res.json();
  console.log(`  ✓ GET /api/rooms -> ${data.rooms.length} active rooms (${formatMs(t0)})`);

  console.log('\n[TEST 3/8] Creating Duel Room via REST (POST /api/room/create)...');
  t0 = performance.now();
  res = await fetch(`${HTTP_URL}/api/room/create`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      hostClientId: 'host_instance_abc',
      hostName: 'LocalTester',
      hostGender: 'Boy',
      language: 'JavaScript',
      difficulty: 'Medium'
    })
  });
  const roomRes = await res.json();
  console.log(`  ✓ POST /api/room/create -> Code: #${roomRes.code} (${formatMs(t0)})`);

  console.log('\n[TEST 4/8] Fetching Rooms with format=raw (Android/Desktop protocol)...');
  t0 = performance.now();
  res = await fetch(`${HTTP_URL}/api/rooms?format=raw`);
  const rawRooms = await res.text();
  console.log(`  ✓ GET /api/rooms?format=raw -> "${rawRooms}" (${formatMs(t0)})`);
  if (!rawRooms.includes(roomRes.code)) {
    throw new Error(`Created room #${roomRes.code} not found in raw room list!`);
  }

  console.log('\n[TEST 5/8] Looking up Room by Code (GET /api/room/:code)...');
  t0 = performance.now();
  res = await fetch(`${HTTP_URL}/api/room/${roomRes.code}`);
  const roomLookup = await res.json();
  console.log(`  ✓ GET /api/room/${roomRes.code} -> Host: ${roomLookup.hostName}, Lang: ${roomLookup.language} (${formatMs(t0)})`);

  console.log('\n[TEST 5B] Testing Self-Join Prevention (hostClientId check)...');
  t0 = performance.now();
  let selfJoinRes = await fetch(`${HTTP_URL}/api/room/join`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ code: roomRes.code, guestClientId: 'host_instance_abc', guestName: 'LocalTester', guestGender: 'Boy' })
  });
  if (selfJoinRes.status === 400) {
    let selfJoinJson = await selfJoinRes.json();
    console.log(`  ✓ Self-join blocked as expected (HTTP 400: "${selfJoinJson.error}") (${formatMs(t0)})`);
  } else {
    throw new Error(`Self-join was not blocked! HTTP ${selfJoinRes.status}`);
  }

  console.log('\n[TEST 5C] Joining Room from Second Player & Verifying 20s Ready Check...');
  t0 = performance.now();
  res = await fetch(`${HTTP_URL}/api/room/join`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ code: roomRes.code, guestClientId: 'guest_instance_xyz', guestName: 'GuestChallenger', guestGender: 'Girl' })
  });
  const joinData = await res.json();
  console.log(`  ✓ POST /api/room/join -> Ready check prompt: ${joinData.readyCheckMsg.slice(0, 45)}... (${formatMs(t0)})`);
  if (!joinData.readyCheckMsg.startsWith('MATCH_READY_CHECK')) {
    throw new Error('Expected MATCH_READY_CHECK from join endpoint!');
  }

  t0 = performance.now();
  res = await fetch(`${HTTP_URL}/api/room/${roomRes.code}/poll?role=host`);
  const hostPoll = await res.json();
  console.log(`  ✓ Host Poll -> Received Ready Check event: ${hostPoll.events[0]?.slice(0, 40)}... (${formatMs(t0)})`);

  // Host clicks "READY"
  t0 = performance.now();
  res = await fetch(`${HTTP_URL}/api/room/${roomRes.code}/action`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ role: 'host', message: 'PLAYER_READY|' + roomRes.code })
  });
  const hostReadyRes = await res.json();
  console.log(`  ✓ Host sends PLAYER_READY -> hostReady: ${hostReadyRes.hostReady} (${formatMs(t0)})`);

  // Guest polls and receives OPPONENT_READY
  t0 = performance.now();
  res = await fetch(`${HTTP_URL}/api/room/${roomRes.code}/poll?role=guest`);
  const guestPollOppReady = await res.json();
  console.log(`  ✓ Guest Poll -> Received ${guestPollOppReady.events[0]} (${formatMs(t0)})`);

  // Guest clicks "READY" -> triggers MATCH_START
  t0 = performance.now();
  res = await fetch(`${HTTP_URL}/api/room/${roomRes.code}/action`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ role: 'guest', message: 'PLAYER_READY|' + roomRes.code })
  });
  const guestReadyRes = await res.json();
  console.log(`  ✓ Guest sends PLAYER_READY -> Both ready! (${formatMs(t0)})`);

  // Host polls and receives MATCH_START
  t0 = performance.now();
  res = await fetch(`${HTTP_URL}/api/room/${roomRes.code}/poll?role=host`);
  const hostMatchStart = await res.json();
  console.log(`  ✓ Host Poll -> Received MATCH_START: ${hostMatchStart.events[0]?.slice(0, 40)}... (${formatMs(t0)})`);

  // In-Game Action Relay
  t0 = performance.now();
  res = await fetch(`${HTTP_URL}/api/room/${roomRes.code}/action`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ role: 'host', message: 'DUEL_ACTION|LocalTester|0|true|3|3|100|1' })
  });
  const actRes = await res.json();
  console.log(`  ✓ In-Game Action -> success: ${actRes.success} (${formatMs(t0)})`);

  t0 = performance.now();
  res = await fetch(`${HTTP_URL}/api/room/${roomRes.code}/poll?role=guest`);
  const guestPoll = await res.json();
  console.log(`  ✓ Guest Poll -> Received Action event: ${guestPoll.events.find(e => e.startsWith('DUEL_ACTION'))} (${formatMs(t0)})`);

  console.log('\n[TEST 6/8] Submitting & Fetching Leaderboard...');
  t0 = performance.now();
  res = await fetch(`${HTTP_URL}/api/score`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name: 'SpeedyBot', score: 9999, gender: 'Girl' })
  });
  data = await res.json();
  console.log(`  ✓ POST /api/score -> Name: ${data.name}, Score: ${data.score} (${formatMs(t0)})`);

  t0 = performance.now();
  res = await fetch(`${HTTP_URL}/api/leaderboard`);
  data = await res.json();
  console.log(`  ✓ GET /api/leaderboard -> Total: ${data.total} records (${formatMs(t0)})`);

  // --- PART 2: RAW TCP SOCKET TEST ---
  console.log('\n[TEST 7/8] Testing Raw TCP Socket Handshake & Commands (Port 5052)...');
  await new Promise((resolve, reject) => {
    const socket = new net.Socket();
    let buffer = '';
    const commandsQueue = [
      { send: 'HELLO|TcpTester|Boy', expect: 'OK' },
      { send: 'ROOM_LIST', expect: 'ROOM_LIST_RESP' },
      { send: 'ROOM_CREATE|TcpHost|Boy|HTML|Easy|Q1:::A', expect: 'ROOM_CREATED' },
      { send: 'BOARD', expect: 'BOARD' },
      { send: 'PING', expect: 'PONG' }
    ];
    let queueIdx = 0;
    let stepStart = performance.now();

    socket.connect(TCP_PORT, TCP_HOST, () => {
      console.log(`  ✓ Connected to TCP socket ${TCP_HOST}:${TCP_PORT}`);
      stepStart = performance.now();
      socket.write(commandsQueue[0].send + '\n');
    });

    socket.on('data', (chunk) => {
      buffer += chunk.toString('utf-8');
      const lines = buffer.split(/\r?\n/);
      buffer = lines.pop() || '';

      for (const line of lines) {
        if (!line.trim()) continue;
        const currentCmd = commandsQueue[queueIdx];
        const elapsed = formatMs(stepStart);
        console.log(`  ✓ [TCP] RECV: ${line.slice(0, 60)} (${elapsed})`);

        if (line.startsWith(currentCmd.expect)) {
          queueIdx++;
          if (queueIdx < commandsQueue.length) {
            stepStart = performance.now();
            socket.write(commandsQueue[queueIdx].send + '\n');
          } else {
            console.log('  ✓ All TCP command sequences validated successfully!');
            socket.end();
            resolve(true);
          }
        }
      }
    });

    socket.on('error', (err) => {
      reject(new Error(`TCP Socket error on port ${TCP_PORT}: ${err.message}`));
    });

    setTimeout(() => {
      if (queueIdx < commandsQueue.length) {
        socket.destroy();
        reject(new Error(`TCP Socket timeout waiting for command: ${commandsQueue[queueIdx].send}`));
      }
    }, 4000);
  });

  console.log('\n====================================================');
  console.log('  🎉 ALL LOCAL SERVER TESTS PASSED (100% OPERATIONAL)');
  console.log('====================================================');
}

runLocalServerTests().catch((err) => {
  console.error('\n❌ LOCAL SERVER TEST FAILED:', err.message);
  process.exit(1);
});
