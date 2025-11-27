package musicsearch.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class EventBus {
    private static final Map<Class<?>, List<Consumer<?>>> listeners = new ConcurrentHashMap<>();
    
    public static <T> void subscribe(Class<T> eventType, Consumer<T> listener) {
        listeners.computeIfAbsent(eventType, k -> new ArrayList<>()).add(listener);
    }
    
    @SuppressWarnings("unchecked")
    public static <T> void publish(T event) {
        List<Consumer<?>> eventListeners = listeners.get(event.getClass());
        if (eventListeners != null) {
            eventListeners.forEach(listener -> ((Consumer<T>) listener).accept(event));
        }
    }
}
