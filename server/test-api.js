async function test() {
  console.log('--- 1. Testing GET /api/rooms ---');
  let res = await fetch('http://localhost:5050/api/rooms');
  console.log('GET /api/rooms =>', await res.json());

  console.log('--- 2. Testing POST /api/room/create ---');
  res = await fetch('http://localhost:5050/api/room/create', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      hostName: '131fgh',
      hostGender: 'Boy',
      language: 'HTML',
      difficulty: 'Medium'
    })
  });
  const created = await res.json();
  console.log('POST /api/room/create =>', created);

  console.log('--- 3. Testing GET /api/rooms (format=raw) ---');
  res = await fetch('http://localhost:5050/api/rooms?format=raw');
  console.log('GET /api/rooms?format=raw =>', await res.text());

  console.log('--- 4. Testing GET /api/room/' + created.code + ' ---');
  res = await fetch('http://localhost:5050/api/room/' + created.code);
  console.log('GET /api/room/:code =>', await res.json());
}

test().catch(console.error);
