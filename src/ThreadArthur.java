import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

public class ThreadArthur implements Runnable {
    private String message;
    private int var;
    private ReentrantLock lock;
    public ThreadArthur(String msg, int var) {
        this.message = msg;
        this.var = var;
        this.lock = new ReentrantLock();
    }

    @Override
    public void run () {
        System.out.println("thread : " + Thread.currentThread().getName());
        while (this.var <= 1000) {
            this.lock.lock();
            if (this.var <= 1000)
                this.var += 1;
            this.lock.unlock();
        }
    }

    public static void main (String[] args) {
        ExecutorService executor = Executors.newFixedThreadPool(100);
        ThreadArthur tp = new ThreadArthur("yo", 0);
        for(int i = 0; i < 20; i++) {
            executor.execute(tp);
        }
        executor.shutdown();
        while (!executor.isTerminated()) {   }
        System.out.println(tp.var);
    }
}

