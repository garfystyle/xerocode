package org.teavm.classlib.java.util.concurrent;

import java.util.AbstractQueue;
import java.util.ArrayDeque;
import java.util.Iterator;

public class TConcurrentLinkedQueue<E> extends AbstractQueue<E> {
    private final ArrayDeque<E> items = new ArrayDeque<>();

    public TConcurrentLinkedQueue() {}

    @Override
    public boolean offer(E e) {
        if (e == null) throw new NullPointerException();
        items.addLast(e);
        return true;
    }

    @Override
    public E poll() { return items.pollFirst(); }

    @Override
    public E peek() { return items.peekFirst(); }

    @Override
    public int size() { return items.size(); }

    @Override
    public Iterator<E> iterator() { return items.iterator(); }
}
