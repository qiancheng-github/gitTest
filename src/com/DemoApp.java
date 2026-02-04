package demo;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/* =========================
 * class 1 : DemoApp (入口)
 * ========================= */
public class DemoApp {

    public static void main(String[] args) {
        PaymentService paymentService = new PaymentService();

        System.out.println(paymentService.pay("WECHAT", "O1"));
        System.out.println(paymentService.pay("ALIPAY", "O2"));
        System.out.println(paymentService.pay("BALANCE", "O3"));

        try {
            paymentService.pay("APPLE", "O4");
        } catch (Exception e) {
            System.out.println("EX: " + e.getMessage());
        }
    }
}

/* =========================
 * class 2 : PaymentService
 * ========================= */
class PaymentService {

    private final OrderService orderService = new OrderService();
    private final PayStrategyFactory factory = new PayStrategyFactory();

    public PayResult pay(String channel, String orderId) {
        PayRequest request = orderService.get(orderId);
        PayStrategy strategy = factory.get(channel);
        return strategy.pay(request);
    }
}

/* =========================
 * class 3 : PayStrategyFactory
 * ========================= */
class PayStrategyFactory {

    private final Map<String, PayStrategy> strategyMap = new HashMap<>();

    public PayStrategyFactory() {
        strategyMap.put("WECHAT", new WechatPayStrategy());
        strategyMap.put("ALIPAY", new AlipayStrategy());
        strategyMap.put("BALANCE", new BalancePayStrategy());
    }

    public PayStrategy get(String channel) {
        PayStrategy strategy = strategyMap.get(channel);
        if (strategy == null) {
            throw new IllegalArgumentException("unsupported channel: " + channel);
        }
        return strategy;
    }
}

/* =========================
 * class 4 : PayStrategy (策略接口)
 * ========================= */
interface PayStrategy {
    PayResult pay(PayRequest request);
}

/* =========================
 * class 5 : WechatPayStrategy
 * ========================= */
class WechatPayStrategy implements PayStrategy {

    @Override
    public PayResult pay(PayRequest request) {
        BigDecimal fee = request.amount.multiply(new BigDecimal("0.006"));
        BigDecimal total = request.amount.add(fee);

        System.out.println("[WECHAT] pay order=" + request.orderId + ", total=" + total);
        return new PayResult(true, "WECHAT", "fee=" + fee);
    }
}

/* =========================
 * class 6 : AlipayStrategy
 * ========================= */
class AlipayStrategy implements PayStrategy {

    @Override
    public PayResult pay(PayRequest request) {
        BigDecimal fee = request.amount.multiply(new BigDecimal("0.0055"));
        BigDecimal total = request.amount.add(fee);

        System.out.println("[ALIPAY] pay order=" + request.orderId + ", total=" + total);
        return new PayResult(true, "ALIPAY", "fee=" + fee);
    }
}

/* =========================
 * class 7 : BalancePayStrategy
 * ========================= */
class BalancePayStrategy implements PayStrategy {

    private final WalletService walletService = new WalletService();

    @Override
    public PayResult pay(PayRequest request) {
        boolean ok = walletService.deduct(request.userId, request.amount);
        if (!ok) {
            return new PayResult(false, "BALANCE", "insufficient balance");
        }

        System.out.println("[BALANCE] pay order=" + request.orderId);
        return new PayResult(true, "BALANCE", "paid by balance");
    }
}

/* =========================
 * class 8 : OrderService
 * ========================= */
class OrderService {

    private final Map<String, PayRequest> orders = new HashMap<>();

    public OrderService() {
        orders.put("O1", new PayRequest("O1", new BigDecimal("100"), "U1"));
        orders.put("O2", new PayRequest("O2", new BigDecimal("80"), "U1"));
        orders.put("O3", new PayRequest("O3", new BigDecimal("300"), "U1"));
        orders.put("O4", new PayRequest("O4", new BigDecimal("100"), "U1"));
    }

    public PayRequest get(String orderId) {
        return orders.get(orderId);
    }
}

/* =========================
 * class 9 : WalletService
 * ========================= */
class WalletService {

    private BigDecimal balance = new BigDecimal("200");

    public boolean deduct(String userId, BigDecimal amount) {
        if (balance.compareTo(amount) < 0) return false;
        balance = balance.subtract(amount);
        return true;
    }
}

/* =========================
 * class 10 : PayRequest
 * ========================= */
class PayRequest {
    String orderId;
    BigDecimal amount;
    String userId;

    public PayRequest(String orderId, BigDecimal amount, String userId) {
        this.orderId = orderId;
        this.amount = amount;
        this.userId = userId;
    }
}

/* =========================
 * class 11 : PayResult
 * ========================= */
class PayResult {
    boolean success;
    String channel;
    String message;

    public PayResult(boolean success, String channel, String message) {
        this.success = success;
        this.channel = channel;
        this.message = message;
    }

    @Override
    public String toString() {
        return "PayResult{" +
                "success=" + success +
                ", channel='" + channel + '\'' +
                ", message='" + message + '\'' +
                '}';
    }
}