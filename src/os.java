import java.util.*;

enum Status {HIT, PAGEFAULT, MIGRATION}

class Page {
    char page;
    boolean ref;
    long lastUsed;
    Status status;

    Page(char page, long time) {
        this.page = page;
        this.ref = false;
        this.lastUsed = time;
    }
}

class FifoPolicy {
    private final int memSize;
    final List<Page> frames;
    private final ArrayDeque<Integer> queue = new ArrayDeque<>();
    private long tick = 0;
    int hit, fault, migration;

    FifoPolicy(int memSize) {
        this.memSize = memSize;
        frames = new ArrayList<>(Collections.nCopies(memSize, null));
    }

    public Page access(char page) {

        tick++;

        for (Page p : frames) //hit 검사
            if (p != null && p.page == page) {
                p.status = Status.HIT;//메모리에 찾는 페이지가 있으면 hit
                hit++;
                return p;
            }

        Page newPage = new Page(page, tick); //fault

        if (queue.size() < memSize) { //빈 프레임 있음
            int i = 0;
            for (; i < memSize; i++) //first fit
                if (frames.get(i) == null) break;

            frames.set(i, newPage);
            queue.addLast(i); //큐에 기록
            newPage.status = Status.PAGEFAULT;

        } else { //빈 프레임 없으니 교체하자

            int victim = queue.removeFirst(); //가장 오래된 페이지 교체
            frames.set(victim, newPage); //교체
            queue.addLast(victim); //큐에 기록
            newPage.status = Status.MIGRATION;
            migration++;
        }
        fault++;
        return newPage;
    }
}

class LruPolicy {
    private final int memSize;
    final List<Page> frames;
    private int size = 0;
    int hit, fault, migration;
    private long tick = 0;

    LruPolicy(int memSize) {
        this.memSize = memSize;
        frames = new ArrayList<>(Collections.nCopies(memSize, null));
    }

    public Page access(char pageNum) {
        tick++;

        for (Page p : frames) { //hit 검사
            if (p != null && p.page == pageNum) {
                p.status = Status.HIT; // 메모리에 있으면 hit
                p.lastUsed = tick; // 사용한거 기록
                hit++;
                return p;
            }
        }

        Page newPage = new Page(pageNum, tick);//fault

        if (size < memSize) { // 빈 공간에 삽입
            int i = 0;
            for (; i < memSize; i++) //first fit
                if (frames.get(i) == null) break;

            frames.set(i, newPage);
            size++;
            newPage.status = Status.PAGEFAULT;
        } else {//빈 프레임 없음 가장 오랫동안 안쓴거 교체
            long minTime = Long.MAX_VALUE;
            int victim = 0;
            for (int i = 0; i < memSize; i++) {//lastUsed 최솟값
                Page p = frames.get(i);
                if (p.lastUsed < minTime) {
                    minTime = p.lastUsed;
                    victim = i;
                }
            }
            frames.set(victim, newPage);
            newPage.status = Status.MIGRATION;
            migration++;
        }
        fault++;
        return newPage;
    }
}

/* ─────────────────────────── Second-Chance(Clock) 구현 ─────────────────────────── */

/**
 * Second-Chance(Clock)
 * - frames[hand] 의 ref 비트가 0이면 그 자리를 교체
 * - ref가 1이면 ref=0으로 클리어하고 hand를 시계 방향(+1)으로 이동
 */
class SecondChancePolicy {
    private final int memSize;
    final List<Page> frames;
    private int victimPtr = 0, size = 0;
    int hit, fault, migration;
    private long tick = 0;

    SecondChancePolicy(int memSize) {
        this.memSize = memSize;
        frames = new ArrayList<>(Collections.nCopies(memSize, null));
    }

    public Page access(char pageNum) {
        tick++;

        for (Page p : frames) { //hit 검사
            if (p != null && p.page == pageNum) {
                p.status = Status.HIT;
                p.ref = true; //최근 사용 표시
                hit++;
                return p;
            }
        }

        Page newPage = new Page(pageNum, tick);//fault

        if (size < memSize) {// 빈 공간 있음
            int i = 0;
            for (; i < memSize; i++) //first fit
                if (frames.get(i) == null) break;

            frames.set(i, newPage);
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
        return newPage;
    }
}

/* ────────────────────────────────── 메인 루틴 ────────────────────────────────── */
public class os {

    private static String ask(Scanner sc, String msg) {//입력받기
        System.out.print(msg);
        return sc.nextLine().trim().toUpperCase();
    }

    private static String frameToString(List<Page> frames) {//프레임을 문자열로
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < frames.size(); i++) {
            Page p = frames.get(i);
            sb.append(p == null ? '□' : p.page);
            if (i != frames.size() - 1) sb.append(' ');
        }
        return sb.append(']').toString();
    }

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        //입력
        String reference = ask(sc, "레퍼런스 스트링: ");
        int memSize = Integer.parseInt(ask(sc, "프레임 수: "));
        String policyId = ask(sc, "정책 (FIFO/LRU/SC): ");

        //교체 정책 객체 생성 (필요한 것만 생성)
        FifoPolicy fifo = null;
        LruPolicy lru = null;
        SecondChancePolicy scp = null;

        if (policyId.equals("LRU")) lru = new LruPolicy(memSize);
        else if (policyId.equals("SC")) scp = new SecondChancePolicy(memSize);
        else fifo = new FifoPolicy(memSize);   // 기본 FIFO

        //레이아웃 출력
        System.out.println("\nStep | Ref | Result      | Frames");
        System.out.println("-----------------------------------------------");

        //시뮬레이션
        int step = 1;
        for (char c : reference.toCharArray()) {

            Page res;
            List<Page> frameView;

            if (fifo != null) { // FIFO 선택
                res = fifo.access(c);
                frameView = fifo.frames;
            } else if (lru != null) { // LRU 선택
                res = lru.access(c);
                frameView = lru.frames;
            } else { // Second Chance 선택
                res = scp.access(c);
                frameView = scp.frames;
            }

            System.out.printf("%4d |  %c  | %-11s | %s%n", step++, c, res.status, frameToString(frameView)); //현재 프레임 상태 출력
        }

        //통계 출력
        int hits, faults, migrations;

        if (fifo != null) {
            hits = fifo.hit;
            faults = fifo.fault;
            migrations = fifo.migration;
        } else if (lru != null) {
            hits = lru.hit;
            faults = lru.fault;
            migrations = lru.migration;
        } else {
            hits = scp.hit;
            faults = scp.fault;
            migrations = scp.migration;
        }

        int total = hits + faults;
        double rate = total == 0 ? 0 : 100.0 * faults / total;

        System.out.println("\n====== 통계 ======");
        System.out.printf("Hits       : %d%n", hits);
        System.out.printf("Faults     : %d%n", faults);
        System.out.printf("Migrations : %d%n", migrations);
        System.out.printf("Fault Rate : %.2f%%%n", rate);
    }

}
