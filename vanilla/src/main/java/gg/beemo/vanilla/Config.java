package gg.beemo.vanilla;

public class Config {

    public static String[] RABBIT_HOST = new String[]{"localhost:5672"};

    public static boolean RABBIT_USE_TLS = false;

    public static String RABBIT_USERNAME = "guest";

    public static String RABBIT_PASSWORD = "guest";

    public static int GRPC_PORT = 1337;

    public static int TEA_SHARD_COUNT = 128;
    public static int TEA_CLUSTER_COUNT = 8;

}
