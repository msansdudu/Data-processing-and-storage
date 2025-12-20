package ru.nsu.chebotareva.list;

import java.util.Iterator;
import java.util.List;

/**
 * Interface for a thread-safe string list that supports iteration
 * and provides a snapshot method for testing purposes.
 */
public interface IterableStringList extends Iterable<String> {
    /**
     * Adds a string to the beginning of the list.
     * @param value the string to add
     * @throws IllegalArgumentException if value is null
     */
    void addFirst(String value);

    /**
     * Returns the current size of the list.
     * @return number of elements in the list
     */
    int size();

    /**
     * Returns an iterator over the elements in the list.
     * @return iterator for traversing the list
     */
    @Override
    Iterator<String> iterator();

    /**
     * Creates a snapshot of the current list state.
     * @return a new list containing all current elements
     */
    List<String> snapshot();
}