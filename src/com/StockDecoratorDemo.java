import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

// ====== 请求/结果 ======
record StockRequest(String orderId, String skuId, String warehouseId, int qty) {}

record StockResult(boolean ok, String message) {}

// ====== 统一接口：库存操作 ======
interface StockService {
    StockResult deduct(StockRequest req); // 扣减/预占都可以
}

// ====== 基础实现：真正做库存扣减（示例用内存库存） ======
class InMemoryStockService implements StockService {
    private final Map<String, Integer> stock = new ConcurrentHashMap<>();

    public InMemoryStockService() {
        stock.put("WH1:SKU1", 10);
        stock.put("WH2:SKU1", 100);
    }

    @Override
    public StockResult deduct(StockRequest req) {
        String key = req.warehouseId() + ":" + req.skuId();
        int cur = stock.getOrDefault(key, 0);
        if (cur < req.qty()) return new StockResult(false, "库存不足, cur=" + cur);

        stock.put(key, cur - req.qty());
        return new StockResult(true, "扣减成功, left=" + (cur - req.qty()));
    }
}

// ====== 抽象装饰器 ======
abstract class StockDecorator implements StockService {
    protected final StockService inner;
    protected StockDecorator(StockService inner) { this.inner = inner; }
}

// 1) 幂等装饰器：同一 orderId 重复请求只执行一次
class IdempotentDecorator extends StockDecorator {
    private final Set<String> processed = ConcurrentHashMap.newKeySet();

    IdempotentDecorator(StockService inner) { super(inner); }

    @Override
    public StockResult deduct(StockRequest req) {
        if (!processed.add(req.orderId())) {
            return new StockResult(true, "幂等命中：重复请求直接返回成功");
        }
        return inner.deduct(req);
    }
}

// 2) 分布式锁/互斥锁装饰器（示例用 synchronized 模拟）
class LockDecorator extends StockDecorator {
    private final Map<String, Object> locks = new ConcurrentHashMap<>();

    LockDecorator(StockService inner) { super(inner); }

    @Override
    public StockResult deduct(StockRequest req) {
        String lockKey = req.warehouseId() + ":" + req.skuId();
        Object lock = locks.computeIfAbsent(lockKey, k -> new Object());

        synchronized (lock) {
            return inner.deduct(req);
        }
    }
}

// 3) 仓库路由装饰器：warehouseId 为空时自动选仓（示例：默认 WH2）
class WarehouseRoutingDecorator extends StockDecorator {
    WarehouseRoutingDecorator(StockService inner) { super(inner); }

    @Override
    public StockResult deduct(StockRequest req) {
        StockRequest routed = req;
        if (req.warehouseId() == null || req.warehouseId().isBlank()) {
            routed = new StockRequest(req.orderId(), req.skuId(), "WH2", req.qty());
        }
        return inner.deduct(routed);
    }
}

// 4) 审计日志装饰器：记录请求、结果、耗时等
class AuditDecorator extends StockDecorator {
    AuditDecorator(StockService inner) { super(inner); }

    @Override
    public StockResult deduct(StockRequest req) {
        long start = System.nanoTime();
        StockResult res = inner.deduct(req);
        long cost = System.nanoTime() - start;

        System.out.println("[AUDIT] order=" + req.orderId()
                + " sku=" + req.skuId()
                + " wh=" + req.warehouseId()
                + " qty=" + req.qty()
                + " -> " + res.message()
                + " costNs=" + cost);
        return res;
    }
}

// ====== Demo：像结算一样拼装库存链 ======
public class StockDecoratorDemo {
    public static void main(String[] args) {
        StockService service =
                new AuditDecorator(
                        new WarehouseRoutingDecorator(
                                new LockDecorator(
                                        new IdempotentDecorator(
                                                new InMemoryStockService()
                                        )
                                )
                        )
                );

        // 第一次正常扣减
        System.out.println(service.deduct(new StockRequest("O1001", "SKU1", "WH1", 3)));
        // 重复请求（幂等命中）
        System.out.println(service.deduct(new StockRequest("O1001", "SKU1", "WH1", 3)));
        // 仓库不传，让路由选择默认仓
        System.out.println(service.deduct(new StockRequest("O1002", "SKU1", "", 5)));
    }
}