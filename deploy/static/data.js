/**
 * data.js — 선별된 8개 워크쓰루와 각 레이어 콘텐츠
 * 각 walkthrough는 최대 2개 레이어만 무료 공개, 이후는 paywall
 */

const WALKTHROUGHS = [
  {
    id: 'kafka-broker',
    title: 'Kafka Broker 내부 구조',
    subtitle: '메시지 브로커의 로그 스토리지 계층',
    desc: 'naïve append-only file에서 시작해 Segmented Log, ISR 복제까지. PRODUCE 요청 하나가 디스크에 닿기까지의 여정.',
    icon: '📨',
    tags: ['be', 'devops'],
    totalLayers: 5,
    layers: [
      {
        title: 'Layer 0: Naïve baseline — single append-only file',
        content: `
          <span class="problem-tag">⚡ 문제 인식</span>
          <p>가장 단순한 메시지 브로커를 만들어봅시다:</p>
          <div class="code-block">def produce(topic, message):
    with open(f"/data/{topic}.log", "ab") as f:
        f.write(message + b"\\n")

def consume(topic, offset):
    with open(f"/data/{topic}.log", "rb") as f:
        lines = f.readlines()
        return lines[offset:]</div>
          <p>이건 동작합니다. 그런데 세 가지 <strong>치명적</strong> 문제:</p>
          <div class="highlight-box">
            <strong>문제 ①</strong> 멀티 스레드가 동시에 produce를 호출하면? 두 스레드가 같은 파일을 동시에 열어 쓰면 메시지가 뒤섞인다. <em>데이터 오염</em>.<br><br>
            <strong>문제 ②</strong> 소비자가 consume(offset=5000)을 부르면? 앞 5000줄을 전부 읽어서 버려야 한다. 파일이 1 GB면 consume 한 번에 <em>1 GB를 전부 스캔</em>. O(N).<br><br>
            <strong>문제 ③</strong> 브로커가 한 대면? 서버가 죽으면 메시지가 통째로 사라진다.
          </div>
          <p>→ 실제 Kafka는 이 세 문제를 <em>각각 다른 layer</em>로 해결한다.</p>
        `
      },
      {
        title: 'Layer 1: Segmented log — file size 제한',
        content: `
          <span class="solution-tag">✅ 해결</span>
          <p><strong>비유</strong>: 소포를 분류하지 않고 창고 바닥에 쌓으면 "3번 소포 어딨어?"가 불가능. 그래서 우체국은 <em>번호표를 붙인 선반</em>에 차례로 올린다.</p>
          <div class="code-block">partition-0/
  00000000000000000000.log    ← 메시지 본체 (FileChannel append)
  00000000000000000000.index  ← offset → file position (mmap)
  00000000001073741824.log    ← 1 GB 차면 다음 세그먼트로 롤
  00000000001073741824.index</div>
          <h3>핵심 설계 결정 두 가지</h3>
          <p><strong>결정 1: write(2) 한 번 / 배치</strong> — LogSegment.append()는 프로듀서 배치 전체를 FileChannel.write(ByteBuffer) 한 번으로 쓴다. 레코드 100개짜리 배치여도 syscall은 1회.</p>
          <p><strong>결정 2: 인덱스는 mmap — syscall 0회</strong> — .index 파일은 MappedByteBuffer에 올린다. 인덱스 엔트리 추가는 메모리 쓰기이고 syscall이 없다.</p>
          <div class="code-block">syscall 정리 (세그먼트 하나 기준):
  produce 배치 1건  : write(2)  × 1
  인덱스 엔트리 추가 : (없음, mmap write)
  fetch 1건        : sendfile(2) × 1
  인덱스 조회      : (없음, mmap read)</div>
          <div class="highlight-box">
            <strong>비용</strong>: 이 layer만으로는 thread-safety가 없다 — Javadoc이 "not thread-safe"를 명시. 여러 스레드가 동시에 append를 부르면 파일이 오염된다.
          </div>
        `
      }
    ]
  },
  {
    id: 'rocksdb',
    title: 'RocksDB Write Path',
    subtitle: 'LSM-Tree 스토리지 엔진의 쓰기 경로',
    desc: '디스크에 직접 쓰는 naïve 코드에서 MemTable, WAL, Group Commit, Compaction까지. Put 한 번이 수 μs에 끝나는 비밀.',
    icon: '🪨',
    tags: ['db', 'be'],
    totalLayers: 5,
    layers: [
      {
        title: 'Layer 0: Naïve baseline — write every Put to disk',
        content: `
          <span class="problem-tag">⚡ 문제 인식</span>
          <p>가장 단순한 키-값 저장소를 직접 만들어 봅시다:</p>
          <div class="code-block">void Put(const std::string& key, const std::string& value) {
    std::ofstream f("data.db", std::ios::app);
    f << key << "=" << value << "\\n";  // 끝!
}</div>
          <p>작동은 합니다. 그런데 세 가지 큰 문제:</p>
          <div class="highlight-box">
            <strong>문제 ①</strong> 매 Put마다 임의(random) 위치에 디스크 쓰기 → SSD에서 ~100 μs, HDD에서 ~10 ms. 초당 10,000건 처리가 한계.<br><br>
            <strong>문제 ②</strong> 크래시 후 마지막 한 줄이 반쪽만 기록될 수 있음 → 데이터 손상. 내구성 없음.<br><br>
            <strong>문제 ③</strong> 읽기 시 파일 전체를 순차 스캔해야 함 → O(N).
          </div>
          <p>→ 세 문제를 하나씩 layer를 추가해 해결한 결과가 RocksDB다.</p>
        `
      },
      {
        title: 'Layer 1: In-memory buffer — MemTable',
        content: `
          <span class="solution-tag">✅ 해결</span>
          <h3>직관</h3>
          <p>노트 정리를 할 때, 수첩에 바로 적는 대신 <strong>포스트잇에 먼저 빠르게 메모</strong>해 두고 나중에 정리하는 방식. 수첩(디스크)에 접근하는 횟수를 줄이고, 포스트잇(메모리)에서는 마음껏 빠르게 적는다.</p>
          <h3>MemTable (+ SkipList)</h3>
          <p>RocksDB는 쓰기 요청을 <strong>MemTable</strong>이라는 인메모리 자료구조에 먼저 넣는다. 기본 구현은 SkipList — 정렬된 연결 리스트의 변형으로, Put/Get 모두 O(log N).</p>
          <div class="code-block">Put("apple", "1") ──→ MemTable (SkipList, 메모리)
Put("banana", "2") ─→ MemTable (SkipList, 메모리)
Put("cherry", "3") ─→ MemTable (SkipList, 메모리)
                           ↓ (가득 차면)
                     immutable MemTable
                           ↓ (FlushJob)
                       L0 SST 파일 (디스크)</div>
          <h3>비용 효과</h3>
          <div class="cost-item"><span class="cost-label">Put 비용</span><span class="cost-value">~1 μs (Layer 0의 100× 개선)</span></div>
          <div class="cost-item"><span class="cost-label">단점</span><span class="cost-value">크래시 시 메모리 내용이 사라짐 → 다음 Layer 필요</span></div>
        `
      }
    ]
  },
  {
    id: 'linux-scheduler',
    title: 'Linux CPU Scheduler',
    subtitle: 'Round-Robin에서 EEVDF까지',
    desc: 'Round-Robin으로 시작해 Scheduling Class 계층, per-CPU runqueue, EEVDF, PELT 로드 추적까지의 진화.',
    icon: '⚙️',
    tags: ['os', 'perf'],
    totalLayers: 6,
    layers: [
      {
        title: 'Layer 0: Naïve — 그냥 Round-Robin하면 안 되나?',
        content: `
          <span class="problem-tag">⚡ 문제 인식</span>
          <p>가장 단순한 CPU 스케줄러를 직접 만들어 봅시다:</p>
          <div class="code-block">ready_queue = [A, B, C, D]

def schedule():
    task = ready_queue.pop(0)   # 첫 번째 꺼내기
    run(task, time_slice=10ms)
    ready_queue.append(task)    # 뒤에 다시 넣기</div>
          <p>작동은 합니다. 세 가지 구조적 문제:</p>
          <div class="highlight-box">
            <strong>문제 ①</strong> 실시간 태스크(SCHED_FIFO)가 "반드시 먼저" 실행돼야 하는데, Round-Robin은 우선순위가 없다. 오디오 재생이 컴파일 작업과 같은 시간을 받으면 끊김.<br><br>
            <strong>문제 ②</strong> CPU가 여러 개면? ready_queue가 전역 1개 → 모든 CPU가 큐 하나에 경쟁 → lock contention.<br><br>
            <strong>문제 ③</strong> "공정"이란 뭔가? 10개 중 1개가 IO를 기다리다 돌아왔을 때, 나머지 9개와 동일 시간을 받아야 하나?
          </div>
          <p>→ 리눅스 스케줄러는 이 세 문제를 <em>각각 다른 layer</em>로 해결한다.</p>
        `
      },
      {
        title: 'Layer 1: 우선순위가 필요 — Scheduling Class 계층',
        content: `
          <span class="solution-tag">✅ 해결</span>
          <h3>직관</h3>
          <p>병원 응급실의 <strong>트리아주 시스템</strong>. 심정지 환자(SCHED_FIFO)가 감기 환자(SCHED_NORMAL)보다 반드시 먼저 진료. 감기 환자끼리는 접수 순서(fair).</p>
          <h3>Scheduling Class 계층</h3>
          <div class="code-block">우선순위 순서 (높은 것이 먼저 실행):

  stop_sched_class      ← CPU hotplug / migration (최고 우선)
    ↓ next
  dl_sched_class        ← SCHED_DEADLINE (마감 기한)
    ↓ next
  rt_sched_class        ← SCHED_FIFO / SCHED_RR (실시간)
    ↓ next
  fair_sched_class      ← SCHED_NORMAL (일반 프로세스) ← 99%
    ↓ next
  idle_sched_class      ← idle loop (아무 할 일 없을 때)</div>
          <p><code>__pick_next_task()</code>는 가장 높은 class부터 순회하여 실행할 태스크를 찾는다. rt 태스크가 있으면 fair는 아예 실행 안 됨.</p>
          <div class="highlight-box">
            <strong>비용 효과</strong>: 99.9%의 시간은 fair_sched_class 안에서만 pick이 일어남. rt/dl이 있을 때만 preemption이 즉각 발생.
          </div>
        `
      }
    ]
  },
  {
    id: 'nginx',
    title: 'Nginx 아키텍처',
    subtitle: 'C10K 문제 해결의 역사',
    desc: 'Apache prefork의 한계에서 시작해 Master-Worker 모델, epoll 이벤트 루프, non-blocking 상태 머신까지.',
    icon: '🌐',
    tags: ['be', 'net', 'devops'],
    totalLayers: 6,
    layers: [
      {
        title: 'Layer 0: Naïve baseline — thread-per-connection',
        content: `
          <span class="problem-tag">⚡ 문제 인식</span>
          <p>1990년대~2000년대 초의 지배적 답은 단순했다:</p>
          <div class="code-block">클라이언트 연결 1개 → 프로세스(또는 스레드) 1개</div>
          <p>Apache httpd의 <code>prefork</code> 모델이 대표적이다. 새 연결이 오면 프로세스를 하나 꺼내서 read() → 처리 → write()를 순서대로 한다.</p>
          <p>문제가 없어 보인다. 그런데 1999년 Dan Kegel이 <strong>C10K problem</strong>이라는 글을 쓴다.</p>
          <h3>수치로 본 나이브 모델의 실패</h3>
          <div class="cost-item"><span class="cost-label">100 연결</span><span class="cost-value">~400 MB — 무시 가능</span></div>
          <div class="cost-item"><span class="cost-label">1,000 연결</span><span class="cost-value">~4 GB — 눈에 띄기 시작</span></div>
          <div class="cost-item"><span class="cost-label">10,000 (C10K)</span><span class="cost-value">~40 GB — 폭증 🔥</span></div>
          <div class="highlight-box">
            결론: <em>연결마다 프로세스를 매핑하는 모델은 C10K에서 구조적으로 실패한다.</em>
          </div>
        `
      },
      {
        title: 'Layer 1: Master + multi-worker process model',
        content: `
          <span class="solution-tag">✅ 해결</span>
          <h3>직관 (비유)</h3>
          <p>큰 식당을 떠올려보자. 손님 1명당 웨이터 1명(Apache prefork)이면 손님이 100명일 때 웨이터도 100명이 필요하다. 하지만 실제 식당은 그렇지 않다. <em>소수의 웨이터가 여러 테이블을 순서 없이 돌아다니며</em> "준비된 테이블"만 처리한다.</p>
          <h3>nginx의 답</h3>
          <div class="code-block">master process (PID 1234)
  ├── worker process 0 (PID 2001)  ← 코어 0에 pinning
  ├── worker process 1 (PID 2002)  ← 코어 1에 pinning
  ├── worker process 2 (PID 2003)  ← 코어 2에 pinning
  └── worker process 3 (PID 2004)  ← 코어 3에 pinning</div>
          <p><strong>master process</strong>: 설정 읽기, 워커 생성(fork()), 시그널 처리. 요청을 직접 처리하지 않는다.</p>
          <p><strong>worker process</strong>: 실제 연결을 받아 처리하는 싱글 스레드 이벤트 루프. CPU 코어 수만큼 띄운다.</p>
          <div class="highlight-box">
            <strong>비용 효과</strong>: worker 수 = 코어 수 (보통 4~32개). 1만 연결이 와도 프로세스는 4~32개로 고정.
          </div>
        `
      }
    ]
  },
  {
    id: 'envoy-proxy',
    title: 'Envoy Proxy 요청 경로',
    subtitle: 'TCP 바이트에서 업스트림 라우팅까지',
    desc: 'naïve TCP proxy 10줄짜리에서 시작해 Worker-per-CPU, Filter Chain, HCM, HTTP Filter, Router까지의 진화.',
    icon: '🔀',
    tags: ['be', 'net', 'devops'],
    totalLayers: 6,
    layers: [
      {
        title: 'Layer 0: Naïve baseline — minimal TCP proxy',
        content: `
          <span class="problem-tag">⚡ 문제 인식</span>
          <p>가장 단순한 프록시는 이렇게 생겼다 (pseudocode):</p>
          <div class="code-block">sock = socket(); sock.bind((host, port)); sock.listen()
while True:
    conn, _ = sock.accept()                  # 새 클라이언트
    upstream = socket(); upstream.connect(backend_addr)
    pipe(conn, upstream)                     # 양방향 byte copy</div>
          <p>이게 <em>실제로 작동</em>한다. nginx의 stream 모듈, HAProxy의 L4 모드가 본질적으로 이 모양이다.</p>
          <h3>못 푸는 문제 4가지</h3>
          <div class="highlight-box">
            ① 한 backend만 알 수 있음 — backend_addr가 hard-coded.<br>
            ② path별 라우팅 불가 — TCP만 보니까 /api/v1/users vs /static/*를 구분 못 함.<br>
            ③ 재시도 / 타임아웃 없음 — backend가 죽으면 그냥 연결 끊김.<br>
            ④ 한 스레드만 쓰면 느림 / 여러 스레드 쓰면 lock 필요 — 동시성 모델이 없음.
          </div>
          <p>각각이 다음 5개 layer의 <em>존재 이유</em>다.</p>
        `
      },
      {
        title: 'Layer 1: Worker-per-CPU + SO_REUSEPORT',
        content: `
          <span class="solution-tag">✅ 해결</span>
          <h3>문제</h3>
          <p>스레드 하나로는 1 코어 1 연결밖에 못 한다. N개로 같은 socket에 accept()를 동시에 부르면 <strong>thundering herd</strong>.</p>
          <h3>Envoy의 해결책: shared-nothing per-worker</h3>
          <div class="code-block">                    ┌───────────────────────────────────────┐
        :8080       │  Linux kernel (SO_REUSEPORT)          │
TCP SYN ─────────►  │  ┌────────┐ ┌────────┐ ┌────────┐    │
                    │  │queue 0 │ │queue 1 │ │queue 2 │    │
                    │  └───┬────┘ └───┬────┘ └───┬────┘    │
                    └──────┼──────────┼──────────┼──────────┘
                           ▼          ▼          ▼
                      ┌────────┐ ┌────────┐ ┌────────┐
                      │worker 0│ │worker 1│ │worker 2│
                      │ epoll  │ │ epoll  │ │ epoll  │
                      └────────┘ └────────┘ └────────┘</div>
          <p>워커마다 독립된 socket fd — 커널이 SYN을 워커별 accept queue로 분배. <strong>lock 없음, atomic 없음</strong>.</p>
          <div class="highlight-box">
            <strong>핵심</strong>: <em>connection을 worker에 영구 고정</em> + <em>워커마다 독립 자료구조</em> = <em>lock-free 동시성</em>. N 코어 ≈ N배 처리량.
          </div>
        `
      }
    ]
  },
  {
    id: 'kube-apiserver',
    title: 'Kubernetes API Server',
    subtitle: 'kubectl apply에서 etcd까지',
    desc: 'HTTP POST로 etcd에 바로 쓰는 naïve 모델에서 Handler Chain, REST verb 핸들러, CAS 충돌 판정, Watch fan-out까지.',
    icon: '☸️',
    tags: ['be', 'devops'],
    totalLayers: 7,
    layers: [
      {
        title: 'Layer 0: Naïve baseline — direct passthrough to storage',
        content: `
          <span class="problem-tag">⚡ 문제 인식</span>
          <p>가장 단순한 apiserver를 직접 만들어 봅시다:</p>
          <div class="code-block">kubectl apply -f pod.yaml
→ HTTP POST /api/v1/namespaces/default/pods
→ etcd.Put("/registry/pods/default/nginx", body)  // 끝!</div>
          <p>작동은 합니다. 그런데 세 가지 즉각적 문제:</p>
          <div class="highlight-box">
            <strong>문제 ①</strong> 누가 이 요청을 보냈는지 모른다 — 아무 HTTP 클라이언트나 Pod를 만들 수 있음. 해커도.<br><br>
            <strong>문제 ②</strong> 기본값이 없다 — restartPolicy를 안 써도 되는가? serviceAccountName은?<br><br>
            <strong>문제 ③</strong> 동시 수정 충돌 — A와 B가 같은 Deployment를 동시에 수정하면 누가 이겼는지 누가 판정하나?
          </div>
          <p>→ apiserver가 단일 통과점(single chokepoint)으로 <em>한 곳</em>에서 강제하는 게 k8s의 선택.</p>
        `
      },
      {
        title: 'Layer 1: HTTP handler chain (auth + audit + rate-limit)',
        content: `
          <span class="solution-tag">✅ 해결</span>
          <h3>직관 (비유)</h3>
          <p>공항 탑승구 직전의 검문소 열. 여권 검사(인증) → 탑승권 확인(인가) → 위험물 검색(admission) → 게이트 통과(storage). 모든 승객(요청)이 동일 열을 통과.</p>
          <h3>실제 스택 순서</h3>
          <div class="code-block">WithPanicRecovery          ← 최외곽: 어떤 panic도 500으로 변환
 └─ WithAudit              ← 요청/응답 감사 로그 기록
     └─ WithImpersonation  ← SA / 사용자 대리 실행
         └─ WithAuthentication  ← "너 누구야?" — 401
             └─ WithAuthorization   ← "너 이거 해도 돼?" — 403
                 └─ WithAdmission   ← "서류 OK?" — 422
                     └─ REST verb handler  ← 실제 처리</div>
          <div class="highlight-box">
            <strong>비용 효과</strong>: 정책 로직이 <em>한 곳</em>. 새 자원 추가 시 인증/인가 재구현 불필요. 감사 로그가 <em>모든</em> 요청을 자동 커버.
          </div>
        `
      }
    ]
  },
  {
    id: 'mysql-innodb-redo',
    title: 'MySQL InnoDB Redo Log',
    subtitle: '6-thread lock-free 쓰기 파이프라인',
    desc: 'fsync 한 번에 수백 μs 걸리는 문제에서 시작해 lock-free SN 예약, 6개 전담 스레드 파이프라인, ARIES 복구까지.',
    icon: '🐬',
    tags: ['db', 'be'],
    totalLayers: 8,
    layers: [
      {
        title: 'Layer 0: Naïve — 데이터 쓸 때 fsync 같이 하면 안 되나?',
        content: `
          <span class="problem-tag">⚡ 문제 인식</span>
          <p>가장 단순한 답: <code>UPDATE</code>가 실행되면 변경된 페이지를 <strong>디스크에 즉시 fsync</strong>.</p>
          <div class="code-block">UPDATE users SET name = 'Bob' WHERE id = 42;

→ Buffer Pool에서 page 수정
→ 그 page를 즉시 fsync(data_file_fd)  // 끝?</div>
          <div class="highlight-box">
            <strong>문제 ①</strong> 16 KB 페이지 전체를 fsync — 1 byte만 바꿔도 16 KB를 디스크에 밀어넣음. SSD fsync ~100–500 μs.<br><br>
            <strong>문제 ②</strong> 트랜잭션이 여러 페이지를 건드리면? 세 번째 fsync 도중 크래시 → 부분 기록(partial write). 롤백 불가능.<br><br>
            <strong>문제 ③</strong> 100개 트랜잭션이 동시에 COMMIT → 100번 fsync → ~50 ms. 초당 2,000 TPS가 한계.
          </div>
          <p>→ 해법: <strong>WAL</strong>(Write-Ahead Log). 변경 "내용"만 먼저 작은 로그에 순차 기록 → 나중에 페이지를 한꺼번에 flush.</p>
        `
      },
      {
        title: 'Layer 1: Lock-free SN 예약 — WAL을 여러 스레드가 쓰면?',
        content: `
          <span class="solution-tag">✅ 해결</span>
          <h3>문제</h3>
          <p>WAL을 도입했지만, 여러 스레드가 동시에 로그를 쓰려면 <strong>"내 자리"를 예약</strong>해야 한다. mutex로 직렬화하면 병목.</p>
          <h3>InnoDB의 답: atomic fetch_add</h3>
          <div class="code-block">// log0buf.cc의 핵심 한 줄 (MySQL 8.0.11+, WL#10310)
start_lsn = log.sn.fetch_add(data_len);
// 이제 [start_lsn, start_lsn + data_len) 구간은 "내 것"

// 예약한 구간에 데이터를 자유롭게 복사
memcpy(log.buf + start_lsn % BUF_SIZE, data, data_len);</div>
          <p><code>fetch_add</code>는 x86에서 <code>lock xadd</code> 한 줄 — ~10 ns. mutex 대비 10–100× 빠르다.</p>
          <div class="highlight-box">
            <strong>비용</strong>: 예약은 ~10 ns. 하지만 "다 썼어"를 어떻게 알려주나? 스레드 A가 먼저 예약했는데 스레드 B보다 늦게 복사를 끝내면? → 다음 Layer의 문제.
          </div>
        `
      }
    ]
  },
  {
    id: 'linux-context-switch',
    title: 'Linux Context Switch',
    subtitle: 'CPU 상태 전환의 어셈블리 레벨 해부',
    desc: 'jmp 한 줄로 안 되는 이유에서 시작해 Register Save, Stack Swap, MMU Switch, FPU State까지의 전환 과정.',
    icon: '🔄',
    tags: ['os', 'perf'],
    totalLayers: 6,
    layers: [
      {
        title: 'Layer 0: Naïve — 그냥 다음 task의 코드로 jump하면 안 되나?',
        content: `
          <span class="problem-tag">⚡ 문제 인식</span>
          <p>가장 단순한 답:</p>
          <div class="code-block">jmp B_address</div>
          <div class="highlight-box">
            <strong>문제 ①</strong> CPU의 register들은 현재 task A의 값 — rbx, rbp, r12-r15 등에 A의 계산 결과 들어있음. B로 jump하면 덮어씀 → A가 다시 실행될 때 변수 다 사라짐.<br><br>
            <strong>문제 ②</strong> Stack pointer (%rsp)가 A의 stack을 가리킴. B의 함수 호출이 A의 stack을 덮어씀.<br><br>
            <strong>문제 ③</strong> Virtual memory map (%cr3 = page table base)이 A의 것. B의 메모리 접근이 A의 page table 통해 → page fault 또는 wrong data.<br><br>
            <strong>문제 ④</strong> FPU/AVX register (xmm/ymm/zmm/AMX tile)가 A의 값. B가 numerical workload면 garbage 사용.
          </div>
          <p>→ task A의 모든 CPU state를 어딘가에 save + task B의 state를 restore 후 jump. 이 <em>save + restore + jump</em>가 <strong>context switch</strong>.</p>
        `
      },
      {
        title: 'Layer 1: Register save/restore — caller vs callee 책임',
        content: `
          <span class="solution-tag">✅ 해결</span>
          <p>가장 단순한 답: 모든 register save. ~16 GPR (rax-r15) + flags + ... = ~140 bytes.</p>
          <p>근데 Linux가 <em>영리하게</em> 줄임 — <strong>System V ABI의 callee-saved만 save</strong>.</p>
          <div class="code-block">caller-saved (= scratch): rax, rcx, rdx, rsi, rdi, r8, r9, r10, r11
   → 호출자가 이미 stack에 저장. switch는 건드릴 책임 없음.

callee-saved (= preserved): rbx, rbp, r12, r13, r14, r15
   → 호출된 함수가 반환 전 원복 책임. switch도 함수처럼 — push/pop.</div>
          <h3>__switch_to_asm 의 push sequence</h3>
          <div class="code-block">__switch_to_asm:
    pushq %rbp          ; callee-saved 1
    pushq %rbx          ; callee-saved 2
    pushq %r12          ; callee-saved 3
    pushq %r13          ; callee-saved 4
    pushq %r14          ; callee-saved 5
    pushq %r15          ; callee-saved 6
    ; ... (stack swap — Layer 2)
    popq %r15
    popq %r14
    popq %r13
    popq %r12
    popq %rbx
    popq %rbp
    jmp __switch_to     ; tail-call to C</div>
          <div class="highlight-box">
            총 6 push + 6 pop = ~12 instruction. 비용: ~6 stores + ~6 loads = <strong>~10-20 ns</strong> (caches hot).
          </div>
        `
      }
    ]
  }
];
