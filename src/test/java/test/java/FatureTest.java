package test.java;

import cc.mrbird.febs.FebsShiroApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.apache.commons.io.FileUtils.waitFor;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author kevin.he
 * @date : 2022/3/11 18:01
 */
@SpringBootTest(classes = FebsShiroApplication.class)
@Slf4j
public class FatureTest {
    private static final RejectedExecutionHandler defaultHandler = new ThreadPoolExecutor.AbortPolicy();
    @Test
    public void test1() throws Exception {
        System.out.println("--main函数开始执行");
        Future<Integer> future=longtime2();
        System.out.println("main函数执行结束");
        System.out.println("异步执行结果："+future.get());
    }

    @Async
    public Future<Integer> longtime2() {
        System.out.println("我在执行一项耗时任务");

        try {

            Thread.sleep(8000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("完成");
        return new AsyncResult<>(3);
    }
    @Test
    public void givenGrowPolicy_WhenSaturated_ThenExecutorIncreaseTheMaxPoolSize() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, MILLISECONDS, new ArrayBlockingQueue<>(2),defaultHandler );
        executor.execute(() -> waitFor(100));

        BlockingQueue<String> queue = new LinkedBlockingDeque<>();
        executor.execute(() -> queue.offer("First"));
        executor.execute(() -> queue.offer("Second"));
        executor.execute(() -> queue.offer("Third"));

        waitFor(150);
        List<String> results = new ArrayList<>();
        queue.drainTo(results);
        assertThat(results);
//        assertThat(results).("First", "Second", "Third");
    }

    private void assertThat(List<String> results) {
    }


    private void waitFor(int i) {
    }
/*    public  void test2(){

        ThreadPoolExecutor executor =new FatureTest();
        stepA();
        Future futureB = executor.submit(() -> stepB());
        Future futureC = executor.submit(() -> stepC());

        stepB1(futureB.get());
// 这一步必须等stepB1执行完
        stepC1(futureC.get());
    }*/

    public  static  void stepA(){
        System.out.println("AA--");
    }
    public  static void stepB(){
        System.out.println("AA--");
    }

    public  static void stepC(){
        System.out.println("AA--");
    }
}
