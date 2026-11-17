package com.college.complaint.utility;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class GenericUtility {

    private GenericUtility() {
    }

    /** Makes a mutable list from a collection whose values are T or a subtype of T. */
    public static <T> List<T> copyToList(Collection<? extends T> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
    }
}
