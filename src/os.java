import java.util.*;

enum Status {HIT, PAGEFAULT, MIGRATION}

class Page {
    char page; // 페이지 식별
    int accessTimeMs; // 디스크에 저장하거나 불러올 때 걸리는 시간
    boolean ref; // 참조비트
    long lastUsed;
    Status status;

    Page(char page, int accessTimeMs, long time) {
        this.page = page;
        this.accessTimeMs = accessTimeMs;
        this.ref = false;
        this.lastUsed = time;
    }
}

class FifoPolicy {
    final List<Page> frames;
    private final int memSize;
    private final ArrayDeque<Integer> queue = new ArrayDeque<>();
    int hit, fault, migration, ioMs;
    private long tick = 0;

    FifoPolicy(int memSize) {
        this.memSize = memSize;
        frames = new ArrayList<>(Collections.nCopies(memSize, null));
    }

    public Page access(char page, int ioMs) {

        tick++;

        for (Page p : frames)
            if (p != null && p.page == page) {
                p.status = Status.HIT;
                hit++;
                return p;
            }

        Page newPage = new Page(page, ioMs, tick);

        if (queue.size() < memSize) { //빈 프레임 있음
            int idx = queue.size();
            frames.set(idx, newPage);
            queue.addLast(idx); //큐에 기록
            newPage.status = Status.PAGEFAULT;

        } else { //빈 프레임 없으니 교체하자

            int victim = queue.removeFirst(); //가장 오래된 페이지 교체
            frames.set(victim, newPage); //교체
            queue.addLast(victim); //큐에 기록
            newPage.status = Status.MIGRATION;
            migration++;
        }
        fault++;
        this.ioMs += ioMs;
        return newPage;
    }
}

class LruPolicy {
    final List<Page> frames;
    private final int memSize;
    int hit, fault, migration, ioMs;
    private int size = 0;
    private long tick = 0;

    LruPolicy(int memSize) {
        this.memSize = memSize;
        frames = new ArrayList<>(Collections.nCopies(memSize, null));
    }

    public Page access(char pageNum, int tMs) {
        tick++;

        for (Page p : frames) { //hit 검사
            if (p != null && p.page == pageNum) {
                p.status = Status.HIT; // 메모리에 있으면 hit
                p.lastUsed = tick; // 사용한거 기록
                hit++;
                return p;
            }
        }

        Page newPage = new Page(pageNum, tMs, tick);//fault

        if (size < memSize) { // 빈 공간에 삽입
            int idx = size;
            frames.set(idx, newPage);
            size++;
            newPage.status = Status.PAGEFAULT;
        } else {//빈 프레임 없음 가장 오랫동안 안쓴거 교체
            long minTime = Long.MAX_VALUE;
            int victim = 0;
            for (int i = 0; i < memSize; i++)
                if (frames.get(i).lastUsed < minTime) {
                    minTime = frames.get(i).lastUsed;
                    victim = i;
                }
            frames.set(victim, newPage);
            newPage.status = Status.MIGRATION;
            migration++;
        }
        fault++;
        ioMs += tMs;
        return newPage;
    }
}

class SecondChancePolicy {
    final List<Page> frames;
    private final int memSize;
    int hit, fault, migration, ioMs;
    private int victimPtr = 0, size = 0;
    private long tick = 0;

    SecondChancePolicy(int memSize) {
        this.memSize = memSize;
        frames = new ArrayList<>(Collections.nCopies(memSize, null));
    }

    public Page access(char pageNum, int tMs) {
        tick++;

        for (Page p : frames) { //hit 검사
            if (p != null && p.page == pageNum) {
                p.status = Status.HIT;
                p.ref = true; //최근 사용 표시
                hit++;
                return p;
            }
        }

        Page newPage = new Page(pageNum, tMs, tick);//fault
        newPage.ref = true;//가져와서 참조

        if (size < memSize) {// 빈 공간 있음
            int idx = size;
            frames.set(idx, newPage);
            size++;
            newPage.status = Status.PAGEFAULT;
        } else {// 페이지 교체
            while (true) {
                Page temp = frames.get(victimPtr);
                if (!temp.ref) { // 교체 후보의 참조비트가 0이면 교체
                    frames.set(victimPtr, newPage);

                    victimPtr = (victimPtr + 1) % memSize;// 다음 프레임으로 이동
                    newPage.status = Status.MIGRATION;
                    migration++;
                    break;
                } else { // 교체 후보의 참조비트가 1이면 기회 한번 더
                    temp.ref = false;
                    victimPtr = (victimPtr + 1) % memSize;
                }
            }
        }
        fault++;
        ioMs += tMs;
        return newPage;
    }
}

class MinAccessTime {
    final int memSize;
    final List<Page> frames;
    int size = 0, hit, fault, migration, ioMs;
    long tick = 0;

    MinAccessTime(int memSize) {
        this.memSize = memSize;
        frames = new ArrayList<>(Collections.nCopies(memSize, null));
    }

