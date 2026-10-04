package dev.zerologin.storage;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 账号存储抽象。
 *
 * <p>所有方法均为异步语义：实现可以同步完成，但调用方必须按异步处理，
 * 因为账号读写不允许阻塞服务端主线程。
 */
public interface AuthStore {

    /**
     * 按 UUID 读取账号。
     *
     * @return 不存在时完成为 {@code null}
     */
    CompletableFuture<AuthRecord> loadByUuid(UUID uuid);

    /**
     * 按当前名字读取账号。
     *
     * <p>名字在 Java 版中是大小写不敏感且可变的，实现需要在解析时处理好这一点，
     * 并把记录的 {@code name} 更新为最新观察到的名字。
     */
    CompletableFuture<AuthRecord> loadByName(String name);

    CompletableFuture<Void> save(AuthRecord record);

    CompletableFuture<Boolean> deleteByUuid(UUID uuid);

    CompletableFuture<Integer> countAccounts();

    /** 打开连接/加载缓存，在服务端启用阶段调用。失败时抛出运行时异常。 */
    void open() throws Exception;

    /** 关闭连接并冲刷未落盘的数据。 */
    void close();

    /** 后端名称，用于启动日志与 /zerologin info。 */
    String name();
}
