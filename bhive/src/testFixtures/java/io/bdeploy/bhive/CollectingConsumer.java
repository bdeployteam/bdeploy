package io.bdeploy.bhive;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class CollectingConsumer<T> implements Consumer<T> {

    private final List<T> items = new ArrayList<>();

    @Override
    public void accept(T t) {
        items.add(t);
    }

    public List<T> getItems() {
        return items;
    }

    public static final <T> List<T> collect(Consumer<Consumer<T>> function) {
        var collector = new CollectingConsumer<T>();
        function.accept(collector);
        return collector.getItems();
    }

}
