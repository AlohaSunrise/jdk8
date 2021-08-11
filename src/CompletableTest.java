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
//join()��get() ����ͬ�������
//        һ.��ͬ�㣺
//����join()��get()��������������ȡCompletableFuture�첽֮��ķ���ֵ
//        ��.����
//����1.join()�����׳�����uncheck�쳣����δ�������쳣),����ǿ�ƿ������׳����߲���
//�����Ὣ�쳣��װ��CompletionException�쳣 /CancellationException�쳣�����Ǳ���ԭ���Ǵ����ڴ��ڵ��������쳣��
//   2.get()�����׳����Ǿ��������쳣��ExecutionException, InterruptedException ��Ҫ�û��ֶ������׳����� try catch��


//        û��ָ��Executor�ķ�����ʹ��ForkJoinPool.commonPool() ��Ϊ�����̳߳�ִ���첽���롣���ָ���̳߳أ���ʹ��ָ�����̳߳����С��������еķ�������ͬ��
//
//        runAsync������֧�ַ���ֵ��
//        supplyAsync����֧�ַ���ֵ��
//        runAsync();
//        supplyAsync();

//        ���������ʱ�Ļص�����  whenComplete�����̵�exception   exceptionally��
//        whenComplete();
        
        //��һ���߳�������һ���߳�ʱ������ʹ�� thenApply ���������������̴߳��л���    �ڶ�������������һ������Ľ����
//        thenApply();

//        handle ��ִ���������ʱ�Խ���Ĵ���
//        handle ������ thenApply ��������ʽ����һ������ͬ���� handle ����������ɺ���ִ�У������Դ����쳣������
//        thenApply ֻ����ִ��������������������쳣��ִ�� thenApply ����
//        handle();

//        thenAccept ���Ѵ�����
//        ��������Ĵ������������Ѵ����޷��ؽ��
//        thenAccept();

//        �� thenAccept ������һ�����ǣ�����������Ĵ�������ֻҪ���������ִ����ɣ��Ϳ�ʼִ�� 
//        thenRun();

//        thenCombine ��� ���� CompletionStage ������ִ����ɺ󣬰���������Ľ��һ�齻�� thenCombine ������
//        thenCombine();
        
        //������CompletionStage��ִ����ɺ󣬰ѽ��һ�齻��thenAcceptBoth����������
//        thenAcceptBoth();

        //����CompletionStage��˭ִ�з��صĽ���죬�Ҿ����Ǹ�CompletionStage�Ľ��������һ����ת��������
//        applyToEither();
//        ����CompletionStage��˭ִ�з��صĽ���죬�Ҿ����Ǹ�CompletionStage�Ľ��������һ�������Ĳ���
//        acceptEither();

//        ����CompletionStage���κ�һ������˶���ִ����һ���Ĳ�����Runnable��
//        runAfterEither();

        //����CompletionStage��������˼���Ż�ִ����һ���Ĳ�����Runnable��
//        runAfterBoth();

        //thenCompose ��������������� CompletionStage ������ˮ�߲�������һ���������ʱ����������Ϊ�������ݸ��ڶ���������

//        thenCompose();
//        CompletableFuture.anyOf() ����һ������
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


        ////        CompletableFuture.allOf() ������
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

    //�޷���ֵ
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

    //�з���ֵ
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
                System.out.println("ִ����ɣ�");
            }

        }).whenComplete(new BiConsumer<Void, Throwable>() {
            @Override
            public void accept(Void t, Throwable action) {
                System.out.println("ִ�����2��");
            }

        }).exceptionally(new Function<Throwable, Void>() {
            @Override
            public Void apply(Throwable t) {
                System.out.println("ִ��ʧ�ܣ�"+t.getMessage());
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
            //exception ��handle ������
        }).exceptionally(new Function<Throwable, Integer>() {
            @Override
            public Integer apply(Throwable throwable) {
                System.out.println(throwable==null?"û����":throwable.getMessage());
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
                System.out.println("������һ���Ѿ�����ˡ�");
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
                System.out.println("������������ִ������ˡ�");
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
                //���ﻹ����дcode
                
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