    public Page access(char page, int tMs) {
        tick++;
        for (Page p : frames) //Hit 검사
            if (p != null && p.page == page) {
                p.status = Status.HIT;
                hit++;
                return p;
            }

        Page newPage = new Page(page, tMs, tick);//Fault

        if (size < memSize) {// 빈 프레임 있으면 그곳에 넣기
            int idx = size;
            frames.set(idx, newPage);
            size++;
            newPage.status = Status.PAGEFAULT;
        } else {// accessTimeMs가 가장 적은 페이지 교체
            int victim = 0;
            int minLat = Integer.MAX_VALUE;
            for (int i = 0; i < memSize; i++)
                if (frames.get(i).accessTimeMs < minLat) {
                    minLat = frames.get(i).accessTimeMs;
                    victim = i;
                }
            frames.set(victim, newPage);
            newPage.status = Status.MIGRATION;
            migration++;
        }
        fault++;
        ioMs += tMs;
        return newPage;
    }
}

public class os {

    private static String ask(Scanner sc, String msg) {//입력받기
        System.out.print(msg);
        return sc.nextLine().trim().toUpperCase();
    }

    private static String frameStringFixed(List<Page> frames, Page cur) {
        StringBuilder sb = new StringBuilder("[");
        int n = frames.size();

        for (int i = 0; i < n; i++) {
            Page p = frames.get(i);

            // ── 슬롯 2글자 만들기 ────────────────────
            if (p == null) {                     // 빈 슬롯
                sb.append('□').append(' ');
            } else {
                sb.append(p.page);               // 실제 페이지 문자
                if (p == cur) {                  // 이번 스텝 접근 페이지라면
                    char mark = switch (cur.status) {
                        case HIT -> '+';
                        case PAGEFAULT -> '!';
                        case MIGRATION -> '*';
                    };
                    sb.append(mark);
                } else {
                    sb.append(' ');              // 상태 기호 대신 공백
                }
            }

            if (i != n - 1) sb.append(' ');      // 슬롯 사이 공백
        }
        sb.append(']');
        return sb.toString();
    }


    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        int[] delays;
        //입력
        String ref = ask(sc, "레퍼런스 스트링: ");
        String[] timesMs = ask(sc, "각 참조의 페이지 디스크 접근시간 (공백 구분): ").split("\\s+");
        if (timesMs[0].equals("-1")) {
            delays = new int[ref.length()];
            java.util.Arrays.fill(delays, 1);
        } else if (timesMs.length != ref.length()) {
            System.out.println("접근 시간 개수가 레퍼런스 길이와 다릅니다.");
            return;
        } else {
            delays = Arrays.stream(timesMs).mapToInt(Integer::parseInt).toArray();
        }

        int memSize = Integer.parseInt(ask(sc, "프레임 수: "));
        String policy = ask(sc, "정책 (FIFO/LRU/SC/MAT): ");

        //교체 정책 객체 생성 (필요한 것만 생성)
        FifoPolicy fifo = null;
        LruPolicy lru = null;
        SecondChancePolicy scp = null;
        MinAccessTime min = null;

        switch (policy) {
            case "LRU" -> lru = new LruPolicy(memSize);
            case "SC" -> scp = new SecondChancePolicy(memSize);
            case "MAT" -> min = new MinAccessTime(memSize);
            default -> fifo = new FifoPolicy(memSize);
        }

        //레이아웃 출력
        int frameColWidth = 3 * memSize - 1 + 2;   // 2×n + (n-1) + [ ]
        String rowFmt = "%4d | %c | %-11s | %-" + frameColWidth + "s | %s%n";
        String header = String.format("Step |Ref| Result      | %-" + frameColWidth + "s | I/O", "Frames");
        String bar = "-".repeat(header.length());
        System.out.println("\n"+"+ = Hit     ! = Page Fault       * = migration"+"\n" + header);
        System.out.println(bar);


        //시뮬레이션
        int step = 1;
        for (int i = 0; i < ref.length(); i++) {
            char c = ref.charAt(i);
            int tMs = delays[i];

            Page res;
            List<Page> frameView;

            if (fifo != null) { // FIFO 선택
                res = fifo.access(c, tMs);
                frameView = fifo.frames;
            } else if (lru != null) { // LRU 선택
                res = lru.access(c, tMs);
                frameView = lru.frames;
            } else if (scp != null) { // Second Chance 선택
                res = scp.access(c, tMs);
                frameView = scp.frames;
            } else { //MinAccessTime
                res = min.access(c, tMs);
                frameView = min.frames;
            }

            System.out.printf(rowFmt, step++, c, res.status, frameStringFixed(frameView, res), (res.status == Status.HIT ? "-" : tMs));

        }

        //통계 출력
        int hits, faults, migrations, ioMs;

        if (fifo != null) {
            hits = fifo.hit;
            faults = fifo.fault;
            migrations = fifo.migration;
            ioMs = fifo.ioMs;
        } else if (lru != null) {
            hits = lru.hit;
            faults = lru.fault;
            migrations = lru.migration;
            ioMs = lru.ioMs;
        } else if (scp != null) {
            hits = scp.hit;
            faults = scp.fault;
            migrations = scp.migration;
            ioMs = scp.ioMs;
        } else {
            hits = min.hit;
            faults = min.fault;
            migrations = min.migration;
            ioMs = min.ioMs;
        }

        int total = hits + faults;
        double rate = total == 0 ? 0 : 100.0 * faults / total;

        System.out.println("\n====== 통계 ======");
        System.out.printf("Hits        : %d%n", hits);
        System.out.printf("Faults      : %d%n", faults);
        System.out.printf("Migrations  : %d%n", migrations);
        System.out.printf("Fault Rate  : %.2f%%%n", rate);
        System.out.printf("총 디스크 I/O 시간 : %d ms%n", ioMs);
    }
}
