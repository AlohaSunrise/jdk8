import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class CompletableTest {

    public static void main(String[] args) {
//join()和get() 的相同点和区别
//        一.相同点：
//　　join()和get()方法都是用来获取CompletableFuture异步之后的返回值
//        二.区别：
//　　1.join()方法抛出的是uncheck异常（即未经检查的异常),不会强制开发者抛出或者捕获，
//　　会将异常包装成CompletionException异常 /CancellationException异常，但是本质原因还是代码内存在的真正的异常，
//   2.get()方法抛出的是经过检查的异常，ExecutionException, InterruptedException 需要用户手动处理（抛出或者 try catch）


//        没有指定Executor的方法会使用ForkJoinPool.commonPool() 作为它的线程池执行异步代码。如果指定线程池，则使用指定的线程池运行。以下所有的方法都类同。
//
//        runAsync方法不支持返回值。
//        supplyAsync可以支持返回值。
//        runAsync();
//        supplyAsync();

//        计算结果完成时的回调方法  whenComplete不会吞掉exception   exceptionally会
//        whenComplete();
        
        //当一个线程依赖另一个线程时，可以使用 thenApply 方法来把这两个线程串行化。    第二个任务依赖第一个任务的结果。
//        thenApply();

//        handle 是执行任务完成时对结果的处理。
//        handle 方法和 thenApply 方法处理方式基本一样。不同的是 handle 是在任务完成后再执行，还可以处理异常的任务。
//        thenApply 只可以执行正常的任务，任务出现异常则不执行 thenApply 方法
//        handle();

//        thenAccept 消费处理结果
//        接收任务的处理结果，并消费处理，无返回结果
//        thenAccept();

//        跟 thenAccept 方法不一样的是，不关心任务的处理结果。只要上面的任务执行完成，就开始执行 
//        thenRun();

//        thenCombine 会把 两个 CompletionStage 的任务都执行完成后，把两个任务的结果一块交给 thenCombine 来处理。
//        thenCombine();
        
        //当两个CompletionStage都执行完成后，把结果一块交给thenAcceptBoth来进行消耗
//        thenAcceptBoth();

        //两个CompletionStage，谁执行返回的结果快，我就用那个CompletionStage的结果进行下一步的转化操作。
//        applyToEither();
//        两个CompletionStage，谁执行返回的结果快，我就用那个CompletionStage的结果进行下一步的消耗操作
//        acceptEither();

//        两个CompletionStage，任何一个完成了都会执行下一步的操作（Runnable）
//        runAfterEither();

        //两个CompletionStage，都完成了计算才会执行下一步的操作（Runnable）
//        runAfterBoth();

        //thenCompose 方法允许你对两个 CompletionStage 进行流水线操作，第一个操作完成时，将其结果作为参数传递给第二个操作。

//        thenCompose();
//        CompletableFuture.anyOf() 任意一个返回
        CompletableFuture<Object> anyOf = CompletableFuture.anyOf(CompletableFuture.supplyAsync(new Supplier<String>() {

            @Override
            public String get() {
                try {
                    Thread.sleep(200L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                return "stage one";
            }
        }), CompletableFuture.supplyAsync(new Supplier<String>() {

            @Override
            public String get() {
                return "stage two";
            }
        }));
        //stage two
        System.out.println(anyOf.join());


        ////        CompletableFuture.allOf() 都返回
        CompletableFuture<Void> allOf = CompletableFuture.allOf(CompletableFuture.supplyAsync(new Supplier<String>() {

            @Override
            public String get() {
                try {
                    Thread.sleep(200L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                return "stage one";
            }
        }), CompletableFuture.supplyAsync(new Supplier<String>() {

            @Override
            public String get() {
                return "stage two";
            }
        }));
        




        try {
            Thread.sleep(10000000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    //无返回值
    public static void runAsync() {
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
            }
            System.out.println("run end ...");
        });

        future.join();
    }

    //有返回值
    public static void supplyAsync() {
        CompletableFuture<Long> future = CompletableFuture.supplyAsync(() -> {
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
            }
            System.out.println("run end ...");
            return System.currentTimeMillis();
        });

        long time = future.join();
        System.out.println("time = " + time);
    }


    public static void whenComplete() {
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
            }
//            if(new Random().nextInt()%2>=0) {
                int i = 12/0;
//            }
            System.out.println("run end ...");
        }).whenComplete(new BiConsumer<Void, Throwable>() {
            @Override
            public void accept(Void t, Throwable action) {
                System.out.println("执行完成！");
            }

        }).whenComplete(new BiConsumer<Void, Throwable>() {
            @Override
            public void accept(Void t, Throwable action) {
                System.out.println("执行完成2！");
            }

        }).exceptionally(new Function<Throwable, Void>() {
            @Override
            public Void apply(Throwable t) {
                System.out.println("执行失败！"+t.getMessage());
                return null;
            }
        });

    }



    private static void thenApply() {
        CompletableFuture<Long> future = CompletableFuture.supplyAsync(new Supplier<Long>() {
            @Override
            public Long get() {
                long result = new Random().nextInt(100);
                System.out.println("result1="+result);
                return result;
            }
        }).thenApply(new Function<Long, Long>() {
            @Override
            public Long apply(Long t) {
                long result = t*5;
                System.out.println("result2="+result);
                return result;
            }
        });

        long result = future.join();
        System.out.println(result);
    }
    

    public static void handle() {
        CompletableFuture<Integer> future = CompletableFuture.supplyAsync(new Supplier<Integer>() {
            @Override
            public Integer get() {
                int i = 10 / 0;
                return new Random().nextInt(10);
            }
        }).handle(new BiFunction<Integer, Throwable, Integer>() {
            @Override
            public Integer apply(Integer param, Throwable throwable) {
                int result = -1;
                if (throwable == null) {
                    result = param * 2;
                } else {
                    System.out.println(throwable.getMessage());
                }
                return result;
            }
            //exception 被handle 处理了
        }).exceptionally(new Function<Throwable, Integer>() {
            @Override
            public Integer apply(Throwable throwable) {
                System.out.println(throwable==null?"没错误":throwable.getMessage());
                return 1;
            }
        });
        System.out.println(future.join());
    }

    public static void thenAccept(){
        CompletableFuture<Void> future = CompletableFuture.supplyAsync(new Supplier<Integer>() {
            @Override
            public Integer get() {
                return new Random().nextInt(10);
            }
        }).thenAccept(new Consumer<Integer>() {
            @Override
            public void accept(Integer integer) {
                System.out.println(integer);
            }
        });
        future.join();
    }

    public static void thenRun() {
        CompletableFuture<Void> future = CompletableFuture.supplyAsync(new Supplier<Integer>() {
            @Override
            public Integer get() {
                System.out.println(Thread.currentThread().getName());
                return new Random().nextInt(10);
            }
        }).thenRun(new Runnable() {
            @Override
            public void run() {
                System.out.println("thenRun ..." + Thread.currentThread().getName());
            }
        });
        future.join();
    }

    private static void thenCombine()  {
        CompletableFuture<String> stringCompletableFuture = CompletableFuture.supplyAsync(new Supplier<String>() {
            @Override
            public String get() {
                System.out.println(Thread.currentThread().getName());
                try {
                    Thread.sleep(100L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                return "stage one ";
            }
        }).thenCombine(CompletableFuture.supplyAsync(new Supplier<String>() {
            @Override
            public String get() {
                System.out.println(Thread.currentThread().getName());
                try {
                    Thread.sleep(100L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                return "stage two";
            }
        }), new BiFunction<String, String, String>() {
            @Override
            public String apply(String t, String u) {
                System.out.println(Thread.currentThread().getName());
                return t + " " + u;
            }
        });

        System.out.println(stringCompletableFuture.join());
    }

    private static void thenAcceptBoth()  {
        
        CompletableFuture<Void> stringCompletableFuture = CompletableFuture.supplyAsync(new Supplier<String>() {
            @Override
            public String get() {
                System.out.println(Thread.currentThread().getName());
                try {
                    Thread.sleep(100L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                return "stage one ";
            }
        }).thenAcceptBoth(CompletableFuture.supplyAsync(new Supplier<String>() {
            @Override
            public String get() {
                System.out.println(Thread.currentThread().getName());
                try {
                    Thread.sleep(100L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                return "stage two";
            }
        }), new BiConsumer<String, String>() {

            @Override
            public void accept(String s, String s2) {
                //stage one stage two
                System.out.println(s+s2);
            }
        });
//null
        System.out.println(stringCompletableFuture.join());
    }



    private static void applyToEither() {
        CompletableFuture<Integer> f1 = CompletableFuture.supplyAsync(new Supplier<Integer>() {
            @Override
            public Integer get() {
                int t = 1;
                try {
                    TimeUnit.SECONDS.sleep(t);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println("f1="+t);
                return t;
            }
        });
        CompletableFuture<Integer> f2 = CompletableFuture.supplyAsync(new Supplier<Integer>() {
            @Override
            public Integer get() {
                int t = 2;
                try {
                    TimeUnit.SECONDS.sleep(t);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println("f2="+t);
                return t;
            }
        });

        CompletableFuture<Integer> result = f1.applyToEither(f2, new Function<Integer, Integer>() {
            @Override
            public Integer apply(Integer t) {
                System.out.println(t);
                return t * 2;
            }
        });

        System.out.println(result.join());
    }


    private static void acceptEither() {
        CompletableFuture<Integer> f1 = CompletableFuture.supplyAsync(new Supplier<Integer>() {
            @Override
            public Integer get() {
                int t = new Random().nextInt(3);
                try {
                    TimeUnit.SECONDS.sleep(t);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println("f1="+t);
                return t;
            }
        });

        CompletableFuture<Integer> f2 = CompletableFuture.supplyAsync(new Supplier<Integer>() {
            @Override
            public Integer get() {
                int t = new Random().nextInt(3);
                try {
                    TimeUnit.SECONDS.sleep(t);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println("f2="+t);
                return t;
            }
        });
        f1.acceptEither(f2, new Consumer<Integer>() {
            @Override
            public void accept(Integer t) {
                System.out.println(t);
            }
        });
    }

    private static void runAfterEither() {
        CompletableFuture<Integer> f1 = CompletableFuture.supplyAsync(new Supplier<Integer>() {
            @Override
            public Integer get() {
                int t = new Random().nextInt(3);
                try {
                    TimeUnit.SECONDS.sleep(t);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println("f1="+t);
                return t;
            }
        });

        CompletableFuture<Integer> f2 = CompletableFuture.supplyAsync(new Supplier<Integer>() {
            @Override
            public Integer get() {
                int t = new Random().nextInt(3);
                try {
                    TimeUnit.SECONDS.sleep(t);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println("f2="+t);
                return t;
            }
        });
        f1.runAfterEither(f2, new Runnable() {

            @Override
            public void run() {
                System.out.println("上面有一个已经完成了。");
            }
        });
    }

    private static void runAfterBoth() {
        CompletableFuture<Integer> f1 = CompletableFuture.supplyAsync(new Supplier<Integer>() {
            @Override
            public Integer get() {
                int t = new Random().nextInt(3);
                try {
                    TimeUnit.SECONDS.sleep(t);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println("f1="+t);
                return t;
            }
        });

        CompletableFuture<Integer> f2 = CompletableFuture.supplyAsync(new Supplier<Integer>() {
            @Override
            public Integer get() {
                int t = new Random().nextInt(3);
                try {
                    TimeUnit.SECONDS.sleep(t);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println("f2="+t);
                return t;
            }
        });
        f1.runAfterBoth(f2, new Runnable() {

            @Override
            public void run() {
                System.out.println("上面两个任务都执行完成了。");
            }
        });
    }

    private static void thenCompose()  {
        CompletableFuture<Integer> f = CompletableFuture.supplyAsync(new Supplier<Integer>() {
            @Override
            public Integer get() {
                int t = new Random().nextInt(3);
                System.out.println("t1="+t);
                return t;
            }
        }).thenCompose(new Function<Integer, CompletionStage<Integer>>() {
            @Override
            public CompletionStage<Integer> apply(Integer param) {
                //这里还可以写code
                
                return CompletableFuture.supplyAsync(new Supplier<Integer>() {
                    @Override
                    public Integer get() {
                        int t = param *2;
                        System.out.println("t2="+t);
                        return t;
                    }
                });
            }

        });
        System.out.println("thenCompose result : "+f.join());
    }

}
