package li.fyun.commons.core.criteria;

import java.util.List;

public interface Condition<E extends Expression, T extends Order> extends Criterion<E> {

    List<T> getOrders();

    void setOrders(List<T> orders);

    default boolean validate() {
        return this.validate(null);
    }

}
